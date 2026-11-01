package com.buslive.app.domain.planner

import java.time.Duration

/** Short labels on trip options ("Earliest arrival", "Tight transfer", …), computed locally. */
enum class Insight { EARLIEST_ARRIVAL, FEWEST_TRANSFERS, LEAST_WALKING, DIRECT, LAST_DEPARTURE, TIGHT_TRANSFER, LONG_WAIT }

/** How the option list is ordered. */
enum class TripSort { BEST, FEWEST_TRANSFERS, LEAST_WALKING, LEAVE_LATEST }

object TripInsights {
    private const val TIGHT_TRANSFER_S = 4 * 60L
    private const val LONG_WAIT_S = 15 * 60L

    fun compute(options: List<TripOption>): Map<String, List<Insight>> {
        val bus = options.filter { it.busLegs.isNotEmpty() }
        if (bus.isEmpty()) return emptyMap()
        val earliest = bus.minOf { it.arrival }
        val minTransfers = bus.minOf { it.transfers }
        val minWalk = bus.minOf { it.walkSeconds }
        val latestDeparture = bus.maxOf { it.departure }
        val multiple = bus.size > 1
        return bus.associate { o ->
            val list = buildList {
                if (o.arrival == earliest) add(Insight.EARLIEST_ARRIVAL)
                if (o.transfers == 0) add(Insight.DIRECT)
                else if (multiple && o.transfers == minTransfers && bus.count { it.transfers == minTransfers } < bus.size) add(Insight.FEWEST_TRANSFERS)
                if (multiple && o.walkSeconds == minWalk && bus.count { it.walkSeconds == minWalk } < bus.size) add(Insight.LEAST_WALKING)
                if (multiple && o.departure == latestDeparture && o.arrival != earliest) add(Insight.LAST_DEPARTURE)
                if (minTransferGap(o)?.let { it < TIGHT_TRANSFER_S } == true) add(Insight.TIGHT_TRANSFER)
                if (o.waitSeconds > LONG_WAIT_S) add(Insight.LONG_WAIT)
            }
            o.id to list
        }
    }

    /** Seconds between getting off one bus and the next bus leaving (includes the walk). */
    fun minTransferGap(o: TripOption): Long? {
        val legs = o.busLegs
        if (legs.size < 2) return null
        return legs.zipWithNext().minOf { (a, b) -> Duration.between(a.end, b.start).seconds }
    }

    fun sort(options: List<TripOption>, sort: TripSort): List<TripOption> = when (sort) {
        TripSort.BEST -> options.sortedWith(compareBy<TripOption> { it.arrival }.thenBy { it.walkSeconds })
        TripSort.FEWEST_TRANSFERS -> options.sortedWith(compareBy<TripOption> { it.transfers }.thenBy { it.arrival })
        TripSort.LEAST_WALKING -> options.sortedWith(compareBy<TripOption> { it.walkSeconds }.thenBy { it.arrival })
        TripSort.LEAVE_LATEST -> options.sortedWith(compareByDescending<TripOption> { it.departure }.thenBy { it.arrival })
    }
}
