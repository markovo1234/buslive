package com.buslive.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buslive.app.data.db.RecentSearchEntity
import com.buslive.app.data.repo.FavoritesRepository
import com.buslive.app.data.repo.PlaceRepository
import com.buslive.app.data.repo.StaticDataRepository
import com.buslive.app.domain.model.AppError
import com.buslive.app.domain.model.Line
import com.buslive.app.domain.model.Place
import com.buslive.app.domain.model.PlaceKind
import com.buslive.app.domain.model.Stop
import com.buslive.app.domain.model.appRunCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One public line = same operator + MOT line id, with all its directions. */
data class LineGroup(val primary: Line, val directions: List<Line>)

data class SearchState(
    val query: String = "",
    val lines: List<LineGroup> = emptyList(),
    val stops: List<Stop> = emptyList(),
    val places: List<Place> = emptyList(),
    val placesLoading: Boolean = false,
    val placesError: AppError? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val static: StaticDataRepository,
    private val places: PlaceRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    val recents: StateFlow<List<RecentSearchEntity>> =
        favorites.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var hebrew: Boolean = false

    init {
        // Local (Room) search: fast, small debounce.
        viewModelScope.launch {
            query.debounce(200).distinctUntilChanged().collectLatest { q ->
                val lines = static.searchLines(q, 40)
                    .groupBy { it.operatorRef to it.mkt.ifEmpty { it.shortName } }
                    .map { (_, ls) -> LineGroup(ls.sortedBy { it.direction }.first(), ls) }
                    .take(12)
                val stops = static.searchStops(q, 20)
                _state.update { it.copy(lines = lines, stops = stops) }
            }
        }
        // Nominatim: polite (≥ 3 letters, 600 ms debounce, 1 req/s enforced in the repository).
        viewModelScope.launch {
            query.debounce(600).distinctUntilChanged().collectLatest { q ->
                val t = q.trim()
                if (t.length < 3 || t.all { it.isDigit() }) {
                    _state.update { it.copy(places = emptyList(), placesLoading = false, placesError = null) }
                    return@collectLatest
                }
                _state.update { it.copy(placesLoading = true, placesError = null) }
                val r = appRunCatching { places.search(t, hebrew) }
                _state.update {
                    it.copy(
                        places = r.getOrDefault(emptyList()),
                        placesLoading = false,
                        placesError = r.exceptionOrNull() as? AppError,
                    )
                }
            }
        }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        query.value = q
    }

    fun rememberLine(l: Line) = viewModelScope.launch {
        favorites.addRecentSearch("LINE", l.lineRef.toString(), l.shortName, "${l.agencyName}|${l.destination}", null, null)
    }

    fun rememberStop(s: Stop) = viewModelScope.launch {
        favorites.addRecentSearch("STOP", s.code.toString(), s.name, s.city, s.lat, s.lon)
    }

    fun rememberPlace(p: Place) = viewModelScope.launch {
        favorites.addRecentSearch("PLACE", "%.5f,%.5f".format(java.util.Locale.ROOT, p.lat, p.lon), p.name, p.address, p.lat, p.lon)
    }

    fun savePlace(p: Place, kind: PlaceKind) = viewModelScope.launch { favorites.savePlace(p, kind) }

    fun clearRecents() = viewModelScope.launch { favorites.clearRecentSearches() }
}
