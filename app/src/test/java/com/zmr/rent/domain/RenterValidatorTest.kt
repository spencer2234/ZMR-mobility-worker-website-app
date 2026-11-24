package com.zmr.rent.domain

import com.zmr.rent.data.entity.HoldReason
import com.zmr.rent.data.entity.RenterStatus
import com.zmr.rent.renterDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenterValidatorTest {

    private fun errorsFor(details: RenterDetails) =
        RenterValidator.validate(RenterValidator.clean(details))

    @Test
    fun `complete details have no errors`() {
        assertEquals(emptySet<RenterFieldError>(), errorsFor(renterDetails()))
    }

    @Test
    fun `clean tidies what was typed`() {
        val clean = RenterValidator.clean(
            renterDetails(name = "  Ramesh   Kumar ", phone = "+91 98765 43210", vehicleNumber = "dl 1s-ab 1234"),
        )
        assertEquals("Ramesh Kumar", clean.name)
        assertEquals("9876543210", clean.phone)
        assertEquals("DL1SAB1234", clean.vehicleNumber)
    }

    @Test
    fun `names in Indian scripts are accepted`() {
        assertEquals(emptySet<RenterFieldError>(), errorsFor(renterDetails(name = "रमेश कुमार")))
    }

    @Test
    fun `each missing or wrong field is reported`() {
        assertEquals(setOf(RenterFieldError.NAME_REQUIRED), errorsFor(renterDetails(name = "   ")))
        assertEquals(setOf(RenterFieldError.PHONE_INVALID), errorsFor(renterDetails(phone = "98765")))
        assertEquals(setOf(RenterFieldError.PHONE_INVALID), errorsFor(renterDetails(phone = "1234567890")))
        assertEquals(setOf(RenterFieldError.VEHICLE_INVALID), errorsFor(renterDetails(vehicleNumber = "")))
        assertEquals(setOf(RenterFieldError.RENT_INVALID), errorsFor(renterDetails(weeklyRentPaise = null)))
        assertEquals(setOf(RenterFieldError.RENT_INVALID), errorsFor(renterDetails(weeklyRentPaise = 0)))
    }

    @Test
    fun `all problems are reported together`() {
        val errors = errorsFor(renterDetails(name = "", phone = "", vehicleNumber = "", weeklyRentPaise = null))
        assertEquals(
            setOf(
                RenterFieldError.NAME_REQUIRED,
                RenterFieldError.PHONE_INVALID,
                RenterFieldError.VEHICLE_INVALID,
                RenterFieldError.RENT_INVALID,
            ),
            errors,
        )
    }

    @Test
    fun `on hold needs a reason`() {
        val onHold = renterDetails().copy(status = RenterStatus.ON_HOLD)
        assertEquals(setOf(RenterFieldError.HOLD_REASON_REQUIRED), errorsFor(onHold))
        assertEquals(
            emptySet<RenterFieldError>(),
            errorsFor(onHold.copy(holdReason = HoldReason.REPAIR)),
        )
    }

    @Test
    fun `an active renter never keeps a hold reason`() {
        val clean = RenterValidator.clean(renterDetails().copy(holdReason = HoldReason.REPAIR))
        assertNull(clean.holdReason)
    }

    @Test
    fun `the return date cannot be before the start date`() {
        val start = renterDetails().startDate
        assertEquals(setOf(RenterFieldError.END_BEFORE_START), errorsFor(renterDetails().copy(endDate = start.minusDays(1))))
        assertEquals(emptySet<RenterFieldError>(), errorsFor(renterDetails().copy(endDate = start)))
    }
}
