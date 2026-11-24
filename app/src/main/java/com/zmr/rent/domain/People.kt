package com.zmr.rent.domain

/** How to reach a person in the company: who reports go to, or who an invite is for. */
data class Contact(
    val name: String,
    /** The company's employee ID, for example "EMP-102". */
    val employeeCode: String,
    /** 10-digit Indian mobile number without +91. */
    val phone: String,
    val email: String,
)

/** Company employee IDs: capital letters, digits and dashes only ("emp 102" becomes "EMP102"). */
object EmployeeCode {
    const val MAX_LENGTH = 20

    fun normalize(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '-' }.take(MAX_LENGTH)

    fun isValid(code: String): Boolean = code.length in 1..MAX_LENGTH && code.any { it.isLetterOrDigit() }
}

object Email {
    fun normalize(raw: String): String = raw.trim().lowercase()

    /** A practical check (something@domain.tld), not the full email standard. */
    fun isValid(email: String): Boolean = PATTERN.matches(email)

    private val PATTERN = Regex("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")
}
