package com.zmr.rent.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The three kinds of account. Stored in the database by name; do not rename entries.
 *
 * - EMPLOYEE: adds renters, takes payments, sends their report to their manager.
 * - MANAGER: everything an employee does, adds employees, and sends a combined team report to the CEO.
 * - CEO: adds managers and combines the managers' team reports into a company report.
 */
enum class UserRole {
    EMPLOYEE,
    MANAGER,
    CEO,
}

/**
 * A person who can log in to the app on this phone. The PIN itself is never stored, only a
 * salted hash of it (see domain.Pin). The "reportsTo" fields say where this person's reports go:
 * their manager, or for a manager the CEO. They are empty for the CEO.
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["employeeCode"], unique = true),
    ],
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: UserRole,
    /** PBKDF2 hash of the PIN, as hex. */
    val pinHash: String,
    /** Random salt used for [pinHash], as hex. */
    val pinSalt: String,
    val createdAt: Long,
    /** The company's employee ID, for example "EMP-102". */
    @ColumnInfo(defaultValue = "") val employeeCode: String = "",
    @ColumnInfo(defaultValue = "") val phone: String = "",
    @ColumnInfo(defaultValue = "") val email: String = "",
    /** City or area the person works in. */
    @ColumnInfo(defaultValue = "") val area: String = "",
    @ColumnInfo(defaultValue = "") val reportsToName: String = "",
    @ColumnInfo(defaultValue = "") val reportsToCode: String = "",
    @ColumnInfo(defaultValue = "") val reportsToPhone: String = "",
    @ColumnInfo(defaultValue = "") val reportsToEmail: String = "",
)

/** A user without their PIN hash: everything the login screen needs to list them. */
data class UserSummary(
    val id: Long,
    val name: String,
    val role: UserRole,
    val employeeCode: String,
)

/**
 * Someone this phone's manager or CEO has invited: an employee added by a manager, or a manager
 * added by the CEO. Kept so a combined report can list who has not sent their report.
 */
@Entity(tableName = "team_members")
data class TeamMember(
    @PrimaryKey val employeeCode: String,
    val name: String,
    val role: UserRole,
    val phone: String,
    val email: String,
    val area: String,
    /** Employee ID of the manager or CEO who added them. */
    val addedByCode: String,
    val addedAt: Long,
)
