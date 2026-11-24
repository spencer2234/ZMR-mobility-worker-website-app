package com.zmr.rent.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One founder-editable setting, stored as text. Keys are listed in domain.PaymentSettings.
 * A key/value table lets new settings be added later without a database migration.
 */
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
)
