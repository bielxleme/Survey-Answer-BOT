package com.researchagent.autofill.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.app.Notification
import com.researchagent.autofill.R
import com.researchagent.autofill.core.Intervention
import com.researchagent.autofill.ui.InterventionActivity

/** Som + notificação de intervenção (Seção 15). */
object Notifier {
    private const val CHANNEL_ID = "intervention_sound_v1"
    private const val CHANNEL_SILENT = "intervention_silent_v1"
    private const val NOTIFICATION_ID = 4101

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(CHANNEL_ID, "Ação necessária", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos quando o agente precisa de você (CAPTCHA, login, dado ausente…)"
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            )
        }
        nm.createNotificationChannel(ch)
        val silent = NotificationChannel(CHANNEL_SILENT, "Ação necessária (sem som)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Mesmos avisos, sem som (quando o som está desligado nos Ajustes)"
            setSound(null, null)
        }
        nm.createNotificationChannel(silent)
    }

    fun showIntervention(context: Context, i: Intervention, playSound: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // sem permissão de notificação: ainda assim toca o som (Seção 15)
            if (playSound) playChime(context)
            return
        }
        val intent = Intent(context, InterventionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = i.question?.text?.let { "\"${it.take(120)}\"" } ?: i.message
        val n = Notification.Builder(context, if (playSound) CHANNEL_ID else CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle("⚠ AÇÃO NECESSÁRIA — ${i.reason.title}")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(i.message))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) { }
    }

    fun cancelIntervention(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun playChime(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context.applicationContext, uri)?.play()
        } catch (_: Exception) { }
    }
}
