import SwiftUI

@main
struct TimetrackApp: App {
    @StateObject private var store = TimeStore()
    @StateObject private var appLock = AppLock()

    var body: some Scene {
        WindowGroup {
            if screenshotMode {
                ScreenshotRootView(viewName: screenshotViewName, referenceDate: screenshotDate)
                    .environmentObject(store)
                    .environmentObject(appLock)
            } else {
                ContentView()
                    .environmentObject(store)
                    .environmentObject(appLock)
            }
        }
    }

    private var screenshotMode: Bool {
        ProcessInfo.processInfo.environment["SCREENSHOT_MODE"] == "1"
    }

    private var screenshotViewName: String {
        ProcessInfo.processInfo.environment["SCREENSHOT_VIEW"] ?? "day"
    }

    private var screenshotDate: Date {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        if let raw = ProcessInfo.processInfo.environment["SCREENSHOT_DATE"],
           let date = formatter.date(from: raw) {
            return date
        }
        return Date()
    }
}
