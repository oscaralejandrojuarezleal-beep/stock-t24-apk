package com.t24.stock

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

/**
 * Actualización de la parte Android (la APK) desde GitHub, gratis.
 *
 * GitHub compila cada cambio y publica en "Releases" dos archivos:
 *   - version.json  -> {versionCode, versionName, apk, notas}
 *   - Stock-T24.apk
 * Al abrir la app (como mucho cada 6 h) se revisa version.json. Si hay una versión más nueva,
 * aparece un cartel: "Nueva versión de la app → Instalar / Después". Nada se instala sin permiso.
 */
object ActualizadorApk {

    private const val CADA_MS = 6 * 60 * 60 * 1000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)

    private fun versionInstalada(ctx: Context): Long = try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    } catch (e: Exception) {
        0L
    }

    /** Revisa en segundo plano. Si hay versión nueva, muestra el cartel en la pantalla. */
    fun revisar(act: MainActivity, forzar: Boolean = false) {
        val ahora = System.currentTimeMillis()
        val ultima = prefs(act).getLong("apk_ultima_revision", 0L)
        if (!forzar && ahora - ultima < CADA_MS) return
        Thread {
            try {
                val texto = Red.get("https://github.com/${Config.GITHUB_REPO}/releases/latest/download/version.json")
                val info = JSONObject(texto)
                prefs(act).edit().putLong("apk_ultima_revision", ahora).apply()
                val nueva = info.optLong("versionCode", 0L)
                val apk = info.optString("apk", "")
                if (nueva > versionInstalada(act) && apk.startsWith("https://github.com/")) {
                    act.runOnUiThread {
                        if (!act.isFinishing) ofrecer(act, info.optString("versionName", ""), info.optString("notas", ""), apk)
                    }
                }
            } catch (e: Exception) {
                // Sin internet o sin publicaciones todavía: se vuelve a intentar más tarde.
            }
        }.start()
    }

    private fun ofrecer(act: MainActivity, version: String, notas: String, apk: String) {
        val texto = buildString {
            append("Hay una versión nueva de la app")
            if (version.isNotBlank()) append(" (").append(version).append(")")
            append(".\n\nTus datos y descargas se mantienen.")
            if (notas.isNotBlank()) append("\n\nNovedades: ").append(notas.take(300))
        }
        AlertDialog.Builder(act)
            .setTitle("⬆️ Actualización disponible")
            .setMessage(texto)
            .setPositiveButton("Instalar") { _, _ -> descargarEInstalar(act, apk) }
            .setNegativeButton("Después", null)
            .show()
    }

    private fun descargarEInstalar(act: MainActivity, apk: String) {
        if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
            act.aviso("Permití instalar actualizaciones de esta app y tocá Instalar de nuevo")
            prefs(act).edit().putLong("apk_ultima_revision", 0L).apply()
            try {
                act.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.packageName))
                )
            } catch (e: Exception) {
                act.aviso("Activá 'Instalar apps desconocidas' para esta app en Ajustes")
            }
            return
        }
        act.aviso("Descargando la actualización…")
        Thread {
            try {
                val carpeta = File(act.cacheDir, "apk").apply { mkdirs() }
                carpeta.listFiles()?.forEach { it.delete() }
                val destino = File(carpeta, Config.ARCHIVO_APK)
                Red.descargar(apk, destino)
                val uri = FileProvider.getUriForFile(act, act.packageName + ".archivos", destino)
                val instalar = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                act.runOnUiThread { act.startActivity(instalar) }
            } catch (e: Exception) {
                act.runOnUiThread { act.aviso("No se pudo descargar: " + (e.message ?: "error") + ". Probá más tarde.") }
            }
        }.start()
    }
}
