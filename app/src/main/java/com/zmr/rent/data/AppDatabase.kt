package com.zmr.rent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zmr.rent.data.dao.DocumentDao
import com.zmr.rent.data.dao.PaymentDao
import com.zmr.rent.data.dao.RenterDao
import com.zmr.rent.data.dao.SettingsDao
import com.zmr.rent.data.dao.UserDao
import com.zmr.rent.data.entity.Payment
import com.zmr.rent.data.entity.Renter
import com.zmr.rent.data.entity.RenterDocument
import com.zmr.rent.data.entity.ReportSubmission
import com.zmr.rent.data.entity.Setting
import com.zmr.rent.data.entity.TeamMember
import com.zmr.rent.data.entity.User
import com.zmr.rent.data.entity.Vehicle

/**
 * The on-phone database, and the app's single source of truth.
 *
 * When a table or column changes, raise [version] and add a Migration. Never use
 * fallbackToDestructiveMigration: this database holds the workshop's rent records.
 */
@Database(
    entities = [
        Renter::class, Vehicle::class, User::class, Payment::class, Setting::class, TeamMember::class,
        RenterDocument::class, ReportSubmission::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun renterDao(): RenterDao

    abstract fun userDao(): UserDao

    abstract fun paymentDao(): PaymentDao

    abstract fun settingsDao(): SettingsDao

    abstract fun documentDao(): DocumentDao

    companion object {
        private const val FILE_NAME = "zmr-rent.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .withAppSetup()
                .build()
    }
}

/** Migrations and payment triggers. Every database the app opens, including in tests, uses this. */
fun RoomDatabase.Builder<AppDatabase>.withAppSetup(): RoomDatabase.Builder<AppDatabase> =
    addMigrations(*Migrations.ALL).addCallback(Triggers.CALLBACK)
