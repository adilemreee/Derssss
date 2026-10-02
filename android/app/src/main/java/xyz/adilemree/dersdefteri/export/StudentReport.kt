package xyz.adilemree.dersdefteri.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.Student
import xyz.adilemree.dersdefteri.data.SummaryPeriod
import xyz.adilemree.dersdefteri.data.addDays
import java.io.File

/// Veliye gönderilebilir PDF ders raporu. İçerik iOS'taki raporla aynıdır;
/// belge her zaman açık zeminli çizilir (koyu moddan bağımsız).
object StudentReport {
    class Row(val leading: String, val title: String, val trailing: String)

    class Data(
        val studentName: String,
        val subject: String,
        val grade: String,
        val teacherName: String,
        val periodTitle: String,
        val rangeText: String,
        val completedCount: Int,
        val cancelledCount: Int,
        val totalMinutes: Int,
        val earned: Double,
        val balance: Double,
        val lessons: List<Row>,
        val homeworks: List<Row>,
    ) {
        val balanceText: String
            get() = when {
                balance > 0.5 -> "${Fmt.money(balance)} borç"
                balance < -0.5 -> "${Fmt.money(-balance)} avans"
                else -> "Bakiye kapalı"
            }

        val fileName: String
            get() {
                val safe = studentName.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.joinToString("-")
                return "${safe.ifEmpty { "ogrenci" }}-rapor-${Fmt.fileStamp()}.pdf"
            }
    }

    fun data(notebook: Notebook, student: Student, period: SummaryPeriod, teacherName: String, now: Long = System.currentTimeMillis()): Data {
        val (start, end) = period.interval(notebook, student, now)
        val inRange = notebook.lessonsOf(student.id).filter { it.date in start until end }.sortedBy { it.date }
        val completed = inRange.filter { it.isCompleted }

        // Rapor bir sayfada okunabilir kalsın diye satır sayısı sınırlanır.
        val lessons = period.visible(inRange, 18).first.map { lesson ->
            val topic = lesson.topic.ifEmpty { student.subject.ifEmpty { "—" } }
            val day = if (period == SummaryPeriod.ALL) Fmt.dayMonthYearShort(lesson.date, now) else Fmt.dayMonthShort(lesson.date)
            Row(
                leading = "$day ${Fmt.time(lesson.date)}",
                title = topic,
                trailing = if (!lesson.isCancelled && lesson.paymentId != null) "${lesson.lessonStatus.title} · Ödendi" else lesson.lessonStatus.title,
            )
        }
        val rangeHomeworks = notebook.homeworksOf(student.id)
            .filter { it.assignedDate < end && (it.dueDate >= start || !it.isDone) }
            .sortedBy { it.dueDate }
        val homeworks = period.visible(rangeHomeworks, 12).first.map { hw ->
            Row(
                leading = Fmt.dayMonthShort(hw.dueDate),
                title = hw.title,
                trailing = when {
                    hw.isDone -> "Tamamlandı"
                    hw.isLate(now) -> "Gecikti"
                    else -> "Bekliyor"
                },
            )
        }
        return Data(
            studentName = student.name,
            subject = student.subject,
            grade = student.grade,
            teacherName = teacherName.trim(),
            periodTitle = period.headline,
            rangeText = "${Fmt.long(start)} – ${Fmt.long(addDays(end, -1))}",
            completedCount = completed.size,
            cancelledCount = inRange.count { it.isCancelled },
            totalMinutes = completed.sumOf { it.duration },
            earned = completed.sumOf { notebook.fee(it) },
            balance = notebook.balance(student.id),
            lessons = lessons,
            homeworks = homeworks,
        )
    }

    // Sabit kağıt renkleri
    private val INK = Color.rgb(0x26, 0x30, 0x3E)
    private val SOFT = Color.rgb(0x77, 0x80, 0x8D)
    private val BOARD = Color.rgb(0x1E, 0x4B, 0x39)
    private val LINE = Color.argb(31, 0x26, 0x30, 0x3E)
    private val TINT = Color.argb(15, 0x1E, 0x4B, 0x39)

    /// A4 genişliği (72 dpi punto)
    private const val PAGE_WIDTH = 595
    private const val PAD = 36f

    private fun paint(size: Float, color: Int, typeface: Typeface = Typeface.DEFAULT) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
    }

    private val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private const val ROW_HEIGHT = 26f

    /// Sayfa yüksekliği içeriğe göre belirlenir; uzun rapor sessizce kırpılmaz.
    private fun height(d: Data): Int {
        var h = PAD
        h += 62f + 22f        // başlık + boşluk
        h += 48f + 22f        // istatistikler
        if (d.lessons.isNotEmpty()) h += 26f + d.lessons.size * ROW_HEIGHT + 22f
        if (d.homeworks.isNotEmpty()) h += 26f + d.homeworks.size * ROW_HEIGHT + 22f
        h += (if (d.cancelledCount > 0) 30f else 16f)
        h += PAD
        return h.toInt()
    }

    private fun Canvas.text(value: String, x: Float, baseline: Float, paint: TextPaint, maxWidth: Float? = null, alignRight: Boolean = false) {
        val shown = if (maxWidth != null) TextUtils.ellipsize(value, paint, maxWidth, TextUtils.TruncateAt.END).toString() else value
        val drawX = if (alignRight) x - paint.measureText(shown) else x
        drawText(shown, drawX, baseline, paint)
    }

    private fun draw(canvas: Canvas, d: Data) {
        val width = PAGE_WIDTH.toFloat()
        val right = width - PAD
        canvas.drawColor(Color.WHITE)
        var y = PAD

        // Başlık
        val namePaint = paint(26f, INK, serifBold)
        canvas.text(d.studentName, PAD, y + 24f, namePaint, maxWidth = width * 0.55f)
        canvas.text(listOf(d.subject, d.grade).filter { it.isNotEmpty() }.joinToString(" • "), PAD, y + 44f, paint(12f, SOFT), maxWidth = width * 0.55f)
        canvas.text("${d.periodTitle} Ders Raporu", right, y + 14f, paint(13f, BOARD, bold), alignRight = true)
        canvas.text(d.rangeText, right, y + 30f, paint(11f, SOFT), alignRight = true)
        y += 56f
        canvas.drawRect(PAD, y, right, y + 2f, Paint().apply { color = BOARD })
        y += 6f + 22f

        // İstatistikler
        val stats = listOf(
            "İşlenen ders" to "${d.completedCount}",
            "Toplam süre" to Fmt.hours(d.totalMinutes),
            "Ders tutarı" to Fmt.money(d.earned),
            "Bakiye" to d.balanceText,
        )
        val gap = 10f
        val boxW = (right - PAD - gap * 3) / 4
        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TINT }
        stats.forEachIndexed { i, (label, value) ->
            val x = PAD + i * (boxW + gap)
            canvas.drawRoundRect(RectF(x, y, x + boxW, y + 48f), 8f, 8f, tintPaint)
            canvas.text(label, x + 12f, y + 18f, paint(10f, SOFT), maxWidth = boxW - 24f)
            canvas.text(value, x + 12f, y + 37f, paint(15f, INK, bold), maxWidth = boxW - 24f)
        }
        y += 48f + 22f

        fun section(title: String, rows: List<Row>) {
            canvas.text(title, PAD, y + 14f, paint(14f, INK, serifBold))
            y += 26f
            val linePaint = Paint().apply { color = LINE }
            rows.forEach { row ->
                val base = y + 17f
                canvas.text(row.leading, PAD, base, paint(11f, SOFT, medium), maxWidth = 92f)
                canvas.text(row.title, PAD + 104f, base, paint(12f, INK), maxWidth = right - PAD - 104f - 90f)
                canvas.text(row.trailing, right, base, paint(11f, SOFT, medium), maxWidth = 78f, alignRight = true)
                y += ROW_HEIGHT
                canvas.drawRect(PAD, y - 0.5f, right, y, linePaint)
            }
            y += 22f
        }
        if (d.lessons.isNotEmpty()) section("Dersler", d.lessons)
        if (d.homeworks.isNotEmpty()) section("Ödevler", d.homeworks)

        // Alt bilgi
        val soft10 = paint(10f, SOFT)
        if (d.cancelledCount > 0) {
            canvas.text("Dönem içinde ${d.cancelledCount} ders iptal edildi.", PAD, y + 10f, soft10)
            y += 14f
        }
        canvas.text(
            if (d.teacherName.isEmpty()) "Ders Defteri ile hazırlandı." else "${d.teacherName} • Ders Defteri ile hazırlandı.",
            PAD, y + 10f, soft10,
        )
    }

    /// Raporu PDF olarak önbellek klasörüne yazar ve dosyayı döndürür.
    fun makePdf(context: Context, notebook: Notebook, student: Student, period: SummaryPeriod, teacherName: String): File? {
        val d = data(notebook, student, period, teacherName)
        val document = PdfDocument()
        return try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, height(d), 1).create())
            draw(page.canvas, d)
            document.finishPage(page)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, d.fileName)
            file.outputStream().use { document.writeTo(it) }
            file
        } catch (e: Exception) {
            null
        } finally {
            document.close()
        }
    }
}
