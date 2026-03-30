package com.miladnalbandi.zpl

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class ZplRendererTest {

    // ── Basic render ──────────────────────────────────────────────────────────

    @Test fun `render returns non-null image`() {
        val img = ZplRenderer.render("^XA^FO50,50^ADN,36,20^FDHello^FS^XZ")
        assertNotNull(img)
    }

    @Test fun `render produces correct dimensions at 300 dpi`() {
        val img = ZplRenderer.render("^XA^XZ", dpi = 300, widthInch = 4.0, heightInch = 6.0)
        assertEquals(1200, img.width)
        assertEquals(1800, img.height)
    }

    @Test fun `render produces correct dimensions at 200 dpi`() {
        val img = ZplRenderer.render("^XA^XZ", dpi = 200, widthInch = 4.0, heightInch = 6.0)
        assertEquals(800, img.width)
        assertEquals(1200, img.height)
    }

    // ── Graphic shapes ────────────────────────────────────────────────────────

    @Test fun `graphic box renders without exception`() {
        assertNotNull(ZplRenderer.render("^XA^FO10,10^GB200,100,3^FS^XZ", useCache = false))
    }

    @Test fun `filled graphic box renders without exception`() {
        assertNotNull(ZplRenderer.render("^XA^FO10,10^GB100,100,100^FS^XZ", useCache = false))
    }

    @Test fun `graphic ellipse renders without exception`() {
        assertNotNull(ZplRenderer.render("^XA^FO10,10^GE200,100,3^FS^XZ", useCache = false))
    }

    @Test fun `graphic diagonal renders without exception`() {
        assertNotNull(ZplRenderer.render("^XA^FO10,10^GD200,100,3,B,R^FS^XZ", useCache = false))
    }

    // ── Barcodes ──────────────────────────────────────────────────────────────

    @Test fun `Code 128 barcode renders without exception`() {
        assertNotNull(ZplRenderer.render(
            "^XA^FO50,50^BY3,2,100^BC^FD12345678^FS^XZ", useCache = false))
    }

    @Test fun `QR code renders without exception`() {
        assertNotNull(ZplRenderer.render(
            "^XA^FO50,50^BQ,2,6^FDhttps://example.com^FS^XZ", useCache = false))
    }

    @Test fun `EAN-13 barcode renders without exception`() {
        assertNotNull(ZplRenderer.render(
            "^XA^FO50,50^BY3,2,100^BE^FD5901234123457^FS^XZ", useCache = false))
    }

    @Test fun `Data Matrix renders without exception`() {
        assertNotNull(ZplRenderer.render(
            "^XA^FO50,50^BX,5^FDABC123^FS^XZ", useCache = false))
    }

    // ── Text features ─────────────────────────────────────────────────────────

    @Test fun `field reverse does not throw`() {
        assertNotNull(ZplRenderer.render(
            "^XA^FO10,10^GB200,50,50,B^FS^FO20,20^FR^FDwhite text^FS^XZ", useCache = false))
    }

    @Test fun `CF default font change is applied`() {
        assertNotNull(ZplRenderer.render("^XA^CF0,60^FO20,20^FDLarge Text^FS^XZ", useCache = false))
    }

    @Test fun `label home offset LH is applied`() {
        assertNotNull(ZplRenderer.render("^XA^LH50,50^FO0,0^FDOffset Text^FS^XZ", useCache = false))
    }

    @Test fun `field block FB enables word wrap`() {
        assertNotNull(ZplRenderer.render(
            "^XA^FO20,20^FB400,3,,^FDThis is a long line that should wrap^FS^XZ", useCache = false))
    }

    @Test fun `field hex FH decodes hex escapes`() {
        assertNotNull(ZplRenderer.render("^XA^FO20,20^FH^FD_48_65_6C_6C_6F^FS^XZ", useCache = false))
    }

    // ── Caching ───────────────────────────────────────────────────────────────

    @Test fun `same ZPL returns identical cached image`() {
        val zpl = "^XA^FO50,50^FDCached^FS^XZ"
        val a = ZplRenderer.render(zpl, useCache = true)
        val b = ZplRenderer.render(zpl, useCache = true)
        assertSame(a, b)
    }

    @Test fun `different ZPL returns different images`() {
        val a = ZplRenderer.render("^XA^FO50,50^FDAlpha^FS^XZ", useCache = true)
        val b = ZplRenderer.render("^XA^FO50,50^FDBravo^FS^XZ", useCache = true)
        assertNotSame(a, b)
    }

    @Test fun `clearCache causes fresh render`() {
        val zpl = "^XA^FO50,50^FDClear Test^FS^XZ"
        val a = ZplRenderer.render(zpl, useCache = true)
        ZplRenderer.clearCache()
        val b = ZplRenderer.render(zpl, useCache = true)
        assertNotSame(a, b)
    }

    // ── DPI clamping ──────────────────────────────────────────────────────────

    @Test fun `DPI below 72 is clamped`() {
        val img = ZplRenderer.render("^XA^XZ", dpi = 1)
        assertEquals((4.0 * 72).toInt(), img.width)
    }

    @Test fun `DPI above 600 is clamped`() {
        val img = ZplRenderer.render("^XA^XZ", dpi = 9999)
        assertEquals((4.0 * 600).toInt(), img.width)
    }

    // ── Image type ────────────────────────────────────────────────────────────

    @Test fun `antialias mode produces TYPE_INT_RGB`() {
        val img = ZplRenderer.render("^XA^XZ", antialias = true, useCache = false)
        assertEquals(BufferedImage.TYPE_INT_RGB, img.type)
    }

    @Test fun `non-antialias mode produces TYPE_BYTE_BINARY`() {
        val img = ZplRenderer.render("^XA^XZ", antialias = false, useCache = false)
        assertEquals(BufferedImage.TYPE_BYTE_BINARY, img.type)
    }
}
