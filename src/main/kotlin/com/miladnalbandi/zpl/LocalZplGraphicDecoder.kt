package com.miladnalbandi.zpl

import org.apache.commons.codec.binary.Base64
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.InflaterInputStream

/**
 * Decodes ^GFA / ^GFB / :Z64: / :B64: bitmap payloads to BufferedImage.
 *
 * ^GFA uses a nibble-based packed-hex format with run-length encoding:
 *   0-9 / A-F  — literal hex nibble (case-insensitive)
 *   G-Z        — repeat multiplier: G=1, H=2, … Z=20  (small counts)
 *   g-z        — repeat multiplier: g=20, h=40, … z=400 (multiples of 20)
 *   Multiple consecutive multipliers are summed before applying to the next nibble.
 *   ,          — end current row (zero-pad remainder to bytesPerRow, flush)
 *   :          — repeat previous row exactly (discard any partial current row)
 */
object LocalZplGraphicDecoder {

    private val graphicsCache = ConcurrentHashMap<String, BufferedImage>()
    private const val MAX_CACHE_SIZE = 50

    // ^GFA,totalBytes,dataBytes,bytesPerRow,<data>
    private val gfaRegex = Regex(
        """\^GFA,(\d+),(\d+),(\d+),(.+?)(?:\^|$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private val gfbRegex = Regex(
        """\^GFB,(\d+),(\d+),(\d+),(.+?)(?:\^|$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun clearCache() = graphicsCache.clear()

    /**
     * Decodes the first ^GFA or ^GFB graphic found in [zplSnippet].
     * Returns null if no graphic is found or decoding fails.
     */
    fun decodeFirstGraphic(zplSnippet: String, useCache: Boolean = true): BufferedImage? {
        val cacheKey = if (useCache) zplSnippet else null
        if (useCache && cacheKey != null) graphicsCache[cacheKey]?.let { return it }
        if (useCache && graphicsCache.size >= MAX_CACHE_SIZE)
            graphicsCache.keys.firstOrNull()?.let { graphicsCache.remove(it) }

        val img = (gfaRegex.find(zplSnippet) ?: gfbRegex.find(zplSnippet))
            ?.let { matchToImage(it) }
            ?: return null

        if (useCache && cacheKey != null) graphicsCache[cacheKey] = img
        return img
    }

    /** Decodes all ^GFA and ^GFB graphics found in [zplSnippet]. */
    fun decodeAllGraphics(zplSnippet: String): List<BufferedImage> =
        (gfaRegex.findAll(zplSnippet) + gfbRegex.findAll(zplSnippet))
            .mapNotNull { matchToImage(it) }
            .toList()

    // ─── private helpers ──────────────────────────────────────────────────────

    private fun matchToImage(m: MatchResult): BufferedImage? {
        val totalBytes  = m.groupValues[1].toIntOrNull() ?: return null
        val bytesPerRow = m.groupValues[3].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val payload     = m.groupValues[4].trim()
        return decodeGraphicField(totalBytes, bytesPerRow, payload)
    }

    private fun decodeGraphicField(totalBytes: Int, bytesPerRow: Int, payload: String): BufferedImage? {
        val raster: ByteArray = when {
            payload.startsWith(":Z64:") -> decodeZ64(payload)
            payload.startsWith(":B64:") -> decodeB64(payload)
            else                        -> decodeAsciiHex(payload, bytesPerRow)
        } ?: return null

        if (raster.isEmpty()) return null

        val w = bytesPerRow * 8
        val h = raster.size / bytesPerRow
        if (w <= 0 || h <= 0) return null

        return monoRasterToImage(raster, w, h, bytesPerRow)
    }

    // ─── format decoders ──────────────────────────────────────────────────────

    private fun decodeZ64(s: String) = runCatching {
        val b64 = s.substringAfter(":Z64:").substringBeforeLast(':')
        InflaterInputStream(ByteArrayInputStream(Base64.decodeBase64(b64))).readBytes()
    }.getOrNull()

    private fun decodeB64(s: String) = runCatching {
        val b64 = s.substringAfter(":B64:").substringBeforeLast(':')
        Base64.decodeBase64(b64)
    }.getOrNull()

    /**
     * Decodes ZPL nibble-based packed-hex data.
     *
     * ZPL graphic data encodes each byte as two hex nibbles.
     * Compression uses run-length characters (G-Z, g-z) that specify how many
     * times the NEXT hex nibble should be repeated. Multiple consecutive
     * multipliers are summed. Special chars:
     *   ','  end row — zero-pad to [bytesPerRow] and flush
     *   ':'  emit previous row exactly (discard any partial current row)
     */
    private fun decodeAsciiHex(raw: String, bytesPerRow: Int): ByteArray? {
        if (bytesPerRow <= 0) return null
        val nibsPerRow = bytesPerRow * 2

        val out    = ByteArrayOutputStream()
        val rowBuf = ByteArray(bytesPerRow)
        var nibPos     = 0   // 0..nibsPerRow-1: current nibble position in rowBuf
        var multiplier = 0   // accumulated repeat count (0 = no pending multiplier)
        var prevRow: ByteArray? = null

        /** Write nibble [nib] at nibble position [pos] inside rowBuf (no-op if out of range). */
        fun writeNibble(nib: Int, pos: Int) {
            if (pos >= nibsPerRow) return
            val idx = pos / 2
            rowBuf[idx] = if (pos % 2 == 0)
                ((rowBuf[idx].toInt() and 0x0F) or (nib shl 4)).toByte()
            else
                ((rowBuf[idx].toInt() and 0xF0) or nib).toByte()
        }

        /** Zero-pad the rest of rowBuf and flush it to [out]. */
        fun flushRow() {
            for (p in nibPos until nibsPerRow) writeNibble(0, p)
            out.write(rowBuf)
            prevRow = rowBuf.copyOf()
            rowBuf.fill(0)
            nibPos = 0
            multiplier = 0
        }

        /** Emit hex nibble [nib], repeating it [multiplier] times (or once if 0). */
        fun emitNibble(nib: Int) {
            val count = if (multiplier > 0) multiplier else 1
            repeat(count) {
                writeNibble(nib, nibPos)
                nibPos++
            }
            multiplier = 0
        }

        for (ch in raw) {
            when {
                ch.code <= ' '.code -> { /* skip whitespace */ }

                ch in '0'..'9' -> emitNibble(ch - '0')
                ch in 'A'..'F' -> emitNibble(ch - 'A' + 10)
                ch in 'a'..'f' -> emitNibble(ch - 'a' + 10)

                // G-Z: small counts  G=1, H=2, …, Z=20
                ch in 'G'..'Z' -> multiplier += ch - 'G' + 1
                // g-z: multiples of 20  g=20, h=40, …, z=400
                ch in 'g'..'z' -> multiplier += (ch - 'g' + 1) * 20

                // ',': end current row (zero-pad remainder)
                ch == ',' -> flushRow()

                // ':': emit previous row exactly, discard any partial current row
                ch == ':' -> {
                    val pr = prevRow ?: ByteArray(bytesPerRow)
                    out.write(pr)
                    // prevRow stays unchanged (so consecutive ':' repeat same row)
                    rowBuf.fill(0)
                    nibPos = 0
                    multiplier = 0
                }
            }
        }

        // Flush any partial row at end of data stream
        if (nibPos > 0) flushRow()

        return out.toByteArray().takeIf { it.isNotEmpty() }
    }

    // ─── raster → image ───────────────────────────────────────────────────────

    /**
     * Convert a 1-bpp raster (ZPL convention: 1=black) to a [BufferedImage].
     *
     * Java's TYPE_BYTE_BINARY default palette: index 0 = black, index 1 = white.
     * ZPL: bit 1 = black ink, bit 0 = white paper.
     * Therefore each byte must be inverted (XOR 0xFF) before copying.
     */
    private fun monoRasterToImage(data: ByteArray, w: Int, h: Int, stride: Int): BufferedImage? {
        if (w <= 0 || h <= 0 || stride <= 0 || data.isEmpty()) return null
        return runCatching {
            val img = BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY)
            val imgData = (img.raster.dataBuffer as DataBufferByte).data
            val copyLen = minOf(data.size, imgData.size)
            for (i in 0 until copyLen) imgData[i] = (data[i].toInt() xor 0xFF).toByte()
            img
        }.getOrNull()
    }
}
