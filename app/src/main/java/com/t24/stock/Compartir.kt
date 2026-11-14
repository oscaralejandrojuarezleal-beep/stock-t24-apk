package com.t24.stock

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

/**
 * Compartir (WhatsApp, Instagram, etc.) y guardar archivos que arma la app,
 * por ejemplo las imágenes de versículos.
 */
object Compartir {

    private fun autoridad(ctx: Context) = ctx.packageName + ".archivos"

    private fun carpetaTemporal(ctx: Context): File =
        File(ctx.cacheDir, "compartidos").apply {
            mkdirs()
            // Limpieza: lo compartido hace más de un día ya no se necesita.
            listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 86_400_000L) it.delete() }
        }

    private fun nombreSeguro(nombre: String): String =
        nombre.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80).ifBlank { "archivo" }

    /** datosJson: {title, text, url, files:[{name, type, b64}]} */
    fun compartir(ctx: Context, datosJson: String) {
        val datos = JSONObject(datosJson)
        val texto = listOf(datos.optString("text"), datos.optString("url"))
            .filter { it.isNotBlank() }
            .joinToString("\n")
        val archivos = datos.optJSONArray("files")
        val uris = ArrayList<Uri>()
        val tipos = mutableSetOf<String>()

        if (archivos != null) {
            for (i in 0 until archivos.length()) {
                val f = archivos.getJSONObject(i)
                val bytes = Base64.decode(f.optString("b64"), Base64.DEFAULT)
                val archivo = File(carpetaTemporal(ctx), System.currentTimeMillis().toString() + "_" + nombreSeguro(f.optString("name", "imagen.png")))
                archivo.writeBytes(bytes)
                uris.add(FileProvider.getUriForFile(ctx, autoridad(ctx), archivo))
                tipos.add(f.optString("type", "*/*").ifBlank { "*/*" })
            }
        }

        val intent = when {
            uris.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            uris.size == 1 -> Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
            else -> Intent(Intent.ACTION_SEND)
        }
        intent.type = when {
            uris.isEmpty() -> "text/plain"
            tipos.size == 1 -> tipos.first()
            else -> "*/*"
        }
        if (uris.isNotEmpty()) {
            val clip = ClipData.newRawUri("", uris[0])
            for (k in 1 until uris.size) clip.addItem(ClipData.Item(uris[k]))
            intent.clipData = clip
        }
        if (texto.isNotBlank()) intent.putExtra(Intent.EXTRA_TEXT, texto)
        val titulo = datos.optString("title")
        if (titulo.isNotBlank()) intent.putExtra(Intent.EXTRA_SUBJECT, titulo)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val selector = Intent.createChooser(intent, "Compartir")
        selector.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(selector)
    }

    /**
     * Guarda en el celular: imágenes en Imágenes/Stock T24, el resto en Descargas.
     * En Android viejos (antes de 10) abre "Compartir" para que la persona elija dónde guardarlo.
     * Devuelve un texto para mostrar.
     */
    fun guardar(ctx: Context, nombre: String, tipo: String, b64: String): String {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        val limpio = nombreSeguro(nombre)
        if (Build.VERSION.SDK_INT < 29) {
            val datos = JSONObject()
                .put("files", org.json.JSONArray().put(JSONObject().put("name", limpio).put("type", tipo).put("b64", b64)))
            compartir(ctx, datos.toString())
            return "Elegí dónde guardarlo"
        }
        val esImagen = tipo.startsWith("image/")
        val coleccion = if (esImagen) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val carpeta = if (esImagen) Environment.DIRECTORY_PICTURES + "/Stock T24" else Environment.DIRECTORY_DOWNLOADS
        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, limpio)
            put(MediaStore.MediaColumns.MIME_TYPE, tipo)
            put(MediaStore.MediaColumns.RELATIVE_PATH, carpeta)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(coleccion, valores) ?: throw IllegalStateException("No se pudo crear el archivo")
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        valores.clear()
        valores.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, valores, null, null)
        return if (esImagen) "Imagen guardada en la galería" else "Archivo guardado en Descargas"
    }
}
