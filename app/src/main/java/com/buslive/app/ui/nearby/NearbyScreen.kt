package com.buslive.app.ui.nearby

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.buslive.app.domain.util.Geo
import com.buslive.app.ui.common.ErrorState
import com.buslive.app.ui.common.LineBadge
import com.buslive.app.ui.common.LiveTag
import com.buslive.app.ui.common.LoadingState
import com.buslive.app.ui.common.MessageState
import com.buslive.app.ui.common.OfflineBanner
import com.buslive.app.ui.common.agoText
import com.buslive.app.ui.common.minutesText
import com.buslive.app.ui.common.rememberNow
import com.buslive.app.ui.theme.liveColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NearbyScreen(onStop: (Int) -> Unit, onLine: (Long) -> Unit, vm: NearbyViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val now by rememberNow()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionResult()
    }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.autoRefreshLoop() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.tab_nearby))
                        state.lastUpdated?.let {
                            Text(
                                stringResource(R.string.updated_ago, agoText(it, now)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!online) OfflineBanner()
            when {
                !state.hasPermission -> MessageState(
                    Icons.Outlined.LocationOff,
                    stringResource(R.string.nearby_needs_location),
                    modifier = Modifier.padding(top = 48.dp),
                ) {
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = {
                        permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text(stringResource(R.string.allow_location)) }
                }
                state.noLocation -> MessageState(
                    Icons.Outlined.LocationSearching, stringResource(R.string.no_location_fix), Modifier.padding(top = 48.dp),
                ) {
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = vm::refresh) { Text(stringResource(R.string.retry)) }
                }
                state.noCache -> MessageState(
                    Icons.Outlined.CloudDownload, stringResource(R.string.syncing_data), Modifier.padding(top = 48.dp),
                ) {
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = vm::refresh) { Text(stringResource(R.string.retry)) }
                }
                state.stops.isEmpty() && state.refreshing -> LoadingState(Modifier.padding(top = 48.dp))
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (state.stops.isEmpty()) item {
                            MessageState(Icons.Outlined.WrongLocation, stringResource(R.string.no_stops_nearby))
                        }
                        items(state.stops, key = { it.stop.code }) { ns ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .clickable { onStop(ns.stop.code) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                ns.stop.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                stringResource(R.string.stop_code, ns.stop.code),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            Geo.formatDistance(ns.distanceM) + " · " + stringResource(R.string.walk_minutes, ns.walkMinutes),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                    Spacer(Modifier.size(8.dp))
                                    when {
                                        !ns.loaded -> Text(
                                            stringResource(R.string.loading_arrivals),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        ns.error != null && ns.lines.isEmpty() -> ErrorState(ns.error, onRetry = vm::refresh)
                                        ns.lines.isEmpty() -> Text(
                                            stringResource(R.string.no_arrivals_90),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        else -> FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            ns.lines.take(8).forEach { line ->
                                                val a = line.next.first()
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.clickable { onLine(line.lineRef) },
                                                ) {
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
                        item { Spacer(Modifier.size(24.dp)) }
                    }
                }
            }
        }
    }
}
