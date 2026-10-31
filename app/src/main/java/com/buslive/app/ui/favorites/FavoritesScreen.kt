package com.buslive.app.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.buslive.app.R
import com.buslive.app.domain.model.Favorite
import com.buslive.app.domain.model.FavoriteType
import com.buslive.app.domain.model.Place
import com.buslive.app.domain.model.PlaceKind
import com.buslive.app.ui.common.LineBadge
import com.buslive.app.ui.common.LiveTag
import com.buslive.app.ui.common.LoadingState
import com.buslive.app.ui.common.MessageState
import com.buslive.app.ui.common.OfflineBanner
import com.buslive.app.ui.common.SectionHeader
import com.buslive.app.ui.common.agoText
import com.buslive.app.ui.common.errorMessage
import com.buslive.app.ui.common.minutesText
import com.buslive.app.ui.common.rememberNow
import com.buslive.app.ui.theme.liveColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FavoritesScreen(
    onStop: (Int) -> Unit,
    onLine: (Long) -> Unit,
    onPlace: (Place) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    vm: FavoritesViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val boards by vm.boards.collectAsStateWithLifecycle()
    val widgetStop by vm.widgetStop.collectAsStateWithLifecycle()
    val updated by vm.lastUpdated.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val now by rememberNow()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val stopCount = items?.count { it.type == FavoriteType.STOP } ?: 0
    LaunchedEffect(lifecycle, stopCount) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.refreshLoop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.tab_favorites))
                        updated?.let {
                            Text(
                                stringResource(R.string.updated_ago, agoText(it, now)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                },
            )
        },
    ) { padding ->
        val list = items
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (!online) item { OfflineBanner() }
            if (list == null) {
                item { LoadingState() }
                return@LazyColumn
            }
            val places = list.filter { it.type == FavoriteType.PLACE }
            val stops = list.filter { it.type == FavoriteType.STOP }
            val lines = list.filter { it.type == FavoriteType.LINE }

            // ---- Places
            item { SectionHeader(stringResource(R.string.section_places)) }
            item {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (places.none { it.placeKind == PlaceKind.HOME }) AssistChip(
                        onClick = onSearch, label = { Text(stringResource(R.string.set_home)) },
                        leadingIcon = { Icon(Icons.Outlined.Home, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                    if (places.none { it.placeKind == PlaceKind.WORK }) AssistChip(
                        onClick = onSearch, label = { Text(stringResource(R.string.set_work)) },
                        leadingIcon = { Icon(Icons.Outlined.Work, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                }
            }
            items(places, key = { "p-${it.id}" }) { f ->
                FavRow(
                    icon = {
                        Icon(
                            when (f.placeKind) {
                                PlaceKind.HOME -> Icons.Outlined.Home
                                PlaceKind.WORK -> Icons.Outlined.Work
                                else -> Icons.Outlined.Place
                            },
                            null, tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    title = when (f.placeKind) {
                        PlaceKind.HOME -> stringResource(R.string.save_home)
                        PlaceKind.WORK -> stringResource(R.string.save_work)
                        else -> f.title
                    },
                    subtitle = if (f.placeKind == PlaceKind.CUSTOM) f.subtitle else f.title,
                    onClick = { if (f.lat != null && f.lon != null) onPlace(Place(f.title, f.subtitle.orEmpty(), f.lat, f.lon)) },
                    onRemove = { vm.remove(f) },
                )
            }

            // ---- Stops (live dashboard)
            item { SectionHeader(stringResource(R.string.section_stops)) }
            if (stops.isEmpty()) item {
                Text(
                    stringResource(R.string.no_favorite_stops),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(stops, key = { "s-${it.id}" }) { f ->
                val code = f.key.toIntOrNull() ?: return@items
                val board = boards[code]
                StopCard(
                    fav = f,
                    code = code,
                    board = board,
                    isWidget = widgetStop == code,
                    now = now,
                    onClick = { onStop(code) },
                    onLine = onLine,
                    onRemove = { vm.remove(f) },
                    onWidget = { vm.setWidgetStop(code) },
                )
            }

            // ---- Lines
            item { SectionHeader(stringResource(R.string.section_lines)) }
            if (lines.isEmpty()) item {
                Text(
                    stringResource(R.string.no_favorite_lines),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(lines, key = { "l-${it.id}" }) { f ->
                val parts = f.subtitle.orEmpty().split('|')
                val lineRef = parts.getOrNull(0)?.toLongOrNull()
                val operatorRef = f.key.substringBefore(':').toIntOrNull() ?: 0
                FavRow(
                    icon = { LineBadge(f.title, operatorRef) },
                    title = parts.getOrNull(2)?.let { stringResource(R.string.to_destination, it) } ?: f.title,
                    subtitle = parts.getOrNull(1),
                    onClick = { lineRef?.let(onLine) },
                    onRemove = { vm.remove(f) },
                )
            }
            if (list.isEmpty()) item {
                MessageState(Icons.Outlined.StarBorder, stringResource(R.string.favorites_empty), Modifier.padding(top = 24.dp))
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun FavRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterStart) { icon() }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(
                subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopCard(
    fav: Favorite,
    code: Int,
    board: StopBoard?,
    isWidget: Boolean,
    now: java.time.Instant,
    onClick: () -> Unit,
    onLine: (Long) -> Unit,
    onRemove: () -> Unit,
    onWidget: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 10.dp)) {
                    Text(fav.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(fav.subtitle?.takeIf { it.isNotBlank() }, stringResource(R.string.stop_code, code)).joinToString(" · ") +
                            if (isWidget) " · " + stringResource(R.string.on_widget) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.show_on_widget)) },
                            leadingIcon = { Icon(Icons.Outlined.Widgets, null) },
                            onClick = { menu = false; onWidget() },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, onClick = { menu = false; onRemove() })
                    }
                }
            }
            when {
                board == null || !board.loaded -> Text(
                    stringResource(R.string.loading_arrivals), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                board.error != null && board.lines.isEmpty() -> Text(
                    errorMessage(board.error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
                board.lines.isEmpty() -> Text(
                    stringResource(R.string.no_arrivals_2h), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(end = 14.dp),
                ) {
                    board.lines.take(8).forEach { line ->
                        val a = line.next.first()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onLine(line.lineRef) }) {
                            LineBadge(line.shortName, line.operatorRef)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                minutesText(a.best, now),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (a.live != null) liveColor() else MaterialTheme.colorScheme.onSurface,
                            )
                            if (a.live != null) {
                                Spacer(Modifier.width(4.dp))
                                LiveTag(estimate = false)
                            }
                        }
                    }
                }
            }
        }
    }
}
