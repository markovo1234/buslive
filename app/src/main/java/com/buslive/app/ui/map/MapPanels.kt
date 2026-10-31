package com.buslive.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buslive.app.R
import com.buslive.app.data.repo.LiveRepository
import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.domain.model.Line
import com.buslive.app.domain.model.LiveBus
import com.buslive.app.domain.model.PlaceKind
import com.buslive.app.domain.model.RideStop
import com.buslive.app.domain.util.TimeUtil
import com.buslive.app.ui.common.ErrorState
import com.buslive.app.ui.common.LineBadge
import com.buslive.app.ui.common.LiveTag
import com.buslive.app.ui.common.LoadingState
import com.buslive.app.ui.common.agoText
import com.buslive.app.ui.common.operatorName
import com.buslive.app.ui.common.rememberNow
import com.buslive.app.ui.theme.Warning
import com.buslive.app.ui.theme.liveColor

@Composable
fun BusPanel(
    bus: LiveBus?,
    detail: BusDetail?,
    following: Boolean,
    onFollow: () -> Unit,
    onShowLine: (Line) -> Unit,
    onSchedule: (Long) -> Unit,
    onAlertAt: (RideStop) -> Unit,
    onClose: () -> Unit,
) {
    val now by rememberNow()
    Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        PanelHandle()
        if (bus == null) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bus_gone), modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
            return@Column
        }
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            LineBadge(bus.shortName, bus.operatorRef, large = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    bus.line?.destination?.let { stringResource(R.string.to_destination, it) } ?: stringResource(R.string.unknown_line),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    operatorName(bus.operatorRef, bus.line?.agencyName) + " · " + stringResource(R.string.vehicle, bus.vehicleRef),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
        }
        val stale = bus.ageSeconds(now) > LiveRepository.FADE_AFTER_S
        Text(
            stringResource(R.string.gps_age, agoText(bus.recordedAt, now)) + " · " +
                stringResource(R.string.speed_kmh, bus.speedKmh) +
                (if (stale) " · " + stringResource(R.string.stale_position) else ""),
            style = MaterialTheme.typography.labelMedium,
            color = if (stale) Warning else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onFollow) {
                Icon(Icons.Outlined.GpsFixed, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (following) R.string.following else R.string.follow_bus))
            }
            bus.line?.let { line ->
                AssistChip(
                    onClick = { onShowLine(line) },
                    label = { Text(stringResource(R.string.show_line)) },
                    leadingIcon = { Icon(Icons.Outlined.Route, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
                AssistChip(
                    onClick = { onSchedule(line.lineRef) },
                    label = { Text(stringResource(R.string.schedule)) },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
        Text(
            stringResource(R.string.next_stops),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
        )
        when {
            detail == null || (detail.loading && detail.nextStops.isEmpty()) -> LoadingState()
            detail.error != null && detail.nextStops.isEmpty() -> ErrorState(detail.error, onRetry = null)
            detail.nextStops.isEmpty() -> Text(
                stringResource(R.string.no_route_info),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(Modifier.heightIn(max = 260.dp)) {
                items(detail.nextStops, key = { it.first.sequence }) { (stop, est) ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stop.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.planned_at, TimeUtil.formatTime(stop.arrival)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (est != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "≈ " + TimeUtil.formatTime(est.eta),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = liveColor(),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    LiveTag()
                                }
                            }
                        }
                        IconButton(onClick = { onAlertAt(stop) }) {
                            Icon(Icons.Outlined.NotificationAdd, stringResource(R.string.alert_me_at, stop.name))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinPanel(
    point: GeoPoint,
    onDirections: (name: String) -> Unit,
    onSave: (PlaceKind, String) -> Unit,
    onClose: () -> Unit,
) {
    val name = stringResource(R.string.dropped_pin)
    Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
        PanelHandle()
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "%.5f, %.5f".format(java.util.Locale.ROOT, point.lat, point.lon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onDirections(name) }) {
                Icon(Icons.Outlined.Directions, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.directions_here))
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { onSave(PlaceKind.HOME, name) },
                label = { Text(stringResource(R.string.save_home)) },
                leadingIcon = { Icon(Icons.Outlined.Home, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
            AssistChip(
                onClick = { onSave(PlaceKind.WORK, name) },
                label = { Text(stringResource(R.string.save_work)) },
                leadingIcon = { Icon(Icons.Outlined.Work, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
            AssistChip(
                onClick = { onSave(PlaceKind.CUSTOM, name) },
                label = { Text(stringResource(R.string.save_place)) },
                leadingIcon = { Icon(Icons.Outlined.Place, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
    }
}
