package com.buslive.app.domain.planner

import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.domain.model.RideStop
import com.buslive.app.domain.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * OpenTripPlanner 2.x via its GTFS GraphQL API (`/otp/gtfs/v1`, fallback `/otp/routers/default/index/graphql`).
 * Used when the user sets an OTP base URL in Settings (e.g. a home server loaded with Israel GTFS).
 */
class OtpTripPlanner(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json,
) : TripPlanner {

    override suspend fun plan(from: GeoPoint, to: GeoPoint, departAt: Instant): List<TripOption> = withContext(Dispatchers.IO) {
        val local = departAt.atZone(TimeUtil.ZONE)
        val variables = buildJsonObject {
            put("fromLat", from.lat); put("fromLon", from.lon)
            put("toLat", to.lat); put("toLon", to.lon)
            put("date", DateTimeFormatter.ISO_LOCAL_DATE.format(local))
            put("time", DateTimeFormatter.ofPattern("HH:mm").format(local))
        }
        val body = buildJsonObject {
            put("query", QUERY)
            put("variables", variables)
        }.toString()

        val base = baseUrl.trimEnd('/')
        val endpoints = listOf("$base/otp/gtfs/v1", "$base/otp/routers/default/index/graphql")
        var lastError: Exception? = null
        for (url in endpoints) {
            try {
                val req = Request.Builder().url(url)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("OTPTimeout", "30000")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("OTP HTTP ${resp.code}")
                    val root = json.parseToJsonElement(resp.body.string()).jsonObject
                    return@withContext parse(root)
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("OTP unreachable")
    }

    private fun parse(root: JsonObject): List<TripOption> {
        val itineraries = root.obj("data")?.obj("plan")?.arr("itineraries") ?: return emptyList()
        return itineraries.mapIndexedNotNull { idx, it ->
            val itin = it as? JsonObject ?: return@mapIndexedNotNull null
            val legs = itin.arr("legs")?.mapNotNull { l -> parseLeg(l as? JsonObject ?: return@mapNotNull null) }.orEmpty()
            if (legs.isEmpty()) null else TripOption("otp-$idx-${itin.long("startTime")}", legs, TripOption.Source.OTP)
        }
    }

    private fun parseLeg(l: JsonObject): TripLeg? {
        val start = Instant.ofEpochMilli(l.long("startTime") ?: return null)
        val end = Instant.ofEpochMilli(l.long("endTime") ?: return null)
        val from = l.obj("from") ?: return null
        val to = l.obj("to") ?: return null
        val fromPt = GeoPoint(from.dbl("lat") ?: return null, from.dbl("lon") ?: return null)
        val toPt = GeoPoint(to.dbl("lat") ?: return null, to.dbl("lon") ?: return null)
        val path = l.obj("legGeometry")?.str("points")?.let { decodePolyline(it) }.orEmpty()
        val mode = l.str("mode") ?: "WALK"
        if (mode == "WALK" || mode == "BICYCLE") {
            return TripLeg.Walk(
                from = fromPt, to = toPt, fromName = from.str("name").orEmpty(), toName = to.str("name").orEmpty(),
                distanceM = (l.dbl("distance") ?: 0.0).toInt(), start = start, end = end,
                path = path.ifEmpty { listOf(fromPt, toPt) },
            )
        }
        val route = l.obj("route")
        val agencyId = route?.obj("agency")?.str("gtfsId")?.substringAfter(':')?.toIntOrNull() ?: 0
        val intermediate = l.arr("intermediateStops")?.mapIndexedNotNull { i, s ->
            val o = s as? JsonObject ?: return@mapIndexedNotNull null
            RideStop(
                stopCode = o.str("code")?.toIntOrNull() ?: 0, name = o.str("name").orEmpty(), city = "",
                lat = o.dbl("lat") ?: return@mapIndexedNotNull null, lon = o.dbl("lon") ?: return@mapIndexedNotNull null,
                sequence = i + 2, arrival = start, departure = start, shapeDistM = null,
            )
        }.orEmpty()
        val fromCode = from.obj("stop")?.str("code")?.toIntOrNull()
        val toCode = to.obj("stop")?.str("code")?.toIntOrNull()
        val stops = buildList {
            add(RideStop(fromCode ?: 0, from.str("name").orEmpty(), "", fromPt.lat, fromPt.lon, 1, start, start, null))
            addAll(intermediate)
            add(RideStop(toCode ?: 0, to.str("name").orEmpty(), "", toPt.lat, toPt.lon, intermediate.size + 2, end, end, null))
        }
        return TripLeg.Bus(
            lineRef = null,
            shortName = route?.str("shortName") ?: route?.str("longName") ?: mode,
            operatorRef = agencyId,
            agencyName = route?.obj("agency")?.str("name").orEmpty(),
            headsign = l.obj("trip")?.str("tripHeadsign") ?: to.str("name").orEmpty(),
            rideId = null,
            rideStart = null,
            from = StopRef(fromCode, from.str("name").orEmpty(), fromPt.lat, fromPt.lon),
            to = StopRef(toCode, to.str("name").orEmpty(), toPt.lat, toPt.lon),
            start = start,
            end = end,
            stopsCount = intermediate.size + 1,
            stops = stops,
            path = path.ifEmpty { stops.map { it.point } },
        )
    }

    companion object {
        private val QUERY = """
            query Plan(${'$'}fromLat: Float!, ${'$'}fromLon: Float!, ${'$'}toLat: Float!, ${'$'}toLon: Float!, ${'$'}date: String!, ${'$'}time: String!) {
              plan(from: {lat: ${'$'}fromLat, lon: ${'$'}fromLon}, to: {lat: ${'$'}toLat, lon: ${'$'}toLon},
                   date: ${'$'}date, time: ${'$'}time, numItineraries: 6,
                   transportModes: [{mode: BUS}, {mode: RAIL}, {mode: TRAM}, {mode: WALK}]) {
                itineraries {
                  startTime endTime
                  legs {
                    mode startTime endTime distance
                    from { name lat lon stop { code } }
                    to { name lat lon stop { code } }
                    route { shortName longName agency { gtfsId name } }
                    trip { tripHeadsign }
                    intermediateStops { name lat lon code }
                    legGeometry { points }
                  }
                }
              }
            }
        """.trimIndent()

        /** Google encoded polyline (precision 5). */
        fun decodePolyline(encoded: String): List<GeoPoint> {
            val out = ArrayList<GeoPoint>()
            var index = 0
            var lat = 0
            var lng = 0
            while (index < encoded.length) {
                var result = 0
                var shift = 0
                var b: Int
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20 && index < encoded.length)
                lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
                result = 0
                shift = 0
                if (index >= encoded.length) break
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20 && index < encoded.length)
                lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
                out += GeoPoint(lat / 1e5, lng / 1e5)
            }
            return out
        }
    }
}

private fun JsonObject.obj(k: String): JsonObject? = (this[k] as? JsonObject)
private fun JsonObject.arr(k: String): JsonArray? = (this[k] as? JsonArray)
private fun JsonObject.prim(k: String): JsonElement? = this[k]?.takeIf { it !is JsonNull }
private fun JsonObject.str(k: String): String? = runCatching { prim(k)?.jsonPrimitive?.contentOrNull }.getOrNull()
private fun JsonObject.dbl(k: String): Double? = runCatching { prim(k)?.jsonPrimitive?.doubleOrNull }.getOrNull()
private fun JsonObject.long(k: String): Long? = runCatching { prim(k)?.jsonPrimitive?.longOrNull }.getOrNull()
