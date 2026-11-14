package com.t24.stock

import android.app.AlertDialog
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Revisa desde Android (sin depender de la página) si hay una versión nueva de la app en Apps Script.
 * Si la hay, la baja en segundo plano y pregunta si aplicarla ahora. Si se elige "Después",
 * queda aplicada la próxima vez que se abra la app.
 * Así, aunque la página tuviera un error, la app siempre se puede arreglar sola.
 */
object RevisarApp {

    private const val CADA_MS = 15 * 60 * 1000L
    @Volatile private var enCurso = false
    private var ultima = 0L

    fun revisar(act: MainActivity, forzar: Boolean = false) {
        val ahora = System.currentTimeMillis()
        if (enCurso || (!forzar && ahora - ultima < CADA_MS)) return
        if (!Actualizador.tieneApp(act)) return // la primera descarga la hace la pantalla de inicio
        enCurso = true
        ultima = ahora
        thread(name = "revisar-app") {
            try {
                val pedido = JSONObject().put("fn", Config.FN_HUELLA).put("args", JSONArray()).toString()
                val r = JSONObject(Red.post(Config.API_URL, pedido)).optJSONObject("r")
                val huella = r?.optString("huella", "") ?: ""
                if (huella.isNotBlank() && huella != Actualizador.buildLocal(act)) {
                    Actualizador.descargar(act)
                    act.runOnUiThread { preguntar(act) }
                }
            } catch (e: Exception) {
                // Sin internet o servidor ocupado: se reintenta más tarde.
            } finally {
                enCurso = false
            }
        }
    }

    private fun preguntar(act: MainActivity) {
        if (act.isFinishing || act.isDestroyed) return
        AlertDialog.Builder(act)
            .setTitle("Versión nueva de la app")
            .setMessage("Ya se descargó una versión nueva. ¿La aplicamos ahora? (Si estás cargando algo, elegí Después: se aplica sola la próxima vez que abras la app.)")
            .setPositiveButton("Aplicar ahora") { _, _ -> act.recargar() }
            .setNegativeButton("Después", null)
            .show()
    }
}
