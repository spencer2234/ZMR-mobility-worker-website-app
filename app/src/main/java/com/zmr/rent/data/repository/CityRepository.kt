package com.zmr.rent.data.repository

import com.zmr.rent.data.dao.RenterDao
import com.zmr.rent.data.dao.SettingsDao
import com.zmr.rent.data.entity.Setting
import com.zmr.rent.data.entity.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** The cities in the inventory dropdown: Delhi, any city added here, and any city a vehicle is in. */
class CityRepository(private val settingsDao: SettingsDao, private val renterDao: RenterDao) {

    fun observeCities(): Flow<List<String>> =
        combine(settingsDao.observeAll(), renterDao.observeInventory()) { settings, vehicles ->
            (listOf(Vehicle.DEFAULT_CITY) + savedCities(settings) + vehicles.map { it.vehicle.city })
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }

    suspend fun addCity(name: String) {
        val city = name.trim().replace(Regex("\\s+"), " ")
        if (city.isEmpty()) return
        val saved = savedCities(settingsDao.observeAll().first())
        settingsDao.upsertAll(listOf(Setting(KEY, (saved + city).joinToString("\n"))))
    }

    private fun savedCities(settings: List<Setting>) =
        settings.firstOrNull { it.key == KEY }?.value?.lines().orEmpty().filter { it.isNotBlank() }

    private companion object {
        const val KEY = "inventory.cities"
    }
}
