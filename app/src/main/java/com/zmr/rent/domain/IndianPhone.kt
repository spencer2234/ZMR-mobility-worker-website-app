package com.zmr.rent.domain

/**
 * Indian mobile numbers. Stored as the 10 digits only ("9876543210"); the +91 country code
 * is added when the number is shown or dialled.
 */
object IndianPhone {
    const val LENGTH = 10
    const val COUNTRY_CODE = "+91"

    /**
     * Cleans typed or pasted text down to the 10-digit number. Accepts the common ways a
     * number is written in India: "98765 43210", "09876543210", "+91 98765-43210", "919876543210".
     * The result may still be invalid; check it with [isValid].
     */
    fun normalize(raw: String): String {
        val digits = raw.filter { it in '0'..'9' }
        val national = when {
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
            else -> digits
        }
        return national.take(LENGTH)
    }

    /** Indian mobile numbers are 10 digits and start with 6, 7, 8 or 9 (numbers starting 0-5 are landlines or not Indian). */
    fun isValid(digits: String): Boolean = MOBILE.matches(digits)

    /** "+91 98765 43210". Numbers that are not 10 digits are returned unchanged. */
    fun format(digits: String): String =
        if (digits.length == LENGTH) "$COUNTRY_CODE ${digits.take(5)} ${digits.drop(5)}" else digits

    private val MOBILE = Regex("[6-9][0-9]{9}")
}
