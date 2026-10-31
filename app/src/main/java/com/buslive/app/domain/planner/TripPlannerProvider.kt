package com.buslive.app.domain.planner

import com.buslive.app.data.prefs.SettingsRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Picks the planner: OpenTripPlanner when an OTP URL is set in Settings, else the built-in Stride planner. */
@Singleton
class TripPlannerProvider @Inject constructor(
    private val settings: SettingsRepository,
    private val local: LocalTripPlanner,
    private val client: OkHttpClient,
    private val json: Json,
) {
    data class Choice(val planner: TripPlanner, val usingOtp: Boolean, val fallback: TripPlanner?)

    suspend fun current(): Choice {
        val url = settings.current().otpBaseUrl.trim()
        if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) return Choice(local, false, null)
        val otpClient = client.newBuilder().readTimeout(40, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
        return Choice(OtpTripPlanner(url, otpClient, json), true, local)
    }
}
