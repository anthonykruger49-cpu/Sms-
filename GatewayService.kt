package com.example.net2sms

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class GatewayService : Service() {

    companion object {
        val logs = ArrayDeque<String>()
        @Volatile var active = false
        private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        fun log(s: String) = synchronized(logs) {
            logs.addLast("${fmt.format(Date())} $s")
            while (logs.size > 100) logs.removeFirst()
        }
    }

    @Volatile private var running = false
    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val chId = "gateway"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(chId, "SMS Gateway", NotificationManager.IMPORTANCE_LOW))
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, chId) else Notification.Builder(this)
        val n = b.setContentTitle("Net2SMS running")
            .setContentText("Waiting for messages to send")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE))
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(1, n)

        if (!running) {
            running = true; active = true
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "net2sms:poll").apply { acquire() }
            worker = Thread { loop() }.also { it.start() }
            log("Gateway started")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false; active = false
        worker?.interrupt()
        wakeLock?.let { if (it.isHeld) it.release() }
        log("Gateway stopped")
        super.onDestroy()
    }

    private fun loop() {
        while (running) {
            val p = getSharedPreferences("cfg", MODE_PRIVATE)
            val base = p.getString("url", "") ?: ""
            val key = p.getString("key", "") ?: ""
            val dev = URLEncoder.encode(p.getString("name", "phone") ?: "phone", "UTF-8")
            val secs = (p.getString("interval", "5")?.toIntOrNull() ?: 5).coerceIn(2, 300)
            try {
                val resp = http("GET", "$base/api/device/pending?device=$dev&limit=10", key, null)
                val arr = JSONObject(resp).optJSONArray("messages")
                if (arr != null) for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val id = m.getString("id")
                    var status = "sent"; var err = ""
                    try {
                        sendSms(m.getString("to"), m.getString("body"))
                        log("SMS sent to ${m.getString("to")}")
                    } catch (e: Exception) {
                        status = "failed"; err = e.message ?: "error"
                        log("SMS FAILED: $err")
                    }
                    val rep = JSONObject().put("id", id).put("status", status).put("error", err)
                    http("POST", "$base/api/device/report", key, rep.toString())
                    Thread.sleep(1500) // throttle to avoid carrier / OS spam limits
                }
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) {
                log("Poll error: ${e.message}")
            }
            try { Thread.sleep(secs * 1000L) } catch (e: InterruptedException) { return }
        }
    }

    @Suppress("DEPRECATION")
    private fun sendSms(to: String, body: String) {
        val sm = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java)
                 else SmsManager.getDefault()
        val parts = sm.divideMessage(body)
        if (parts.size > 1) sm.sendMultipartTextMessage(to, null, parts, null, null)
        else sm.sendTextMessage(to, null, body, null, null)
    }

    private fun http(method: String, url: String, key: String, body: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("X-API-Key", key)
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code >= 400) throw IOException("HTTP $code")
        return text
    }
}
