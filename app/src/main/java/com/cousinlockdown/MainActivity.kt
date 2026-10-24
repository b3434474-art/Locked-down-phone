package com.cousinlockdown

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.content.pm.PackageManager
import android.app.AlertDialog
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
        layout.addView(button("Choose Allowed Apps") { showAllowedAppsPicker() })
        setContentView(layout)
    }

    private fun showAllowedAppsPicker() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = packageManager.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        if (activities.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("No launchable apps were found on this phone.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val labels = activities.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        val prefs = getSharedPreferences("cousin_lockdown", Context.MODE_PRIVATE)
        val saved = prefs.getStringSet("allowed_apps", emptySet())?.toSet() ?: emptySet()
        val checked = BooleanArray(activities.size) { activities[it].activityInfo.packageName in saved }

        AlertDialog.Builder(this)
            .setTitle("Choose apps for your cousin")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                val chosen = activities.indices
                    .filter { checked[it] }
                    .map { activities[it].activityInfo.packageName }
                    .toSet()
                prefs.edit().putStringSet("allowed_apps", chosen).apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
