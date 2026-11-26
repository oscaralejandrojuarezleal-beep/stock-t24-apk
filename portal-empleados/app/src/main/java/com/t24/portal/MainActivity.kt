package com.t24.portal

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * APK del Portal de Empleados T24: un WebView que abre la misma web de Apps Script.
 * Todo lo que se cambia en Apps Script llega solo a la APK (no hay que reinstalar).
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView

    private val permisoAvisos = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun pedirPermisoAvisos() {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) permisoAvisos.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    private var archivosPendientes: ValueCallback<Array<Uri>>? = null
    private var fotoCamara: File? = null

    private val elegirArchivo =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            archivosPendientes?.onReceiveValue(urisElegidas(r.resultCode, r.data))
            archivosPendientes = null
        }

    private fun urisElegidas(codigo: Int, data: Intent?): Array<Uri>? {
        val foto = fotoCamara
        fotoCamara = null
        if (codigo != Activity.RESULT_OK) {
            foto?.delete()
            return null
        }
        val lista = LinkedHashSet<Uri>()
        data?.clipData?.let { c -> for (i in 0 until c.itemCount) c.getItemAt(i)?.uri?.let { lista.add(it) } }
        data?.data?.let { lista.add(it) }
        if (foto != null) {
            if (foto.length() <= 0) foto.delete()
            else if (lista.isEmpty()) lista.add(FileProvider.getUriForFile(this, "$packageName.archivos", foto))
        }
        return if (lista.isEmpty()) null else lista.toTypedArray()
    }

    private fun intentElegir(params: WebChromeClient.FileChooserParams): Intent {
        val tipos = params.acceptTypes.flatMap { it.split(",") }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        val elegir = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            when {
                tipos.isEmpty() -> type = "*/*"
                tipos.size == 1 && tipos[0].contains("/") -> type = tipos[0]
                else -> {
                    type = "*/*"
                    val mimes = tipos.filter { it.contains("/") }
                    if (mimes.isNotEmpty()) putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
                }
            }
            if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val extras = mutableListOf<Intent>()
        if (tipos.isEmpty() || tipos.any { it.startsWith("image/") || it == "*/*" }) {
            try {
                val carpeta = File(cacheDir, "fotos").apply { mkdirs() }
                carpeta.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 86_400_000L) it.delete() }
                val archivo = File(carpeta, "foto_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(this, "$packageName.archivos", archivo)
                val camara = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (camara.resolveActivity(packageManager) != null) {
                    fotoCamara = archivo
                    extras.add(camara)
                }
            } catch (e: Exception) {
                fotoCamara = null
            }
        }
        if (params.isCaptureEnabled && extras.isNotEmpty()) return extras[0]
        return Intent.createChooser(elegir, "Elegir archivo").apply {
            if (extras.isNotEmpty()) putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toTypedArray())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val contenedor = FrameLayout(this)
        contenedor.setBackgroundColor(Color.parseColor("#0E2A20"))
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#F3F6F4"))
        contenedor.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(contenedor)
        ViewCompat.setOnApplyWindowInsetsListener(contenedor) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        WindowCompat.getInsetsController(window, contenedor).isAppearanceLightStatusBars = false

        configurarWeb()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(Config.URL_PORTAL)
        revisarApkNueva()
        Avisos.crearCanal(this)
        Avisos.programar(this)
    }

    /** Hosts de Google que forman parte del portal: se quedan dentro de la app. */
    private fun esDelPortal(u: Uri): Boolean {
        val h = (u.host ?: "").lowercase()
        return u.scheme == "https" && (h == "script.google.com" || h.endsWith(".googleusercontent.com") ||
            h == "accounts.google.com" || h.endsWith(".gstatic.com") || h.endsWith(".googleapis.com"))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWeb() {
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = 100
        }
        web.addJavascriptInterface(PuentePortal(this), "T24Portal")
        // Oculta el cartel "Esta aplicación fue creada por un usuario de Google Apps Script":
        // se ejecuta antes de que se dibuje la página, así ni se llega a ver.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(web, SIN_CARTEL, setOf("https://script.google.com"))
            } catch (e: Exception) {
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (esDelPortal(request.url)) return false
                abrirAfuera(request.url)
                return true
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                if (url?.startsWith("https://script.google.com/") == true) view.evaluateJavascript(SIN_CARTEL, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (url?.startsWith("https://script.google.com/") == true) view.evaluateJavascript(SIN_CARTEL, null)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) mostrarSinConexion()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val temporal = WebView(this@MainActivity)
                temporal.webViewClient = object : WebViewClient() {
                    private var listo = false
                    private fun derivar(v: WebView, url: Uri?) {
                        if (listo || url == null || url.toString() == "about:blank") return
                        listo = true
                        abrirAfuera(url)
                        v.stopLoading()
                        v.destroy()
                    }
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        derivar(v, r.url); return true
                    }
                    override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                        derivar(v, url?.let { Uri.parse(it) })
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = temporal
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
                archivosPendientes?.onReceiveValue(null)
                archivosPendientes = filePathCallback
                return try {
                    elegirArchivo.launch(intentElegir(fileChooserParams)); true
                } catch (e: Exception) {
                    fotoCamara = null
                    try {
                        elegirArchivo.launch(fileChooserParams.createIntent()); true
                    } catch (e2: ActivityNotFoundException) {
                        archivosPendientes = null; false
                    }
                }
            }
        }
    }

    companion object {
        /**
         * Quita el cartel de Google de arriba y deja la app (el cuadro "sandboxFrame")
         * ocupando toda la pantalla. Sólo actúa en la página principal de script.google.com.
         */
        private val SIN_CARTEL = """
            (function(){
              try{
                if(window.top!==window||location.hostname!=='script.google.com')return;
                if(window.__t24SinCartel)return; window.__t24SinCartel=1;
                var css='html,body{margin:0!important;padding:0!important;overflow:hidden!important;height:100%!important;background:#F3F6F4!important}'+
                  'div#warning{display:none!important}'+
                  'iframe#sandboxFrame,iframe.t24-app{position:fixed!important;top:0!important;left:0!important;width:100%!important;height:100%!important;border:0!important;margin:0!important;z-index:2147483647!important}';
                function estilo(){
                  if(document.getElementById('t24-sin-cartel'))return;
                  var r=document.head||document.documentElement; if(!r)return;
                  var s=document.createElement('style'); s.id='t24-sin-cartel'; s.textContent=css; r.appendChild(s);
                }
                function marcar(){
                  estilo();
                  if(document.getElementById('sandboxFrame'))return;
                  var fs=document.getElementsByTagName('iframe'),mejor=null,max=0;
                  for(var i=0;i<fs.length;i++){var h=fs[i].offsetHeight*fs[i].offsetWidth;if(h>max){max=h;mejor=fs[i];}}
                  if(mejor&&mejor.className.indexOf('t24-app')<0)mejor.className+=' t24-app';
                }
                estilo();
                document.addEventListener('DOMContentLoaded',marcar);
                window.addEventListener('load',marcar);
                new MutationObserver(marcar).observe(document.documentElement,{childList:true,subtree:true});
              }catch(e){}
            })();
        """.trimIndent()
    }

    private fun mostrarSinConexion() {
        val html = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{margin:0;height:100vh;display:grid;place-items:center;font-family:sans-serif;background:#f3f6f4;color:#17231d;text-align:center}
            button{background:#0f6f52;color:#fff;border:0;border-radius:12px;padding:12px 22px;font-size:16px;font-weight:bold}</style></head>
            <body><div><h2>Sin conexión</h2><p>Revisá internet y volvé a intentar.</p>
            <button onclick="location.href='${Config.URL_PORTAL}'">Reintentar</button></div></body></html>"""
        web.loadDataWithBaseURL(Config.URL_PORTAL, html, "text/html", "utf-8", Config.URL_PORTAL)
    }

    private fun abrirAfuera(url: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
        }
    }

    /** Si GitHub publicó una APK más nueva del Portal, ofrece descargarla (como mucho cada 6 h). */
    private fun revisarApkNueva() {
        val prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong("ultimaRevision", 0) < 6 * 3_600_000L) return
        prefs.edit().putLong("ultimaRevision", System.currentTimeMillis()).apply()
        thread {
            try {
                val c = URL("https://github.com/${Config.GITHUB_REPO}/releases/download/${Config.TAG}/version.json").openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 10000; c.instanceFollowRedirects = true
                val texto = c.inputStream.bufferedReader().use { it.readText() }
                val nueva = Regex("\"versionCode\"\\s*:\\s*(\\d+)").find(texto)?.groupValues?.get(1)?.toLongOrNull() ?: return@thread
                val apk = Regex("\"apk\"\\s*:\\s*\"([^\"]+)\"").find(texto)?.groupValues?.get(1) ?: return@thread
                val actual = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
                if (nueva > actual && apk.startsWith("https://github.com/")) runOnUiThread {
                    if (!isFinishing) AlertDialog.Builder(this)
                        .setTitle("Hay una versión nueva")
                        .setMessage("¿Querés descargar e instalar la nueva versión del Portal T24?")
                        .setPositiveButton("Descargar") { _, _ -> abrirAfuera(Uri.parse(apk)) }
                        .setNegativeButton("Después", null)
                        .show()
                }
            } catch (e: Exception) {
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
}
