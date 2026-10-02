package xyz.adilemree.dersdefteri

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import xyz.adilemree.dersdefteri.data.CancellationReason
import xyz.adilemree.dersdefteri.data.Homework
import xyz.adilemree.dersdefteri.data.Lesson
import xyz.adilemree.dersdefteri.data.LessonStatus
import xyz.adilemree.dersdefteri.data.LessonTemplate
import xyz.adilemree.dersdefteri.data.Payment
import xyz.adilemree.dersdefteri.data.PaymentMethod
import xyz.adilemree.dersdefteri.data.Recurring
import xyz.adilemree.dersdefteri.data.Student
import xyz.adilemree.dersdefteri.data.addDays
import xyz.adilemree.dersdefteri.data.atTime
import xyz.adilemree.dersdefteri.data.startOfDay

/// Yalnızca geliştirme sürümünde: ekran görüntüleri için gerçekçi bir örnek
/// defter yükler. Mağaza sürümüne girmez.
///
/// adb shell am broadcast -n xyz.adilemree.dersdefteri.debug/xyz.adilemree.dersdefteri.DemoDataReceiver
class DemoDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.app
        app.scope.launch {
            try {
                app.repository.edit { seed(this) }
                app.settings.teacherName.value = "Selin"
                app.settings.hasCompletedOnboarding.value = true
                app.notifications.resync()
            } finally {
                result.finish()
            }
        }
    }

    private fun seed(e: xyz.adilemree.dersdefteri.data.NotebookEditor) {
        e.deleteAll()
        val now = System.currentTimeMillis()
        val today = startOfDay(now)

        fun student(name: String, subject: String, grade: String, rate: Double, color: Int, parent: String, phone: String) =
            Student(
                name = name, subject = subject, grade = grade, hourlyRate = rate, colorIndex = color,
                parentName = parent, parentPhone = phone, phone = "",
                startDate = addDays(today, -60),
            ).also { e.students[it.id] = it }

        val ayse = student("Ayşe Yılmaz", "Matematik", "11. Sınıf", 750.0, 0, "Fatma Yılmaz", "0532 114 25 36")
        val can = student("Can Demir", "Fizik", "12. Sınıf", 800.0, 1, "Murat Demir", "0533 218 47 59")
        val elif = student("Elif Kaya", "İngilizce", "9. Sınıf", 600.0, 4, "Zeynep Kaya", "0542 391 62 70")
        val mert = student("Mert Aydın", "Kimya", "10. Sınıf", 700.0, 2, "Hakan Aydın", "0505 472 83 14")
        val zeynep = student("Zeynep Şahin", "Türkçe", "8. Sınıf", 550.0, 6, "Ayten Şahin", "0536 625 19 48")
        val deniz = student("Deniz Arslan", "Matematik", "7. Sınıf", 500.0, 5, "Burak Arslan", "0541 738 26 51")

        // Haftalık dersler (Calendar.weekday: 2 = Pazartesi ... 7 = Cumartesi)
        fun weekly(s: Student, weekday: Int, hour: Int, minute: Int, duration: Int) =
            LessonTemplate(studentId = s.id, weekday = weekday, hour = hour, minute = minute, duration = duration, createdAt = addDays(now, -50))
                .also { e.templates[it.id] = it }

        val templates = listOf(
            weekly(ayse, 3, 17, 0, 90), weekly(ayse, 5, 17, 0, 90),
            weekly(can, 2, 18, 30, 60), weekly(can, 4, 18, 30, 60),
            weekly(elif, 3, 15, 0, 60),
            weekly(mert, 6, 16, 0, 90),
            weekly(zeynep, 7, 11, 0, 60),
            weekly(deniz, 4, 15, 30, 60),
        )

        // Geçmiş beş haftanın dersleri: çoğu işlendi, birkaçı iptal, dünün birkaçı işaretlenmemiş.
        val topics = mapOf(
            ayse.id to listOf("Türev", "İntegral", "Limit", "Logaritma", "Trigonometri"),
            can.id to listOf("Elektrik alan", "Manyetizma", "Dalgalar", "Optik"),
            elif.id to listOf("Present Perfect", "Reading", "Vocabulary"),
            mert.id to listOf("Mol kavramı", "Asit ve bazlar", "Gazlar"),
            zeynep.id to listOf("Paragraf", "Fiilimsiler", "Cümlenin ögeleri"),
            deniz.id to listOf("Kesirler", "Oran orantı", "Cebirsel ifadeler"),
        )
        val yesterday = addDays(today, -1)
        var counter = 0
        for (t in templates) {
            val s = e.students.getValue(t.studentId!!)
            val slots = Recurring.occurrences(t.weekday, t.hour, t.minute, addDays(today, -35), today)
            for (slot in slots) {
                counter++
                val unmarked = slot >= yesterday && slot < today
                val status = when {
                    unmarked -> LessonStatus.PLANNED
                    counter % 11 == 0 -> LessonStatus.CANCELLED
                    else -> LessonStatus.COMPLETED
                }
                val reason = if (status == LessonStatus.CANCELLED) {
                    if (counter % 2 == 0) CancellationReason.STUDENT else CancellationReason.MAKEUP
                } else CancellationReason.NONE
                val lesson = Lesson(
                    id = Recurring.lessonId(t.id, slot),
                    studentId = s.id, templateId = t.id, date = slot, duration = t.duration,
                    status = status.raw, cancellationReason = reason.raw,
                    topic = topics[s.id]!!.let { it[counter % it.size] },
                    feeOverride = Lesson.standardFee(s, t.duration),
                )
                e.lessons[lesson.id] = lesson
            }
            e.templates[t.id] = t.copy(generatedUntil = today)
        }

        // Bugün: biri işlenmiş, ikisi planlı ders (biri bitmiş, işaretlenmeyi bekliyor)
        fun oneOff(s: Student, day: Long, hour: Int, minute: Int, duration: Int, status: LessonStatus, topic: String) {
            val lesson = Lesson(
                studentId = s.id, date = atTime(day, hour, minute), duration = duration, status = status.raw,
                topic = topic, feeOverride = Lesson.standardFee(s, duration),
            )
            e.lessons[lesson.id] = lesson
        }
        oneOff(deniz, today, 10, 0, 60, LessonStatus.COMPLETED, "Kesirler")
        oneOff(elif, today, 13, 30, 60, LessonStatus.PLANNED, "Speaking")
        oneOff(zeynep, today, 19, 0, 60, LessonStatus.PLANNED, "Deneme çözümü")

        // Ödemeler
        fun pay(s: Student, daysAgo: Int, amount: Double, method: PaymentMethod, note: String = "") =
            Payment(studentId = s.id, date = addDays(today, -daysAgo) + 12 * 3_600_000L, amount = amount, method = method.raw, note = note)
                .also { e.payments[it.id] = it }

        // Ayşe: son işlenen dört dersi toplu ödeme
        val ayseDone = e.lessons.values.filter { it.studentId == ayse.id && it.isCompleted }.sortedBy { it.date }
        val bulk = pay(ayse, 1, ayseDone.takeLast(4).sumOf { e.fee(it) }, PaymentMethod.TRANSFER, "Eylül dersleri")
        ayseDone.takeLast(4).forEach { e.lessons[it.id] = it.copy(paymentId = bulk.id) }
        pay(ayse, 24, ayseDone.dropLast(4).sumOf { e.fee(it) }, PaymentMethod.TRANSFER)

        val canDone = e.lessons.values.filter { it.studentId == can.id && it.isCompleted }.sumOf { e.fee(it) }
        pay(can, 18, (canDone * 0.5 / 100).toInt() * 100.0, PaymentMethod.CASH)
        val elifDone = e.lessons.values.filter { it.studentId == elif.id && it.isCompleted }.sumOf { e.fee(it) }
        pay(elif, 0, elifDone + 1200.0, PaymentMethod.TRANSFER, "Ekim peşin")
        val zeynepDone = e.lessons.values.filter { it.studentId == zeynep.id && it.isCompleted }.sumOf { e.fee(it) }
        pay(zeynep, 6, zeynepDone, PaymentMethod.CASH)
        val denizDone = e.lessons.values.filter { it.studentId == deniz.id && it.isCompleted }.sumOf { e.fee(it) }
        pay(deniz, 12, denizDone - 500.0, PaymentMethod.OTHER)
        pay(mert, 30, 2100.0, PaymentMethod.TRANSFER)

        // Ödevler
        fun hw(s: Student, title: String, detail: String, dueIn: Int, done: Boolean = false) =
            Homework(
                studentId = s.id, title = title, detail = detail,
                assignedDate = addDays(today, -7), dueDate = addDays(today, dueIn),
                isDone = done, doneDate = if (done) addDays(now, -1) else null,
            ).also { e.homeworks[it.id] = it }
        hw(ayse, "Türev testi 1-20", "Kitap sayfa 112", 2)
        hw(can, "Elektrik alan soruları", "Föy 4, ilk iki bölüm", 0)
        hw(mert, "Mol hesapları", "15 soru", -2)
        hw(zeynep, "Paragraf denemesi", "40 soru, süre tut", 4)
        hw(elif, "Essay: My Summer", "150 kelime", 1)
        hw(deniz, "Kesir alıştırmaları", "Sayfa 34-35", -5, done = true)

        e.topUp()
    }
}
