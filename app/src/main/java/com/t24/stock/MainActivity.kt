package com.t24.stock

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import java.io.File

/**
 * Pantalla única de la APK: un WebView que carga la misma app de Apps Script,
 * guardada en el celular para que abra al instante y funcione sin conexión.
 */
class MainActivity : ComponentActivity() {

    lateinit var web: WebView
        private set

    private var archivosPendientes: ValueCallback<Array<Uri>>? = null

    private val pedirPermisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Foto que saca la cámara cuando se elige "Cámara" al subir archivos. */
    private var fotoCamara: File? = null

    private val elegirArchivo =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
            archivosPendientes?.onReceiveValue(urisElegidas(resultado.resultCode, resultado.data))
            archivosPendientes = null
        }

    /**
     * Archivos elegidos. Android devuelve varias fotos en "clipData" (y a veces una sola también),
     * que el lector estándar ignora: por eso antes no cargaba nada al elegir fotos en roturas.
     */
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

    /** Elegir archivos: galería/archivos (una o varias) y, si acepta imágenes, también la cámara. */
    private fun intentElegir(params: WebChromeClient.FileChooserParams): Intent {
        val tipos = params.acceptTypes
            .flatMap { it.split(",") }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
        val varios = params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
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
            if (varios) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val extras = mutableListOf<Intent>()
        val aceptaImagen = tipos.isEmpty() || tipos.any { it.startsWith("image/") || it == "*/*" }
        if (aceptaImagen) {
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
        // <input capture> (botón "Sacar foto"): abre la cámara directo.
        if (params.isCaptureEnabled && extras.isNotEmpty()) return extras[0]
        if (fotoCamara != null && extras.isEmpty()) fotoCamara = null
        return Intent.createChooser(elegir, "Elegir archivo").apply {
            if (extras.isNotEmpty()) putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toTypedArray())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Notificaciones.crearCanal(this)

        val contenedor = FrameLayout(this)
        contenedor.setBackgroundColor(Color.parseColor("#003D32"))
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#003D32"))
        contenedor.addView(
            web,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(contenedor)

        // Que la app no quede debajo de la barra de estado ni de los botones de Android.
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

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            web.loadUrl(Config.URL_INICIO)
        }

        // ¿Hay una APK nueva publicada en GitHub? (como mucho cada 6 h; pregunta antes de instalar)
        ActualizadorApk.revisar(this)

        // ¿Hay una versión nueva de la app en Apps Script? (lo revisa Android, no la página)
        RevisarApp.revisar(this, forzar = true)

        if (Build.VERSION.SDK_INT >= 33 && !Notificaciones.permitido(this)) {
            pedirPermisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWeb() {
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = 100
        }

        val cargador = WebViewAssetLoader.Builder()
            .addPathHandler("/", PaginasLocales(this))
            .build()

        web.addJavascriptInterface(PuenteJs(this), Config.PUENTE)

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                cargador.shouldInterceptRequest(request.url)
                    ?: CacheExterna.servir(this@MainActivity, request)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
                abrirAfuera(request.url)
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            // window.open(...) y links con target="_blank": se abren en el navegador o la app que corresponda.
            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message
            ): Boolean {
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
                        derivar(v, r.url)
                        return true
                    }

                    override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                        derivar(v, url?.let { Uri.parse(it) })
                    }
                }
                val transporte = resultMsg.obj as WebView.WebViewTransport
                transporte.webView = temporal
                resultMsg.sendToTarget()
                return true
            }

            // <input type="file"> (por ejemplo, subir imágenes desde administración).
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                archivosPendientes?.onReceiveValue(null)
                archivosPendientes = filePathCallback
                return try {
                    elegirArchivo.launch(intentElegir(fileChooserParams))
                    true
                } catch (e: Exception) {
                    // Si el selector propio falla, se usa el estándar de Android.
                    fotoCamara = null
                    try {
                        elegirArchivo.launch(fileChooserParams.createIntent())
                        true
                    } catch (e2: ActivityNotFoundException) {
                        archivosPendientes = null
                        false
                    }
                }
            }
        }
    }

    private fun abrirAfuera(url: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // No hay app para abrirlo: se ignora.
        }
    }

    override fun onResume() {
        super.onResume()
        RevisarApp.revisar(this)
    }

    fun recargar() {
        web.clearCache(false)
        web.loadUrl(Config.URL_INICIO)
    }

    fun aviso(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }

    /** Imprimir o guardar como PDF lo que se ve en la app. */
    fun imprimirPantalla(titulo: String) {
        val impresora = getSystemService(PrintManager::class.java) ?: return
        impresora.print(titulo, web.createPrintDocumentAdapter(titulo), PrintAttributes.Builder().build())
    }

    // Se guarda acá para que Android no la descarte antes de terminar de imprimir.
    private var webImpresion: WebView? = null

    /** Imprimir o guardar como PDF un reporte armado aparte (HTML completo). */
    fun imprimirHtml(html: String, titulo: String) {
        val impresora = getSystemService(PrintManager::class.java) ?: return
        val hoja = WebView(this)
        webImpresion = hoja
        hoja.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                impresora.print(titulo, view.createPrintDocumentAdapter(titulo), PrintAttributes.Builder().build())
            }
        }
        hoja.loadDataWithBaseURL(Config.URL_INICIO, html, "text/html", "utf-8", null)
    }

    fun ejecutarJs(codigo: String) {
        web.evaluateJavascript(codigo, null)
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
