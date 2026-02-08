package il.co.simplevision.timetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun OnboardingFlow(onFinish: () -> Unit) {
    val steps = listOf(
        OnboardingStep(
            id = 0,
            title = "Welcome to MyTimetrack",
            message = "Track your day in 15-minute slots. Drag on the timeline to log work fast.",
            preview = OnboardingPreviewKind.DAY,
        ),
        OnboardingStep(
            id = 1,
            title = "Create Your Projects",
            message = "Add a color and hourly rate so totals, timesheets, and invoices are automatic.",
            preview = OnboardingPreviewKind.SETTINGS,
        ),
        OnboardingStep(
            id = 2,
            title = "Review And Edit",
            message = "Use the list view to filter by date or project, and fine-tune labels anytime.",
            preview = OnboardingPreviewKind.LIST,
        ),
        OnboardingStep(
            id = 3,
            title = "Export And Invoice",
            message = "Get monthly totals, export a timesheet, and send a proforma PDF invoice when it's time to bill.",
            preview = OnboardingPreviewKind.SUMMARY,
        ),
    )

    val pagerState = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "MyTimetrack",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Skip",
                    modifier = Modifier
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                // Clickable without ripple for simplicity.
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(Color.Transparent)
                        .padding(0.dp),
                ) {
                    // no-op (the "Skip" label is the hit target via parent Row)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
            ) { page ->
                OnboardingPage(step = steps[page])
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dots(count = steps.size, index = pagerState.currentPage)
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = {
                    if (pagerState.currentPage >= steps.size - 1) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                }) {
                    Text(if (pagerState.currentPage == steps.size - 1) "Get Started" else "Continue")
                }
            }
        }

        // Full-screen dismiss prevention is handled by BackHandler in the caller.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, end = 12.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            OutlinedButton(onClick = onFinish) {
                Text("Skip")
            }
        }
    }
}

private data class OnboardingStep(
    val id: Int,
    val title: String,
    val message: String,
    val preview: OnboardingPreviewKind,
)

private enum class OnboardingPreviewKind { DAY, SETTINGS, SUMMARY, LIST }

@Composable
private fun OnboardingPage(step: OnboardingStep) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingPreview(kind = step.preview)
        Spacer(modifier = Modifier.height(22.dp))
        Text(step.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(10.dp))
        Text(step.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OnboardingPreview(kind: OnboardingPreviewKind) {
    val label = when (kind) {
        OnboardingPreviewKind.DAY -> "Day"
        OnboardingPreviewKind.SETTINGS -> "Settings"
        OnboardingPreviewKind.SUMMARY -> "Summary"
        OnboardingPreviewKind.LIST -> "List"
    }

    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun Dots(count: Int, index: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            val color = if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            Box(
                modifier = Modifier
                    .size(if (i == index) 10.dp else 8.dp)
                    .background(color = color, shape = CircleShape),
            )
        }
    }
}

