package app.fieldwatch.data

import app.fieldwatch.domain.LanguageText

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import app.fieldwatch.domain.DebriefDoc
import app.fieldwatch.domain.ExtraAttentionHit
import app.fieldwatch.domain.FlockApproach
import app.fieldwatch.domain.FlockCamera
import app.fieldwatch.domain.FlockCredit
import app.fieldwatch.domain.FlockIndex
import app.fieldwatch.domain.FlockMark
import app.fieldwatch.domain.ReportBar
import app.fieldwatch.domain.ReportChart
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.SensorKind
import app.fieldwatch.domain.SitPathPlot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/**
 * Letter-size field debrief. Android [PdfDocument] allows only one open page
 * at a time, so content is measured first, packed into pages, then each page
 * is drawn and finished before the next starts.
 */
object DebriefPdf {
    /** Debrief and Compare pages use the phone sans. That face covers Cyrillic. */
    const val PDF_SANS = "sans-serif"

    /** Characters placed on the page. Nothing is stripped, including Cyrillic. */
    fun charactersDrawn(text: String): String = text

    private const val PAGE_W = 612
    private const val PAGE_H = 792
    private const val MARGIN = 48f
    private const val HEADER_H = 40f
    private const val FOOTER_H = 36f
    private val INK by lazy { Color.parseColor("#12171C") }
    private val MUTED by lazy { Color.parseColor("#4A5560") }
    private val PHOS by lazy { Color.parseColor("#0B7A48") }
    private val HEADER_BG by lazy { Color.parseColor("#063D26") }
    private val ALERT_BG by lazy { Color.parseColor("#FFF6E5") }
    private val ALERT_BAR by lazy { Color.parseColor("#C47A00") }
    private val RULE by lazy { Color.parseColor("#C5CDD4") }
    private val TAKE_BG by lazy { Color.parseColor("#E8F5EE") }
    private val META_RULE by lazy { Color.parseColor("#E2E8ED") }
    private val PLOT_PANEL by lazy { Color.parseColor("#F4F7F5") }
    private val PLOT_INNER by lazy { Color.parseColor("#FFFFFF") }
    private val PLOT_GRID by lazy { Color.parseColor("#E4EBE6") }
    private val PATH_OTHER by lazy { Color.parseColor("#4A6FA5") }
    /** Same blue as the path on the phone. */
    private val PATH_ROUTE by lazy { Color.parseColor("#3E78F0") }
    private val AIRCRAFT by lazy { Color.parseColor("#C47A00") }
    private val AIRCRAFT_INK by lazy { Color.parseColor("#111111") }
    private val CLASS_DISC by lazy { Color.parseColor("#FFB020") }
    private val PILOT_INK by lazy { Color.parseColor("#3D4A55") }
    private val PILOT_FILL by lazy { Color.parseColor("#F4F7FB") }
    private val PATH_STAY by lazy { Color.parseColor("#35D683") }
    private val DOT_ATTENTION by lazy { Color.parseColor("#E53935") }
    private val DOT_BOOKMARK by lazy { Color.parseColor("#0288D1") }
    private val GNSS_INK by lazy { Color.parseColor("#B91C1C") }
    private const val MARK_STAY = 1
    private const val MARK_SLATE = 2
    private const val MARK_AMBER = 3
    private const val MARK_AMBER_DASH = 4
    private const val MARK_RED = 5
    private const val MARK_BLUE = 6
    private const val MARK_PILOT = 7
    private const val MARK_ALERT = 8
    private const val MARK_GNSS = 9
    private const val MARK_GNSS_2 = 10
    private const val MARK_ROUTE = 11
    private const val MARK_CAMERA = 12
    private const val PDF_CONE_M = 150.0 * 0.3048
    private const val PDF_CONE_DEG = 50f
    private val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()
    private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val BODY_TOP = HEADER_H + 18f
    private val BODY_BOT = PAGE_H - FOOTER_H - 8f
    private val USABLE = BODY_BOT - BODY_TOP

    fun write(
        doc: DebriefDoc,
        file: File,
        tiles: List<PathTiles.Tile> = emptyList(),
        extraTiles: List<List<PathTiles.Tile>> = emptyList(),
        onProgress: (Float) -> Unit = {},
    ) {
        file.parentFile?.mkdirs()
        onProgress(0.02f)
        val blocks = layoutBlocks(doc, tiles, extraTiles) { fraction ->
            onProgress(0.02f + 0.66f * fraction)
        }
        onProgress(0.70f)
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
            onProgress(0.70f + 0.26f * (i + 1).toFloat() / n)
        }
        onProgress(0.97f)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f)
    }

    private class Block(
        val height: Float,
        val keepWithNext: Boolean = false,
        val draw: (Canvas, Float) -> Unit,
    )

    private fun layoutBlocks(
        doc: DebriefDoc,
        tiles: List<PathTiles.Tile>,
        extraTiles: List<List<PathTiles.Tile>>,
        onProgress: (Float) -> Unit = {},
    ): List<Block> {
        val out = ArrayList<Block>()
        out += titleBlock(doc.pdfTitle)
        out += spacer(6f)
        out += sectionHead("", LanguageText.text("ui.disclaimer.label"), alert = false)
        doc.disclaimer.split("\n\n").forEach { para ->
            chunkText(para.trim().ifBlank { " " }, CONTENT_W, 9.5f, muted = false).forEach { sl ->
                out += bodyBlock(sl, alert = false)
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
            out += pathFigureBlock(figure, tiles)
            out += spacer(4f)
            pathKeyBlocks(figure).forEach { out += it }
            out += spacer(10f)
        }
        doc.extraFigures.forEachIndexed { index, extra ->
            if (!extra.drawable) return@forEachIndexed
            out += pathFigureBlock(extra, extraTiles.getOrElse(index) { emptyList() })
            out += spacer(10f)
        }
        var expected = 0
        for (section in doc.sections) {
            if (section.body.isNotBlank()) expected += textLines(section.body)
            if (section.after.isNotBlank()) expected += textLines(section.after)
        }
        var done = 0
        var lastReported = 0f
        fun advance(n: Int) {
            if (n <= 0) return
            done += n
            val fraction = if (expected <= 0) 1f else done.toFloat() / expected.toFloat()
            if (fraction >= lastReported + 0.01f || done >= expected) {
                lastReported = fraction
                onProgress(fraction.coerceIn(0f, 1f))
            }
        }
        for (section in doc.sections) {
            if (section.title == LanguageText.text("chrome.extra.attention") && doc.extraAttention.isNotEmpty()) {
                out += sectionHead(section.number, section.title, alert = true)
                out += spacer(4f)
                doc.extraAttention.forEach { hit ->
                    out += attentionNoteBlock(hit)
                    out += spacer(8f)
                }
                val skip = (if (section.body.isNotBlank()) textLines(section.body) else 0) +
                    (if (section.after.isNotBlank()) textLines(section.after) else 0)
                advance(skip)
                continue
            }
            out += sectionHead(section.number, section.title, section.alert)
            appendProse(
                out,
                section.body,
                section.alert,
                cameraRows = section.title == LanguageText.text("ui.cameras.on.path"),
                onLine = { advance(1) },
            )
            section.chart?.takeIf { it.rows.isNotEmpty() }?.let { chart ->
                chartBlocks(chart).forEach { out += it }
                out += spacer(6f)
            }
            appendProse(out, section.after, section.alert, onLine = { advance(1) })
            out += spacer(8f)
        }
        out += takeawayBlock(doc.takeaway)
        onProgress(1f)
        return out
    }

    private fun textLines(text: String): Int {
        if (text.isEmpty()) return 0
        var n = 1
        for (c in text) if (c == '\n') n++
        return n
    }

    private fun appendProse(
        out: ArrayList<Block>,
        text: String,
        alert: Boolean,
        cameraRows: Boolean = false,
        onLine: () -> Unit = {},
    ) {
        if (text.isBlank()) return
        val paras = text.split('\n')
        val width = CONTENT_W - if (alert) 12 else 0
        val quiet = if (cameraRows) cameraQuietLines() else emptySet()
        var inRow = false
        paras.forEachIndexed { i, raw ->
            val para = raw.trimEnd()
            val kind = if (cameraRows) cameraKindColor(para.trim()) else null
            val nextBlank = i + 1 >= paras.size || paras[i + 1].isBlank()
            when {
                para.isBlank() -> {
                    inRow = false
                    out += spacer(if (cameraRows) 7f else 5f)
                }
                kind != null -> {
                    if (i > 0 && paras[i - 1].isNotBlank()) out += spacer(6f)
                    inRow = true
                    out += cameraKindBlock(para.trim(), kind)
                }
                inRow -> out += cameraCountBlock(para.trim(), alert, keepWithNext = !nextBlank)
                isStayHead(para) -> {
                    if (i > 0) out += spacer(8f)
                    out += subheadBlock(para.trim(), alert = alert)
                }
                isKickerLine(para) -> {
                    if (i > 0) out += spacer(6f)
                    out += kickerLineBlock(para.trim(), alert)
                }
                isBullet(para) -> out += bulletBlock(
                    para.trimStart().removePrefix("·").trimStart().removePrefix("•").trim(),
                    alert,
                )
                para.trim() in quiet -> chunkText(para.trim(), width, 9f, muted = true).forEach { sl ->
                    out += bodyBlock(sl, alert)
                }
                else -> chunkText(para, width, 9.5f, muted = false).forEach { sl ->
                    out += bodyBlock(sl, alert)
                }
            }
            onLine()
        }
    }

    /** Same solids as the path-screen dots. */
    private fun cameraKindColor(line: String): Int? = when (line) {
        LanguageText.text("ui.cameras.kind.flock") -> Color.parseColor("#3B82F6")
        LanguageText.text("ui.cameras.kind.plate") -> Color.parseColor("#F59E0B")
        LanguageText.text("ui.cameras.kind.speed") -> Color.parseColor("#14B8A6")
        LanguageText.text("ui.cameras.kind.red") -> Color.parseColor("#EF4444")
        LanguageText.text("ui.cameras.kind.other") -> Color.parseColor("#64748B")
        else -> null
    }

    private fun cameraQuietLines(): Set<String> = setOf(
        LanguageText.text(
            "ui.cameras.within",
            mapOf("meters" to FlockApproach.PATH_M.toInt().toString()),
        ),
        LanguageText.text("ui.cameras.no.path"),
        LanguageText.text("ui.cameras.none.along"),
    )

    private fun cameraKindBlock(name: String, color: Int): Block {
        val sl = layout(name, CONTENT_W - 16, 10f, muted = false, bold = true)
        val h = maxOf(sl.height + 2f, 13f)
        return Block(h, keepWithNext = true) { canvas, y ->
            val dot = Paint().apply {
                this.color = color
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(MARGIN + 3.5f, y + 5.5f, 3.2f, dot)
            canvas.save()
            canvas.translate(MARGIN + 14f, y)
            sl.draw(canvas)
            canvas.restore()
        }
    }

    private fun cameraCountBlock(text: String, alert: Boolean, keepWithNext: Boolean): Block {
        val sl = layout(text, CONTENT_W - 14 - if (alert) 12 else 0, 9f, muted = true)
        val h = sl.height + 2f
        return Block(h, keepWithNext = keepWithNext) { canvas, y ->
            canvas.save()
            canvas.translate(MARGIN + 14f + if (alert) 10f else 0f, y)
            sl.draw(canvas)
            canvas.restore()
        }
    }

    private fun chartBlocks(chart: ReportChart): List<Block> {
        val blocks = ArrayList<Block>()
        if (chart.caption.isNotBlank()) {
            blocks += textBlock(layout(chart.caption, CONTENT_W, 8f, muted = true))
        }
        if (chart.split) {
            blocks += legendBlock()
        }
        val max = chart.rows.maxOf { maxOf(it.value, it.second ?: 0) }.coerceAtLeast(1)
        chart.rows.forEach { blocks += barBlock(it, max, chart.split) }
        return blocks
    }

    private fun legendBlock() = Block(14f) { canvas, y ->
        val paint = TextPaint().apply {
            color = MUTED
            textSize = 8f
            isAntiAlias = true
        }
        drawLegendSwatch(canvas, MARGIN, y + 3f, PHOS)
        canvas.drawText(LanguageText.text("radio.wifi"), MARGIN + 14f, y + 10f, paint)
        drawLegendSwatch(canvas, MARGIN + 58f, y + 3f, PATH_OTHER)
        canvas.drawText(LanguageText.text("radio.ble"), MARGIN + 72f, y + 10f, paint)
    }

    private fun drawLegendSwatch(canvas: Canvas, x: Float, y: Float, color: Int) {
        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(x, y, x + 10f, y + 6f), 2f, 2f, paint)
    }

    private fun barBlock(row: ReportBar, max: Int, split: Boolean): Block {
        val height = if (split) 28f else 16f
        return Block(height) { canvas, y ->
            val labelPaint = TextPaint().apply {
                color = INK
                textSize = 9f
                isAntiAlias = true
            }
            val numPaint = TextPaint().apply {
                color = INK
                textSize = 9f
                isAntiAlias = true
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val detailPaint = TextPaint().apply {
                color = MUTED
                textSize = 8f
                isAntiAlias = true
            }
            val labelW = 148f
            val labelBase = if (split) y + 16f else y + 10f
            canvas.drawText(fitText(row.label, labelPaint, labelW), MARGIN, labelBase, labelPaint)
            val barLeft = MARGIN + labelW + 8f
            val numW = 32f
            val detailW = if (split || row.detail.isBlank()) 0f else 118f
            val barRight = PAGE_W - MARGIN - numW - detailW - 8f
            fun drawOne(value: Int, top: Float, color: Int) {
                val track = Paint().apply {
                    this.color = Color.parseColor("#E6EEE9")
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawRoundRect(RectF(barLeft, top, barRight, top + 7f), 3f, 3f, track)
                if (value > 0) {
                    val frac = (value.toFloat() / max).coerceIn(0.03f, 1f)
                    val fill = Paint().apply {
                        this.color = color
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(
                        RectF(barLeft, top, barLeft + (barRight - barLeft) * frac, top + 7f),
                        3f,
                        3f,
                        fill,
                    )
                }
                canvas.drawText(value.toString(), barRight + 6f, top + 7f, numPaint)
            }
            if (split) {
                drawOne(row.value, y + 2f, PHOS)
                drawOne(row.second ?: 0, y + 14f, PATH_OTHER)
            } else {
                drawOne(row.value, y + 2f, PHOS)
                if (row.detail.isNotBlank()) {
                    canvas.drawText(row.detail, barRight + numW + 8f, y + 10f, detailPaint)
                }
            }
        }
    }

    private fun fitText(text: String, paint: TextPaint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > width) end--
        return text.substring(0, end) + "…"
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

    private fun figurePlotSize(): Pair<Float, Float> = (PAGE_W - 2 * MARGIN - 16f) to 300f

    private fun pathFigureBlock(fig: SitPathPlot.Figure, tiles: List<PathTiles.Tile>): Block {
        val legend = legendRows(fig)
        val headerH = 20f + legend.size * 13f + 6f
        val (plotW, plotH) = figurePlotSize()
        val cap = layout(fig.caption, CONTENT_W - 24, 10f, muted = false)
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
                color = if (fig.kicker == "AIRCRAFT") AIRCRAFT else PHOS
                textSize = 8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
                letterSpacing = 0.12f
            }
            val kickerLabel = if (fig.kicker == "AIRCRAFT") {
                LanguageText.text("pdf.aircraft")
            } else {
                fig.kicker
            }
            canvas.drawText(kickerLabel, MARGIN + 8f, y + 15f, kicker)
            val stats = Paint().apply { color = MUTED; textSize = 8f; isAntiAlias = true }
            val span = if (fig.spanM >= 1000) {
                LanguageText.text("pdf.km.span", mapOf("n" to "%.1f".format(Locale.US, fig.spanM / 1000)))
            } else {
                LanguageText.text("pdf.m.span", mapOf("n" to "${fig.spanM.toInt()}"))
            }
            val len = if (fig.lengthM >= 1000) {
                LanguageText.text("pdf.km.path", mapOf("n" to "%.1f".format(Locale.US, fig.lengthM / 1000)))
            } else {
                LanguageText.text("pdf.m.path", mapOf("n" to "${fig.lengthM.toInt()}"))
            }
            val fixes = fig.tracks.filter { it.aircraft }.sumOf { it.samples.size }
            val right = if (fig.tracks.all { it.aircraft } && fixes == 1) {
                LanguageText.text("ui.1.advertised.fix")
            } else {
                "$len  ·  $span"
            }
            canvas.drawText(right, PAGE_W - MARGIN - 8f - stats.measureText(right), y + 15f, stats)
            drawLegend(canvas, MARGIN + 8f, y + 22f, PAGE_W - MARGIN - 8f, legend)
            val plotTop = y + headerH
            val plot = RectF(MARGIN + 8f, plotTop, MARGIN + 8f + plotW, plotTop + plotH)
            val inner = Paint().apply { color = PLOT_INNER; style = Paint.Style.FILL }
            canvas.drawRect(plot, inner)
            val model = figureModel(fig)
            val lay = SitPathPlot.layout(model, plot.width(), plot.height(), pad = 12f, scaleBarReserve = 0f)
            if (lay != null) {
                drawPathMap(canvas, plot, lay, fig, tiles, stroke)
            } else {
                canvas.drawRect(plot, stroke)
            }
            canvas.save()
            canvas.translate(MARGIN + 8f, plot.bottom + 14f + scaleH)
            cap.draw(canvas)
            canvas.restore()
        }
    }

    private fun drawPathMap(
        canvas: Canvas,
        plot: RectF,
        lay: SitPathPlot.Layout,
        fig: SitPathPlot.Figure,
        tiles: List<PathTiles.Tile>,
        stroke: Paint,
    ) {
        fun ox(x: Float) = plot.left + x
        fun oy(y: Float) = plot.top + y
        canvas.save()
        canvas.clipRect(plot)
        if (tiles.isEmpty()) {
            val grid = Paint().apply { color = PLOT_GRID; strokeWidth = 0.6f }
            for (i in 1..3) {
                val gx = plot.left + plot.width() * i / 4f
                val gy = plot.top + plot.height() * i / 4f
                canvas.drawLine(gx, plot.top, gx, plot.bottom, grid)
                canvas.drawLine(plot.left, gy, plot.right, gy, grid)
            }
        } else {
            val tilePaint = Paint().apply {
                isFilterBitmap = true
                isAntiAlias = true
            }
            tiles.forEach { tile ->
                val nw = lay.project(tile.north, tile.west)
                val se = lay.project(tile.south, tile.east)
                val dst = RectF(ox(nw.x), oy(nw.y), ox(se.x), oy(se.y))
                if (dst.width() < 1f || dst.height() < 1f) return@forEach
                val src = Rect(0, 0, tile.bitmap.width, tile.bitmap.height)
                runCatching { canvas.drawBitmap(tile.bitmap, src, dst, tilePaint) }
            }
        }
        fig.tracks.forEach { track ->
            drawPathTrack(canvas, lay, track, ::ox, ::oy)
        }
        val phonePrimary = fig.tracks.firstOrNull { !it.aircraft && !it.secondary }
        if (phonePrimary != null && phonePrimary.samples.size >= 2 && fig.tracks.count { !it.aircraft } == 1) {
            drawPathTicks(canvas, lay, phonePrimary.samples, ::ox, ::oy, despike = true)
        }
        drawPathCameras(canvas, lay, fig.cameras, ::ox, ::oy)
        fig.gnss.forEach { pin ->
            val pt = lay.project(pin.lat, pin.lon)
            drawGnssMark(canvas, ox(pt.x), oy(pt.y), pin.secondary)
        }
        val craftPrimary = fig.tracks.firstOrNull { it.aircraft && !it.secondary && it.samples.size >= 2 }
        if (phonePrimary == null && craftPrimary != null) {
            drawPathTicks(canvas, lay, craftPrimary.samples, ::ox, ::oy, despike = false)
        }
        fig.pilots.forEach { mark ->
            val pt = lay.project(mark.lat, mark.lon)
            drawPilot(canvas, ox(pt.x), oy(pt.y))
        }
        val piles = SitPathPlot.clusters(lay.dots)
        piles.forEachIndexed { i, pile ->
            val extra = pile.members.any { it.dot.extraAttention }
            val named = pile.members.any { it.dot.named }
            val fillD = Paint().apply {
                color = when {
                    extra -> DOT_ATTENTION
                    named -> DOT_BOOKMARK
                    else -> PHOS
                }
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val ring = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 1.4f
                isAntiAlias = true
            }
            val r = if (pile.stacked) 8.5f else 7f
            val cx = ox(pile.center.x)
            val cy = oy(pile.center.y)
            canvas.drawCircle(cx, cy, r, fillD)
            canvas.drawCircle(cx, cy, r, ring)
            val num = Paint().apply {
                color = Color.WHITE
                textSize = if (pile.stacked) 8f else 7f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("${i + 1}", cx, cy + 2.6f, num)
        }
        canvas.restore()
        canvas.drawRect(plot, stroke)
        drawMapCredit(canvas, plot, tiles, fig.cameras.isNotEmpty())
        val nP = Paint().apply {
            color = MUTED
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val nx = plot.right - 12f
        val ny = plot.top + 12f
        val nDisc = Paint().apply {
            color = Color.argb(230, 255, 255, 255)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(nx, ny - 2f, 8f, nDisc)
        canvas.drawText(LanguageText.text("pdf.north"), nx, ny + 1f, nP)
        val barW = plot.width() * lay.scaleBarFrac
        val label = if (lay.scaleBarM >= 1000) {
            LanguageText.text("pdf.km", mapOf("n" to "${(lay.scaleBarM / 1000).toInt()}"))
        } else {
            LanguageText.text("pdf.m", mapOf("n" to "${lay.scaleBarM.toInt()}"))
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

    private fun drawPathTrack(
        canvas: Canvas,
        lay: SitPathPlot.Layout,
        track: SitPathPlot.FigureTrack,
        ox: (Float) -> Float,
        oy: (Float) -> Float,
    ) {
        if (track.samples.isEmpty()) return
        if (track.samples.size == 1) {
            if (!track.aircraft) return
            val only = lay.project(track.samples[0].lat, track.samples[0].lon)
            drawClassMark(canvas, ox(only.x), oy(only.y))
            return
        }
        val cleaned = if (track.aircraft) {
            track.samples
        } else {
            Geo.despikePath(track.samples).let { if (it.size >= 2) it else track.samples }
        }
        val pts = cleaned.map { lay.project(it.lat, it.lon) }
        val pth = Path()
        pth.moveTo(ox(pts[0].x), oy(pts[0].y))
        for (i in 1 until pts.size) pth.lineTo(ox(pts[i].x), oy(pts[i].y))
        val tp = Paint().apply {
            color = when {
                track.aircraft && track.secondary -> PATH_OTHER
                track.aircraft -> AIRCRAFT_INK
                track.secondary -> PATH_OTHER
                else -> PATH_ROUTE
            }
            style = Paint.Style.STROKE
            strokeWidth = when {
                track.aircraft -> 1.8f
                track.secondary -> 1.8f
                else -> 4.8f
            }
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            if (track.aircraft) {
                pathEffect = DashPathEffect(floatArrayOf(2.2f, 3.8f), 0f)
            } else if (track.secondary) {
                pathEffect = DashPathEffect(floatArrayOf(7f, 4.5f), 0f)
            }
        }
        canvas.drawPath(pth, tp)
        if (track.aircraft) {
            val start = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val ring = Paint().apply {
                color = if (track.secondary) PATH_OTHER else AIRCRAFT_INK
                style = Paint.Style.STROKE
                strokeWidth = 1.4f
                isAntiAlias = true
            }
            canvas.drawCircle(ox(pts.first().x), oy(pts.first().y), 3.2f, start)
            canvas.drawCircle(ox(pts.first().x), oy(pts.first().y), 3.2f, ring)
            drawClassMark(canvas, ox(pts.last().x), oy(pts.last().y))
            return
        }
        val stays = Geo.realStops(cleaned)
        val stayPaint = Paint().apply {
            color = if (track.secondary) PATH_OTHER else PATH_STAY
            style = Paint.Style.STROKE
            strokeWidth = if (track.secondary) 4.5f else 4.8f
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
            alpha = if (track.secondary) 180 else 230
            if (track.secondary) pathEffect = DashPathEffect(floatArrayOf(8f, 5f), 0f)
        }
        for (i in 1 until pts.size) {
            val from = cleaned.getOrNull(i - 1)?.at ?: continue
            val to = cleaned.getOrNull(i)?.at ?: continue
            if (!Geo.stopCovers(stays, from, to)) continue
            canvas.drawLine(
                ox(pts[i - 1].x), oy(pts[i - 1].y),
                ox(pts[i].x), oy(pts[i].y),
                stayPaint,
            )
        }
        if (!track.secondary) {
            val glow = Paint().apply {
                color = PATH_STAY
                style = Paint.Style.FILL
                isAntiAlias = true
                alpha = 48
            }
            stays.forEach { stay ->
                val pt = lay.project(stay.lat, stay.lon)
                canvas.drawCircle(ox(pt.x), oy(pt.y), 14f, glow)
            }
        }
        val disc = Paint().apply {
            color = if (track.secondary) PATH_OTHER else PHOS
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(ox(pts.first().x), oy(pts.first().y), 3.4f, disc)
        canvas.drawCircle(ox(pts.last().x), oy(pts.last().y), 4.6f, disc)
        if (!track.secondary) {
            val lab = Paint().apply {
                color = MUTED
                textSize = 7.5f
                isAntiAlias = true
            }
            canvas.drawText(LanguageText.text("ui.start.label"), ox(pts.first().x) + 6f, oy(pts.first().y) - 4f, lab)
            canvas.drawText(LanguageText.text("ui.end.label"), ox(pts.last().x) + 6f, oy(pts.last().y) - 4f, lab)
        }
    }

    private class LegendSwatch(val label: String, val mark: Int)

    private fun legendSwatches(fig: SitPathPlot.Figure): List<LegendSwatch> {
        val phone = fig.tracks.filter { !it.aircraft }
        val out = ArrayList<LegendSwatch>()
        when {
            phone.size >= 2 -> {
                out += LegendSwatch(LanguageText.text("ui.sit.label.2"), MARK_ROUTE)
                out += LegendSwatch(LanguageText.text("ui.second.sit"), MARK_SLATE)
            }
            phone.any { it.samples.size >= 2 } -> {
                out += LegendSwatch(LanguageText.text("ui.sit.label.2"), MARK_ROUTE)
                out += LegendSwatch(LanguageText.text("ui.stay.label"), MARK_STAY)
            }
        }
        if (fig.cameras.isNotEmpty()) {
            out += LegendSwatch(LanguageText.text("ui.cameras.along.path"), MARK_CAMERA)
        }
        val craft = fig.tracks.filter { it.aircraft }
        if (craft.isNotEmpty()) {
            out += LegendSwatch(LanguageText.text("ui.advertised.label"), MARK_AMBER)
            if (craft.any { it.secondary }) out += LegendSwatch(LanguageText.text("ui.second.advertised"), MARK_AMBER_DASH)
        }
        if (fig.dots.any { it.extraAttention }) out += LegendSwatch(LanguageText.text("chrome.extra.attention"), MARK_RED)
        if (fig.dots.any { it.named }) out += LegendSwatch(LanguageText.text("ui.mac.alert"), MARK_BLUE)
        if (fig.dots.any { !it.extraAttention && !it.named }) out += LegendSwatch(LanguageText.text("ui.signature.alert"), MARK_ALERT)
        if (fig.pilots.isNotEmpty()) out += LegendSwatch(LanguageText.text("ui.operator.label.2"), MARK_PILOT)
        if (fig.gnss.any { !it.secondary }) out += LegendSwatch("GNSS", MARK_GNSS)
        if (fig.gnss.any { it.secondary }) out += LegendSwatch(LanguageText.text("ui.second.gnss"), MARK_GNSS_2)
        return out
    }

    private fun legendRows(fig: SitPathPlot.Figure): List<List<LegendSwatch>> {
        val bits = legendSwatches(fig)
        if (bits.isEmpty()) return emptyList()
        val paint = legendLabelPaint()
        val maxW = PAGE_W - 2 * MARGIN - 16f
        val rows = ArrayList<List<LegendSwatch>>()
        var row = ArrayList<LegendSwatch>()
        var used = 0f
        for (bit in bits) {
            val w = swatchAdvance(paint, bit.label)
            if (row.isNotEmpty() && used + w > maxW) {
                rows += row
                row = ArrayList()
                used = 0f
            }
            row += bit
            used += w
        }
        if (row.isNotEmpty()) rows += row
        return rows
    }

    private fun legendLabelPaint() = Paint().apply {
        color = MUTED
        textSize = 7.5f
        isAntiAlias = true
    }

    private fun swatchAdvance(paint: Paint, label: String) = 14f + paint.measureText(label) + 10f

    private fun drawLegend(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        rows: List<List<LegendSwatch>>,
    ) {
        val lab = legendLabelPaint()
        rows.forEachIndexed { rowIndex, row ->
            var x = left
            val y = top + rowIndex * 13f
            row.forEach { bit ->
                drawSwatch(canvas, x + 5f, y + 3f, bit.mark)
                canvas.drawText(bit.label, x + 14f, y + 6.5f, lab)
                x += swatchAdvance(lab, bit.label)
                if (x > right) return@forEach
            }
        }
    }

    private fun drawSwatch(canvas: Canvas, cx: Float, cy: Float, mark: Int) {
        val stroke = Paint().apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
            strokeWidth = 2.2f
        }
        val fill = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        when (mark) {
            MARK_ROUTE -> {
                stroke.color = PATH_ROUTE
                stroke.strokeWidth = 3.2f
                canvas.drawLine(cx - 5f, cy, cx + 5f, cy, stroke)
            }
            MARK_STAY -> {
                stroke.color = PATH_STAY
                stroke.strokeWidth = 3.2f
                canvas.drawLine(cx - 5f, cy, cx + 5f, cy, stroke)
            }
            MARK_CAMERA -> {
                fill.color = Color.parseColor("#3B82F6")
                canvas.drawCircle(cx, cy, 2.8f, fill)
            }
            MARK_SLATE -> {
                stroke.color = PATH_OTHER
                stroke.pathEffect = DashPathEffect(floatArrayOf(2.4f, 1.8f), 0f)
                canvas.drawLine(cx - 5f, cy, cx + 5f, cy, stroke)
            }
            MARK_AMBER -> {
                stroke.color = AIRCRAFT_INK
                stroke.strokeWidth = 1.8f
                stroke.pathEffect = DashPathEffect(floatArrayOf(1.6f, 2.4f), 0f)
                canvas.drawLine(cx - 6f, cy, cx + 6f, cy, stroke)
            }
            MARK_AMBER_DASH -> {
                stroke.color = PATH_OTHER
                stroke.strokeWidth = 1.8f
                stroke.pathEffect = DashPathEffect(floatArrayOf(1.6f, 2.4f), 0f)
                canvas.drawLine(cx - 6f, cy, cx + 6f, cy, stroke)
            }
            MARK_RED -> {
                fill.color = DOT_ATTENTION
                canvas.drawCircle(cx, cy, 2.6f, fill)
            }
            MARK_BLUE -> {
                fill.color = DOT_BOOKMARK
                canvas.drawCircle(cx, cy, 2.6f, fill)
            }
            MARK_ALERT -> {
                fill.color = PHOS
                canvas.drawCircle(cx, cy, 2.6f, fill)
            }
            MARK_PILOT -> drawPilot(canvas, cx, cy, radius = 4.4f)
            MARK_GNSS -> drawGnssMark(canvas, cx, cy, secondary = false, radius = 4.2f)
            MARK_GNSS_2 -> drawGnssMark(canvas, cx, cy, secondary = true, radius = 4.2f)
        }
    }

    private fun drawGnssMark(
        canvas: Canvas,
        x: Float,
        y: Float,
        secondary: Boolean,
        radius: Float = 5.6f,
    ) {
        val path = Path()
        path.moveTo(x, y - radius)
        path.lineTo(x + radius * 0.86f, y)
        path.lineTo(x, y + radius)
        path.lineTo(x - radius * 0.86f, y)
        path.close()
        val fill = Paint().apply {
            color = if (secondary) PATH_OTHER else GNSS_INK
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val halo = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.3f
            isAntiAlias = true
        }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, halo)
    }

    private fun figureModel(fig: SitPathPlot.Figure): SitPathPlot.Model {
        val phone = fig.tracks.filter { !it.aircraft }.flatMap { it.samples }
        val craft = fig.tracks.filter { it.aircraft }.flatMap { it.samples } +
            fig.pilots.map { GpsSample(0L, it.lat, it.lon, 0) }
        return SitPathPlot.Model(
            samples = phone,
            frameSamples = craft,
            dots = fig.dots,
            lengthM = fig.lengthM,
            spanM = fig.spanM,
            title = fig.kicker,
            minHalfSpanM = if (phone.isEmpty() && Geo.spanM(craft) < 80.0) 140f else 0f,
        )
    }

    private fun drawClassMark(canvas: Canvas, x: Float, y: Float, radius: Float = 7.2f) {
        val halo = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val fill = Paint().apply {
            color = CLASS_DISC
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(x, y, radius + 1.1f, halo)
        canvas.drawCircle(x, y, radius, fill)
        drawGlyph(canvas, Icons.Outlined.Flight, x, y, radius * 1.25f, Color.WHITE)
    }

    private fun drawPilot(canvas: Canvas, x: Float, y: Float, radius: Float = 6.4f) {
        val halo = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val fill = Paint().apply {
            color = PILOT_FILL
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val ring = Paint().apply {
            color = PILOT_INK
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
            isAntiAlias = true
        }
        canvas.drawCircle(x, y, radius + 0.7f, halo)
        canvas.drawCircle(x, y, radius, fill)
        canvas.drawCircle(x, y, radius, ring)
        drawGlyph(canvas, Icons.Outlined.Person, x, y, radius * 1.35f, PILOT_INK)
    }

    private fun drawGlyph(
        canvas: Canvas,
        vector: ImageVector,
        cx: Float,
        cy: Float,
        size: Float,
        color: Int,
    ) {
        canvas.save()
        canvas.translate(cx - size / 2f, cy - size / 2f)
        canvas.scale(size / vector.viewportWidth, size / vector.viewportHeight)
        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        drawVector(canvas, vector.root, paint)
        canvas.restore()
    }

    private fun drawVector(canvas: Canvas, node: VectorNode, paint: Paint) {
        when (node) {
            is VectorPath -> canvas.drawPath(node.pathData.toPath().asAndroidPath(), paint)
            is VectorGroup -> {
                canvas.save()
                if (node.translationX != 0f || node.translationY != 0f) {
                    canvas.translate(node.translationX, node.translationY)
                }
                if (node.rotation != 0f) canvas.rotate(node.rotation, node.pivotX, node.pivotY)
                if (node.scaleX != 1f || node.scaleY != 1f) {
                    canvas.scale(node.scaleX, node.scaleY, node.pivotX, node.pivotY)
                }
                node.forEach { drawVector(canvas, it, paint) }
                canvas.restore()
            }
        }
    }

    /**
     * Cameras the path came near. A stored direction is a wedge. No direction is a dot.
     * A direction that pointed away is not in [cameras].
     */
    private fun drawPathCameras(
        canvas: Canvas,
        lay: SitPathPlot.Layout,
        cameras: List<FlockCamera>,
        ox: (Float) -> Float,
        oy: (Float) -> Float,
    ) {
        if (cameras.isEmpty()) return
        val corners = arrayOf(
            lay.unproject(lay.plotLeft, lay.plotTop),
            lay.unproject(lay.plotRight, lay.plotTop),
            lay.unproject(lay.plotLeft, lay.plotBottom),
            lay.unproject(lay.plotRight, lay.plotBottom),
        )
        var south = 90.0
        var north = -90.0
        var west = 180.0
        var east = -180.0
        for ((lat, lon) in corners) {
            if (!lat.isFinite() || !lon.isFinite()) return
            if (lat < south) south = lat
            if (lat > north) north = lat
            if (lon < west) west = lon
            if (lon > east) east = lon
        }
        val index = FlockIndex()
        index.replace(cameras)
        val marks = index.marks(
            south, north, west, east,
            project = { lat, lon ->
                val pt = lay.project(lat, lon)
                pt.x to pt.y
            },
            inside = { x, y ->
                x in lay.plotLeft..lay.plotRight && y in lay.plotTop..lay.plotBottom
            },
        )
        val metersPerPx = lay.metersPerPx.coerceAtLeast(0.05f)
        for (mark in marks) {
            val pt = lay.project(mark.lat, mark.lon)
            drawPdfCamera(canvas, ox(pt.x), oy(pt.y), mark, lay, metersPerPx, ox, oy)
        }
    }

    private fun drawPdfCamera(
        canvas: Canvas,
        atX: Float,
        atY: Float,
        mark: FlockMark,
        lay: SitPathPlot.Layout,
        metersPerPx: Float,
        ox: (Float) -> Float,
        oy: (Float) -> Float,
    ) {
        val ink = pdfCameraInk(mark)
        if (mark.count > 1) {
            val label = mark.count.toString()
            val num = Paint().apply {
                color = ink.ink
                textSize = 7f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val radius = max(9f, num.measureText(label) / 2f + 3.2f)
            val disc = Paint().apply { color = ink.dot; style = Paint.Style.FILL; isAntiAlias = true }
            val halo = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
            val edge = Paint().apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.STROKE
                strokeWidth = 1.4f
                isAntiAlias = true
            }
            canvas.drawCircle(atX, atY, radius + 2.2f, halo)
            canvas.drawCircle(atX, atY, radius + 2.2f, edge)
            canvas.drawCircle(atX, atY, radius, disc)
            canvas.drawText(label, atX, atY + 2.6f, num)
            return
        }
        val fill = Paint().apply { color = ink.fill; style = Paint.Style.FILL; isAntiAlias = true }
        val line = Paint().apply {
            color = ink.line
            style = Paint.Style.STROKE
            strokeWidth = ink.stroke
            isAntiAlias = true
        }
        var reach = 0f
        if (ink.coneM > 0.0) {
            for (facing in mark.facings) {
                val (lat, lon) = FlockApproach.destination(mark.lat, mark.lon, facing.toDouble(), ink.coneM)
                val end = lay.project(lat, lon)
                val dx = ox(end.x) - atX
                val dy = oy(end.y) - atY
                if (dx == 0f && dy == 0f) continue
                val radius = (ink.coneM / metersPerPx).toFloat().coerceIn(ink.minPx, ink.maxPx)
                reach = max(reach, radius)
                val bearing = atan2(dy.toDouble(), dx.toDouble()) * (180.0 / PI)
                val box = RectF(atX - radius, atY - radius, atX + radius, atY + radius)
                canvas.drawArc(box, (bearing - PDF_CONE_DEG / 2.0).toFloat(), PDF_CONE_DEG, true, fill)
                canvas.drawArc(box, (bearing - PDF_CONE_DEG / 2.0).toFloat(), PDF_CONE_DEG, true, line)
            }
        }
        val discR = 6.4f
        val halo = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
        val edge = Paint().apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        val dot = Paint().apply { color = ink.dot; style = Paint.Style.FILL; isAntiAlias = true }
        canvas.drawCircle(atX, atY, discR, halo)
        canvas.drawCircle(atX, atY, discR, edge)
        canvas.drawCircle(atX, atY, 4.2f, dot)
    }

    private data class PdfInk(
        val fill: Int,
        val line: Int,
        val glow: Int,
        val core: Int,
        val ink: Int,
        val dot: Int,
        val coneM: Double,
        val minPx: Float,
        val maxPx: Float,
        val stroke: Float,
    )

    private fun pdfCameraInk(mark: FlockMark): PdfInk = when (mark.kind) {
        SensorKind.SPEED -> PdfInk(
            argb(0.62f, 0xFF14B8A6.toInt()), argb(0.92f, 0xFF5EEAD4.toInt()),
            argb(0.55f, 0xFF2DD4BF.toInt()), argb(1f, 0xFF99F6E4.toInt()), Color.WHITE, 0xFF14B8A6.toInt(),
            PDF_CONE_M, 18f, 56f, 1.8f,
        )
        SensorKind.RED -> PdfInk(
            argb(0.62f, 0xFFEF4444.toInt()), argb(0.92f, 0xFFFCA5A5.toInt()),
            argb(0.55f, 0xFFF87171.toInt()), argb(1f, 0xFFFECACA.toInt()), Color.WHITE, 0xFFEF4444.toInt(),
            PDF_CONE_M, 18f, 56f, 1.8f,
        )
        SensorKind.GUNSHOT -> PdfInk(
            argb(0.62f, 0xFF8B5CF6.toInt()), argb(0.92f, 0xFFC4B5FD.toInt()),
            argb(0.55f, 0xFFA78BFA.toInt()), argb(1f, 0xFFDDD6FE.toInt()), Color.WHITE, 0xFFA78BFA.toInt(),
            0.0, 18f, 56f, 1.8f,
        )
        SensorKind.CAMERA -> PdfInk(
            argb(0.55f, 0xFF64748B.toInt()), argb(0.90f, 0xFF94A3B8.toInt()),
            argb(0.45f, 0xFF64748B.toInt()), argb(1f, 0xFFCBD5E1.toInt()), Color.WHITE, 0xFF64748B.toInt(),
            PDF_CONE_M * 0.5, 14f, 36f, 1.4f,
        )
        SensorKind.PLATE -> if (mark.flock) {
            PdfInk(
                argb(0.62f, 0xFF3B82F6.toInt()), argb(0.95f, 0xFF93C5FD.toInt()),
                argb(0.55f, 0xFF60A5FA.toInt()), argb(1f, 0xFFBFDBFE.toInt()), Color.BLACK, 0xFF3B82F6.toInt(),
                PDF_CONE_M, 18f, 56f, 1.8f,
            )
        } else {
            PdfInk(
                argb(0.62f, 0xFFF59E0B.toInt()), argb(0.95f, 0xFFFCD34D.toInt()),
                argb(0.55f, 0xFFFBBF24.toInt()), argb(1f, 0xFFFDE68A.toInt()), Color.BLACK, 0xFFF59E0B.toInt(),
                PDF_CONE_M, 18f, 56f, 1.8f,
            )
        }
    }

    private fun argb(alpha: Float, rgb: Int): Int {
        val a = (alpha * 255f).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
    }

    private fun drawMapCredit(
        canvas: Canvas,
        plot: RectF,
        tiles: List<PathTiles.Tile>,
        cameras: Boolean,
    ) {
        val tile = tiles.firstOrNull()
        val line = when {
            tile != null -> FlockCredit.append(tile.basemap.creditLine(tile.overlays), cameras)
            cameras -> FlockCredit.LINE
            else -> return
        }
        val text = Paint().apply {
            color = Color.rgb(32, 32, 32)
            textSize = 6.5f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val width = text.measureText(line)
        val padX = 3f
        val padY = 1.5f
        val right = plot.right - 3f
        val bottom = plot.bottom - 3f
        val left = (right - width - padX * 2f).coerceAtLeast(plot.left + 2f)
        val top = bottom - text.textSize - padY * 2f
        if (right - left < 8f || top < plot.top) return
        val back = Paint().apply {
            color = Color.argb(220, 255, 255, 255)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(left, top, right, bottom), 2f, 2f, back)
        canvas.drawText(line, left + padX, bottom - padY - 1f, text)
    }

    private fun drawPathTicks(
        canvas: Canvas,
        lay: SitPathPlot.Layout,
        samples: List<GpsSample>,
        ox: (Float) -> Float,
        oy: (Float) -> Float,
        despike: Boolean,
    ) {
        val trace = if (despike) {
            Geo.despikePath(samples).let { if (it.size >= 2) it else samples }
        } else {
            samples
        }
        if (trace.size < 2) return
        val dur = (trace.last().at - trace.first().at).coerceAtLeast(1L)
        val tick = Paint().apply {
            color = MUTED
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val lab = Paint().apply { color = MUTED; textSize = 7f; isAntiAlias = true }
        val chip = Paint().apply {
            color = Color.argb(228, 255, 255, 255)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        listOf(0.25, 0.5, 0.75).forEach { frac ->
            val want = trace.first().at + (dur * frac).toLong()
            val idx = trace.indices.minByOrNull { abs(trace[it].at - want) } ?: return@forEach
            if (idx == 0 || idx == trace.lastIndex) return@forEach
            val pt = lay.project(trace[idx].lat, trace[idx].lon)
            val label = TIME_FMT.format(Date(trace[idx].at))
            val tx = ox(pt.x) + 5f
            val ty = oy(pt.y) - 3f
            val tw = lab.measureText(label)
            canvas.drawCircle(ox(pt.x), oy(pt.y), 2.4f, tick)
            canvas.drawRoundRect(tx - 1.5f, ty - 7.5f, tx + tw + 1.5f, ty + 2f, 2f, 2f, chip)
            canvas.drawText(label, tx, ty, lab)
        }
    }

    private fun pathKeyBlocks(fig: SitPathPlot.Figure): List<Block> {
        val model = figureModel(fig)
        val (plotW, plotH) = figurePlotSize()
        val lay = SitPathPlot.layout(model, plotW, plotH, pad = 12f, scaleBarReserve = 0f) ?: return emptyList()
        val piles = SitPathPlot.clusters(lay.dots)
        val craft = fig.craftKeys.filter { it.isNotBlank() }
        if (piles.isEmpty() && craft.isEmpty() && fig.gnss.isEmpty()) return emptyList()
        val out = ArrayList<Block>()
        out += sectionHead("", LanguageText.text("ui.path.key"), alert = false)
        out += spacer(4f)
        piles.forEachIndexed { i, pile ->
            out += pathKeyRow(i + 1, pile)
            out += spacer(5f)
        }
        craft.forEach { line ->
            out += pathKeyCraftRow(line)
            out += spacer(5f)
        }
        fig.gnss.forEach { pin ->
            out += pathKeyGnssRow(pin)
            out += spacer(5f)
        }
        return out
    }

    private fun pathKeyLine(n: Int, pile: SitPathPlot.Cluster): String {
        val radios = pile.members.joinToString("  ·  ") { m ->
            val d = m.dot
            val kind = if (d.kind.name == "WIFI") "WIFI" else "BLE"
            val tag = if (d.extraAttention) LanguageText.text("chrome.extra.attention") else null
            val fleets = d.fleetNames.filter { it.isNotBlank() }.joinToString(", ")
            val obs = d.observerNotes.trim().takeIf { it.isNotEmpty() }?.let {
                LanguageText.text("pdf.observer", mapOf("note" to it))
            }
            val who = listOfNotNull(kind, d.label.ifBlank { d.mac }, fleets.ifBlank { null }, tag, obs)
                .joinToString(" ")
            val advertised = d.advertisedNote.trim()
            if (advertised.isEmpty()) who else "$who — $advertised"
        }
        return if (pile.stacked) {
            "$n  " + LanguageText.text("pdf.stop", mapOf("n" to "${pile.members.size}")) + " — $radios"
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
        val stay = Regex.escape(LanguageText.text("report.stay.word"))
        val transit = Regex.escape(LanguageText.text("report.transit.word"))
        return t.matches(Regex("""^\d+\.\s+(Stay|Transit|$stay|$transit)(?=\s|$).*""")) ||
            t.startsWith("• ")
    }

    private fun isKickerLine(line: String): Boolean {
        val t = line.trim()
        if (t.startsWith("Phone GPS")) return true
        val phone = LanguageText.text("ui.phone.gps.hear.time").take(20)
        if (phone.isNotEmpty() && t.startsWith(phone)) return true
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

    private fun pathKeyGnssRow(pin: SitPathPlot.GnssPin): Block {
        val sl = layout(pin.keyLine(), CONTENT_W - 28, 9f, muted = false)
        val h = maxOf(18f, sl.height + 4f)
        return Block(h) { canvas, y ->
            drawGnssMark(canvas, MARGIN + 7f, y + 8f, pin.secondary, radius = 5f)
            canvas.save()
            canvas.translate(MARGIN + 22f, y)
            sl.draw(canvas)
            canvas.restore()
        }
    }

    /** Same flight disc the map draws at the end of an advertised track. */
    private fun pathKeyCraftRow(line: String): Block {
        val sl = layout(line, CONTENT_W - 28, 9f, muted = false)
        val h = maxOf(18f, sl.height + 4f)
        return Block(h) { canvas, y ->
            drawClassMark(canvas, MARGIN + 7f, y + 8f, radius = 6.4f)
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
        val foot = layout(LanguageText.text("ui.pattern.match.not.identity"), innerW, 8f, muted = true)
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
            val chrome = LanguageText.text("chrome.extra.attention").uppercase(LanguageText.dateLocale())
            val label = "$chrome  ·  ${hit.signature.uppercase(Locale.US)}"
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
            canvas.drawText(LanguageText.text("pdf.takeaway"), MARGIN + 10f, y + 14f, k)
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
        canvas.drawText(LanguageText.text("ui.off.grid.pete.llc"), MARGIN, PAGE_H - 18f, f)
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
        val shown = charactersDrawn(text)
        val tp = TextPaint().apply {
            color = if (muted) MUTED else INK
            textSize = size
            isAntiAlias = true
            typeface = Typeface.create(
                PDF_SANS,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
        }
        return StaticLayout.Builder.obtain(shown, 0, shown.length, tp, width.coerceAtLeast(40))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1f)
            .setIncludePad(false)
            .build()
    }
}
