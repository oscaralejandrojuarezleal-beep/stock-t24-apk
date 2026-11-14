package com.t24.stock

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Sirve la app al WebView desde el propio celular:
 * 1) primero la versión descargada (files/www),
 * 2) si no hay, la página de carga incluida en la APK (assets/www).
 */
class PaginasLocales(private val ctx: Context) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        val limpio = path.trimStart('/').ifEmpty { "index.html" }
        if (limpio.contains("..")) return null

        val descargado = File(Actualizador.carpeta(ctx), limpio)
        val datos: InputStream = if (descargado.isFile) {
            descargado.inputStream()
        } else {
            try {
                ctx.assets.open("www/$limpio")
            } catch (e: IOException) {
                return null
            }
        }
        val respuesta = WebResourceResponse(tipo(limpio), "utf-8", datos)
        respuesta.responseHeaders = mapOf("Cache-Control" to "no-cache")
        return respuesta
    }

    private fun tipo(nombre: String): String = when (nombre.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "js" -> "application/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "svg" -> "image/svg+xml"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}
