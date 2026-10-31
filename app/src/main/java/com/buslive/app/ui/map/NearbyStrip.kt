package com.buslive.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.buslive.app.R
import com.buslive.app.domain.model.Stop
import com.buslive.app.domain.util.Geo
import com.buslive.app.ui.common.LineBadge
import com.buslive.app.ui.common.minutesText
import com.buslive.app.ui.common.rememberNow
import com.buslive.app.ui.nearby.NearbyViewModel
import com.buslive.app.ui.theme.liveColor

/** Bottom third of the home screen: closest stops with their next buses. Tap a stop to show it on the map. */
@Composable
fun NearbyStrip(
    vm: NearbyViewModel,
    onRequestPermission: () -> Unit,
    onStop: (Stop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.autoRefreshLoop() } }

    Surface(modifier = modifier, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.stops_around_you),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (state.refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                IconButton(onClick = vm::refresh) { Icon(Icons.Outlined.Refresh, stringResource(R.string.retry)) }
            }
            HorizontalDivider()
            when {
                !state.hasPermission -> Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.LocationOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.nearby_needs_location), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRequestPermission) { Text(stringResource(R.string.allow)) }
                }
                state.noLocation -> Text(stringResource(R.string.no_location_fix), Modifier.padding(16.dp))
                state.noCache -> Text(stringResource(R.string.syncing_data), Modifier.padding(16.dp))
                state.stops.isEmpty() && !state.refreshing && state.lastUpdated != null ->
                    Text(stringResource(R.string.no_stops_nearby), Modifier.padding(16.dp))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.stops, key = { it.stop.code }) { ns ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onStop(ns.stop) }.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    ns.stop.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, null, Modifier.size(16.dp))
                                Text(
                                    Geo.formatDistance(ns.distanceM) + " · " + stringResource(R.string.walk_minutes, ns.walkMinutes),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                when {
                                    !ns.loaded -> Text(
                                        stringResource(R.string.loading_arrivals),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ns.lines.isEmpty() -> Text(
                                        stringResource(R.string.no_arrivals_90),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    else -> ns.lines.take(8).forEach { line ->
                                        val a = line.next.first()
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            LineBadge(line.shortName, line.operatorRef)
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                minutesText(a.best, now),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (a.live != null) liveColor() else MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}
