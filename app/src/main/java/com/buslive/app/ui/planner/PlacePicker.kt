package com.buslive.app.ui.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Signpost
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buslive.app.R
import com.buslive.app.domain.model.Favorite
import com.buslive.app.domain.model.Place
import com.buslive.app.domain.model.PlaceKind
import com.buslive.app.ui.common.SectionHeader
import com.buslive.app.ui.common.isHebrewUi
import com.buslive.app.ui.search.SearchViewModel

/** Full-screen picker for trip origin / destination: my location, saved places, stops, addresses. */
@Composable
fun PlacePickerDialog(
    title: String,
    allowMyLocation: Boolean,
    saved: List<Favorite>,
    onMyLocation: () -> Unit,
    onPick: (Place) -> Unit,
    onDismiss: () -> Unit,
    vm: SearchViewModel = hiltViewModel(key = "picker-$title"),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    vm.hebrew = isHebrewUi()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::onQuery,
                        label = { Text(title) },
                        placeholder = { Text(stringResource(R.string.picker_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    if (state.query.isBlank()) {
                        if (allowMyLocation) item {
                            PickRow(Icons.Outlined.MyLocation, stringResource(R.string.my_location), null, onMyLocation)
                        }
                        items(saved, key = { "f${it.id}" }) { f ->
                            val icon = when (f.placeKind) {
                                PlaceKind.HOME -> Icons.Outlined.Home
                                PlaceKind.WORK -> Icons.Outlined.Work
                                else -> Icons.Outlined.Place
                            }
                            val name = when (f.placeKind) {
                                PlaceKind.HOME -> stringResource(R.string.save_home)
                                PlaceKind.WORK -> stringResource(R.string.save_work)
                                else -> f.title
                            }
                            PickRow(icon, name, f.subtitle) {
                                if (f.lat != null && f.lon != null) onPick(Place(name, f.subtitle.orEmpty(), f.lat, f.lon))
                            }
                        }
                        return@LazyColumn
                    }
                    if (state.stops.isNotEmpty()) item { SectionHeader(stringResource(R.string.section_stops)) }
                    items(state.stops.take(8), key = { "s${it.code}" }) { s ->
                        PickRow(Icons.Outlined.Signpost, s.name, listOf(s.city, stringResource(R.string.stop_code, s.code)).joinToString(" · ")) {
                            onPick(Place(s.name, s.city, s.lat, s.lon))
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(stringResource(R.string.section_places), Modifier.weight(1f))
                            if (state.placesLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Box(Modifier.width(16.dp))
                        }
                    }
                    items(state.places, key = { "p${it.lat},${it.lon},${it.name}" }) { p ->
                        PickRow(Icons.Outlined.Place, p.name, p.address) { onPick(p) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Box(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(
                subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
