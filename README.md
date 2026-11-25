## Nexus Charge 🔋

A simple Android battery dashboard built with Kotlin and Jetpack Compose (Material 3).

**Shows:** battery percentage, charging / not charging, status, charging source (AC, USB, wireless), full or not, temperature (°C), voltage (V), health and technology. Current and charge counter appear only if your phone reports them; otherwise the app says "Not supported" or "Unavailable".

**Privacy:** no permissions, no internet, no data collected or stored.

### Download the APK (no computer needed)
1. Open the **Actions** tab of this repository.
2. Tap the latest green ✅ **Android Build** run.
3. Scroll to **Artifacts** and download **nexus-charge-debug-apk** (a .zip).
4. Unzip it and open `app-debug.apk`. Allow "install unknown apps" when asked.

To start a build by hand: Actions → Android Build → **Run workflow**.

### Build on a computer
- Open the folder in Android Studio and press Run, or
- run `./gradlew assembleDebug` (JDK 17). The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

Requirements: Android 8.0 (API 26) or newer.
