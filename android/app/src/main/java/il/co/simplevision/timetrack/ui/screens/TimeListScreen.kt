package il.co.simplevision.timetrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import il.co.simplevision.timetrack.data.TimeEntry
import il.co.simplevision.timetrack.data.TimeStore
import il.co.simplevision.timetrack.ui.components.EntryEditorSheet
import il.co.simplevision.timetrack.ui.components.EnumPicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeListScreen(
    store: TimeStore,
    referenceDate: LocalDate,
    onDone: () -> Unit,
    selectedProjectId: String? = null,
) {
    val snap by store.state.collectAsState()

    val (startOfMonth, endOfMonth) = remember(referenceDate) { store.monthRange(referenceDate) }
    var selectedId by remember { mutableStateOf<String?>(selectedProjectId) }
    var rangeStart by remember { mutableStateOf(startOfMonth) }
    var rangeEnd by remember { mutableStateOf(endOfMonth) }
    var editingEntry by remember { mutableStateOf<TimeEntry?>(null) }

    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    fun normalizeRange() {
        if (rangeStart.isAfter(rangeEnd)) rangeEnd = rangeStart
    }

    LaunchedEffect(rangeStart, rangeEnd) { normalizeRange() }

    val entryList = remember(snap.lastUpdatedEpochMillis, selectedId, rangeStart, rangeEnd) {
        store.entries(rangeStart, rangeEnd, selectedId)
    }

    val totalMinutes = remember(snap.lastUpdatedEpochMillis, selectedId, rangeStart, rangeEnd) {
        store.totalMinutes(rangeStart, rangeEnd, selectedId)
    }

    val totalAmount = remember(entryList, totalMinutes, selectedId) {
        if (selectedId != null) {
            val rate = store.projectForId(selectedId)?.rate ?: 0.0
            (totalMinutes.toDouble() / 60.0) * rate
        } else {
            entryList.sumOf { entry ->
                val minutes = (entry.endIndex - entry.startIndex + 1) * TimeStore.SLOT_MINUTES
                val rate = store.projectForId(entry.projectId)?.rate ?: 0.0
                (minutes.toDouble() / 60.0) * rate
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Time List", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDone) { Text("Done") }
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SectionHeader("Filters")
                EnumPicker(
                    label = "Project",
                    value = selectedId,
                    values = listOf(null) + snap.projects.map { it.id },
                    valueLabel = { id -> if (id == null) "All Projects" else (store.projectForId(id)?.name ?: "Unknown") },
                    onChange = { selectedId = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = { pickingStart = true }) { Text("From: ${rangeStart.format(DAY_FORMAT)}") }
                    TextButton(onClick = { pickingEnd = true }) { Text("To: ${rangeEnd.format(DAY_FORMAT)}") }
                }
            }

            item {
                SectionHeader("Totals")
                KeyValueRow("Total Hours", hoursText(totalMinutes))
                KeyValueRow("Total Amount", currencyText(totalAmount))
            }

            item { SectionHeader("Entries") }
            if (entryList.isEmpty()) {
                item {
                    Text(
                        "No entries in this range.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(entryList) { entry ->
                    EntryRow(store = store, entry = entry, onClick = { editingEntry = entry })
                    Divider()
                }
            }

            item { Spacer(modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }

    if (pickingStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = rangeStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingStart = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        rangeStart = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    pickingStart = false
                }) { Text("Done") }
            },
        ) { DatePicker(state = state) }
    }

    if (pickingEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = rangeEnd.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        rangeEnd = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    pickingEnd = false
                }) { Text("Done") }
            },
        ) { DatePicker(state = state) }
    }

    val entry = editingEntry
    if (entry != null) {
        val entryDay = LocalDate.parse(entry.dayKey, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        EntryEditorSheet(
            projects = snap.projects,
            existingSlots = store.slotsForDay(entryDay),
            initialRange = entry.startIndex..entry.endIndex,
            initialProjectId = entry.projectId,
            initialLabel = entry.label,
            onDismiss = { editingEntry = null },
            onSave = { projectId, range, label ->
                store.setEntry(projectId, label, range, entryDay)
                editingEntry = null
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EntryRow(store: TimeStore, entry: TimeEntry, onClick: () -> Unit) {
    val minutes = (entry.endIndex - entry.startIndex + 1) * TimeStore.SLOT_MINUTES
    val hours = minutes.toDouble() / 60.0
    val project = store.projectForId(entry.projectId)
    val rate = project?.rate ?: 0.0

    val day = LocalDate.parse(entry.dayKey, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val title = "${day.format(ENTRY_DAY_FORMAT)} · ${TimeStore.timeString(entry.startIndex)}–${TimeStore.timeString(entry.endIndex + 1)}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(project?.name ?: "Unknown", style = MaterialTheme.typography.bodySmall)
            val label = entry.label
            if (!label.isNullOrBlank()) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(String.format(Locale.US, "%.2f h", hours), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(currencyText(hours * rate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun hoursText(minutes: Int): String {
    val hours = minutes.toDouble() / 60.0
    return String.format(Locale.US, "%.1f h", hours)
}

private fun currencyText(amount: Double): String = String.format(Locale.US, "$%.2f", amount)

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withLocale(Locale.US)
private val ENTRY_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d").withLocale(Locale.US)
