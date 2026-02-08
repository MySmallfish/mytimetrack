package il.co.simplevision.timetrack.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimeStore(appContext: Context) {
    private val context = appContext.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val localFile = File(context.filesDir, "TimetrackStore.json")

    private val _state = MutableStateFlow(loadLocal())
    val state: StateFlow<TimeStoreSnapshot> = _state.asStateFlow()

    private var saveJob: Job? = null
    @Volatile private var isApplyingCloudUpdate: Boolean = false

    init {
        // Best-effort: bring any configured cloud file in at startup.
        loadFromCloudIfAvailable()
    }

    fun slotsForDay(day: LocalDate): List<String?> {
        val key = dayKey(day)
        val snap = state.value
        val log = snap.logs[key]
        val slots = if (log != null && log.slots.size == SLOTS_PER_DAY) {
            log.slots
        } else {
            List(SLOTS_PER_DAY) { null }
        }
        val validIds = snap.projects.map { it.id }.toHashSet()
        return slots.map { id -> if (id != null && validIds.contains(id)) id else null }
    }

    fun labelsForDay(day: LocalDate): List<String?> {
        val key = dayKey(day)
        val log = state.value.logs[key]
        val labels = if (log != null && log.labels.size == SLOTS_PER_DAY) {
            log.labels
        } else {
            List(SLOTS_PER_DAY) { null }
        }
        return labels
    }

    fun setEntry(projectId: String?, label: String?, range: IntRange, day: LocalDate) {
        val key = dayKey(day)
        val normalizedLabel = label?.trim()?.takeIf { it.isNotEmpty() }

        mutate { snap ->
            val existing = snap.logs[key]
            val slotsMutable = (existing?.slots?.takeIf { it.size == SLOTS_PER_DAY }
                ?: List(SLOTS_PER_DAY) { null }).toMutableList()
            val labelsMutable = (existing?.labels?.takeIf { it.size == SLOTS_PER_DAY }
                ?: List(SLOTS_PER_DAY) { null }).toMutableList()

            for (index in range) {
                if (index < 0 || index >= SLOTS_PER_DAY) continue
                slotsMutable[index] = projectId
                labelsMutable[index] = if (projectId == null) null else normalizedLabel
            }

            val updatedLogs = snap.logs.toMutableMap()
            updatedLogs[key] = DayLog(slots = slotsMutable, labels = labelsMutable)

            snap.copy(
                logs = updatedLogs,
                lastSelectedProjectId = projectId ?: snap.lastSelectedProjectId,
            )
        }
    }

    fun projectForId(id: String?): Project? {
        if (id.isNullOrBlank()) return null
        return state.value.projects.firstOrNull { it.id == id }
    }

    fun addProject() {
        val snap = state.value
        val color = PALETTE[snap.projects.size % PALETTE.size]
        val project = Project(name = "New Project", colorHex = color)
        mutate { it.copy(projects = it.projects + project) }
    }

    fun removeProject(projectId: String) {
        mutate { snap ->
            val updated = snap.projects.filterNot { it.id == projectId }
            val lastSelected = snap.lastSelectedProjectId?.takeIf { id -> updated.any { it.id == id } }
            snap.copy(projects = updated, lastSelectedProjectId = lastSelected)
        }
    }

    fun updateProject(updated: Project) {
        mutate { snap ->
            val projects = snap.projects.map { if (it.id == updated.id) updated else it }
            val lastSelected = snap.lastSelectedProjectId?.takeIf { id -> projects.any { it.id == id } }
            snap.copy(projects = projects, lastSelectedProjectId = lastSelected)
        }
    }

    fun dayTotals(day: LocalDate): Map<String, Int> {
        val totals = linkedMapOf<String, Int>()
        for (slot in slotsForDay(day)) {
            val id = slot ?: continue
            totals[id] = (totals[id] ?: 0) + SLOT_MINUTES
        }
        return totals
    }

    fun dayTotalMinutes(day: LocalDate): Int = dayTotals(day).values.sum()

    fun monthTotals(monthDate: LocalDate): Map<String, Int> {
        val totals = linkedMapOf<String, Int>()
        for (day in daysInMonth(monthDate)) {
            for (slot in slotsForDay(day)) {
                val id = slot ?: continue
                totals[id] = (totals[id] ?: 0) + SLOT_MINUTES
            }
        }
        return totals
    }

    fun monthTotalMinutes(monthDate: LocalDate, projectId: String?): Int {
        val totals = monthTotals(monthDate)
        if (!projectId.isNullOrBlank()) return totals[projectId] ?: 0
        return totals.values.sum()
    }

    fun totalMinutes(startDate: LocalDate, endDate: LocalDate, projectId: String?): Int {
        return entries(startDate, endDate, projectId).sumOf { (it.endIndex - it.startIndex + 1) * SLOT_MINUTES }
    }

    fun dailyTotals(monthDate: LocalDate, projectId: String): List<Pair<LocalDate, Int>> {
        return daysInMonth(monthDate).mapNotNull { day ->
            val total = slotsForDay(day).sumOf { if (it == projectId) SLOT_MINUTES else 0 }
            if (total > 0) day to total else null
        }
    }

    fun entries(day: LocalDate, filterProjectId: String?): List<TimeEntry> {
        val daySlots = slotsForDay(day)
        val dayLabels = labelsForDay(day)
        val entries = mutableListOf<TimeEntry>()

        var currentId: String? = null
        var currentLabel: String? = null
        var currentStart: Int? = null

        fun closeEntry(endIndex: Int) {
            val start = currentStart ?: return
            val id = currentId ?: return
            entries += TimeEntry(
                dayKey = dayKey(day),
                startIndex = start,
                endIndex = endIndex,
                projectId = id,
                label = currentLabel,
            )
            currentStart = null
            currentId = null
            currentLabel = null
        }

        for (index in 0 until SLOTS_PER_DAY) {
            val slotId = daySlots[index]
            val label = dayLabels[index]
            val matchesFilter = filterProjectId.isNullOrBlank() || slotId == filterProjectId

            if (slotId == null || !matchesFilter) {
                if (currentStart != null) closeEntry(index - 1)
                continue
            }

            if (currentStart == null) {
                currentStart = index
                currentId = slotId
                currentLabel = label
                continue
            }

            if (slotId != currentId || label != currentLabel) {
                closeEntry(index - 1)
                currentStart = index
                currentId = slotId
                currentLabel = label
            }
        }

        if (currentStart != null) closeEntry(SLOTS_PER_DAY - 1)
        return entries
    }

    fun entries(startDate: LocalDate, endDate: LocalDate, filterProjectId: String?): List<TimeEntry> {
        return daysBetween(startDate, endDate).flatMap { day -> entries(day, filterProjectId) }
    }

    fun timesheetCsv(startDate: LocalDate, endDate: LocalDate, filterProjectId: String?): String {
        val lines = mutableListOf("Date,Start,End,Project,Label,Hours,Rate,Amount")
        for (entry in entries(startDate, endDate, filterProjectId)) {
            val project = projectForId(entry.projectId)
            val projectName = project?.name ?: "Unknown"
            val label = escapeCsv(entry.label ?: "")
            val start = timeString(entry.startIndex)
            val end = timeString(entry.endIndex + 1)
            val minutes = (entry.endIndex - entry.startIndex + 1) * SLOT_MINUTES
            val hours = minutes.toDouble() / 60.0
            val hoursText = String.format(Locale.US, "%.2f", hours)
            val rate = project?.rate ?: 0.0
            val rateText = String.format(Locale.US, "%.2f", rate)
            val amountText = String.format(Locale.US, "%.2f", hours * rate)
            val dateText = entry.dayKey
            lines += "\"$dateText\",\"$start\",\"$end\",\"${escapeCsv(projectName)}\",\"$label\",\"$hoursText\",\"$rateText\",\"$amountText\""
        }
        return lines.joinToString("\n")
    }

    fun seedSampleData(referenceDate: LocalDate) {
        val sampleProjects = listOf(
            Project(name = "Acme Mobile", colorHex = PALETTE[0], rate = 120.0, vatType = VatType.INCLUDED),
            Project(name = "Nimbus", colorHex = PALETTE[1], rate = 95.0, vatType = VatType.EXCLUDED),
            Project(name = "Studio Ops", colorHex = PALETTE[2], rate = 80.0, vatType = VatType.NONE),
            Project(name = "Admin", colorHex = PALETTE[3], rate = 60.0, vatType = VatType.NONE),
        )

        // Seed is a full overwrite; keep it deterministic.
        isApplyingCloudUpdate = true
        _state.value = TimeStoreSnapshot(
            lastUpdatedEpochMillis = System.currentTimeMillis(),
            projects = sampleProjects,
            logs = emptyMap(),
            invoiceRecords = emptyList(),
            greenInvoiceApiKey = "",
            greenInvoiceApiSecret = "",
            greenInvoiceTestMode = false,
            invoiceLogoPng = state.value.invoiceLogoPng,
            invoiceSignaturePng = state.value.invoiceSignaturePng,
            invoiceStartNumber = state.value.invoiceStartNumber,
            invoiceNextNumber = state.value.invoiceNextNumber,
            lastSelectedProjectId = sampleProjects.firstOrNull()?.id,
            cloudSyncUri = state.value.cloudSyncUri,
            lastCloudSyncEpochMillis = state.value.lastCloudSyncEpochMillis,
        )
        isApplyingCloudUpdate = false

        fun slotIndex(hour: Int, minute: Int): Int {
            val totalMinutes = hour * 60 + minute
            return (totalMinutes / SLOT_MINUTES).coerceIn(0, SLOTS_PER_DAY - 1)
        }

        fun addEntry(
            project: Project,
            label: String?,
            dayOffset: Int,
            startHour: Int,
            startMinute: Int,
            endHour: Int,
            endMinute: Int,
        ) {
            val day = referenceDate.plusDays(dayOffset.toLong())
            val start = slotIndex(startHour, startMinute)
            val end = slotIndex(endHour, endMinute)
            val rangeEnd = maxOf(start, end - 1)
            setEntry(project.id, label, start..rangeEnd, day)
        }

        if (sampleProjects.size >= 4) {
            addEntry(sampleProjects[0], "Sprint review", 0, 8, 0, 10, 30)
            addEntry(sampleProjects[1], "Wireframes", 0, 10, 30, 12, 0)
            addEntry(sampleProjects[0], "Build", 0, 13, 0, 15, 30)
            addEntry(sampleProjects[2], "Ops review", 0, 15, 30, 16, 30)
            addEntry(sampleProjects[3], "Admin", 0, 16, 30, 17, 30)
        }

        val offsets = listOf(-1, -2, -3, -5, -7, -9, -11, -13)
        offsets.forEachIndexed { index, offset ->
            val primary = sampleProjects[index % sampleProjects.size]
            val secondary = sampleProjects[(index + 1) % sampleProjects.size]
            addEntry(primary, null, offset, 9, 0, 12, 0)
            addEntry(secondary, "Client sync", offset, 13, 0, 16, 0)
        }
    }

    fun setGreenInvoiceApiKey(value: String) {
        mutate { it.copy(greenInvoiceApiKey = value) }
    }

    fun setGreenInvoiceApiSecret(value: String) {
        mutate { it.copy(greenInvoiceApiSecret = value) }
    }

    fun setGreenInvoiceTestMode(value: Boolean) {
        mutate { it.copy(greenInvoiceTestMode = value) }
    }

    fun addInvoiceRecord(clientId: String?, clientKey: String, date: LocalDate, isSandbox: Boolean) {
        val trimmedKey = clientKey.trim()
        if (trimmedKey.isEmpty()) return
        val trimmedId = clientId?.trim()?.takeIf { it.isNotEmpty() }
        val epoch = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val record = InvoiceRecord(
            clientKey = trimmedKey,
            clientId = trimmedId,
            dateEpochMillis = epoch,
            isSandbox = isSandbox,
        )
        mutate { snap -> snap.copy(invoiceRecords = snap.invoiceRecords + record) }
    }

    fun invoiceCount(clientKey: String, clientId: String?, date: LocalDate, isSandbox: Boolean): Int {
        val monthKey = invoiceMonthKey(date)
        val normalizedKey = clientKey.trim().lowercase()
        val normalizedId = clientId?.trim()?.lowercase()
        return state.value.invoiceRecords.count { record ->
            if (record.isSandbox != isSandbox) return@count false
            val recordDate = Instant.ofEpochMilli(record.dateEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            if (invoiceMonthKey(recordDate) != monthKey) return@count false
            if (!normalizedId.isNullOrEmpty()) {
                val recordId = record.clientId?.trim()?.lowercase()
                if (!recordId.isNullOrEmpty()) {
                    return@count recordId == normalizedId
                }
            }
            record.clientKey.trim().lowercase() == normalizedKey
        }
    }

    fun nextInvoiceNumber(): Int {
        val snap = state.value
        val start = maxOf(snap.invoiceStartNumber, 1)
        val next = snap.invoiceNextNumber
        return if (next != null && next >= start) next else start
    }

    fun setInvoiceStartNumber(value: Int) {
        mutate { snap ->
            val normalized = maxOf(value, 1)
            val next = snap.invoiceNextNumber?.let { if (it < normalized) normalized else it }
            snap.copy(invoiceStartNumber = normalized, invoiceNextNumber = next)
        }
    }

    fun advanceInvoiceNumber(fromCurrent: Int) {
        val start = maxOf(state.value.invoiceStartNumber, 1)
        val next = maxOf(fromCurrent + 1, start)
        mutate { it.copy(invoiceNextNumber = next) }
    }

    fun setInvoiceLogoPng(png: ByteArray?) {
        mutate { it.copy(invoiceLogoPng = png) }
    }

    fun setInvoiceSignaturePng(png: ByteArray?) {
        mutate { it.copy(invoiceSignaturePng = png) }
    }

    fun setCloudSyncUri(uri: Uri?) {
        val uriString = uri?.toString()
        mutate { snap -> snap.copy(cloudSyncUri = uriString) }
    }

    fun cloudSyncStatusLabel(): String {
        return if (state.value.cloudSyncUri.isNullOrBlank()) "Unavailable" else "Available"
    }

    fun lastCloudSyncLabel(): String {
        if (state.value.cloudSyncUri.isNullOrBlank()) return "Unavailable"
        val last = state.value.lastCloudSyncEpochMillis ?: return "Not yet"
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        return formatter.format(Instant.ofEpochMilli(last))
    }

    fun syncNowToCloud() {
        scope.launch {
            saveSnapshotToCloud()
        }
    }

    fun loadFromCloudIfAvailable() {
        val uriString = state.value.cloudSyncUri
        if (uriString.isNullOrBlank()) return
        scope.launch {
            val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@launch
            val remote = readSnapshotFromUri(uri) ?: return@launch
            applySnapshotIfNewer(remote)
        }
    }

    private fun dayKey(day: LocalDate): String = day.format(DAY_FORMATTER)

    private fun invoiceMonthKey(date: LocalDate): String {
        val ym = YearMonth.from(date)
        return String.format(Locale.US, "%04d-%02d", ym.year, ym.monthValue)
    }

    fun monthRange(date: LocalDate): Pair<LocalDate, LocalDate> {
        val ym = YearMonth.from(date)
        val start = ym.atDay(1)
        val end = ym.atEndOfMonth()
        return start to end
    }

    private fun daysInMonth(date: LocalDate): List<LocalDate> {
        val ym = YearMonth.from(date)
        val start = ym.atDay(1)
        val end = ym.atEndOfMonth()
        return daysBetween(start, end)
    }

    private fun daysBetween(start: LocalDate, end: LocalDate): List<LocalDate> {
        if (start.isAfter(end)) return emptyList()
        val days = ArrayList<LocalDate>()
        var current = start
        while (!current.isAfter(end)) {
            days += current
            current = current.plusDays(1)
        }
        return days
    }

    private fun escapeCsv(value: String): String = value.replace("\"", "\"\"")

    private fun loadLocal(): TimeStoreSnapshot {
        if (!localFile.exists()) return TimeStoreSnapshot()
        return runCatching {
            val text = localFile.readText()
            json.decodeFromString<TimeStoreSnapshot>(text)
        }.getOrElse { TimeStoreSnapshot() }
    }

    private fun saveLocal(snapshot: TimeStoreSnapshot) {
        runCatching {
            localFile.writeText(json.encodeToString(snapshot))
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            saveSnapshot()
        }
    }

    private fun recordChange() {
        if (isApplyingCloudUpdate) return
        val now = System.currentTimeMillis()
        _state.update { it.copy(lastUpdatedEpochMillis = now) }
        scheduleSave()
    }

    private fun saveSnapshot() {
        val snapshot = state.value
        saveLocal(snapshot)
        saveSnapshotToCloud()
    }

    private fun saveSnapshotToCloud() {
        val uriString = state.value.cloudSyncUri ?: return
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val body = runCatching { json.encodeToString(state.value) }.getOrNull() ?: return

        val wrote = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
                out.flush()
                true
            } ?: false
        }.getOrElse { false }

        if (wrote) {
            // Do not "recordChange" when updating last sync time.
            val now = System.currentTimeMillis()
            _state.update { it.copy(lastCloudSyncEpochMillis = now) }
            saveLocal(state.value)
        }
    }

    private fun readSnapshotFromUri(uri: Uri): TimeStoreSnapshot? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val text = input.bufferedReader(Charsets.UTF_8).readText()
                json.decodeFromString<TimeStoreSnapshot>(text)
            }
        }.getOrNull()
    }

    private fun applySnapshotIfNewer(remote: TimeStoreSnapshot) {
        val localUpdated = state.value.lastUpdatedEpochMillis
        if (remote.lastUpdatedEpochMillis <= localUpdated) return
        isApplyingCloudUpdate = true
        _state.value = remote
        isApplyingCloudUpdate = false
        // Save local copy and mark sync time.
        val now = System.currentTimeMillis()
        _state.update { it.copy(lastCloudSyncEpochMillis = now) }
        saveLocal(state.value)
    }

    private inline fun mutate(transform: (TimeStoreSnapshot) -> TimeStoreSnapshot) {
        _state.update(transform)
        recordChange()
    }

    companion object {
        const val SLOTS_PER_DAY: Int = 96
        const val SLOT_MINUTES: Int = 15

        private val DAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withLocale(Locale.US)

        val PALETTE: List<String> = listOf(
            "#0F9D58",
            "#F4B400",
            "#DB4437",
            "#4285F4",
            "#AB47BC",
            "#00ACC1",
            "#FF7043",
            "#9E9D24",
        )

        fun timeString(slotIndex: Int): String {
            val clamped = slotIndex.coerceIn(0, SLOTS_PER_DAY)
            val hour = clamped / 4
            val minute = (clamped % 4) * SLOT_MINUTES
            return String.format(Locale.US, "%02d:%02d", hour, minute)
        }
    }
}
