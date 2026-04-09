package com.miladnalbandi.zpl.graphics

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage
import java.util.*

/**
 * Utility class for generating various barcode types.
 * Uses ZXing library for barcode generation.
 */
object BarcodeUtil {

    /**
     * Generates a Code 128 barcode
     *
     * @param data The data to encode
     * @param w The width of the barcode
     * @param h The height of the barcode
     * @return The generated barcode image
     */
    fun code128(data: String, w: Int, h: Int): BufferedImage =
        encode(BarcodeFormat.CODE_128, data, w, h)

    /**
     * Generates a QR code
     *
     * @param data The data to encode
     * @param size The size of the QR code (width and height)
     * @return The generated QR code image
     */
    /**
     * Generates a QR code at exactly [magnification] dots per module.
     * Uses [QRCodeWriter] to get the raw module matrix, then scales each module
     * to [magnification] pixels — avoids hardcoding a QR version.
     */
    fun qr(data: String, magnification: Int,
           errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M): BufferedImage {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            this[EncodeHintType.MARGIN] = 0
            this[EncodeHintType.ERROR_CORRECTION] = errorCorrection
        }
        // Encode at 1×1 so ZXing returns 1-pixel-per-module; scale is 1 when MARGIN=0
        val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 1, 1, hints)
        val size = matrix.width * magnification
        val img = BufferedImage(size, size, BufferedImage.TYPE_BYTE_BINARY)
        val raster = img.raster
        for (y in 0 until size) {
            for (x in 0 until size) {
                raster.setSample(x, y, 0, if (matrix[x / magnification, y / magnification]) 0 else 1)
            }
        }
        return img
    }

    fun code39(data: String, w: Int, h: Int): BufferedImage =
        encode(BarcodeFormat.CODE_39, data, w, h)

    fun interleaved2of5(data: String, w: Int, h: Int): BufferedImage {
        val cleanData = data.filter { it.isDigit() }
        val padded = if (cleanData.length % 2 != 0) "0$cleanData" else cleanData
        return try {
            encode(BarcodeFormat.ITF, padded, w, h)
        } catch (e: Exception) {
            encode(BarcodeFormat.CODE_128, data, w, h)
        }
    }

    fun pdf417(data: String, w: Int, h: Int): BufferedImage =
        try { encode(BarcodeFormat.PDF_417, data, w, h) }
        catch (e: Exception) { encode(BarcodeFormat.CODE_128, data, w, h) }

    /**
     * Generates an EAN-8 barcode
     *
     * @param data The data to encode (must be 7-8 digits)
     * @param w The width of the barcode
     * @param h The height of the barcode
     * @return The generated barcode image
     */
    fun ean8(data: String, w: Int, h: Int): BufferedImage {
        // Ensure data is valid for EAN-8
        val cleanData = data.filter { it.isDigit() }.take(8)
        return try {
            encode(BarcodeFormat.EAN_8, cleanData, w, h)
        } catch (e: Exception) {
            // Fallback to CODE_128 if EAN_8 fails
            encode(BarcodeFormat.CODE_128, cleanData, w, h)
        }
    }

    /**
     * Generates an EAN-13 barcode
     *
     * @param data The data to encode (must be 12-13 digits)
     * @param w The width of the barcode
     * @param h The height of the barcode
     * @return The generated barcode image
     */
    fun ean13(data: String, w: Int, h: Int): BufferedImage {
        // Ensure data is valid for EAN-13
        val cleanData = data.filter { it.isDigit() }.take(13)
        return try {
            encode(BarcodeFormat.EAN_13, cleanData, w, h)
        } catch (e: Exception) {
            // Fallback to CODE_128 if EAN_13 fails
            encode(BarcodeFormat.CODE_128, cleanData, w, h)
        }
    }

    /**
     * Generates a UPC-A barcode
     *
     * @param data The data to encode (must be 11-12 digits)
     * @param w The width of the barcode
     * @param h The height of the barcode
     * @return The generated barcode image
     */
    fun upcA(data: String, w: Int, h: Int): BufferedImage {
        // Ensure data is valid for UPC-A
        val cleanData = data.filter { it.isDigit() }.take(12)
        return try {
            encode(BarcodeFormat.UPC_A, cleanData, w, h)
        } catch (e: Exception) {
            // Fallback to CODE_128 if UPC_A fails
            encode(BarcodeFormat.CODE_128, cleanData, w, h)
        }
    }

    /**
     * Generates a Data Matrix barcode
     *
     * @param data The data to encode
     * @param size The size of the Data Matrix (width and height)
     * @return The generated Data Matrix image
     */
    fun dataMatrix(data: String, size: Int): BufferedImage {
        return try {
            encode(BarcodeFormat.DATA_MATRIX, data, size, size)
        } catch (e: Exception) {
            // Fallback to QR code if Data Matrix fails
            qr(data, size)
        }
    }

    /**
     * Encodes data into a barcode image
     *
     * @param fmt The barcode format
     * @param data The data to encode
     * @param w The width of the barcode
     * @param h The height of the barcode
     * @param customHints Optional custom encoding hints
     * @return The generated barcode image
     */
    private fun encode(
        fmt: BarcodeFormat,
        data: String,
        w: Int,
        h: Int,
        customHints: EnumMap<EncodeHintType, Any>? = null
    ): BufferedImage {
        val hints = customHints ?: EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            this[EncodeHintType.MARGIN] = 0
        }

        val m: BitMatrix = MultiFormatWriter().encode(data, fmt, w, h, hints)

        // Optimized image creation
        val img = BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY)
        val raster = img.raster

        for (y in 0 until h) {
            for (x in 0 until w) {
                // dark module (bar) → index 0 = black; light module (space) → index 1 = white
                raster.setSample(x, y, 0, if (m[x, y]) 0 else 1)
            }
        }

        return img
    }
}
