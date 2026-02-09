package il.co.simplevision.timetrack.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import il.co.simplevision.timetrack.data.Project
import il.co.simplevision.timetrack.data.TimeStore
import il.co.simplevision.timetrack.util.colorFromHex
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun TimeGridView(
    day: LocalDate,
    store: TimeStore,
    onRangeSelected: (IntRange) -> Unit,
    onGridPositioned: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snap by store.state.collectAsState()
    val density = LocalDensity.current

    val rowHeightPx = with(density) { 24.dp.toPx() }
    val slotHeightPx = rowHeightPx / 2f
    // Keep the first visible hour line tight to the header divider.
    val gridOffsetY = 0f
    val labelWidthPx = with(density) { 60.dp.toPx() }
    val columnSpacingPx = with(density) { 12.dp.toPx() }
    val baseX = labelWidthPx + columnSpacingPx

    val slotsPerDay = TimeStore.SLOTS_PER_DAY
    val rowsPerDay = TimeStore.SLOTS_PER_DAY / 2
    val dragSnapSlots = 2

    val scroll = rememberScrollState()
    LaunchedEffect(day) {
        // Mirror iOS: scroll to 08:00 (row index 16).
        val targetRowIndex = min(rowsPerDay - 1, 8 * 2)
        val y = (targetRowIndex * rowHeightPx).roundToInt()
        scroll.scrollTo(y)
    }

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(day) {
        if (day != LocalDate.now()) return@LaunchedEffect
        while (true) {
            now = LocalTime.now()
            kotlinx.coroutines.delay(60_000)
        }
    }

    // Drag state mirrors iOS.
    var dragStart by remember { mutableStateOf<Int?>(null) }
    var dragCurrent by remember { mutableStateOf<Int?>(null) }
    var movingSegment by remember { mutableStateOf<TimeSegment?>(null) }
    var movingStartIndex by remember { mutableStateOf(0) }
    var movingMinStart by remember { mutableStateOf(0) }
    var movingMaxStart by remember { mutableStateOf(0) }
    var movingOffset by remember { mutableStateOf(0) }
    var dragMode by remember { mutableStateOf<DragMode?>(null) }
    var lastTranslation by remember { mutableStateOf(Offset.Zero) }
    var ignoreDrag by remember { mutableStateOf(false) }
    var dragStartLocation by remember { mutableStateOf<Offset?>(null) }

    fun selectedRange(): IntRange? {
        val start = dragStart ?: return null
        val current = dragCurrent ?: return null
        return min(start, current)..max(start, current)
    }

    fun slotIndex(locationY: Float): Int {
        val adjusted = locationY - gridOffsetY
        val raw = floor(adjusted / slotHeightPx).toInt()
        return raw.coerceIn(0, slotsPerDay - 1)
    }

    fun rowIndex(locationY: Float): Int {
        val adjusted = locationY - gridOffsetY
        val raw = floor(adjusted / rowHeightPx).toInt()
        return raw.coerceIn(0, rowsPerDay - 1)
    }

    fun segmentAt(slotIndex: Int): TimeSegment? {
        val daySlots = store.slotsForDay(day)
        val dayLabels = store.labelsForDay(day)
        if (slotIndex < 0 || slotIndex >= slotsPerDay) return null
        val projectId = daySlots[slotIndex] ?: return null
        val project = store.projectForId(projectId) ?: return null
        val label = dayLabels[slotIndex]

        var start = slotIndex
        while (start > 0 && daySlots[start - 1] == projectId && dayLabels[start - 1] == label) start -= 1
        var end = slotIndex
        while (end < slotsPerDay - 1 && daySlots[end + 1] == projectId && dayLabels[end + 1] == label) end += 1
        return TimeSegment(startIndex = start, endIndex = end, project = project, label = label)
    }

    fun timeSegments(
        slots: List<String?>,
        labels: List<String?>,
        projectMap: Map<String, Project>,
    ): List<TimeSegment> {
        val segments = mutableListOf<TimeSegment>()
        var currentId: String? = null
        var currentLabel: String? = null
        var currentStart: Int? = null

        fun close(endIndex: Int) {
            val start = currentStart ?: return
            val id = currentId ?: return
            val project = projectMap[id] ?: return
            segments += TimeSegment(startIndex = start, endIndex = endIndex, project = project, label = currentLabel)
            currentStart = null
            currentId = null
            currentLabel = null
        }

        for (i in 0 until slotsPerDay) {
            val slotId = slots[i]
            val label = labels[i]
            if (slotId == null) {
                if (currentStart != null) close(i - 1)
                continue
            }
            if (currentStart == null) {
                currentStart = i
                currentId = slotId
                currentLabel = label
                continue
            }
            if (slotId != currentId || label != currentLabel) {
                close(i - 1)
                currentStart = i
                currentId = slotId
                currentLabel = label
            }
        }
        if (currentStart != null) close(slotsPerDay - 1)
        return segments
    }

    fun normalizedRange(range: IntRange): IntRange {
        var lower = range.first
        var upper = range.last
        if (lower > upper) {
            val tmp = lower
            lower = upper
            upper = tmp
        }
        val count = upper - lower + 1
        if (count % dragSnapSlots == 0) return lower..upper
        return if (upper < slotsPerDay - 1) {
            lower..(upper + 1)
        } else if (lower > 0) {
            (lower - 1)..upper
        } else {
            lower..upper
        }
    }

    fun moveBounds(segment: TimeSegment): Pair<Int, Int> {
        val length = segment.endIndex - segment.startIndex
        val daySlots = store.slotsForDay(day)
        val start = segment.startIndex
        val end = segment.endIndex

        var previousOccupied: Int? = null
        if (start > 0) {
            for (i in (start - 1) downTo 0) {
                if (daySlots[i] != null) {
                    previousOccupied = i
                    break
                }
            }
        }

        var nextOccupied: Int? = null
        if (end < slotsPerDay - 1) {
            for (i in (end + 1) until slotsPerDay) {
                if (daySlots[i] != null) {
                    nextOccupied = i
                    break
                }
            }
        }

        val minStart = max(0, (previousOccupied ?: -1) + 1)
        val maxStart = min(slotsPerDay - length - 1, (nextOccupied ?: slotsPerDay) - length)
        return minStart to maxStart
    }

    fun snapToGrid(index: Int): Int {
        if (dragSnapSlots <= 1) return index
        val ratio = index.toDouble() / dragSnapSlots.toDouble()
        return ratio.roundToInt() * dragSnapSlots
    }

    fun snapUpToGrid(index: Int): Int {
        if (dragSnapSlots <= 1) return index
        val remainder = index % dragSnapSlots
        return if (remainder == 0) index else index + (dragSnapSlots - remainder)
    }

    fun snapDownToGrid(index: Int): Int {
        if (dragSnapSlots <= 1) return index
        val remainder = index % dragSnapSlots
        return if (remainder == 0) index else index - remainder
    }

    fun beginMoveIfNeeded(segment: TimeSegment) {
        if (movingSegment != null) return
        movingSegment = segment
        movingStartIndex = segment.startIndex
        movingOffset = 0
        dragStart = null
        dragCurrent = null

        val (minStart, maxStart) = moveBounds(segment)
        movingMinStart = minStart
        movingMaxStart = maxStart
    }

    fun updateMove(translation: Offset) {
        val segment = movingSegment ?: return
        val deltaRows = (translation.y / rowHeightPx).roundToInt()
        val deltaSlots = deltaRows * dragSnapSlots
        val length = segment.endIndex - segment.startIndex
        val maxStart = min(movingMaxStart, slotsPerDay - length - 1)
        val minAligned = snapUpToGrid(movingMinStart)
        val maxAligned = snapDownToGrid(maxStart)
        if (minAligned > maxAligned) {
            movingOffset = 0
            return
        }

        val desiredStart = movingStartIndex + deltaSlots
        val boundedDesired = desiredStart.coerceIn(minAligned, maxAligned)
        var snappedStart = snapToGrid(boundedDesired).coerceIn(minAligned, maxAligned)
        movingOffset = snappedStart - movingStartIndex
    }

    fun movingRange(segment: TimeSegment): IntRange? {
        val length = segment.endIndex - segment.startIndex
        val start = movingStartIndex + movingOffset
        if (start < 0 || start + length >= slotsPerDay) return null
        return start..(start + length)
    }

    fun finishMove() {
        val segment = movingSegment ?: return
        val length = segment.endIndex - segment.startIndex
        val newStart = movingStartIndex + movingOffset
        val newRange = newStart..(newStart + length)
        val originalRange = segment.startIndex..segment.endIndex

        if (newRange != originalRange) {
            store.setEntry(null, null, originalRange, day)
            store.setEntry(segment.project.id, segment.label, newRange, day)
        }

        movingSegment = null
        movingOffset = 0
    }

    fun updateSelection(location: Offset) {
        if (movingSegment != null) return
        val start = dragStart ?: return
        val currentRow = rowIndex(location.y)
        val startRow = start / dragSnapSlots
        val lowerRow = min(startRow, currentRow)
        val upperRow = max(startRow, currentRow)
        dragStart = lowerRow * dragSnapSlots
        dragCurrent = upperRow * dragSnapSlots + (dragSnapSlots - 1)
    }

    fun finishSelection() {
        if (movingSegment != null) {
            dragStart = null
            dragCurrent = null
            return
        }
        val range = selectedRange()
        if (range != null) onRangeSelected(normalizedRange(range))
        dragStart = null
        dragCurrent = null
    }

    val slots = store.slotsForDay(day)
    val labels = store.labelsForDay(day)
    val projectMap = snap.projects.associateBy { it.id }
    val segments = remember(slots, labels, projectMap) { timeSegments(slots, labels, projectMap) }

    // Text paints (native canvas).
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.GRAY
            textSize = 24f
            textAlign = Paint.Align.RIGHT
        }
    }
    val segmentLabelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
            textSize = 24f
            textAlign = Paint.Align.LEFT
        }
    }
    val nowPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.RED
            textSize = 22f
            textAlign = Paint.Align.RIGHT
        }
    }

    val nowFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(java.util.Locale.US) }

    val totalHeightDp = with(density) { (rowHeightPx * rowsPerDay + gridOffsetY).toDp() }

    // Capture theme colors outside the draw scope (MaterialTheme is @Composable).
    val selectionFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val selectionStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .onGloballyPositioned { coords -> onGridPositioned(coords.boundsInRoot()) }
            .verticalScroll(scroll),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeightDp)
                .pointerInput(day, snap.lastUpdatedEpochMillis) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x < baseX) {
                            // Let label column be scroll-only.
                            return@awaitEachGesture
                        }

                        dragStartLocation = down.position
                        dragMode = null
                        ignoreDrag = false
                        lastTranslation = Offset.Zero
                        val startPos = down.position

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val translation = change.position - startPos
                            lastTranslation = translation
                            if (dragStartLocation == null) dragStartLocation = startPos

                            if (dragMode == null) {
                                val horizontal = abs(translation.x)
                                val vertical = abs(translation.y)
                                if (horizontal < 6f && vertical < 6f) {
                                    continue
                                }
                                if (horizontal > vertical) {
                                    ignoreDrag = true
                                    // Do not consume; parent (day swipe) may handle.
                                    continue
                                }
                                val index = slotIndex(change.position.y)
                                val segment = segmentAt(index)
                                if (segment != null) {
                                    dragMode = DragMode.MOVE
                                    beginMoveIfNeeded(segment)
                                } else {
                                    dragMode = DragMode.SELECTION
                                    val startRow = rowIndex(change.position.y)
                                    val startIndex = startRow * dragSnapSlots
                                    dragStart = startIndex
                                    dragCurrent = startIndex + (dragSnapSlots - 1)
                                }
                            }

                            if (ignoreDrag) continue

                            when (dragMode) {
                                DragMode.MOVE -> updateMove(translation)
                                DragMode.SELECTION -> updateSelection(change.position)
                                null -> Unit
                            }
                            change.consume()
                        }

                        // End gesture.
                        if (ignoreDrag) {
                            ignoreDrag = false
                            dragMode = null
                            lastTranslation = Offset.Zero
                            dragStartLocation = null
                            return@awaitEachGesture
                        }

                        if (dragMode == null) {
                            val location = dragStartLocation
                            if (location != null) {
                                val index = slotIndex(location.y)
                                val segment = segmentAt(index)
                                if (segment != null) {
                                    onRangeSelected(segment.startIndex..segment.endIndex)
                                }
                            }
                            lastTranslation = Offset.Zero
                            dragStartLocation = null
                            return@awaitEachGesture
                        }

                        when (dragMode) {
                            DragMode.MOVE -> {
                                val segment = movingSegment
                                if (abs(movingOffset) == 0 &&
                                    abs(lastTranslation.y) < 4f &&
                                    abs(lastTranslation.x) < 4f &&
                                    segment != null
                                ) {
                                    onRangeSelected(segment.startIndex..segment.endIndex)
                                    movingSegment = null
                                    movingOffset = 0
                                } else {
                                    finishMove()
                                }
                            }
                            DragMode.SELECTION -> finishSelection()
                            null -> Unit
                        }

                        dragMode = null
                        lastTranslation = Offset.Zero
                        dragStartLocation = null
                    }
                },
        ) {
            val totalHeight = rowHeightPx * rowsPerDay + gridOffsetY
            val timeWidth = max(0f, size.width - baseX)

            // Background grid (labels + lines).
            for (rowIndex in 0 until rowsPerDay) {
                val rowTop = rowIndex * rowHeightPx
                val lineY = gridOffsetY + rowIndex * rowHeightPx
                val base = android.graphics.Color.GRAY
                val alpha = if (rowIndex % 2 == 0) 0.35f else 0.18f
                val lineColor = Color(android.graphics.Color.argb((alpha * 255).toInt(), 128, 128, 128))
                val lineHeight = if (rowIndex % 2 == 0) 1.5f else 1f
                drawLine(
                    color = lineColor,
                    start = Offset(baseX, lineY),
                    end = Offset(baseX + timeWidth, lineY),
                    strokeWidth = lineHeight,
                )

                if (rowIndex % 2 == 0) {
                    val hour = rowIndex / 2
                    val label = String.format(java.util.Locale.US, "%02d:00", hour)
                    // Baseline: approximate middle of row.
                    val baselineY = rowTop + rowHeightPx * 0.72f
                    labelPaint.textSize = rowHeightPx * 0.5f
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        labelWidthPx,
                        baselineY,
                        labelPaint,
                    )
                }
            }

            // Segments.
            segments.forEach { segment ->
                val isMoving = movingSegment?.matches(segment) == true
                val alpha = if (isMoving) 0.05f else 1f
                drawSegment(
                    segment = segment,
                    range = segment.startIndex..segment.endIndex,
                    baseX = baseX,
                    timeWidth = timeWidth,
                    slotHeightPx = slotHeightPx,
                    baseOffsetY = gridOffsetY,
                    alpha = alpha,
                    labelPaint = segmentLabelPaint,
                )
            }

            val moving = movingSegment
            if (moving != null) {
                val range = movingRange(moving)
                if (range != null) {
                    drawSegment(
                        segment = moving,
                        range = range,
                        baseX = baseX,
                        timeWidth = timeWidth,
                        slotHeightPx = slotHeightPx,
                        baseOffsetY = gridOffsetY,
                        alpha = 0.9f,
                        labelPaint = segmentLabelPaint,
                    )
                }
            }

            // Selection overlay.
            val selection = selectedRange()
            if (selection != null) {
                val start = min(selection.first, selection.last)
                val end = max(selection.first, selection.last)
                val height = (end - start + 1) * slotHeightPx
                val offsetY = start * slotHeightPx + gridOffsetY
                val radius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                drawRoundRect(
                    color = selectionFill,
                    topLeft = Offset(baseX, offsetY),
                    size = androidx.compose.ui.geometry.Size(timeWidth, height),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    color = selectionStroke,
                    topLeft = Offset(baseX, offsetY),
                    size = androidx.compose.ui.geometry.Size(timeWidth, height),
                    cornerRadius = radius,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            // Now line (today only).
            if (day == LocalDate.now()) {
                val minutes = now.hour * 60 + now.minute
                val rawOffset = (minutes / 15f) * slotHeightPx + gridOffsetY
                val lineY = rawOffset.coerceIn(gridOffsetY, totalHeight)
                drawLine(
                    color = androidx.compose.ui.graphics.Color.Red,
                    start = Offset(0f, lineY),
                    end = Offset(size.width, lineY),
                    strokeWidth = 2.dp.toPx(),
                )
                val labelY = max(lineY - 16.dp.toPx(), 0f)
                val label = now.format(nowFormatter)
                nowPaint.textSize = 22f
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    labelWidthPx,
                    labelY + 18f,
                    nowPaint,
                )
            }
        }
    }
}

private enum class DragMode { SELECTION, MOVE }

private data class TimeSegment(
    val startIndex: Int,
    val endIndex: Int,
    val project: Project,
    val label: String?,
) {
    fun matches(other: TimeSegment): Boolean {
        return project.id == other.project.id &&
            startIndex == other.startIndex &&
            endIndex == other.endIndex &&
            label == other.label
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSegment(
    segment: TimeSegment,
    range: IntRange,
    baseX: Float,
    timeWidth: Float,
    slotHeightPx: Float,
    baseOffsetY: Float,
    alpha: Float,
    labelPaint: Paint,
) {
    val start = range.first
    val end = range.last
    val height = (end - start + 1) * slotHeightPx
    val offsetY = start * slotHeightPx + baseOffsetY
    val radius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
    val color = colorFromHex(segment.project.colorHex).copy(alpha = alpha)
    drawRoundRect(
        color = color,
        topLeft = Offset(baseX, offsetY),
        size = androidx.compose.ui.geometry.Size(timeWidth, height),
        cornerRadius = radius,
    )
    val label = segment.label?.takeIf { it.isNotBlank() } ?: return
    labelPaint.textSize = 24f
    val paddingX = 6.dp.toPx()
    val paddingY = 4.dp.toPx()
    drawContext.canvas.nativeCanvas.drawText(
        label,
        baseX + paddingX,
        offsetY + paddingY + 22f,
        labelPaint,
    )
}
