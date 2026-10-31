package com.buslive.app.ui.stop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buslive.app.data.repo.FavoritesRepository
import com.buslive.app.data.repo.LiveRepository
import com.buslive.app.data.repo.ScheduleRepository
import com.buslive.app.data.repo.StaticDataRepository
import com.buslive.app.domain.ArrivalEstimator
import com.buslive.app.domain.model.AppError
import com.buslive.app.domain.model.Arrival
import com.buslive.app.domain.model.LiveBus
import com.buslive.app.domain.model.Stop
import com.buslive.app.domain.model.appRunCatching
import com.buslive.app.service.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** All departures of one line (direction) at the stop. */
data class LineAtStop(
    val lineRef: Long,
    val shortName: String,
    val operatorRef: Int,
    val agencyName: String,
    val destination: String,
    val next: List<Arrival>,
)

data class StopDetailsState(
    val code: Int? = null,
    val stop: Stop? = null,
    val lines: List<LineAtStop> = emptyList(),
    val loading: Boolean = false,
    val error: AppError? = null,
    val lastUpdated: Instant? = null,
    val isFavorite: Boolean = false,
    val liveCount: Int = 0,
)

@HiltViewModel
class StopDetailsViewModel @Inject constructor(
    private val static: StaticDataRepository,
    private val schedule: ScheduleRepository,
    private val live: LiveRepository,
    private val favorites: FavoritesRepository,
    private val reminders: ReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(StopDetailsState())
    val state: StateFlow<StopDetailsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(favorites.favoriteStopCodes, _state) { codes, s -> s.code != null && s.code in codes }
                .collect { fav -> if (fav != _state.value.isFavorite) _state.update { it.copy(isFavorite = fav) } }
        }
    }

    fun load(code: Int) {
        if (_state.value.code == code) return
        _state.value = StopDetailsState(code = code, loading = true)
        viewModelScope.launch {
            val stop = static.stop(code)
            _state.update { if (it.code == code) it.copy(stop = stop) else it }
        }
    }

    /** Refresh every 20 s while visible. */
    suspend fun refreshLoop() {
        while (true) {
            refresh()
            delay(20_000)
        }
    }

    fun retry() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val code = _state.value.code ?: return
        if (_state.value.lines.isEmpty()) _state.update { it.copy(loading = true) }
        val result = appRunCatching {
            val arrivals = schedule.arrivals(code)
            val now = Instant.now()
            // Live buses only for lines due in the next hour (small per-line queries, ≤ 10 lines).
            val soonLines = arrivals.filter { it.planned < now + Duration.ofMinutes(60) }
                .map { it.lineRef }.distinct().take(10)
            val buses = runCatching { busesFor(soonLines) }.getOrDefault(emptyList())
            ArrivalEstimator.attachLive(arrivals, buses, now) to buses.size
        }
        result.onSuccess { (arrivals, liveCount) ->
            val now = Instant.now()
            val lines = arrivals
                .filter { it.best >= now.minusSeconds(60) }
                .groupBy { it.lineRef }
                .map { (ref, list) ->
                    val sorted = list.sortedBy { it.best }
                    val first = sorted.first()
                    LineAtStop(ref, first.shortName, first.operatorRef, first.agencyName, first.destination, sorted.take(4))
                }
                .sortedBy { it.next.first().best }
            _state.update {
                if (it.code != code) it
                else it.copy(lines = lines, loading = false, error = null, lastUpdated = Instant.now(), liveCount = liveCount)
            }
        }.onFailure { e ->
            _state.update { if (it.code != code) it else it.copy(loading = false, error = e as? AppError ?: AppError.Unknown(e)) }
        }
    }

    private suspend fun busesFor(lineRefs: List<Long>): List<LiveBus> = coroutineScope {
        lineRefs.map { async { runCatching { live.busesForLine(it) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
    }

    fun toggleFavorite() {
        val stop = _state.value.stop ?: return
        viewModelScope.launch { favorites.toggleStop(stop, _state.value.isFavorite) }
    }

    fun remind(arrival: Arrival): Boolean {
        val stop = _state.value.stop ?: return false
        return reminders.schedule(stop, arrival, minutesBefore = 5)
    }
}
