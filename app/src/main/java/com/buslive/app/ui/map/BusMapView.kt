package com.buslive.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.buslive.app.domain.model.BBox
import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.ui.theme.LocalDarkTheme
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * MapLibre MapView in Compose, with OpenFreeMap tiles (liberty by day, dark by night).
 * Hands a fresh [BusMapController] to [onControllerReady] after every style load.
 */
@Composable
fun BusMapView(
    initialCenter: GeoPoint,
    initialZoom: Double,
    onControllerReady: (BusMapController?) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    onCameraIdle: (BBox, Double) -> Unit = { _, _ -> },
    onMapClick: (LatLng) -> Unit = {},
    onMapLongClick: (LatLng) -> Unit = {},
    onUserGesture: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dark = LocalDarkTheme.current
    val scope = rememberCoroutineScope()

    val idle by rememberUpdatedState(onCameraIdle)
    val click by rememberUpdatedState(onMapClick)
    val longClick by rememberUpdatedState(onMapLongClick)
    val gesture by rememberUpdatedState(onUserGesture)
    val ready by rememberUpdatedState(onControllerReady)

    val mapView = remember { MapView(context).also { it.onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var controller by remember { mutableStateOf<BusMapController?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller?.dispose()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isTiltGesturesEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true
                isCompassEnabled = true
            }
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(initialCenter.lat, initialCenter.lon), initialZoom))
            m.addOnCameraIdleListener {
                val b = m.projection.visibleRegion.latLngBounds
                idle(BBox(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast), m.cameraPosition.zoom)
            }
            m.addOnMapClickListener { click(it); true }
            m.addOnMapLongClickListener { longClick(it); true }
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) gesture()
            }
            map = m
        }
    }

    LaunchedEffect(map, topInset, bottomInset) {
        val m = map ?: return@LaunchedEffect
        with(density) {
            val top = topInset.roundToPx()
            val bottom = bottomInset.roundToPx()
            val side = 12.dp.roundToPx()
            m.uiSettings.setCompassMargins(side, top + side, side, side)
            m.uiSettings.setAttributionMargins(side, side, side, bottom + side / 2)
        }
    }

    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        controller?.dispose()
        controller = null
        ready(null)
        m.setStyle(Style.Builder().fromUri(if (dark) BusMapController.STYLE_DARK else BusMapController.STYLE_LIGHT)) { style ->
            val c = BusMapController(context, m, style, scope, dark)
            controller = c
            ready(c)
            val b = m.projection.visibleRegion.latLngBounds
            idle(BBox(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast), m.cameraPosition.zoom)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
