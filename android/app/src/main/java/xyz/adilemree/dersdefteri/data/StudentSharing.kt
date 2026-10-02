package xyz.adilemree.dersdefteri.data

import android.net.Uri

// Öğrenci iletişimi, ödeme hatırlatma ve paylaşılabilir özet metinleri.
// Metinler iOS uygulamasındakilerle aynıdır.

enum class SummaryPeriod(val title: String) {
    WEEK("Haftalık"),
    MONTH("Aylık"),
    ALL("Tüm zamanlar");

    /// Başlıklarda: "Haftalık Ders Özeti", "Tüm Zamanlar Ders Özeti".
    val headline: String get() = if (this == ALL) "Tüm Zamanlar" else title

    /// [start, end) aralığı. Tüm zamanlar: öğrencinin başlangıcından (ya da
    /// ilk dersinden) bugünün sonuna kadar; ileri tarihli planlı dersler dahil
    /// edilmez, özet yapılanı anlatır.
    fun interval(notebook: Notebook, student: Student, now: Long = System.currentTimeMillis()): Pair<Long, Long> =
        when (this) {
            WEEK -> startOfWeek(now).let { it to addDays(it, 7) }
            MONTH -> startOfMonth(now).let { it to addMonths(it, 1) }
            ALL -> {
                val lessons = notebook.lessonsOf(student.id)
                val firstLesson = lessons.minOfOrNull { it.date } ?: student.startDate
                val start = startOfDay(minOf(student.startDate, firstLesson))
                // İleri tarihli ama işaretlenmiş (işlendi/iptal) ders de sayılır.
                val lastMarked = lessons.filter { !it.isPlanned }.maxOfOrNull { it.date }
                val end = maxOf(
                    addDays(startOfDay(now), 1),
                    addDays(startOfDay(lastMarked ?: start), 1),
                )
                start to end
            }
        }

    /// Uzun listelerde hangi dersler gösterilir: kısa dönemde ilkler, tüm
    /// zamanlarda en yeniler.
    fun <T> visible(items: List<T>, limit: Int): Pair<List<T>, Int> {
        if (items.size <= limit) return items to 0
        return if (this == ALL) items.takeLast(limit) to items.size - limit
        else items.take(limit) to items.size - limit
    }
}

object StudentSharing {
    fun cleanPhone(raw: String): String = raw.filter { it.isDigit() }

    fun bestContactPhone(student: Student): String =
        if (student.parentPhone.isNotBlank()) student.parentPhone else student.phone

    fun dialUri(rawPhone: String): Uri? {
        val phone = cleanPhone(rawPhone)
        return if (phone.isEmpty()) null else Uri.parse("tel:$phone")
    }

    fun smsUri(rawPhone: String): Uri? {
        val phone = cleanPhone(rawPhone)
        return if (phone.isEmpty()) null else Uri.parse("smsto:$phone")
    }

    /// WhatsApp yüklü değilse bağlantı tarayıcıda açılır.
    fun whatsappUri(rawPhone: String, text: String): Uri? {
        val phone = whatsappPhone(rawPhone) ?: return null
        return Uri.parse("https://wa.me/$phone?text=${Uri.encode(text)}")
    }

    private fun whatsappPhone(raw: String): String? {
        val digits = cleanPhone(raw)
        if (digits.isEmpty()) return null
        if (digits.startsWith("90")) return digits
        if (digits.startsWith("0") && digits.length == 11) return "90" + digits.drop(1)
        if (digits.length == 10) return "90$digits"
        return digits
    }

    fun paymentReminder(notebook: Notebook, student: Student, now: Long = System.currentTimeMillis()): String {
        val month = Fmt.monthYear(now)
        val balance = Fmt.money(maxOf(notebook.balance(student.id), 0.0))
        return "Merhaba, $month ders bakiyeniz $balance görünüyor.\n\n" +
            "Müsait olduğunuzda ödeme bilgisini paylaşabilir misiniz? Teşekkür ederim."
    }

    fun balanceText(notebook: Notebook, student: Student): String {
        val stats = notebook.stats(student.id)
        val balance = stats.balance
        if (balance > 0.5) return "${Fmt.money(balance)} borç"
        if (balance < -0.5) return "${Fmt.money(-balance)} avans"
        if (stats.totalEarned <= 0.5 && stats.totalPaid <= 0.5) {
            val planned = stats.plannedCount
            return if (planned > 0) "$planned planlı ders, borç yok" else "borç yok"
        }
        return "ödendi"
    }

    fun summary(notebook: Notebook, student: Student, period: SummaryPeriod, now: Long = System.currentTimeMillis()): String {
        val (start, end) = period.interval(notebook, student, now)
        val lessons = notebook.lessonsOf(student.id).filter { it.date in start until end }.sortedBy { it.date }
        val homeworks = notebook.homeworksOf(student.id)
            .filter { it.assignedDate < end && (it.dueDate >= start || !it.isDone) }
            .sortedBy { it.dueDate }
        val completed = lessons.filter { it.isCompleted }
        val minutes = completed.sumOf { it.duration }
        val earned = completed.sumOf { notebook.fee(it) }

        val lines = mutableListOf<String>()
        lines += "${student.name} - ${period.headline} Ders Özeti"
        lines += "${Fmt.long(start)} - ${Fmt.long(addDays(end, -1))}"
        lines += ""
        lines += "Genel durum"
        lines += "• İşlenen ders: ${completed.size}"
        lines += "• Toplam süre: ${Fmt.hours(minutes)}"
        lines += "• İşlenen ders tutarı: ${Fmt.money(earned)}"
        if (period == SummaryPeriod.ALL) {
            val cancelled = lessons.count { it.isCancelled }
            if (cancelled > 0) lines += "• İptal edilen ders: $cancelled"
            lines += "• Toplam ödenen: ${Fmt.money(notebook.stats(student.id).totalPaid)}"
        }
        lines += "• Güncel bakiye: ${balanceText(notebook, student)}"

        if (lessons.isNotEmpty()) {
            lines += ""
            lines += if (period == SummaryPeriod.ALL) "Son dersler" else "Dersler"
            val (shown, hidden) = period.visible(lessons, 12)
            for (lesson in shown) {
                val topic = lesson.topic.ifEmpty { student.subject.ifEmpty { "Konu belirtilmedi" } }
                // Tüm zamanlarda dersler farklı yıllardan olabilir.
                val day = if (period == SummaryPeriod.ALL) Fmt.dayMonthYearShort(lesson.date, now) else Fmt.dayMonthShort(lesson.date)
                val paid = if (!lesson.isCancelled && lesson.paymentId != null) " (toplu ödendi)" else ""
                lines += "• $day ${Fmt.time(lesson.date)} - ${lesson.lessonStatus.title} - $topic$paid"
            }
            if (hidden > 0) {
                lines += if (period == SummaryPeriod.ALL) "• ve daha önceki $hidden ders" else "• +$hidden ders daha"
            }
        }

        if (homeworks.isNotEmpty()) {
            lines += ""
            lines += "Ödevler"
            val (shown, hidden) = period.visible(homeworks, 8)
            for (hw in shown) {
                val status = when {
                    hw.isDone -> "tamamlandı"
                    hw.isLate(now) -> "gecikti"
                    else -> "bekliyor"
                }
                lines += "• ${hw.title} - $status, son: ${Fmt.dayMonthShort(hw.dueDate)}"
            }
            if (hidden > 0) lines += "• +$hidden ödev daha"
        }

        return lines.joinToString("\n")
    }
}

/// Türkiye telefon numarası girişlerini okunur biçime çevirir: "0532 123 45 67".
object TurkishPhoneFormat {
    fun format(value: String): String {
        var digits = value.filter { it.isDigit() }
        if (digits.startsWith("90") && digits.length > 11) digits = digits.drop(2)
        if (!digits.startsWith("0") && digits.isNotEmpty()) digits = "0$digits"
        digits = digits.take(11)
        val groups = listOf(
            digits.take(4),
            digits.drop(4).take(3),
            digits.drop(7).take(2),
            digits.drop(9).take(2),
        ).filter { it.isNotEmpty() }
        return groups.joinToString(" ")
    }
}
