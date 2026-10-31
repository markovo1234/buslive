package com.buslive.app

import com.buslive.app.data.api.GtfsRideDto
import com.buslive.app.data.api.GtfsRideStopDto
import com.buslive.app.data.api.GtfsRouteDto
import com.buslive.app.data.api.GtfsStopDto
import com.buslive.app.data.api.VehicleLocationDto
import com.buslive.app.domain.util.TimeUtil
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Decodes the real Stride responses saved in docs/api-samples with the app's DTOs and JSON config. */
class ApiSamplesTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    private fun sample(name: String): String {
        val candidates = listOf(File("../docs/api-samples/$name"), File("docs/api-samples/$name"))
        return candidates.first { it.exists() }.readText()
    }

    @Test
    fun vehicleLocations() {
        val rows = json.decodeFromString<List<VehicleLocationDto>>(sample("siri_vehicle_locations.json"))
        assertTrue(rows.isNotEmpty())
        val r = rows.first()
        assertNotNull(r.lat); assertNotNull(r.lon); assertNotNull(r.lineRef); assertNotNull(r.vehicleRef)
        assertNotNull(TimeUtil.parse(r.recordedAtTime))
        assertNotNull(TimeUtil.parse(r.scheduledStartTime))
    }

    @Test
    fun stopsRoutesRides() {
        val stops = json.decodeFromString<List<GtfsStopDto>>(sample("gtfs_stops.json"))
        assertTrue(stops.all { it.code > 0 && it.lat != null })
        val routes = json.decodeFromString<List<GtfsRouteDto>>(sample("gtfs_routes.json"))
        assertTrue(routes.all { it.lineRef > 0 && !it.shortName.isNullOrBlank() })
        val rides = json.decodeFromString<List<GtfsRideDto>>(sample("gtfs_rides.json"))
        assertTrue(rides.all { TimeUtil.parse(it.startTime) != null })
    }

    @Test
    fun rideStops() {
        val byRide = json.decodeFromString<List<GtfsRideStopDto>>(sample("gtfs_ride_stops_by_ride.json"))
        assertEquals(1, byRide.first().stopSequence)
        assertNotNull(byRide.first().stopCode)
        assertNotNull(byRide.first().routeShortName)
        val byStop = json.decodeFromString<List<GtfsRideStopDto>>(sample("gtfs_ride_stops_by_stop_code.json"))
        assertTrue(byStop.all { it.lineRef != null && TimeUtil.parse(it.arrivalTime) != null })
    }
}
