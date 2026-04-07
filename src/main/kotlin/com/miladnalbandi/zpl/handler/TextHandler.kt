package com.miladnalbandi.zpl.handler

import com.miladnalbandi.zpl.CommandHandler
import com.miladnalbandi.zpl.RenderContext
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.util.*

/**
 * Handles text-related ZPL commands.
 *
 * Consumed commands:
 *   FO / FT — field origin / typeset (sets x, y with label-home offset)
 *   A       — font selection; ^Afo,h,w (f=font, o=orientation, h=height, w=width)
 *   CI      — international character set (encoding hint, no-op)
 *   FR / FI — field reverse / invert (next field drawn white-on-black)
 *   FH      — field hex indicator (enables _xx hex escapes in ^FD)
 *   FN      — field name/number placeholder (no-op)
 *   FB      — field block (enables word-wrap with a given width)
 *   FD      — field data (stores pending text)
 *   FV      — field variable (alias for FD)
 *   FS      — field separator (flushes pending text to canvas)
 */
class TextHandler : CommandHandler {

    private var blockWidth = 0
    private var pending: String? = null
    private var fontWidthRatio = 1.0   // from ^A w/h; <1.0 = condensed font

    override fun handle(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext): Boolean {
        when {
            // ── Positioning ──────────────────────────────────────────────────────
            cmd.startsWith("FO") -> {
                val p = cmd.drop(2).split(',')
                ctx.x = (p[0].toIntOrNull() ?: ctx.x) + ctx.labelHomeX
                ctx.y = (p.getOrNull(1)?.toIntOrNull() ?: ctx.y) + ctx.labelHomeY
                // 3rd param = justification (0=L, 1=R, 2=auto) — not yet applied
            }
            cmd.startsWith("FT") -> {
                val p = cmd.drop(2).split(',')
                if (p.size >= 2) {
                    ctx.x = (p[0].toIntOrNull() ?: ctx.x) + ctx.labelHomeX
                    ctx.y = (p[1].toIntOrNull() ?: ctx.y) + ctx.labelHomeY
                }
            }

            // ── Font selection  ^Afo,h,w ─────────────────────────────────────────
            // f  = font name (single char: '0'–'Z'); we map everything to SansSerif
            // o  = orientation (N/R/I/B); may be fused with f, e.g. "0N"
            // h  = character height in dots
            // w  = character width in dots; if w < h apply horizontal condensation
            cmd.startsWith("A") -> {
                val rest = cmd.drop(1)           // e.g. "0N,36,20" or "0,36"
                val orientChar = rest.getOrNull(1)
                val hasOrient = orientChar != null && orientChar in "NRIB"
                if (hasOrient) ctx.rot = orientChar!!
                val paramStr = rest.drop(if (hasOrient) 2 else 1).trimStart(',')
                val parts = if (paramStr.isEmpty()) emptyList() else paramStr.split(',')
                val h = parts.getOrNull(0)?.toIntOrNull() ?: ctx.defaultFontHeight
                val w = parts.getOrNull(1)?.toIntOrNull() ?: 0
                ctx.font = Font("SansSerif", Font.PLAIN, scale(h, ctx.dpi))
                fontWidthRatio = if (w > 0 && h > 0) w.toDouble() / h.toDouble() else 1.0
            }

            // ── Field flags ───────────────────────────────────────────────────────
            cmd.startsWith("CI") -> { /* UTF-8 / international encoding hint — no-op */ }

            // Field Reverse/Invert — draw in white (NOT a rotation command)
            cmd == "FR" || cmd == "FI" -> ctx.fieldReverse = true

            // Field Hex — enables _xx hex-escape decoding in the next ^FD
            cmd.startsWith("FH") -> ctx.fieldHex = true

            // Field Name/Number — store pending field number for variable reference
            cmd.startsWith("FN") -> {
                ctx.pendingFieldNum = cmd.drop(2).toIntOrNull()
            }

            // ── Block text  ^FBw,n,g,j,i ──────────────────────────────────────────
            // w = width in dots for word-wrap (only param we use)
            cmd.startsWith("FB") -> {
                blockWidth = cmd.drop(2).split(',')[0].toIntOrNull() ?: 0
            }

            // ── Text content ─────────────────────────────────────────────────────
            cmd.startsWith("FD") -> {
                val data = cmd.drop(2)
                if (ctx.pendingFieldNum != null) {
                    // Variable definition: ^FN##^FDvalue^FS → store only, don't draw
                    ctx.variables[ctx.pendingFieldNum!!] = data
                    ctx.pendingFieldNum = null
                    // pending stays null → ^FS will flush nothing
                } else {
                    pending = data
                }
            }
            cmd.startsWith("FV") -> {
                val data = cmd.drop(2)
                if (ctx.pendingFieldNum != null) {
                    ctx.variables[ctx.pendingFieldNum!!] = data
                    ctx.pendingFieldNum = null
                } else {
                    pending = data
                }
            }
            cmd.startsWith("FS") -> {
                if (ctx.pendingFieldNum != null) {
                    // Variable draw: ^FN##^FS → draw variable value at current position
                    pending = ctx.variables[ctx.pendingFieldNum] ?: ""
                    ctx.pendingFieldNum = null
                }
                flush(g, ctx)
                blockWidth = 0
            }

            else -> return false
        }
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private fun flush(g: Graphics2D, ctx: RenderContext) {
        val txt = pending
        if (txt != null) {
            val processed = processText(txt, ctx)

            // Apply horizontal condensation from ^A width parameter
            val drawFont = if (fontWidthRatio != 1.0 && fontWidthRatio > 0.0) {
                val at = java.awt.geom.AffineTransform.getScaleInstance(fontWidthRatio, 1.0)
                ctx.font.deriveFont(at)
            } else ctx.font

            // Font and metrics must be set before computing background rect
            g.font = drawFont
            val fm = g.fontMetrics
            val lines = if (blockWidth > 0) wrapText(processed, blockWidth, fm) else listOf(processed)

            val oldColor = g.color

            if (ctx.fieldReverse) {
                // Fill a black background rectangle, then draw white text on top
                val textW = lines.maxOfOrNull { fm.stringWidth(it) } ?: fm.stringWidth(processed)
                val textH = fm.height * lines.size
                g.color = Color.BLACK
                g.fillRect(ctx.x, ctx.y, textW, textH)
                g.color = Color.WHITE
            }

            val angle = when (ctx.rot) {
                'R' -> 90.0
                'I' -> 180.0
                'B' -> 270.0
                else -> 0.0
            }

            val oldTransform = g.transform
            if (angle != 0.0) {
                g.rotate(Math.toRadians(angle), ctx.x.toDouble(), ctx.y.toDouble())
            }

            var yCursor = ctx.y + fm.ascent
            for (line in lines) {
                g.drawString(line, ctx.x, yCursor)
                yCursor += fm.height
            }

            g.transform = oldTransform
            g.color = oldColor
        }

        pending = null
        ctx.fieldReverse = false
        ctx.fieldHex = false
        ctx.pendingFieldNum = null
        fontWidthRatio = 1.0
        // Per-field rotation: reset to the label-wide default after each field ends
        ctx.rot = ctx.defaultRot
    }

    /**
     * Process ZPL field data:
     *  - Unescape backslash sequences
     *  - If ctx.fieldHex is set, decode _xx hex sequences (e.g. _5F → '_')
     *
     * Note: underscores are NOT replaced with spaces — that was a bug in the
     * original code.  Underscores are literal unless ^FH is active.
     */
    private fun processText(text: String, ctx: RenderContext): String {
        var result = text
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")

        if (ctx.fieldHex) {
            result = result.replace(Regex("_([0-9A-Fa-f]{2})")) { mr ->
                mr.groupValues[1].toInt(16).toChar().toString()
            }
        }

        return result
    }

    private fun wrapText(text: String, w: Int, fm: FontMetrics): List<String> {
        if (text.contains("\n")) {
            return text.split("\n").flatMap { wrapText(it, w, fm) }
        }
        val out = mutableListOf<String>()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            val tryLine = if (line.isEmpty()) word else "$line $word"
            if (fm.stringWidth(tryLine) > w) {
                if (line.isNotEmpty()) out += line.toString()
                line = StringBuilder(word)
            } else {
                line.append(if (line.isEmpty()) word else " $word")
            }
        }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }

    // ZPL dot values are already at the printer's DPI — map 1:1 to pixels, no cross-DPI scaling.
    private fun scale(h: Int, @Suppress("UNUSED_PARAMETER") dpi: Int) = h.coerceAtLeast(1)
}
