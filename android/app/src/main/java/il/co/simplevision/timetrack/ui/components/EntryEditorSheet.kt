package il.co.simplevision.timetrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import il.co.simplevision.timetrack.data.Project
import il.co.simplevision.timetrack.data.TimeStore
import il.co.simplevision.timetrack.util.colorFromHex
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorSheet(
    projects: List<Project>,
    existingSlots: List<String?>,
    initialRange: IntRange,
    initialProjectId: String?,
    initialLabel: String?,
    onDismiss: () -> Unit,
    onSave: (String?, IntRange, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val lower = min(initialRange.first, initialRange.last)
    val upper = max(initialRange.first, initialRange.last)

    var startSlot by remember { mutableStateOf(lower) }
    var endSlot by remember { mutableStateOf(min(upper + 1, TimeStore.SLOTS_PER_DAY)) }
    var label by remember { mutableStateOf(initialLabel ?: "") }
    var selectedProjectId by remember { mutableStateOf(initialProjectId ?: projects.firstOrNull()?.id) }
    var showOverlapAlert by remember { mutableStateOf(false) }

    LaunchedEffect(startSlot) {
        if (endSlot <= startSlot) {
            endSlot = min(startSlot + 1, TimeStore.SLOTS_PER_DAY)
        }
    }
    LaunchedEffect(endSlot) {
        if (endSlot <= startSlot) {
            startSlot = max(endSlot - 1, 0)
        }
    }

    fun hasOverlap(range: IntRange): Boolean {
        if (selectedProjectId == null) return false
        for (index in range) {
            if (index < 0 || index >= existingSlots.size) continue
            if (existingSlots[index] == null) continue
            if (initialProjectId != null && index in initialRange) continue
            return true
        }
        return false
    }

    fun save() {
        val safeEnd = max(endSlot, startSlot + 1)
        val range = startSlot..(safeEnd - 1)
        val trimmed = label.trim()
        val normalizedLabel = trimmed.ifEmpty { null }
        if (hasOverlap(range)) {
            showOverlapAlert = true
            return
        }
        if (initialProjectId != null) {
            onSave(null, initialRange, null)
        }
        onSave(selectedProjectId, range, normalizedLabel)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Assign Time", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { save(); onDismiss() }) { Text("Save") }
            }

            Divider()
            Spacer(modifier = Modifier.height(12.dp))

            Text("Time", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeDropdown(
                    label = "Start",
                    value = startSlot,
                    values = (0 until TimeStore.SLOTS_PER_DAY).toList(),
                    valueLabel = { TimeStore.timeString(it) },
                    onChange = { startSlot = it },
                    modifier = Modifier.weight(1f),
                )
                TimeDropdown(
                    label = "End",
                    value = endSlot,
                    values = (1..TimeStore.SLOTS_PER_DAY).toList(),
                    valueLabel = { TimeStore.timeString(it) },
                    onChange = { endSlot = it },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Label", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = label,
                onValueChange = { label = it },
                placeholder = { Text("Optional label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Project", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                item {
                    ProjectRow(
                        name = "Clear Time",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        isSelected = selectedProjectId == null,
                        onClick = { selectedProjectId = null },
                    )
                }
                items(projects) { project ->
                    ProjectRow(
                        name = if (project.name.isBlank()) "Untitled Project" else project.name,
                        color = colorFromHex(project.colorHex),
                        isSelected = selectedProjectId == project.id,
                        onClick = { selectedProjectId = project.id },
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showOverlapAlert) {
        AlertDialog(
            onDismissRequest = { showOverlapAlert = false },
            title = { Text("Overlapping time") },
            text = { Text("This time range overlaps existing time for a different project. Adjust the range or clear time first.") },
            confirmButton = { TextButton(onClick = { showOverlapAlert = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun ProjectRow(
    name: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.material3.Surface(color = color, shape = CircleShape) {
                Spacer(modifier = Modifier.padding(6.dp))
            }
            Text(name, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.weight(1f))
            if (isSelected) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDropdown(
    label: String,
    value: Int,
    values: List<Int>,
    valueLabel: (Int) -> String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        TextField(
            readOnly = true,
            value = valueLabel(value),
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            values.forEach { item ->
                DropdownMenuItem(
                    text = { Text(valueLabel(item)) },
                    onClick = {
                        expanded = false
                        onChange(item)
                    },
                )
            }
        }
    }
}
