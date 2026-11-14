package com.t24.stock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Actualización "por aire": baja la app completa (pantallas + código) desde Apps Script
 * y la guarda en el celular. No hace falta reinstalar la APK.
 *
 * Se pide por la misma puerta que el resto (doPost): {"fn":"T24_apiAppApk"} -> {ok, r:{ok, build, html}}.
 */
object Actualizador {

    fun carpeta(ctx: Context): File = File(ctx.filesDir, "www").apply { mkdirs() }

    fun tieneApp(ctx: Context): Boolean = File(carpeta(ctx), "index.html").isFile

    fun buildLocal(ctx: Context): String =
        ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE).getString("build", "") ?: ""

    /** Descarga y reemplaza la app. Devuelve el número de build instalado. Lanza error si falla. */
    fun descargar(ctx: Context): String {
        if (!Config.API_URL.contains("/exec")) {
            throw IOException("Falta configurar API_URL en Config.kt")
        }
        val pedido = JSONObject().put("fn", Config.FN_APP).put("args", JSONArray()).toString()
        val texto = Red.post(Config.API_URL, pedido)
        val respuesta = try {
            JSONObject(texto)
        } catch (e: Exception) {
            throw IOException("El servidor no respondió bien (¿falta importar api-rest-apk-servidor o la nueva implementación?)")
        }
        if (!respuesta.optBoolean("ok")) throw IOException(respuesta.optString("error", "Error del servidor"))
        val json = respuesta.optJSONObject("r") ?: throw IOException("Respuesta vacía del servidor")
        if (!json.optBoolean("ok")) throw IOException(json.optString("error", "Error al armar la app"))
        val html = json.optString("html", "")
        val build = json.optString("build", "")
        if (html.length < 2000 || !html.contains("<html", ignoreCase = true)) {
            throw IOException("La app llegó incompleta, probá de nuevo")
        }

        val dir = carpeta(ctx)
        val temporal = File(dir, "index.html.tmp")
        val destino = File(dir, "index.html")
        temporal.writeText(html, Charsets.UTF_8)
        if (destino.exists() && !destino.delete()) throw IOException("No se pudo reemplazar la versión anterior")
        if (!temporal.renameTo(destino)) throw IOException("No se pudo guardar la app")

        ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
            .edit().putString("build", build).apply()
        return build
    }
}
