package com.buslive.app.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buslive.app.data.ConnectivityMonitor
import com.buslive.app.data.repo.LiveRepository
import com.buslive.app.data.repo.LocationRepository
import com.buslive.app.data.repo.ScheduleRepository
import com.buslive.app.data.repo.StaticDataRepository
import com.buslive.app.domain.ArrivalEstimator
import com.buslive.app.domain.model.AppError
import com.buslive.app.domain.model.BBox
import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.domain.model.Stop
import com.buslive.app.domain.model.appRunCatching
import com.buslive.app.domain.util.Geo
import com.buslive.app.ui.stop.LineAtStop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class NearbyStop(
    val stop: Stop,
    val distanceM: Double,
    val walkMinutes: Int,
    val lines: List<LineAtStop> = emptyList(),
    val loaded: Boolean = false,
    val error: AppError? = null,
)

data class NearbyState(
    val hasPermission: Boolean = false,
    val location: GeoPoint? = null,
    val stops: List<NearbyStop> = emptyList(),
    val refreshing: Boolean = false,
    val noLocation: Boolean = false,
    val noCache: Boolean = false,
    val lastUpdated: Instant? = null,
)

@HiltViewModel
class NearbyViewModel @Inject constructor(
    private val location: LocationRepository,
    private val static: StaticDataRepository,
    private val schedule: ScheduleRepository,
    private val live: LiveRepository,
    connectivity: ConnectivityMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(NearbyState(hasPermission = location.hasPermission()))
    val state: StateFlow<NearbyState> = _state.asStateFlow()
    val online = connectivity.online

    fun onPermissionResult() {
        _state.update { it.copy(hasPermission = location.hasPermission()) }
        refresh()
    }

    /** Auto refresh every 30 s while the screen is visible. */
    suspend fun autoRefreshLoop() {
        while (true) {
            refreshNow()
            delay(30_000)
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        if (!location.hasPermission()) {
            _state.update { it.copy(hasPermission = false) }
            return
        }
        _state.update { it.copy(hasPermission = true, refreshing = true) }
        val here = location.current()
        if (here == null) {
            _state.update { it.copy(refreshing = false, noLocation = true) }
            return
        }
        if (!static.hasData()) {
            _state.update { it.copy(refreshing = false, noCache = true, location = here) }
            return
        }
        val nearest = static.nearestStops(here, count = 10, maxRadiusM = 1500.0)
        val previous = _state.value.stops.associateBy { it.stop.code }
        _state.update { s ->
            s.copy(
                location = here, noLocation = false, noCache = false,
                stops = nearest.map { (stop, d) ->
                    previous[stop.code]?.copy(distanceM = d, walkMinutes = Geo.walkMinutes(d))
                        ?: NearbyStop(stop, d, Geo.walkMinutes(d))
                },
            )
        }
        // Live buses around me (one small bbox call) for live estimates.
        val buses = appRunCatching { live.busesInBounds(BBox.around(here, 3_000.0), limit = 800) }.getOrDefault(emptyList())
        coroutineScope {
            nearest.take(8).map { (stop, _) ->
                async {
                    val now = Instant.now()
                    val result = appRunCatching {
                        schedule.arrivals(stop.code, from = now.minusSeconds(60), to = now.plus(Duration.ofMinutes(90)))
                    }
                    val lines = result.getOrNull()?.let { arrivals ->
                        ArrivalEstimator.attachLive(arrivals, buses, now)
                            .filter { it.best >= now.minusSeconds(30) }
                            .groupBy { it.lineRef }
                            .map { (ref, list) ->
                                val sorted = list.sortedBy { it.best }
                                val f = sorted.first()
                                LineAtStop(ref, f.shortName, f.operatorRef, f.agencyName, f.destination, sorted.take(2))
                            }
                            .sortedBy { it.next.first().best }
                    }
                    _state.update { s ->
                        s.copy(stops = s.stops.map {
                            if (it.stop.code != stop.code) it
                            else it.copy(
                                lines = lines ?: it.lines,
                                loaded = true,
                                error = result.exceptionOrNull() as? AppError,
                            )
                        })
                    }
                }
            }.awaitAll()
        }
        _state.update { it.copy(refreshing = false, lastUpdated = Instant.now()) }
    }
}
