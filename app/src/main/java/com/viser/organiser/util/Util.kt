package com.viser.organiser.util

import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toLdt(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)
fun LocalDateTime.toMillis(): Long = atZone(zone).toInstant().toEpochMilli()
fun LocalDate.startMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

fun YearMonth.startMillis(): Long = atDay(1).startMillis()
fun YearMonth.endMillis(): Long = plusMonths(1).atDay(1).startMillis()

/** Indian digit grouping: 120000 -> 1,20,000 */
fun groupIndian(n: Long): String {
    val s = abs(n).toString()
    if (s.length <= 3) return s
    val last3 = s.takeLast(3)
    var rest = s.dropLast(3)
    val parts = ArrayList<String>()
    while (rest.length > 2) {
        parts.add(0, rest.takeLast(2))
        rest = rest.dropLast(2)
    }
    if (rest.isNotEmpty()) parts.add(0, rest)
    return parts.joinToString(",") + "," + last3
}

/** paise -> "₹1,20,000" (rupees only) */
fun rupees(paise: Long): String = (if (paise < 0) "−" else "") + "₹" + groupIndian(abs(paise) / 100)

/** paise -> "38,420" and ".00" parts, for the big serif figures. */
fun rupeeParts(paise: Long): Pair<String, String> {
    val a = abs(paise)
    val main = (if (paise < 0) "−₹" else "₹") + groupIndian(a / 100)
    val dec = "." + (a % 100).toString().padStart(2, '0')
    return main to dec
}

/** "349.50" / "1,20,000" -> paise */
fun parseRupees(s: String): Long? {
    val clean = s.replace(",", "").replace("₹", "").trim()
    if (clean.isEmpty()) return null
    val d = clean.toBigDecimalOrNull() ?: return null
    return d.movePointRight(2).toLong()
}

private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val dMon = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dMonY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val dow = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

fun timeHm(ms: Long): String = ms.toLdt().format(hm)
fun dateShort(ms: Long): String = ms.toLdt().format(dMon)
fun dateLong(ms: Long): String = ms.toLdt().format(dMonY)

/** "Today 19:00", "Tomorrow 09:00", "Sat 20:00", "1 Oct 09:00", "Yesterday 18:00" */
fun friendlyWhen(ms: Long): String {
    val t = ms.toLdt()
    val today = LocalDate.now(zone)
    val d = t.toLocalDate()
    val time = t.format(hm)
    return when {
        d == today -> "Today $time"
        d == today.plusDays(1) -> "Tomorrow $time"
        d == today.minusDays(1) -> "Yesterday $time"
        d.isAfter(today) && d.isBefore(today.plusDays(7)) -> "${t.format(dow)} $time"
        else -> "${t.format(dMon)} $time"
    }
}

fun monthLabel(ym: YearMonth): String =
    ym.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + ym.year

fun monthLong(ym: YearMonth): String =
    ym.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).uppercase(Locale.ENGLISH)

// ---------------------------------------------------------------- links & places

private val urlRegex = Regex("""(https?://[^\s]+|geo:[^\s]+)""", RegexOption.IGNORE_CASE)

fun findUrl(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')

fun domainOf(url: String): String = try {
    (Uri.parse(url).host ?: "").removePrefix("www.")
} catch (e: Exception) { "" }

fun isMapsUrl(url: String): Boolean {
    val u = url.lowercase(Locale.ROOT)
    return u.startsWith("geo:") ||
        u.contains("maps.app.goo.gl") ||
        u.contains("goo.gl/maps") ||
        u.contains("google.com/maps") ||
        u.contains("maps.google.") ||
        u.contains("g.co/kgs")
}

private val coordPatterns = listOf(
    Regex("""@(-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)"""),
    Regex("""!3d(-?\d{1,2}\.\d+)!4d(-?\d{1,3}\.\d+)"""),
    Regex("""[?&](?:q|ll|query|destination)=(-?\d{1,2}\.\d+),\s?(-?\d{1,3}\.\d+)"""),
    Regex("""geo:(-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)"""),
)

/** Lat/long from a Maps URL where it is present in the URL itself (short links hide it). */
fun parseLatLng(url: String): Pair<Double, Double>? {
    val decoded = Uri.decode(url)
    for (p in coordPatterns) {
        val m = p.find(decoded) ?: continue
        val lat = m.groupValues[1].toDoubleOrNull() ?: continue
        val lng = m.groupValues[2].toDoubleOrNull() ?: continue
        return lat to lng
    }
    return null
}

/** Place name from a long Maps URL: /maps/place/Toit+Brewpub/... */
fun placeNameFromUrl(url: String): String? {
    val m = Regex("""/maps/place/([^/@?]+)""").find(url) ?: return null
    return Uri.decode(m.groupValues[1].replace('+', ' ')).trim().ifBlank { null }
}

data class Shared(val kind: String, val url: String, val title: String, val body: String)

/** Classify shared or typed text as note / link / place (C-3). */
fun classifyShared(text: String, subject: String? = null): Shared {
    val url = findUrl(text)
    if (url == null) return Shared("note", "", subject.orEmpty(), text.trim())
    val rest = text.replace(url, "").trim().trim('-', '|', ':').trim()
    return if (isMapsUrl(url)) {
        val name = rest.lines().firstOrNull { it.isNotBlank() }?.trim()
            ?: placeNameFromUrl(url) ?: subject.orEmpty()
        val body = rest.lines().drop(1).joinToString("\n").trim()
        Shared("place", url, name, body)
    } else {
        Shared("link", url, rest.lines().firstOrNull { it.isNotBlank() }?.trim() ?: subject.orEmpty(), "")
    }
}
