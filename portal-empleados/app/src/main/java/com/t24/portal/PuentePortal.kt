package com.t24.portal

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Funciones de Android para la web del portal: window.T24Portal */
class PuentePortal(private val act: MainActivity) {

    /** Guarda un archivo (por ejemplo, el Excel de legajos) en Descargas y lo abre. */
    @JavascriptInterface
    fun guardarArchivo(nombre: String, base64: String, mime: String) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val limpio = nombre.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val uri: Uri = if (Build.VERSION.SDK_INT >= 29) {
                val valores = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, limpio)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Portal T24")
                }
                val u = act.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                    ?: throw IllegalStateException("No se pudo crear el archivo")
                act.contentResolver.openOutputStream(u)?.use { it.write(bytes) }
                u
            } else {
                val carpeta = File(act.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Portal T24").apply { mkdirs() }
                val f = File(carpeta, limpio)
                f.writeBytes(bytes)
                FileProvider.getUriForFile(act, "${act.packageName}.archivos", f)
            }
            act.runOnUiThread {
                Toast.makeText(act, "Guardado en Descargas/Portal T24: $limpio", Toast.LENGTH_LONG).show()
                try {
                    act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    // No hay app para abrirlo: queda guardado igual.
                }
            }
        } catch (e: Exception) {
            act.runOnUiThread { Toast.makeText(act, "No se pudo guardar: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }
}
