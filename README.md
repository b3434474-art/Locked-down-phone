# Cousin Lock Down

Android app project targeting Android 8+ (tested build target Android 16 / API 36).

## Build APK on GitHub using your phone
1. Open the **Actions** tab and select **Build Cousin Lock Down APK**.
2. Tap **Run workflow** (or push a commit to main to trigger it automatically).
3. When it finishes successfully, open the workflow run and download the **Cousin-Lock-Down-debug-APK** artifact.
4. Extract the downloaded ZIP to find app-debug.apk and install it.

## Important limitations
This is an early prototype. The overlay is not a secure kiosk and does not reliably block every app or Android system surface. It does not currently provide a true configurable allowed-app launcher, robust UsageStats enforcement, or purchase controls. Android may allow Quick Settings, notifications, emergency features, or Settings to bypass an ordinary app. Test carefully before relying on it to protect your phone. Do not use it to lock yourself out of your own device.
