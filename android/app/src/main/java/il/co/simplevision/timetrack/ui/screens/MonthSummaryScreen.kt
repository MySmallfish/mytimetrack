package il.co.simplevision.timetrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import il.co.simplevision.timetrack.data.Project
import il.co.simplevision.timetrack.data.TimeStore
import il.co.simplevision.timetrack.data.VatType
import il.co.simplevision.timetrack.ui.components.EnumPicker
import il.co.simplevision.timetrack.util.MailAttachment
import il.co.simplevision.timetrack.util.invoiceStrings
import il.co.simplevision.timetrack.util.makeProformaPdf
import il.co.simplevision.timetrack.util.shareEmail
import il.co.simplevision.timetrack.util.colorFromHex
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MonthSummaryScreen(
    store: TimeStore,
    hostActivity: FragmentActivity,
    monthDate: LocalDate,
    onDone: () -> Unit,
    selectedProjectId: String? = null,
) {
    val snap by store.state.collectAsState()
    val scope = rememberCoroutineScope()

    val (rangeStart, rangeEnd) = remember(monthDate) { store.monthRange(monthDate) }

    var selectedId by remember { mutableStateOf<String?>(selectedProjectId) }
    var isGenerating by remember { mutableStateOf(false) }
    var alert by remember { mutableStateOf<Pair<String, String>?>(null) }

    val monthTitle = remember(monthDate) {
        monthDate.format(DateTimeFormatter.ofPattern("LLLL yyyy").withLocale(Locale.getDefault()))
    }

    val totalMinutes = remember(snap.lastUpdatedEpochMillis, selectedId, monthDate) {
        store.monthTotalMinutes(monthDate, selectedId)
    }

    val totalAmount = remember(snap.lastUpdatedEpochMillis, selectedId, monthDate) {
        if (selectedId != null) {
            val rate = store.projectForId(selectedId)?.rate ?: 0.0
            (totalMinutes.toDouble() / 60.0) * rate
        } else {
            projectTotals(store, monthDate).sumOf { it.amount }
        }
    }

    val projectTotals = remember(snap.lastUpdatedEpochMillis, monthDate) { projectTotals(store, monthDate) }
    val dailyTotals = remember(snap.lastUpdatedEpochMillis, monthDate, selectedId) {
        if (selectedId == null) emptyList() else store.dailyTotals(monthDate, selectedId!!)
    }

    fun sendProforma() {
        if (isGenerating) return
        val projectId = selectedId
        if (projectId == null) {
            alert = "Select a project" to "Choose a project before sending a proforma invoice."
            return
        }
        val project = store.projectForId(projectId)
        if (project == null) {
            alert = "Project missing" to "Could not find the selected project."
            return
        }

        val recipients = parseEmails(project.timesheetEmails)
        if (recipients.isEmpty()) {
            alert = "Missing recipients" to "Add timesheet recipient emails in project settings."
            return
        }

        val logo = snap.invoiceLogoPng
        if (logo == null) {
            alert = "Missing logo" to "Add a logo in Settings > Invoice PDF."
            return
        }
        val signature = snap.invoiceSignaturePng
        if (signature == null) {
            alert = "Missing signature" to "Add a signature in Settings > Invoice PDF."
            return
        }

        val minutes = store.totalMinutes(rangeStart, rangeEnd, project.id)
        if (minutes <= 0) {
            alert = "No time tracked" to "There is no time in this range to invoice."
            return
        }

        val hours = minutes.toDouble() / 60.0
        val roundedHours = ((hours * 100.0).roundToInt()) / 100.0
        val baseAmount = roundedHours * project.rate
        val amounts = invoiceAmounts(project, baseAmount)

        val invoiceDate = if (snap.greenInvoiceTestMode) LocalDate.now() else effectiveInvoiceDate()
        val header = invoiceHeader(project, rangeStart)
        val itemDetails = invoiceItemDetails(project)
        val invoiceNumber = store.nextInvoiceNumber()
        val rangeText = localizedRangeTitle(project, rangeStart, rangeEnd)

        val csv = store.timesheetCsv(rangeStart, rangeEnd, project.id)
        val safeProject = project.name.replace("/", "-")
        val rangeMonthTitle = rangeStart.format(DateTimeFormatter.ofPattern("LLLL yyyy").withLocale(Locale.getDefault()))
        val pdfName = "Proforma-$safeProject-$rangeMonthTitle.pdf"
        val csvName = "Timesheet-${rangeStart.format(FILE_FORMAT)}-${rangeEnd.format(FILE_FORMAT)}.csv"

        isGenerating = true
        scope.launch(Dispatchers.Default) {
            val pdf = makeProformaPdf(
                logoPng = logo,
                signaturePng = signature,
                project = project,
                header = header,
                itemDetails = itemDetails,
                hours = roundedHours,
                rate = project.rate,
                subtotal = amounts.subtotal,
                vatAmount = amounts.vat,
                total = amounts.total,
                date = invoiceDate,
                invoiceNumber = invoiceNumber,
                rangeText = rangeText,
            )
            val csvBytes = csv.toByteArray(Charsets.UTF_8)
            hostActivity.runOnUiThread {
                isGenerating = false
                if (pdf == null) {
                    alert = "Invoice failed" to "Could not generate the PDF invoice."
                    return@runOnUiThread
                }
                val ok = shareEmail(
                    activity = hostActivity,
                    recipients = recipients,
                    subject = header,
                    body = proformaEmailBody(project, rangeStart, rangeEnd),
                    attachments = listOf(
                        MailAttachment(bytes = pdf, mimeType = "application/pdf", fileName = pdfName),
                        MailAttachment(bytes = csvBytes, mimeType = "text/csv", fileName = csvName),
                    ),
                )
                if (!ok) {
                    alert = "Mail not configured" to "No email app is available on this device."
                    return@runOnUiThread
                }
                store.advanceInvoiceNumber(fromCurrent = invoiceNumber)
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Month Summary", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDone) { Text("Done") }
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Text(
                    monthTitle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EnumPicker(
                    label = "Project",
                    value = selectedId,
                    values = listOf(null) + snap.projects.map { it.id },
                    valueLabel = { id -> if (id == null) "All Projects" else (store.projectForId(id)?.name ?: "Unknown") },
                    onChange = { selectedId = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                KeyValueRow("Total Hours", hoursText(totalMinutes))
                KeyValueRow("Total Amount", currencyText(totalAmount))
            }

            item { Spacer(modifier = Modifier.padding(top = 10.dp)) }

            if (selectedId == null) {
                item { SectionHeader("By Project") }
                if (projectTotals.isEmpty()) {
                    item {
                        Text(
                            "No tracked time yet.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(projectTotals) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Surface(color = colorFromHex(item.project.colorHex), shape = CircleShape) {
                                Spacer(modifier = Modifier.padding(5.dp))
                            }
                            Spacer(modifier = Modifier.padding(6.dp))
                            Text(item.project.name, modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(hoursText(item.minutes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(currencyText(item.amount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Divider()
                    }
                }
            } else {
                item { SectionHeader("By Day") }
                if (dailyTotals.isEmpty()) {
                    item {
                        Text(
                            "No entries in this month.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(dailyTotals) { (day, minutes) ->
                        val rate = store.projectForId(selectedId!!)?.rate ?: 0.0
                        val amount = (minutes.toDouble() / 60.0) * rate
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(day.format(DAY_SHORT))
                            Spacer(modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(hoursText(minutes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(currencyText(amount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Divider()
                    }
                }
            }

            item { Spacer(modifier = Modifier.padding(top = 10.dp)) }
            item { SectionHeader("Export") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { sendProforma() },
                        enabled = selectedId != null && !isGenerating,
                    ) {
                        if (isGenerating) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 6.dp))
                                Text("Preparing Proforma PDF")
                            }
                        } else {
                            Text("Send Proforma PDF + Timesheet")
                        }
                    }
                }
                if (selectedId == null) {
                    Text(
                        "Select a project to send.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Spacer(modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }

    val alertValue = alert
    if (alertValue != null) {
        AlertDialog(
            onDismissRequest = { alert = null },
            title = { Text(alertValue.first) },
            text = { Text(alertValue.second) },
            confirmButton = { TextButton(onClick = { alert = null }) { Text("OK") } },
        )
    }
}

private data class ProjectTotal(val project: Project, val minutes: Int, val amount: Double)

private fun projectTotals(store: TimeStore, monthDate: LocalDate): List<ProjectTotal> {
    val totals = store.monthTotals(monthDate)
    return store.state.value.projects.mapNotNull { project ->
        val minutes = totals[project.id] ?: 0
        if (minutes <= 0) return@mapNotNull null
        val amount = (minutes.toDouble() / 60.0) * project.rate
        ProjectTotal(project, minutes, amount)
    }
}

private fun parseEmails(raw: String): List<String> {
    return raw
        .split(',', ';', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun hoursText(minutes: Int): String {
    val hours = minutes.toDouble() / 60.0
    return String.format(Locale.US, "%.1f h", hours)
}

private fun currencyText(amount: Double): String = String.format(Locale.US, "$%.2f", amount)

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

private data class Amounts(val subtotal: Double, val vat: Double, val total: Double)

private fun roundedCurrency(amount: Double): Double = ((amount * 100.0).roundToInt()) / 100.0

private fun invoiceAmounts(project: Project, baseAmount: Double, vatRate: Double = 0.17): Amounts {
    val base = roundedCurrency(baseAmount)
    return when (project.vatType) {
        VatType.NONE -> Amounts(subtotal = base, vat = 0.0, total = base)
        VatType.INCLUDED -> {
            val subtotal = roundedCurrency(base / (1 + vatRate))
            val vat = roundedCurrency(base - subtotal)
            Amounts(subtotal, vat, base)
        }
        VatType.EXCLUDED -> {
            val subtotal = base
            val vat = roundedCurrency(subtotal * vatRate)
            val total = roundedCurrency(subtotal + vat)
            Amounts(subtotal, vat, total)
        }
    }
}

private fun invoiceHeader(project: Project, rangeStart: LocalDate): String {
    val trimmed = project.invoiceHeader.trim()
    val month = rangeStart.format(DateTimeFormatter.ofPattern("LLLL yyyy").withLocale(Locale.getDefault()))
    if (trimmed.isEmpty()) return month
    return "$trimmed - $month"
}

private fun invoiceItemDetails(project: Project): String {
    val trimmed = project.invoiceItemDetails.trim()
    if (trimmed.isNotEmpty()) return trimmed
    return "${project.name} time"
}

private fun timesheetPretext(project: Project, rangeStart: LocalDate, rangeEnd: LocalDate): String {
    val trimmed = project.timesheetPretext.trim()
    if (trimmed.isNotEmpty()) return trimmed
    val title = rangeTitle(rangeStart, rangeEnd)
    return "Attached is the timesheet for $title."
}

private fun proformaEmailBody(project: Project, rangeStart: LocalDate, rangeEnd: LocalDate): String {
    val base = timesheetPretext(project, rangeStart, rangeEnd)
    val line = invoiceStrings(project.invoiceLanguage).emailLine
    return base + "\n\n" + line
}

private fun rangeTitle(start: LocalDate, end: LocalDate): String {
    val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy").withLocale(Locale.getDefault())
    val startText = start.format(fmt)
    val endText = end.format(fmt)
    return if (startText == endText) startText else "$startText - $endText"
}

private fun localizedRangeTitle(project: Project, start: LocalDate, end: LocalDate): String {
    val locale = if (project.invoiceLanguage.rawValue == "he") Locale("he", "IL") else Locale.US
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val startText = start.format(fmt)
    val endText = end.format(fmt)
    return if (startText == endText) startText else "$startText - $endText"
}

private fun effectiveInvoiceDate(): LocalDate {
    val today = LocalDate.now()
    val ym = YearMonth.from(today)
    val startOfMonth = ym.atDay(1)
    val lastDay = ym.atEndOfMonth()
    if (today == lastDay) return lastDay
    return startOfMonth.minusDays(1)
}

private val FILE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val DAY_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
