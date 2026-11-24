package com.zmr.rent.domain

/**
 * Money helpers. Every amount in the app is a whole number of paise held in a [Long]
 * (Rs 1,400 is 140000), never a decimal, so totals cannot pick up rounding errors.
 */
object Money {
    const val PAISE_PER_RUPEE = 100L

    /** Largest amount a form accepts: Rs 9,99,999. Guards against a slipped extra digit. */
    const val MAX_AMOUNT_PAISE = 999_999 * PAISE_PER_RUPEE

    fun rupeesToPaise(rupees: Long): Long = Math.multiplyExact(rupees, PAISE_PER_RUPEE)

    /**
     * Reads an amount typed in rupees ("1400", "1,400", "1400.5") and returns paise,
     * or null when the text is not a valid non-negative amount.
     */
    fun parseRupees(input: String): Long? {
        val text = input.trim().removePrefix("₹").replace(",", "").trim()
        val match = AMOUNT.matchEntire(text) ?: return null
        val rupees = match.groupValues[1].toLong()
        val paise = match.groupValues[2].padEnd(2, '0').toLong()
        return rupeesToPaise(rupees) + paise
    }

    /**
     * Formats paise for display with the rupee sign and Indian digit grouping
     * (thousands, then lakhs and crores): 140000 -> "₹1,400", 12345678 -> "₹1,23,456.78".
     * Paise are shown only when they are not zero.
     */
    fun format(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val absolute = Math.abs(paise)
        val rupees = groupIndian(absolute / PAISE_PER_RUPEE)
        return "$sign₹$rupees${fractionText(absolute)}"
    }

    /** Text for an amount input field: plain digits, no sign or grouping ("1400"). */
    fun toInputText(paise: Long): String = "${paise / PAISE_PER_RUPEE}${fractionText(paise)}"

    private fun fractionText(paise: Long): String {
        val fraction = paise % PAISE_PER_RUPEE
        return if (fraction == 0L) "" else "." + fraction.toString().padStart(2, '0')
    }

    private fun groupIndian(rupees: Long): String {
        val digits = rupees.toString()
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$rest,$lastThree"
    }

    // At most 12 rupee digits, so the value always fits in a Long once converted to paise.
    private val AMOUNT = Regex("""(\d{1,12})(?:\.(\d{1,2}))?""")
}
