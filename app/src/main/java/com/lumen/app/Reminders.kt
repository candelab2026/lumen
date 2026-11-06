package com.lumen.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat

/** Un programma della guida da ricordare: l'avviso arriva un minuto prima dell'inizio. */
data class Reminder(val chId: Int, val chName: String, val title: String, val start: Long) {
    val key get() = "$chId@$start"
}

/**
 * Promemoria salvati sul dispositivo e programmati con AlarmManager: funzionano anche ad app chiusa
 * e si riprogrammano dopo il riavvio. Con l'app aperta compare un dialogo, altrimenti una notifica.
 */
object Reminders {
    private const val LEAD = 60_000L
    private const val CHANNEL = "reminders"
    const val EXTRA_CH = "reminder_ch"

    /** Promemoria appena scattato con l'app in primo piano: MainActivity mostra il dialogo. */
    var fired by mutableStateOf<Reminder?>(null)

    /** Aggiornato da MainActivity in onStart/onStop. */
    @Volatile
    var foreground = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Reminder> =
        (prefs(ctx).getString("list", "") ?: "").split('\n').mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 4) null else Reminder(f[0].toIntOrNull() ?: return@mapNotNull null, f[1], f[2], f[3].toLongOrNull() ?: return@mapNotNull null)
        }

    private fun save(ctx: Context, l: List<Reminder>) {
        fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
        prefs(ctx).edit().putString("list", l.joinToString("\n") { "${it.chId}\t${clean(it.chName)}\t${clean(it.title)}\t${it.start}" }).apply()
    }

    fun has(ctx: Context, chId: Int, start: Long) = all(ctx).any { it.chId == chId && it.start == start }

    /** Aggiunge o toglie il promemoria; restituisce true se ora è attivo. */
    fun toggle(ctx: Context, r: Reminder): Boolean {
        val l = all(ctx)
        return if (l.any { it.key == r.key }) {
            save(ctx, l.filter { it.key != r.key })
            cancel(ctx, r)
            false
        } else {
            save(ctx, l.filter { it.start > System.currentTimeMillis() } + r)
            schedule(ctx, r)
            true
        }
    }

    /** Android 13+: le notifiche vanno chieste all'utente (una volta, al primo promemoria). */
    fun askPermission(ctx: Context) {
        val a = ctx as? android.app.Activity ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            a.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }

    /** Dopo il riavvio gli allarmi sono persi: si riprogrammano quelli futuri. */
    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        val l = all(ctx).filter { it.start > now - LEAD }
        save(ctx, l)
        l.forEach { schedule(ctx, it) }
    }

    private fun pending(ctx: Context, r: Reminder): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("ch", r.chId).putExtra("name", r.chName).putExtra("title", r.title).putExtra("start", r.start)
        return PendingIntent.getBroadcast(ctx, r.key.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun schedule(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val at = (r.start - LEAD).coerceAtLeast(System.currentTimeMillis() + 1_000)
        val pi = pending(ctx, r)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(ctx: Context, r: Reminder) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx, r))
    }

    internal fun fire(ctx: Context, r: Reminder) {
        save(ctx, all(ctx).filter { it.key != r.key })
        if (foreground) {
            fired = r
            return
        }
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.rem_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            ctx, r.key.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_CH, r.chId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(ctx.getString(R.string.rem_starting, r.title))
            .setContentText(r.chName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
        try {
            nm.notify(r.key.hashCode(), n)
        } catch (e: SecurityException) {
            // notifiche negate dall'utente: nulla da fare
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val r = Reminder(i.getIntExtra("ch", -1), i.getStringExtra("name") ?: "", i.getStringExtra("title") ?: "", i.getLongExtra("start", 0L))
        if (r.chId >= 0) Reminders.fire(ctx, r)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED || i.action == "android.intent.action.QUICKBOOT_POWERON") Reminders.rescheduleAll(ctx)
    }
}
