package com.buslive.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.buslive.app.data.repo.LiveRepository
import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.domain.model.LiveBus
import com.buslive.app.domain.model.Operators
import com.buslive.app.domain.model.Stop
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconColor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOpacity
import org.maplibre.android.style.layers.PropertyFactory.textRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.time.Instant
import kotlin.math.abs

/** A drawable part of a trip preview. */
data class MapPolyline(val points: List<GeoPoint>, val color: Int, val dashed: Boolean)

/**
 * Owns all MapLibre sources/layers for BusLive. Created after each style load.
 * Bus markers are SDF icons (tinted by operator color), rotated by bearing, with the line number on top,
 * and are animated smoothly between updates.
 */
class BusMapController(
    private val context: Context,
    val map: MapLibreMap,
    private val style: Style,
    private val scope: CoroutineScope,
    private val dark: Boolean,
) {
    private data class Shown(val lat: Double, val lon: Double, val bearing: Float)

    private var shown: MutableMap<String, Shown> = HashMap()
    private var lastBuses: List<LiveBus> = emptyList()
    private var animJob: Job? = null
    private var followId: String? = null

    init {
        addImages()
        addSourcesAndLayers()
    }

    // ------------------------------------------------------------------ setup

    private fun addImages() {
        style.addImage(IMG_BUS, busMarkerBitmap(), true)
    }

    /** White teardrop: circle with a tip pointing up (north before rotation). Used as an SDF image. */
    private fun busMarkerBitmap(): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (44 * d).toInt()
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
        val cx = size / 2f
        val cy = size / 2f
        val r = 14 * d
        c.drawCircle(cx, cy, r, p)
        val tip = Path().apply {
            moveTo(cx, 2 * d)
            lineTo(cx - 8 * d, cy - r + 6 * d)
            lineTo(cx + 8 * d, cy - r + 6 * d)
            close()
        }
        c.drawPath(tip, p)
        return bmp
    }

    private fun addSourcesAndLayers() {
        style.addSource(GeoJsonSource(SRC_ROUTE))
        style.addSource(GeoJsonSource(SRC_ROUTE_STOPS))
        style.addSource(GeoJsonSource(SRC_TRIP))
        style.addSource(
            GeoJsonSource(
                SRC_STOPS,
                GeoJsonOptions().withCluster(true).withClusterMaxZoom(15).withClusterRadius(42),
            )
        )
        style.addSource(GeoJsonSource(SRC_USER))
        style.addSource(GeoJsonSource(SRC_PIN))
        style.addSource(GeoJsonSource(SRC_BUSES))

        val stopStroke = if (dark) "#80D4DB" else "#00696F"
        val stopFill = if (dark) "#1B2121" else "#FFFFFF"

        // Route of the selected line.
        style.addLayer(
            LineLayer(L_ROUTE, SRC_ROUTE).withProperties(
                lineColor(get("color")),
                lineWidth(interpolate(linear(), zoom(), stop(10, 3f), stop(16, 7f))),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineCap(Property.LINE_CAP_ROUND),
                lineOpacity(0.85f),
            )
        )
        // Trip planner preview (bus = solid, walk = dashed).
        style.addLayer(
            LineLayer(L_TRIP_BUS, SRC_TRIP).withFilter(Expression.eq(get("kind"), literal("bus"))).withProperties(
                lineColor(get("color")),
                lineWidth(6f),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineCap(Property.LINE_CAP_ROUND),
            )
        )
        style.addLayer(
            LineLayer(L_TRIP_WALK, SRC_TRIP).withFilter(Expression.eq(get("kind"), literal("walk"))).withProperties(
                lineColor(if (dark) "#E0E3E3" else "#3F4949"),
                lineWidth(4f),
                lineDasharray(arrayOf(0.6f, 1.6f)),
                lineCap(Property.LINE_CAP_ROUND),
            )
        )
        style.addLayer(
            CircleLayer(L_ROUTE_STOPS, SRC_ROUTE_STOPS).withProperties(
                circleRadius(interpolate(linear(), zoom(), stop(11, 2.5f), stop(16, 6f))),
                circleColor(stopFill),
                circleStrokeColor(get("color")),
                circleStrokeWidth(2.5f),
            )
        )
        // Stops: clusters at mid zoom, single stops when zoomed in. Hidden below zoom 13.
        style.addLayer(
            CircleLayer(L_STOP_CLUSTER, SRC_STOPS).withFilter(has("point_count")).withProperties(
                circleColor(if (dark) "#324B4D" else "#CCE8E9"),
                circleStrokeColor(stopStroke),
                circleStrokeWidth(1.5f),
                circleRadius(
                    Expression.step(get("point_count"), literal(11f), stop(10, 14f), stop(40, 18f))
                ),
                circleOpacity(0.92f),
            ).apply { minZoom = MIN_STOP_ZOOM }
        )
        style.addLayer(
            SymbolLayer(L_STOP_CLUSTER_COUNT, SRC_STOPS).withFilter(has("point_count")).withProperties(
                textField(Expression.toString(get("point_count"))),
                textFont(arrayOf(FONT_BOLD)),
                textSize(11f),
                textColor(if (dark) "#CCE8E9" else "#051F21"),
                textAllowOverlap(true),
                textIgnorePlacement(true),
            ).apply { minZoom = MIN_STOP_ZOOM }
        )
        style.addLayer(
            CircleLayer(L_STOPS, SRC_STOPS).withFilter(Expression.not(has("point_count"))).withProperties(
                circleRadius(interpolate(linear(), zoom(), stop(13, 3.5f), stop(17, 7f))),
                circleColor(stopFill),
                circleStrokeColor(stopStroke),
                circleStrokeWidth(2f),
            ).apply { minZoom = MIN_STOP_ZOOM }
        )
        style.addLayer(
            SymbolLayer(L_STOP_NAMES, SRC_STOPS).withFilter(Expression.not(has("point_count"))).withProperties(
                textField(get("name")),
                textFont(arrayOf(FONT_REGULAR)),
                textSize(11f),
                textOffset(arrayOf(0f, 1.2f)),
                textAnchor(Property.TEXT_ANCHOR_TOP),
                textColor(if (dark) "#E0E3E3" else "#191C1C"),
                textHaloColor(if (dark) "#0F1415" else "#FFFFFF"),
                textHaloWidth(1.5f),
            ).apply { minZoom = 16.5f }
        )
        style.addLayer(
            CircleLayer(L_STOP_SELECTED, SRC_STOPS)
                .withFilter(Expression.eq(get("code"), literal(-1)))
                .withProperties(
                    circleRadius(11f),
                    circleColor("#00000000"),
                    circleStrokeColor("#E65100"),
                    circleStrokeWidth(3.5f),
                )
        )
        // User location.
        style.addLayer(
            CircleLayer(L_USER_HALO, SRC_USER).withProperties(
                circleRadius(16f), circleColor("#1A73E8"), circleOpacity(0.18f),
            )
        )
        style.addLayer(
            CircleLayer(L_USER, SRC_USER).withProperties(
                circleRadius(7f), circleColor("#1A73E8"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2.5f),
            )
        )
        // Long-press pin.
        style.addLayer(
            CircleLayer(L_PIN, SRC_PIN).withProperties(
                circleRadius(9f), circleColor("#D32F2F"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
            )
        )
        // Buses on top.
        style.addLayer(
            CircleLayer(L_BUS_SELECTED, SRC_BUSES)
                .withFilter(Expression.eq(get("id"), literal("")))
                .withProperties(
                    circleRadius(interpolate(linear(), zoom(), stop(10, 14f), stop(16, 22f))),
                    circleColor("#00000000"),
                    circleStrokeColor("#E65100"),
                    circleStrokeWidth(3.5f),
                )
        )
        style.addLayer(
            SymbolLayer(L_BUS, SRC_BUSES).withProperties(
                iconImage(IMG_BUS),
                iconColor(get("color")),
                iconRotate(get("bearing")),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconSize(interpolate(linear(), zoom(), stop(9, 0.5f), stop(13, 0.8f), stop(16, 1.0f))),
                iconOpacity(get("opacity")),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                textField(get("line")),
                textFont(arrayOf(FONT_BOLD)),
                textSize(interpolate(linear(), zoom(), stop(9, 8f), stop(13, 11f), stop(16, 13f))),
                textColor("#FFFFFF"),
                textOpacity(get("opacity")),
                textRotationAlignment(Property.TEXT_ROTATION_ALIGNMENT_VIEWPORT),
                textAllowOverlap(true),
                textIgnorePlacement(true),
            )
        )
    }

    // ------------------------------------------------------------------ data

    /**
     * Show buses. Moving buses glide from their old spot to the new one over ~1.2 s instead of jumping.
     */
    fun setBuses(buses: List<LiveBus>, now: Instant = Instant.now()) {
        lastBuses = buses
        val targets = buses.associate { b -> b.vehicleRef to Shown(b.lat, b.lon, b.bearing.toFloat()) }
        val from = HashMap(shown)
        animJob?.cancel()
        val moving = targets.any { (id, t) ->
            val s = from[id]
            s != null && (abs(s.lat - t.lat) > 1e-6 || abs(s.lon - t.lon) > 1e-6 || abs(s.bearing - t.bearing) > 0.5f)
        }
        if (!moving) {
            shown = HashMap(targets)
            render(buses, targets, now)
            followCamera(animated = true)
            return
        }
        followCamera(animated = true, durationMs = ANIM_MS.toInt())
        animJob = scope.launch {
            val start = System.currentTimeMillis()
            while (isActive) {
                val t = ((System.currentTimeMillis() - start).toFloat() / ANIM_MS).coerceIn(0f, 1f)
                val e = easeInOut(t)
                val frame = HashMap<String, Shown>(targets.size)
                for ((id, target) in targets) {
                    val s = from[id]
                    frame[id] = if (s == null) target else Shown(
                        lat = s.lat + (target.lat - s.lat) * e,
                        lon = s.lon + (target.lon - s.lon) * e,
                        bearing = lerpAngle(s.bearing, target.bearing, e),
                    )
                }
                shown = frame
                render(buses, frame, now)
                if (t >= 1f) break
                delay(FRAME_MS)
            }
        }
    }

    private fun render(buses: List<LiveBus>, pos: Map<String, Shown>, now: Instant) {
        val features = buses.mapNotNull { b ->
            val p = pos[b.vehicleRef] ?: return@mapNotNull null
            val props = JsonObject().apply {
                addProperty("id", b.vehicleRef)
                addProperty("line", b.shortName)
                addProperty("bearing", p.bearing)
                addProperty("color", colorHex(Operators.color(b.operatorRef)))
                val age = b.ageSeconds(now)
                addProperty("opacity", if (age > LiveRepository.FADE_AFTER_S) 0.45f else 1f)
            }
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat), props)
        }
        style.getSourceAs<GeoJsonSource>(SRC_BUSES)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun setSelectedBus(id: String?) {
        style.getLayerAs<CircleLayer>(L_BUS_SELECTED)?.setFilter(Expression.eq(get("id"), literal(id ?: "")))
    }

    fun setFollow(id: String?) {
        followId = id
        followCamera(animated = true)
    }

    private fun followCamera(animated: Boolean, durationMs: Int = 600) {
        val id = followId ?: return
        val b = lastBuses.firstOrNull { it.vehicleRef == id } ?: return
        val update = CameraUpdateFactory.newLatLng(LatLng(b.lat, b.lon))
        if (animated) map.easeCamera(update, durationMs) else map.moveCamera(update)
    }

    fun setStops(stops: List<Stop>) {
        val features = stops.map { s ->
            Feature.fromGeometry(Point.fromLngLat(s.lon, s.lat), JsonObject().apply {
                addProperty("code", s.code)
                addProperty("name", s.name)
            })
        }
        style.getSourceAs<GeoJsonSource>(SRC_STOPS)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun setSelectedStop(code: Int?) {
        style.getLayerAs<CircleLayer>(L_STOP_SELECTED)?.setFilter(Expression.eq(get("code"), literal(code ?: -1)))
    }

    /** Selected line: a polyline through its stops (the API has no shapes) + stop dots. */
    fun setRoute(points: List<GeoPoint>, stops: List<Stop>, color: Int?) {
        val hex = colorHex(color ?: 0xFF00696F.toInt())
        val line = if (points.size >= 2) listOf(
            Feature.fromGeometry(
                LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) }),
                JsonObject().apply { addProperty("color", hex) },
            )
        ) else emptyList()
        style.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(line))
        val stopFeatures = stops.map { s ->
            Feature.fromGeometry(Point.fromLngLat(s.lon, s.lat), JsonObject().apply {
                addProperty("code", s.code)
                addProperty("name", s.name)
                addProperty("color", hex)
            })
        }
        style.getSourceAs<GeoJsonSource>(SRC_ROUTE_STOPS)?.setGeoJson(FeatureCollection.fromFeatures(stopFeatures))
    }

    fun setTrip(lines: List<MapPolyline>) {
        val features = lines.filter { it.points.size >= 2 }.map { l ->
            Feature.fromGeometry(
                LineString.fromLngLats(l.points.map { Point.fromLngLat(it.lon, it.lat) }),
                JsonObject().apply {
                    addProperty("kind", if (l.dashed) "walk" else "bus")
                    addProperty("color", colorHex(l.color))
                },
            )
        }
        style.getSourceAs<GeoJsonSource>(SRC_TRIP)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun setUser(p: GeoPoint?) {
        val fc = if (p == null) FeatureCollection.fromFeatures(emptyList())
        else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)))
        style.getSourceAs<GeoJsonSource>(SRC_USER)?.setGeoJson(fc)
    }

    fun setPin(p: GeoPoint?) {
        val fc = if (p == null) FeatureCollection.fromFeatures(emptyList())
        else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)))
        style.getSourceAs<GeoJsonSource>(SRC_PIN)?.setGeoJson(fc)
    }

    // ------------------------------------------------------------------ camera + hit testing

    fun moveTo(p: GeoPoint, zoom: Double? = null, animated: Boolean = true) {
        val update = if (zoom != null) CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), zoom)
        else CameraUpdateFactory.newLatLng(LatLng(p.lat, p.lon))
        if (animated) map.animateCamera(update, 700) else map.moveCamera(update)
    }

    fun fitTo(points: List<GeoPoint>, paddingPx: Int) {
        if (points.isEmpty()) return
        if (points.size == 1) { moveTo(points[0], 15.0); return }
        val b = LatLngBounds.Builder().apply { points.forEach { include(LatLng(it.lat, it.lon)) } }.build()
        runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(b, paddingPx), 700) }
    }

    sealed interface Hit {
        data class Bus(val id: String) : Hit
        data class StopHit(val code: Int) : Hit
        data class Cluster(val at: LatLng) : Hit
    }

    fun hitTest(latLng: LatLng): Hit? {
        val pt: PointF = map.projection.toScreenLocation(latLng)
        val d = 18 * context.resources.displayMetrics.density
        val rect = RectF(pt.x - d, pt.y - d, pt.x + d, pt.y + d)
        map.queryRenderedFeatures(rect, L_BUS).firstOrNull()?.let { f ->
            f.getStringProperty("id")?.let { return Hit.Bus(it) }
        }
        map.queryRenderedFeatures(rect, L_STOPS, L_ROUTE_STOPS).firstOrNull()?.let { f ->
            f.getNumberProperty("code")?.toInt()?.let { return Hit.StopHit(it) }
        }
        map.queryRenderedFeatures(rect, L_STOP_CLUSTER).firstOrNull()?.let { return Hit.Cluster(latLng) }
        return null
    }

    fun dispose() {
        animJob?.cancel()
    }

    companion object {
        const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
        const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
        const val MIN_STOP_ZOOM = 13f
        private const val FONT_BOLD = "Noto Sans Bold"
        private const val FONT_REGULAR = "Noto Sans Regular"
        private const val ANIM_MS = 1200L
        private const val FRAME_MS = 33L

        private const val IMG_BUS = "bus-marker"
        private const val SRC_BUSES = "buses"
        private const val SRC_STOPS = "stops"
        private const val SRC_ROUTE = "route"
        private const val SRC_ROUTE_STOPS = "route-stops"
        private const val SRC_USER = "user"
        private const val SRC_PIN = "pin"
        private const val SRC_TRIP = "trip"
        private const val L_ROUTE = "route-line"
        private const val L_ROUTE_STOPS = "route-stops-layer"
        private const val L_TRIP_BUS = "trip-bus"
        private const val L_TRIP_WALK = "trip-walk"
        private const val L_STOP_CLUSTER = "stop-cluster"
        private const val L_STOP_CLUSTER_COUNT = "stop-cluster-count"
        private const val L_STOPS = "stops-layer"
        private const val L_STOP_NAMES = "stop-names"
        private const val L_STOP_SELECTED = "stop-selected"
        private const val L_USER_HALO = "user-halo"
        private const val L_USER = "user-dot"
        private const val L_PIN = "pin-layer"
        private const val L_BUS_SELECTED = "bus-selected"
        private const val L_BUS = "bus-layer"

        fun colorHex(argb: Int): String = String.format("#%06X", 0xFFFFFF and argb)

        private fun easeInOut(t: Float): Float = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).let { it * it } / 2

        private fun lerpAngle(a: Float, b: Float, t: Float): Float {
            var diff = (b - a) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f
            return (a + diff * t + 360f) % 360f
        }
    }
}
