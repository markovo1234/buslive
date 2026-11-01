package com.buslive.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `/siri_vehicle_locations/list`. Only fields the app uses. */
@Serializable
data class VehicleLocationDto(
    val id: Long? = null,
    @SerialName("recorded_at_time") val recordedAtTime: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val bearing: Int? = null,
    val velocity: Int? = null,
    @SerialName("distance_from_journey_start") val distanceFromJourneyStart: Int? = null,
    @SerialName("siri_route__line_ref") val lineRef: Long? = null,
    @SerialName("siri_route__operator_ref") val operatorRef: Int? = null,
    @SerialName("siri_ride__id") val siriRideId: Long? = null,
    @SerialName("siri_ride__journey_ref") val journeyRef: String? = null,
    @SerialName("siri_ride__scheduled_start_time") val scheduledStartTime: String? = null,
    @SerialName("siri_ride__vehicle_ref") val vehicleRef: String? = null,
)

/** Row of `/siri_snapshots/list`: one SIRI download per minute. */
@Serializable
data class SiriSnapshotDto(
    val id: Long,
    @SerialName("snapshot_id") val snapshotId: String? = null,
    @SerialName("etl_status") val etlStatus: String? = null,
)

/** Row of `/gtfs_stops/list`. */
@Serializable
data class GtfsStopDto(
    val id: Long,
    val date: String? = null,
    val code: Int,
    val lat: Double? = null,
    val lon: Double? = null,
    val name: String? = null,
    val city: String? = null,
)

/** Row of `/gtfs_routes/list`. */
@Serializable
data class GtfsRouteDto(
    val id: Long,
    val date: String,
    @SerialName("line_ref") val lineRef: Long,
    @SerialName("operator_ref") val operatorRef: Int,
    @SerialName("route_short_name") val shortName: String? = null,
    @SerialName("route_long_name") val longName: String? = null,
    @SerialName("route_mkt") val mkt: String? = null,
    @SerialName("route_direction") val direction: String? = null,
    @SerialName("route_alternative") val alternative: String? = null,
    @SerialName("agency_name") val agencyName: String? = null,
    @SerialName("route_type") val routeType: String? = null,
)

/** Row of `/gtfs_rides/list`. */
@Serializable
data class GtfsRideDto(
    val id: Long,
    @SerialName("gtfs_route_id") val routeId: Long? = null,
    @SerialName("journey_ref") val journeyRef: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
)

/** Row of `/gtfs_ride_stops/list` including the related stop / ride / route columns. */
@Serializable
data class GtfsRideStopDto(
    val id: Long? = null,
    @SerialName("gtfs_stop_id") val stopId: Long? = null,
    @SerialName("gtfs_ride_id") val rideId: Long,
    @SerialName("arrival_time") val arrivalTime: String? = null,
    @SerialName("departure_time") val departureTime: String? = null,
    @SerialName("stop_sequence") val stopSequence: Int = 0,
    @SerialName("pickup_type") val pickupType: Int? = null,
    @SerialName("drop_off_type") val dropOffType: Int? = null,
    @SerialName("shape_dist_traveled") val shapeDistTraveled: Int? = null,
    @SerialName("gtfs_ride__gtfs_route_id") val routeId: Long? = null,
    @SerialName("gtfs_ride__start_time") val rideStartTime: String? = null,
    @SerialName("gtfs_ride__journey_ref") val rideJourneyRef: String? = null,
    @SerialName("gtfs_ride__end_time") val rideEndTime: String? = null,
    @SerialName("gtfs_stop__code") val stopCode: Int? = null,
    @SerialName("gtfs_stop__lat") val stopLat: Double? = null,
    @SerialName("gtfs_stop__lon") val stopLon: Double? = null,
    @SerialName("gtfs_stop__name") val stopName: String? = null,
    @SerialName("gtfs_stop__city") val stopCity: String? = null,
    @SerialName("gtfs_route__line_ref") val lineRef: Long? = null,
    @SerialName("gtfs_route__operator_ref") val operatorRef: Int? = null,
    @SerialName("gtfs_route__route_short_name") val routeShortName: String? = null,
    @SerialName("gtfs_route__route_long_name") val routeLongName: String? = null,
    @SerialName("gtfs_route__route_mkt") val routeMkt: String? = null,
    @SerialName("gtfs_route__route_direction") val routeDirection: String? = null,
    @SerialName("gtfs_route__route_alternative") val routeAlternative: String? = null,
    @SerialName("gtfs_route__agency_name") val agencyName: String? = null,
    @SerialName("gtfs_route__route_type") val routeType: String? = null,
)

/** Result of `/search` on Nominatim. */
@Serializable
data class NominatimPlaceDto(
    @SerialName("place_id") val placeId: Long? = null,
    val lat: String,
    val lon: String,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val category: String? = null,
    val type: String? = null,
)
