package il.co.simplevision.timetrack.util

import androidx.compose.ui.graphics.Color

fun colorFromHex(hex: String): Color {
    val trimmed = hex.trim().removePrefix("#")
    val value = trimmed.toLongOrNull(16) ?: 0L
    val rgb = when (trimmed.length) {
        3 -> {
            val r = ((value shr 8) and 0xF) * 17
            val g = ((value shr 4) and 0xF) * 17
            val b = (value and 0xF) * 17
            (r shl 16) or (g shl 8) or b
        }
        else -> value and 0xFFFFFF
    }
    val r = ((rgb shr 16) and 0xFF).toInt()
    val g = ((rgb shr 8) and 0xFF).toInt()
    val b = (rgb and 0xFF).toInt()
    return Color(r, g, b)
}

fun Color.toHex(): String {
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

