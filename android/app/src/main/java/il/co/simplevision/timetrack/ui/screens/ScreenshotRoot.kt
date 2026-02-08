package il.co.simplevision.timetrack.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import il.co.simplevision.timetrack.data.AppPrefs
import il.co.simplevision.timetrack.data.TimeStore
import java.time.LocalDate

@Composable
fun ScreenshotRoot(
    store: TimeStore,
    viewName: String,
    referenceDate: LocalDate,
    hostActivity: FragmentActivity,
) {
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!seeded) {
            store.seedSampleData(referenceDate)
            seeded = true
        }
    }

    val prefs = remember { AppPrefs(hostActivity.applicationContext) }

    when (viewName.lowercase()) {
        "summary" -> MonthSummaryScreen(
            store = store,
            hostActivity = hostActivity,
            monthDate = referenceDate,
            onDone = {},
        )
        "list" -> TimeListScreen(
            store = store,
            referenceDate = referenceDate,
            onDone = {},
            selectedProjectId = store.state.value.projects.firstOrNull()?.id,
        )
        "settings" -> SettingsScreen(
            store = store,
            prefs = prefs,
            hostActivity = hostActivity,
            onDone = {},
            onOpenProject = {},
        )
        else -> DayScreen(
            store = store,
            prefs = prefs,
            onOpenSettings = {},
            onOpenSummary = {},
            onOpenList = {},
        )
    }
}
