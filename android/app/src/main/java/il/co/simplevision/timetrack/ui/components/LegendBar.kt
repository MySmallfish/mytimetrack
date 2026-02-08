package il.co.simplevision.timetrack.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import il.co.simplevision.timetrack.data.Project
import il.co.simplevision.timetrack.util.colorFromHex

@Composable
fun LegendBar(
    projects: List<Project>,
    totals: Map<String, Int>,
    totalMinutes: Int,
) {
    Surface(tonalElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Divider()
            val scroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (projects.isEmpty()) {
                    Text("No projects yet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    projects.forEach { project ->
                        val minutes = totals[project.id] ?: 0
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .padding(vertical = 6.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Dot(color = colorFromHex(project.colorHex))
                            Text(project.name, style = MaterialTheme.typography.labelMedium)
                            if (minutes > 0) {
                                Text(
                                    hoursText(minutes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(hoursText(totalMinutes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Surface(color = color, shape = CircleShape) {
        Spacer(modifier = Modifier.padding(5.dp))
    }
}

private fun hoursText(minutes: Int): String {
    val hours = minutes.toDouble() / 60.0
    return String.format(java.util.Locale.US, "%.1f h", hours)
}

