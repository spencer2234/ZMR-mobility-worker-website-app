package com.zmr.rent.domain

import com.zmr.rent.data.entity.RenterWithVehicle

/** Search for the All renters list: one box that matches name, phone or vehicle number. */
object RenterSearch {

    fun filter(renters: List<RenterWithVehicle>, query: String): List<RenterWithVehicle> {
        val text = query.trim()
        if (text.isEmpty()) return renters

        // Compare phone and vehicle numbers in their stored form, so "98765 43" and
        // "dl 1s" match what is in the database. A search containing letters is never a
        // phone number, so "AB12" does not match every phone with "12" in it.
        val digits = if (text.any { it.isLetter() }) "" else text.filter { it in '0'..'9' }
        val vehicle = VehicleNumber.normalize(text)

        return renters.filter { item ->
            item.renter.name.contains(text, ignoreCase = true) ||
                (digits.length >= MIN_NUMBER_QUERY && item.renter.phone.contains(digits)) ||
                (vehicle.length >= MIN_NUMBER_QUERY && item.vehicle.vehicleNumber.contains(vehicle))
        }
    }

    /** Shorter number fragments match almost every renter, so they are ignored. */
    private const val MIN_NUMBER_QUERY = 2
}
