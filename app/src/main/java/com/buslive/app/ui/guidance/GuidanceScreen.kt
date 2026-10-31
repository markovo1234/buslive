package com.buslive.app.ui.guidance

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buslive.app.R
import com.buslive.app.domain.model.LiveBus
import com.buslive.app.domain.model.Operators
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidanceScreen(onBack: () -> Unit, vm: GuidanceViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.live_directions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (state != null) IconButton(onClick = {
                        vm.shareText()?.let { text ->
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_trip)))
                        }
                    }) { Icon(Icons.Outlined.Share, stringResource(R.string.share_trip)) }
                },
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                MessageState(Icons.Outlined.Navigation, stringResource(R.string.no_active_directions), Modifier.padding(top = 48.dp))
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { InstructionCard(s) }
            item { GuidanceMap(s) }
            itemsIndexed(s.trip.legs) { i, leg -> LegItem(leg, current = i == s.legIndex, done = i < s.legIndex) }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        vm.shareText()?.let { text ->
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_trip)))
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.share_trip))
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
            item {
                Text(
                    stringResource(R.string.guidance_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun InstructionCard(s: GuidanceState) {
    val now by rememberNow()
    val urgent = s.getOffAlerted && s.phase == GuidancePhase.ON_BOARD
    val container = when {
        urgent -> MaterialTheme.colorScheme.errorContainer
        s.readyAlerted && s.phase == GuidancePhase.ON_BOARD -> MaterialTheme.colorScheme.tertiaryContainer
        s.phase == GuidancePhase.ARRIVED -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            when (s.phase) {
                GuidancePhase.WALK -> {
                    val leg = s.leg as TripLeg.Walk
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, null, Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.walk_to, leg.toName.ifBlank { stringResource(R.string.destination) }),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    s.distanceToTargetM?.let { d ->
                        Text(
                            Geo.formatDistance(d.toDouble()) + " · " + stringResource(R.string.walk_minutes, Geo.walkMinutes(d.toDouble())),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    } ?: Text(stringResource(R.string.waiting_gps), style = MaterialTheme.typography.bodyLarge)
                    val nextBus = s.trip.legs.drop(s.legIndex + 1).filterIsInstance<TripLeg.Bus>().firstOrNull()
                    if (nextBus != null) Text(
                        stringResource(R.string.next_bus_leaves, nextBus.shortName, TimeUtil.formatTime(nextBus.start), minutesText(nextBus.start, now)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                GuidancePhase.WAIT -> {
                    val leg = s.leg as TripLeg.Bus
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineBadge(leg.shortName, leg.operatorRef, large = true)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.wait_at, leg.from.name), style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(6.dp))
                    val est = s.busEstimate
                    val at = est?.eta ?: leg.start
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            minutesText(at, now),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (est != null) liveColor() else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(10.dp))
                        if (est != null) LiveTag() else Text(stringResource(R.string.planned_at, TimeUtil.formatTime(leg.start)))
                    }
                    if (est != null) Text(
                        stringResource(R.string.bus_distance, Geo.formatDistance(est.metersAway.toDouble()), agoText(est.recordedAt, now)),
                        style = MaterialTheme.typography.bodyMedium,
                    ) else Text(stringResource(R.string.bus_not_live_yet), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.toward, leg.headsign), style = MaterialTheme.typography.bodyMedium)
                }
                GuidancePhase.ON_BOARD -> {
                    val leg = s.leg as TripLeg.Bus
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineBadge(leg.shortName, leg.operatorRef, large = true)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.get_off_at, leg.to.name), style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (urgent) {
                        Text(stringResource(R.string.alert_get_off_title), fontSize = 34.sp, fontWeight = FontWeight.Black, color = Warning)
                    } else s.stopsLeft?.let { left ->
                        Text(
                            pluralStringResource(R.plurals.stops_left, left, left),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    } ?: Text(stringResource(R.string.waiting_gps), style = MaterialTheme.typography.bodyLarge)
                    s.distanceToTargetM?.let {
                        Text(Geo.formatDistance(it.toDouble()) + " · " + stringResource(R.string.eta_at, TimeUtil.formatTime(s.etaDestination ?: leg.end)))
                    }
                }
                GuidancePhase.ARRIVED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.alert_arrived_title), style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(s.finalDestinationName, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (s.phase != GuidancePhase.ARRIVED) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.final_eta, s.finalDestinationName.ifBlank { stringResource(R.string.destination) },
                        TimeUtil.formatTime(s.etaDestination ?: s.trip.arrival)),
                    style = MaterialTheme.typography.labelLarge,
                )
                s.lastGpsAt?.let {
                    Text(stringResource(R.string.gps_age, agoText(it, now)), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun GuidanceMap(s: GuidanceState) {
    var controller by remember { mutableStateOf<BusMapController?>(null) }
    var fitted by remember { mutableStateOf(false) }
    var userMoved by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val start = s.user ?: s.trip.legs.first().path.firstOrNull() ?: return
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
            c.fitTo(s.trip.legs.flatMap { it.path } + listOfNotNull(s.user), with(density) { 40.dp.roundToPx() })
        }
    }
    LaunchedEffect(controller, s.user) {
        val c = controller ?: return@LaunchedEffect
        c.setUser(s.user)
        if (fitted && !userMoved && s.phase == GuidancePhase.ON_BOARD) s.user?.let { c.moveTo(it) }
    }
    LaunchedEffect(controller, s.busPosition) {
        val c = controller ?: return@LaunchedEffect
        val leg = s.leg as? TripLeg.Bus
        val p = s.busPosition
        c.setBuses(
            if (p == null || leg == null) emptyList()
            else listOf(
                LiveBus(
                    vehicleRef = s.busEstimate?.vehicleRef ?: "bus", lineRef = leg.lineRef ?: 0, operatorRef = leg.operatorRef,
                    lat = p.lat, lon = p.lon, bearing = 0, speedKmh = 0, recordedAt = s.busEstimate?.recordedAt ?: Instant.now(),
                    scheduledStart = leg.rideStart, distanceFromStartM = null, siriRideId = null, line = null,
                )
            )
        )
    }
    Card(Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 12.dp), shape = RoundedCornerShape(16.dp)) {
        BusMapView(
            initialCenter = start,
            initialZoom = 15.0,
            onControllerReady = { controller = it },
            modifier = Modifier.fillMaxSize(),
            onUserGesture = { userMoved = true },
        )
    }
}

@Composable
private fun LegItem(leg: TripLeg, current: Boolean, done: Boolean) {
    val color = when {
        current -> MaterialTheme.colorScheme.primary
        done -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(TimeUtil.formatTime(leg.start), style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.width(52.dp))
        when (leg) {
            is TripLeg.Walk -> {
                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.leg_walk, Geo.formatDistance(leg.distanceM.toDouble()), leg.toName.ifBlank { stringResource(R.string.destination) }),
                    color = color,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                )
            }
            is TripLeg.Bus -> {
                if (done) Icon(Icons.Outlined.DirectionsBus, null, tint = color) else LineBadge(leg.shortName, leg.operatorRef)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.leg_ride, leg.stopsCount, leg.to.name, TimeUtil.formatTime(leg.end)),
                    color = color,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
