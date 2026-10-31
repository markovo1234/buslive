package com.buslive.app.data.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open Bus Stride API (https://open-bus-stride-api.hasadna.org.il/docs).
 * Every param here was checked against /openapi.json and with real calls — see docs/API_NOTES.md.
 * Unknown params are silently ignored by the server, so do not add params that are not in the spec.
 */
interface StrideApi {

    @GET("siri_vehicle_locations/list")
    suspend fun vehicleLocations(
        @Query("recorded_at_time_from") recordedFrom: String,
        @Query("recorded_at_time_to") recordedTo: String,
        @Query("lat__greater_or_equal") latMin: Double? = null,
        @Query("lat__lower_or_equal") latMax: Double? = null,
        @Query("lon__greater_or_equal") lonMin: Double? = null,
        @Query("lon__lower_or_equal") lonMax: Double? = null,
        @Query("siri_routes__line_ref") lineRef: Long? = null,
        @Query("limit") limit: Int,
        @Query("order_by") orderBy: String? = null,
    ): List<VehicleLocationDto>

    @GET("gtfs_stops/list")
    suspend fun stops(
        @Query("date_from") dateFrom: String,
        @Query("date_to") dateTo: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0,
        @Query("order_by") orderBy: String = "id asc",
    ): List<GtfsStopDto>

    @GET("gtfs_stops/list")
    suspend fun stopsCount(
        @Query("date_from") dateFrom: String,
        @Query("date_to") dateTo: String,
        @Query("get_count") getCount: Boolean = true,
    ): Int

    @GET("gtfs_routes/list")
    suspend fun routes(
        @Query("date_from") dateFrom: String,
        @Query("date_to") dateTo: String,
        @Query("line_refs") lineRefs: String? = null,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0,
        @Query("order_by") orderBy: String = "id asc",
    ): List<GtfsRouteDto>

    /** All rides of one (per-date) route. Note: the API's start_time_to filters on end_time, so we never send it. */
    @GET("gtfs_rides/list")
    suspend fun rides(
        @Query("gtfs_route_id") routeId: Long,
        @Query("start_time_from") startTimeFrom: String? = null,
        @Query("limit") limit: Int = 500,
        @Query("order_by") orderBy: String = "start_time asc",
    ): List<GtfsRideDto>

    /** Full stop list (with planned times) of one ride. */
    @GET("gtfs_ride_stops/list")
    suspend fun rideStopsOfRide(
        @Query("gtfs_ride_ids") rideId: Long,
        @Query("limit") limit: Int = 400,
        @Query("order_by") orderBy: String = "stop_sequence asc",
    ): List<GtfsRideStopDto>

    /** Planned calls at one stop (by public stop code) in a time window: the arrivals board. */
    @GET("gtfs_ride_stops/list")
    suspend fun rideStopsAtStop(
        @Query("gtfs_stop__code") stopCode: Int,
        @Query("arrival_time_from") arrivalFrom: String,
        @Query("arrival_time_to") arrivalTo: String,
        @Query("limit") limit: Int = 400,
        @Query("order_by") orderBy: String = "arrival_time asc",
    ): List<GtfsRideStopDto>

    companion object {
        const val BASE_URL = "https://open-bus-stride-api.hasadna.org.il/"
        /** Server hard limit per request. */
        const val MAX_LIMIT = 15000
    }
}
