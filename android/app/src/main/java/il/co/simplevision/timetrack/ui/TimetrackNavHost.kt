package il.co.simplevision.timetrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import il.co.simplevision.timetrack.data.AppPrefs
import il.co.simplevision.timetrack.data.TimeStore
import il.co.simplevision.timetrack.ui.screens.DayScreen
import il.co.simplevision.timetrack.ui.screens.MonthSummaryScreen
import il.co.simplevision.timetrack.ui.screens.ProjectDetailScreen
import il.co.simplevision.timetrack.ui.screens.SettingsScreen
import il.co.simplevision.timetrack.ui.screens.TimeListScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TimetrackNavHost(
    store: TimeStore,
    prefs: AppPrefs,
    hostActivity: FragmentActivity,
) {
    val nav = rememberNavController()
    val snap by store.state.collectAsState()
    val context = LocalContext.current

    // When a cloud file is configured, re-check it whenever we enter the app.
    LaunchedEffect(Unit) {
        // No-op; the Activity already triggers it. Keeping a hook here makes it easy
        // to extend behavior later without plumbing.
        @Suppress("UNUSED_VARIABLE")
        val unused = context
    }

    NavHost(navController = nav, startDestination = Routes.DAY) {
        composable(Routes.DAY) {
            DayScreen(
                store = store,
                prefs = prefs,
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenSummary = { monthRef ->
                    nav.navigate("${Routes.SUMMARY}?month=${encodeDate(monthRef)}")
                },
                onOpenList = { monthRef ->
                    nav.navigate("${Routes.LIST}?month=${encodeDate(monthRef)}")
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                store = store,
                prefs = prefs,
                hostActivity = hostActivity,
                onDone = { nav.popBackStack() },
                onOpenProject = { projectId ->
                    nav.navigate("${Routes.PROJECT}/$projectId")
                },
            )
        }

        composable(
            route = "${Routes.SUMMARY}?month={month}",
            arguments = listOf(navArgument("month") { type = NavType.StringType; defaultValue = "" }),
        ) { backStack ->
            val raw = backStack.arguments?.getString("month")
            MonthSummaryScreen(
                store = store,
                hostActivity = hostActivity,
                monthDate = parseDateOrToday(raw),
                onDone = { nav.popBackStack() },
            )
        }

        composable(
            route = "${Routes.LIST}?month={month}",
            arguments = listOf(navArgument("month") { type = NavType.StringType; defaultValue = "" }),
        ) { backStack ->
            val raw = backStack.arguments?.getString("month")
            TimeListScreen(
                store = store,
                referenceDate = parseDateOrToday(raw),
                onDone = { nav.popBackStack() },
            )
        }

        composable(
            route = "${Routes.PROJECT}/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { backStack ->
            val projectId = backStack.arguments?.getString("projectId") ?: ""
            val project = snap.projects.firstOrNull { it.id == projectId }
            ProjectDetailScreen(
                initialProject = project,
                onBack = { nav.popBackStack() },
                onSave = { updated ->
                    store.updateProject(updated)
                },
            )
        }
    }
}

private object Routes {
    const val DAY = "day"
    const val SETTINGS = "settings"
    const val SUMMARY = "summary"
    const val LIST = "list"
    const val PROJECT = "project"
}

private fun encodeDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}

private fun parseDateOrToday(raw: String?): LocalDate {
    if (raw.isNullOrBlank()) return LocalDate.now()
    return runCatching {
        LocalDate.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }.getOrElse { LocalDate.now() }
}

