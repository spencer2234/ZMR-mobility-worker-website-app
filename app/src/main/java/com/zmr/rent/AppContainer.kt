package com.zmr.rent

import android.content.Context
import com.zmr.rent.data.AppDatabase
import com.zmr.rent.data.repository.AuthRepository
import com.zmr.rent.data.repository.CityRepository
import com.zmr.rent.data.repository.DocumentRepository
import com.zmr.rent.data.repository.PaymentRepository
import com.zmr.rent.data.repository.RenterRepository
import com.zmr.rent.data.repository.ReportRepository
import com.zmr.rent.data.session.SessionManager
import com.zmr.rent.domain.IndiaTime
import com.zmr.rent.domain.Leaders
import java.io.IOException
import java.time.Clock

/**
 * The app's long-lived objects, created once and handed to the screens that need them.
 * The app is small enough that this replaces a dependency-injection library.
 */
class AppContainer(private val context: Context) {
    val clock: Clock = IndiaTime.systemClock()
    val session = SessionManager()

    private val database: AppDatabase by lazy { AppDatabase.create(context) }

    val renterRepository: RenterRepository by lazy {
        RenterRepository(database.renterDao(), session, clock)
    }

    val paymentRepository: PaymentRepository by lazy {
        PaymentRepository(database.paymentDao(), database.renterDao(), database.settingsDao(), session, clock)
    }

    val cityRepository: CityRepository by lazy { CityRepository(database.settingsDao(), database.renterDao()) }

    val documentRepository: DocumentRepository by lazy {
        DocumentRepository(context, database.documentDao(), session, clock)
    }

    val authRepository: AuthRepository by lazy {
        // The upper-management list is left out of git; a build without it has no one to set up as upper management.
        val leaders = try {
            context.assets.open(Leaders.ASSET).bufferedReader().use { Leaders.parse(it.readText()) }
        } catch (e: IOException) {
            emptyList()
        }
        AuthRepository(database.userDao(), database.settingsDao(), session, clock, leaders)
    }

    val reportRepository: ReportRepository by lazy {
        ReportRepository(context, database.renterDao(), database.paymentDao(), database.userDao(), database.documentDao(), session, clock)
    }
}
