package com.buslive.app

import com.buslive.app.domain.planner.Insight
import com.buslive.app.domain.planner.StopRef
import com.buslive.app.domain.planner.TripInsights
import com.buslive.app.domain.planner.TripLeg
import com.buslive.app.domain.planner.TripOption
import com.buslive.app.domain.planner.TripSort
import com.buslive.app.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TripInsightsTest {
    private val t0 = Instant.parse("2026-11-01T08:00:00Z")
    private val stop = StopRef(1, "s", 32.0, 34.8)
    private fun bus(startMin: Long, endMin: Long, line: String) = TripLeg.Bus(
        lineRef = line.hashCode().toLong(), shortName = line, operatorRef = 5, agencyName = "", headsign = "",
        rideId = 1, rideStart = t0, from = stop, to = stop,
        start = t0.plusSeconds(startMin * 60), end = t0.plusSeconds(endMin * 60), stopsCount = 5,
    )
    private fun walk(startMin: Long, endMin: Long) = TripLeg.Walk(
        GeoPoint(32.0, 34.8), GeoPoint(32.0, 34.8), "", "", 300, t0.plusSeconds(startMin * 60), t0.plusSeconds(endMin * 60),
    )

    private val direct = TripOption("direct", listOf(walk(0, 5), bus(10, 40, "5"), walk(40, 45)), TripOption.Source.LOCAL)
    private val transfer = TripOption("transfer", listOf(walk(0, 2), bus(3, 20, "1"), bus(22, 35, "2"), walk(35, 36)), TripOption.Source.LOCAL)

    @Test
    fun labels() {
        val ins = TripInsights.compute(listOf(direct, transfer))
        assertTrue(Insight.EARLIEST_ARRIVAL in ins.getValue("transfer"))
        assertTrue(Insight.TIGHT_TRANSFER in ins.getValue("transfer"))
        assertTrue(Insight.LEAST_WALKING in ins.getValue("transfer"))
        assertTrue(Insight.DIRECT in ins.getValue("direct"))
        assertTrue(Insight.LAST_DEPARTURE !in ins.getValue("transfer"))
    }

    @Test
    fun sorting() {
        assertEquals("transfer", TripInsights.sort(listOf(direct, transfer), TripSort.BEST).first().id)
        assertEquals("direct", TripInsights.sort(listOf(transfer, direct), TripSort.FEWEST_TRANSFERS).first().id)
    }
}
