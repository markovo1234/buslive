package com.buslive.app.data.repo

import android.util.LruCache
import com.buslive.app.data.api.NominatimApi
import com.buslive.app.domain.model.Place
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Place / address search via Nominatim. Follows the usage policy:
 * identifying User-Agent (OkHttp interceptor), at most 1 request per second, results cached, callers debounce.
 */
@Singleton
class PlaceRepository @Inject constructor(private val api: NominatimApi) {
    private val mutex = Mutex()
    private var lastRequestAt = 0L
    private val cache = LruCache<String, List<Place>>(64)

    suspend fun search(query: String, hebrew: Boolean): List<Place> {
        val q = query.trim()
        if (q.length < 3) return emptyList()
        val lang = if (hebrew) "he,en" else "en,he"
        val key = "$lang|${q.lowercase()}"
        cache.get(key)?.let { return it }
        return mutex.withLock {
            cache.get(key)?.let { return@withLock it }
            val wait = 1100 - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
            val result = api.search(query = q, language = lang).mapNotNull { dto ->
                val lat = dto.lat.toDoubleOrNull() ?: return@mapNotNull null
                val lon = dto.lon.toDoubleOrNull() ?: return@mapNotNull null
                val display = dto.displayName.orEmpty()
                val name = dto.name?.takeIf { it.isNotBlank() } ?: display.substringBefore(',')
                Place(
                    name = name,
                    address = display.split(',').drop(1).take(3).joinToString(",").trim(),
                    lat = lat,
                    lon = lon,
                )
            }
            cache.put(key, result)
            result
        }
    }
}
