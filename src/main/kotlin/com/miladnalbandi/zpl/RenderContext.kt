package com.miladnalbandi.zpl

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints

/**
 * Mutable rendering state threaded through all [CommandHandler] invocations
 * during a single ZPL label render.
 *
 * State is reset on every `^XA` command via [reset].  Fields that are
 * per-field (e.g. [fieldReverse], [fieldHex], [rot]) are cleared after
 * each `^FS` by their respective handlers.
 *
 * @param dpi Target output resolution in dots-per-inch. Immutable per render.
 */
data class RenderContext(
    /** Current pen X position in dots. */
    var x: Int = 0,
    /** Current pen Y position in dots. */
    var y: Int = 0,
    /**
     * Current field orientation: `N`=0°, `R`=90°, `I`=180°, `B`=270° clockwise.
     * Resets to [defaultRot] after each `^FS`.
     */
    var rot: Char = 'N',
    /** Active font used by the next text flush. */
    var font: Font = Font("SansSerif", Font.PLAIN, 30),
    /** Whether the label is printed in inverted color mode. */
    var invert: Boolean = false,
    /** Reversed printing flag (placeholder, not fully implemented). */
    var reversed: Boolean = false,
    /** Print darkness 0–30 (placeholder). */
    var darkness: Int = 15,
    /** Output DPI — immutable per render session. */
    val dpi: Int,
    /** X offset from `^LH` (Label Home). Added to every `^FO`/`^FT` x. */
    var labelHomeX: Int = 0,
    /** Y offset from `^LH` (Label Home). Added to every `^FO`/`^FT` y. */
    var labelHomeY: Int = 0,
    /**
     * Default font height in dots at 203 dpi (set by `^CF`).
     * Used as fallback when `^A` omits the height parameter.
     */
    var defaultFontHeight: Int = 30,
    /**
     * Default field orientation set by `^FW`.
     * [rot] resets to this value after every `^FS`.
     */
    var defaultRot: Char = 'N',
    /**
     * When `true` the next field is drawn in white (reverse-video).
     * Set by `^FR`/`^FI`, cleared after `^FS` or after a graphic element.
     */
    var fieldReverse: Boolean = false,
    /**
     * When `true` the next `^FD` value decodes `_xx` hex-escape sequences.
     * Set by `^FH`, cleared after `^FS`.
     */
    var fieldHex: Boolean = false,
    /** Narrowest bar width in dots for barcodes (set by `^BY`, param 1). */
    var barcodeModule: Int = 3,
    /** Default barcode height in dots (set by `^BY`, param 3). */
    var barcodeHeight: Int = 100,
    /**
     * Field origin justification from `^FO` 3rd param: 0=left (default), 1=right, 2=center.
     * Cleared after each field is drawn.
     */
    var fieldJustification: Int = 0,
    /**
     * Default font width ratio set by `^CF` 3rd param.
     * Zebra built-in fonts are narrower than SansSerif; 0.65 is a good empirical match.
     */
    var defaultFontWidthRatio: Double = 0.65,
) {
    // ── Variable / format state — persists across ^XA resets ─────────────────
    /** Pre-loaded variable map: field number → value (^FN##^FD…^FS). */
    val variables: MutableMap<Int, String> = mutableMapOf()
    /** Named format store: format name → captured command list (^DF/^XF). */
    val formatStore: MutableMap<String, List<String>> = mutableMapOf()
    /** Non-null while the engine is in ^DF capture mode. */
    var capturingFormat: String? = null
    /** Commands buffered during ^DF format download. */
    val captureBuffer: MutableList<String> = mutableListOf()
    /** Named graphic store: graphic name → decoded image (populated by ~DG, consumed by ^XG). */
    val graphicStore: MutableMap<String, java.awt.image.BufferedImage> = mutableMapOf()

    // ── Per-field variable reference ─────────────────────────────────────────
    /** Set by ^FN##; consumed by the next ^FD (variable definition) or ^FS (variable draw). */
    var pendingFieldNum: Int? = null
    /**
     * Applies initial graphics state (font, colors, rendering hints) to [g].
     * Called once at the start of each render.
     */
    fun applyTo(g: Graphics2D) {
        g.font = font
        g.color = if (invert) Color.WHITE else Color.BLACK
        g.background = if (invert) Color.BLACK else Color.WHITE
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
    }

    /**
     * Resets all mutable state to defaults.  Called on `^XA`.
     *
     * @param keepPosition When `true` the current [x]/[y] position is preserved.
     */
    fun reset(keepPosition: Boolean = false) {
        if (!keepPosition) { x = 0; y = 0 }
        rot             = 'N'
        font            = Font("SansSerif", Font.PLAIN, 30)
        invert          = false
        reversed        = false
        labelHomeX      = 0
        labelHomeY      = 0
        defaultFontHeight = 30
        defaultRot      = 'N'
        fieldReverse    = false
        fieldHex        = false
        barcodeModule        = 3
        barcodeHeight        = 100
        fieldJustification   = 0
        defaultFontWidthRatio = 0.65
        pendingFieldNum      = null
    }
}
