package com.zmr.rent.domain

import com.zmr.rent.renterWithVehicle
import org.junit.Assert.assertEquals
import org.junit.Test

class RenterSearchTest {

    private val ramesh = renterWithVehicle(1, "Ramesh Kumar", "9876543210", "DL1SAB1234")
    private val suresh = renterWithVehicle(2, "Suresh Yadav", "9123456780", "UP16ER0042")
    private val all = listOf(ramesh, suresh)

    private fun search(query: String) = RenterSearch.filter(all, query)

    @Test
    fun `blank search shows everyone`() {
        assertEquals(all, search(""))
        assertEquals(all, search("   "))
    }

    @Test
    fun `matches part of a name in any letter case`() {
        assertEquals(listOf(ramesh), search("ramesh"))
        assertEquals(listOf(suresh), search("YADAV"))
        assertEquals(all, search("esh"))
    }

    @Test
    fun `matches part of a phone number however it is spaced`() {
        assertEquals(listOf(ramesh), search("98765"))
        assertEquals(listOf(ramesh), search("98765 43"))
        assertEquals(listOf(suresh), search("456780"))
    }

    @Test
    fun `matches part of a vehicle number however it is written`() {
        assertEquals(listOf(ramesh), search("dl 1s"))
        assertEquals(listOf(suresh), search("UP-16"))
        assertEquals(listOf(suresh), search("0042"))
    }

    @Test
    fun `digits alone match phone and vehicle numbers, digits with letters only vehicles`() {
        // "1234" is in Ramesh's vehicle number and in Suresh's phone number.
        assertEquals(all, search("1234"))
        assertEquals(listOf(ramesh), search("AB1234"))
    }

    @Test
    fun `no match gives an empty list`() {
        assertEquals(emptyList<Any>(), search("zzz"))
    }
}
