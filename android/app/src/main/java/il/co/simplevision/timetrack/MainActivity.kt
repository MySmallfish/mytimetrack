package il.co.simplevision.timetrack

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import il.co.simplevision.timetrack.data.AppLock
import il.co.simplevision.timetrack.data.TimeStore
import il.co.simplevision.timetrack.ui.TimetrackRoot
import il.co.simplevision.timetrack.ui.theme.MyTimetrackTheme

class MainActivity : AppCompatActivity() {
    private val store by lazy { TimeStore(applicationContext) }
    private val appLock by lazy { AppLock() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dev ergonomics: keep the screen awake while the app is visible in the emulator/device.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val screenshotMode = intent?.getStringExtra("SCREENSHOT_MODE") == "1" ||
            intent?.getBooleanExtra("SCREENSHOT_MODE", false) == true ||
            (System.getenv("SCREENSHOT_MODE") == "1")
        val screenshotView = intent?.getStringExtra("SCREENSHOT_VIEW")
            ?: System.getenv("SCREENSHOT_VIEW")
            ?: "day"
        val screenshotDate = intent?.getStringExtra("SCREENSHOT_DATE")
            ?: System.getenv("SCREENSHOT_DATE")

        setContent {
            MyTimetrackTheme {
                TimetrackRoot(
                    store = store,
                    appLock = appLock,
                    screenshotMode = screenshotMode,
                    screenshotView = screenshotView,
                    screenshotDate = screenshotDate,
                    hostActivity = this,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.requestUnlock(this)
        store.loadFromCloudIfAvailable()
    }

    override fun onStop() {
        super.onStop()
        appLock.lock()
    }
}
