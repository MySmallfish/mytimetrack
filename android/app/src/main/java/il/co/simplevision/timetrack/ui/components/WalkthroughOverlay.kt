package il.co.simplevision.timetrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max

data class WalkthroughTargets(
    val settings: Rect? = null,
    val grid: Rect? = null,
    val summary: Rect? = null,
)

@Composable
fun WalkthroughOverlay(
    isPresented: Boolean,
    targets: WalkthroughTargets = WalkthroughTargets(),
    onDismiss: () -> Unit,
) {
    if (!isPresented) return

    var stepIndex by remember { mutableIntStateOf(0) }

    val steps = listOf(
        WalkthroughStep(
            target = WalkthroughTarget.SETTINGS,
            title = "Create a project",
            message = "Open Settings to add a project. Set a color and hourly rate so totals are automatic.",
        ),
        WalkthroughStep(
            target = WalkthroughTarget.GRID,
            title = "Report time",
            message = "Drag on the timeline to select time blocks (snaps to 30 minutes). Release to pick a project and optional label.",
        ),
        WalkthroughStep(
            target = WalkthroughTarget.SUMMARY,
            title = "Produce an invoice",
            message = "Use Month Summary to review totals and send a proforma PDF invoice with a CSV timesheet.",
        ),
    )

    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]
    val targetRect = when (step.target) {
        WalkthroughTarget.SETTINGS -> targets.settings
        WalkthroughTarget.GRID -> targets.grid
        WalkthroughTarget.SUMMARY -> targets.summary
    } ?: Rect.Zero
    val highlightRect = highlightRect(targetRect)

    LaunchedEffect(isPresented) {
        stepIndex = 0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scrim = Color.Black.copy(alpha = 0.56f)
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                if (highlightRect != Rect.Zero) {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            rect = highlightRect,
                            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                        ),
                    )
                }
            }
            drawPath(path, scrim)

            if (highlightRect != Rect.Zero) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.88f),
                    topLeft = highlightRect.topLeft,
                    size = highlightRect.size,
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        WalkthroughCard(
            title = step.title,
            message = step.message,
            stepIndex = stepIndex,
            totalSteps = steps.size,
            isLast = stepIndex == steps.lastIndex,
            onSkip = onDismiss,
            onNext = {
                if (stepIndex >= steps.lastIndex) onDismiss() else stepIndex += 1
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

private enum class WalkthroughTarget { SETTINGS, GRID, SUMMARY }

private data class WalkthroughStep(
    val target: WalkthroughTarget,
    val title: String,
    val message: String,
)

private fun highlightRect(targetRect: Rect): Rect {
    if (targetRect == Rect.Zero) return Rect.Zero
    var rect = targetRect.inflate(10f)
    val minSize = 56f
    if (rect.width < minSize) {
        val delta = (minSize - rect.width) / 2f
        rect = Rect(rect.left - delta, rect.top, rect.right + delta, rect.bottom)
    }
    if (rect.height < minSize) {
        val delta = (minSize - rect.height) / 2f
        rect = Rect(rect.left, rect.top - delta, rect.right, rect.bottom + delta)
    }
    return rect
}

@Composable
private fun WalkthroughCard(
    title: String,
    message: String,
    stepIndex: Int,
    totalSteps: Int,
    isLast: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 4.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Quick tour",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${stepIndex + 1} / $totalSteps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.padding(top = 10.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.padding(top = 14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onSkip) { Text("Skip") }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onNext) { Text(if (isLast) "Done" else "Next") }
            }
        }
    }
}

private fun Rect.inflate(delta: Float): Rect {
    return Rect(left - delta, top - delta, right + delta, bottom + delta)
}

