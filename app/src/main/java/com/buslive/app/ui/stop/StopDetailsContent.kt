package com.buslive.app.ui.stop

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buslive.app.R
import com.buslive.app.domain.model.Arrival
import com.buslive.app.domain.util.TimeUtil
import com.buslive.app.ui.common.ErrorState
import com.buslive.app.ui.common.LineBadge
import com.buslive.app.ui.common.LiveTag
import com.buslive.app.ui.common.LoadingState
import com.buslive.app.ui.common.MessageState
import com.buslive.app.ui.common.agoText
import com.buslive.app.ui.common.minutesText
import com.buslive.app.ui.common.operatorName
import com.buslive.app.ui.common.rememberNow
import com.buslive.app.ui.theme.liveColor

data class StopActions(
    val onLineSchedule: (lineRef: Long) -> Unit,
    val onShowLineOnMap: ((lineRef: Long) -> Unit)?,
    val onDirections: (com.buslive.app.domain.model.Stop) -> Unit,
    val onShowOnMap: (() -> Unit)? = null,
)

@Composable
fun StopHeader(state: StopDetailsState, onToggleFavorite: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val now by rememberNow()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                state.stop?.name ?: stringResource(R.string.stop_code_title, state.code ?: 0),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildString {
                state.stop?.city?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                append(stringResource(R.string.stop_code, state.code ?: 0))
            }
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.lastUpdated?.let {
                Text(
                    stringResource(R.string.updated_ago, agoText(it, now)) +
                        if (state.liveCount > 0) " · " + stringResource(R.string.live_buses_count, state.liveCount) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (state.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(if (state.isFavorite) R.string.cd_unfavorite else R.string.cd_favorite),
                tint = if (state.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

@Composable
fun StopDetailsList(
    state: StopDetailsState,
    actions: StopActions,
    onRetry: () -> Unit,
    onRemind: (Arrival) -> Boolean,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val now by rememberNow()
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingRemind by remember { mutableStateOf<Arrival?>(null) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        pendingRemind?.let { a ->
            val ok = onRemind(a)
            Toast.makeText(context, if (ok) R.string.reminder_set else R.string.reminder_too_late, Toast.LENGTH_SHORT).show()
        }
        pendingRemind = null
    }

    LazyColumn(modifier = modifier) {
        if (header != null) item { header() }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { state.stop?.let(actions.onDirections) },
                    label = { Text(stringResource(R.string.directions_here)) },
                    leadingIcon = { Icon(Icons.Outlined.Directions, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
                actions.onShowOnMap?.let {
                    AssistChip(
                        onClick = it,
                        label = { Text(stringResource(R.string.show_on_map)) },
                        leadingIcon = { Icon(Icons.Outlined.Map, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                }
            }
        }
        when {
            state.lines.isEmpty() && state.loading -> item { LoadingState(text = stringResource(R.string.loading_arrivals)) }
            state.lines.isEmpty() && state.error != null -> item { ErrorState(state.error, onRetry) }
            state.lines.isEmpty() && state.lastUpdated != null -> item {
                MessageState(Icons.Outlined.DirectionsBus, stringResource(R.string.no_arrivals))
            }
        }
        items(state.lines, key = { it.lineRef }) { line ->
            val first = line.next.first()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = if (expanded == line.lineRef) null else line.lineRef }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(line.shortName, line.operatorRef)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.to_destination, line.destination),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            operatorName(line.operatorRef, line.agencyName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            minutesText(first.best, now),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (first.live != null) liveColor() else MaterialTheme.colorScheme.onSurface,
                        )
                        if (first.live != null) LiveTag()
                        else Text(
                            stringResource(R.string.planned_at, TimeUtil.formatTime(first.planned)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (line.next.size > 1) {
                    Text(
                        stringResource(
                            R.string.then_at,
                            line.next.drop(1).joinToString(", ") { a ->
                                TimeUtil.formatTime(a.best) + if (a.live != null) "•" else ""
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 52.dp, top = 2.dp),
                    )
                }
                AnimatedVisibility(expanded == line.lineRef) {
                    Row(
                        Modifier.padding(start = 44.dp, top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    pendingRemind = first
                                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val ok = onRemind(first)
                                    Toast.makeText(context, if (ok) R.string.reminder_set else R.string.reminder_too_late, Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text(stringResource(R.string.remind_me)) },
                            leadingIcon = { Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        )
                        AssistChip(
                            onClick = { actions.onLineSchedule(line.lineRef) },
                            label = { Text(stringResource(R.string.schedule)) },
                            leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        )
                        actions.onShowLineOnMap?.let { show ->
                            AssistChip(
                                onClick = { show(line.lineRef) },
                                label = { Text(stringResource(R.string.on_map)) },
                                leadingIcon = { Icon(Icons.Outlined.Map, null, Modifier.size(AssistChipDefaults.IconSize)) },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        item { Spacer(Modifier.size(16.dp)) }
    }
}
