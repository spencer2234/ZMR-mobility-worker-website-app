package com.zmr.rent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleNumberTest {

    @Test
    fun `normalize gives one form for every way of writing a plate`() {
        assertEquals("DL1SAB1234", VehicleNumber.normalize("DL1SAB1234"))
        assertEquals("DL1SAB1234", VehicleNumber.normalize("dl 1s ab 1234"))
        assertEquals("DL1SAB1234", VehicleNumber.normalize(" DL-1S-AB-1234 "))
        assertEquals("22BH1234AA", VehicleNumber.normalize("22 bh 1234 aa"))
    }

    @Test
    fun `accepts plates and fleet numbers`() {
        assertTrue(VehicleNumber.isValid("DL1SAB1234"))
        assertTrue(VehicleNumber.isValid("UP16ER0042"))
        assertTrue(VehicleNumber.isValid("22BH1234AA"))
        assertTrue(VehicleNumber.isValid("ZMR017"))
    }

    @Test
    fun `rejects numbers that are too short, too long or have no digit`() {
        assertFalse(VehicleNumber.isValid(""))
        assertFalse(VehicleNumber.isValid("DL1"))
        assertFalse(VehicleNumber.isValid("ABCDEF"))
        assertFalse(VehicleNumber.isValid("A".repeat(17) + "1"))
    }
}
