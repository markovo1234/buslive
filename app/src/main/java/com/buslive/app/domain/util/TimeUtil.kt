package com.buslive.app.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** All app time logic runs in Israel time. */
object TimeUtil {
    val ZONE: ZoneId = ZoneId.of("Asia/Jerusalem")

    private val apiFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")
    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** API wants an explicit zone: "2026-10-31T17:29:00+00:00". */
    fun toApi(instant: Instant): String = apiFormat.format(instant.atOffset(ZoneOffset.UTC))

    /** API returns "+00:00" offsets; Instant.parse can't read those on API 26. */
    fun parse(s: String?): Instant? = s?.let {
        runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
    }

    fun today(): LocalDate = LocalDate.now(ZONE)

    fun formatTime(instant: Instant): String = hhmm.format(instant.atZone(ZONE))

    fun minutesUntil(instant: Instant, now: Instant = Instant.now()): Long =
        Math.floorDiv(instant.epochSecond - now.epochSecond, 60L)
}
