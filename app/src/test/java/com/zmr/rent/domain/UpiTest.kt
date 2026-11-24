package com.zmr.rent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiTest {

    @Test
    fun `recognises UPI IDs`() {
        assertTrue(Upi.isValidId("zmrmobility@okaxis"))
        assertTrue(Upi.isValidId("9876543210@ybl"))
        assertTrue(Upi.isValidId("zmr.mobility-1@paytm"))
        assertFalse(Upi.isValidId("zmrmobility"))
        assertFalse(Upi.isValidId("@okaxis"))
        assertFalse(Upi.isValidId("zmr mobility@okaxis"))
        assertFalse(Upi.isValidId("zmr@"))
    }

    @Test
    fun `the link is exactly what ZMR's official QR code contains`() {
        assertEquals(
            "upi://pay?pa=zmrmobility@ptaxis&pn=ZMR%20MOBILITY%20PRIVATE%20LIMITED",
            Upi.qrLink(PaymentSettings.ZMR_UPI_ID, PaymentSettings.ZMR_PAYEE_NAME),
        )
    }

    @Test
    fun `encodes non-English names safely`() {
        assertTrue(Upi.qrLink("zmr@okaxis", "ज़ेडएमआर").contains("pn=%E0%A4"))
    }

    @Test
    fun `refuses a bad UPI ID`() {
        assertThrows(IllegalArgumentException::class.java) { Upi.qrLink("not-an-id", "ZMR") }
    }
}
