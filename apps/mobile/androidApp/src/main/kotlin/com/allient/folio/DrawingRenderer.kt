package com.allient.folio

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.DashPathEffect
import android.graphics.Typeface
import java.io.File

/** One renderer for the editor, page thumbnails, PNG and PDF output. */
internal object NotebookRenderer {
    private class Scratch {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val path = Path()
        val bounds = RectF()
        val ink = JetpackInkEngine()
    }
    // Exports run on workers; mutable drawing scratch and native Ink caches are thread confined.
    private val local = ThreadLocal.withInitial { Scratch() }

    fun drawPage(canvas: Canvas, page: NotebookPage, width: Int, height: Int, filesDir: File? = null,
                 allowImageDecode: Boolean = true, ink: JetpackInkEngine? = null, viewportZoom: Float = 1f,
                 imageBitmaps: Map<String, android.graphics.Bitmap>? = null, drawBackground: Boolean = true) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (drawBackground && !page.template.startsWith("inf-")) { canvas.drawColor(Color.WHITE); drawTemplate(canvas, page.template, w, h) }
        val scratch = local.get()!!
        for (layer in page.layers) {
            if (!layer.visible) continue
            for (image in page.images) {
                if (image.layerId != layer.id) continue
                val bitmap = if (imageBitmaps != null) imageBitmaps[image.path] else NotebookImageStore.get(image.path, filesDir, allowImageDecode)
                if (bitmap == null) continue
                scratch.bounds.set(image.x * w, image.y * h, (image.x + image.width) * w, (image.y + image.height) * h)
                canvas.drawBitmap(bitmap, null, scratch.bounds, scratch.imagePaint)
            }
            for (block in page.blocks) if (block.layerId == layer.id) drawBlock(canvas, block, w, h)
            for (stroke in page.strokes) if (stroke.layerId == layer.id) drawStroke(canvas, stroke, w, h, ink, viewportZoom)
            for (text in page.texts) {
                if (text.layerId != layer.id) continue
                if (text.kind != "plain") {
                    drawEmbedCard(canvas, text, w, h, scratch)
                    continue
                }
                val paint = scratch.paint
                paint.reset(); paint.isAntiAlias = true; paint.color = text.color; paint.textSize = text.size * w / 600f
                val family = when (text.font) { "Serif" -> Typeface.SERIF; "Mono" -> Typeface.MONOSPACE; else -> Typeface.SANS_SERIF }
                val style = (if (text.bold) Typeface.BOLD else Typeface.NORMAL) or (if (text.italic) Typeface.ITALIC else Typeface.NORMAL)
                paint.typeface = Typeface.create(family, style); paint.isUnderlineText = text.underline
                paint.textAlign = when (text.alignment) { "Center" -> Paint.Align.CENTER; "End" -> Paint.Align.RIGHT; else -> Paint.Align.LEFT }
                // Walk newline offsets rather than creating a list of lines during every recording.
                var start = 0; var line = 0
                while (start <= text.text.length) {
                    val end = text.text.indexOf('\n', start).let { if (it < 0) text.text.length else it }
                    canvas.drawText(text.text, start, end, text.x * w, text.y * h + line * paint.fontSpacing * text.lineSpacing.coerceIn(.6f, 2.5f), paint)
                    if (end == text.text.length) break
                    start = end + 1; line++
                }
            }
        }
    }

    /** Embedded links, recordings, and dates use the same geometry in the editor and exports. */
    private fun drawEmbedCard(canvas: Canvas, text: InkText, w: Float, h: Float, scratch: Scratch) {
        val left = text.x * w
        val top = text.y * h
        val cardWidth = text.boxWidth.coerceIn(.08f, 1f) * w
        val cardHeight = h * (.055f * (text.size / 22f)).coerceIn(.035f, .16f)
        val right = left + cardWidth
        val bottom = top + cardHeight
        val radius = cardHeight * .18f
        val paint = scratch.paint.apply { reset(); isAntiAlias = true }
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        paint.color = Color.rgb(105, 111, 116)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (w * .0017f).coerceAtLeast(1f)
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)

        val iconX = left + cardHeight * .48f
        val iconY = top + cardHeight * .5f
        val iconR = cardHeight * .19f
        paint.strokeWidth = (w * .0022f).coerceAtLeast(1f)
        when (text.kind) {
            "audio" -> {
                paint.style = Paint.Style.FILL
                val path = scratch.path.apply {
                    rewind()
                    moveTo(iconX - iconR * .6f, iconY - iconR)
                    lineTo(iconX + iconR, iconY)
                    lineTo(iconX - iconR * .6f, iconY + iconR)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "timestamp" -> {
                paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(iconX - iconR, iconY - iconR, iconX + iconR, iconY + iconR, iconR * .16f, iconR * .16f, paint)
                canvas.drawLine(iconX - iconR, iconY - iconR * .35f, iconX + iconR, iconY - iconR * .35f, paint)
                canvas.drawLine(iconX - iconR * .45f, iconY - iconR * 1.2f, iconX - iconR * .45f, iconY - iconR * .75f, paint)
                canvas.drawLine(iconX + iconR * .45f, iconY - iconR * 1.2f, iconX + iconR * .45f, iconY - iconR * .75f, paint)
            }
            else -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(iconX - iconR * .55f, iconY + iconR * .42f, iconR * .7f, paint)
                canvas.drawCircle(iconX + iconR * .55f, iconY - iconR * .42f, iconR * .7f, paint)
                canvas.drawLine(iconX - iconR * .22f, iconY + iconR * .15f, iconX + iconR * .22f, iconY - iconR * .15f, paint)
            }
        }

        val actionX = right - cardHeight * .45f
        paint.style = Paint.Style.STROKE
        if (text.kind == "audio") {
            canvas.drawCircle(actionX, iconY, iconR * .88f, paint)
            val path = scratch.path.apply {
                rewind(); moveTo(actionX - iconR * .25f, iconY - iconR * .45f)
                lineTo(actionX + iconR * .48f, iconY)
                lineTo(actionX - iconR * .25f, iconY + iconR * .45f)
                close()
            }
            paint.style = Paint.Style.FILL
            canvas.drawPath(path, paint)
        } else if (text.kind != "timestamp") {
            canvas.drawLine(actionX - iconR * .5f, iconY + iconR * .45f, actionX + iconR * .5f, iconY - iconR * .45f, paint)
            canvas.drawLine(actionX - iconR * .1f, iconY - iconR * .45f, actionX + iconR * .5f, iconY - iconR * .45f, paint)
            canvas.drawLine(actionX + iconR * .5f, iconY - iconR * .45f, actionX + iconR * .5f, iconY + iconR * .1f, paint)
        }

        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        paint.textSize = (cardHeight * .39f).coerceAtLeast(1f)
        val labelLeft = left + cardHeight * .83f
        val labelRight = if (text.kind == "timestamp") right - cardHeight * .20f else right - cardHeight * .83f
        val available = (labelRight - labelLeft).coerceAtLeast(0f)
        val label = text.text.replace('\n', ' ')
        val count = paint.breakText(label, true, available, null)
        val visible = if (count < label.length) {
            val shortCount = paint.breakText(label, true, (available - paint.measureText("…")).coerceAtLeast(0f), null)
            label.take(shortCount) + "…"
        } else label
        canvas.drawText(visible, labelLeft, iconY - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    fun drawStroke(canvas: Canvas, stroke: InkStroke, width: Float, height: Float, ink: JetpackInkEngine? = null, viewportZoom: Float = 1f) {
        if (stroke.points.isEmpty()) return
        if (stroke.engine == "shape-v1") {
            val scratch = local.get()!!
            val path = scratch.path.apply {
                rewind(); moveTo(stroke.points.first().x * width, stroke.points.first().y * height)
                for (i in 1 until stroke.points.size) lineTo(stroke.points[i].x * width, stroke.points[i].y * height)
                if (stroke.filled) close()
            }
            val paint = scratch.paint.apply {
                reset(); isAntiAlias = true; color = stroke.color; strokeWidth = (stroke.width * width / 600f).coerceAtLeast(.5f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND; style = if (stroke.filled) Paint.Style.FILL else Paint.Style.STROKE
            }
            if (stroke.filled) { paint.color = stroke.fillColor ?: stroke.color; canvas.drawPath(path, paint) }
            paint.color = stroke.color
            paint.style = Paint.Style.STROKE
            val unit = paint.strokeWidth.coerceAtLeast(1f)
            paint.pathEffect = when (stroke.lineStyle) {
                "dotted" -> DashPathEffect(floatArrayOf(unit, unit * 2.4f), 0f)
                "dashed" -> DashPathEffect(floatArrayOf(unit * 6f, unit * 4f), 0f)
                "dashdot" -> DashPathEffect(floatArrayOf(unit * 6f, unit * 3f, unit, unit * 3f), 0f)
                else -> null
            }
            canvas.drawPath(path, paint); paint.pathEffect = null
            return
        }
        if (JetpackInkEngine.supports(stroke.engine) && !stroke.filled) {
            (ink ?: local.get()!!.ink).draw(canvas, stroke, width, height, viewportZoom)
            return
        }
        val scratch = local.get()!!
        val paint = scratch.paint.apply {
            reset(); isAntiAlias = true
            color = stroke.color
            style = Paint.Style.STROKE
            strokeCap = if (stroke.brush == "Highlighter") Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (stroke.filled && stroke.points.size >= 3) {
            val path = scratch.path.apply {
                rewind()
                moveTo(stroke.points.first().x * width, stroke.points.first().y * height)
                for (i in 1 until stroke.points.size) { val p = stroke.points[i]; lineTo(p.x * width, p.y * height) }
                close()
            }
            paint.style = Paint.Style.FILL
            paint.color = stroke.fillColor ?: stroke.color
            canvas.drawPath(path, paint)
            if (stroke.engine == FilledInkEraser.FORMAT) return
            paint.style = Paint.Style.STROKE
            paint.color = stroke.color
        }
        fun thickness(p: Float): Float {
            val pressure = p.coerceIn(0f, 1f)
            val factor = when (stroke.brush) {
                "Fineliner", "Highlighter" -> 1f
                "Fountain pen" -> .15f + .85f * pressure
                "Brush pen" -> .08f + .92f * pressure * pressure
                else -> .35f + .65f * pressure
            }
            return stroke.width * width / 600f * factor
        }
        fun pencilOpacity(p: Float) {
            if (stroke.brush == "Pencil") paint.alpha = (Color.alpha(stroke.color) * (.35f + .65f * p.coerceIn(0f, 1f))).toInt()
        }
        if (stroke.brush == "Highlighter" && stroke.points.size > 1) {
            val path = scratch.path.apply {
                rewind()
                moveTo(stroke.points.first().x * width, stroke.points.first().y * height)
                for (i in 1 until stroke.points.size) { val p = stroke.points[i]; lineTo(p.x * width, p.y * height) }
            }
            paint.strokeWidth = thickness(1f)
            canvas.drawPath(path, paint)
            return
        }
        if (stroke.points.size == 1) {
            val point = stroke.points.first()
            paint.strokeWidth = thickness(point.pressure)
            pencilOpacity(point.pressure)
            canvas.drawPoint(point.x * width, point.y * height, paint)
        }
        for (index in 1 until stroke.points.size) {
            val a = stroke.points[index - 1]; val b = stroke.points[index]
            paint.strokeWidth = thickness((a.pressure + b.pressure) / 2f)
            pencilOpacity((a.pressure + b.pressure) / 2f)
            canvas.drawLine(a.x * width, a.y * height, b.x * width, b.y * height, paint)
        }
    }

    /** Unbounded paper for infinite canvases. Fills the background and draws only the visible world rectangle; spacing doubles when zoomed out so density stays readable. */
    fun drawInfinitePattern(canvas: Canvas, template: String, width: Float, left: Float, top: Float, right: Float, bottom: Float, zoom: Float) {
        val paper = com.allient.folio.InfinitePaper.parse(template) ?: return
        if (width <= 0f) return
        val z = zoom.coerceAtLeast(.01f)
        canvas.drawColor(paper.background)
        if (paper.pattern == "blank") return
        var gap = width * paper.gapFraction
        while (gap * zoom < 8f) gap *= 2f
        val ink = if (paper.dark) Color.rgb(128, 128, 128) else Color.rgb(190, 195, 199)
        val rule = if (paper.dark) Color.rgb(98, 98, 98) else Color.rgb(214, 218, 222)
        val paint = local.get()!!.paint.apply { reset(); isAntiAlias = true; color = rule; strokeCap = Paint.Cap.ROUND; strokeWidth = 1f / z }
        val x0 = kotlin.math.floor(left / gap) * gap; val y0 = kotlin.math.floor(top / gap) * gap
        when (paper.pattern) {
            "dots" -> {
                paint.color = ink; paint.strokeWidth = (2.2f / z).coerceAtLeast(width / 800f)
                var y = y0; while (y <= bottom) { var x = x0; while (x <= right) { canvas.drawPoint(x, y, paint); x += gap }; y += gap }
            }
            "lines" -> { var y = y0; while (y <= bottom) { canvas.drawLine(left, y, right, y, paint); y += gap } }
            else -> {
                var y = y0; while (y <= bottom) { canvas.drawLine(left, y, right, y, paint.also { it.color = if (paper.pattern == "sheet" && kotlin.math.round(y / gap).toLong() % 5L == 0L) ink else rule }); y += gap }
                var x = x0; while (x <= right) { canvas.drawLine(x, top, x, bottom, paint.also { it.color = if (paper.pattern == "sheet" && kotlin.math.round(x / gap).toLong() % 5L == 0L) ink else rule }); x += gap }
            }
        }
    }

    /** Tables and sticky notes sit under ink so pen strokes write over them. */
    fun drawBlock(canvas: Canvas, block: InkBlock, w: Float, h: Float) {
        val left = block.x * w; val top = block.y * h; val right = (block.x + block.width) * w; val bottom = (block.y + block.height) * h
        val paint = local.get()!!.paint.apply { reset(); isAntiAlias = true }
        if (block.kind == InkBlock.Sticky) { drawSticky(canvas, block, left, top, right, bottom, w, paint); return }
        val columns = block.columns.ifEmpty { listOf(1f) }.let { c -> val sum = c.sum().coerceAtLeast(.001f); c.map { it / sum } }
        val rows = block.rows.ifEmpty { listOf(1f) }.let { r -> val sum = r.sum().coerceAtLeast(.001f); r.map { it / sum } }
        val xs = FloatArray(columns.size + 1).also { var a = left; it[0] = a; columns.forEachIndexed { i, f -> a += f * (right - left); it[i + 1] = a } }
        val ys = FloatArray(rows.size + 1).also { var a = top; it[0] = a; rows.forEachIndexed { i, f -> a += f * (bottom - top); it[i + 1] = a } }
        paint.style = Paint.Style.FILL
        if (block.fill != 0) { paint.color = block.fill; canvas.drawRect(left, top, right, bottom, paint) }
        paint.color = block.headerFill
        if (block.headerRow) canvas.drawRect(left, top, right, ys[1], paint)
        if (block.headerColumn) canvas.drawRect(left, top, xs[1], bottom, paint)
        if (block.borderWidth > 0f) {
            paint.style = Paint.Style.STROKE; paint.color = block.borderColor; paint.strokeWidth = (block.borderWidth * w / 1200f).coerceAtLeast(1f)
            // A grid line is hidden where it runs through the inside of a merged range.
            fun hiddenH(k: Int, c: Int) = block.merges.any { (r0, c0, r1, c1) -> k > r0 && k <= r1 && c in c0..c1 }
            fun hiddenV(k: Int, r: Int) = block.merges.any { (r0, c0, r1, c1) -> k > c0 && k <= c1 && r in r0..r1 }
            for (k in ys.indices) for (c in columns.indices) if (!hiddenH(k, c)) canvas.drawLine(xs[c], ys[k], xs[c + 1], ys[k], paint)
            for (k in xs.indices) for (r in rows.indices) if (!hiddenV(k, r)) canvas.drawLine(xs[k], ys[r], xs[k], ys[r + 1], paint)
        }
    }

    private fun drawSticky(canvas: Canvas, block: InkBlock, left: Float, top: Float, right: Float, bottom: Float, w: Float, paint: Paint) {
        val fold = minOf(right - left, bottom - top) * .16f
        val path = local.get()!!.path.apply { rewind(); moveTo(left, top); lineTo(right, top); lineTo(right, bottom - fold); lineTo(right - fold, bottom); lineTo(left, bottom); close() }
        paint.style = Paint.Style.FILL; paint.setShadowLayer(w * .006f, 0f, w * .003f, 0x33000000)
        paint.color = if (block.fill == 0) 0xFFFDDC8C.toInt() else block.fill
        canvas.drawPath(path, paint); paint.clearShadowLayer()
        paint.color = Color.argb(60, 0, 0, 0)
        val flap = local.get()!!.path.apply { rewind(); moveTo(right, bottom - fold); lineTo(right - fold, bottom); lineTo(right - fold, bottom - fold * .35f); close() }
        canvas.drawPath(flap, paint)
        if (block.text.isNotBlank()) {
            val pad = w * .018f
            val textPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3326.toInt(); textSize = block.textSize * w / 600f }
            val layout = android.text.StaticLayout.Builder.obtain(block.text, 0, block.text.length, textPaint, (right - left - pad * 2).toInt().coerceAtLeast(1)).build()
            canvas.save(); canvas.clipRect(left, top, right, bottom); canvas.translate(left + pad, top + pad); layout.draw(canvas); canvas.restore()
        }
    }

    private fun drawTemplate(canvas: Canvas, template: String, w: Float, h: Float) {
        if (template == "blank") return
        val paint = local.get()!!.paint.apply { reset(); isAntiAlias = true; val small = w < 700f; color = if (small) Color.rgb(200, 205, 210) else Color.rgb(222, 225, 228); strokeWidth = if (small) 1f else (w / 1200f).coerceAtLeast(.5f) }
        val left = w * .07f; val right = w * .93f
        fun heading(label: String) { paint.color = Color.rgb(72, 76, 78); paint.textSize = w * .032f; paint.style = Paint.Style.FILL; canvas.drawText(label, left, h * .075f, paint); paint.color = Color.rgb(210, 216, 214) }
        fun ruled(start: Float = h * .09f, end: Float = h * .95f, gap: Float = w / if (template == "wide-ruled" || template == "english" || template == "custom-ruled") 15f else 24f) {
            var y = start; while (y < end) { canvas.drawLine(left, y, right, y, paint); y += gap }
            if (template == "english") { y = start + gap * .5f; paint.color = Color.rgb(231, 191, 191); while (y < end) { canvas.drawLine(left, y, right, y, paint); y += gap }; paint.color = Color.rgb(210, 216, 214) }
        }
        fun panel(x: Float, y: Float, r: Float, b: Float) { paint.style = Paint.Style.STROKE; canvas.drawRect(x, y, r, b, paint); paint.style = Paint.Style.FILL }
        fun checklist(x: Float, y: Float, r: Float, count: Int, spacing: Float) {
            for (i in 0 until count) { val yy = y + i * spacing; val box = w * .014f; paint.style = Paint.Style.STROKE; canvas.drawRect(x, yy, x + box, yy + box, paint); paint.style = Paint.Style.FILL; canvas.drawLine(x + box * 1.8f, yy + box, r, yy + box, paint) }
        }
        when (template) {
            "todo-daily" -> {
                heading("DAILY TO-DO"); paint.style = Paint.Style.FILL
                paint.textSize = w * .016f; canvas.drawText("DATE", left, h * .115f, paint); canvas.drawLine(left + w * .07f, h * .115f, w * .5f, h * .115f, paint)
                panel(left, h * .14f, right, h * .27f); paint.style = Paint.Style.FILL; paint.textSize = w * .016f; canvas.drawText("TOP 3 PRIORITIES", left + w * .02f, h * .165f, paint)
                for (i in 0..2) { val y = h * .19f + i * h * .027f; val box = w * .016f; paint.style = Paint.Style.STROKE; canvas.drawRect(left + w * .02f, y, left + w * .02f + box, y + box, paint); paint.style = Paint.Style.FILL; canvas.drawLine(left + w * .06f, y + box, right - w * .02f, y + box, paint) }
                paint.textSize = w * .016f; canvas.drawText("TASKS", left, h * .305f, paint); canvas.drawText("P", right - w * .17f, h * .305f, paint); canvas.drawText("DONE", right - w * .06f, h * .305f, paint)
                canvas.drawLine(right - w * .19f, h * .31f, right - w * .19f, h * .78f, paint); canvas.drawLine(right - w * .1f, h * .31f, right - w * .1f, h * .78f, paint)
                for (i in 0..11) { val y = h * .325f + i * h * .0375f; val box = w * .018f; paint.style = Paint.Style.STROKE; canvas.drawRect(left, y, left + box, y + box, paint); paint.style = Paint.Style.FILL; canvas.drawLine(left + w * .045f, y + box, right, y + box, paint) }
                panel(left, h * .8f, right, h * .94f); paint.style = Paint.Style.FILL; paint.textSize = w * .016f; canvas.drawText("NOTES", left + w * .02f, h * .825f, paint)
            }
            "todo" -> { heading("TO-DO LIST"); paint.style = Paint.Style.STROKE; for (i in 0..12) { val y = h * .15f + i * h * .06f; val box = w * .022f; canvas.drawRect(left, y, left + box, y + box, paint); canvas.drawLine(left + w * .08f, y + box, right, y + box, paint) }; paint.style = Paint.Style.FILL }
            "meeting" -> { heading("MEETING NOTES"); canvas.drawLine(left, h * .12f, right, h * .12f, paint); canvas.drawLine(left, h * .18f, right, h * .18f, paint); ruled(h * .24f); canvas.drawLine(w * .72f, h * .24f, w * .72f, h * .95f, paint) }
            "daily" -> {
                heading("DAILY PLANNER"); val top = h * .13f
                panel(left, top, right, h * .23f); panel(left, h * .25f, w * .59f, h * .49f)
                panel(w * .62f, h * .25f, right, h * .49f); panel(left, h * .51f, w * .59f, h * .73f)
                panel(w * .62f, h * .51f, right, h * .73f); panel(left, h * .75f, right, h * .94f)
                checklist(left + w * .02f, h * .29f, w * .56f, 4, h * .044f)
                checklist(w * .64f, h * .29f, right - w * .02f, 4, h * .044f)
                ruled(h * .78f, h * .93f, h * .04f)
            }
            "weekly", "project" -> {
                heading(if (template == "weekly") "WEEKLY PLANNER" else "PROJECT PLANNER")
                val top = h * .14f; val bottom = h * .94f; val columns = if (template == "weekly") 5 else 2
                for (i in 0..columns) { val x = left + (right - left) * i / columns; canvas.drawLine(x, top, x, bottom, paint) }
                for (i in 0..5) { val y = top + (bottom - top) * i / 5f; canvas.drawLine(left, y, right, y, paint) }
            }
            "time-block" -> { heading("TIME BLOCKS"); val top = h * .13f; val gap = h * .064f; for (i in 0..12) { val y = top + i * gap; paint.textSize = w * .018f; canvas.drawText("${6 + i}:00", left, y + gap * .68f, paint); canvas.drawLine(left + w * .12f, y, right, y, paint) }; canvas.drawLine(left + w * .12f, top + gap * 13, right, top + gap * 13, paint) }
            "monthly" -> { heading("MONTHLY PLANNER"); val top = h * .14f; val bottom = h * .93f; for (i in 0..7) canvas.drawLine(left + (right-left) * i / 7, top, left + (right-left) * i / 7, bottom, paint); for (i in 0..5) canvas.drawLine(left, top + (bottom-top) * i / 5, right, top + (bottom-top) * i / 5, paint) }
            "habit" -> { heading("HABIT TRACKER"); val top = h * .15f; val nameEnd = w * .40f; canvas.drawLine(nameEnd, top, nameEnd, h * .94f, paint); for (i in 0..7) canvas.drawLine(nameEnd + (right-nameEnd) * i / 7, top, nameEnd + (right-nameEnd) * i / 7, h * .94f, paint); for (i in 0..11) canvas.drawLine(left, top + (h*.79f) * i / 11, right, top + (h*.79f) * i / 11, paint) }
            "priority" -> { heading("PRIORITY MATRIX"); val top = h * .16f; canvas.drawLine(w*.5f, top, w*.5f, h*.92f, paint); canvas.drawLine(left, h*.54f, right, h*.54f, paint); panel(left, top, w*.49f, h*.52f); panel(w*.51f, top, right, h*.52f); panel(left, h*.56f, w*.49f, h*.92f); panel(w*.51f, h*.56f, right, h*.92f) }
            "kanban" -> { heading("KANBAN BOARD"); val top = h*.14f; for (i in 0..3) { val x = left + (right-left)*i/3; canvas.drawLine(x, top, x, h*.94f, paint) }; for (i in 0..2) { val x = left + (right-left)*i/3 + w*.012f; for (j in 0..3) panel(x, top+h*.06f+j*h*.19f, x+(right-left)/3-w*.024f, top+h*.20f+j*h*.19f) } }
            "revision" -> { heading("REVISION NOTES"); panel(left, h*.13f, right, h*.27f); panel(left, h*.30f, w*.5f, h*.59f); panel(w*.5f, h*.30f, right, h*.59f); panel(left, h*.62f, right, h*.94f); ruled(h*.33f,h*.57f,h*.045f); ruled(h*.65f,h*.92f,h*.045f) }
            "vocabulary" -> { heading("VOCABULARY"); val top=h*.14f; canvas.drawLine(w*.34f,top,w*.34f,h*.94f,paint); canvas.drawLine(w*.68f,top,w*.68f,h*.94f,paint); for(i in 0..12) canvas.drawLine(left,top+(h*.8f)*i/12,right,top+(h*.8f)*i/12,paint) }
            "lab" -> { heading("LAB REPORT"); val labels = listOf("QUESTION", "HYPOTHESIS", "MATERIALS", "METHOD", "OBSERVATIONS", "CONCLUSION"); val heights = listOf(.10f,.13f,.12f,.18f,.22f,.11f); var y=h*.12f; labels.indices.forEach { i -> paint.textSize=w*.018f; canvas.drawText(labels[i],left,y+h*.025f,paint); y += h*heights[i]; canvas.drawLine(left,y,right,y,paint) } }
            "cornell" -> { heading("CORNELL NOTES"); val gap = w / 24f; var y = h * .12f; while (y < h * .78f) { canvas.drawLine(w * .3f, y, right, y, paint); y += gap }; y = h * .78f + gap; while (y < h * .95f) { canvas.drawLine(left, y, right, y, paint); y += gap }; paint.color = Color.rgb(180, 186, 190); canvas.drawLine(w * .3f, h * .12f, w * .3f, h * .78f, paint); canvas.drawLine(left, h * .78f, right, h * .78f, paint); paint.color = Color.rgb(210, 216, 214) }
            "lecture", "reading" -> { heading(if (template == "lecture") "LECTURE NOTES" else "READING NOTES"); canvas.drawLine(left, h * .12f, right, h * .12f, paint); ruled(h * .18f); if (template == "reading") canvas.drawLine(w * .68f, h * .18f, w * .68f, h * .95f, paint) }
            "graph" -> { val step=w/22f; var y=0f; while(y<h) { canvas.drawLine(0f,y,w,y,paint); y+=step }; var x=0f; while(x<w) { canvas.drawLine(x,0f,x,h,paint); x+=step } }
            "hex-grid" -> {
                val radius = w / 36f
                val horizontal = kotlin.math.sqrt(3f) * radius
                val vertical = 1.5f * radius
                var row = 0
                var cy = -radius
                while (cy < h + radius) {
                    var cx = if (row % 2 == 0) 0f else horizontal / 2f
                    while (cx < w + horizontal) {
                        var previousX = cx
                        var previousY = cy - radius
                        for (side in 1..6) {
                            val angle = (side * 60f - 90f) * kotlin.math.PI.toFloat() / 180f
                            val nextX = cx + radius * kotlin.math.cos(angle)
                            val nextY = cy + radius * kotlin.math.sin(angle)
                            canvas.drawLine(previousX, previousY, nextX, nextY, paint)
                            previousX = nextX; previousY = nextY
                        }
                        cx += horizontal
                    }
                    cy += vertical; row++
                }
            }
            "sketch" -> { paint.style=Paint.Style.STROKE; canvas.drawRect(left,h*.08f,right,h*.92f,paint); paint.style=Paint.Style.FILL }
            "mind-map" -> { heading("MIND MAP"); val cx=w*.5f; val cy=h*.53f; canvas.drawCircle(cx,cy,w*.12f,paint); canvas.drawCircle(cx,cy,w*.115f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE}); val points=listOf(.2f to .22f,.5f to .16f,.8f to .22f,.2f to .80f,.5f to .86f,.8f to .80f); points.forEach { (px,py)-> val x=w*px; val y=h*py; canvas.drawLine(cx,cy,x,y,paint); canvas.drawCircle(x,y,w*.07f,paint); canvas.drawCircle(x,y,w*.065f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE}) } }
            "comic" -> { heading("COMIC PANELS"); paint.style=Paint.Style.STROKE; for(row in 0..2) for(col in 0..1) { val x=left+col*w*.44f; val y=h*.12f+row*h*.27f; canvas.drawRect(x,y,x+w*.4f,y+h*.23f,paint) }; paint.style=Paint.Style.FILL }
            "isometric" -> { val step = w / 12f; var y = -h; while (y < h * 2) { canvas.drawLine(0f, y, w, y + w, paint); canvas.drawLine(0f, y, w, y - w, paint); y += step } }
            "music" -> { val staffGap = h / 12f; for (staff in 0..2) for (line in 0..4) { val y = h * .12f + staff * staffGap * 3f + line * staffGap * .35f; canvas.drawLine(left, y, right, y, paint) } }
            "storyboard" -> { paint.style = Paint.Style.STROKE; for (row in 0..2) for (col in 0..1) { val x = left + col * w * .44f; val y = h * .08f + row * h * .29f; canvas.drawRoundRect(x, y, x + w * .4f, y + h * .22f, w * .012f, w * .012f, paint) }; paint.style = Paint.Style.FILL }
            "dots", "custom-dots" -> { val step = w / 24f; var y = step; while (y < h) { var x = step; while (x < w) { canvas.drawCircle(x, y, (w / 700f).coerceAtLeast(1f), paint); x += step }; y += step } }
            "grid", "big-grid", "math-grid", "custom-grid" -> { val step = w / if (template == "big-grid") 12f else 24f; var y = step; while (y < h) { canvas.drawLine(0f, y, w, y, paint); y += step }; var x = step; while (x < w) { canvas.drawLine(x, 0f, x, h, paint); x += step } }
            else -> ruled()
        }
    }
}
