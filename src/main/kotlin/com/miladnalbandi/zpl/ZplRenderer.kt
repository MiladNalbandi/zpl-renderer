package com.miladnalbandi.zpl

import com.miladnalbandi.zpl.handler.*
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Entry point for ZPL II → [BufferedImage] rendering.
 *
 * ### Quickstart
 * ```kotlin
 * val image = ZplRenderer.render("^XA^FO50,50^ADN,36,20^FDHello World^FS^XZ")
 * ImageIO.write(image, "png", File("label.png"))
 * ```
 *
 * ### Configuration
 * Pass parameters directly to [render]:
 * ```kotlin
 * val image = ZplRenderer.render(
 *     zpl        = myZpl,
 *     dpi        = 300,
 *     widthInch  = 4.0,
 *     heightInch = 6.0,
 *     antialias  = true,
 * )
 * ```
 *
 * Rendered images are cached by default (keyed on ZPL + all parameters).
 * Call [clearCache] to free memory between label batches.
 */
object ZplRenderer {

    private val imageCache  = ConcurrentHashMap<String, BufferedImage>()
    private const val MAX_CACHE = 50
    private var debug = false

    private val engine: ZplEngine = ZplEngine()
        .register(ControlHandler())
        .register(TextHandler())
        .register(RectangleHandler())
        .register(BitmapHandler())
        .register(BarcodeHandler())

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Render [zpl] to a [BufferedImage].
     *
     * @param zpl        ZPL II source code.
     * @param dpi        Output resolution in dots-per-inch (72–600, default 300).
     * @param widthInch  Label width in inches (default 4.0).
     * @param heightInch Label height in inches (default 6.0).
     * @param antialias  Enable antialiased rendering (slightly slower, smoother text).
     * @param useCache   Return cached result when the same parameters are repeated.
     * @return Rendered label as a [BufferedImage].
     */
    @JvmStatic
    @JvmOverloads
    fun render(
        zpl: String,
        dpi: Int = 300,
        widthInch: Double = 4.0,
        heightInch: Double = 6.0,
        antialias: Boolean = false,
        useCache: Boolean = true,
    ): BufferedImage {
        val safeDpi = dpi.coerceIn(72, 600)
        val safeW   = widthInch.coerceIn(0.5, 12.0)
        val safeH   = heightInch.coerceIn(0.5, 24.0)

        val cacheKey = if (useCache) "$zpl|$safeDpi|$safeW|$safeH|$antialias" else null
        cacheKey?.let { imageCache[it] }?.let { return it }

        if (useCache && imageCache.size >= MAX_CACHE)
            imageCache.keys.firstOrNull()?.let { imageCache.remove(it) }

        val w = (safeW * safeDpi).roundToInt()
        val h = (safeH * safeDpi).roundToInt()

        val imgType = if (antialias) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_BYTE_BINARY
        val img = BufferedImage(w, h, imgType)
        val g   = img.createGraphics()

        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, w, h)
            g.color = Color.BLACK

            if (antialias) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY)
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,  RenderingHints.VALUE_STROKE_PURE)
            }

            val ctx  = RenderContext(dpi = safeDpi)
            ctx.applyTo(g)

            val cmds = splitCommands(zpl)
            if (debug) println("[ZplRenderer] Dispatching ${cmds.size} commands")

            engine.dispatch(cmds, g, ctx)

            cacheKey?.let { imageCache[it] = img }
            return img

        } catch (e: Exception) {
            if (debug) { println("[ZplRenderer] Render error: ${e.message}"); e.printStackTrace() }
            g.color = Color.RED
            g.drawString("ZPL render error: ${e.message}", 10, 20)
            return img
        } finally {
            g.dispose()
        }
    }

    /** Flush the in-memory image cache. */
    @JvmStatic fun clearCache() = imageCache.clear()

    /** Toggle verbose debug output (unhandled commands, statistics). */
    @JvmStatic fun setDebugMode(enabled: Boolean) { debug = enabled; engine.setDebug(enabled) }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun splitCommands(zpl: String): List<String> =
        zpl.replace("\r\n", "\n").replace("\r", "\n")
            .replace("\\^", "\u0001")
            .replace("\\~", "\u0002")
            .split('^', '~')
            .map { it.replace("\u0001", "^").replace("\u0002", "~") }
            .filter { it.isNotBlank() }
            .map { it.trim() }
}
