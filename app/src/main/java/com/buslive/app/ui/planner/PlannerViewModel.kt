package com.buslive.app.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buslive.app.data.db.RecentTripEntity
import com.buslive.app.data.repo.FavoritesRepository
import com.buslive.app.data.repo.LiveRepository
import com.buslive.app.domain.ArrivalEstimator
import com.buslive.app.domain.model.Arrival
import com.buslive.app.domain.model.LiveEstimate
import com.buslive.app.data.repo.LocationRepository
import com.buslive.app.data.repo.ScheduleRepository
import com.buslive.app.domain.model.AppError
import com.buslive.app.domain.model.Favorite
import com.buslive.app.domain.model.FavoriteType
import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.domain.model.Place
import com.buslive.app.domain.model.appRunCatching
import com.buslive.app.domain.planner.LocalTripPlanner
import com.buslive.app.domain.planner.TripLeg
import com.buslive.app.domain.planner.TripOption
import com.buslive.app.domain.planner.TripPlannerProvider
import com.buslive.app.service.GuidanceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Origin is either "my location" (resolved at plan time) or a fixed place. */
sealed interface Endpoint {
    data object MyLocation : Endpoint
    data class Fixed(val place: Place) : Endpoint
}

data class PlannerState(
    val from: Endpoint = Endpoint.MyLocation,
    val to: Place? = null,
    val departAt: Instant? = null,
    val options: List<TripOption> = emptyList(),
    val selectedId: String? = null,
    val loading: Boolean = false,
    val error: AppError? = null,
    val noService: Boolean = false,
    val needsLocation: Boolean = false,
    val usedOtp: Boolean = false,
    val otpFellBack: Boolean = false,
    val searched: Boolean = false,
    /** Option id → live estimate of its first bus at the boarding stop (null value = bus tracked, no ETA). */
    val live: Map<String, LiveEstimate?> = emptyMap(),
) {
    val selected: TripOption? get() = options.firstOrNull { it.id == selectedId }
}

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val provider: TripPlannerProvider,
    private val location: LocationRepository,
    private val favorites: FavoritesRepository,
    private val schedule: ScheduleRepository,
    private val guidance: GuidanceManager,
    private val liveRepo: LiveRepository,
    commands: PlannerCommands,
) : ViewModel() {

    private val _state = MutableStateFlow(PlannerState())
    val state: StateFlow<PlannerState> = _state.asStateFlow()

    val savedPlaces: StateFlow<List<Favorite>> = favorites.favorites
        .map { list -> list.filter { it.type == FavoriteType.PLACE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentTrips: StateFlow<List<RecentTripEntity>> =
        favorites.recentTrips.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val guidanceState = guidance.state

    private var planJob: Job? = null
    private var liveJob: Job? = null
    private var appliedInitial: Place? = null

    init {
        viewModelScope.launch { commands.destinations.collect { setTo(it) } }
    }

    fun applyInitialDestination(p: Place?) {
        if (p == null || p == appliedInitial) return
        appliedInitial = p
        setTo(p)
    }

    fun setFrom(e: Endpoint) {
        _state.update { it.copy(from = e) }
        plan()
    }

    fun setTo(p: Place) {
        _state.update { it.copy(to = p) }
        plan()
    }

    fun swap() {
        val s = _state.value
        val to = s.to ?: return
        val newTo = (s.from as? Endpoint.Fixed)?.place
        _state.update { it.copy(from = Endpoint.Fixed(to), to = newTo, options = emptyList()) }
        if (newTo != null) plan()
    }

    fun setDepartAt(t: Instant?) {
        _state.update { it.copy(departAt = t) }
        plan()
    }

    fun useRecent(r: RecentTripEntity) {
        val from = if (r.fromLat != null && r.fromLon != null) Endpoint.Fixed(Place(r.fromName, "", r.fromLat, r.fromLon)) else Endpoint.MyLocation
        _state.update { it.copy(from = from, to = Place(r.toName, "", r.toLat, r.toLon)) }
        plan()
    }

    fun select(id: String) {
        _state.update { it.copy(selectedId = id) }
        viewModelScope.launch { fillStops(id) }
    }

    fun plan() {
        val s = _state.value
        val to = s.to ?: return
        planJob?.cancel()
        planJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, noService = false, needsLocation = false, searched = true, live = emptyMap()) }
            val fromPoint: GeoPoint = when (val f = s.from) {
                is Endpoint.Fixed -> f.place.point
                Endpoint.MyLocation -> location.current() ?: run {
                    _state.update { it.copy(loading = false, needsLocation = true, options = emptyList()) }
                    return@launch
                }
            }
            val departAt = s.departAt?.takeIf { it > Instant.now() } ?: Instant.now()
            val choice = provider.current()
            var usedOtp = choice.usingOtp
            var fellBack = false
            var result = appRunCatching { choice.planner.plan(fromPoint, to.point, departAt) }
            if (result.isFailure && choice.fallback != null) {
                fellBack = true
                usedOtp = false
                result = appRunCatching { choice.fallback.plan(fromPoint, to.point, departAt) }
            }
            result.onSuccess { options ->
                _state.update {
                    it.copy(
                        options = options, loading = false, usedOtp = usedOtp, otpFellBack = fellBack,
                        selectedId = options.firstOrNull()?.id, noService = options.isEmpty(),
                    )
                }
                options.firstOrNull()?.let { fillStops(it.id) }
                trackLive()
                val fromName = (s.from as? Endpoint.Fixed)?.place?.name.orEmpty()
                favorites.addRecentTrip(fromName, (s.from as? Endpoint.Fixed)?.place, to)
            }.onFailure { e ->
                val cause = (e as? AppError.Unknown)?.cause
                _state.update {
                    it.copy(
                        loading = false, options = emptyList(),
                        noService = cause is LocalTripPlanner.NoServiceException,
                        error = if (cause is LocalTripPlanner.NoServiceException) null else e as? AppError,
                    )
                }
            }
        }
    }

    /** Local planner legs have only first/last stop: load the ride's stops for the map preview. */
    private suspend fun fillStops(id: String) {
        val option = _state.value.options.firstOrNull { it.id == id } ?: return
        if (option.busLegs.all { it.stops.size >= 2 }) return
        val legs = option.legs.map { leg ->
            if (leg !is TripLeg.Bus || leg.stops.size >= 2 || leg.rideId == null) return@map leg
            val all = appRunCatching { schedule.rideStops(leg.rideId) }.getOrDefault(emptyList())
            val a = all.indexOfFirst { it.stopCode == leg.from.code }
            val b = all.indexOfLast { it.stopCode == leg.to.code }
            if (a >= 0 && b > a) {
                val slice = all.subList(a, b + 1)
                leg.copy(stops = slice, path = slice.map { it.point })
            } else leg
        }
        _state.update { s ->
            s.copy(options = s.options.map { if (it.id == id) it.copy(legs = legs) else it })
        }
    }

    /** Is the first bus of each option already driving? (one small call per line, max 5 lines) */
    private fun trackLive() {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            val firsts = _state.value.options.take(6).mapNotNull { o -> o.busLegs.firstOrNull()?.let { o.id to it } }
            val byLine = firsts.mapNotNull { it.second.lineRef }.distinct().take(5)
            val buses = byLine.flatMap { ref -> appRunCatching { liveRepo.busesForLine(ref) }.getOrDefault(emptyList()) }
            val now = Instant.now()
            val result = HashMap<String, LiveEstimate?>()
            for ((id, leg) in firsts) {
                val bus = buses.firstOrNull { it.lineRef == leg.lineRef && it.scheduledStart != null && it.scheduledStart == leg.rideStart }
                    ?: continue
                val opt = _state.value.options.firstOrNull { it.id == id }
                val boarding = (opt?.busLegs?.firstOrNull() ?: leg).stops.firstOrNull()
                result[id] = ArrivalEstimator.estimate(
                    Arrival(
                        lineRef = bus.lineRef, shortName = leg.shortName, operatorRef = leg.operatorRef, agencyName = "",
                        destination = leg.headsign, rideId = leg.rideId ?: 0, routeId = 0,
                        rideStart = leg.rideStart ?: leg.start, planned = leg.start,
                        stopSequence = boarding?.sequence ?: 0, shapeDistM = boarding?.shapeDistM,
                    ),
                    bus, now,
                )
            }
            _state.update { it.copy(live = result) }
        }
    }

    fun start(option: TripOption) = guidance.startTrip(option)

    fun hasLocationPermission() = location.hasPermission()
}
