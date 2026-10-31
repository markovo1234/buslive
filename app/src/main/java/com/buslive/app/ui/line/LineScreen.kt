package com.buslive.app.ui.line

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.buslive.app.domain.model.Operators
import com.buslive.app.domain.model.RideStop
import com.buslive.app.domain.util.TimeUtil
import com.buslive.app.ui.common.ErrorState
import com.buslive.app.ui.common.LineBadge
import com.buslive.app.ui.common.LoadingState
import com.buslive.app.ui.common.MessageState
import com.buslive.app.ui.common.agoText
import com.buslive.app.ui.common.operatorName
import com.buslive.app.ui.common.rememberNow
import com.buslive.app.ui.theme.liveColor
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LineScreen(
    lineRef: Long,
    onBack: () -> Unit,
    onStop: (Int) -> Unit,
    onShowOnMap: (Long) -> Unit,
    onStartGuidance: () -> Unit,
    vm: LineViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingAlert by remember { mutableStateOf<RideStop?>(null) }
    val alertPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val stop = pendingAlert
        pendingAlert = null
        if (stop != null && r[Manifest.permission.ACCESS_FINE_LOCATION] == true && vm.alertAt(stop)) onStartGuidance()
    }

    LaunchedEffect(lineRef) { vm.load(lineRef) }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.liveLoop() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                title = {
                    val l = state.line
                    if (l != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        LineBadge(l.shortName, l.operatorRef)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            operatorName(l.operatorRef, l.agencyName),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = vm::toggleFavorite) {
                        Icon(
                            if (state.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            stringResource(if (state.isFavorite) R.string.cd_unfavorite else R.string.cd_favorite),
                            tint = if (state.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { state.selected?.let { onShowOnMap(it.lineRef) } }) {
                        Icon(Icons.Outlined.Map, stringResource(R.string.show_on_map))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.notFound) {
                MessageState(Icons.Outlined.DirectionsBus, stringResource(R.string.line_not_found))
                return@Column
            }
            // Directions / alternatives
            if (state.directions.size > 1) {
                val selIndex = state.directions.indexOfFirst { it.lineRef == state.selected?.lineRef }.coerceAtLeast(0)
                PrimaryScrollableTabRow(selectedTabIndex = selIndex, edgePadding = 8.dp) {
                    state.directions.forEachIndexed { i, d ->
                        Tab(
                            selected = i == selIndex,
                            onClick = { vm.selectDirection(d) },
                            text = {
                                Text(
                                    stringResource(R.string.to_destination, d.destination),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(180.dp),
                                )
                            },
                        )
                    }
                }
            } else state.selected?.let {
                Text(
                    stringResource(R.string.to_destination, it.destination),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // Date
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val today = TimeUtil.today()
                FilterChip(selected = state.date == today, onClick = { vm.selectDate(today) }, label = { Text(stringResource(R.string.today)) })
                FilterChip(
                    selected = state.date == today.plusDays(1),
                    onClick = { vm.selectDate(today.plusDays(1)) },
                    label = { Text(stringResource(R.string.tomorrow)) },
                )
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(state.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
            if (state.date != state.sourceDate) {
                Text(
                    stringResource(R.string.schedule_estimated_from, state.sourceDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            SecondaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.stops_tab)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.departures_tab)) })
            }
            when {
                state.loading && state.rides.isEmpty() -> LoadingState()
                state.error != null && state.rides.isEmpty() -> ErrorState(state.error!!, onRetry = vm::retry)
                state.rides.isEmpty() -> MessageState(Icons.Outlined.DirectionsBus, stringResource(R.string.no_departures))
                tab == 1 -> DeparturesGrid(state, now) { ride -> vm.selectRide(ride); tab = 0 }
                else -> StopTimeline(
                    state = state,
                    now = now,
                    onStop = onStop,
                    onAlert = { stop ->
                        pendingAlert = stop
                        alertPerms.launch(
                            buildList {
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                            }.toTypedArray()
                        )
                    },
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.selectDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeparturesGrid(state: LineState, now: Instant, onRide: (com.buslive.app.domain.model.Ride) -> Unit) {
    val byHour = state.rides.groupBy { it.start.atZone(TimeUtil.ZONE).hour }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        byHour.forEach { (hour, rides) ->
            item(key = "h$hour") {
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        "%02d".format(hour),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(36.dp).padding(top = 6.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rides.forEach { ride ->
                            val past = state.date == TimeUtil.today() && ride.start < now
                            val selected = state.selectedRide?.rideId == ride.rideId
                            FilterChip(
                                selected = selected,
                                onClick = { onRide(ride) },
                                label = {
                                    Text(
                                        TimeUtil.formatTime(ride.start),
                                        color = if (past && !selected) MaterialTheme.colorScheme.outline else Color.Unspecified,
                                    )
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun StopTimeline(state: LineState, now: Instant, onStop: (Int) -> Unit, onAlert: (RideStop) -> Unit) {
    val color = Color(Operators.color(state.selected?.operatorRef ?: 0))
    val ride = state.selectedRide
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                ride?.let { stringResource(R.string.trip_departing, TimeUtil.formatTime(it.start)) }.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (state.date == TimeUtil.today()) Text(
                stringResource(R.string.live_buses_count, state.liveBuses.size) +
                    (state.liveUpdated?.let { " · " + agoText(it, now) } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = liveColor(),
            )
        }
        if (state.stopsLoading && state.stops.isEmpty()) {
            LoadingState()
            return
        }
        val busesAfter = state.liveBuses.groupBy { it.afterIndex }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(state.stops, key = { _, s -> "${s.sequence}-${s.stopCode}" }) { i, stop ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable { onStop(stop.stopCode) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Timeline rail
                    Box(Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(color.copy(alpha = 0.6f)))
                        Box(
                            Modifier.size(if (i == 0 || i == state.stops.lastIndex) 14.dp else 10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stop.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(stop.city, stringResource(R.string.stop_code, stop.stopCode)).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Text(TimeUtil.formatTime(stop.arrival), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { onAlert(stop) }) {
                        Icon(Icons.Outlined.NotificationAdd, stringResource(R.string.alert_me_at, stop.name))
                    }
                }
                busesAfter[i]?.forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.DirectionsBus, null, tint = liveColor(), modifier = Modifier.size(22.dp))
                        }
                        Text(
                            stringResource(R.string.vehicle, b.bus.vehicleRef) + " · " +
                                stringResource(R.string.gps_age, agoText(b.bus.recordedAt, now)),
                            style = MaterialTheme.typography.labelMedium,
                            color = liveColor(),
                        )
                    }
                }
            }
        }
    }
}
