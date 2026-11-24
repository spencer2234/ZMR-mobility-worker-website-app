package com.zmr.rent.data.entity

/** Whether rent is currently being collected from a renter. Stored in the database by name. */
enum class RenterStatus {
    ACTIVE,
    ON_HOLD,
}

/** The two customer segments. Lessees may buy the vehicle at the end of the lease. */
enum class CustomerType {
    RENTER,
    LESSEE,
}

/** Why a renter's vehicle is on hold. Stored in the database by name; do not rename entries. */
enum class HoldReason {
    MISSED_PAYMENTS,
    REPAIR,
    RETURNED,
    OTHER,
}
