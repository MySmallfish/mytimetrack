package il.co.simplevision.timetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import il.co.simplevision.timetrack.data.InvoiceLanguage
import il.co.simplevision.timetrack.data.Project
import il.co.simplevision.timetrack.data.TimeStore
import il.co.simplevision.timetrack.data.VatType
import il.co.simplevision.timetrack.ui.components.EnumPicker
import il.co.simplevision.timetrack.util.colorFromHex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    initialProject: Project?,
    onBack: () -> Unit,
    onSave: (Project) -> Unit,
) {
    if (initialProject == null) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("Back") }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Project", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.weight(1f))
                }
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text("Project not found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    var name by remember { mutableStateOf(initialProject.name) }
    var colorHex by remember { mutableStateOf(initialProject.colorHex) }
    var rateText by remember { mutableStateOf(if (initialProject.rate == 0.0) "" else initialProject.rate.toString()) }

    var timesheetEmails by remember { mutableStateOf(initialProject.timesheetEmails) }
    var timesheetSubject by remember { mutableStateOf(initialProject.timesheetSubject) }
    var timesheetPretext by remember { mutableStateOf(initialProject.timesheetPretext) }

    var invoiceHeader by remember { mutableStateOf(initialProject.invoiceHeader) }
    var invoiceItemDetails by remember { mutableStateOf(initialProject.invoiceItemDetails) }
    var invoiceLanguage by remember { mutableStateOf(initialProject.invoiceLanguage) }
    var vatType by remember { mutableStateOf(initialProject.vatType) }

    val palette = TimeStore.PALETTE

    fun save() {
        val rate = rateText.toDoubleOrNull() ?: 0.0
        val updated = initialProject.copy(
            name = name,
            colorHex = colorHex,
            rate = rate,
            timesheetEmails = timesheetEmails,
            timesheetSubject = timesheetSubject,
            timesheetPretext = timesheetPretext,
            invoiceHeader = invoiceHeader,
            invoiceItemDetails = invoiceItemDetails,
            invoiceLanguage = invoiceLanguage,
            vatType = vatType,
        )
        onSave(updated)
        onBack()
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Spacer(modifier = Modifier.weight(1f))
                Text(if (name.isBlank()) "Project" else name, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = ::save) { Text("Save") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionTitle("Basics")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    palette.forEach { hex ->
                        val selected = hex.equals(colorHex, ignoreCase = true)
                        ColorDot(
                            color = colorFromHex(hex),
                            selected = selected,
                            onClick = { colorHex = hex },
                        )
                    }
                }
                OutlinedTextField(
                    value = colorHex,
                    onValueChange = { colorHex = it },
                    label = { Text("Color hex") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Rate (USD/hour)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item {
                Divider()
                SectionTitle("Timesheet Email")
                OutlinedTextField(
                    value = timesheetEmails,
                    onValueChange = { timesheetEmails = it },
                    label = { Text("Recipients (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = timesheetSubject,
                    onValueChange = { timesheetSubject = it },
                    label = { Text("Subject") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = timesheetPretext,
                    onValueChange = { timesheetPretext = it },
                    label = { Text("Pretext") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }

            item {
                Divider()
                SectionTitle("Invoice")
                OutlinedTextField(
                    value = invoiceHeader,
                    onValueChange = { invoiceHeader = it },
                    label = { Text("Header (month added automatically)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = invoiceItemDetails,
                    onValueChange = { invoiceItemDetails = it },
                    label = { Text("Item details") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                EnumPicker(
                    label = "Language",
                    value = invoiceLanguage,
                    values = InvoiceLanguage.entries,
                    valueLabel = { it.title },
                    onChange = { invoiceLanguage = it },
                )

                EnumPicker(
                    label = "VAT",
                    value = vatType,
                    values = VatType.entries,
                    valueLabel = { it.title },
                    onChange = { vatType = it },
                )
            }

            item {
                Spacer(modifier = Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(border, CircleShape)
            .padding(3.dp)
            .background(color, CircleShape)
            .clickable(onClick = onClick),
    )
}
