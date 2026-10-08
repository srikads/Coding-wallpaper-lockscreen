package com.srikads.codewall.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import com.srikads.codewall.core.Align
import com.srikads.codewall.core.CodeTheme
import com.srikads.codewall.core.Line
import com.srikads.codewall.core.Span
import com.srikads.codewall.core.Tok
import com.srikads.codewall.core.WallConfig
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Draws highlighted [Line]s onto a Canvas like a code editor, with
 * soft wrapping, auto-fit, line numbers and a few subtle animations.
 * Used by both the live wallpaper and the in-app preview.
 */
class WallRenderer(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val scaledDensity = density * context.resources.configuration.fontScale

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private var config = WallConfig()
    private var theme: CodeTheme = config.theme
    private var loadedFont: String? = "\u0000"
    private var baseTypeface: Typeface = Typeface.MONOSPACE

    private var lines: List<Line> = emptyList()
    private var footer: List<Line> = emptyList()
    private val lastText = HashMap<String, String>()
    private val changedAt = HashMap<String, Long>()
    private var typingStart = -1L

    /** 0..1 horizontal home-screen scroll position, used for parallax. */
    var parallaxOffset = 0.5f

    /** Outlines the code and footer areas (in-app preview only). */
    var showGuides = false
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }

    fun setConfig(newConfig: WallConfig) {
        config = newConfig
        theme = newConfig.theme
        if (newConfig.fontPath != loadedFont) {
            loadedFont = newConfig.fontPath
            baseTypeface = newConfig.fontPath?.let { p -> runCatching { Typeface.createFromFile(File(p)) }.getOrNull() }
                ?: Typeface.MONOSPACE
        }
        paint.typeface = if (newConfig.bold) Typeface.create(baseTypeface, Typeface.BOLD) else baseTypeface
    }

    /** Replaces the content. Lines whose text changed get a brief highlight. */
    fun submit(newLines: List<Line>, newFooter: List<Line>, nowMs: Long, ignoreChangesContaining: String? = null) {
        footer = newFooter
        val first = lastText.isEmpty()
        val seen = HashSet<String>()
        for (l in newLines) {
            seen += l.id
            val prev = lastText.put(l.id, l.text)
            val isClock = l.id.endsWith("/TIME") ||
                (ignoreChangesContaining != null && l.text.contains(ignoreChangesContaining))
            if (!first && prev != null && prev != l.text && !isClock && !l.id.startsWith("#header")) {
                changedAt[l.id] = nowMs
            }
        }
        lastText.keys.retainAll(seen)
        changedAt.keys.retainAll(seen)
        lines = newLines
    }

    /** Starts the "typing" reveal, e.g. when the screen turns on. */
    fun startTyping(nowMs: Long) {
        if (config.typingEffect) typingStart = nowMs
    }

    private data class Row(val spans: List<Span>, val number: Int?, val lineId: String)

    /** Draws a frame. Returns true while an animation is running and frames should keep coming quickly. */
    fun draw(canvas: Canvas, width: Int, height: Int, nowMs: Long, locked: Boolean): Boolean {
        canvas.drawColor(theme.background)
        if (width <= 0 || height <= 0) return false

        val (areaTopF, areaBottomF) = if (locked) config.lockTop to config.lockBottom else config.homeTop to config.homeBottom
        val areaTop = height * areaTopF.coerceIn(0f, 1f)
        val areaBottom = max(areaTop + height * 0.05f, height * areaBottomF.coerceIn(0f, 1f))
        if (showGuides) {
            guidePaint.color = (theme.comment and 0xFFFFFF) or (0x99 shl 24)
            guidePaint.strokeWidth = density
            canvas.drawRect(density * 4, areaTop, width - density * 4, areaBottom, guidePaint)
            if (footer.isNotEmpty()) canvas.drawRect(density * 4, height * config.footerTop, width - density * 4, height * config.footerBottom, guidePaint)
        }
        drawFooter(canvas, width, height)
        if (lines.isEmpty()) return false

        val pad = config.horizontalPaddingDp * density
        val parallaxRange = if (config.parallax) 18 * density else 0f
        val shiftX = (0.5f - parallaxOffset) * 2 * parallaxRange

        // Fit: shrink the font until everything fits in the code area.
        var size = config.fontSizeSp * scaledDensity
        var rows: List<Row> = emptyList()
        var charW = 0f
        var lineH = 0f
        var gutter = 0f
        for (attempt in 0 until 4) {
            paint.textSize = size
            charW = paint.measureText("M")
            lineH = paint.fontSpacing * 1.12f
            gutter = if (config.lineNumbers) (lines.size.toString().length + 2) * charW else 0f
            val maxChars = max(12, floor((width - 2 * pad - gutter - parallaxRange) / charW).toInt())
            rows = wrap(lines, maxChars)
            val needed = rows.size * lineH
            val available = areaBottom - areaTop
            if (needed <= available) break
            size = max(8 * scaledDensity, size * available / needed)
        }

        val blockH = rows.size * lineH
        val widest = rows.maxOf { r -> r.spans.sumOf { it.text.length } } * charW + gutter
        val left = when (config.align) {
            Align.LEFT -> pad
            Align.CENTER -> max(pad, (width - widest) / 2f)
        } + shiftX
        val top = areaTop + max(0f, areaBottom - areaTop - blockH) * config.verticalPosition.coerceIn(0f, 1f)
        val ascent = -paint.fontMetrics.ascent + (lineH - paint.fontSpacing) / 2f

        // Typing reveal.
        val totalChars = rows.sumOf { r -> r.spans.sumOf { it.text.length } }
        var budget = Int.MAX_VALUE
        var typing = false
        if (typingStart >= 0) {
            val elapsed = nowMs - typingStart
            val perMs = max(totalChars / TYPING_MS.toFloat(), 0.25f)
            budget = (elapsed * perMs).toInt()
            if (budget >= totalChars) typingStart = -1 else typing = true
        }

        var animating = typing
        var cursorX = left + gutter
        var cursorY = top
        for ((i, row) in rows.withIndex()) {
            val y = top + i * lineH
            if (budget <= 0) break

            if (config.flashChanges) {
                changedAt[row.lineId]?.let { t ->
                    val a = 1f - (nowMs - t) / FLASH_MS.toFloat()
                    if (a > 0f) {
                        animating = true
                        val base = theme.highlight
                        val alpha = ((base ushr 24) * a).toInt().coerceIn(0, 255)
                        fill.color = (alpha shl 24) or (base and 0xFFFFFF)
                        canvas.drawRect(left - charW / 2, y, width.toFloat(), y + lineH, fill)
                    } else changedAt.remove(row.lineId)
                }
            }

            if (row.number != null && config.lineNumbers) {
                paint.color = theme.lineNumber
                val num = row.number.toString()
                canvas.drawText(num, left + gutter - (num.length + 1.5f) * charW, y + ascent, paint)
            }

            var x = left + gutter
            for (span in row.spans) {
                if (budget <= 0) break
                val text = if (span.text.length > budget) span.text.substring(0, budget) else span.text
                budget -= text.length
                paint.color = theme.color(span.tok)
                canvas.drawText(text, x, y + ascent, paint)
                x += paint.measureText(text)
            }
            cursorX = x
            cursorY = y
        }

        val cursorOn = typing || (config.blinkingCursor && (nowMs / 530) % 2 == 0L)
        if (cursorOn) {
            fill.color = theme.cursor
            canvas.drawRect(cursorX + 1, cursorY + lineH * 0.12f, cursorX + 1 + max(2f, charW * 0.55f), cursorY + lineH * 0.92f, fill)
        }
        return animating
    }

    /** Draws the footer (e.g. the quote) centered in its own slot, shrinking it to fit. */
    private fun drawFooter(canvas: Canvas, width: Int, height: Int) {
        if (footer.isEmpty()) return
        val top = height * config.footerTop.coerceIn(0f, 1f)
        val bottom = max(top + height * 0.02f, height * config.footerBottom.coerceIn(0f, 1f))
        val pad = max(config.horizontalPaddingDp * density, 16 * density)
        var size = config.fontSizeSp * scaledDensity * 0.85f
        var rows: List<Row> = emptyList()
        var lineH = 0f
        for (attempt in 0 until 4) {
            paint.textSize = size
            lineH = paint.fontSpacing * 1.08f
            val maxChars = max(12, floor((width - 2 * pad) / paint.measureText("M")).toInt())
            rows = wrap(footer, maxChars, hangingIndent = false)
            val needed = rows.size * lineH
            if (needed <= bottom - top) break
            size = max(8 * scaledDensity, size * (bottom - top) / needed)
        }
        val ascent = -paint.fontMetrics.ascent + (lineH - paint.fontSpacing) / 2f
        var y = top + max(0f, (bottom - top - rows.size * lineH) / 2f)
        val shiftX = if (config.parallax) (0.5f - parallaxOffset) * 36 * density else 0f
        for (row in rows) {
            val text = row.spans.joinToString("") { it.text }.trim()
            val w = paint.measureText(text)
            paint.color = row.spans.firstOrNull()?.let { theme.color(it.tok) } ?: theme.comment
            canvas.drawText(text, (width - w) / 2f + shiftX, y + ascent, paint)
            y += lineH
        }
    }

    /** Soft-wraps logical lines into rows of at most [maxChars] characters. */
    private fun wrap(lines: List<Line>, maxChars: Int, hangingIndent: Boolean = true): List<Row> {
        val rows = ArrayList<Row>()
        lines.forEachIndexed { index, line ->
            if (line.length <= maxChars) {
                rows += Row(line.spans, index + 1, line.id)
                return@forEachIndexed
            }
            val leading = line.text.takeWhile { it == ' ' }.length
            val contIndent = if (hangingIndent) min(leading + 4, maxChars / 2) else 0
            var current = ArrayList<Span>()
            var used = 0
            var number: Int? = index + 1
            fun flush() {
                rows += Row(current, number, line.id)
                number = null
                current = arrayListOf(Span(" ".repeat(contIndent), Tok.PLAIN))
                used = contIndent
            }
            for (span in line.spans) {
                var rest = span.text
                while (rest.isNotEmpty()) {
                    val room = maxChars - used
                    if (room <= 0) { flush(); continue }
                    if (rest.length <= room) {
                        current += Span(rest, span.tok); used += rest.length; rest = ""
                    } else {
                        // Prefer breaking after a space within the available room.
                        val cut = rest.lastIndexOf(' ', room - 1).takeIf { it > room / 3 }?.plus(1) ?: room
                        current += Span(rest.substring(0, cut), span.tok)
                        rest = rest.substring(cut)
                        flush()
                    }
                }
            }
            if (used > contIndent || number != null) rows += Row(current, number, line.id)
        }
        return rows
    }

    companion object {
        private const val TYPING_MS = 900
        private const val FLASH_MS = 1200
    }
}
