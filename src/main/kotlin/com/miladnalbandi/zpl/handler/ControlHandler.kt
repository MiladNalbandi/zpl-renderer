package com.miladnalbandi.zpl.handler

import com.miladnalbandi.zpl.CommandHandler
import com.miladnalbandi.zpl.RenderContext
import java.awt.Font
import java.awt.Graphics2D
import java.util.*

/**
 * Handles ZPL control and label-level commands.
 *
 * Consumed commands:
 *   XA  — start of label; resets all context state
 *   XZ  — end of label marker (no-op)
 *   FX  — comment line (no-op)
 *   LH  — label home offset
 *   PW  — label width (printer info, ignored)
 *   LL  — label length (printer info, ignored)
 *   CF  — change default font (stores height, updates ctx.font)
 *   FW  — field write orientation (default rotation for all fields)
 *   MM, MN, MT, MD, MF — media/print settings (no-op)
 *   PR  — print rate (no-op)
 *   PQ  — print quantity (no-op)
 *   JZ, JA, JB, JM, JN, JP, JR, JS — calibration/config commands (no-op)
 */
class ControlHandler : CommandHandler {

    override fun handle(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext): Boolean {
        when {
            cmd == "XA" -> ctx.reset()
            cmd == "XZ" -> { /* end of label — no-op */ }

            // Comment line
            cmd.startsWith("FX") -> { /* no-op */ }

            // Label home offset  ^LHx,y
            cmd.startsWith("LH") -> {
                val p = cmd.drop(2).split(',')
                ctx.labelHomeX = p[0].toIntOrNull() ?: 0
                ctx.labelHomeY = p.getOrNull(1)?.toIntOrNull() ?: 0
            }

            // Change default font  ^CFf,h[,w]
            // f = font name (ignored, mapped to SansSerif)
            // h = character height in dots
            // w = character width in dots (ignored)
            cmd.startsWith("CF") -> {
                val p = cmd.drop(2).split(',')
                val h = p.getOrNull(1)?.toIntOrNull() ?: ctx.defaultFontHeight
                ctx.defaultFontHeight = h
                ctx.font = Font("SansSerif", Font.PLAIN, scale(h, ctx.dpi))
            }

            // Default field write orientation  ^FWo
            cmd.startsWith("FW") -> {
                val orient = cmd.drop(2).firstOrNull() ?: 'N'
                if (orient in "NRIB") {
                    ctx.defaultRot = orient
                    ctx.rot = orient
                }
            }

            // Printer/media settings — informational only, no visual effect
            cmd.startsWith("PW") -> { /* label width — no-op */ }
            cmd.startsWith("LL") -> { /* label length — no-op */ }
            cmd.startsWith("MM") -> { /* media mode — no-op */ }
            cmd.startsWith("MN") -> { /* media tracking — no-op */ }
            cmd.startsWith("MT") -> { /* media type — no-op */ }
            cmd.startsWith("MD") -> { /* media darkness — no-op */ }
            cmd.startsWith("MF") -> { /* media feed — no-op */ }
            cmd.startsWith("PR") -> { /* print rate — no-op */ }
            cmd.startsWith("PQ") -> { /* print quantity — no-op */ }
            // Calibration / host commands
            cmd.startsWith("JZ") || cmd.startsWith("JA") || cmd.startsWith("JB") ||
            cmd.startsWith("JM") || cmd.startsWith("JN") || cmd.startsWith("JP") ||
            cmd.startsWith("JR") || cmd.startsWith("JS") -> { /* calibration — no-op */ }

            // Download Format — ^DFname^FS : begin capturing subsequent commands as a named template
            cmd.startsWith("DF") -> {
                ctx.capturingFormat = cmd.drop(2)
                ctx.captureBuffer.clear()
                // consume the mandatory ^FS terminator that follows ^DFname
                if (it.hasNext()) {
                    val nxt = it.next().trim()
                    if (nxt != "FS") it.previous()
                }
            }

            else -> return false
        }
        return true
    }

    private fun scale(h203: Int, dpi: Int) = (h203 * (dpi / 203.0)).toInt().coerceAtLeast(1)
}
