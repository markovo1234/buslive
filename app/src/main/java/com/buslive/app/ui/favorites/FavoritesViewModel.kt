package com.buslive.app.ui.favorites

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buslive.app.data.ConnectivityMonitor
import com.buslive.app.data.prefs.SettingsRepository
import com.buslive.app.data.repo.FavoritesRepository
import com.buslive.app.data.repo.LiveRepository
import com.buslive.app.data.repo.ScheduleRepository
import com.buslive.app.domain.ArrivalEstimator
import com.buslive.app.domain.model.AppError
import com.buslive.app.domain.model.Favorite
import com.buslive.app.domain.model.FavoriteType
import com.buslive.app.domain.model.appRunCatching
import com.buslive.app.service.widget.ArrivalsWidget
import com.buslive.app.ui.stop.LineAtStop
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class StopBoard(val lines: List<LineAtStop> = emptyList(), val loaded: Boolean = false, val error: AppError? = null)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favorites: FavoritesRepository,
    private val schedule: ScheduleRepository,
    private val live: LiveRepository,
    private val settings: SettingsRepository,
    connectivity: ConnectivityMonitor,
) : ViewModel() {

    val items: StateFlow<List<Favorite>?> =
        favorites.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val widgetStop: StateFlow<Int?> =
        settings.settings.map { it.widgetStopCode }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val online = connectivity.online

    private val _boards = MutableStateFlow<Map<Int, StopBoard>>(emptyMap())
    val boards: StateFlow<Map<Int, StopBoard>> = _boards.asStateFlow()
    private val _lastUpdated = MutableStateFlow<Instant?>(null)
    val lastUpdated: StateFlow<Instant?> = _lastUpdated.asStateFlow()

    /** Live dashboard: refresh favorite stops every 30 s while visible. */
    suspend fun refreshLoop() {
        while (true) {
            refresh()
            delay(30_000)
        }
    }

    fun refreshNow() = viewModelScope.launch { refresh() }

    private suspend fun refresh() {
        val stops = (items.value ?: favorites.favorites.first())
            .filter { it.type == FavoriteType.STOP }
            .mapNotNull { it.key.toIntOrNull() }
            .take(10)
        coroutineScope {
            stops.map { code -> async { loadBoard(code) } }.awaitAll()
        }
        _lastUpdated.value = Instant.now()
    }

    private suspend fun loadBoard(code: Int) {
        val now = Instant.now()
        val r = appRunCatching {
            val arrivals = schedule.arrivals(code, from = now.minusSeconds(60), to = now.plus(Duration.ofMinutes(120)))
            val soon = arrivals.filter { it.planned < now.plus(Duration.ofMinutes(30)) }.map { it.lineRef }.distinct().take(3)
            val buses = soon.map { ref -> runCatching { live.busesForLine(ref) }.getOrDefault(emptyList()) }.flatten()
            ArrivalEstimator.attachLive(arrivals, buses, now)
                .filter { it.best >= now.minusSeconds(30) }
                .groupBy { it.lineRef }
                .map { (ref, list) ->
                    val sorted = list.sortedBy { it.best }
                    val f = sorted.first()
                    LineAtStop(ref, f.shortName, f.operatorRef, f.agencyName, f.destination, sorted.take(3))
                }
                .sortedBy { it.next.first().best }
        }
        _boards.update { m ->
            val prev = m[code]
            m + (code to StopBoard(
                lines = r.getOrNull() ?: prev?.lines.orEmpty(),
                loaded = true,
                error = r.exceptionOrNull() as? AppError,
            ))
        }
    }

    fun remove(f: Favorite) = viewModelScope.launch {
        favorites.remove(f)
        if (f.type == FavoriteType.STOP && widgetStop.value == f.key.toIntOrNull()) settings.setWidgetStop(null)
    }

    fun setWidgetStop(code: Int) = viewModelScope.launch {
        settings.setWidgetStop(code)
        ArrivalsWidget.refreshAll(context)
    }
}
