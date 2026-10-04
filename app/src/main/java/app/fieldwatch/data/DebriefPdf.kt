package app.fieldwatch.data

import app.fieldwatch.i18n.localized

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
import app.fieldwatch.domain.ReportSectionKind
import app.fieldwatch.domain.ExtraAttentionHit
import app.fieldwatch.domain.ReportBar
import app.fieldwatch.domain.ReportChart
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.SitPathPlot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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
    private val AIRCRAFT = Color.parseColor("#C47A00")
    private val AIRCRAFT_INK = Color.parseColor("#111111")
    private val CLASS_DISC = Color.parseColor("#FFB020")
    private val PILOT_INK = Color.parseColor("#3D4A55")
    private val PILOT_FILL = Color.parseColor("#F4F7FB")
    private val PATH_STAY = Color.parseColor("#35D683")
    private val DOT_ATTENTION = Color.parseColor("#E53935")
    private val DOT_BOOKMARK = Color.parseColor("#0288D1")
    private const val MARK_STAY = 1
    private const val MARK_SLATE = 2
    private const val MARK_AMBER = 3
    private const val MARK_AMBER_DASH = 4
    private const val MARK_RED = 5
    private const val MARK_BLUE = 6
    private const val MARK_PILOT = 7
    private const val MARK_ALERT = 8
    private val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()
    private val TIME_FMT get() = SimpleDateFormat("HH:mm", app.fieldwatch.i18n.TextRuntime.localeProvider())
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
        onProgress(0.08f)
        val blocks = layoutBlocks(doc, tiles, extraTiles)
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

    private fun layoutBlocks(
        doc: DebriefDoc,
        tiles: List<PathTiles.Tile>,
        extraTiles: List<List<PathTiles.Tile>>,
    ): List<Block> {
        val out = ArrayList<Block>()
        out += titleBlock(doc.pdfTitle)
        out += spacer(6f)
        out += sectionHead("", localized("debrief_pdf_disclaimer", "Disclaimer"), alert = false)
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
        for (section in doc.sections) {
            if (section.kind == ReportSectionKind.EXTRA_ATTENTION && doc.extraAttention.isNotEmpty()) {
                out += sectionHead(section.number, section.title, alert = true)
                out += spacer(4f)
                doc.extraAttention.forEach { hit ->
                    out += attentionNoteBlocks(hit)
                    out += spacer(8f)
                }
                continue
            }
            out += sectionHead(section.number, section.title, section.alert)
            appendProse(out, section.body, section.alert)
            section.chart?.takeIf { it.rows.isNotEmpty() }?.let { chart ->
                chartBlocks(chart).forEach { out += it }
                out += spacer(6f)
            }
            appendProse(out, section.after, section.alert)
            out += spacer(8f)
        }
        out += takeawayBlock(doc.takeaway)
        return out
    }

    private fun appendProse(out: ArrayList<Block>, text: String, alert: Boolean) {
        if (text.isBlank()) return
        val paras = text.split('\n')
        val width = CONTENT_W - if (alert) 12 else 0
        paras.forEachIndexed { i, raw ->
            val para = raw.trimEnd()
            when {
                para.isBlank() -> out += spacer(5f)
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
                else -> chunkText(para, width, 9.5f, muted = false).forEach { sl ->
                    out += bodyBlock(sl, alert)
                }
            }
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
        canvas.drawText("Wi-Fi", MARGIN + 14f, y + 10f, paint)
        drawLegendSwatch(canvas, MARGIN + 58f, y + 3f, PATH_OTHER)
        canvas.drawText("BLE", MARGIN + 72f, y + 10f, paint)
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
                color = if (fig.kicker == localized("debrief_pdf_aircraft", "AIRCRAFT")) AIRCRAFT else PHOS
                textSize = 8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
                letterSpacing = 0.12f
            }
            canvas.drawText(fig.kicker, MARGIN + 8f, y + 15f, kicker)
            val stats = Paint().apply { color = MUTED; textSize = 8f; isAntiAlias = true }
            val span = if (fig.spanM >= 1000) {
                localized("debrief_pdf_km_span", "%1\$s km span", "%.1f".format(Locale.US, fig.spanM / 1000))
            } else {
                localized("debrief_pdf_m_span", "%1\$s m span", fig.spanM.toInt())
            }
            val len = if (fig.lengthM >= 1000) {
                localized("debrief_pdf_km_path", "%1\$s km path", "%.1f".format(Locale.US, fig.lengthM / 1000))
            } else {
                localized("debrief_pdf_m_path", "%1\$s m path", fig.lengthM.toInt())
            }
            val fixes = fig.tracks.filter { it.aircraft }.sumOf { it.samples.size }
            val right = if (fig.tracks.all { it.aircraft } && fixes == 1) {
                localized("debrief_pdf_1_advertised_fix", "1 advertised fix")
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
                alpha = 210
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
        canvas.drawText("N", nx, ny + 1f, nP)
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
                else -> Color.parseColor("#2A3340")
            }
            style = Paint.Style.STROKE
            strokeWidth = when {
                track.aircraft -> 1.8f
                track.secondary -> 1.8f
                else -> 2.4f
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
        val stays = Geo.legs(cleaned).filter { it.stay }
        val stayPaint = Paint().apply {
            color = if (track.secondary) PATH_OTHER else PATH_STAY
            style = Paint.Style.STROKE
            strokeWidth = if (track.secondary) 4.5f else 7.2f
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
            alpha = if (track.secondary) 180 else 230
            if (track.secondary) pathEffect = DashPathEffect(floatArrayOf(8f, 5f), 0f)
        }
        for (i in 1 until pts.size) {
            val t = cleaned.getOrNull(i)?.at ?: continue
            if (stays.none { t in it.startAt..it.endAt }) continue
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
            canvas.drawText(localized("debrief_pdf_start", "Start"), ox(pts.first().x) + 6f, oy(pts.first().y) - 4f, lab)
            canvas.drawText(localized("debrief_pdf_end", "End"), ox(pts.last().x) + 6f, oy(pts.last().y) - 4f, lab)
        }
    }

    private class LegendSwatch(val label: String, val mark: Int)

    private fun legendSwatches(fig: SitPathPlot.Figure): List<LegendSwatch> {
        val phone = fig.tracks.filter { !it.aircraft }
        val out = ArrayList<LegendSwatch>()
        when {
            phone.size >= 2 -> {
                out += LegendSwatch(localized("debrief_pdf_this_sit", "This sit"), MARK_STAY)
                out += LegendSwatch(localized("debrief_pdf_second_sit", "Second sit"), MARK_SLATE)
            }
            phone.any { it.samples.size >= 2 } -> out += LegendSwatch(localized("debrief_pdf_stay", "Stay"), MARK_STAY)
        }
        val craft = fig.tracks.filter { it.aircraft }
        if (craft.isNotEmpty()) {
            out += LegendSwatch(localized("debrief_pdf_advertised", "Advertised"), MARK_AMBER)
            if (craft.any { it.secondary }) out += LegendSwatch(localized("debrief_pdf_second_advertised", "Second advertised"), MARK_AMBER_DASH)
        }
        if (fig.dots.any { it.extraAttention }) out += LegendSwatch(localized("debrief_pdf_extra_attention", "Extra attention"), MARK_RED)
        if (fig.dots.any { it.named }) out += LegendSwatch(localized("debrief_pdf_mac_alert", "MAC alert"), MARK_BLUE)
        if (fig.dots.any { !it.extraAttention && !it.named }) out += LegendSwatch(localized("debrief_pdf_signature_alert", "Signature alert"), MARK_ALERT)
        if (fig.pilots.isNotEmpty()) out += LegendSwatch(localized("debrief_pdf_pilot", "Pilot"), MARK_PILOT)
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
            MARK_STAY -> {
                stroke.color = PATH_STAY
                stroke.strokeWidth = 3.2f
                canvas.drawLine(cx - 5f, cy, cx + 5f, cy, stroke)
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
        }
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
        if (piles.isEmpty() && craft.isEmpty()) return emptyList()
        val out = ArrayList<Block>()
        out += sectionHead("", localized("debrief_pdf_path_key", "Path key"), alert = false)
        out += spacer(4f)
        piles.forEachIndexed { i, pile ->
            out += pathKeyRow(i + 1, pile)
            out += spacer(5f)
        }
        craft.forEach { line ->
            out += pathKeyCraftRow(line)
            out += spacer(5f)
        }
        return out
    }

    private fun pathKeyLine(n: Int, pile: SitPathPlot.Cluster): String {
        val radios = pile.members.joinToString("  ·  ") { m ->
            val d = m.dot
            val kind = if (d.kind.name == "WIFI") "WIFI" else "BLE"
            val tag = if (d.extraAttention) localized("debrief_pdf_extra_attention", "Extra attention") else null
            val fleets = d.fleetNames.filter { it.isNotBlank() }.joinToString(", ")
            val obs = d.observerNotes.trim().takeIf { it.isNotEmpty() }?.let { localized("debrief_pdf_observer", "Observer: %1\$s", it) }
            val who = listOfNotNull(kind, d.label.ifBlank { d.mac }, fleets.ifBlank { null }, tag, obs)
                .joinToString(" ")
            val advertised = d.advertisedNote.trim()
            if (advertised.isEmpty()) who else "$who — $advertised"
        }
        return if (pile.stacked) {
            localized("debrief_pdf_radios_at_this_stop", "%1\$s  %2\$s radios at this stop — %3\$s", n, pile.members.size, radios)
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
        return t.matches(Regex("""^\d+\.\s+(Stay|Transit|停留|途经)(?:\s|$).*""")) ||
            t.startsWith("• ")
    }

    private fun isKickerLine(line: String): Boolean {
        val t = line.trim()
        if (t.startsWith("Phone GPS")) return true
        if (t.contains(". ")) return false
        return t.matches(Regex("""^[\p{L}][\p{L}\p{N} +/'()&.,，·-]{0,48}[:：](\s.*)?$"""))
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

    private fun attentionNoteBlocks(hit: ExtraAttentionHit): List<Block> {
        val innerW = CONTENT_W - 24
        val label = localized("debrief_pdf_extra_attention_2", "EXTRA ATTENTION  ·  %1\$s", hit.signature).uppercase()
        val kicker = layout(label, innerW, 8f, muted = false, bold = true).apply { paint.color = ALERT_BAR }
        val radio = layout(hit.radioLabel, innerW, 9f, muted = false, bold = true)
        val note = layout(hit.note, innerW, 9.5f, muted = false)
        val foot = layout(localized("debrief_pdf_pattern_match_not_identity_not_a_safety", "Pattern match, not identity. Not a safety finding."), innerW, 8f, muted = true)
        val headH = kicker.height + 8f
        val h = headH + radio.height + 6f + note.height + 8f + foot.height + 12f
        // A user note can exceed a whole page. Split it before pagination rather than clip it.
        if (h > USABLE) return buildList {
            chunkText(label, innerW, 8f, muted = false).forEach { add(bodyBlock(it, alert = true)) }
            chunkText(hit.radioLabel, innerW, 9f, muted = false).forEach { add(bodyBlock(it, alert = true)) }
            chunkText(hit.note, innerW, 9.5f, muted = false).forEach { add(bodyBlock(it, alert = true)) }
            add(bodyBlock(foot, alert = true))
        }
        return listOf(Block(h) { canvas, y ->
            val box = RectF(MARGIN - 6f, y, PAGE_W - MARGIN + 6f, y + h - 4f)
            val fill = Paint().apply { color = ALERT_BG; style = Paint.Style.FILL }
            val bar = Paint().apply { color = ALERT_BAR; style = Paint.Style.FILL }
            canvas.drawRoundRect(box, 4f, 4f, fill)
            canvas.drawRect(box.left, box.top, box.left + 4f, box.bottom, bar)
            canvas.save()
            canvas.translate(MARGIN + 10f, y + 4f)
            kicker.draw(canvas)
            canvas.restore()
            var ty = y + headH
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
        })
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
            canvas.drawText(localized("debrief_pdf_takeaway", "TAKEAWAY"), MARGIN + 10f, y + 14f, k)
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
        canvas.drawText(localized("debrief_pdf_off_grid_pete_llc_operationally_sensitive", "Off Grid Pete LLC  ·  operationally sensitive"), MARGIN, PAGE_H - 18f, f)
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
