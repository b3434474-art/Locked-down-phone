package com.cousinlockdown

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class MainActivity : FragmentActivity() {
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 24, 28, 24)
        }
        layout.addView(TextView(this).apply {
            text = "🔒 Cousin Lock Down\n\nChoose the apps your cousin can use, then start lockdown. This is best-effort protection, not a replacement for Android's own security."
            textSize = 19f
        })
        layout.addView(button("Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        layout.addView(button("Enable Usage Access") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        layout.addView(button("Start Lockdown") { startLockdown() })
        layout.addView(button("Exit Lockdown (fingerprint or phone PIN)") { authenticateAndStop() })
        layout.addView(button("Open allowed-app settings") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        setContentView(layout)
    }

    private fun startLockdown() {
        val overlayAllowed = Settings.canDrawOverlays(this)
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val usageAllowed = ops.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
        if (!overlayAllowed || !usageAllowed) {
            startActivity(Intent(if (!overlayAllowed) Settings.ACTION_MANAGE_OVERLAY_PERMISSION else Settings.ACTION_USAGE_ACCESS_SETTINGS,
                Uri.parse("package:$packageName")))
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, LockdownService::class.java).setAction("START"))
    }

    private fun authenticateAndStop() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                stopService(Intent(this@MainActivity, LockdownService::class.java))
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Exit Cousin Lock Down")
            .setSubtitle("Authenticate to stop lockdown")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}
