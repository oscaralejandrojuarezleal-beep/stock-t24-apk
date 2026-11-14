package com.t24.stock

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

/** Notificaciones generadas por el propio celular (sin Firebase ni servidores externos). */
object Notificaciones {

    private const val CANAL = "novedades"

    fun crearCanal(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL, "Avisos", NotificationManager.IMPORTANCE_DEFAULT)
            canal.description = "Capacitaciones, devocionales, cumpleaños y actualizaciones"
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
        }
    }

    fun permitido(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun mostrar(ctx: Context, titulo: String, texto: String, id: Int = 1001) {
        if (!permitido(ctx)) return
        crearCanal(ctx)
        val abrir = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val alTocar = PendingIntent.getActivity(
            ctx, id, abrir, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val aviso = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setAutoCancel(true)
            .setContentIntent(alTocar)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, aviso)
        } catch (e: SecurityException) {
            // Permiso retirado por la persona: no se muestra y listo.
        }
    }
}
