package com.cousinlockdown

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat

class LockdownService : Service() {
    private var overlay: android.view.View? = null
    private var wm: WindowManager? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        running = true
        val channel = "cousin_lockdown"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(channel, "Cousin Lock Down", NotificationManager.IMPORTANCE_LOW))
        val notification: Notification = NotificationCompat.Builder(this, channel)
            .setContentTitle("Cousin Lock Down is active")
            .setContentText("Tap the app to authenticate before exiting.")
            .setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build()
        startForeground(41, notification)
        showOverlay()
        return START_STICKY
    }
    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this) || overlay != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFFFCE4EC.toInt())
            addView(Button(this@LockdownService).apply {
                text = "Cousin Lock Down is active"
                isAllCaps = false
            })
            addView(Button(this@LockdownService).apply {
                text = "Open app to exit"
                setOnClickListener {
                    val i = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (i != null) startActivity(i)
                }
            })
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP }
        try { wm?.addView(panel, params); overlay = panel } catch (_: Exception) { }
    }
    override fun onDestroy() {
        running = false
        try { overlay?.let { wm?.removeView(it) } } catch (_: Exception) { }
        overlay = null
        super.onDestroy()
    }
    companion object { @Volatile var running: Boolean = false }
}
