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
 * Statistiche anonime di utilizzo: al massimo UN conteggio al giorno ("app_open") verso il portale candelab.net/stats.
 * Si inviano solo nome dell'app, versione, "android" e lingua del dispositivo: nessun identificativo,
 * nessun dato dell'account o dei contenuti guardati.
 */
object AppStats {
    private const val ENDPOINT = "https://candelab.net/stats/v1/event"

    fun ping(ctx: Context) {
        val app = ctx.applicationContext
        thread(name = "stats", isDaemon = true) {
            try {
                val p = app.getSharedPreferences("stats", Context.MODE_PRIVATE)
                val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                val today = f.format(Date())
                if (p.getString("last", "") == today) return@thread

                val lang = Locale.getDefault().language
                val o = JSONObject()
                    .put("app", "lumen")
                    .put("event", "app_open")
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
                if (c.responseCode == 204) p.edit().putString("last", today).apply()
                c.disconnect()
            } catch (_: Exception) {
                // le statistiche non devono mai disturbare l'app
            }
        }
    }
}
