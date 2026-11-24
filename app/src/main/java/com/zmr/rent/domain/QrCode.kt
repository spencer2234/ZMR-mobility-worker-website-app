package com.zmr.rent.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A QR code as a square grid of dark and light modules, without the white border. */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    fun isDark(x: Int, y: Int): Boolean = dark[y * size + x]
}

/** Makes QR codes on the phone itself, so showing a payment QR code needs no internet. */
object QrCode {

    fun encode(text: String): QrMatrix {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            // Medium error correction: still scans from a scratched or dim phone screen.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
        )
        // Size 0 asks for the smallest matrix: one entry per QR module.
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = bits.width
        val dark = BooleanArray(size * size) { index -> bits.get(index % size, index / size) }
        return QrMatrix(size, dark)
    }
}
