package com.zmr.rent.domain

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class QrCodeTest {

    /** Turns the matrix into a picture, the way the screen draws it, and reads it back. */
    private fun scan(matrix: QrMatrix): String {
        val scale = 4
        val border = 4
        val side = (matrix.size + 2 * border) * scale
        val pixels = IntArray(side * side) { index ->
            val x = index % side / scale - border
            val y = index / side / scale - border
            val dark = x in 0 until matrix.size && y in 0 until matrix.size && matrix.isDark(x, y)
            if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap).text
    }

    @Test
    fun `a UPI payment QR code scans back to the same link`() {
        val link = Upi.qrLink(PaymentSettings.ZMR_UPI_ID, PaymentSettings.ZMR_PAYEE_NAME)
        assertEquals(link, scan(QrCode.encode(link)))
    }

    @Test
    fun `the matrix is square with no border`() {
        val matrix = QrCode.encode("upi://pay?pa=zmr@okaxis")
        // The three finder squares put dark modules in the top-left corner.
        assertEquals(true, matrix.isDark(0, 0))
        assertEquals(true, matrix.isDark(matrix.size - 1, 0))
        assertEquals(true, matrix.isDark(0, matrix.size - 1))
    }
}
