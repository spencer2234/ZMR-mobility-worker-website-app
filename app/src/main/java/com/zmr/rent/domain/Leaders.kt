package com.zmr.rent.domain

/**
 * An upper-management person built into the APK. They set up their own phone by picking their
 * name and typing the PIN ZMR gave them, so nobody else can set up as upper management.
 */
data class Leader(val contact: Contact, val pinSalt: String, val pinHash: String) {
    fun pinMatches(pin: String) = Pin.isValid(pin) && Pin.matches(pin, pinSalt.hexToBytes(), pinHash.hexToBytes())

    companion object {
        fun create(contact: Contact, pin: String): Leader {
            val salt = Pin.newSalt()
            return Leader(contact, salt.toHex(), Pin.hash(pin, salt).toHex())
        }
    }
}

/**
 * Reads the list from app/src/main/assets/upper_management.txt. That file holds real phone
 * numbers, so it is not in git; docs/upper-management.md explains how to make it.
 */
object Leaders {
    const val ASSET = "upper_management.txt"

    /** One person per line: employee ID | name | mobile | email (may be empty) | PIN salt | PIN hash. */
    fun parse(text: String): List<Leader> = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val f = line.split("|").map { it.trim() }
            if (f.size != 6) return@mapNotNull null
            val contact = Contact(f[1], EmployeeCode.normalize(f[0]), IndianPhone.normalize(f[2]), Email.normalize(f[3]))
            Leader(contact, f[4], f[5])
        }
}
