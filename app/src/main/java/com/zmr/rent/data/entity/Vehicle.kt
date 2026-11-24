package com.zmr.rent.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A ZMR vehicle. A vehicle with no [renterId] is free in the inventory. The two unique indices
 * enforce ZMR's rule in the database itself: a vehicle number exists once, and a renter has at
 * most one vehicle. (SQLite lets many rows have a null renterId, so many vehicles can be free.)
 */
@Entity(
    tableName = "vehicles",
    indices = [
        Index(value = ["vehicleNumber"], unique = true),
        Index(value = ["renterId"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Renter::class,
            parentColumns = ["id"],
            childColumns = ["renterId"],
        ),
    ],
)
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The number plate, in canonical form: capital letters and digits only, see VehicleNumber.normalize. */
    val vehicleNumber: String,
    /** The renter using it now, or null when it is free. */
    val renterId: Long?,
    /** Registration certificate (RC) number, canonical like [vehicleNumber]. Empty if not known. */
    @ColumnInfo(defaultValue = "") val rcNumber: String = "",
    /** Make or model, for example "BGauss" or "C12i". */
    @ColumnInfo(defaultValue = "") val model: String = "",
    @ColumnInfo(defaultValue = DEFAULT_CITY) val city: String = DEFAULT_CITY,
) {
    companion object {
        const val DEFAULT_CITY = "Delhi"
    }
}

/** A renter together with the one vehicle linked to them. */
data class RenterWithVehicle(
    @Embedded val renter: Renter,
    @Embedded(prefix = "vehicle_") val vehicle: Vehicle,
)

/** A vehicle for the inventory list, with the name of whoever is renting it. */
data class VehicleWithRenter(
    @Embedded val vehicle: Vehicle,
    val renterName: String?,
)
