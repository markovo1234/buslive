package com.buslive.app.data.repo

import android.content.Context
import com.buslive.app.domain.model.BBox
import com.buslive.app.domain.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import javax.inject.Inject
import javax.inject.Singleton

data class OfflineProgress(
    val running: Boolean = false,
    val percent: Int = 0,
    val bytes: Long = 0,
    val done: Boolean = false,
    val error: String? = null,
)

/** Downloads map tiles around a point (≈ a city, zoom 10–15) with MapLibre's offline database. */
@Singleton
class OfflineMapRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val _progress = MutableStateFlow(OfflineProgress())
    val progress: StateFlow<OfflineProgress> = _progress.asStateFlow()

    /** Must be called on the main thread (MapLibre offline API). */
    fun download(center: GeoPoint, styleUrl: String, radiusM: Double = 9_000.0) {
        if (_progress.value.running) return
        val b = BBox.around(center, radiusM)
        val bounds = LatLngBounds.Builder()
            .include(LatLng(b.north, b.east))
            .include(LatLng(b.south, b.west))
            .build()
        val def = OfflineTilePyramidRegionDefinition(
            styleUrl, bounds, 10.0, 15.0, context.resources.displayMetrics.density,
        )
        _progress.value = OfflineProgress(running = true)
        OfflineManager.getInstance(context).createOfflineRegion(
            def,
            "buslive-city".toByteArray(),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setDeliverInactiveMessages(false)
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val pct = if (status.requiredResourceCount > 0) {
                                (100.0 * status.completedResourceCount / status.requiredResourceCount).toInt()
                            } else 0
                            _progress.value = OfflineProgress(
                                running = !status.isComplete, percent = pct,
                                bytes = status.completedResourceSize, done = status.isComplete,
                            )
                            if (status.isComplete) offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        }

                        override fun onError(error: OfflineRegionError) {
                            _progress.value = _progress.value.copy(error = error.message)
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            _progress.value = _progress.value.copy(running = false, error = "tile limit $limit")
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    _progress.value = OfflineProgress(error = error)
                }
            },
        )
    }

    /** Remove all downloaded regions. */
    fun deleteAll(onDone: () -> Unit = {}) {
        OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                offlineRegions?.forEach { r ->
                    r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() = Unit
                        override fun onError(error: String) = Unit
                    })
                }
                _progress.value = OfflineProgress()
                onDone()
            }

            override fun onError(error: String) = onDone()
        })
    }
}
