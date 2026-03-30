package com.miladnalbandi.zpl.handler

import com.miladnalbandi.zpl.CommandHandler
import com.miladnalbandi.zpl.RenderContext
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.util.*

/**
 * Handles ZPL graphic-element commands.
 *
 * Consumed commands:
 *   GB  — Graphic Box       ^GBw,h,t,c,r
 *   GE  — Graphic Ellipse   ^GEw,h,t,c
 *   GD  — Graphic Diagonal  ^GDw,h,t,c,o
 *
 * Parameter conventions shared by all three:
 *   w = width in dots
 *   h = height in dots
 *   t = border thickness in dots
 *   c = color — 'B' (black) or 'W' (white); also overridden by ctx.fieldReverse
 *
 * Fill rule: if t * 2 >= min(w, h) the shape is completely filled; otherwise
 * only the border/outline is drawn at the given thickness.
 */
class RectangleHandler : CommandHandler {

    override fun handle(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext): Boolean {
        return when {
            cmd.startsWith("GB") -> { drawBox(cmd, g, ctx); true }
            cmd.startsWith("GE") -> { drawEllipse(cmd, g, ctx); true }
            cmd.startsWith("GD") -> { drawDiagonal(cmd, g, ctx); true }
            else -> false
        }
    }

    // ── ^GBw,h,t,c,r ─────────────────────────────────────────────────────────
    private fun drawBox(cmd: String, g: Graphics2D, ctx: RenderContext) {
        val p = cmd.drop(2).split(',')
        val w = p[0].toIntOrNull() ?: 0
        val h = p.getOrNull(1)?.toIntOrNull() ?: w
        val thickness = p.getOrNull(2)?.toIntOrNull() ?: 1
        val colorParam = p.getOrNull(3) ?: "B"
        val rounding = p.getOrNull(4)?.toIntOrNull() ?: 0

        withGraphicState(g, ctx, colorParam) { drawWhite ->
            val filled = thickness * 2 >= minOf(w, h)
            if (filled) {
                if (rounding > 0) g.fillRoundRect(ctx.x, ctx.y, w, h, rounding, rounding)
                else              g.fillRect(ctx.x, ctx.y, w, h)
            } else {
                val oldStroke = g.stroke
                g.stroke = BasicStroke(thickness.toFloat())
                if (rounding > 0) g.drawRoundRect(ctx.x, ctx.y, w, h, rounding, rounding)
                else              g.drawRect(ctx.x, ctx.y, w, h)
                g.stroke = oldStroke
            }
        }
    }

    // ── ^GEw,h,t,c ───────────────────────────────────────────────────────────
    private fun drawEllipse(cmd: String, g: Graphics2D, ctx: RenderContext) {
        val p = cmd.drop(2).split(',')
        val w = p[0].toIntOrNull() ?: 0
        val h = p.getOrNull(1)?.toIntOrNull() ?: w
        val thickness = p.getOrNull(2)?.toIntOrNull() ?: 1
        val colorParam = p.getOrNull(3) ?: "B"

        withGraphicState(g, ctx, colorParam) { _ ->
            val filled = thickness * 2 >= minOf(w, h)
            if (filled) {
                g.fillOval(ctx.x, ctx.y, w, h)
            } else {
                val oldStroke = g.stroke
                g.stroke = BasicStroke(thickness.toFloat())
                g.drawOval(ctx.x, ctx.y, w, h)
                g.stroke = oldStroke
            }
        }
    }

    // ── ^GDw,h,t,c,o ─────────────────────────────────────────────────────────
    // o = 'R' (right-leaning diagonal) or 'L' (left-leaning diagonal)
    private fun drawDiagonal(cmd: String, g: Graphics2D, ctx: RenderContext) {
        val p = cmd.drop(2).split(',')
        val w = p[0].toIntOrNull() ?: 0
        val h = p.getOrNull(1)?.toIntOrNull() ?: w
        val thickness = p.getOrNull(2)?.toIntOrNull() ?: 1
        val colorParam = p.getOrNull(3) ?: "B"
        val orientation = p.getOrNull(4)?.firstOrNull() ?: 'R'

        withGraphicState(g, ctx, colorParam) { _ ->
            val oldStroke = g.stroke
            g.stroke = BasicStroke(thickness.toFloat())
            if (orientation == 'L') {
                // Left-leaning: top-right to bottom-left
                g.drawLine(ctx.x + w, ctx.y, ctx.x, ctx.y + h)
            } else {
                // Right-leaning: top-left to bottom-right
                g.drawLine(ctx.x, ctx.y, ctx.x + w, ctx.y + h)
            }
            g.stroke = oldStroke
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared setup: rotation transform, color selection, fieldReverse clearing.
    // The [block] receives a flag indicating whether white is being drawn.
    private inline fun withGraphicState(
        g: Graphics2D,
        ctx: RenderContext,
        colorParam: String,
        block: (drawWhite: Boolean) -> Unit
    ) {
        val drawWhite = colorParam == "W" || ctx.fieldReverse
        val oldColor = g.color
        g.color = if (drawWhite) Color.WHITE else Color.BLACK

        val oldTransform = g.transform
        val angle = when (ctx.rot) {
            'R' -> Math.toRadians(90.0)
            'I' -> Math.toRadians(180.0)
            'B' -> Math.toRadians(270.0)
            else -> 0.0
        }
        if (angle != 0.0) g.rotate(angle, ctx.x.toDouble(), ctx.y.toDouble())

        block(drawWhite)

        g.transform = oldTransform
        g.color = oldColor
        ctx.fieldReverse = false
    }
}
