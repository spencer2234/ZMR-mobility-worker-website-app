package com.zmr.rent.domain

/**
 * UPI payment requests. A UPI QR code holds a "upi://pay" link; any UPI app (Google Pay,
 * PhonePe, Paytm, BHIM, bank apps) that scans it opens a payment to ZMR.
 */
object Upi {

    /** A UPI ID (VPA) looks like "name@bank": letters, digits, dots, dashes or underscores, then a handle. */
    fun isValidId(upiId: String): Boolean = UPI_ID.matches(upiId)

    /**
     * The link inside ZMR's official QR code: payee only. The renter types the amount in their
     * UPI app, because unsigned requests with a pre-filled amount can be refused for merchants.
     */
    fun qrLink(upiId: String, payeeName: String): String {
        require(isValidId(upiId)) { "Not a UPI ID: $upiId" }
        return "upi://pay?pa=${encode(upiId)}&pn=${encode(payeeName)}"
    }

    /**
     * Percent-encodes a value. Spaces become %20, not "+", which some UPI apps show literally.
     * "@" is left as it is so UPI IDs stay readable to apps that do not decode them.
     */
    private fun encode(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val char = byte.toInt().toChar()
            if (byte >= 0 && (char.isLetterOrDigit() || char in SAFE)) {
                append(char)
            } else {
                append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }

    private const val SAFE = "-._~@"
    private val UPI_ID = Regex("[a-z0-9._-]{2,256}@[a-z][a-z0-9]{1,63}")
}
