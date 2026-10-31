package com.buslive.app.ui.guidance

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buslive.app.R
import com.buslive.app.domain.model.LiveBus
import com.buslive.app.domain.model.Operators
import com.buslive.app.domain.model.Stop
import com.buslive.app.domain.planner.TripLeg
import com.buslive.app.domain.util.Geo
import com.buslive.app.domain.util.TimeUtil
import com.buslive.app.service.GuidanceManager
import com.buslive.app.service.GuidancePhase
import com.buslive.app.service.GuidanceState
import com.buslive.app.ui.common.LineBadge
import com.buslive.app.ui.common.LiveTag
import com.buslive.app.ui.common.MessageState
import com.buslive.app.ui.common.agoText
import com.buslive.app.ui.common.minutesText
import com.buslive.app.ui.common.rememberNow
import com.buslive.app.ui.map.BusMapController
import com.buslive.app.ui.map.BusMapView
import com.buslive.app.ui.map.MapPolyline
import com.buslive.app.ui.theme.Warning
import com.buslive.app.ui.theme.liveColor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class GuidanceViewModel @Inject constructor(private val manager: GuidanceManager) : ViewModel() {
    val state = manager.state
    fun stop() = manager.stop()
    fun shareText() = manager.shareText()
}

/** Full-screen live directions: big map that follows you, current step, next step, stop progress. */
@Composable
fun GuidanceScreen(onBack: () -> Unit, vm: GuidanceViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_trip)
    BackHandler(onBack = onBack)
    val share: () -> Unit = {
        vm.shareText()?.let { text ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, shareTitle))
        }
    }

    val s = state
    if (s == null) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            MessageState(Icons.Outlined.Navigation, stringResource(R.string.no_active_directions), Modifier.padding(top = 48.dp))
        }
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            GuidanceMap(s, Modifier.fillMaxSize())
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilledTonalIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                FilledTonalIconButton(onClick = share) { Icon(Icons.Outlined.Share, shareTitle) }
            }
        }
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                InstructionCard(s)
                TripProgress(s)
                NextStep(s)
                StopProgress(s)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = share, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(shareTitle)
                    }
                    Button(
                        onClick = { vm.stop(); onBack() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.stop_directions))
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionCard(s: GuidanceState) {
    val now by rememberNow()
    val urgent = s.getOffAlerted && s.phase == GuidancePhase.ON_BOARD
    val bg = when {
        urgent -> MaterialTheme.colorScheme.errorContainer
        s.readyAlerted && s.phase == GuidancePhase.ON_BOARD -> MaterialTheme.colorScheme.tertiaryContainer
        s.phase == GuidancePhase.ARRIVED -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(color = bg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            when (s.phase) {
                GuidancePhase.WALK -> {
                    val leg = s.leg as TripLeg.Walk
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, null, Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.walk_to, leg.toName.ifBlank { stringResource(R.string.destination) }),
                                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            val d = s.distanceToTargetM
                            Text(
                                if (d != null) Geo.formatDistance(d.toDouble()) + " · " + stringResource(R.string.walk_minutes, Geo.walkMinutes(d.toDouble()))
                                else stringResource(R.string.waiting_gps),
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    val nextBus = s.trip.legs.getOrNull(s.legIndex + 1) as? TripLeg.Bus
                    if (nextBus != null) {
                        val at = s.busEstimate?.eta ?: nextBus.start
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            LineBadge(nextBus.shortName, nextBus.operatorRef)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.bus_leaves_in, minutesText(at, now), TimeUtil.formatTime(at)),
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                            )
                            if (s.busEstimate != null) LiveTag()
                        }
                        val walkLeft = s.distanceToTargetM?.let { Geo.walkSeconds(it.toDouble()) }
                        if (walkLeft != null && Instant.now().plusSeconds(walkLeft) > at) Text(
                            stringResource(R.string.hurry_up), color = Warning, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                GuidancePhase.WAIT -> {
                    val leg = s.leg as TripLeg.Bus
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineBadge(leg.shortName, leg.operatorRef, large = true)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.wait_at, leg.from.name), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text(stringResource(R.string.toward, leg.headsign), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    val est = s.busEstimate
                    val at = est?.eta ?: leg.start
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text(
                            minutesText(at, now),
                            fontSize = 34.sp, fontWeight = FontWeight.Black,
                            color = if (est != null) liveColor() else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            if (est != null) LiveTag() else Text(stringResource(R.string.planned_at, TimeUtil.formatTime(leg.start)))
                            Text(
                                if (est != null) stringResource(R.string.bus_distance, Geo.formatDistance(est.metersAway.toDouble()), agoText(est.recordedAt, now))
                                else stringResource(R.string.bus_not_live_yet),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                GuidancePhase.ON_BOARD -> {
                    val leg = s.leg as TripLeg.Bus
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineBadge(leg.shortName, leg.operatorRef, large = true)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.get_off_at, leg.to.name), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    }
                    if (urgent) Text(stringResource(R.string.alert_get_off_title), fontSize = 34.sp, fontWeight = FontWeight.Black, color = Warning)
                    else s.stopsLeft?.let { left ->
                        Text(pluralStringResource(R.plurals.stops_left, left, left), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    } ?: Text(stringResource(R.string.waiting_gps))
                    s.distanceToTargetM?.let {
                        Text(Geo.formatDistance(it.toDouble()) + " · " + stringResource(R.string.eta_at, TimeUtil.formatTime(s.etaDestination ?: leg.end)))
                    }
                }
                GuidancePhase.ARRIVED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.alert_arrived_title), style = MaterialTheme.typography.headlineSmall)
                        Text(s.finalDestinationName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** Step x of y + overall progress bar + arrival time. */
@Composable
private fun TripProgress(s: GuidanceState) {
    val now by rememberNow(5000)
    val total = (s.trip.arrival.epochSecond - s.trip.departure.epochSecond).coerceAtLeast(1)
    val done = (now.epochSecond - s.trip.departure.epochSecond).coerceIn(0, total)
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row {
            Text(
                stringResource(R.string.step_of, s.legIndex + 1, s.trip.legs.size),
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.final_eta, s.finalDestinationName.ifBlank { stringResource(R.string.destination) },
                    TimeUtil.formatTime(s.etaDestination ?: s.trip.arrival)),
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        LinearProgressIndicator(
            progress = { if (s.phase == GuidancePhase.ARRIVED) 1f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun NextStep(s: GuidanceState) {
    val next = s.trip.legs.getOrNull(s.legIndex + 1) ?: return
    if (s.phase == GuidancePhase.ARRIVED) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.then), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        when (next) {
            is TripLeg.Walk -> {
                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.leg_walk, Geo.formatDistance(next.distanceM.toDouble()), next.toName.ifBlank { stringResource(R.string.destination) }),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            is TripLeg.Bus -> {
                LineBadge(next.shortName, next.operatorRef)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.leg_board_at_time, next.from.name, TimeUtil.formatTime(next.start)),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Stops of the current (or next) bus ride: passed ones grey, the next one bold, yours highlighted. */
@Composable
private fun StopProgress(s: GuidanceState) {
    val leg = (s.leg as? TripLeg.Bus) ?: (s.trip.legs.getOrNull(s.legIndex + 1) as? TripLeg.Bus) ?: return
    if (leg.stops.size < 2 || s.phase == GuidancePhase.ARRIVED) return
    val onBoard = s.phase == GuidancePhase.ON_BOARD && s.leg == leg
    val nextIdx = if (onBoard) (leg.stops.size - (s.stopsLeft ?: leg.stops.size)).coerceIn(0, leg.stops.lastIndex) else 0
    val color = Color(Operators.color(leg.operatorRef))
    val listState = rememberLazyListState()
    LaunchedEffect(nextIdx) { listState.animateScrollToItem((nextIdx - 1).coerceAtLeast(0)) }
    HorizontalDivider(Modifier.padding(top = 4.dp))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp), state = listState) {
        itemsIndexed(leg.stops, key = { _, st -> "${st.sequence}-${st.stopCode}" }) { i, st ->
            val passed = onBoard && i < nextIdx
            val isNext = onBoard && i == nextIdx
            val isMine = i == leg.stops.lastIndex
            Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(if (passed) MaterialTheme.colorScheme.outlineVariant else color))
                    Box(
                        Modifier.size(if (isMine || isNext) 12.dp else 8.dp).clip(CircleShape)
                            .background(if (passed) MaterialTheme.colorScheme.outlineVariant else if (isMine) Warning else color)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    st.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isNext || isMine) FontWeight.Bold else FontWeight.Normal,
                    color = if (passed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                Text(TimeUtil.formatTime(st.arrival), style = MaterialTheme.typography.labelMedium,
                    color = if (passed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GuidanceMap(s: GuidanceState, modifier: Modifier) {
    var controller by remember { mutableStateOf<BusMapController?>(null) }
    var fitted by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val start = s.user ?: s.trip.legs.first().path.firstOrNull() ?: return
    val focusLeg = (s.leg as? TripLeg.Bus) ?: (s.trip.legs.getOrNull(s.legIndex + 1) as? TripLeg.Bus)

    LaunchedEffect(controller, s.trip) {
        val c = controller ?: return@LaunchedEffect
        c.setTrip(s.trip.legs.map { leg ->
            when (leg) {
                is TripLeg.Walk -> MapPolyline(leg.path, 0xFF3F4949.toInt(), dashed = true)
                is TripLeg.Bus -> MapPolyline(leg.path, Operators.color(leg.operatorRef), dashed = false)
            }
        })
        c.setPin(s.trip.legs.last().path.lastOrNull())
        if (!fitted) {
            fitted = true
            c.fitTo(s.trip.legs.flatMap { it.path } + listOfNotNull(s.user), with(density) { 48.dp.roundToPx() })
        }
    }
    LaunchedEffect(controller, focusLeg) {
        val c = controller ?: return@LaunchedEffect
        val leg = focusLeg
        c.setRoute(
            points = emptyList(),
            stops = leg?.stops?.map { Stop(it.stopCode, it.name, it.city, it.lat, it.lon) }.orEmpty(),
            color = leg?.operatorRef?.let { Operators.color(it) },
        )
    }
    LaunchedEffect(controller, s.user, follow, s.phase) {
        val c = controller ?: return@LaunchedEffect
        c.setUser(s.user)
        val u = s.user ?: return@LaunchedEffect
        if (follow && fitted) c.moveTo(u, if (s.phase == GuidancePhase.ON_BOARD) 15.5 else 16.5)
    }
    LaunchedEffect(controller, s.busPosition) {
        val c = controller ?: return@LaunchedEffect
        val p = s.busPosition
        val leg = focusLeg
        c.setBuses(
            if (p == null || leg == null) emptyList()
            else listOf(
                LiveBus(
                    vehicleRef = s.busEstimate?.vehicleRef ?: "bus", lineRef = leg.lineRef ?: 0, operatorRef = leg.operatorRef,
                    lat = p.lat, lon = p.lon, bearing = 0, speedKmh = 0, recordedAt = s.busEstimate?.recordedAt ?: Instant.now(),
                    scheduledStart = leg.rideStart, distanceFromStartM = null, siriRideId = null,
                    line = null, labelOverride = leg.shortName,
                ),
            )
        )
    }
    Box(modifier) {
        BusMapView(
            initialCenter = start,
            initialZoom = 15.0,
            onControllerReady = { controller = it },
            modifier = Modifier.fillMaxSize(),
            onUserGesture = { follow = false },
        )
        if (!follow) SmallFloatingActionButton(
            onClick = { follow = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        ) { Icon(Icons.Outlined.MyLocation, stringResource(R.string.recenter)) }
    }
}
