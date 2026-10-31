package com.buslive.app.ui.line

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buslive.app.data.repo.FavoritesRepository
import com.buslive.app.data.repo.LiveRepository
import com.buslive.app.data.repo.ScheduleRepository
import com.buslive.app.data.repo.StaticDataRepository
import com.buslive.app.domain.model.AppError
import com.buslive.app.domain.model.Line
import com.buslive.app.domain.model.LiveBus
import com.buslive.app.domain.model.Ride
import com.buslive.app.domain.model.RideStop
import com.buslive.app.domain.model.appRunCatching
import com.buslive.app.domain.util.Geo
import com.buslive.app.domain.util.TimeUtil
import com.buslive.app.service.GuidanceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** A live bus placed on the stop list: it is between stop [afterIndex] and the next one. */
data class BusOnLine(val bus: LiveBus, val afterIndex: Int)

data class LineState(
    val line: Line? = null,
    val directions: List<Line> = emptyList(),
    val selected: Line? = null,
    val date: LocalDate = TimeUtil.today(),
    val sourceDate: LocalDate = TimeUtil.today(),
    val rides: List<Ride> = emptyList(),
    val selectedRide: Ride? = null,
    val stops: List<RideStop> = emptyList(),
    val liveBuses: List<BusOnLine> = emptyList(),
    val liveUpdated: Instant? = null,
    val loading: Boolean = true,
    val stopsLoading: Boolean = false,
    val error: AppError? = null,
    val isFavorite: Boolean = false,
    val notFound: Boolean = false,
)

@HiltViewModel
class LineViewModel @Inject constructor(
    private val static: StaticDataRepository,
    private val schedule: ScheduleRepository,
    private val live: LiveRepository,
    private val favorites: FavoritesRepository,
    private val guidance: GuidanceManager,
) : ViewModel() {

    private val _state = MutableStateFlow(LineState())
    val state: StateFlow<LineState> = _state.asStateFlow()
    private var loadedRef: Long? = null
    private var ridesJob: Job? = null

    init {
        viewModelScope.launch {
            favorites.favoriteLineKeys.collect { keys ->
                _state.update { s -> s.copy(isFavorite = s.line?.let { FavoritesRepository.lineKey(it) in keys } == true) }
            }
        }
    }

    fun load(lineRef: Long) {
        if (loadedRef == lineRef) return
        loadedRef = lineRef
        viewModelScope.launch {
            val line = static.line(lineRef) ?: appRunCatching { static.lineForDate(lineRef, TimeUtil.today()) }.getOrNull()
            if (line == null) {
                _state.update { it.copy(loading = false, notFound = true) }
                return@launch
            }
            val dirs = runCatching { static.siblings(line) }.getOrDefault(listOf(line))
            _state.update { it.copy(line = line, directions = dirs, selected = line) }
            loadRides()
        }
    }

    fun selectDirection(l: Line) {
        if (_state.value.selected?.lineRef == l.lineRef) return
        _state.update { it.copy(selected = l, selectedRide = null, stops = emptyList(), liveBuses = emptyList()) }
        loadRides()
    }

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(date = date, selectedRide = null) }
        loadRides()
    }

    fun retry() = loadRides()

    private fun loadRides() {
        val sel = _state.value.selected ?: return
        val date = _state.value.date
        ridesJob?.cancel()
        ridesJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val r = appRunCatching { schedule.departures(sel.lineRef, date) }
            r.onSuccess { dep ->
                val now = Instant.now()
                val ride = dep.rides.firstOrNull { (it.end ?: it.start) > now } ?: dep.rides.firstOrNull()
                _state.update {
                    it.copy(rides = dep.rides, sourceDate = dep.sourceDate, loading = false, selectedRide = ride)
                }
                ride?.let { loadStops(it) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e as? AppError ?: AppError.Unknown(e)) }
            }
        }
    }

    fun selectRide(ride: Ride) {
        _state.update { it.copy(selectedRide = ride) }
        viewModelScope.launch { loadStops(ride) }
    }

    private suspend fun loadStops(ride: Ride) {
        _state.update { it.copy(stopsLoading = true) }
        val r = appRunCatching { schedule.rideStops(ride.rideId) }
        _state.update { s ->
            if (s.selectedRide?.rideId != ride.rideId) s
            else s.copy(stops = r.getOrDefault(s.stops), stopsLoading = false, error = r.exceptionOrNull() as? AppError ?: s.error)
        }
        placeBuses()
    }

    /** Live buses on the selected direction, refreshed every 20 s while visible. */
    suspend fun liveLoop() {
        while (true) {
            val sel = _state.value.selected
            if (sel != null && _state.value.date == TimeUtil.today()) {
                appRunCatching { live.busesForLine(sel.lineRef) }.onSuccess { buses ->
                    lastBuses = buses
                    _state.update { it.copy(liveUpdated = Instant.now()) }
                    placeBuses()
                }
            }
            delay(20_000)
        }
    }

    private var lastBuses: List<LiveBus> = emptyList()

    private fun placeBuses() {
        val s = _state.value
        val stops = s.stops
        if (stops.isEmpty()) return
        val placed = lastBuses.filter { it.lineRef == s.selected?.lineRef }.map { bus ->
            val dist = bus.distanceFromStartM
            val idx = if (dist != null && stops.any { it.shapeDistM != null }) {
                stops.indexOfLast { (it.shapeDistM ?: 0) <= dist }.coerceAtLeast(0)
            } else {
                Geo.progressAlong(stops.map { it.point }, bus.point).toInt()
            }
            BusOnLine(bus, idx)
        }
        _state.update { it.copy(liveBuses = placed) }
    }

    fun toggleFavorite() {
        val line = _state.value.line ?: return
        viewModelScope.launch { favorites.toggleLine(line, _state.value.isFavorite) }
    }

    /** "Alert me at stop X" on this line (no full trip plan needed). */
    fun alertAt(stop: RideStop): Boolean {
        val s = _state.value
        val line = s.selected ?: return false
        return guidance.startStopAlert(
            shortName = line.shortName, operatorRef = line.operatorRef, lineRef = line.lineRef,
            headsign = line.destination, rideId = s.selectedRide?.rideId, rideStart = s.selectedRide?.start,
            stops = s.stops, destination = stop,
        )
    }
}
