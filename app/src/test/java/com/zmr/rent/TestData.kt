package com.zmr.rent

import com.zmr.rent.data.entity.Renter
import com.zmr.rent.data.entity.RenterStatus
import com.zmr.rent.data.entity.RenterWithVehicle
import com.zmr.rent.data.entity.Vehicle
import com.zmr.rent.domain.RenterDetails
import java.time.DayOfWeek
import java.time.LocalDate

// Made-up renters for tests. Never put real renter data in the repository.

fun renterDetails(
    name: String = "Ramesh Kumar",
    phone: String = "9876543210",
    vehicleNumber: String = "DL1SAB1234",
    // A Monday.
    startDate: LocalDate = LocalDate.of(2026, 10, 12),
    weeklyRentPaise: Long? = 140_000,
) = RenterDetails(name, phone, vehicleNumber, startDate, weeklyRentPaise)

fun renter(
    id: Long = 0,
    name: String = "Ramesh Kumar",
    phone: String = "9876543210",
    status: RenterStatus = RenterStatus.ACTIVE,
) = Renter(
    id = id,
    name = name,
    phone = phone,
    startDate = LocalDate.of(2026, 10, 12),
    rentDay = DayOfWeek.MONDAY,
    status = status,
    createdBy = "Test worker",
    createdAt = 0,
)

fun renterWithVehicle(id: Long, name: String, phone: String, vehicleNumber: String) =
    RenterWithVehicle(
        renter = renter(id = id, name = name, phone = phone),
        vehicle = Vehicle(id = id, vehicleNumber = vehicleNumber, renterId = id),
    )
