package com.t24.portal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Avisos al celular: cada 15 minutos (aunque la app esté cerrada) pregunta al portal si hay
 * notificaciones nuevas (cambios de horario, solicitudes aprobadas, capacitaciones...) y las muestra.
 */
object Avisos {
    private const val CANAL = "avisos_portal"
    private const val TRABAJO = "avisos_portal"

    fun crearCanal(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel(CANAL, "Avisos del Portal", NotificationManager.IMPORTANCE_HIGH)
            c.description = "Cambios de horario, solicitudes, capacitaciones y novedades"
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(c)
        }
    }

    fun registrar(ctx: Context, token: String) {
        val p = ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        val nuevo = p.getString("token", "") != token
        val ed = p.edit().putString("token", token)
        if (nuevo && p.getLong("desde", 0L) == 0L) ed.putLong("desde", System.currentTimeMillis() - 3_600_000L)
        ed.apply()
        programar(ctx)
    }

    fun cerrar(ctx: Context) {
        ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE).edit().remove("token").apply()
        WorkManager.getInstance(ctx).cancelUniqueWork(TRABAJO)
    }

    fun programar(ctx: Context) {
        val token = ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE).getString("token", "") ?: ""
        if (token.isEmpty()) return
        val pedido = PeriodicWorkRequestBuilder<AvisosWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(TRABAJO, ExistingPeriodicWorkPolicy.KEEP, pedido)
    }

    fun mostrar(ctx: Context, id: Int, titulo: String, mensaje: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val abrir = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
        }
    }
}

class AvisosWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val p = applicationContext.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        val token = p.getString("token", "") ?: ""
        if (token.isEmpty()) return Result.success()
        return try {
            val desde = p.getLong("desde", System.currentTimeMillis() - 3_600_000L)
            val url = URL(Config.URL_PORTAL + "?api=avisos&t=" + URLEncoder.encode(token, "UTF-8") + "&desde=" + desde)
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 20000; c.readTimeout = 30000; c.instanceFollowRedirects = true
            val texto = c.inputStream.bufferedReader().use { it.readText() }
            val r = JSONObject(texto)
            if (!r.optBoolean("ok")) {
                val err = r.optString("error")
                if (Regex("sesi|token|válida|valida|venci", RegexOption.IGNORE_CASE).containsMatchIn(err)) Avisos.cerrar(applicationContext)
                return Result.success()
            }
            val lista = r.optJSONArray("avisos")
            var maximo = desde
            Avisos.crearCanal(applicationContext)
            if (lista != null) for (i in 0 until lista.length()) {
                val a = lista.getJSONObject(i)
                val creado = a.optLong("creado", 0L)
                if (creado > maximo) maximo = creado
                Avisos.mostrar(applicationContext, (a.optString("id").hashCode()), a.optString("titulo", "Portal T24"), a.optString("mensaje", ""))
            }
            p.edit().putLong("desde", maxOf(maximo, desde)).apply()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
