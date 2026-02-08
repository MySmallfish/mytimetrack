package il.co.simplevision.timetrack.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun SignatureCaptureDialog(
    initialPng: ByteArray?,
    onDismiss: () -> Unit,
    onSave: (ByteArray) -> Unit,
) {
    var lines by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentLine by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showEmptyAlert by remember { mutableStateOf(false) }
    var initialBitmap by remember(initialPng) {
        mutableStateOf(initialPng?.let { BitmapFactory.decodeByteArray(it, 0, it.size) })
    }

    fun hasPoints(): Boolean {
        return lines.any { it.isNotEmpty() } || currentLine.isNotEmpty()
    }

    fun clear() {
        lines = emptyList()
        currentLine = emptyList()
        initialBitmap = null
    }

    fun save() {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) {
            showEmptyAlert = true
            return
        }
        if (!hasPoints()) {
            val png = initialPng
            if (png != null && png.isNotEmpty()) {
                onSave(png)
                return
            }
            showEmptyAlert = true
            return
        }
        val png = renderSignaturePng(
            lines = lines + (if (currentLine.isEmpty()) emptyList() else listOf(currentLine)),
            size = canvasSize,
            base = initialBitmap,
        )
        if (png == null) {
            showEmptyAlert = true
            return
        }
        onSave(png)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Signature", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = ::save) { Text("Save") }
                }
                Divider()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    SignaturePad(
                        lines = lines,
                        currentLine = currentLine,
                        initialBitmap = initialBitmap,
                        onLineChanged = { currentLine = it },
                        onLineFinished = { line ->
                            if (line.isNotEmpty()) {
                                lines = lines + listOf(line)
                            }
                            currentLine = emptyList()
                        },
                        onSizeChanged = { canvasSize = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Sign with your finger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        TextButton(onClick = ::clear) { Text("Clear") }
                    }
                }
            }
        }
    }

    if (showEmptyAlert) {
        AlertDialog(
            onDismissRequest = { showEmptyAlert = false },
            title = { Text("Signature is empty") },
            text = { Text("Please sign before saving.") },
            confirmButton = { TextButton(onClick = { showEmptyAlert = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun SignaturePad(
    lines: List<List<Offset>>,
    currentLine: List<Offset>,
    initialBitmap: Bitmap?,
    onLineChanged: (List<Offset>) -> Unit,
    onLineFinished: (List<Offset>) -> Unit,
    onSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .onSizeChanged(onSizeChanged)
            .pointerInput(Unit) {
                var activeLine: MutableList<Offset> = mutableListOf()
                detectDragGestures(
                    onDragStart = { start ->
                        activeLine = mutableListOf(start)
                        onLineChanged(activeLine.toList())
                    },
                    onDrag = { change, _ ->
                        activeLine.add(change.position)
                        onLineChanged(activeLine.toList())
                        change.consume()
                    },
                    onDragEnd = {
                        onLineFinished(activeLine.toList())
                        activeLine = mutableListOf()
                    },
                    onDragCancel = {
                        onLineFinished(activeLine.toList())
                        activeLine = mutableListOf()
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Base image (if any).
            if (initialBitmap != null) {
                val bmp = initialBitmap.asImageBitmap()
                val scale = minOf(size.width / bmp.width.toFloat(), size.height / bmp.height.toFloat())
                val w = bmp.width * scale
                val h = bmp.height * scale
                val left = (size.width - w) / 2f
                val top = (size.height - h) / 2f
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(w.toInt(), h.toInt()),
                )
            }

            val path = Path()
            (lines + (if (currentLine.isEmpty()) emptyList() else listOf(currentLine))).forEach { line ->
                val first = line.firstOrNull() ?: return@forEach
                path.moveTo(first.x, first.y)
                line.drop(1).forEach { p -> path.lineTo(p.x, p.y) }
            }
            drawPath(
                path = path,
                color = Color.Black,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        }
    }
}

private fun renderSignaturePng(
    lines: List<List<Offset>>,
    size: IntSize,
    base: Bitmap?,
): ByteArray? {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    if (base != null) {
        val rect = aspectFitRect(base.width, base.height, size.width, size.height)
        canvas.drawBitmap(base, null, rect, null)
    }

    val paint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.BLACK
        strokeWidth = 2f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    lines.forEach { line ->
        if (line.isEmpty()) return@forEach
        for (i in 0 until line.size - 1) {
            val p1 = line[i]
            val p2 = line[i + 1]
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }
    }

    val out = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

private fun aspectFitRect(imageW: Int, imageH: Int, canvasW: Int, canvasH: Int): android.graphics.RectF {
    if (imageW <= 0 || imageH <= 0) return android.graphics.RectF(0f, 0f, canvasW.toFloat(), canvasH.toFloat())
    val scale = minOf(canvasW.toFloat() / imageW.toFloat(), canvasH.toFloat() / imageH.toFloat())
    val w = imageW * scale
    val h = imageH * scale
    val left = (canvasW - w) / 2f
    val top = (canvasH - h) / 2f
    return android.graphics.RectF(left, top, left + w, top + h)
}
