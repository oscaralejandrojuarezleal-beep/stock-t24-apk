package com.t24.stock

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Llamadas a internet desde Kotlin (actualización de la app y chequeo nocturno).
 * Apps Script responde con una redirección: la seguimos a mano para que funcione siempre.
 */
object Red {

    fun get(url: String): String = pedir(url, "GET", null, 0)

    fun post(url: String, cuerpo: String): String = pedir(url, "POST", cuerpo, 0)

    /** Descarga un archivo (por ejemplo la APK nueva) siguiendo redirecciones. */
    fun descargar(urlTexto: String, destino: File, saltos: Int = 0) {
        if (saltos > 8) throw IOException("Demasiadas redirecciones")
        val conexion = URL(urlTexto).openConnection() as HttpURLConnection
        try {
            conexion.instanceFollowRedirects = false
            conexion.connectTimeout = 20_000
            conexion.readTimeout = 120_000
            val codigo = conexion.responseCode
            if (codigo in 300..399) {
                val siguiente = conexion.getHeaderField("Location") ?: throw IOException("Redirección sin destino")
                return descargar(URL(URL(urlTexto), siguiente).toString(), destino, saltos + 1)
            }
            if (codigo !in 200..299) throw IOException("El servidor respondió $codigo")
            val temporal = File(destino.parentFile, destino.name + ".tmp")
            conexion.inputStream.use { entrada -> temporal.outputStream().use { entrada.copyTo(it) } }
            if (destino.exists()) destino.delete()
            if (!temporal.renameTo(destino)) throw IOException("No se pudo guardar la descarga")
        } finally {
            conexion.disconnect()
        }
    }

    private fun pedir(urlTexto: String, metodo: String, cuerpo: String?, saltos: Int): String {
        if (saltos > 5) throw IOException("Demasiadas redirecciones")
        val conexion = URL(urlTexto).openConnection() as HttpURLConnection
        try {
            conexion.instanceFollowRedirects = false
            conexion.connectTimeout = 20_000
            conexion.readTimeout = 90_000
            conexion.requestMethod = metodo
            if (cuerpo != null) {
                conexion.doOutput = true
                conexion.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
                conexion.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            }
            val codigo = conexion.responseCode
            if (codigo in 300..399) {
                val destino = conexion.getHeaderField("Location")
                    ?: throw IOException("Redirección sin destino")
                val siguiente = URL(URL(urlTexto), destino).toString()
                return pedir(siguiente, "GET", null, saltos + 1)
            }
            if (codigo !in 200..299) throw IOException("El servidor respondió $codigo")
            return conexion.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conexion.disconnect()
        }
    }
}
