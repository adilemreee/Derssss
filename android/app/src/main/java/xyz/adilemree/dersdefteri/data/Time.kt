package xyz.adilemree.dersdefteri.data

import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Currency
import java.util.Locale

// Tarih ve biçim yardımcıları. Uygulama Türkçe ve Pazartesi ile başlayan
// hafta kullanır; tarihler epoch milisaniye olarak taşınır.

val TR: Locale = Locale.forLanguageTag("tr-TR")

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toZoned(): ZonedDateTime = Instant.ofEpochMilli(this).atZone(zone)
fun Long.toLocalDate(): LocalDate = toZoned().toLocalDate()
fun ZonedDateTime.millis(): Long = toInstant().toEpochMilli()
fun LocalDate.startMillis(): Long = atStartOfDay(zone).millis()
fun LocalDateTime.millis(): Long = atZone(zone).millis()

fun startOfDay(ms: Long): Long = ms.toLocalDate().startMillis()

fun startOfWeek(ms: Long): Long =
    ms.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).startMillis()

fun startOfMonth(ms: Long): Long = ms.toLocalDate().withDayOfMonth(1).startMillis()

/// Takvim günü ekler; saat korunur (yaz saati geçişlerinde de).
fun addDays(ms: Long, days: Int): Long = ms.toZoned().plusDays(days.toLong()).millis()

fun addMonths(ms: Long, months: Int): Long = ms.toZoned().plusMonths(months.toLong()).millis()

fun isSameDay(a: Long, b: Long): Boolean = a.toLocalDate() == b.toLocalDate()

fun isToday(ms: Long, now: Long = System.currentTimeMillis()): Boolean = isSameDay(ms, now)

/// İki tarihin gün başları arasındaki gün farkı.
fun daysBetween(from: Long, to: Long): Int =
    ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()).toInt()

/// iOS `Calendar.weekday` karşılığı: 1 = Pazar ... 7 = Cumartesi.
fun calendarWeekday(ms: Long): Int = ms.toZoned().dayOfWeek.value % 7 + 1

fun hourOf(ms: Long): Int = ms.toZoned().hour
fun minuteOf(ms: Long): Int = ms.toZoned().minute
fun dayOfMonth(ms: Long): Int = ms.toZoned().dayOfMonth

/// Günün tarihine verilen saat ve dakikayı koyar.
fun atTime(dayMs: Long, hour: Int, minute: Int): Long =
    dayMs.toLocalDate().atTime(LocalTime.of(hour, minute)).millis()

/// Gün ve saati ayrı seçen formlar için: `day`in tarihi, `time`ın saati.
fun combine(dayMs: Long, timeMs: Long): Long = atTime(dayMs, hourOf(timeMs), minuteOf(timeMs))

/// Türkçe büyük harf kuralıyla her kelimenin ilk harfi büyür ("ışık" → "Işık").
fun String.capitalizedTr(): String = split(" ").joinToString(" ") { word ->
    if (word.isEmpty()) word
    else word.substring(0, 1).uppercase(TR) + word.substring(1).lowercase(TR)
}

object Fmt {
    private fun make(pattern: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, TR)

    private val timeF = make("HH:mm")
    private val dayMonthF = make("d MMMM")
    private val dayMonthShortF = make("d MMM")
    private val dayMonthYearShortF = make("d MMM yyyy")
    private val weekdayF = make("EEEE")
    private val weekdayShortF = make("EEE")
    private val longF = make("d MMMM yyyy")
    private val monthYearF = make("MMMM yyyy")
    private val monthNameF = make("LLLL")
    private val fileStampF = make("yyyyMMdd-HHmm")

    fun time(ms: Long): String = timeF.format(ms.toZoned())
    fun dayMonth(ms: Long): String = dayMonthF.format(ms.toZoned())
    fun dayMonthShort(ms: Long): String = dayMonthShortF.format(ms.toZoned())
    fun weekday(ms: Long): String = weekdayF.format(ms.toZoned())
    fun weekdayShort(ms: Long): String = weekdayShortF.format(ms.toZoned())
    fun long(ms: Long): String = longF.format(ms.toZoned())
    fun monthYear(ms: Long): String = monthYearF.format(ms.toZoned()).capitalizedTr()
    fun fileStamp(ms: Long = System.currentTimeMillis()): String = fileStampF.format(ms.toZoned())

    /// Bu yıl içindeyse "12 Mar", değilse "12 Mar 2025".
    fun dayMonthYearShort(ms: Long, now: Long = System.currentTimeMillis()): String =
        if (ms.toZoned().year == now.toZoned().year) dayMonthShort(ms) else dayMonthYearShortF.format(ms.toZoned())

    /// "Eylül" (büyük harfle)
    fun monthName(ms: Long): String = monthNameF.format(ms.toZoned()).capitalizedTr()

    private val currency: NumberFormat = NumberFormat.getCurrencyInstance(TR).apply {
        currency = Currency.getInstance("TRY")
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    @Synchronized
    fun money(value: Double): String = currency.format(value)

    fun hours(minutes: Int): String {
        val h = minutes / 60.0
        if (h == Math.rint(h)) return "${h.toInt()} sa"
        return String.format(Locale.US, "%.1f sa", h).replace(".", ",")
    }

    /// "Bugün", "Yarın" ya da kısa gün adı
    fun dayLabel(ms: Long, now: Long = System.currentTimeMillis()): String = when {
        isSameDay(ms, now) -> "Bugün"
        isSameDay(ms, addDays(now, 1)) -> "Yarın"
        else -> weekdayShort(ms)
    }
}
