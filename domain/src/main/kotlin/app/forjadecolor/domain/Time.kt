package app.forjadecolor.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val ISO_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC)

/** Same text as JavaScript's Date.toISOString(): always milliseconds, always UTC ("…T10:00:00.000Z"). */
fun isoString(instant: Instant): String = ISO_MILLIS.format(instant)
