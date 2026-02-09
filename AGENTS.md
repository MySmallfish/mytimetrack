# MyTimetrack app notes for future sessions

## Summary
- iOS SwiftUI time tracker (MyTimetrack) with a day timeline view, month summary, and list view.
- Android native clone lives under `android/` (Kotlin + Jetpack Compose).
- Day view shows 96 slots (15 minutes each) with 30-minute grid rows; drag selection assigns a project and optional label.
- Projects, rates, timesheet email settings, invoice details, and GreenInvoice credentials/test mode are managed in Settings; data is stored in UserDefaults.
- App opens directly into the day view and requires Face ID/device authentication.
- Brand accent color (onboarding Continue button + iOS icon accent): `Color(red: 0.25, green: 0.62, blue: 0.60)` (`OnboardingTheme.accent` in `Timetrack/ContentView.swift`, `Palette.accent` in `.tmp_generate_assets.swift`).
- Marketing kit lives under `marketing/`. Website lives under `website/` (static HTML/CSS/JS).

## Key files
- `Timetrack/ContentView.swift`: UI (lock screen, day header, time grid, picker, settings).
- `Timetrack/TimeStore.swift`: Models, storage, palette, Face ID lock logic.
- `Timetrack/MailView.swift`: Mail composer wrapper for sending CSV timesheets.
- `Timetrack/GreenInvoiceClient.swift`: GreenInvoice API client.
- `Timetrack/TimetrackApp.swift`: App entry point wiring environment objects.
- `Timetrack/Info.plist`: Face ID usage description and basic app metadata.
- `Timetrack.xcodeproj/project.pbxproj`: Xcode project configuration.
- `android/app/src/main/java/il/co/simplevision/timetrack/data/TimeStore.kt`: Android models + storage + business logic.
- `android/app/src/main/java/il/co/simplevision/timetrack/ui/screens/DayScreen.kt`: Android day view.
- `android/app/src/main/java/il/co/simplevision/timetrack/ui/components/TimeGridView.kt`: Android timeline grid (selection + move).
- `android/app/src/main/java/il/co/simplevision/timetrack/ui/screens/MonthSummaryScreen.kt`: Android month summary + proforma export.
- `android/app/src/main/java/il/co/simplevision/timetrack/util/ProformaPdf.kt`: Android proforma PDF generator.

## Data model
- `Project`: `id`, `name`, `colorHex`, `rate`, timesheet email fields, invoice header/details.
- `DayLog`: `slots` array with 96 entries, each storing optional `Project.id`, plus optional labels.
- Day key format: `yyyy-MM-dd` (local timezone) using `TimeStore.dayKey`.

## Core behavior
- Drag selection in the grid snaps to 30-minute rows (manual edit pickers still allow 15-minute slots); on end, a project picker sheet appears.
- Month summary includes a summary view and a list view with a date range, CSV export, and invoice creation.
- GreenInvoice test mode shows JSON preview and can send to the sandbox base URL in `Timetrack/GreenInvoiceClient.swift` (currently `https://sandbox.d.greeninvoice.co.il/api/v1`).
- If there are no projects, an alert prompts the user to open Settings.
- App locks when backgrounded; unlocks on activation using `LAContext`.
- Screenshot mode: set `SCREENSHOT_MODE=1` and `SCREENSHOT_VIEW=day|summary|list|settings` (optional `SCREENSHOT_DATE=yyyy-MM-dd`) to render seeded demo data.

## Build and run (Xcode UI)
1. Open `Timetrack.xcodeproj` in Xcode.
2. Set your signing team under the app target's Signing & Capabilities.
3. Select your connected iPhone as the run destination and press Run.

## Build and deploy (command line)
- List devices:
  - `xcrun xctrace list devices`
- Build and install (replace placeholders):
  - `xcodebuild -project Timetrack.xcodeproj -target Timetrack -destination 'id=YOUR_DEVICE_UDID' -configuration Debug -allowProvisioningUpdates DEVELOPMENT_TEAM=YOUR_TEAM_ID`.
- Auto-deploy requirement:
  - After every change, build and deploy to the connected iPhone (UDID `00008150-000D04523C84401C`).
  - Use:
    - `xcodebuild -scheme Timetrack -configuration Debug -destination 'platform=iOS,id=00008150-000D04523C84401C' -derivedDataPath ./DerivedData -allowProvisioningUpdates -allowProvisioningDeviceRegistration build`
    - `xcrun devicectl device install app --device 00008150-000D04523C84401C DerivedData/Build/Products/Debug-iphoneos/Timetrack.app`
    - `xcrun devicectl device process launch --device 00008150-000D04523C84401C il.co.simplevision.timetrack`
  - Assistant note: always run the build/install/launch sequence after changes without asking.

## TestFlight upload (Xcode CLI)
- Keep `MARKETING_VERSION = 1.0`, bump `CURRENT_PROJECT_VERSION` for each upload.
- Archive + upload:
  - `xcodebuild -scheme Timetrack -configuration Release -destination 'generic/platform=iOS' -archivePath build/Timetrack.xcarchive -derivedDataPath ./DerivedData-Archive -allowProvisioningUpdates archive`
  - `xcodebuild -exportArchive -archivePath build/Timetrack.xcarchive -exportOptionsPlist build/ExportOptionsUpload.plist -exportPath build/export-upload -allowProvisioningUpdates`
- iCloud Sync note:
  - If TestFlight shows iCloud as "Unavailable", the uploaded build was likely signed without iCloud entitlements; confirm `SystemCapabilities` includes `com.apple.iCloud` in `Timetrack.xcodeproj/project.pbxproj` and re-archive/re-upload.

## Android build (Android Studio)
1. Open the `android/` folder in Android Studio.
2. Let Gradle sync (first sync needs internet to download dependencies).
3. Run the `app` module on a device/emulator.


## Known gaps / TODO
- No server sync yet (local only).
- GreenInvoice payload schema may need adjustment based on API requirements.
