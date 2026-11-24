package com.zmr.rent.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.zmr.rent.data.dao.RenterDao
import com.zmr.rent.data.entity.Renter
import com.zmr.rent.data.entity.RenterWithVehicle
import com.zmr.rent.data.entity.Vehicle
import com.zmr.rent.data.entity.VehicleWithRenter
import com.zmr.rent.data.session.Session
import com.zmr.rent.domain.RenterDetails
import com.zmr.rent.domain.RenterFieldError
import com.zmr.rent.domain.RenterValidator
import com.zmr.rent.domain.VehicleNumber
import kotlinx.coroutines.flow.Flow
import java.time.Clock

/** The outcome of trying to add or edit a renter. */
sealed interface SaveRenterResult {
    data class Saved(val renterId: Long) : SaveRenterResult
    data class Invalid(val errors: Set<RenterFieldError>) : SaveRenterResult
    data object NotFound : SaveRenterResult
}

/**
 * The only way the rest of the app reads or changes renters. Details are cleaned and
 * validated here as well as on the form, so no code path can save a bad record.
 */
class RenterRepository(
    private val renterDao: RenterDao,
    private val session: Session,
    private val clock: Clock,
) {
    fun observeRenters(): Flow<List<RenterWithVehicle>> = renterDao.observeAll()

    suspend fun getRenter(renterId: Long): RenterWithVehicle? = renterDao.findById(renterId)

    fun observeInventory(): Flow<List<VehicleWithRenter>> = renterDao.observeInventory()

    /** Adds a free vehicle to the inventory. Returns false when the number is invalid or already there. */
    suspend fun addVehicle(
        vehicleNumber: String,
        rcNumber: String,
        model: String = "",
        city: String = Vehicle.DEFAULT_CITY,
    ): Boolean {
        val number = VehicleNumber.normalize(vehicleNumber)
        if (!VehicleNumber.isValid(number) || renterDao.findVehicle(number) != null) return false
        renterDao.insertVehicle(Vehicle(0, number, null, VehicleNumber.normalize(rcNumber), model.trim(), city))
        return true
    }

    suspend fun addRenter(details: RenterDetails): SaveRenterResult {
        val clean = RenterValidator.clean(details)
        checkDetails(clean, renterId = null)?.let { return it }

        val renter = Renter(
            name = clean.name,
            phone = clean.phone,
            startDate = clean.startDate,
            endDate = clean.endDate,
            rentDay = clean.startDate.dayOfWeek,
            weeklyRentPaise = requireNotNull(clean.weeklyRentPaise),
            status = clean.status,
            holdReason = clean.holdReason,
            createdBy = session.userName,
            createdAt = clock.millis(),
            customerType = clean.customerType,
            residualValuePaise = clean.residualValuePaise,
            pendingPaise = clean.pendingPaise,
            pendingChallans = clean.pendingChallans,
        )
        return saveGuardingVehicle {
            SaveRenterResult.Saved(renterDao.insertRenterWithVehicle(renter, clean.vehicleNumber, clean.rcNumber, clean.vehicleModel, clean.city))
        }
    }

    suspend fun updateRenter(renterId: Long, details: RenterDetails): SaveRenterResult {
        val existing = renterDao.findById(renterId) ?: return SaveRenterResult.NotFound
        val clean = RenterValidator.clean(details)
        checkDetails(clean, renterId)?.let { return it }

        // copy() keeps createdBy and createdAt: who first added the renter never changes.
        val renter = existing.renter.copy(
            name = clean.name,
            phone = clean.phone,
            startDate = clean.startDate,
            endDate = clean.endDate,
            rentDay = clean.startDate.dayOfWeek,
            weeklyRentPaise = requireNotNull(clean.weeklyRentPaise),
            status = clean.status,
            holdReason = clean.holdReason,
            customerType = clean.customerType,
            residualValuePaise = clean.residualValuePaise,
            pendingPaise = clean.pendingPaise,
            pendingChallans = clean.pendingChallans,
        )
        return saveGuardingVehicle {
            renterDao.updateRenterWithVehicle(renter, clean.vehicleNumber, clean.rcNumber, clean.vehicleModel, clean.city)
            SaveRenterResult.Saved(renterId)
        }
    }

    /** Returns the problem that stops [details] being saved, or null when they are fine. */
    private suspend fun checkDetails(details: RenterDetails, renterId: Long?): SaveRenterResult? {
        val errors = RenterValidator.validate(details)
        if (errors.isNotEmpty()) return SaveRenterResult.Invalid(errors)

        val owner = renterDao.findRenterIdByVehicleNumber(details.vehicleNumber)
        if (owner != null && owner != renterId) return VEHICLE_TAKEN
        return null
    }

    /**
     * The unique indices on the vehicles table are the real guard for "one vehicle, one renter".
     * If a save slips past the check above (two saves at once), the database still refuses it.
     */
    private suspend fun saveGuardingVehicle(save: suspend () -> SaveRenterResult): SaveRenterResult =
        try {
            save()
        } catch (e: SQLiteConstraintException) {
            VEHICLE_TAKEN
        }

    private companion object {
        val VEHICLE_TAKEN = SaveRenterResult.Invalid(setOf(RenterFieldError.VEHICLE_TAKEN))
    }
}
