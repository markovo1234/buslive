package com.buslive.app.data.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenStreetMap Nominatim. Usage policy: real User-Agent (set in OkHttp), max 1 req/s,
 * no autocomplete spam — callers debounce and cache (see PlaceRepository).
 */
interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("countrycodes") countryCodes: String = "il",
        @Query("limit") limit: Int = 6,
        @Query("accept-language") language: String,
    ): List<NominatimPlaceDto>

    companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org/"
    }
}
