package com.buslive.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buslive.app.data.ConnectivityMonitor
import com.buslive.app.data.repo.FavoritesRepository
import com.buslive.app.data.repo.LiveRepository
import com.buslive.app.data.repo.LocationRepository
import com.buslive.app.data.repo.ScheduleRepository
import com.buslive.app.data.repo.StaticDataRepository
import com.buslive.app.domain.ArrivalEstimator
import com.buslive.app.domain.model.AppError
import com.buslive.app.domain.model.Arrival
import com.buslive.app.domain.model.BBox
import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.domain.model.Line
import com.buslive.app.domain.model.LiveBus
import com.buslive.app.domain.model.LiveEstimate
import com.buslive.app.domain.model.Operators
import com.buslive.app.domain.model.Place
import com.buslive.app.domain.model.PlaceKind
import com.buslive.app.domain.model.Ride
import com.buslive.app.domain.model.RideStop
import com.buslive.app.domain.model.Stop
import com.buslive.app.domain.model.appRunCatching
import com.buslive.app.service.GuidanceManager
import com.buslive.app.service.SyncMonitor
import com.buslive.app.service.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject

enum class BusFilter { ALL, FAVORITES, LINE }

data class SelectedLine(
    val line: Line,
    val siblings: List<Line>,
    val stops: List<RideStop>,
    val color: Int,
    val loading: Boolean = false,
)

sealed interface MapSelection {
    data class Bus(val vehicleRef: String) : MapSelection
    data class StopSel(val code: Int) : MapSelection
    data class Pin(val point: GeoPoint) : MapSelection
}

data class BusDetail(
    val vehicleRef: String,
    val ride: Ride? = null,
    val nextStops: List<Pair<RideStop, LiveEstimate?>> = emptyList(),
    val loading: Boolean = true,
    val error: AppError? = null,
)

data class MapUiState(
    val buses: List<LiveBus> = emptyList(),
    val lastUpdated: Instant? = null,
    val newestRecordedAt: Instant? = null,
    val loadingBuses: Boolean = false,
    val busError: AppError? = null,
    val zoomTooLow: Boolean = false,
    val filter: BusFilter = BusFilter.ALL,
    val selectedLine: SelectedLine? = null,
    val stops: List<Stop> = emptyList(),
    val selection: MapSelection? = null,
    val busDetail: BusDetail? = null,
    val followBus: String? = null,
    val userLocation: GeoPoint? = null,
    val initialCenter: GeoPoint? = null,
    val zoom: Double = 15.0,
    /** Freshness of the whole live feed (newest loaded snapshot); markers fade relative to it. */
    val feedTime: Instant? = null,
    /** Last camera, so coming back to the map tab keeps your place instead of jumping. */
    val cameraCenter: GeoPoint? = null,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val live: LiveRepository,
    private val static: StaticDataRepository,
    private val schedule: ScheduleRepository,
    private val favorites: FavoritesRepository,
    private val location: LocationRepository,
    private val syncMonitor: SyncMonitor,
    private val mapCommands: MapCommands,
    private val guidance: GuidanceManager,
    connectivity: ConnectivityMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    val online = connectivity.online
    val syncState = syncMonitor.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncState.IDLE)
    val favoriteLineKeys = favorites.favoriteLineKeys.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val guidanceState = guidance.state
    /** Saved places for one-tap "take me there" chips on the home map. */
    val savedPlaces = favorites.favorites
        .map { list -> list.filter { it.type == com.buslive.app.domain.model.FavoriteType.PLACE && it.lat != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var bbox: BBox? = null
    private var fetchedBox: BBox? = null
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private var stopsJob: Job? = null
    private var detailJob: Job? = null

    sealed interface CameraEvent {
        data class MoveTo(val point: GeoPoint, val zoom: Double?) : CameraEvent
        data class Fit(val points: List<GeoPoint>) : CameraEvent
    }

    private val _camera = Channel<CameraEvent>(Channel.BUFFERED)
    val camera = _camera.receiveAsFlow()

    init {
        viewModelScope.launch {
            mapCommands.commands.collect { c ->
                when (c) {
                    is MapCommand.FocusStop -> static.stop(c.code)?.let { stop ->
                        selectStop(stop.code)
                        _camera.send(CameraEvent.MoveTo(stop.point, 16.5))
                    }
                    is MapCommand.ShowLine -> selectLineByRef(c.lineRef)
                    is MapCommand.FocusPoint -> _camera.send(CameraEvent.MoveTo(c.point, 16.0))
                }
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(initialCenter = location.fallbackCenter()) }
            if (static.needsSync()) syncMonitor.syncNow()
        }
        viewModelScope.launch {
            // Reload stops + lines once the first sync finishes.
            syncMonitor.state.collect { s ->
                if (s == SyncState.IDLE) {
                    bbox?.let { loadStops(it, _state.value.zoom) }
                    forceNext = true
                    refreshRequests.trySend(Unit)
                }
            }
        }
    }

    // ------------------------------------------------------------- live loop

    /** Runs while the map is visible (cancelled when the screen stops): refresh every 15 s or on demand. */
    suspend fun liveLoop() {
        while (true) {
            refreshBuses()
            withTimeoutOrNull(REFRESH_MS) { refreshRequests.receive() }
            delay(300) // coalesce bursts
        }
    }

    fun refreshNow() {
        forceNext = true
        refreshRequests.trySend(Unit)
    }

    private var lastFetchKey: Any? = null
    private var lastSnapshot: Long? = null
    @Volatile private var forceNext = false

    private suspend fun refreshBuses() {
        val s = _state.value
        val box = bbox ?: return
        // Data saver: SIRI only changes about once a minute. If nothing about the query changed and no new
        // snapshot is loaded on the server, skip the (large) positions download.
        val bboxMode = s.filter != BusFilter.LINE && s.zoom >= MIN_BUS_ZOOM
        val covered = !bboxMode || fetchedBox?.let { fb ->
            fb.contains(GeoPoint(box.south, box.west)) && fb.contains(GeoPoint(box.north, box.east))
        } == true
        val key = Triple(s.filter, s.selectedLine?.siblings?.map { it.lineRef }, bboxMode)
        val snapshot = live.latestSnapshotId()
        val force = forceNext
        forceNext = false
        if (!force && covered && key == lastFetchKey && snapshot != null && snapshot == lastSnapshot &&
            s.busError == null && s.lastUpdated != null
        ) {
            _state.update { it.copy(lastUpdated = Instant.now()) }
            return
        }
        _state.update { it.copy(loadingBuses = true) }
        val result = appRunCatching {
            when {
                s.filter == BusFilter.LINE && s.selectedLine != null ->
                    busesForLines(s.selectedLine.siblings.map { it.lineRef }.take(4))
                s.zoom < MIN_BUS_ZOOM && s.filter == BusFilter.FAVORITES ->
                    busesForLines(favoriteLineRefs().take(8))
                s.zoom < MIN_BUS_ZOOM -> null
                else -> {
                    val query = box.expand(0.15)
                    fetchedBox = query
                    val all = live.busesInBounds(query)
                    if (s.filter == BusFilter.FAVORITES) {
                        val keys = favoriteLineKeys.value
                        all.filter { b -> b.line?.let { FavoritesRepository.lineKey(it) in keys } == true }
                    } else all
                }
            }
        }
        result.onSuccess { buses ->
            lastSnapshot = snapshot
            lastFetchKey = key
            _state.update {
                it.copy(
                    buses = buses ?: emptyList(),
                    zoomTooLow = buses == null,
                    lastUpdated = if (buses != null) Instant.now() else it.lastUpdated,
                    newestRecordedAt = buses?.maxOfOrNull { b -> b.recordedAt },
                    feedTime = live.feedTime,
                    loadingBuses = false,
                    busError = null,
                )
            }
            refreshBusDetail()
        }.onFailure { e ->
            _state.update { it.copy(loadingBuses = false, busError = e as? AppError ?: AppError.Unknown(e)) }
        }
    }

    private suspend fun busesForLines(lineRefs: List<Long>): List<LiveBus> = coroutineScope {
        lineRefs.map { ref -> async { live.busesForLine(ref) } }.awaitAll().flatten().distinctBy { it.vehicleRef }
    }

    private suspend fun favoriteLineRefs(): List<Long> {
        val keys = favoriteLineKeys.value
        if (keys.isEmpty()) return emptyList()
        return static.linesByRef().values.filter { FavoritesRepository.lineKey(it) in keys }.map { it.lineRef }
    }

    // ------------------------------------------------------------- camera

    fun onCameraIdle(box: BBox, zoom: Double) {
        bbox = box
        val prevZoom = _state.value.zoom
        _state.update { it.copy(zoom = zoom, cameraCenter = GeoPoint((box.south + box.north) / 2, (box.west + box.east) / 2)) }
        loadStops(box, zoom)
        val fetched = fetchedBox
        val crossedZoom = (prevZoom < MIN_BUS_ZOOM) != (zoom < MIN_BUS_ZOOM)
        val outside = fetched == null || !(fetched.contains(GeoPoint(box.south, box.west)) && fetched.contains(GeoPoint(box.north, box.east)))
        if (_state.value.filter != BusFilter.LINE && (outside || crossedZoom)) refreshRequests.trySend(Unit)
    }

    private fun loadStops(box: BBox, zoom: Double) {
        stopsJob?.cancel()
        if (zoom < BusMapController.MIN_STOP_ZOOM) {
            if (_state.value.stops.isNotEmpty()) _state.update { it.copy(stops = emptyList()) }
            return
        }
        stopsJob = viewModelScope.launch {
            delay(150)
            val stops = static.stopsInBounds(box.expand(0.3), limit = 2500)
            _state.update { it.copy(stops = stops) }
        }
    }

    // ------------------------------------------------------------- filters + line

    fun setFilter(filter: BusFilter) {
        if (filter != BusFilter.LINE) _state.update { it.copy(filter = filter, selectedLine = null) }
        refreshRequests.trySend(Unit)
    }

    fun selectLineByRef(lineRef: Long) {
        viewModelScope.launch { static.line(lineRef)?.let { selectLine(it) } }
    }

    fun selectLine(line: Line) {
        val color = Operators.color(line.operatorRef)
        _state.update {
            it.copy(
                filter = BusFilter.LINE,
                selectedLine = SelectedLine(line, listOf(line), emptyList(), color, loading = true),
                buses = it.buses.filter { b -> b.lineRef == line.lineRef },
            )
        }
        viewModelScope.launch {
            val siblings = runCatching { static.siblings(line) }.getOrDefault(listOf(line))
            _state.update { s -> s.copy(selectedLine = s.selectedLine?.copy(siblings = siblings)) }
            refreshRequests.trySend(Unit)
            val stops = appRunCatching { representativeStops(line) }.getOrDefault(emptyList())
            _state.update { s ->
                if (s.selectedLine?.line?.lineRef != line.lineRef) s
                else s.copy(selectedLine = s.selectedLine.copy(stops = stops, loading = false))
            }
            if (stops.isNotEmpty()) _camera.send(CameraEvent.Fit(stops.map { it.point }))
        }
    }

    /** Stops of the ride closest to now (today's route), used to draw the line on the map. */
    private suspend fun representativeStops(line: Line): List<RideStop> {
        val today = com.buslive.app.domain.util.TimeUtil.today()
        val route = if (line.date == today) line else schedule.lineFor(line.lineRef, today) ?: line
        val rides = schedule.rides(route.routeId)
        val now = Instant.now()
        val ride = rides.firstOrNull { (it.end ?: it.start) > now } ?: rides.lastOrNull() ?: return emptyList()
        return schedule.rideStops(ride.rideId)
    }

    fun clearLine() {
        _state.update { it.copy(filter = BusFilter.ALL, selectedLine = null) }
        refreshRequests.trySend(Unit)
    }

    // ------------------------------------------------------------- selection

    fun selectBus(id: String) {
        _state.update { it.copy(selection = MapSelection.Bus(id), busDetail = BusDetail(id)) }
        detailJob?.cancel()
        detailJob = viewModelScope.launch { loadBusDetail(id, firstLoad = true) }
    }

    private fun refreshBusDetail() {
        val sel = _state.value.selection as? MapSelection.Bus ?: return
        if (detailJob?.isActive == true) return
        detailJob = viewModelScope.launch { loadBusDetail(sel.vehicleRef, firstLoad = false) }
    }

    private suspend fun loadBusDetail(id: String, firstLoad: Boolean) {
        val bus = _state.value.buses.firstOrNull { it.vehicleRef == id } ?: run {
            if (firstLoad) _state.update { it.copy(busDetail = BusDetail(id, loading = false)) }
            return
        }
        val result = appRunCatching {
            val ride = schedule.matchRide(bus)
            val stops = ride?.let { schedule.rideStops(it.rideId) }.orEmpty()
            val busDist = bus.distanceFromStartM ?: 0
            val upcoming = stops.filter { (it.shapeDistM ?: 0) > busDist - 30 }.take(12)
            val now = Instant.now()
            val withEta = upcoming.map { rs ->
                val a = Arrival(
                    lineRef = bus.lineRef, shortName = bus.shortName, operatorRef = bus.operatorRef,
                    agencyName = "", destination = "", rideId = ride?.rideId ?: 0, routeId = ride?.routeId ?: 0,
                    rideStart = ride?.start ?: rs.arrival, planned = rs.arrival, stopSequence = rs.sequence,
                    rideStartKnown = ride != null,
                    shapeDistM = rs.shapeDistM,
                )
                rs to ArrivalEstimator.estimate(a, bus, now)
            }
            BusDetail(id, ride, withEta, loading = false)
        }
        _state.update { s ->
            if ((s.selection as? MapSelection.Bus)?.vehicleRef != id) s
            else s.copy(busDetail = result.getOrElse { e -> BusDetail(id, loading = false, error = e as? AppError) })
        }
    }

    fun selectStop(code: Int) {
        _state.update { it.copy(selection = MapSelection.StopSel(code), busDetail = null) }
    }

    fun dropPin(p: GeoPoint) {
        _state.update { it.copy(selection = MapSelection.Pin(p), busDetail = null) }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = null, busDetail = null, followBus = null) }
    }

    fun toggleFollow(id: String) {
        _state.update { it.copy(followBus = if (it.followBus == id) null else id) }
    }

    fun stopFollowing() {
        if (_state.value.followBus != null) _state.update { it.copy(followBus = null) }
    }

    /** Start "alert me at this stop" for the selected bus. */
    fun startStopAlert(vehicleRef: String, stop: RideStop): Boolean {
        val bus = _state.value.buses.firstOrNull { it.vehicleRef == vehicleRef } ?: return false
        val detail = _state.value.busDetail ?: return false
        val stops = detail.nextStops.map { it.first }
        return guidance.startStopAlert(
            shortName = bus.shortName, operatorRef = bus.operatorRef, lineRef = bus.lineRef,
            headsign = bus.line?.destination.orEmpty(), rideId = detail.ride?.rideId, rideStart = detail.ride?.start,
            stops = stops, destination = stop,
        )
    }

    fun savePlace(place: Place, kind: PlaceKind) {
        viewModelScope.launch { favorites.savePlace(place, kind) }
        clearSelection()
    }

    // ------------------------------------------------------------- location

    suspend fun locationLoop() {
        if (!location.hasPermission()) return
        location.updates(intervalMs = 10_000, highAccuracy = false).collect { u ->
            _state.update { it.copy(userLocation = u.point) }
        }
    }

    suspend fun currentLocation(): GeoPoint? = location.current()?.also { p ->
        _state.update { it.copy(userLocation = p) }
    }

    fun hasLocationPermission() = location.hasPermission()

    suspend fun awaitInitialCenter(): GeoPoint = state.first { it.initialCenter != null }.initialCenter!!

    companion object {
        const val REFRESH_MS = 15_000L
        const val MIN_BUS_ZOOM = 11.0
    }
}
