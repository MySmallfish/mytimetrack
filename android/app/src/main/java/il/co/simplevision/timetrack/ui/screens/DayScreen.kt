package il.co.simplevision.timetrack.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.List as ListIcon
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import il.co.simplevision.timetrack.data.AppPrefs
import il.co.simplevision.timetrack.data.TimeStore
import il.co.simplevision.timetrack.ui.components.EntryEditorSheet
import il.co.simplevision.timetrack.ui.components.LegendBar
import il.co.simplevision.timetrack.ui.components.TimeGridView
import il.co.simplevision.timetrack.ui.components.WalkthroughTargets
import il.co.simplevision.timetrack.ui.components.WalkthroughOverlay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    store: TimeStore,
    prefs: AppPrefs,
    onOpenSettings: () -> Unit,
    onOpenSummary: (LocalDate) -> Unit,
    onOpenList: (LocalDate) -> Unit,
) {
    val snap by store.state.collectAsState()
    val hasSeenOnboarding by prefs.hasSeenOnboarding.collectAsState()
    val hasSeenWalkthrough by prefs.hasSeenWalkthrough.collectAsState()
    val debugRunWalkthrough by prefs.debugRunWalkthrough.collectAsState()

    var day by remember { mutableStateOf(LocalDate.now()) }
    var selectionRange by remember { mutableStateOf<IntRange?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNoProjectsAlert by remember { mutableStateOf(false) }
    var showWalkthrough by remember { mutableStateOf(false) }

    var settingsRect by remember { mutableStateOf<Rect?>(null) }
    var summaryRect by remember { mutableStateOf<Rect?>(null) }
    var gridRect by remember { mutableStateOf<Rect?>(null) }

    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }

    val summaryMonthDate = remember(day) { summaryMonthDate() }

    fun previousDay() { day = day.minusDays(1) }
    fun nextDay() { day = day.plusDays(1) }

    // Walkthrough scheduling mirrors iOS logic.
    LaunchedEffect(hasSeenOnboarding, hasSeenWalkthrough, debugRunWalkthrough) {
        val envForce = System.getenv("DEBUG_RUN_WALKTHROUGH") == "1"
        val force = debugRunWalkthrough || envForce
        if (force || (hasSeenOnboarding && !hasSeenWalkthrough)) {
            kotlinx.coroutines.delay(350)
            if (!showWalkthrough && selectionRange == null) {
                showWalkthrough = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            LegendBar(
                projects = snap.projects,
                totals = store.dayTotals(day),
                totalMinutes = store.dayTotalMinutes(day),
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var mode: SwipeMode? = null
                    var dx = 0f
                    var dy = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (mode == SwipeMode.HORIZONTAL &&
                                abs(dx) > swipeThresholdPx &&
                                abs(dx) > abs(dy)
                            ) {
                                if (dx < 0) nextDay() else previousDay()
                            }
                            break
                        }

                        dx = change.position.x - start.x
                        dy = change.position.y - start.y

                        if (mode == null) {
                            if (abs(dx) < 6f && abs(dy) < 6f) continue
                            mode = if (abs(dx) > abs(dy)) SwipeMode.HORIZONTAL else SwipeMode.VERTICAL
                        }

                        if (mode == SwipeMode.HORIZONTAL) {
                            change.consume()
                        } else {
                            // Let vertical gestures (grid selection/move + scroll) win.
                            break
                        }
                    }
                }
            },
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(modifier = Modifier.weight(1f)) {
                        IconButton(onClick = ::previousDay) {
                            Icon(imageVector = Icons.Filled.ChevronLeft, contentDescription = "Previous day")
                        }
                        IconButton(onClick = ::nextDay) {
                            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = "Next day")
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = { showDatePicker = true }) {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Text(dayTitle(day), style = MaterialTheme.typography.titleMedium)
                            Text(daySubtitle(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(modifier = Modifier.weight(1f)) {
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { onOpenList(summaryMonthDate) }) {
                            Icon(imageVector = Icons.Filled.ListIcon, contentDescription = "List")
                        }
                        IconButton(
                            onClick = { onOpenSummary(summaryMonthDate) },
                            modifier = Modifier.onGloballyPositioned { coords -> summaryRect = coords.boundsInRoot() },
                        ) {
                            Icon(imageVector = Icons.Filled.BarChart, contentDescription = "Summary")
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.onGloballyPositioned { coords -> settingsRect = coords.boundsInRoot() },
                        ) {
                            Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                }

                Divider()

                TimeGridView(
                    day = day,
                    store = store,
                    onRangeSelected = { range ->
                        if (snap.projects.isEmpty()) {
                            showNoProjectsAlert = true
                        } else {
                            selectionRange = range
                        }
                    },
                    onGridPositioned = { rect -> gridRect = rect },
                    modifier = Modifier.weight(1f),
                )
            }

            WalkthroughOverlay(
                isPresented = showWalkthrough,
                targets = WalkthroughTargets(settings = settingsRect, grid = gridRect, summary = summaryRect),
                onDismiss = {
                    prefs.setHasSeenWalkthrough(true)
                    prefs.setDebugRunWalkthrough(false)
                    showWalkthrough = false
                },
            )
        }
    }

    if (showDatePicker) {
        val initialMillis = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        day = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { day = LocalDate.now() }) { Text("Today") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showNoProjectsAlert) {
        AlertDialog(
            onDismissRequest = { showNoProjectsAlert = false },
            title = { Text("No projects yet") },
            text = { Text("Add your active projects in Settings before tracking time.") },
            confirmButton = {
                TextButton(onClick = {
                    showNoProjectsAlert = false
                    onOpenSettings()
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showNoProjectsAlert = false }) { Text("Cancel") }
            },
        )
    }

    val range = selectionRange
    if (range != null) {
        val defaults = selectionDefaults(store, day, range)
        EntryEditorSheet(
            projects = snap.projects,
            existingSlots = store.slotsForDay(day),
            initialRange = range,
            initialProjectId = defaults.first,
            initialLabel = defaults.second,
            onDismiss = { selectionRange = null },
            onSave = { projectId, updatedRange, label ->
                store.setEntry(projectId, label, updatedRange, day)
                selectionRange = null
            },
        )
    }
}

private enum class SwipeMode { HORIZONTAL, VERTICAL }

private fun dayTitle(day: LocalDate): String {
    val today = LocalDate.now()
    if (day == today) return "Today"
    return day.format(DateTimeFormatter.ofPattern("EEE").withLocale(Locale.getDefault()))
}

private fun daySubtitle(day: LocalDate): String {
    return day.format(DateTimeFormatter.ofPattern("MMM d, yyyy").withLocale(Locale.getDefault()))
}

private fun summaryMonthDate(): LocalDate {
    val today = LocalDate.now()
    return if (today.dayOfMonth < 10) today.minusMonths(1) else today
}

private fun selectionDefaults(store: TimeStore, day: LocalDate, range: IntRange): Pair<String?, String?> {
    val slots = store.slotsForDay(day)
    val labels = store.labelsForDay(day)

    val slotValues = range.mapNotNull { idx -> slots.getOrNull(idx) }
    val labelValues = range.mapNotNull { idx -> labels.getOrNull(idx) }

    val uniqueProjects = slotValues.toSet()
    val hasNilSlots = range.any { slots.getOrNull(it) == null }
    val projectId = if (uniqueProjects.size == 1 && !hasNilSlots) uniqueProjects.firstOrNull() else null

    val uniqueLabels = labelValues.toSet()
    val hasNilLabels = range.any { labels.getOrNull(it) == null }
    val label = if (uniqueLabels.size == 1 && !hasNilLabels) uniqueLabels.firstOrNull() else null

    val lastSelected = store.state.value.lastSelectedProjectId?.takeIf { store.projectForId(it) != null }
    return (projectId ?: lastSelected) to label
}
