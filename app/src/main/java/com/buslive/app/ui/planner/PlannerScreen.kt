package com.buslive.app.ui.planner

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buslive.app.R
import com.buslive.app.domain.model.Operators
import com.buslive.app.domain.model.Place
import com.buslive.app.domain.planner.TripLeg
import com.buslive.app.domain.planner.TripOption
import com.buslive.app.domain.util.Geo
import com.buslive.app.domain.util.TimeUtil
import com.buslive.app.ui.common.ErrorState
import com.buslive.app.ui.common.LineBadge
import com.buslive.app.ui.common.MessageState
import com.buslive.app.ui.common.SectionHeader
import com.buslive.app.ui.map.BusMapController
import com.buslive.app.ui.map.BusMapView
import com.buslive.app.ui.map.MapPolyline
import java.time.Instant
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    initialTo: Place?,
    onStartGuidance: () -> Unit,
    vm: PlannerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val saved by vm.savedPlaces.collectAsStateWithLifecycle()
    val recents by vm.recentTrips.collectAsStateWithLifecycle()
    val guidanceActive = vm.guidanceState.collectAsStateWithLifecycle().value != null
    var picking by rememberSaveable { mutableStateOf<String?>(null) } // "from" | "to"
    var showTime by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<TripOption?>(null) }

    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.plan() }
    val startPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val opt = pendingStart
        pendingStart = null
        if (opt != null && (r[Manifest.permission.ACCESS_FINE_LOCATION] == true || vm.hasLocationPermission())) {
            vm.start(opt)
            onStartGuidance()
        }
    }

    LaunchedEffect(initialTo) { vm.applyInitialDestination(initialTo) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.plan_title)) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
                if (guidanceActive) com.buslive.app.ui.map.GuidanceBanner(onClick = onStartGuidance)
                EndpointsCard(
                    fromLabel = when (val f = state.from) {
                        Endpoint.MyLocation -> stringResource(R.string.my_location)
                        is Endpoint.Fixed -> f.place.name
                    },
                    toLabel = state.to?.name?.ifBlank { stringResource(R.string.dropped_pin) },
                    onFrom = { picking = "from" },
                    onTo = { picking = "to" },
                    onSwap = vm::swap,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { showTime = true },
                        label = {
                            Text(state.departAt?.let { stringResource(R.string.leave_at, TimeUtil.formatTime(it)) } ?: stringResource(R.string.leave_now))
                        },
                        leadingIcon = { Icon(Icons.Outlined.AccessTime, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                    if (state.departAt != null) TextButton(onClick = { vm.setDepartAt(null) }) { Text(stringResource(R.string.leave_now)) }
                }
                if (state.loading) Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.planning), style = MaterialTheme.typography.bodySmall)
                }
                // Map stays on top while the options scroll below it.
                if (state.options.isNotEmpty() && state.to != null) TripPreviewMap(state.selected)
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.to == null -> {
                    if (recents.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.recent_trips)) }
                        items(recents, key = { "rt${it.id}" }) { r ->
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.useRecent(r) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.History, null)
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    stringResource(R.string.from_to, r.fromName.ifBlank { stringResource(R.string.my_location) }, r.toName),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else item {
                        MessageState(Icons.Outlined.Place, stringResource(R.string.plan_hint), Modifier.padding(top = 24.dp))
                    }
                }
                state.needsLocation -> item {
                    MessageState(Icons.Outlined.LocationOff, stringResource(R.string.plan_needs_location)) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }) { Text(stringResource(R.string.allow_location)) }
                    }
                }
                state.error != null && !state.loading -> item { ErrorState(state.error!!, onRetry = vm::plan) }
                state.noService && !state.loading -> item {
                    MessageState(Icons.Outlined.DirectionsBus, stringResource(R.string.no_trips_found)) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = vm::plan) { Text(stringResource(R.string.retry)) }
                    }
                }
                state.options.isNotEmpty() -> {
                    item {
                        Text(
                            stringResource(
                                when {
                                    state.otpFellBack -> R.string.planner_otp_failed
                                    state.usedOtp -> R.string.planner_otp
                                    else -> R.string.planner_local
                                }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    items(state.options, key = { it.id }) { o ->
                        TripCard(
                            option = o,
                            selected = o.id == state.selectedId,
                            tracked = state.live.containsKey(o.id),
                            live = state.live[o.id],
                            onClick = { vm.select(o.id) },
                            onStart = {
                                pendingStart = o
                                startPerms.launch(
                                    buildList {
                                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                                    }.toTypedArray()
                                )
                            },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
        }
    }

    picking?.let { which ->
        PlacePickerDialog(
            title = stringResource(if (which == "from") R.string.from else R.string.to),
            allowMyLocation = which == "from",
            saved = saved,
            onMyLocation = { vm.setFrom(Endpoint.MyLocation); picking = null },
            onPick = { p ->
                if (which == "from") vm.setFrom(Endpoint.Fixed(p)) else vm.setTo(p)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }

    if (showTime) {
        val now = LocalTime.now(TimeUtil.ZONE)
        val tp = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(stringResource(R.string.leave_at_title)) },
            text = { TimePicker(state = tp) },
            confirmButton = {
                TextButton(onClick = {
                    var t = TimeUtil.today().atTime(tp.hour, tp.minute).atZone(TimeUtil.ZONE).toInstant()
                    if (t < Instant.now().minusSeconds(60)) t = t.plusSeconds(24 * 3600)
                    vm.setDepartAt(t)
                    showTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun EndpointsCard(fromLabel: String, toLabel: String?, onFrom: () -> Unit, onTo: () -> Unit, onSwap: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                EndpointRow(Icons.Outlined.MyLocation, stringResource(R.string.from), fromLabel, onFrom)
                HorizontalDivider(Modifier.padding(start = 52.dp))
                EndpointRow(Icons.Outlined.Place, stringResource(R.string.to), toLabel ?: stringResource(R.string.choose_destination), onTo, placeholder = toLabel == null)
            }
            IconButton(onClick = onSwap) { Icon(Icons.Outlined.SwapVert, stringResource(R.string.swap)) }
        }
    }
}

@Composable
private fun EndpointRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit, placeholder: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (placeholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TripPreviewMap(option: TripOption?) {
    var controller by remember { mutableStateOf<BusMapController?>(null) }
    val density = LocalDensity.current
    val start = option?.legs?.firstOrNull()?.path?.firstOrNull() ?: return
    LaunchedEffect(controller, option) {
        val c = controller ?: return@LaunchedEffect
        val lines = option.legs.map { leg ->
            when (leg) {
                is TripLeg.Walk -> MapPolyline(leg.path, 0xFF3F4949.toInt(), dashed = true)
                is TripLeg.Bus -> MapPolyline(leg.path, Operators.color(leg.operatorRef), dashed = false)
            }
        }
        c.setTrip(lines)
        c.setPin(option.legs.last().path.lastOrNull())
        c.setUser(option.legs.first().path.firstOrNull())
        c.fitTo(option.legs.flatMap { it.path }, with(density) { 32.dp.roundToPx() })
    }
    Card(Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 12.dp), shape = RoundedCornerShape(16.dp)) {
        BusMapView(
            initialCenter = start,
            initialZoom = 13.0,
            onControllerReady = { controller = it },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripCard(
    option: TripOption,
    selected: Boolean,
    tracked: Boolean,
    live: com.buslive.app.domain.model.LiveEstimate?,
    onClick: () -> Unit,
    onStart: () -> Unit,
) {
    val now by com.buslive.app.ui.common.rememberNow(15_000)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.minutes_total, ((option.totalSeconds + 59) / 60).toInt()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.from_to, TimeUtil.formatTime(option.departure), TimeUtil.formatTime(option.arrival)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (option.transfers > 0) Text(
                    pluralStringResource(R.plurals.transfers, option.transfers, option.transfers),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            // When to leave + is the first bus already driving?
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.leave_in, com.buslive.app.ui.common.minutesText(option.departure, now)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tracked) {
                    Spacer(Modifier.width(8.dp))
                    com.buslive.app.ui.common.LiveTag(estimate = live != null)
                    live?.let {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.bus_at, TimeUtil.formatTime(it.eta)),
                            style = MaterialTheme.typography.labelMedium,
                            color = com.buslive.app.ui.theme.liveColor(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                option.legs.forEachIndexed { i, leg ->
                    if (i > 0) Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                        tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp).align(Alignment.CenterVertically),
                    )
                    when (leg) {
                        is TripLeg.Walk -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterVertically)) {
                            Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, null, Modifier.size(20.dp))
                            Text(
                                "${((java.time.Duration.between(leg.start, leg.end).seconds + 59) / 60)}′",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        is TripLeg.Bus -> LineBadge(leg.shortName, leg.operatorRef, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    R.string.trip_breakdown,
                    ((option.walkSeconds + 59) / 60).toInt(),
                    ((option.waitSeconds + 59) / 60).toInt(),
                    ((option.rideSeconds + 59) / 60).toInt(),
                    option.stopsCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(selected) {
                Column(Modifier.padding(top = 8.dp)) {
                    option.legs.forEach { LegRow(it) }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Navigation, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.start))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegRow(leg: TripLeg) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(
            TimeUtil.formatTime(leg.start),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(52.dp),
        )
        when (leg) {
            is TripLeg.Walk -> {
                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        R.string.leg_walk,
                        Geo.formatDistance(leg.distanceM.toDouble()),
                        leg.toName.ifBlank { stringResource(R.string.destination) },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is TripLeg.Bus -> {
                LineBadge(leg.shortName, leg.operatorRef)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.leg_board, leg.from.name, leg.headsign), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.leg_ride, leg.stopsCount, leg.to.name, TimeUtil.formatTime(leg.end)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
