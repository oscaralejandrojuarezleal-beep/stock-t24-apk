package com.t24.stock

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.webkit.WebViewAssetLoader

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

    private val elegirArchivo =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
            val uris = WebChromeClient.FileChooserParams.parseResult(resultado.resultCode, resultado.data)
            archivosPendientes?.onReceiveValue(uris)
            archivosPendientes = null
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
                    elegirArchivo.launch(fileChooserParams.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    archivosPendientes = null
                    false
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
