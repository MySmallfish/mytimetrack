# MyTimetrack (Android)

Native Android clone of the iOS MyTimetrack app, implemented in Kotlin + Jetpack Compose.

## Open & Run
1. Open the `android/` folder in Android Studio.
2. Let Gradle sync (requires a JDK, and internet access to download dependencies the first time).
3. Run the `app` configuration on a device/emulator.

## Screenshot Mode (Demo Data)
This mirrors the iOS env-var based screenshot mode, but uses intent extras (or env vars if present):
- `SCREENSHOT_MODE=1`
- `SCREENSHOT_VIEW=day|summary|list|settings`
- `SCREENSHOT_DATE=yyyy-MM-dd` (optional)

Example:
```
adb shell am start -n il.co.simplevision.timetrack.debug/il.co.simplevision.timetrack.MainActivity \
  -e SCREENSHOT_MODE 1 \
  -e SCREENSHOT_VIEW summary \
  -e SCREENSHOT_DATE 2026-02-08
```
