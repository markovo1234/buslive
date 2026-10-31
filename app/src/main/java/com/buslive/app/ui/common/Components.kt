package com.buslive.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buslive.app.R
import com.buslive.app.domain.model.AppError
import com.buslive.app.domain.model.Operators
import com.buslive.app.ui.theme.liveColor
import kotlinx.coroutines.delay
import java.time.Instant

/** Line number badge in the operator's color. */
@Composable
fun LineBadge(
    shortName: String,
    operatorRef: Int,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val color = Color(Operators.color(operatorRef))
    val desc = stringResource(R.string.cd_line, shortName)
    Box(
        modifier = modifier
            .widthIn(min = if (large) 52.dp else 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = if (large) 6.dp else 3.dp)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shortName,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (large) 20.sp else 15.sp,
            maxLines = 1,
        )
    }
}

/** Small green "LIVE" tag, optionally with "estimate". */
@Composable
fun LiveTag(modifier: Modifier = Modifier, estimate: Boolean = true) {
    val c = liveColor()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(c.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c))
        Spacer(Modifier.size(4.dp))
        Text(
            text = if (estimate) stringResource(R.string.live_estimate) else stringResource(R.string.live),
            color = c,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Ticks every [periodMs] so "X sec ago" texts stay fresh. */
@Composable
fun rememberNow(periodMs: Long = 1000L): State<Instant> {
    val state = remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            state.value = Instant.now()
        }
    }
    return state
}

/** "updated 12 sec ago" / "3 min ago". */
@Composable
fun agoText(then: Instant, now: Instant): String {
    val s = (now.epochSecond - then.epochSecond).coerceAtLeast(0)
    return if (s < 60) stringResource(R.string.ago_seconds, s.toInt())
    else stringResource(R.string.ago_minutes, (s / 60).toInt())
}

/** "now" / "4 min" / "1 h 5 min" until an instant. */
@Composable
fun minutesText(target: Instant, now: Instant): String {
    val m = Math.floorDiv(target.epochSecond - now.epochSecond + 30, 60L)
    return when {
        m <= 0 -> stringResource(R.string.now)
        m < 60 -> pluralStringResource(R.plurals.minutes_short, m.toInt(), m.toInt())
        else -> stringResource(R.string.hours_minutes, (m / 60).toInt(), (m % 60).toInt())
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WifiOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.offline_banner), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun errorMessage(error: Throwable): String = when (error) {
    is AppError.NoInternet -> stringResource(R.string.error_no_internet)
    is AppError.ApiDown -> stringResource(R.string.error_api_down)
    is AppError.NotFound -> stringResource(R.string.error_not_found)
    else -> stringResource(R.string.error_unknown)
}

@Composable
fun ErrorState(error: Throwable, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val icon = when (error) {
        is AppError.NoInternet -> Icons.Outlined.WifiOff
        is AppError.ApiDown -> Icons.Outlined.CloudOff
        else -> Icons.Outlined.ErrorOutline
    }
    MessageState(icon = icon, text = errorMessage(error), modifier = modifier) {
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
fun MessageState(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        extra()
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, text: String? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (text != null) {
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FullScreenCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** True when UI language is Hebrew. */
@Composable
fun isHebrewUi(): Boolean {
    val lang = LocalConfiguration.current.locales[0]?.language
    return lang == "he" || lang == "iw"
}

@Composable
fun operatorName(operatorRef: Int, fallback: String? = null): String =
    Operators.name(operatorRef, isHebrewUi(), fallback)

val SheetCorner: Dp = 20.dp
