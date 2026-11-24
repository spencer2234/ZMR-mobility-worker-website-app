package com.zmr.rent.domain

import com.zmr.rent.data.entity.CustomerType
import com.zmr.rent.data.entity.HoldReason
import com.zmr.rent.data.entity.RenterStatus
import com.zmr.rent.data.entity.Vehicle
import java.time.LocalDate

/** Everything a person enters on the add / edit renter form, before it is cleaned and saved. */
data class RenterDetails(
    val name: String,
    val phone: String,
    val vehicleNumber: String,
    /** The day the rental began. Rent is due every week on this date's weekday. */
    val startDate: LocalDate,
    val weeklyRentPaise: Long?,
    val status: RenterStatus = RenterStatus.ACTIVE,
    val holdReason: HoldReason? = null,
    /** Registration certificate (RC) number of the vehicle; optional. */
    val rcNumber: String = "",
    /** The agreed return date; null for an open-ended rental. */
    val endDate: LocalDate? = null,
    val customerType: CustomerType = CustomerType.RENTER,
    /** Purchase price at the end of a lease; lessees only. */
    val residualValuePaise: Long? = null,
    val pendingPaise: Long = 0,
    val pendingChallans: Int = 0,
    val vehicleModel: String = "",
    val city: String = Vehicle.DEFAULT_CITY,
)

/** A problem with one field of the renter form. */
enum class RenterFieldError {
    NAME_REQUIRED,
    PHONE_INVALID,
    VEHICLE_INVALID,

    /** The vehicle number already belongs to a different renter. */
    VEHICLE_TAKEN,
    RENT_INVALID,
    HOLD_REASON_REQUIRED,
    END_BEFORE_START,
}

object RenterValidator {
    const val MAX_NAME_LENGTH = 60

    /** Trims and canonicalises the typed values. Run this before [validate] and before saving. */
    fun clean(details: RenterDetails): RenterDetails = details.copy(
        name = details.name.trim().replace(WHITESPACE, " "),
        phone = IndianPhone.normalize(details.phone),
        vehicleNumber = VehicleNumber.normalize(details.vehicleNumber),
        rcNumber = VehicleNumber.normalize(details.rcNumber),
        vehicleModel = details.vehicleModel.trim().replace(WHITESPACE, " "),
        residualValuePaise = details.residualValuePaise.takeIf { details.customerType == CustomerType.LESSEE },
        // A reason only makes sense while the vehicle is on hold.
        holdReason = details.holdReason.takeIf { details.status == RenterStatus.ON_HOLD },
    )

    /** Checks already-[clean]ed details. An empty set means they can be saved. */
    fun validate(details: RenterDetails): Set<RenterFieldError> = buildSet {
        if (details.name.isBlank() || details.name.length > MAX_NAME_LENGTH) {
            add(RenterFieldError.NAME_REQUIRED)
        }
        if (!IndianPhone.isValid(details.phone)) add(RenterFieldError.PHONE_INVALID)
        if (!VehicleNumber.isValid(details.vehicleNumber)) add(RenterFieldError.VEHICLE_INVALID)
        val rent = details.weeklyRentPaise
        if (rent == null || rent <= 0 || rent > Money.MAX_AMOUNT_PAISE) {
            add(RenterFieldError.RENT_INVALID)
        }
        if (details.status == RenterStatus.ON_HOLD && details.holdReason == null) {
            add(RenterFieldError.HOLD_REASON_REQUIRED)
        }
        if (details.endDate != null && details.endDate < details.startDate) add(RenterFieldError.END_BEFORE_START)
    }

    private val WHITESPACE = Regex("\\s+")
}
