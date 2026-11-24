package com.zmr.rent.domain

/**
 * Vehicle numbers. Stored in one canonical form (capital letters and digits, no spaces or
 * dashes) so "dl 1s-ab 1234" and "DL1SAB1234" are the same vehicle and cannot be added twice.
 */
object VehicleNumber {
    const val MIN_LENGTH = 4

    /** Long enough for a 17-character chassis number, for vehicles without a number plate. */
    const val MAX_LENGTH = 17

    fun normalize(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(MAX_LENGTH)

    /**
     * Deliberately loose. Many electric rental vehicles carry a fleet or chassis number instead
     * of an RTO plate, so anything of a sensible length that contains a digit is accepted.
     */
    fun isValid(normalized: String): Boolean =
        normalized.length in MIN_LENGTH..MAX_LENGTH && normalized.any { it in '0'..'9' }
}
