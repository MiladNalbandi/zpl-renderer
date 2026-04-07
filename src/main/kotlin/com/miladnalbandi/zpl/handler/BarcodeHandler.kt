package com.miladnalbandi.zpl.handler

import com.miladnalbandi.zpl.graphics.BarcodeUtil
import com.miladnalbandi.zpl.CommandHandler
import com.miladnalbandi.zpl.RenderContext
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles barcode-related ZPL commands.
 *
 * Consumed commands:
 *   BY  — bar code defaults (module width, ratio, height)
 *   BC / B3 — Code 128
 *   BQ  — QR Code
 *   B8  — EAN-8
 *   BE  — EAN-13
 *   BU  — UPC-A
 *   BX  — Data Matrix
 *   BZ  — Aztec (pre-sets orientation for next barcode)
 *
 * Design note: module width and height are stored in RenderContext (ctx.barcodeModule,
 * ctx.barcodeHeight) so they reset cleanly on ^XA and don't bleed across separate
 * ZPL strings rendered by the same handler instance.
 *
 * Per-barcode rotation is consumed and reset to 'N' after each draw so it doesn't
 * carry forward to the next barcode.
 */
class BarcodeHandler : CommandHandler {

    private val barcodeCache = ConcurrentHashMap<String, BufferedImage>()

    // Per-barcode orientation set by ^BC / ^BQ / ^BZ — reset to 'N' after each draw
    private var rotation = 'N'

    companion object {
        private const val MAX_CACHE_SIZE = 50
    }

    fun clearCache() = barcodeCache.clear()

    override fun handle(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext): Boolean {
        when {
            // ^BYm,r,h — bar code field default
            // m = module width (narrowest bar) in dots
            // r = wide-to-narrow ratio (ignored — not used by all symbologies)
            // h = bar code height in dots
            cmd.startsWith("BY") -> {
                val p = cmd.drop(2).split(',')
                ctx.barcodeModule = p[0].toIntOrNull() ?: ctx.barcodeModule
                // p[1] = ratio — ignored
                ctx.barcodeHeight = p.getOrNull(2)?.toIntOrNull() ?: ctx.barcodeHeight
            }
            cmd.startsWith("BC") || cmd.startsWith("B3") -> drawCode128(cmd, it, g, ctx)
            cmd.startsWith("BQ") -> drawQr(cmd, it, g, ctx)
            cmd.startsWith("B8") -> drawEAN8(cmd, it, g, ctx)
            cmd.startsWith("BE") -> drawEAN13(cmd, it, g, ctx)
            cmd.startsWith("BU") -> drawUPCA(cmd, it, g, ctx)
            cmd.startsWith("BX") -> drawDataMatrix(cmd, it, g, ctx)
            // ^BZo — Aztec pre-sets orientation; full Aztec rendering not yet supported
            cmd.startsWith("BZ") -> {
                val orient = cmd.drop(2).firstOrNull() ?: 'N'
                if (orient in "NRIB") rotation = orient
            }
            else -> return false
        }
        return true
    }

    // ─── Code 128  ^BCo,h,f,g,e ──────────────────────────────────────────────
    private fun drawCode128(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext) {
        val params = cmd.drop(2).split(',')
        val orient = params.getOrNull(0)?.firstOrNull() ?: 'N'
        if (orient in "NRIB") rotation = orient
        // ^BC allows overriding the height set by ^BY
        val effectiveHeight = params.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: ctx.barcodeHeight

        val data = fetchFD(it, ctx)
        if (data.isBlank()) return

        val wEst = data.length * 11 * ctx.barcodeModule
        val cacheKey = "CODE128:$data:$wEst:$effectiveHeight:$rotation"
        val barcode = cachedOrGenerate(cacheKey) { BarcodeUtil.code128(data, wEst, effectiveHeight) }
        drawBarcodeWithRotation(barcode, g, ctx)
    }

    // ─── QR Code  ^BQo,m,e,n,d ───────────────────────────────────────────────
    private fun drawQr(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext) {
        val data = fetchFD(it, ctx)
        if (data.isBlank()) return

        // ^BQo,m,e — index 0=orientation, 1=model, 2=magnification (cell size in dots)
        val magnification = cmd.drop(2).split(',').getOrNull(2)?.toIntOrNull() ?: 6
        val size = magnification * ctx.barcodeModule * 4
        val cacheKey = "QR:$data:$size:$rotation"
        val barcode = cachedOrGenerate(cacheKey) { BarcodeUtil.qr(data, size) }
        drawBarcodeWithRotation(barcode, g, ctx)
    }

    // ─── EAN-8  ^B8o,h,f,g ───────────────────────────────────────────────────
    private fun drawEAN8(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext) {
        val data = fetchFD(it, ctx)
        if (data.isBlank()) return

        val h = ctx.barcodeHeight
        val w = (h * 0.8).toInt()
        val cacheKey = "EAN8:$data:$w:$h:$rotation"
        val barcode = cachedOrGenerate(cacheKey) { BarcodeUtil.ean8(data, w, h) }
        drawBarcodeWithRotation(barcode, g, ctx)
    }

    // ─── EAN-13  ^BEo,h,f,g ──────────────────────────────────────────────────
    private fun drawEAN13(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext) {
        val data = fetchFD(it, ctx)
        if (data.isBlank()) return

        val h = ctx.barcodeHeight
        val w = (h * 1.5).toInt()
        val cacheKey = "EAN13:$data:$w:$h:$rotation"
        val barcode = cachedOrGenerate(cacheKey) { BarcodeUtil.ean13(data, w, h) }
        drawBarcodeWithRotation(barcode, g, ctx)
    }

    // ─── UPC-A  ^BUo,h,f,g,e ─────────────────────────────────────────────────
    private fun drawUPCA(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext) {
        val data = fetchFD(it, ctx)
        if (data.isBlank()) return

        val h = ctx.barcodeHeight
        val w = (h * 1.5).toInt()
        val cacheKey = "UPCA:$data:$w:$h:$rotation"
        val barcode = cachedOrGenerate(cacheKey) { BarcodeUtil.upcA(data, w, h) }
        drawBarcodeWithRotation(barcode, g, ctx)
    }

    // ─── Data Matrix  ^BXo,h,q,c ─────────────────────────────────────────────
    private fun drawDataMatrix(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext) {
        val data = fetchFD(it, ctx)
        if (data.isBlank()) return

        val size = (cmd.drop(2).split(',').getOrNull(1)?.toIntOrNull() ?: 10) * ctx.barcodeModule
        val cacheKey = "DATAMATRIX:$data:$size:$rotation"
        val barcode = cachedOrGenerate(cacheKey) { BarcodeUtil.dataMatrix(data, size) }
        drawBarcodeWithRotation(barcode, g, ctx)
    }

    // ─── Shared helpers ───────────────────────────────────────────────────────

    /** Draw the barcode image at the current position, applying per-barcode rotation. */
    private fun drawBarcodeWithRotation(barcode: BufferedImage, g: Graphics2D, ctx: RenderContext) {
        // Per-barcode rotation takes precedence over the field default
        val rot = if (rotation != 'N') rotation else ctx.rot
        val angle = when (rot) {
            'R' -> Math.toRadians(90.0)
            'I' -> Math.toRadians(180.0)
            'B' -> Math.toRadians(270.0)
            else -> 0.0
        }

        if (angle != 0.0) {
            val oldTransform = g.transform
            g.rotate(angle, ctx.x.toDouble(), ctx.y.toDouble())
            g.drawImage(barcode, ctx.x, ctx.y, null)
            g.transform = oldTransform
        } else {
            g.drawImage(barcode, ctx.x, ctx.y, null)
        }

        // Reset per-barcode rotation so it doesn't bleed into the next barcode
        rotation = 'N'
        // Consume any pending fieldReverse (barcode inversion is not yet implemented,
        // but the flag must be cleared to avoid affecting subsequent fields)
        ctx.fieldReverse = false
    }

    /**
     * Return a cached barcode image or generate and cache a new one.
     * Evicts the oldest entry when the cache is full.
     */
    private inline fun cachedOrGenerate(key: String, generate: () -> BufferedImage): BufferedImage {
        barcodeCache[key]?.let { return it }
        if (barcodeCache.size >= MAX_CACHE_SIZE) {
            barcodeCache.keys.firstOrNull()?.let { barcodeCache.remove(it) }
        }
        return generate().also { barcodeCache[key] = it }
    }

    /**
     * Advance the iterator to find the ^FD (field data) command or resolve a ^FN
     * variable reference.
     * Commands have already been split by '^', so there are no embedded "^FD" tokens.
     * Stops at the first ^FS encountered.
     */
    private fun fetchFD(it: ListIterator<String>, ctx: RenderContext): String {
        var data = ""
        while (it.hasNext()) {
            val nxt = it.next()
            when {
                nxt.startsWith("FD") -> data = nxt.drop(2)
                nxt.startsWith("FN") -> {
                    val num = nxt.drop(2).toIntOrNull()
                    if (num != null) data = ctx.variables[num] ?: ""
                }
                nxt.startsWith("FS") -> break
            }
        }
        return data
    }
}
