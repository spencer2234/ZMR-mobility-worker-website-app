package com.zmr.rent.domain

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The business runs on Indian Standard Time. Rent days and "today" are always worked out in
 * IST, even if the phone's time zone is set to something else (for example a developer's
 * emulator, or the founder's phone while travelling).
 */
object IndiaTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    fun systemClock(): Clock = Clock.system(ZONE)

    fun today(clock: Clock): LocalDate = LocalDate.now(clock.withZone(ZONE))
}
