package com.zmr.rent.data.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zmr.rent.data.entity.Renter
import com.zmr.rent.data.entity.RenterWithVehicle
import com.zmr.rent.data.entity.Vehicle
import com.zmr.rent.data.entity.VehicleWithRenter
import kotlinx.coroutines.flow.Flow

private const val SELECT_RENTER_WITH_VEHICLE = """
    SELECT r.*,
           v.id AS vehicle_id,
           v.vehicleNumber AS vehicle_vehicleNumber,
           v.renterId AS vehicle_renterId,
           v.rcNumber AS vehicle_rcNumber,
           v.model AS vehicle_model,
           v.city AS vehicle_city
    FROM renters r
    INNER JOIN vehicles v ON v.renterId = r.id
"""

/**
 * Renters and their vehicles. There is no delete method on purpose: a renter who leaves is
 * put on hold, so their history is kept.
 */
@Dao
interface RenterDao {

    @Query("$SELECT_RENTER_WITH_VEHICLE ORDER BY r.name COLLATE NOCASE, r.id")
    fun observeAll(): Flow<List<RenterWithVehicle>>

    @Query("$SELECT_RENTER_WITH_VEHICLE ORDER BY r.name COLLATE NOCASE, r.id")
    suspend fun getAll(): List<RenterWithVehicle>

    @Query("$SELECT_RENTER_WITH_VEHICLE WHERE r.id = :renterId")
    suspend fun findById(renterId: Long): RenterWithVehicle?

    /** The renter a vehicle number is linked to, or null when the number is free or unknown. */
    @Query("SELECT renterId FROM vehicles WHERE vehicleNumber = :vehicleNumber")
    suspend fun findRenterIdByVehicleNumber(vehicleNumber: String): Long?

    /** Every vehicle, rented or free, for the Inventory screen. */
    @Query(
        """SELECT v.*, r.name AS renterName FROM vehicles v
           LEFT JOIN renters r ON r.id = v.renterId
           ORDER BY v.vehicleNumber""",
    )
    fun observeInventory(): Flow<List<VehicleWithRenter>>

    @Query("SELECT * FROM vehicles WHERE vehicleNumber = :vehicleNumber")
    suspend fun findVehicle(vehicleNumber: String): Vehicle?

    @Query("SELECT * FROM vehicles WHERE renterId = :renterId")
    suspend fun findVehicleOfRenter(renterId: Long): Vehicle?

    @Insert
    suspend fun insertRenter(renter: Renter): Long

    @Insert
    suspend fun insertVehicle(vehicle: Vehicle): Long

    @Update
    suspend fun updateRenter(renter: Renter): Int

    @Update
    suspend fun updateVehicle(vehicle: Vehicle): Int

    /** Saves a new renter and gives them their vehicle, or does neither. Returns the new renter id. */
    @Transaction
    suspend fun insertRenterWithVehicle(
        renter: Renter,
        vehicleNumber: String,
        rcNumber: String = "",
        model: String = "",
        city: String = Vehicle.DEFAULT_CITY,
    ): Long {
        val renterId = insertRenter(renter)
        assignVehicle(renterId, vehicleNumber, rcNumber, model, city)
        return renterId
    }

    /** Saves changes to a renter and their vehicle together, or neither. */
    @Transaction
    suspend fun updateRenterWithVehicle(
        renter: Renter,
        vehicleNumber: String,
        rcNumber: String = "",
        model: String = "",
        city: String = Vehicle.DEFAULT_CITY,
    ) {
        updateRenter(renter)
        assignVehicle(renter.id, vehicleNumber, rcNumber, model, city)
    }

    /**
     * Links [vehicleNumber] to the renter. A free inventory vehicle with that number is handed
     * over (the renter's previous vehicle goes back to the inventory); an unknown number either
     * corrects the renter's current plate or adds a new vehicle. A vehicle rented to someone
     * else is refused.
     */
    @Transaction
    suspend fun assignVehicle(renterId: Long, vehicleNumber: String, rcNumber: String, model: String, city: String) {
        val current = findVehicleOfRenter(renterId)
        val wanted = findVehicle(vehicleNumber)
        when {
            wanted == null && current == null ->
                insertVehicle(Vehicle(0, vehicleNumber, renterId, rcNumber, model, city))
            wanted == null -> updateVehicle(current!!.copy(vehicleNumber = vehicleNumber, rcNumber = rcNumber, model = model, city = city))
            wanted.renterId != null && wanted.renterId != renterId ->
                throw SQLiteConstraintException("Vehicle $vehicleNumber is rented to someone else")
            else -> {
                if (current != null && current.id != wanted.id) updateVehicle(current.copy(renterId = null))
                updateVehicle(
                    wanted.copy(
                        renterId = renterId,
                        rcNumber = rcNumber.ifEmpty { wanted.rcNumber },
                        model = model.ifEmpty { wanted.model },
                        city = city,
                    ),
                )
            }
        }
    }
}
