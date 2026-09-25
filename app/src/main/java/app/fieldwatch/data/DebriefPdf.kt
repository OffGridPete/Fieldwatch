package app.fieldwatch.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.fieldwatch.domain.DebriefDoc
import app.fieldwatch.domain.ExtraAttentionHit
import app.fieldwatch.domain.SitPathPlot
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
    private val PLOT_PANEL = Color.parseColor("#F4F7F5")
    private val PLOT_INNER = Color.parseColor("#FFFFFF")
    private val PLOT_GRID = Color.parseColor("#E4EBE6")
    private val PATH_OTHER = Color.parseColor("#4A6FA5")
    private val DOT_NAMED = Color.parseColor("#1F4E79")
    private val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()
    private val BODY_TOP = HEADER_H + 18f
    private val BODY_BOT = PAGE_H - FOOTER_H - 8f
    private val USABLE = BODY_BOT - BODY_TOP

    fun write(doc: DebriefDoc, file: File, onProgress: (Float) -> Unit = {}) {
        file.parentFile?.mkdirs()
        onProgress(0.08f)
        val blocks = layoutBlocks(doc)
        onProgress(0.18f)
        val pages = paginate(blocks)
        val pdf = PdfDocument()
        val n = pages.size.coerceAtLeast(1)
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
            onProgress(0.18f + 0.72f * (i + 1).toFloat() / n)
        }
        onProgress(0.94f)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f)
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
        val figure = doc.pathFigure
        if (figure != null && figure.drawable) {
            out += pathFigureBlock(figure)
            out += spacer(4f)
            pathKeyBlocks(figure).forEach { out += it }
            out += spacer(10f)
        }
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
            out += sectionHead(section.number, section.title, section.alert)
            val paras = section.body.split('\n')
            if (paras.isEmpty()) {
                out += spacer(10f)
                continue
            }
            val width = CONTENT_W - if (section.alert) 12 else 0
            paras.forEachIndexed { i, raw ->
                val para = raw.trimEnd()
                when {
                    para.isBlank() -> out += spacer(5f)
                    isStayHead(para) -> {
                        if (i > 0) out += spacer(8f)
                        out += subheadBlock(para.trim(), alert = section.alert)
                    }
                    isKickerLine(para) -> {
                        if (i > 0) out += spacer(6f)
                        out += kickerLineBlock(para.trim(), section.alert)
                    }
                    isBullet(para) -> out += bulletBlock(para.trimStart().removePrefix("·").trimStart().removePrefix("•").trim(), section.alert)
                    else -> chunkText(para, width, 9.5f, muted = false).forEach { sl ->
                        out += bodyBlock(sl, section.alert)
                    }
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

    private fun figurePlotSize(): Pair<Float, Float> = (CONTENT_W - 20f) to 186f

    private fun pathFigureBlock(fig: SitPathPlot.Figure): Block {
        val two = fig.tracks.size == 2
        val headerH = if (two) 36f else 24f
        val (plotW, plotH) = figurePlotSize()
        val cap = layout(fig.caption, CONTENT_W - 24, 8f, muted = true)
        val scaleH = 20f
        val h = headerH + plotH + 14f + scaleH + 8f + cap.height + 12f
        return Block(h) { canvas, y ->
            val panel = RectF(MARGIN, y, PAGE_W - MARGIN, y + h)
            val fill = Paint().apply { color = PLOT_PANEL; style = Paint.Style.FILL; isAntiAlias = true }
            val stroke = Paint().apply {
                color = RULE; style = Paint.Style.STROKE; strokeWidth = 0.9f; isAntiAlias = true
            }
            canvas.drawRoundRect(panel, 7f, 7f, fill)
            canvas.drawRoundRect(panel, 7f, 7f, stroke)
            val kicker = Paint().apply {
                color = PHOS
                textSize = 8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
                letterSpacing = 0.12f
            }
            canvas.drawText(fig.kicker, MARGIN + 12f, y + 16f, kicker)
            val stats = Paint().apply { color = MUTED; textSize = 8f; isAntiAlias = true }
            val span = if (fig.spanM >= 1000) {
                "${"%.1f".format(java.util.Locale.US, fig.spanM / 1000)} km span"
            } else {
                "${fig.spanM.toInt()} m span"
            }
            val len = if (fig.lengthM >= 1000) {
                "${"%.1f".format(java.util.Locale.US, fig.lengthM / 1000)} km path"
            } else {
                "${fig.lengthM.toInt()} m path"
            }
            val right = "$len  ·  $span"
            canvas.drawText(right, PAGE_W - MARGIN - 12f - stats.measureText(right), y + 16f, stats)
            if (fig.tracks.size == 2) {
                val lg = Paint().apply { color = MUTED; textSize = 7.5f; isAntiAlias = true }
                canvas.drawText(
                    "Green = this sit   ·   Slate = second sit   ·   Gold = Extra attention",
                    MARGIN + 12f,
                    y + 28f,
                    lg,
                )
            }
            val plotTop = y + headerH
            val plot = RectF(MARGIN + 10f, plotTop, MARGIN + 10f + plotW, plotTop + plotH)
            val inner = Paint().apply { color = PLOT_INNER; style = Paint.Style.FILL }
            canvas.drawRect(plot, inner)
            val grid = Paint().apply { color = PLOT_GRID; strokeWidth = 0.6f }
            for (i in 1..3) {
                val gx = plot.left + plot.width() * i / 4f
                val gy = plot.top + plot.height() * i / 4f
                canvas.drawLine(gx, plot.top, gx, plot.bottom, grid)
                canvas.drawLine(plot.left, gy, plot.right, gy, grid)
            }
            canvas.drawRect(plot, stroke)
            val all = fig.tracks.flatMap { it.samples }
            val model = SitPathPlot.Model(
                samples = all,
                dots = fig.dots,
                lengthM = fig.lengthM,
                spanM = fig.spanM,
                title = fig.kicker,
            )
            val lay = SitPathPlot.layout(model, plot.width(), plot.height(), pad = 16f)
            if (lay != null) {
                fun ox(x: Float) = plot.left + x
                fun oy(y: Float) = plot.top + y
                fig.tracks.forEach { track ->
                    if (track.samples.size < 2) return@forEach
                    val pth = Path()
                    val first = lay.project(track.samples[0].lat, track.samples[0].lon)
                    pth.moveTo(ox(first.x), oy(first.y))
                    for (i in 1 until track.samples.size) {
                        val pt = lay.project(track.samples[i].lat, track.samples[i].lon)
                        pth.lineTo(ox(pt.x), oy(pt.y))
                    }
                    val tp = Paint().apply {
                        color = if (track.secondary) PATH_OTHER else PHOS
                        style = Paint.Style.STROKE
                        strokeWidth = if (track.secondary) 1.6f else 2.1f
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        isAntiAlias = true
                        if (track.secondary) pathEffect = DashPathEffect(floatArrayOf(8f, 5f), 0f)
                    }
                    canvas.drawPath(pth, tp)
                    val start = lay.project(track.samples.first().lat, track.samples.first().lon)
                    val end = lay.project(track.samples.last().lat, track.samples.last().lon)
                    val disc = Paint().apply {
                        color = if (track.secondary) PATH_OTHER else PHOS
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawCircle(ox(start.x), oy(start.y), 3.2f, disc)
                    canvas.drawCircle(ox(end.x), oy(end.y), 4.4f, disc)
                }
                val num = Paint().apply {
                    color = Color.WHITE
                    textSize = 7f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                val piles = SitPathPlot.clusters(lay.dots)
                piles.forEachIndexed { i, pile ->
                    val extra = pile.members.any { it.dot.extraAttention }
                    val fillD = Paint().apply {
                        color = if (extra) ALERT_BAR else DOT_NAMED
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val r = if (pile.stacked) 8.5f else 7f
                    canvas.drawCircle(ox(pile.center.x), oy(pile.center.y), r, fillD)
                    canvas.drawText("${i + 1}", ox(pile.center.x), oy(pile.center.y) + 2.5f, num)
                }
                val nP = Paint().apply {
                    color = MUTED
                    textSize = 8f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    isAntiAlias = true
                }
                canvas.drawText("N", plot.right - 14f, plot.top + 14f, nP)
                val barW = plot.width() * lay.scaleBarFrac
                val label = if (lay.scaleBarM >= 1000) {
                    "${(lay.scaleBarM / 1000).toInt()} km"
                } else {
                    "${lay.scaleBarM.toInt()} m"
                }
                val barPaint = Paint().apply {
                    color = MUTED
                    strokeWidth = 1.6f
                    isAntiAlias = true
                }
                val lab = Paint().apply { color = MUTED; textSize = 8f; isAntiAlias = true }
                val group = barW + 6f + lab.measureText(label)
                val bx = plot.centerX() - group / 2f
                val by = plot.bottom + 16f
                canvas.drawLine(bx, by, bx + barW, by, barPaint)
                canvas.drawLine(bx, by - 3.5f, bx, by + 3.5f, barPaint)
                canvas.drawLine(bx + barW, by - 3.5f, bx + barW, by + 3.5f, barPaint)
                canvas.drawText(label, bx + barW + 6f, by + 3f, lab)
            }
            canvas.save()
            canvas.translate(MARGIN + 12f, plot.bottom + 14f + scaleH)
            cap.draw(canvas)
            canvas.restore()
        }
    }

    private fun pathKeyBlocks(fig: SitPathPlot.Figure): List<Block> {
        val all = fig.tracks.flatMap { it.samples }
        val model = SitPathPlot.Model(
            samples = all,
            dots = fig.dots,
            lengthM = fig.lengthM,
            spanM = fig.spanM,
            title = fig.kicker,
        )
        val (plotW, plotH) = figurePlotSize()
        val lay = SitPathPlot.layout(model, plotW, plotH, pad = 16f) ?: return emptyList()
        val piles = SitPathPlot.clusters(lay.dots)
        if (piles.isEmpty()) return emptyList()
        val out = ArrayList<Block>()
        out += sectionHead("", "Path key", alert = false)
        out += spacer(4f)
        piles.forEachIndexed { i, pile ->
            out += pathKeyRow(i + 1, pile)
            out += spacer(5f)
        }
        return out
    }

    private fun pathKeyLine(n: Int, pile: SitPathPlot.Cluster): String {
        val radios = pile.members.joinToString("  ·  ") { m ->
            val d = m.dot
            val kind = if (d.kind.name == "WIFI") "WIFI" else "BLE"
            val tag = if (d.extraAttention) "Extra attention" else null
            val fleets = d.fleetNames.filter { it.isNotBlank() }.joinToString(", ")
            val obs = d.observerNotes.trim().takeIf { it.isNotEmpty() }?.let { "Observer: $it" }
            listOfNotNull(kind, d.label.ifBlank { d.mac }, fleets.ifBlank { null }, tag, obs)
                .joinToString(" ")
        }
        return if (pile.stacked) {
            "$n  ${pile.members.size} radios at this stop — $radios"
        } else {
            "$n  $radios"
        }
    }

    private fun textBlock(sl: StaticLayout) = Block(sl.height + 4f) { canvas, y ->
        canvas.save()
        canvas.translate(MARGIN, y)
        sl.draw(canvas)
        canvas.restore()
    }

    private fun isStayHead(line: String): Boolean {
        val t = line.trim()
        return t.matches(Regex("""^\d+\.\s+(Stay|Transit)\b.*""")) ||
            t.startsWith("• ")
    }

    private fun isKickerLine(line: String): Boolean {
        val t = line.trim()
        if (t.startsWith("Phone GPS")) return true
        if (t.contains(". ")) return false
        return t.matches(Regex("""^[A-Z][A-Za-z0-9 +/'()&.,-]{0,48}:(\s.*)?$"""))
    }

    private fun isBullet(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("· ") || t.startsWith("• ")
    }

    private fun subheadBlock(text: String, alert: Boolean): Block {
        val sl = layout(text, CONTENT_W - if (alert) 12 else 0, 10.5f, muted = false, bold = true)
        val h = sl.height + 6f
        return Block(h, keepWithNext = true) { canvas, y ->
            if (alert) {
                val fill = Paint().apply { color = ALERT_BG; style = Paint.Style.FILL }
                canvas.drawRect(MARGIN - 6f, y, PAGE_W - MARGIN + 6f, y + h, fill)
            }
            canvas.save()
            canvas.translate(MARGIN + if (alert) 10f else 0f, y + 2f)
            sl.draw(canvas)
            canvas.restore()
        }
    }

    private fun kickerLineBlock(text: String, alert: Boolean): Block {
        val sl = layout(text, CONTENT_W - if (alert) 12 else 0, 9f, muted = false, bold = true)
        val h = sl.height + 4f
        return Block(h) { canvas, y ->
            canvas.save()
            canvas.translate(MARGIN + if (alert) 10f else 0f, y)
            sl.draw(canvas)
            canvas.restore()
        }
    }

    private fun bulletBlock(text: String, alert: Boolean): Block {
        val sl = layout(text, CONTENT_W - 18 - if (alert) 12 else 0, 9.5f, muted = false)
        val h = sl.height + 3f
        return Block(h) { canvas, y ->
            val dot = Paint().apply { color = PHOS; style = Paint.Style.FILL; isAntiAlias = true }
            canvas.drawCircle(MARGIN + if (alert) 14f else 4f, y + 7f, 2.2f, dot)
            canvas.save()
            canvas.translate(MARGIN + 14f + if (alert) 10f else 0f, y)
            sl.draw(canvas)
            canvas.restore()
        }
    }

    private fun pathKeyRow(n: Int, pile: SitPathPlot.Cluster): Block {
        val rest = pathKeyLine(n, pile).substringAfter("  ")
        val sl = layout(rest, CONTENT_W - 28, 9f, muted = false)
        val h = maxOf(16f, sl.height + 4f)
        return Block(h) { canvas, y ->
            val num = Paint().apply {
                color = PHOS
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("$n", MARGIN, y + 11f, num)
            canvas.save()
            canvas.translate(MARGIN + 22f, y)
            sl.draw(canvas)
            canvas.restore()
        }
    }

    private fun sectionHead(number: String, title: String, alert: Boolean) = Block(
        height = 24f,
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
        if (!alert) {
            val rule = Paint().apply { color = RULE; strokeWidth = 0.6f }
            canvas.drawLine(MARGIN, y + 22f, PAGE_W - MARGIN, y + 22f, rule)
        }
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
