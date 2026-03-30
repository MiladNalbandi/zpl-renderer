package com.miladnalbandi.zpl.handler

import com.miladnalbandi.zpl.CommandHandler
import com.miladnalbandi.zpl.LocalZplGraphicDecoder
import com.miladnalbandi.zpl.RenderContext
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.util.*

/**
 * Handles bitmap-related ZPL commands.
 * Supports GFA and GFB commands for images.
 */
class BitmapHandler : CommandHandler {
    override fun handle(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext): Boolean {
        // Handle both GFA and GFB formats
        if (!cmd.startsWith("GF")) return false

        // We need to prepend ^ to make it a valid ZPL command for the decoder
        val img = LocalZplGraphicDecoder.decodeFirstGraphic("^$cmd^") ?: return true

        // Apply rotation based on context
        val angle = when (ctx.rot) {
            'R' -> Math.toRadians(90.0)
            'I' -> Math.toRadians(180.0)
            'B' -> Math.toRadians(270.0)
            else -> 0.0
        }

        // Save the current transformation
        val oldTransform = g.transform

        if (angle != 0.0) {
            // Apply rotation around the current position
            g.rotate(angle, ctx.x.toDouble(), ctx.y.toDouble())
        }

        // Draw the image
        g.drawImage(img, ctx.x, ctx.y, null)

        // Restore the original transformation
        g.transform = oldTransform

        return true
    }
}