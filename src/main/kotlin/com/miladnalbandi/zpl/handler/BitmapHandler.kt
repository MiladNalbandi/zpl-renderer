package com.miladnalbandi.zpl.handler

import com.miladnalbandi.zpl.CommandHandler
import com.miladnalbandi.zpl.LocalZplGraphicDecoder
import com.miladnalbandi.zpl.RenderContext
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.util.*

/**
 * Handles bitmap-related ZPL commands.
 *
 * Consumed commands:
 *   GF  — Graphic Field (^GFA/^GFB) — draw inline bitmap at current position
 *   DG  — Download Graphic (~DG) — decode and store a named bitmap in graphicStore
 *   XG  — Execute Graphic (^XG) — draw a previously stored named bitmap
 */
class BitmapHandler : CommandHandler {

    override fun handle(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext): Boolean {
        return when {
            cmd.startsWith("GF") -> { drawGF(cmd, g, ctx); true }
            cmd.startsWith("DG") -> { storeDownloadGraphic(cmd, ctx); true }
            cmd.startsWith("XG") -> { drawStoredGraphic(cmd, g, ctx); true }
            else -> false
        }
    }

    // ── ^GFA / ^GFB — inline graphic ─────────────────────────────────────────

    private fun drawGF(cmd: String, g: Graphics2D, ctx: RenderContext) {
        val img = LocalZplGraphicDecoder.decodeFirstGraphic("^$cmd^") ?: return
        drawImageRotated(img, g, ctx)
    }

    // ── ~DG — download and store named graphic ────────────────────────────────
    // Format after "DG": "R:name.GRF,filesize,width_bytes,<data>"

    private fun storeDownloadGraphic(cmd: String, ctx: RenderContext) {
        val rest = cmd.drop(2)   // strip "DG"
        val (name, img) = LocalZplGraphicDecoder.decodeDownloadGraphic(rest) ?: return
        ctx.graphicStore[name] = img
    }

    // ── ^XG — draw stored named graphic ──────────────────────────────────────
    // Format after "XG": "R:name.GRF,scale_x,scale_y"

    private fun drawStoredGraphic(cmd: String, g: Graphics2D, ctx: RenderContext) {
        val rest  = cmd.drop(2)   // strip "XG"
        val parts = rest.split(',')
        val name  = parts[0].trim()
        val scaleX = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val scaleY = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        val src = ctx.graphicStore[name] ?: return
        val img = if (scaleX == 1 && scaleY == 1) src else {
            // Scale the stored graphic
            val w = src.width  * scaleX
            val h = src.height * scaleY
            val scaled = BufferedImage(w, h, src.type.takeIf { it != 0 } ?: BufferedImage.TYPE_INT_ARGB)
            val sg = scaled.createGraphics()
            sg.drawImage(src, 0, 0, w, h, null)
            sg.dispose()
            scaled
        }
        drawImageRotated(img, g, ctx)
    }

    // ── shared ────────────────────────────────────────────────────────────────

    private fun drawImageRotated(img: BufferedImage, g: Graphics2D, ctx: RenderContext) {
        val angle = when (ctx.rot) {
            'R' -> Math.toRadians(90.0)
            'I' -> Math.toRadians(180.0)
            'B' -> Math.toRadians(270.0)
            else -> 0.0
        }
        val oldTransform = g.transform
        if (angle != 0.0) g.rotate(angle, ctx.x.toDouble(), ctx.y.toDouble())
        g.drawImage(img, ctx.x, ctx.y, null)
        g.transform = oldTransform
    }
}