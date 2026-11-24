package com.zmr.rent.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Step-by-step upgrades of the on-phone database, so a new app version never loses records.
 * The SQL must match what Room expects, which is in app/schemas/ for each version.
 */
object Migrations {

    /** Version 2: rental start date on renters, and the users table for login. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `renters` ADD COLUMN `startDate` INTEGER NOT NULL DEFAULT 0")
            // Existing renters get the date (in India) on which they were added.
            db.execSQL("UPDATE `renters` SET `startDate` = (`createdAt` + $IST_OFFSET_MILLIS) / $MILLIS_PER_DAY")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `role` TEXT NOT NULL, `pinHash` TEXT NOT NULL, " +
                    "`pinSalt` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_name` ON `users` (`name`)")
        }
    }

    /** Version 3: the payment ledger (with its no-edit, no-delete triggers) and settings. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`renterId` INTEGER NOT NULL, `vehicleId` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                    "`amountPaise` INTEGER NOT NULL, `method` TEXT NOT NULL, `reference` TEXT NOT NULL, " +
                    "`receivedAt` INTEGER NOT NULL, `enteredBy` TEXT NOT NULL, `enteredAt` INTEGER NOT NULL, " +
                    "`correctsPaymentId` INTEGER, `correctionReason` TEXT, " +
                    "FOREIGN KEY(`renterId`) REFERENCES `renters`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_renterId` ON `payments` (`renterId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_vehicleId` ON `payments` (`vehicleId`)")
            Triggers.createPaymentTriggers(db)
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
            )
        }
    }

    /**
     * Version 4: Employee / Manager / CEO accounts with employee ID, contact details and who
     * they report to, and the team list. Old Worker accounts become Employees and the Founder
     * becomes the CEO; each gets a placeholder employee ID so the unique index can be built.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (column in listOf(
                "employeeCode", "phone", "email", "area",
                "reportsToName", "reportsToCode", "reportsToPhone", "reportsToEmail",
            )) {
                db.execSQL("ALTER TABLE `users` ADD COLUMN `$column` TEXT NOT NULL DEFAULT ''")
            }
            db.execSQL("UPDATE `users` SET `role` = 'EMPLOYEE' WHERE `role` = 'WORKER'")
            db.execSQL("UPDATE `users` SET `role` = 'CEO' WHERE `role` = 'FOUNDER'")
            db.execSQL("UPDATE `users` SET `employeeCode` = 'LOCAL-' || `id`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_employeeCode` ON `users` (`employeeCode`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `team_members` (`employeeCode` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`role` TEXT NOT NULL, `phone` TEXT NOT NULL, `email` TEXT NOT NULL, `area` TEXT NOT NULL, " +
                    "`addedByCode` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`employeeCode`))",
            )
        }
    }

    /**
     * Version 5: renters get an optional return date; vehicles get an RC number and may have no
     * renter (free in the inventory). SQLite cannot drop NOT NULL from a column, so the vehicles
     * table is rebuilt. Payments point at vehicles by id, which is kept, so they stay linked.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `renters` ADD COLUMN `endDate` INTEGER")
            // Check the payments -> vehicles link at commit, after the new table has the old name.
            db.execSQL("PRAGMA defer_foreign_keys = ON")
            db.execSQL(
                "CREATE TABLE `vehicles_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`vehicleNumber` TEXT NOT NULL, `renterId` INTEGER, `rcNumber` TEXT NOT NULL DEFAULT '', " +
                    "FOREIGN KEY(`renterId`) REFERENCES `renters`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            db.execSQL("INSERT INTO `vehicles_new` (`id`, `vehicleNumber`, `renterId`) SELECT `id`, `vehicleNumber`, `renterId` FROM `vehicles`")
            db.execSQL("DROP TABLE `vehicles`")
            db.execSQL("ALTER TABLE `vehicles_new` RENAME TO `vehicles`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_vehicleNumber` ON `vehicles` (`vehicleNumber`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_renterId` ON `vehicles` (`renterId`)")
        }
    }

    /** Version 6: renters and lessees, pending amounts, vehicle model and city, documents, weekly submissions. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `renters` ADD COLUMN `customerType` TEXT NOT NULL DEFAULT 'RENTER'")
            db.execSQL("ALTER TABLE `renters` ADD COLUMN `residualValuePaise` INTEGER")
            db.execSQL("ALTER TABLE `renters` ADD COLUMN `pendingPaise` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `renters` ADD COLUMN `pendingChallans` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `model` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `city` TEXT NOT NULL DEFAULT 'Delhi'")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `renter_documents` (`renterId` INTEGER NOT NULL, `slot` TEXT NOT NULL, " +
                    "`type` TEXT, `fileName` TEXT NOT NULL, `storedPath` TEXT NOT NULL, `addedBy` TEXT NOT NULL, " +
                    "`addedAt` INTEGER NOT NULL, PRIMARY KEY(`renterId`, `slot`), " +
                    "FOREIGN KEY(`renterId`) REFERENCES `renters`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `report_submissions` (`employeeCode` TEXT NOT NULL, " +
                    "`weekStart` INTEGER NOT NULL, `submittedAt` INTEGER NOT NULL, PRIMARY KEY(`employeeCode`, `weekStart`))",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

    private const val IST_OFFSET_MILLIS = 19_800_000L // UTC+05:30
    private const val MILLIS_PER_DAY = 86_400_000L
}
