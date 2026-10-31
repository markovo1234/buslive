package com.buslive.app.ui.search

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Signpost
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buslive.app.R
import com.buslive.app.domain.model.Place
import com.buslive.app.domain.model.PlaceKind
import com.buslive.app.ui.common.LineBadge
import com.buslive.app.ui.common.MessageState
import com.buslive.app.ui.common.SectionHeader
import com.buslive.app.ui.common.errorMessage
import com.buslive.app.ui.common.isHebrewUi
import com.buslive.app.ui.common.operatorName

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onLine: (Long) -> Unit,
    onStop: (Int) -> Unit,
    onPlace: (Place) -> Unit,
    onPlaceOnMap: (Place) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    vm.hebrew = isHebrewUi()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                TextField(
                    value = state.query,
                    onValueChange = vm::onQuery,
                    placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (state.query.isBlank()) {
                if (recents.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(stringResource(R.string.recent_searches), Modifier.weight(1f))
                            TextButton(onClick = { vm.clearRecents() }) { Text(stringResource(R.string.clear)) }
                        }
                    }
                    items(recents, key = { "r-${it.type}-${it.key}" }) { r ->
                        ResultRow(
                            leading = { Icon(Icons.Outlined.History, null) },
                            title = if (r.type == "LINE") stringResource(R.string.cd_line, r.title) else r.title,
                            subtitle = r.subtitle?.split('|')?.lastOrNull(),
                            onClick = {
                                when (r.type) {
                                    "LINE" -> r.key.toLongOrNull()?.let(onLine)
                                    "STOP" -> r.key.toIntOrNull()?.let(onStop)
                                    else -> if (r.lat != null && r.lon != null) onPlace(Place(r.title, r.subtitle.orEmpty(), r.lat, r.lon))
                                }
                            },
                        )
                    }
                } else item {
                    MessageState(Icons.Outlined.Signpost, stringResource(R.string.search_tips), Modifier.padding(top = 32.dp))
                }
                return@LazyColumn
            }

            if (state.lines.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.section_lines)) }
                items(state.lines, key = { "l-${it.primary.lineRef}" }) { g ->
                    val l = g.primary
                    ResultRow(
                        leading = { LineBadge(l.shortName, l.operatorRef) },
                        title = g.directions.map { it.destination }.distinct().take(2).joinToString(" ⇄ "),
                        subtitle = operatorName(l.operatorRef, l.agencyName),
                        onClick = { vm.rememberLine(l); onLine(l.lineRef) },
                    )
                }
            }
            if (state.stops.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.section_stops)) }
                items(state.stops, key = { "s-${it.code}" }) { s ->
                    ResultRow(
                        leading = { Icon(Icons.Outlined.Signpost, null, tint = MaterialTheme.colorScheme.primary) },
                        title = s.name,
                        subtitle = listOf(s.city, stringResource(R.string.stop_code, s.code)).filter { it.isNotBlank() }.joinToString(" · "),
                        onClick = { vm.rememberStop(s); onStop(s.code) },
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.section_places), Modifier.weight(1f))
                    if (state.placesLoading) CircularProgressIndicator(Modifier.size(16.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                }
            }
            state.placesError?.let { e ->
                item { Text(errorMessage(e), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
            }
            items(state.places, key = { "p-${it.lat}-${it.lon}-${it.name}" }) { p ->
                PlaceRow(
                    place = p,
                    onClick = { vm.rememberPlace(p); onPlace(p) },
                    onMap = { vm.rememberPlace(p); onPlaceOnMap(p) },
                    onSave = { kind -> vm.savePlace(p, kind) },
                )
            }
            if (state.lines.isEmpty() && state.stops.isEmpty() && state.places.isEmpty() && !state.placesLoading) {
                item { MessageState(Icons.Outlined.SearchOff, stringResource(R.string.no_results)) }
            }
            item { Text(stringResource(R.string.osm_credit), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun ResultRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterStart) { leading() }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(
                subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun PlaceRow(place: Place, onClick: () -> Unit, onMap: () -> Unit, onSave: (PlaceKind) -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    ResultRow(
        leading = { Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.error) },
        title = place.name,
        subtitle = place.address,
        onClick = onClick,
        trailing = {
            IconButton(onClick = onMap) { Icon(Icons.Outlined.Map, stringResource(R.string.show_on_map)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(
                        PlaceKind.HOME to R.string.save_as_home,
                        PlaceKind.WORK to R.string.save_as_work,
                        PlaceKind.CUSTOM to R.string.save_place,
                    ).forEach { (kind, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                            menu = false
                            onSave(kind)
                            Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }
        },
    )
}
