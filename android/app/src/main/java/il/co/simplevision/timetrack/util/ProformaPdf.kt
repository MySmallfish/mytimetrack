package il.co.simplevision.timetrack.util

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import il.co.simplevision.timetrack.data.InvoiceLanguage
import il.co.simplevision.timetrack.data.Project
import il.co.simplevision.timetrack.data.VatType
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class InvoiceStrings(
    val title: String,
    val projectLabel: String,
    val periodLabel: String,
    val dateLabel: String,
    val invoiceNumberLabel: String,
    val descriptionHeader: String,
    val hoursHeader: String,
    val rateHeader: String,
    val amountHeader: String,
    val subtotalLabel: String,
    val vatLabelFormat: String,
    val vatIncludedLabel: String,
    val totalLabel: String,
    val signatureLabel: String,
    val emailLine: String,
)

fun invoiceStrings(language: InvoiceLanguage): InvoiceStrings {
    return when (language) {
        InvoiceLanguage.HEBREW -> InvoiceStrings(
            title = "חשבונית פרו-פורמה",
            projectLabel = "פרויקט",
            periodLabel = "טווח",
            dateLabel = "תאריך",
            invoiceNumberLabel = "מספר חשבונית",
            descriptionHeader = "תיאור",
            hoursHeader = "שעות",
            rateHeader = "תעריף",
            amountHeader = "סכום",
            subtotalLabel = "סכום ביניים",
            vatLabelFormat = "מע״מ (%.0f%%)",
            vatIncludedLabel = "מע״מ (כלול)",
            totalLabel = "סה״כ",
            signatureLabel = "חתימה",
            emailLine = "מצורפים חשבונית פרו-פורמה ודוח שעות.",
        )
        InvoiceLanguage.ENGLISH -> InvoiceStrings(
            title = "Proforma Invoice",
            projectLabel = "Project",
            periodLabel = "Period",
            dateLabel = "Date",
            invoiceNumberLabel = "Invoice #",
            descriptionHeader = "Description",
            hoursHeader = "Hours",
            rateHeader = "Rate",
            amountHeader = "Amount",
            subtotalLabel = "Subtotal",
            vatLabelFormat = "VAT (%.0f%%)",
            vatIncludedLabel = "VAT (included)",
            totalLabel = "Total",
            signatureLabel = "Signature",
            emailLine = "Attached are the proforma invoice and timesheet.",
        )
    }
}

fun makeProformaPdf(
    logoPng: ByteArray,
    signaturePng: ByteArray,
    project: Project,
    header: String,
    itemDetails: String,
    hours: Double,
    rate: Double,
    subtotal: Double,
    vatAmount: Double,
    total: Double,
    date: LocalDate,
    invoiceNumber: Int,
    rangeText: String,
    vatRate: Double = 0.17,
): ByteArray? {
    val strings = invoiceStrings(project.invoiceLanguage)
    val isRtl = project.invoiceLanguage == InvoiceLanguage.HEBREW
    val locale = if (isRtl) Locale("he", "IL") else Locale.US
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val dateText = date.format(dateFormatter)

    val pageW = 595
    val pageH = 842
    val margin = 36f
    val contentW = pageW - margin * 2
    val headerH = 80f
    val logoAreaW = contentW * 0.4f
    val titleAreaW = contentW - logoAreaW

    val borderColor = 0xFFD1D1D1.toInt()
    val headerFill = 0xFFEBEBEB.toInt()
    val boxFill = 0xFFF5F5F5.toInt()

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.BLACK
        textAlign = Paint.Align.RIGHT
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        color = android.graphics.Color.BLACK
        textAlign = Paint.Align.RIGHT
    }
    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        color = android.graphics.Color.BLACK
        textAlign = if (isRtl) Paint.Align.RIGHT else Paint.Align.LEFT
    }
    val headerBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        color = android.graphics.Color.BLACK
        textAlign = Paint.Align.RIGHT
    }
    val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.BLACK
        textAlign = if (isRtl) Paint.Align.RIGHT else Paint.Align.LEFT
    }
    val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        color = android.graphics.Color.BLACK
        textAlign = Paint.Align.RIGHT
    }

    fun keyValueText(label: String, value: String): String {
        return if (isRtl) "$value :$label" else "$label: $value"
    }

    fun drawMultiline(
        canvas: android.graphics.Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int,
        align: Layout.Alignment,
    ): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    val doc = PdfDocument()
    try {
        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        var cursorY = margin

        val logo = BitmapFactory.decodeByteArray(logoPng, 0, logoPng.size)
        if (logo != null) {
            val maxLogoW = logoAreaW
            val maxLogoH = headerH
            val scale = min(min(maxLogoW / logo.width.toFloat(), maxLogoH / logo.height.toFloat()), 1f)
            val w = logo.width * scale
            val h = logo.height * scale
            canvas.drawBitmap(logo, null, android.graphics.RectF(margin, cursorY, margin + w, cursorY + h), null)
        }

        val titleXRight = margin + logoAreaW + titleAreaW
        canvas.drawText(strings.title, titleXRight, cursorY + 24f, titlePaint)
        canvas.drawText(header, titleXRight, cursorY + 52f, subtitlePaint)

        val invoiceMeta = "${strings.invoiceNumberLabel}: $invoiceNumber   ${strings.dateLabel}: $dateText"
        canvas.drawText(invoiceMeta, titleXRight, cursorY + 72f, headerBodyPaint)

        cursorY += headerH + 12f

        // Divider line.
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            strokeWidth = 1f
        }
        canvas.drawLine(margin, cursorY, pageW - margin, cursorY, linePaint)
        cursorY += 12f

        // Info box.
        val infoRect = android.graphics.RectF(margin, cursorY, margin + contentW, cursorY + 74f)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = boxFill; style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderColor; style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRect(infoRect, boxPaint)
        canvas.drawRect(infoRect, strokePaint)

        val infoX = margin + 10f
        val infoY = cursorY + 10f
        val infoWidth = (contentW - 20f).toInt()
        val align = if (isRtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        val line1 = keyValueText(strings.projectLabel, project.name)
        val line2 = keyValueText(strings.periodLabel, rangeText)
        val h1 = drawMultiline(canvas, line1, bodyPaint, infoX, infoY, infoWidth, align)
        drawMultiline(canvas, line2, bodyPaint, infoX, infoY + h1 + 4f, infoWidth, align)

        cursorY = infoRect.bottom + 16f

        // Table columns.
        val colW = floatArrayOf(
            contentW * 0.46f,
            contentW * 0.18f,
            contentW * 0.18f,
            contentW * 0.18f,
        )
        val colX = FloatArray(4)
        if (isRtl) {
            var x = pageW - margin
            for (i in 0..3) {
                x -= colW[i]
                colX[i] = x
            }
        } else {
            var x = margin
            for (i in 0..3) {
                colX[i] = x
                x += colW[i]
            }
        }

        fun drawTableRow(values: List<String>, headerRow: Boolean, fillColor: Int? = null): Float {
            val paintText = if (headerRow) boldPaint else bodyPaint
            val paintNum = if (headerRow) boldPaint else numberPaint
            val paddingY = 6f
            val paddingX = 6f

            // Compute height (wrap for description only).
            val descLayout = StaticLayout.Builder.obtain(values[0], 0, values[0].length, paintText, (colW[0] - paddingX * 2).toInt())
                .setAlignment(if (isRtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
            val rowH = max(22f, descLayout.height.toFloat()) + paddingY * 2

            if (fillColor != null) {
                val r = android.graphics.RectF(margin, cursorY, margin + contentW, cursorY + rowH)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor; style = Paint.Style.FILL }
                canvas.drawRect(r, p)
            }

            // Description column.
            canvas.save()
            canvas.translate(colX[0] + paddingX, cursorY + paddingY)
            descLayout.draw(canvas)
            canvas.restore()

            // Number columns.
            for (i in 1..3) {
                val text = values[i]
                val xRight = colX[i] + colW[i] - paddingX
                val baseline = cursorY + paddingY + 14f
                val p = if (i == 0) paintText else paintNum
                canvas.drawText(text, xRight, baseline, p.apply { textAlign = Paint.Align.RIGHT })
            }

            // Row separator.
            canvas.drawLine(margin, cursorY + rowH, margin + contentW, cursorY + rowH, linePaint.apply { strokeWidth = 0.5f })
            return rowH
        }

        cursorY += drawTableRow(
            values = listOf(strings.descriptionHeader, strings.hoursHeader, strings.rateHeader, strings.amountHeader),
            headerRow = true,
            fillColor = headerFill,
        )

        val hoursText = String.format(Locale.US, "%.2f", hours)
        val rateText = String.format(Locale.US, "$%.2f", rate)
        val amountText = String.format(Locale.US, "$%.2f", hours * rate)
        cursorY += drawTableRow(
            values = listOf(itemDetails, hoursText, rateText, amountText),
            headerRow = false,
            fillColor = null,
        )
        cursorY += 12f

        // Summary box (right side).
        val summaryW = contentW * 0.46f
        val summaryX = pageW - margin - summaryW
        val summaryRect = android.graphics.RectF(summaryX, cursorY, summaryX + summaryW, cursorY + 90f)
        canvas.drawRect(summaryRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = boxFill; style = Paint.Style.FILL })
        canvas.drawRect(summaryRect, strokePaint)

        var summaryY = summaryRect.top + 10f
        fun drawSummaryLine(label: String, value: String, bold: Boolean = false) {
            val p = if (bold) boldPaint else bodyPaint
            val text = keyValueText(label, value)
            val alignLocal = if (isRtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
            val h = drawMultiline(canvas, text, p, summaryRect.left + 10f, summaryY, (summaryRect.width() - 20f).toInt(), alignLocal)
            summaryY += max(18f, h)
        }

        drawSummaryLine(strings.subtotalLabel, String.format(Locale.US, "$%.2f", subtotal))
        if (project.vatType != VatType.NONE) {
            val vatLabel = if (project.vatType == VatType.INCLUDED) {
                strings.vatIncludedLabel
            } else {
                String.format(Locale.US, strings.vatLabelFormat, vatRate * 100)
            }
            drawSummaryLine(vatLabel, String.format(Locale.US, "$%.2f", vatAmount))
        }
        drawSummaryLine(strings.totalLabel, String.format(Locale.US, "$%.2f", total), bold = true)

        // Signature.
        val signatureTop = max(summaryRect.bottom + 24f, pageH - margin - 110f)
        val signature = BitmapFactory.decodeByteArray(signaturePng, 0, signaturePng.size)
        if (signature != null) {
            val labelAlign = if (isRtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
            drawMultiline(canvas, strings.signatureLabel, bodyPaint, margin, signatureTop, contentW.toInt(), labelAlign)

            val maxSigW = 200f
            val maxSigH = 70f
            val scale = min(min(maxSigW / signature.width.toFloat(), maxSigH / signature.height.toFloat()), 1f)
            val w = signature.width * scale
            val h = signature.height * scale
            val sigRect = android.graphics.RectF(margin, signatureTop + 20f, margin + w, signatureTop + 20f + h)
            canvas.drawBitmap(signature, null, sigRect, null)
        }

        doc.finishPage(page)

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        return out.toByteArray()
    } catch (e: Exception) {
        return null
    } finally {
        doc.close()
    }
}
