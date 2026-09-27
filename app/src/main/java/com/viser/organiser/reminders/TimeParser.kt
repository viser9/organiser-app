package com.viser.organiser.reminders

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Finds a time in free text (T-2): "call bank tomorrow 11am", "gym at 6:30 pm",
 * "rent on 1st", "dinner tonight", "meet sat 8pm".
 */
object TimeParser {
    data class Found(val at: LocalDateTime, val phrase: String)

    private val timeRx = Regex("""\b(?:at\s+)?(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)\b|\bat\s+(\d{1,2})(?:[:.](\d{2}))?\b|\b(\d{1,2})[:.](\d{2})\b""", RegexOption.IGNORE_CASE)
    private val dayWords = mapOf(
        "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY,
    )
    private val dayRx = Regex("""\b(today|tonight|tomorrow|tmrw|tmr|day after tomorrow|this evening|this weekend|weekend|next week|(?:next\s+|on\s+)?(?:mon|monday|tue|tues|tuesday|wed|wednesday|thu|thur|thurs|thursday|fri|friday|sat|saturday|sun|sunday))\b""", RegexOption.IGNORE_CASE)
    private val dateRx = Regex("""\b(?:on\s+)?(\d{1,2})(?:st|nd|rd|th)\b""", RegexOption.IGNORE_CASE)

    fun parse(text: String, now: LocalDateTime = LocalDateTime.now()): Found? {
        val today = now.toLocalDate()
        var date: LocalDate? = null
        var time: LocalTime? = null
        val phrases = mutableListOf<String>()

        dayRx.find(text)?.let { m ->
            val w = m.value.lowercase(Locale.ROOT).removePrefix("on ").trim()
            phrases += m.value
            date = when {
                w == "today" -> today
                w == "tonight" || w == "this evening" -> { if (time == null) time = LocalTime.of(20, 0); today }
                w == "tomorrow" || w == "tmrw" || w == "tmr" -> today.plusDays(1)
                w == "day after tomorrow" -> today.plusDays(2)
                w == "weekend" || w == "this weekend" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                w == "next week" -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                else -> {
                    val dow = dayWords[w.removePrefix("next ").trim()]
                    if (dow == null) null else today.with(TemporalAdjusters.next(dow))
                }
            }
        }

        if (date == null) {
            dateRx.find(text)?.let { m ->
                val day = m.groupValues[1].toInt()
                if (day in 1..31) {
                    phrases += m.value
                    var d = runCatching { today.withDayOfMonth(day) }.getOrNull()
                    if (d == null || d.isBefore(today)) d = runCatching { today.plusMonths(1).withDayOfMonth(day) }.getOrNull()
                    date = d
                }
            }
        }

        timeRx.find(text)?.let { m ->
            val g = m.groupValues
            var h: Int
            var min = 0
            if (g[1].isNotEmpty()) {
                h = g[1].toInt(); min = g[2].toIntOrNull() ?: 0
                val pm = g[3].lowercase(Locale.ROOT).startsWith("p")
                if (h == 12) h = 0
                if (pm) h += 12
            } else if (g[4].isNotEmpty()) {
                h = g[4].toInt(); min = g[5].toIntOrNull() ?: 0
                // "at 7" with no am/pm: assume the next sensible hour (7 -> 19 if it's afternoon-ish)
                if (h in 1..7) h += 12
            } else {
                h = g[6].toInt(); min = g[7].toInt()
            }
            if (h in 0..23 && min in 0..59) {
                time = LocalTime.of(h, min)
                phrases += m.value
            }
        }

        if (date == null && time == null) return null
        val t = time ?: LocalTime.of(9, 0)
        var at = LocalDateTime.of(date ?: today, t)
        if (date == null && !at.isAfter(now)) at = at.plusDays(1)
        return Found(at, phrases.joinToString(" ").trim())
    }
}
