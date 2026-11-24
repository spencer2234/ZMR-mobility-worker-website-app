package com.zmr.rent.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database rules that hold no matter what the app code does: a payment, once saved, can never
 * be edited or deleted. Created for new installs by [CALLBACK] and for upgrades by Migrations.
 */
object Triggers {

    fun createPaymentTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `payments_no_update` BEFORE UPDATE ON `payments` " +
                "BEGIN SELECT RAISE(ABORT, 'Payments cannot be edited'); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `payments_no_delete` BEFORE DELETE ON `payments` " +
                "BEGIN SELECT RAISE(ABORT, 'Payments cannot be deleted'); END",
        )
    }

    val CALLBACK = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = createPaymentTriggers(db)
    }
}
