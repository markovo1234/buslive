package com.buslive.app

import com.buslive.app.domain.ArrivalEstimator
import com.buslive.app.domain.model.Arrival
import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.domain.model.LiveBus
import com.buslive.app.domain.model.RouteNames
import com.buslive.app.domain.planner.OtpTripPlanner
import com.buslive.app.domain.util.Geo
import com.buslive.app.domain.util.TimeUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DomainLogicTest {

    @Test
    fun routeNames_parseMotLongName() {
        val name = "ת.מרכזית תל אביב קומה 6/רציפים-תל אביב יפו<->מסוף שכונה ירוקה-כפר סבא-2#"
        assertEquals("מסוף שכונה ירוקה, כפר סבא", RouteNames.destination(name))
        assertEquals("ת.מרכזית תל אביב קומה 6/רציפים, תל אביב יפו", RouteNames.origin(name))
        val dan = "משרד הרישוי/הלוחמים-חולון<->ת. רכבת תל אביב - סבידור/הורדה-תל אביב יפו-20"
        assertEquals("ת. רכבת תל אביב - סבידור/הורדה, תל אביב יפו", RouteNames.destination(dan))
    }

    @Test
    fun time_parsesApiOffsetsAndFormats() {
        val t = TimeUtil.parse("2026-10-31T17:55:37+00:00")
        assertEquals(Instant.parse("2026-10-31T17:55:37Z"), t)
        assertEquals("2026-10-31T17:55:37+00:00", TimeUtil.toApi(t!!))
        // 17:55 UTC = 19:55 in Israel (winter time, UTC+2) on 31 Oct 2026.
        assertEquals("19:55", TimeUtil.formatTime(t))
        assertNull(TimeUtil.parse("garbage"))
    }

    @Test
    fun geo_distanceAndProgress() {
        val telAviv = GeoPoint(32.0853, 34.7818)
        val jerusalem = GeoPoint(31.7683, 35.2137)
        val d = Geo.distanceM(telAviv, jerusalem)
        assertTrue("TA–JLM ~54 km, got $d", d in 50_000.0..58_000.0)
        val line = listOf(GeoPoint(32.0, 34.8), GeoPoint(32.0, 34.81), GeoPoint(32.0, 34.82))
        val mid = Geo.progressAlong(line, GeoPoint(32.0001, 34.815))
        assertEquals(1.5, mid, 0.05)
    }

    @Test
    fun otp_decodesGooglePolyline() {
        val pts = OtpTripPlanner.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].lat, 1e-5)
        assertEquals(-120.2, pts[0].lon, 1e-5)
        assertEquals(43.252, pts[2].lat, 1e-5)
        assertEquals(-126.453, pts[2].lon, 1e-5)
    }

    private val rideStart = Instant.parse("2026-10-31T17:30:00Z")

    private fun arrival(planned: String, shapeDist: Int?) = Arrival(
        lineRef = 21698, shortName = "148", operatorRef = 15, agencyName = "", destination = "",
        rideId = 1, routeId = 1, rideStart = rideStart, planned = Instant.parse(planned),
        stopSequence = 10, shapeDistM = shapeDist,
    )

    private fun bus(distance: Int, at: String, speed: Int = 30) = LiveBus(
        vehicleRef = "v1", lineRef = 21698, operatorRef = 15, lat = 32.1, lon = 34.8, bearing = 0,
        speedKmh = speed, recordedAt = Instant.parse(at), scheduledStart = rideStart,
        distanceFromStartM = distance, siriRideId = 1, line = null,
    )

    @Test
    fun estimator_lateBusGivesLaterEta() {
        // Planned: 6 km in 20 min (5 m/s). Bus at 3 km at 17:50 (10 min late).
        val a = arrival("2026-10-31T17:50:00Z", 6000)
        val now = Instant.parse("2026-10-31T17:50:30Z")
        val est = ArrivalEstimator.estimate(a, bus(3000, "2026-10-31T17:50:00Z"), now)
        assertNotNull(est)
        val minutesAfterPlanned = (est!!.eta.epochSecond - a.planned.epochSecond) / 60.0
        assertTrue("expected ~8–11 min late, got $minutesAfterPlanned", minutesAfterPlanned in 7.0..12.0)
        assertEquals(3000, est.metersAway)
    }

    @Test
    fun estimator_passedBusIsIgnored() {
        val a = arrival("2026-10-31T17:50:00Z", 6000)
        assertNull(ArrivalEstimator.estimate(a, bus(6500, "2026-10-31T17:49:00Z"), Instant.parse("2026-10-31T17:49:30Z")))
    }

    @Test
    fun estimator_attachesOnlyMatchingRide() {
        val a = arrival("2026-10-31T17:50:00Z", 6000)
        val other = bus(3000, "2026-10-31T17:45:00Z").copy(scheduledStart = Instant.parse("2026-10-31T17:00:00Z"))
        val now = Instant.parse("2026-10-31T17:45:10Z")
        assertNull(ArrivalEstimator.attachLive(listOf(a), listOf(other), now).first().live)
        assertNotNull(ArrivalEstimator.attachLive(listOf(a), listOf(bus(3000, "2026-10-31T17:45:00Z")), now).first().live)
    }

    @Test
    fun estimator_neverInThePast() {
        val a = arrival("2026-10-31T17:50:00Z", 6000)
        val now = Instant.parse("2026-10-31T18:30:00Z")
        val est = ArrivalEstimator.estimate(a, bus(5990, "2026-10-31T17:50:00Z"), now)
        assertEquals(now, est!!.eta)
    }
}
