package com.buslive.app.service.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.buslive.app.MainActivity
import com.buslive.app.R
import com.buslive.app.data.prefs.SettingsRepository
import com.buslive.app.data.repo.FavoritesRepository
import com.buslive.app.data.repo.LiveRepository
import com.buslive.app.data.repo.ScheduleRepository
import com.buslive.app.data.repo.StaticDataRepository
import com.buslive.app.domain.ArrivalEstimator
import com.buslive.app.domain.model.FavoriteType
import com.buslive.app.domain.model.Operators
import com.buslive.app.domain.util.TimeUtil
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun settings(): SettingsRepository
    fun favorites(): FavoritesRepository
    fun staticData(): StaticDataRepository
    fun schedule(): ScheduleRepository
    fun live(): LiveRepository
}

private data class WidgetRow(
    val line: String,
    val color: Int,
    val destination: String,
    val time: String,
    val minutes: Long,
    val live: Boolean,
)

private data class WidgetData(
    val stopName: String? = null,
    val rows: List<WidgetRow> = emptyList(),
    val updatedAt: String = "",
    val noStop: Boolean = false,
    val error: Boolean = false,
)

/** Home screen widget: next arrivals at one favorite stop (chosen in Favorites → ⋮ → Show on widget). */
class ArrivalsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent {
            GlanceTheme { Content(context, data) }
        }
    }

    private suspend fun load(context: Context): WidgetData {
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val code = ep.settings().current().widgetStopCode
            ?: ep.favorites().favorites.first().firstOrNull { it.type == FavoriteType.STOP }?.key?.toIntOrNull()
            ?: return WidgetData(noStop = true)
        val stopName = ep.staticData().stop(code)?.name ?: "#$code"
        val now = Instant.now()
        return runCatching {
            val arrivals = ep.schedule().arrivals(code, from = now.minusSeconds(60), to = now.plus(Duration.ofHours(2)), maxAgeMs = 0)
            val soon = arrivals.filter { it.planned < now.plus(Duration.ofMinutes(30)) }.map { it.lineRef }.distinct().take(3)
            val buses = soon.flatMap { runCatching { ep.live().busesForLine(it) }.getOrDefault(emptyList()) }
            val rows = ArrivalEstimator.attachLive(arrivals, buses, now)
                .filter { it.best >= now.minusSeconds(30) }
                .groupBy { it.lineRef }
                .map { (_, list) -> list.minBy { it.best } }
                .sortedBy { it.best }
                .take(5)
                .map { a ->
                    WidgetRow(
                        line = a.shortName,
                        color = Operators.color(a.operatorRef),
                        destination = a.destination,
                        time = TimeUtil.formatTime(a.best),
                        minutes = Duration.between(now, a.best).toMinutes().coerceAtLeast(0),
                        live = a.live != null,
                    )
                }
            WidgetData(stopName, rows, TimeUtil.formatTime(now))
        }.getOrElse { WidgetData(stopName, updatedAt = TimeUtil.formatTime(now), error = true) }
    }

    @Composable
    private fun Content(context: Context, data: WidgetData) {
        val fg = GlanceTheme.colors.onSurface
        val sub = GlanceTheme.colors.onSurfaceVariant
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = data.stopName ?: context.getString(R.string.app_name),
                    style = TextStyle(color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    text = "⟳ " + data.updatedAt,
                    style = TextStyle(color = sub, fontSize = 12.sp),
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>()).padding(start = 8.dp),
                )
            }
            Spacer(GlanceModifier.padding(top = 6.dp))
            when {
                data.noStop -> Text(context.getString(R.string.widget_no_stop), style = TextStyle(color = sub, fontSize = 13.sp))
                data.error && data.rows.isEmpty() -> Text(context.getString(R.string.error_no_internet), style = TextStyle(color = sub, fontSize = 13.sp))
                data.rows.isEmpty() -> Text(context.getString(R.string.no_arrivals_2h), style = TextStyle(color = sub, fontSize = 13.sp))
                else -> data.rows.forEach { r ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = GlanceModifier.width(44.dp).background(Color(r.color)).cornerRadius(6.dp).padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                r.line,
                                style = TextStyle(color = ColorProvider(Color.White, Color.White), fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                maxLines = 1,
                            )
                        }
                        Text(
                            r.destination,
                            style = TextStyle(color = fg, fontSize = 13.sp),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight().padding(horizontal = 8.dp),
                        )
                        Text(
                            (if (r.live) "● " else "") + r.time,
                            style = TextStyle(
                                color = if (r.live) ColorProvider(Color(0xFF1B9E4B), Color(0xFF5BD68A)) else fg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            ),
                        )
                    }
                }
            }
        }
    }

    companion object {
        suspend fun refreshAll(context: Context) = ArrivalsWidget().updateAll(context)
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ArrivalsWidget().update(context, glanceId)
    }
}

class ArrivalsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ArrivalsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateWorker.cancel(context)
    }
}
