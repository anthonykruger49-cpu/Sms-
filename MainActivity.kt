package com.example.net2sms

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    private lateinit var url: EditText
    private lateinit var key: EditText
    private lateinit var name: EditText
    private lateinit var interval: EditText
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)

        fun field(hint: String, value: String) = EditText(this).apply {
            this.hint = hint; setText(value); setSingleLine()
        }
        url = field("Server URL (https://example.com)", prefs.getString("url", "") ?: "")
        url.inputType = InputType.TYPE_TEXT_VARIATION_URI
        key = field("API key", prefs.getString("key", "") ?: "")
        name = field("Device name", prefs.getString("name", "phone-1") ?: "phone-1")
        interval = field("Poll interval (seconds)", prefs.getString("interval", "5") ?: "5")
        interval.inputType = InputType.TYPE_CLASS_NUMBER

        status = TextView(this).apply { textSize = 16f; setPadding(0, 24, 0, 8) }
        logView = TextView(this).apply { textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE }

        val start = Button(this).apply { text = "Start gateway" }
        val stop = Button(this).apply { text = "Stop gateway" }

        start.setOnClickListener {
            prefs.edit()
                .putString("url", url.text.toString().trim().trimEnd('/'))
                .putString("key", key.text.toString().trim())
                .putString("name", name.text.toString().trim())
                .putString("interval", interval.text.toString().trim())
                .apply()
            if (!hasPermissions()) { requestPerms(); return@setOnClickListener }
            startGateway()
        }
        stop.setOnClickListener { stopService(Intent(this, GatewayService::class.java)) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(url); addView(key); addView(name); addView(interval)
            addView(start); addView(stop); addView(status)
            addView(ScrollView(this@MainActivity).apply { addView(logView) },
                LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
    }

    private fun needed(): Array<String> {
        val l = mutableListOf(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l.toTypedArray()
    }

    private fun hasPermissions() = needed().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPerms() = requestPermissions(needed(), 1)

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        if (checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) startGateway()
        else Toast.makeText(this, "SMS permission is required", Toast.LENGTH_LONG).show()
    }

    private fun startGateway() {
        val i = Intent(this, GatewayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private val refresh = object : Runnable {
        override fun run() {
            status.text = if (GatewayService.active) "Status: RUNNING" else "Status: stopped"
            synchronized(GatewayService.logs) { logView.text = GatewayService.logs.joinToString("\n") }
            handler.postDelayed(this, 1000)
        }
    }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }
}
