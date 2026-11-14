package com.t24.stock

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Librerías y fuentes de internet que usa la app (Bootstrap, íconos, fuentes, jsPDF...).
 * La primera vez que se abren con internet se guardan en el celular; desde ahí se sirven
 * del celular, así la app abre igual de completa SIN conexión y más rápido.
 * Sólo para sitios de librerías públicas conocidos (nada de datos ni del servidor de la app).
 */
object CacheExterna {

    private val SITIOS = setOf(
        "cdn.jsdelivr.net", "cdnjs.cloudflare.com", "fonts.googleapis.com", "fonts.gstatic.com",
        "unpkg.com", "code.jquery.com", "stackpath.bootstrapcdn.com", "maxcdn.bootstrapcdn.com",
        "use.fontawesome.com", "ka-f.fontawesome.com", "cdn.sheetjs.com"
    )

    fun aplica(url: Uri): Boolean = url.scheme == "https" && url.host in SITIOS

    private fun carpeta(ctx: Context) = File(ctx.filesDir, "cdn").apply { mkdirs() }

    private fun clave(texto: String): String =
        MessageDigest.getInstance("SHA-1").digest(texto.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Devuelve la respuesta desde el celular (o la baja y la guarda). null = que el WebView se arregle solo. */
    fun servir(ctx: Context, pedido: WebResourceRequest): WebResourceResponse? {
        if (pedido.method != "GET" || !aplica(pedido.url)) return null
        val url = pedido.url.toString()
        val base = File(carpeta(ctx), clave(url))
        val tipoArchivo = File(base.path + ".tipo")
        if (!base.isFile || !tipoArchivo.isFile) {
            try {
                bajar(url, base, tipoArchivo, 0)
            } catch (e: Exception) {
                return null
            }
        }
        val tipoCompleto = tipoArchivo.readText().ifBlank { "application/octet-stream" }
        val mime = tipoCompleto.substringBefore(';').trim()
        val charset = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(tipoCompleto)?.groupValues?.get(1)?.trim()
        val r = WebResourceResponse(mime, charset, base.inputStream())
        r.responseHeaders = mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "max-age=31536000")
        return r
    }

    private fun bajar(url: String, destino: File, tipo: File, saltos: Int) {
        if (saltos > 5) throw IllegalStateException("Demasiadas redirecciones")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            val codigo = c.responseCode
            if (codigo in 300..399) {
                val sig = c.getHeaderField("Location") ?: throw IllegalStateException("Sin destino")
                return bajar(URL(URL(url), sig).toString(), destino, tipo, saltos + 1)
            }
            if (codigo !in 200..299) throw IllegalStateException("Respuesta $codigo")
            val tmp = File(destino.path + ".tmp")
            c.inputStream.use { e -> tmp.outputStream().use { e.copyTo(it) } }
            if (tmp.length() == 0L) throw IllegalStateException("Vacío")
            tipo.writeText(c.contentType ?: "application/octet-stream")
            if (destino.exists()) destino.delete()
            tmp.renameTo(destino)
        } finally {
            c.disconnect()
        }
    }
}
