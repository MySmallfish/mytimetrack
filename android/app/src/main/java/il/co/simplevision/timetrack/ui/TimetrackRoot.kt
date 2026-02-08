package il.co.simplevision.timetrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import il.co.simplevision.timetrack.data.AppLock
import il.co.simplevision.timetrack.data.AppPrefs
import il.co.simplevision.timetrack.data.TimeStore
import il.co.simplevision.timetrack.ui.screens.LockScreen
import il.co.simplevision.timetrack.ui.screens.OnboardingFlow
import il.co.simplevision.timetrack.ui.screens.ScreenshotRoot
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TimetrackRoot(
    store: TimeStore,
    appLock: AppLock,
    screenshotMode: Boolean,
    screenshotView: String,
    screenshotDate: String?,
    hostActivity: FragmentActivity,
) {
    val prefs = remember { AppPrefs(hostActivity.applicationContext) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (screenshotMode) {
            ScreenshotRoot(
                store = store,
                viewName = screenshotView,
                referenceDate = parseDateOrToday(screenshotDate),
                hostActivity = hostActivity,
            )
            return@Surface
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (appLock.isUnlocked) {
                TimetrackNavHost(store = store, prefs = prefs, hostActivity = hostActivity)
            } else {
                LockScreen(
                    onUnlock = { appLock.unlock(hostActivity) },
                    onAppearRequestUnlock = { appLock.requestUnlock(hostActivity) },
                )
            }

            OnboardingCoordinator(
                store = store,
                prefs = prefs,
                isUnlocked = appLock.isUnlocked,
            )
        }
    }
}

@Composable
private fun OnboardingCoordinator(
    store: TimeStore,
    prefs: AppPrefs,
    isUnlocked: Boolean,
) {
    val snap by store.state.collectAsState()
    val hasSeenOnboarding by prefs.hasSeenOnboarding.collectAsState()
    val debugRunOnboarding by prefs.debugRunOnboarding.collectAsState()

    var hasConsumedEnvOnboarding by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(false) }
    var isScheduling by remember { mutableStateOf(false) }

    LaunchedEffect(isUnlocked, hasSeenOnboarding, debugRunOnboarding, snap.projects.size) {
        if (showOnboarding || isScheduling) return@LaunchedEffect

        val envForce = System.getenv("DEBUG_RUN_ONBOARDING") == "1"
        val force = debugRunOnboarding || (envForce && !hasConsumedEnvOnboarding)

        if (!force) {
            // Avoid showing onboarding to users who already have data (e.g. after an update).
            if (!hasSeenOnboarding && snap.projects.isNotEmpty()) {
                prefs.setHasSeenOnboarding(true)
                prefs.setHasSeenWalkthrough(true)
            }
        }

        val shouldShowNormally = !hasSeenOnboarding && snap.projects.isEmpty()
        if (isUnlocked && (force || shouldShowNormally)) {
            isScheduling = true
            kotlinx.coroutines.delay(180)
            isScheduling = false
            if (!isUnlocked) return@LaunchedEffect
            if (showOnboarding) return@LaunchedEffect
            showOnboarding = true
            if (debugRunOnboarding) prefs.setDebugRunOnboarding(false)
            if (envForce) hasConsumedEnvOnboarding = true
        }
    }

    if (showOnboarding) {
        BackHandler(enabled = true) { /* disable back */ }
        OnboardingFlow(
            onFinish = {
                prefs.setHasSeenOnboarding(true)
                prefs.setDebugRunOnboarding(false)
                showOnboarding = false
            },
        )
    }
}

private fun parseDateOrToday(raw: String?): LocalDate {
    if (raw.isNullOrBlank()) return LocalDate.now()
    return runCatching {
        LocalDate.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }.getOrElse { LocalDate.now() }
}
