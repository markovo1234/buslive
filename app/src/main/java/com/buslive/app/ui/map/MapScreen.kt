package com.buslive.app.ui.map

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.buslive.app.R
import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.domain.model.Line
import com.buslive.app.domain.model.Place
import com.buslive.app.domain.model.PlaceKind
import com.buslive.app.domain.model.RideStop
import com.buslive.app.service.SyncState
import com.buslive.app.ui.common.LineBadge
import com.buslive.app.ui.common.OfflineBanner
import com.buslive.app.ui.common.SheetCorner
import com.buslive.app.ui.common.agoText
import com.buslive.app.ui.common.errorMessage
import com.buslive.app.ui.common.rememberNow
import com.buslive.app.ui.nearby.NearbyViewModel
import com.buslive.app.ui.stop.StopActions
import com.buslive.app.ui.stop.StopDetailsList
import com.buslive.app.ui.stop.StopDetailsViewModel
import com.buslive.app.ui.stop.StopHeader
import kotlinx.coroutines.launch

data class MapNav(
    val openSearch: () -> Unit,
    val openSettings: () -> Unit,
    val openLine: (lineRef: Long) -> Unit,
    val planTo: (Place) -> Unit,
    val openGuidance: () -> Unit,
)

@Composable
fun MapScreen(
    nav: MapNav,
    vm: MapViewModel = hiltViewModel(),
    nearbyVm: NearbyViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val favoriteLines by vm.favoriteLineKeys.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var controller by remember { mutableStateOf<BusMapController?>(null) }
    var hasPerm by remember { mutableStateOf(vm.hasLocationPermission()) }
    var showRationale by rememberSaveable { mutableStateOf(!hasPerm) }
    var centeredOnce by rememberSaveable { mutableStateOf(false) }
    var showLegend by rememberSaveable { mutableStateOf(false) }
    var pendingAlert by remember { mutableStateOf<Pair<String, RideStop>?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        hasPerm = r.values.any { it }
        showRationale = false
        nearbyVm.onPermissionResult()
        if (hasPerm) scope.launch { vm.currentLocation()?.let { controller?.moveTo(it, 15.5) } }
    }
    val alertPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val p = pendingAlert
        pendingAlert = null
        if (p != null && (r[Manifest.permission.ACCESS_FINE_LOCATION] == true || vm.hasLocationPermission())) {
            hasPerm = true
            if (vm.startStopAlert(p.first, p.second)) nav.openGuidance()
        }
    }

    // Live updates only while the screen is visible.
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.liveLoop() } }
    LaunchedEffect(lifecycle, hasPerm) {
        if (hasPerm) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.locationLoop() }
    }
    LaunchedEffect(controller, hasPerm) {
        val c = controller ?: return@LaunchedEffect
        if (hasPerm && !centeredOnce) {
            centeredOnce = true
            vm.currentLocation()?.let { c.moveTo(it, 15.5, animated = false) }
        }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        vm.camera.collect { e ->
            when (e) {
                is MapViewModel.CameraEvent.MoveTo -> c.moveTo(e.point, e.zoom)
                is MapViewModel.CameraEvent.Fit -> c.fitTo(e.points, with(density) { 64.dp.roundToPx() })
            }
        }
    }
    // Push state into the map.
    LaunchedEffect(controller, state.buses) {
        val now = java.time.Instant.now()
        controller?.setBuses(state.buses, state.feedTime?.let { minOf(it, now) } ?: now)
    }
    LaunchedEffect(controller, state.stops) { controller?.setStops(state.stops) }
    LaunchedEffect(controller, state.userLocation) { controller?.setUser(state.userLocation) }
    LaunchedEffect(controller, state.followBus) { controller?.setFollow(state.followBus) }
    LaunchedEffect(controller, state.selectedLine?.stops, state.selectedLine?.color) {
        val sl = state.selectedLine
        controller?.setRoute(
            points = sl?.stops?.map { it.point }.orEmpty(),
            stops = sl?.stops?.map { com.buslive.app.domain.model.Stop(it.stopCode, it.name, it.city, it.lat, it.lon) }.orEmpty(),
            color = sl?.color,
        )
    }
    LaunchedEffect(controller, state.selection) {
        val c = controller ?: return@LaunchedEffect
        val sel = state.selection
        c.setSelectedBus((sel as? MapSelection.Bus)?.vehicleRef)
        c.setSelectedStop((sel as? MapSelection.StopSel)?.code)
        c.setPin((sel as? MapSelection.Pin)?.point)
    }

    val guidance by vm.guidanceState.collectAsStateWithLifecycle()
    // Back closes a sheet first, then a selected line, then leaves the app.
    BackHandler(enabled = state.selection != null || state.filter == BusFilter.LINE) {
        if (state.selection != null) vm.clearSelection() else vm.clearLine()
    }

    val initial = state.cameraCenter ?: state.initialCenter
    val initialZoom = if (state.cameraCenter != null) state.zoom else 15.0
    // Home = 2/3 live map + 1/3 stops around you.
    Column(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxWidth().weight(2f)) {
        if (initial != null) {
            BusMapView(
                initialCenter = initial,
                initialZoom = initialZoom,
                onControllerReady = { controller = it },
                modifier = Modifier.fillMaxSize(),
                topInset = 132.dp,
                bottomInset = 0.dp,
                onCameraIdle = vm::onCameraIdle,
                onMapClick = { latLng ->
                    when (val hit = controller?.hitTest(latLng)) {
                        is BusMapController.Hit.Bus -> vm.selectBus(hit.id)
                        is BusMapController.Hit.StopHit -> vm.selectStop(hit.code)
                        is BusMapController.Hit.Cluster -> controller?.moveTo(
                            GeoPoint(hit.at.latitude, hit.at.longitude), (controller?.map?.cameraPosition?.zoom ?: 13.0) + 2,
                        )
                        null -> vm.clearSelection()
                    }
                },
                onMapLongClick = { vm.dropPin(GeoPoint(it.latitude, it.longitude)) },
                onUserGesture = vm::stopFollowing,
            )
        }

        // ---------------- top overlay
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            if (!online) OfflineBanner()
            if (sync == SyncState.RUNNING) InfoBanner(stringResource(R.string.syncing_data), progress = true)
            if (sync == SyncState.FAILED) InfoBanner(stringResource(R.string.sync_failed))
            if (guidance != null) GuidanceBanner(onClick = nav.openGuidance)
            SearchPill(onClick = nav.openSearch, onSettings = nav.openSettings)
            val places by vm.savedPlaces.collectAsStateWithLifecycle()
            if (places.isNotEmpty()) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                places.forEach { f ->
                    val label = when (f.placeKind) {
                        com.buslive.app.domain.model.PlaceKind.HOME -> stringResource(R.string.save_home)
                        com.buslive.app.domain.model.PlaceKind.WORK -> stringResource(R.string.save_work)
                        else -> f.title
                    }
                    androidx.compose.material3.SuggestionChip(
                        onClick = { nav.planTo(Place(label, f.subtitle.orEmpty(), f.lat!!, f.lon!!)) },
                        label = { Text(label, maxLines = 1) },
                        icon = {
                            Icon(
                                when (f.placeKind) {
                                    com.buslive.app.domain.model.PlaceKind.HOME -> Icons.Outlined.Home
                                    com.buslive.app.domain.model.PlaceKind.WORK -> Icons.Outlined.Work
                                    else -> Icons.Outlined.Place
                                },
                                null, Modifier.size(18.dp),
                            )
                        },
                        colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.filter == BusFilter.ALL,
                    onClick = { vm.setFilter(BusFilter.ALL) },
                    label = { Text(stringResource(R.string.filter_all)) },
                    leadingIcon = { Icon(Icons.Outlined.DirectionsBus, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    colors = chipColors(),
                )
                FilterChip(
                    selected = state.filter == BusFilter.FAVORITES,
                    onClick = { vm.setFilter(BusFilter.FAVORITES) },
                    label = { Text(stringResource(R.string.filter_favorites)) },
                    leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    enabled = favoriteLines.isNotEmpty() || state.filter == BusFilter.FAVORITES,
                    colors = chipColors(),
                )
                state.selectedLine?.let { sl ->
                    InputChip(
                        selected = true,
                        onClick = { nav.openLine(sl.line.lineRef) },
                        label = {
                            Text(
                                stringResource(R.string.line_to, sl.line.shortName, sl.line.destination),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(180.dp),
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = vm::clearLine, modifier = Modifier.size(InputChipDefaults.IconSize + 8.dp)) {
                                Icon(Icons.Filled.Close, stringResource(R.string.clear_line), Modifier.size(InputChipDefaults.IconSize))
                            }
                        },
                    )
                }
            }
        }

        // ---------------- bottom overlay
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                StatusPill(state, onRetry = vm::refreshNow, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallFloatingActionButton(onClick = { showLegend = true }) {
                        Icon(Icons.Outlined.Info, stringResource(R.string.legend_title))
                    }
                    SmallFloatingActionButton(onClick = {
                        if (!hasPerm) permLauncher.launch(LOCATION_PERMS)
                        else scope.launch { vm.currentLocation()?.let { controller?.moveTo(it, 16.0) } }
                    }) {
                        Icon(
                            if (hasPerm) Icons.Outlined.MyLocation else Icons.Outlined.LocationOff,
                            stringResource(R.string.my_location),
                        )
                    }
                }
            }
            if (showRationale && state.selection == null) {
                LocationRationale(
                    onAllow = { permLauncher.launch(LOCATION_PERMS) },
                    onLater = { showRationale = false },
                )
            }
            AnimatedVisibility(
                visible = state.selection != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when (val sel = state.selection) {
                        is MapSelection.Bus -> BusPanel(
                            bus = state.buses.firstOrNull { it.vehicleRef == sel.vehicleRef },
                            detail = state.busDetail,
                            following = state.followBus == sel.vehicleRef,
                            onFollow = { vm.toggleFollow(sel.vehicleRef) },
                            onShowLine = { line: Line -> vm.selectLine(line) },
                            onSchedule = nav.openLine,
                            onAlertAt = { stop ->
                                pendingAlert = sel.vehicleRef to stop
                                alertPermLauncher.launch(ALERT_PERMS)
                            },
                            onClose = vm::clearSelection,
                        )
                        is MapSelection.StopSel -> StopPanel(
                            code = sel.code,
                            onClose = vm::clearSelection,
                            actions = StopActions(
                                onLineSchedule = nav.openLine,
                                onShowLineOnMap = { vm.selectLineByRef(it) },
                                onDirections = { s -> nav.planTo(Place(s.name, s.city, s.lat, s.lon)) },
                            ),
                        )
                        is MapSelection.Pin -> PinPanel(
                            point = sel.point,
                            onDirections = { name -> nav.planTo(Place(name, "", sel.point.lat, sel.point.lon)) },
                            onSave = { kind, name -> vm.savePlace(Place(name, "", sel.point.lat, sel.point.lon), kind) },
                            onClose = vm::clearSelection,
                        )
                        null -> Unit
                    }
                }
            }
        }
    }
    NearbyStrip(
        vm = nearbyVm,
        modifier = Modifier.fillMaxWidth().weight(1f),
        onRequestPermission = { permLauncher.launch(LOCATION_PERMS) },
        onStop = { stop ->
            vm.selectStop(stop.code)
            controller?.moveTo(stop.point, 16.5)
        },
    )
    }

    if (showLegend) LegendDialog(onDismiss = { showLegend = false })
}

private val LOCATION_PERMS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
private val ALERT_PERMS = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,
)

@Composable
private fun SearchPill(onClick: () -> Unit, onSettings: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
        }
    }
}

/** Shown on top of the map while live directions run in the background. */
@Composable
fun GuidanceBanner(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Navigation, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.guidance_running), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.open), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun InfoBanner(text: String, progress: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (progress) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatusPill(state: MapUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val now by rememberNow()
    Surface(
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 3.dp,
        tonalElevation = 2.dp,
        modifier = modifier,
        onClick = onRetry,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.loadingBuses) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            val text = when {
                state.busError != null -> errorMessage(state.busError) + " · " + stringResource(R.string.tap_to_retry)
                state.zoomTooLow -> stringResource(R.string.zoom_in_for_buses)
                state.lastUpdated != null -> {
                    val data = state.newestRecordedAt?.let { stringResource(R.string.gps_age, agoText(it, now)) }
                    stringResource(R.string.live_buses_count, state.buses.size) + " · " +
                        stringResource(R.string.updated_ago, agoText(state.lastUpdated, now)) +
                        (data?.let { " · $it" } ?: "")
                }
                else -> stringResource(R.string.loading_buses)
            }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
    }
}

@Composable
private fun LocationRationale(onAllow: () -> Unit, onLater: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(SheetCorner),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.location_rationale_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.location_rationale), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) { Text(stringResource(R.string.not_now)) }
                Button(onClick = onAllow) { Text(stringResource(R.string.allow)) }
            }
        }
    }
}

@Composable
private fun StopPanel(code: Int, onClose: () -> Unit, actions: StopActions, vm: StopDetailsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(code) { vm.load(code) }
    LaunchedEffect(code, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.refreshLoop() } }
    Column(Modifier.padding(top = 8.dp)) {
        PanelHandle()
        StopHeader(state, onToggleFavorite = vm::toggleFavorite) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
        }
        StopDetailsList(
            state = state,
            actions = actions,
            onRetry = vm::retry,
            onRemind = vm::remind,
            modifier = Modifier.heightIn(max = 380.dp),
        )
    }
}

@Composable
fun PanelHandle() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.size(width = 36.dp, height = 4.dp),
        ) {}
    }
}

@Composable
private fun LegendDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        title = { Text(stringResource(R.string.legend_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendRow({ LineBadge("5", 5) }, stringResource(R.string.legend_live))
                LegendRow({ LineBadge("5", 5, modifier = Modifier.alpha(0.45f)) }, stringResource(R.string.legend_faded))
                LegendRow({ com.buslive.app.ui.common.LiveTag() }, stringResource(R.string.legend_estimate))
                LegendRow({ Text("14:05", style = MaterialTheme.typography.labelLarge) }, stringResource(R.string.legend_planned))
                Text(stringResource(R.string.legend_source), style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
private fun LegendRow(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
