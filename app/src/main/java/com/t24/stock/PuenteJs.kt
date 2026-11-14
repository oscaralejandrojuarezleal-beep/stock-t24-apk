package com.t24.stock

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * Funciones de Android que la app web puede usar como  window.T24Android.xxx()
 * El WebView sólo navega la app local; los links externos se abren en el navegador.
 * Ninguna función acá expone datos personales ni permite descargar desde otro lugar.
 */
class PuenteJs(private val act: MainActivity) {

    @JavascriptInterface
    fun apiUrl(): String = Config.API_URL

    @JavascriptInterface
    fun versionApk(): String = try {
        act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    /** Huella de la app descargada (para saber si hay versión nueva). */
    @JavascriptInterface
    fun buildLocal(): String = Actualizador.buildLocal(act)

    /** true: Android ya revisa solo si hay versión nueva (la página no necesita hacerlo). */
    @JavascriptInterface
    fun revisaSolo(): Boolean = true

    /** Descarga la versión nueva de la app y recarga. Avisa errores con window.T24_APK_error(msg). */
    @JavascriptInterface
    fun actualizarApp() {
        Thread {
            try {
                Actualizador.descargar(act)
                act.runOnUiThread { act.recargar() }
            } catch (e: Exception) {
                val msg = JSONObject.quote(e.message ?: "Error desconocido")
                act.runOnUiThread { act.ejecutarJs("window.T24_APK_error && window.T24_APK_error($msg)") }
            }
        }.start()
    }

    /** navigator.share(...) de la app: abre el menú de Android para compartir (WhatsApp, etc.). */
    @JavascriptInterface
    fun compartir(datosJson: String?) {
        act.runOnUiThread {
            try {
                Compartir.compartir(act, datosJson ?: "{}")
            } catch (e: Exception) {
                act.aviso("No se pudo compartir: " + (e.message ?: "error"))
            }
        }
    }

    /** Descargas de la app (<a download>): guarda en la galería o en Descargas. */
    @JavascriptInterface
    fun guardarArchivo(nombre: String?, tipo: String?, b64: String?) {
        Thread {
            val mensaje = try {
                Compartir.guardar(act, nombre ?: "archivo", tipo ?: "application/octet-stream", b64 ?: "")
            } catch (e: Exception) {
                "No se pudo guardar: " + (e.message ?: "error")
            }
            act.runOnUiThread { act.aviso(mensaje) }
        }.start()
    }

    /** window.print(): imprimir o "Guardar como PDF" la pantalla actual. */
    @JavascriptInterface
    fun imprimir(titulo: String?) {
        act.runOnUiThread { act.imprimirPantalla(titulo ?: Config.NOMBRE) }
    }

    /** Reportes que se arman en una ventana aparte y se imprimen. */
    @JavascriptInterface
    fun imprimirHtml(html: String?, titulo: String?) {
        act.runOnUiThread { act.imprimirHtml(html ?: "", titulo ?: Config.NOMBRE) }
    }

    @JavascriptInterface
    fun notificar(titulo: String?, texto: String?) {
        Notificaciones.mostrar(act, titulo ?: Config.NOMBRE, texto ?: "", (System.currentTimeMillis() % 100000).toInt())
    }

    @JavascriptInterface
    fun notificacionesPermitidas(): Boolean = Notificaciones.permitido(act)

    @JavascriptInterface
    fun hayInternet(): Boolean {
        val cm = act.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @JavascriptInterface
    fun esWifi(): Boolean {
        val cm = act.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
