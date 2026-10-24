package com.cousinlockdown

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class MainActivity : FragmentActivity() {
    private val prefs by lazy { getSharedPreferences("cousin_lockdown", Context.MODE_PRIVATE) }
    private var editing = false
    private var kidMode = false
    private var chosen = mutableSetOf<String>()
    private var available: List<ResolveInfo> = emptyList()
    private lateinit var root: FrameLayout

    private val tileColors = intArrayOf(0xFFF51B29.toInt(), 0xFFFF8500.toInt(), 0xFFFFE600.toInt(), 0xFF00D82E.toInt(), 0xFF183CFF.toInt(), 0xFF7415E8.toInt(), 0xFFE6009B.toInt())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chosen = (prefs.getStringSet("allowed_apps", emptySet()) ?: emptySet()).toMutableSet()
        loadApps()
        showHome()
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        available = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun bg(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun text(value: String, size: Float, color: Int = Color.BLACK) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; setOnClickListener { action() }
    }

    private fun showHome() {
        editing = false
        root = FrameLayout(this)
        root.setBackgroundColor(Color.WHITE)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            background = bg(0xFFFFC13B.toInt(), 38f)
        }
        val heading = text(if (kidMode) "Have fun!" else "Cousin Lock Down", 23f).apply {
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }
        page.addView(heading)
        val grid = GridLayout(this).apply { columnCount = 3; alignmentMode = GridLayout.ALIGN_BOUNDS }
        val apps = available.filter { it.activityInfo.packageName in chosen }
        if (apps.isEmpty()) {
            val hint = text(if (kidMode) "No apps selected yet" else "Choose the apps your cousin can use", 16f).apply {
                gravity = Gravity.CENTER
                setPadding(14, 28, 14, 28)
            }
            page.addView(hint)
        } else {
            apps.forEachIndexed { index, info ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(7, 10, 7, 10)
                    background = bg(tileColors[index % tileColors.size], 22f)
                    val icon = ImageView(this@MainActivity).apply {
                        setImageDrawable(info.loadIcon(packageManager))
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    addView(icon, LinearLayout.LayoutParams(56, 56))
                    addView(TextView(this@MainActivity).apply {
                        text = info.loadLabel(packageManager).toString()
                        textSize = 12f; gravity = Gravity.CENTER
                        setTextColor(if (index % tileColors.size == 2) Color.BLACK else Color.WHITE)
                        maxLines = 2
                    })
                    setOnClickListener {
                        if (kidMode) {
                            val launch = packageManager.getLaunchIntentForPackage(info.activityInfo.packageName)
                            if (launch != null) startActivity(launch)
                        } else {
                            openAppPicker()
                        }
                    }
                }
                val lp = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 116
                    columnSpec = GridLayout.spec(index % 3, 1f)
                    setMargins(5, 5, 5, 5)
                }
                grid.addView(tile, lp)
            }
            page.addView(grid)
        }

        if (!kidMode) {
            page.addView(button("Choose Apps") { openAppPicker() })
            page.addView(button("Start Kid Mode") {
                if (chosen.isEmpty()) openAppPicker() else { kidMode = true; showHome(); enterScreenPinning() }
            })
            page.addView(button("Allow overlay permission (optional)") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            })
        }

        val scroll = ScrollView(this).apply { addView(page) }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val lock = TextView(this).apply {
            text = "🔓"
            textSize = 30f
            gravity = Gravity.CENTER
            background = bg(Color.WHITE, 40f)
            elevation = 8f
            setOnClickListener {
                if (kidMode) authenticateAndExit() else openAppPicker()
            }
            contentDescription = if (kidMode) "Unlock Cousin Lock Down" else "Choose allowed apps"
        }
        val lockLp = FrameLayout.LayoutParams(64, 64, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, 14, 14)
        }
        root.addView(lock, lockLp)
        setContentView(root)
    }

    private fun openAppPicker() {
        editing = true
        root = FrameLayout(this)
        root.setBackgroundColor(0xFFFFC13B.toInt())
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 12)
        }
        page.addView(text("Choose apps for your cousin", 23f).apply { typeface = Typeface.DEFAULT_BOLD })
        page.addView(text("Check the apps you want to show on the kid screen.", 15f))
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val checks = mutableMapOf<String, CheckBox>()
        available.forEach { info ->
            val pkg = info.activityInfo.packageName
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(10, 6, 10, 6)
                background = bg(0xFFFFD56B.toInt(), 16f)
            }
            row.addView(ImageView(this).apply { setImageDrawable(info.loadIcon(packageManager)) }, LinearLayout.LayoutParams(44, 44))
            val check = CheckBox(this).apply {
                text = info.loadLabel(packageManager).toString()
                textSize = 16f
                isChecked = pkg in chosen
            }
            checks[pkg] = check
            row.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4, 0, 4) })
        }
        scroll.addView(list)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(button("Save Apps") {
            chosen = checks.filterValues { it.isChecked }.keys.toMutableSet()
            prefs.edit().putStringSet("allowed_apps", chosen.toSet()).apply()
            kidMode = false
            showHome()
        })
        page.addView(button("Cancel") { showHome() })
        root.addView(page)
        setContentView(root)
    }

    private fun enterScreenPinning() {\n        // This invokes Android screen pinning where available. Full multi-app kiosk\n        // mode requires device-owner provisioning and cannot be enabled by an ordinary app.\n        try { startLockTask() } catch (_: Exception) { }\n    }\n\n    private fun authenticateAndExit() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // If the phone has no supported lock credential, don't silently exit kid mode.
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                kidMode = false
                showHome()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Cousin Lock Down")
            .setSubtitle("Use your fingerprint or phone screen lock")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    @Deprecated("Deprecated by Android")
    override fun onBackPressed() {
        if (kidMode) authenticateAndExit() else if (editing) showHome() else super.onBackPressed()
    }
}
