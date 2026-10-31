package com.lumen.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

/**
 * Statistiche anonime di utilizzo verso il portale candelab.net/stats: UN "app_open" al giorno più alcune azioni
 * principali (solo il nome fisso dell'azione, es. "play_live"). Si inviano nome dell'app, versione, "android" e lingua
 * del dispositivo: nessun identificativo, nessun dato dell'account, nessun titolo o canale guardato.
 */
object AppStats {
    private const val ENDPOINT = "https://candelab.net/stats/v1/event"

    private const val MAX_PER_RUN = 40   // tetto agli invii per avvio dell'app
    private var sent = 0

    /** Azione principale (nome fisso a-z0-9_). Non blocca l'app e ignora ogni errore. */
    @Synchronized
    fun event(ctx: Context, name: String) {
        if (sent >= MAX_PER_RUN || !Regex("[a-z0-9_]{1,40}").matches(name)) return
        sent++
        send(ctx.applicationContext, name, false)
    }

    fun ping(ctx: Context) = send(ctx.applicationContext, "app_open", true)

    private fun send(app: Context, name: String, daily: Boolean) {
        thread(name = "stats", isDaemon = true) {
            try {
                val p = app.getSharedPreferences("stats", Context.MODE_PRIVATE)
                val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                val today = f.format(Date())
                if (daily && p.getString("last", "") == today) return@thread

                val lang = Locale.getDefault().language
                val o = JSONObject()
                    .put("app", "lumen")
                    .put("event", name)
                    .put("platform", "android")
                    .put("version", BuildConfig.VERSION_NAME)
                if (lang.length == 2) o.put("lang", lang)

                val c = URL(ENDPOINT).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 10_000
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(o.toString().toByteArray()) }
                if (c.responseCode == 204 && daily) p.edit().putString("last", today).apply()
                c.disconnect()
            } catch (_: Exception) {
                // le statistiche non devono mai disturbare l'app
            }
        }
    }
}
