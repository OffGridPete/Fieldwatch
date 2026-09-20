package app.fieldwatch.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.fieldwatch.domain.DebriefDoc
import app.fieldwatch.domain.ExtraAttentionHit
import java.io.File

/**
 * Letter-size field debrief. Android [PdfDocument] allows only one open page
 * at a time, so content is measured first, packed into pages, then each page
 * is drawn and finished before the next starts.
 */
object DebriefPdf {
    private const val PAGE_W = 612
    private const val PAGE_H = 792
    private const val MARGIN = 48f
    private const val HEADER_H = 40f
    private const val FOOTER_H = 36f
    private val INK = Color.parseColor("#12171C")
    private val MUTED = Color.parseColor("#4A5560")
    private val PHOS = Color.parseColor("#0B7A48")
    private val HEADER_BG = Color.parseColor("#063D26")
    private val ALERT_BG = Color.parseColor("#FFF6E5")
    private val ALERT_BAR = Color.parseColor("#C47A00")
    private val RULE = Color.parseColor("#C5CDD4")
    private val TAKE_BG = Color.parseColor("#E8F5EE")
    private val META_RULE = Color.parseColor("#E2E8ED")
    private val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()
    private val BODY_TOP = HEADER_H + 18f
    private val BODY_BOT = PAGE_H - FOOTER_H - 8f
    private val USABLE = BODY_BOT - BODY_TOP

    fun write(doc: DebriefDoc, file: File) {
        file.parentFile?.mkdirs()
        val blocks = layoutBlocks(doc)
        val pages = paginate(blocks)
        val pdf = PdfDocument()
        pages.forEachIndexed { i, pageBlocks ->
            val page = pdf.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, i + 1).create(),
            )
            val canvas = page.canvas
            var y = BODY_TOP
            for (block in pageBlocks) {
                block.draw(canvas, y)
                y += block.height
            }
            stampChrome(canvas, i + 1, pages.size, doc)
            pdf.finishPage(page)
        }
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
    }

    private class Block(
        val height: Float,
        val keepWithNext: Boolean = false,
        val draw: (Canvas, Float) -> Unit,
    )

    private fun layoutBlocks(doc: DebriefDoc): List<Block> {
        val out = ArrayList<Block>()
        out += titleBlock(doc.pdfTitle)
        out += spacer(6f)
        out += sectionHead("", "Disclaimer", alert = false)
        doc.disclaimer.split("\n\n").forEach { para ->
            chunkText(para.trim().ifBlank { " " }, CONTENT_W, 9f, muted = true).forEach { sl ->
                out += textBlock(sl)
            }
            out += spacer(6f)
        }
        out += ruleBlock()
        doc.meta.forEachIndexed { i, (key, value) ->
            out += metaRow(key, value, zebra = i % 2 == 0)
        }
        out += spacer(8f)
        for (section in doc.sections) {
            if (section.title == "Extra attention" && doc.extraAttention.isNotEmpty()) {
                out += sectionHead(section.number, section.title, alert = true)
                out += spacer(4f)
                doc.extraAttention.forEach { hit ->
                    out += attentionNoteBlock(hit)
                    out += spacer(8f)
                }
                continue
            }
            val paras = section.body.split('\n')
            out += sectionHead(section.number, section.title, section.alert)
            if (paras.isEmpty()) {
                out += spacer(10f)
                continue
            }
            paras.forEachIndexed { i, para ->
                val width = CONTENT_W - if (section.alert) 12 else 0
                chunkText(para.ifBlank { " " }, width, 9.5f, muted = false).forEach { sl ->
                    out += bodyBlock(sl, section.alert)
                }
                if (i == paras.lastIndex) out += spacer(12f)
            }
        }
        out += takeawayBlock(doc.takeaway)
        return out
    }

    private fun paginate(blocks: List<Block>): List<List<Block>> {
        val pages = ArrayList<List<Block>>()
        var current = ArrayList<Block>()
        var used = 0f
        fun flush() {
            if (current.isEmpty()) return
            pages += current
            current = ArrayList()
            used = 0f
        }
        for (i in blocks.indices) {
            val block = blocks[i]
            val h = block.height
            val need = if (block.keepWithNext && i + 1 < blocks.size) {
                h + blocks[i + 1].height.coerceAtMost(28f)
            } else h
            if (current.isNotEmpty() && used + need > USABLE) flush()
            if (h > USABLE && current.isEmpty()) {
                current += block
                flush()
                continue
            }
            current += block
            used += h
        }
        flush()
        if (pages.isEmpty()) pages += emptyList<Block>()
        return pages
    }

    private fun titleBlock(text: String) = Block(26f) { canvas, y ->
        val p = Paint().apply {
            color = INK
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(text, MARGIN, y + 16f, p)
    }

    private fun metaRow(key: String, value: String, zebra: Boolean): Block {
        val valueLayout = layout(value, CONTENT_W - 96, 9f, muted = false)
        val h = maxOf(16f, valueLayout.height + 8f)
        return Block(h) { canvas, y ->
            if (zebra) {
                val fill = Paint().apply { color = META_RULE; style = Paint.Style.FILL }
                canvas.drawRect(MARGIN - 4f, y, PAGE_W - MARGIN + 4f, y + h, fill)
            }
            val k = Paint().apply {
                color = PHOS
                textSize = 8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(key.uppercase(), MARGIN + 4f, y + 12f, k)
            canvas.save()
            canvas.translate(MARGIN + 96f, y + 4f)
            valueLayout.draw(canvas)
            canvas.restore()
        }
    }

    private fun ruleBlock() = Block(14f) { canvas, y ->
        val rule = Paint().apply { color = RULE; strokeWidth = 0.8f }
        canvas.drawLine(MARGIN, y + 6f, PAGE_W - MARGIN, y + 6f, rule)
    }

    private fun spacer(h: Float) = Block(h) { _, _ -> }

    private fun textBlock(sl: StaticLayout) = Block(sl.height + 4f) { canvas, y ->
        canvas.save()
        canvas.translate(MARGIN, y)
        sl.draw(canvas)
        canvas.restore()
    }

    private fun sectionHead(number: String, title: String, alert: Boolean) = Block(
        height = 22f,
        keepWithNext = true,
    ) { canvas, y ->
        if (alert) {
            val fill = Paint().apply { color = ALERT_BG; style = Paint.Style.FILL }
            val bar = Paint().apply { color = ALERT_BAR; style = Paint.Style.FILL }
            canvas.drawRect(MARGIN - 6f, y, PAGE_W - MARGIN + 6f, y + 22f, fill)
            canvas.drawRect(MARGIN - 6f, y, MARGIN - 2f, y + 22f, bar)
        }
        val p = Paint().apply {
            color = if (alert) ALERT_BAR else PHOS
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val label = if (number.isBlank()) title.uppercase() else "$number  ${title.uppercase()}"
        canvas.drawText(label, MARGIN + if (alert) 10f else 0f, y + 15f, p)
    }

    private fun bodyBlock(sl: StaticLayout, alert: Boolean): Block {
        val h = sl.height + 4f
        val indent = if (alert) 10f else 0f
        return Block(h) { canvas, y ->
            if (alert) {
                val fill = Paint().apply { color = ALERT_BG; style = Paint.Style.FILL }
                val bar = Paint().apply { color = ALERT_BAR; style = Paint.Style.FILL }
                canvas.drawRect(MARGIN - 6f, y, PAGE_W - MARGIN + 6f, y + h, fill)
                canvas.drawRect(MARGIN - 6f, y, MARGIN - 2f, y + h, bar)
            }
            canvas.save()
            canvas.translate(MARGIN + indent, y)
            sl.draw(canvas)
            canvas.restore()
        }
    }

    private fun attentionNoteBlock(hit: ExtraAttentionHit): Block {
        val innerW = CONTENT_W - 24
        val radio = layout(hit.radioLabel, innerW, 9f, muted = false, bold = true)
        val note = layout(hit.note, innerW, 9.5f, muted = false)
        val foot = layout("Pattern match, not identity. Not a safety finding.", innerW, 8f, muted = true)
        val h = 22f + radio.height + 6f + note.height + 8f + foot.height + 12f
        return Block(h) { canvas, y ->
            val box = RectF(MARGIN - 6f, y, PAGE_W - MARGIN + 6f, y + h - 4f)
            val fill = Paint().apply { color = ALERT_BG; style = Paint.Style.FILL }
            val bar = Paint().apply { color = ALERT_BAR; style = Paint.Style.FILL }
            canvas.drawRoundRect(box, 4f, 4f, fill)
            canvas.drawRect(box.left, box.top, box.left + 4f, box.bottom, bar)
            val kicker = Paint().apply {
                color = ALERT_BAR
                textSize = 8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
                letterSpacing = 0.06f
            }
            val label = "EXTRA ATTENTION  ·  ${hit.signature}".uppercase()
            canvas.drawText(label, MARGIN + 10f, y + 14f, kicker)
            var ty = y + 20f
            canvas.save()
            canvas.translate(MARGIN + 10f, ty)
            radio.draw(canvas)
            canvas.restore()
            ty += radio.height + 6f
            canvas.save()
            canvas.translate(MARGIN + 10f, ty)
            note.draw(canvas)
            canvas.restore()
            ty += note.height + 6f
            canvas.save()
            canvas.translate(MARGIN + 10f, ty)
            foot.draw(canvas)
            canvas.restore()
        }
    }

    private fun takeawayBlock(text: String): Block {
        val body = layout(text, CONTENT_W - 20, 10f, muted = false, bold = true)
        val h = body.height + 32f
        return Block(h) { canvas, y ->
            val box = RectF(MARGIN - 6f, y, PAGE_W - MARGIN + 6f, y + h - 4f)
            val fill = Paint().apply { color = TAKE_BG; style = Paint.Style.FILL }
            val bar = Paint().apply { color = PHOS; style = Paint.Style.FILL }
            canvas.drawRoundRect(box, 4f, 4f, fill)
            canvas.drawRect(box.left, box.top, box.left + 4f, box.bottom, bar)
            val k = Paint().apply {
                color = PHOS
                textSize = 8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("TAKEAWAY", MARGIN + 10f, y + 14f, k)
            canvas.save()
            canvas.translate(MARGIN + 10f, y + 20f)
            body.draw(canvas)
            canvas.restore()
        }
    }

    private fun stampChrome(canvas: Canvas, page: Int, total: Int, doc: DebriefDoc) {
        val bg = Paint().apply { color = HEADER_BG; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), HEADER_H, bg)
        val title = Paint().apply {
            color = Color.parseColor("#3DFF9A")
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.12f
        }
        canvas.drawText("FIELDWATCH", MARGIN, 26f, title)
        val sub = Paint().apply {
            color = Color.parseColor("#C8D0D8")
            textSize = 9f
            isAntiAlias = true
            letterSpacing = 0.08f
        }
        val label = doc.pdfKicker
        canvas.drawText(label, PAGE_W - MARGIN - sub.measureText(label), 26f, sub)
        if (doc.trackingAlert) {
            val alert = Paint().apply { color = ALERT_BAR; style = Paint.Style.FILL }
            canvas.drawRect(0f, HEADER_H, PAGE_W.toFloat(), HEADER_H + 3f, alert)
        }
        val foot = Paint().apply { color = RULE; strokeWidth = 0.6f }
        canvas.drawLine(MARGIN, PAGE_H - FOOTER_H, PAGE_W - MARGIN, PAGE_H - FOOTER_H, foot)
        val f = Paint().apply {
            color = MUTED
            textSize = 8f
            isAntiAlias = true
        }
        canvas.drawText("Off Grid Pete LLC  ·  operationally sensitive", MARGIN, PAGE_H - 18f, f)
        val pn = "$page / $total"
        canvas.drawText(pn, PAGE_W - MARGIN - f.measureText(pn), PAGE_H - 18f, f)
    }

    private fun chunkText(
        text: String,
        width: Int,
        size: Float,
        muted: Boolean,
    ): List<StaticLayout> {
        val full = layout(text, width, size, muted)
        val maxH = USABLE - 8f
        if (full.height <= maxH) return listOf(full)
        val chunks = ArrayList<StaticLayout>()
        var startLine = 0
        while (startLine < full.lineCount) {
            var endLine = startLine
            var h = 0
            while (endLine < full.lineCount) {
                val lh = full.getLineBottom(endLine) - full.getLineTop(endLine)
                if (h + lh > maxH && endLine > startLine) break
                h += lh
                endLine++
            }
            if (endLine == startLine) endLine++
            val start = full.getLineStart(startLine)
            val end = full.getLineEnd(endLine - 1)
            chunks += layout(text.substring(start, end).trimEnd().ifEmpty { " " }, width, size, muted)
            startLine = endLine
        }
        return chunks.ifEmpty { listOf(full) }
    }

    private fun layout(
        text: String,
        width: Int,
        size: Float,
        muted: Boolean,
        bold: Boolean = false,
    ): StaticLayout {
        val tp = TextPaint().apply {
            color = if (muted) MUTED else INK
            textSize = size
            isAntiAlias = true
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, tp, width.coerceAtLeast(40))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1f)
            .setIncludePad(false)
            .build()
    }
}
