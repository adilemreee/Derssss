package xyz.adilemree.dersdefteri.data

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max

// Defteri değiştiren her işlem buradan geçer. Düzenleyici defterin bir
// kopyasını alır; işlemler kopyayı değiştirir, sonunda yalnızca değişen
// kayıtlar veritabanına yazılır. iOS'taki SwiftData kodu nesneleri yerinde
// değiştiriyordu; aynı kurallar burada aynı sırayla uygulanır.

class NotebookEditor(base: Notebook, val now: Long = System.currentTimeMillis()) {
    private val originalStudents = base.studentById
    private val originalLessons = base.lessonById
    private val originalPayments = base.paymentById
    private val originalHomeworks = base.homeworkById
    private val originalTemplates = base.templateById

    val students = LinkedHashMap(base.studentById)
    val lessons = LinkedHashMap(base.lessonById)
    val payments = LinkedHashMap(base.paymentById)
    val homeworks = LinkedHashMap(base.homeworkById)
    val templates = LinkedHashMap(base.templateById)

    fun fee(lesson: Lesson): Double =
        lesson.feeOverride ?: Lesson.standardFee(lesson.studentId?.let { students[it] }, lesson.duration)

    private fun updateLesson(id: String, change: (Lesson) -> Lesson) {
        lessons[id]?.let { lessons[id] = change(it) }
    }

    // MARK: - Silme (iOS'taki ilişki kurallarıyla)

    fun deleteLesson(id: String) {
        lessons.remove(id)
    }

    /// Toplu ödeme silinince dersleri kalır, yalnız bağları kopar.
    fun deletePayment(id: String) {
        payments.remove(id)
        for (lesson in lessons.values.filter { it.paymentId == id }) {
            lessons[lesson.id] = lesson.copy(paymentId = null)
        }
    }

    /// Haftalık ders silinince üretilmiş dersleri kalır, yalnız seriden çıkar.
    fun deleteTemplate(id: String) {
        templates.remove(id)
        for (lesson in lessons.values.filter { it.templateId == id }) {
            lessons[lesson.id] = lesson.copy(templateId = null)
        }
    }

    /// Öğrenci silinince dersleri, ödemeleri, ödevleri ve haftalık dersleri de silinir.
    fun deleteStudent(id: String) {
        students.remove(id)
        lessons.values.filter { it.studentId == id }.forEach { lessons.remove(it.id) }
        payments.values.filter { it.studentId == id }.map { it.id }.forEach { deletePayment(it) }
        homeworks.values.filter { it.studentId == id }.forEach { homeworks.remove(it.id) }
        templates.values.filter { it.studentId == id }.map { it.id }.forEach { deleteTemplate(it) }
    }

    fun deleteAll() {
        students.clear(); lessons.clear(); payments.clear(); homeworks.clear(); templates.clear()
    }

    // MARK: - Ders durumu

    fun setStatus(lessonId: String, status: LessonStatus, reason: CancellationReason = CancellationReason.NONE) {
        updateLesson(lessonId) { it.withStatus(status, reason) }
    }

    /// Bildirim ve widget'tan gelen işaret: bu arada iptal edilmiş ya da
    /// silinmiş derse dokunulmaz.
    fun markCompletedIfPlanned(ids: Collection<String>): Int {
        var count = 0
        for (id in ids) {
            val lesson = lessons[id] ?: continue
            if (!lesson.isPlanned) continue
            lessons[id] = lesson.withStatus(LessonStatus.COMPLETED)
            count++
        }
        return count
    }

    // MARK: - Ders işlemleri

    fun copyLesson(lessonId: String, date: Long) {
        val lesson = lessons[lessonId] ?: return
        val fee = if (lesson.usesCustomFee) fee(lesson)
        else Lesson.standardFee(lesson.studentId?.let { students[it] }, lesson.duration)
        val new = Lesson(
            studentId = lesson.studentId,
            date = date,
            duration = lesson.duration,
            topic = lesson.topic,
            note = lesson.note,
            feeOverride = fee,
            usesCustomFee = lesson.usesCustomFee,
        )
        lessons[new.id] = new
    }

    fun copyNextWeek(lessonId: String) {
        val lesson = lessons[lessonId] ?: return
        copyLesson(lessonId, addDays(lesson.date, 7))
    }

    /// Seriyi bu dersten sonra bitirir; bu ders ve öncekiler kalır.
    fun stopRepeating(afterLessonId: String) {
        val lesson = lessons[afterLessonId] ?: return
        val templateId = lesson.templateId ?: return
        endSeries(templateId, after = lesson.date)
    }

    /// Bu ders ve serinin sonraki planlı dersleri silinir, seri biter.
    fun deleteThisAndFollowing(lessonId: String) {
        val lesson = lessons[lessonId] ?: return
        lesson.templateId?.let { endSeries(it, after = lesson.date) }
        lessons.remove(lessonId)
    }

    enum class SaveScope { ONLY_THIS, THIS_AND_FOLLOWING }

    data class LessonInput(
        val lessonId: String?,
        val studentId: String,
        val start: Long,
        val duration: Int,
        val status: LessonStatus,
        val cancellationReason: CancellationReason,
        val topic: String,
        val note: String,
        val useCustomFee: Boolean,
        val customFee: Double,
        val detachFromPayment: Boolean,
        val repeatsWeekly: Boolean,
    )

    /// Ders formunun kaydı (yeni ders ya da düzenleme).
    fun saveLesson(input: LessonInput, scope: SaveScope) {
        val student = students[input.studentId] ?: return
        val start = input.start
        val fee = if (input.useCustomFee) input.customFee else Lesson.standardFee(student, input.duration)
        val existing = input.lessonId?.let { lessons[it] }
        val reason = if (input.status == LessonStatus.CANCELLED) input.cancellationReason else CancellationReason.NONE

        if (existing != null) {
            val studentChanged = existing.studentId != student.id
            if (scope == SaveScope.THIS_AND_FOLLOWING && existing.templateId != null) {
                // Sonraki dersler silinip yeniden üretilmez, taşınır: konu ve notlar kalır.
                applyToFollowing(
                    templateId = existing.templateId,
                    from = existing.date,
                    excludingId = existing.id,
                    dayShift = daysBetween(existing.date, start),
                    newWeekday = calendarWeekday(start),
                    hour = hourOf(start),
                    minute = minuteOf(start),
                    duration = input.duration,
                    feeOverride = if (input.useCustomFee) input.customFee else null,
                    usesCustomFee = input.useCustomFee,
                )
            }
            var updated = lessons[existing.id] ?: existing
            // Başka öğrenciye taşınan ders o öğrencinin serisinden çıkar.
            if (studentChanged) updated = updated.copy(templateId = null)
            updated = updated.copy(
                studentId = student.id,
                date = start,
                duration = input.duration,
                status = input.status.raw,
                cancellationReason = reason.raw,
                topic = input.topic,
                note = input.note,
                feeOverride = fee,
                usesCustomFee = input.useCustomFee,
            )
            // Başka öğrenciye taşınan ders o öğrencinin ödemesiyle ödenmiş sayılmaz.
            if (input.detachFromPayment || studentChanged) updated = updated.copy(paymentId = null)
            lessons[updated.id] = updated
            if (input.repeatsWeekly && updated.templateId == null) startSeries(updated.id)
        } else {
            val new = Lesson(
                studentId = student.id,
                date = start,
                duration = input.duration,
                status = input.status.raw,
                cancellationReason = reason.raw,
                topic = input.topic,
                note = input.note,
                feeOverride = fee,
                usesCustomFee = input.useCustomFee,
            )
            lessons[new.id] = new
            if (input.repeatsWeekly) startSeries(new.id)
        }
    }

    /// Ana ekrandan hızlı ders: planlı, o günkü saatlik ücretle.
    fun quickLesson(studentId: String, start: Long, duration: Int, repeatsWeekly: Boolean) {
        val student = students[studentId] ?: return
        val lesson = Lesson(
            studentId = student.id,
            date = start,
            duration = duration,
            feeOverride = Lesson.standardFee(student, duration),
            usesCustomFee = false,
        )
        lessons[lesson.id] = lesson
        if (repeatsWeekly) startSeries(lesson.id)
    }

    // MARK: - Haftalık dersler

    /// Tüm şablonlar için ufka kadar eksik dersleri üretir.
    /// Uygulama açılışında ve şablon kaydedildiğinde çağrılır.
    fun topUp() {
        if (templates.isEmpty()) return
        val existing = lessons.values.toMutableList()
        val horizon = addDays(startOfDay(now), Recurring.HORIZON_DAYS)

        for (template in templates.values.toList()) {
            if (template.isPaused) continue
            val student = template.studentId?.let { students[it] } ?: continue
            if (student.isArchived) continue

            // Daha önce üretilen aralığı tekrar üretme; silinen ders geri gelmesin
            val from = max(template.generatedUntil ?: now, now)
            if (from >= horizon) continue

            for (slot in Recurring.occurrences(template.weekday, template.hour, template.minute, from, horizon)) {
                val end = slot + template.duration * 60_000L
                val clash = existing.any { !it.isCancelled && slot < it.endDate && it.date < end }
                if (clash) continue
                val id = Recurring.lessonId(template.id, slot)
                if (lessons.containsKey(id)) continue

                val fee = template.feeOverride ?: Lesson.standardFee(student, template.duration)
                val lesson = Lesson(
                    id = id,
                    studentId = student.id,
                    templateId = template.id,
                    date = slot,
                    duration = template.duration,
                    feeOverride = fee,
                    usesCustomFee = template.usesCustomFee,
                )
                lessons[id] = lesson
                existing += lesson
            }

            if (template.generatedUntil != horizon) {
                templates[template.id] = template.copy(generatedUntil = horizon)
            }
        }
    }

    /// Bir dersi haftalık serinin ilk dersi yapar. Sonraki haftalar `topUp`
    /// ile ufka kadar üretilir.
    fun startSeries(lessonId: String): String? {
        val lesson = lessons[lessonId] ?: return null
        val student = lesson.studentId?.let { students[it] }
        if (student == null || lesson.templateId != null) return lesson.templateId
        val template = LessonTemplate(
            studentId = student.id,
            weekday = calendarWeekday(lesson.date),
            hour = hourOf(lesson.date),
            minute = minuteOf(lesson.date),
            duration = lesson.duration,
            feeOverride = if (lesson.usesCustomFee) lesson.feeOverride else null,
            usesCustomFee = lesson.usesCustomFee,
            // Bu dersin haftası zaten var; üretim bir sonraki haftadan başlar.
            generatedUntil = lesson.date + 60_000L,
        )
        templates[template.id] = template
        lessons[lessonId] = lesson.copy(templateId = template.id)
        topUp()
        return template.id
    }

    /// "Bu ve sonraki dersler": serinin `from` tarihinden itibaren planlı
    /// derslerini silmeden yeni gün/saat/süre/ücrete taşır; konu ve notlar
    /// korunur. Önceki dersler olduğu gibi kalır.
    fun applyToFollowing(
        templateId: String,
        from: Long,
        excludingId: String?,
        dayShift: Int,
        newWeekday: Int,
        hour: Int,
        minute: Int,
        duration: Int,
        feeOverride: Double?,
        usesCustomFee: Boolean,
    ) {
        val template = templates[templateId] ?: return
        val following = lessons.values
            .filter { it.templateId == templateId && it.isPlanned && it.date >= from && it.id != excludingId }
            .sortedBy { it.date }

        for (lesson in following) {
            var updated = lesson
            // Yeni yeri geçmişte kalan ders bu hafta eski gününde yapılır;
            // taşıma sonraki haftadan başlar. Geçmişte "planlı" ders bırakılmaz.
            val newDate = atTime(addDays(startOfDay(lesson.date), dayShift), hour, minute)
            if (newDate >= now || excludingId != null) updated = updated.copy(date = newDate)
            updated = updated.copy(duration = duration)
            updated = if (usesCustomFee && feeOverride != null) {
                updated.copy(feeOverride = feeOverride, usesCustomFee = true)
            } else {
                updated.copy(
                    feeOverride = Lesson.standardFee(updated.studentId?.let { students[it] }, duration),
                    usesCustomFee = false,
                )
            }
            lessons[lesson.id] = updated
        }

        templates[templateId] = template.copy(
            weekday = newWeekday,
            hour = hour,
            minute = minute,
            duration = duration,
            feeOverride = if (usesCustomFee) feeOverride else null,
            usesCustomFee = usesCustomFee,
        )
        topUp()
    }

    /// Seriyi bitirir: `after` tarihinden sonraki planlı dersler silinir, seri
    /// yeni ders üretmez. Önceki ve işlenmiş dersler kalır.
    fun endSeries(templateId: String, after: Long) {
        lessons.values
            .filter { it.templateId == templateId && it.isPlanned && it.date > after }
            .forEach { lessons.remove(it.id) }
        deleteTemplate(templateId)
    }

    /// Şablondan üretilmiş, henüz işlenmemiş gelecek dersleri siler.
    fun deleteUpcomingLessons(templateId: String) {
        lessons.values
            .filter { it.templateId == templateId && it.date > now && it.isPlanned }
            .forEach { lessons.remove(it.id) }
    }

    fun togglePause(templateId: String) {
        val template = templates[templateId] ?: return
        templates[templateId] = template.copy(isPaused = !template.isPaused)
        if (template.isPaused) topUp()
    }

    /// Haftalık ders formunun kaydı.
    fun saveTemplate(
        templateId: String?,
        studentId: String,
        weekdays: Set<Int>,
        hour: Int,
        minute: Int,
        duration: Int,
        useCustomFee: Boolean,
        customFee: Double,
        isPaused: Boolean,
    ) {
        val student = students[studentId] ?: return
        val fee: Double? = if (useCustomFee) customFee else null
        val template = templateId?.let { templates[it] }

        if (template != null) {
            val newWeekday = weekdays.firstOrNull() ?: template.weekday
            val scheduleChanged = template.weekday != newWeekday ||
                template.hour != hour ||
                template.minute != minute ||
                template.duration != duration ||
                template.usesCustomFee != useCustomFee ||
                (useCustomFee && template.feeOverride != fee)
            var updated = template
            if (template.studentId != student.id) {
                // Gelecek planlı dersler de yeni öğrenciye geçer.
                lessons.values
                    .filter { it.templateId == template.id && it.isPlanned && it.date > now }
                    .forEach { lessons[it.id] = it.copy(studentId = student.id) }
                updated = updated.copy(studentId = student.id)
            }
            updated = updated.copy(isPaused = isPaused)
            templates[updated.id] = updated
            if (scheduleChanged) {
                // Silip yeniden üretmek yerine taşır: konu ve notlar kalır.
                applyToFollowing(
                    templateId = updated.id,
                    from = now,
                    excludingId = null,
                    dayShift = Recurring.dayShift(fromWeekday = template.weekday, toWeekday = newWeekday),
                    newWeekday = newWeekday,
                    hour = hour,
                    minute = minute,
                    duration = duration,
                    feeOverride = fee,
                    usesCustomFee = useCustomFee,
                )
            } else {
                topUp()
            }
        } else {
            for (weekday in LessonTemplate.weekdayOrder.filter { it in weekdays }) {
                val new = LessonTemplate(
                    studentId = student.id,
                    weekday = weekday,
                    hour = hour,
                    minute = minute,
                    duration = duration,
                    feeOverride = fee,
                    usesCustomFee = useCustomFee,
                )
                templates[new.id] = new
            }
            topUp()
        }
    }

    // MARK: - Öğrenciler

    data class StudentInput(
        val name: String,
        val subject: String,
        val grade: String,
        val phone: String,
        val parentName: String,
        val parentPhone: String,
        val hourlyRate: Double,
        val startDate: Long,
        val colorIndex: Int,
        val notes: String,
    )

    /// Öğrenci formunun kaydı. Yeni öğrencide haftalık ders günleri seçildiyse
    /// şablonlar da kurulur.
    fun saveStudent(
        studentId: String?,
        input: StudentInput,
        weeklyDays: Set<Int> = emptySet(),
        weeklyHour: Int = 17,
        weeklyMinute: Int = 0,
        weeklyDuration: Int = 60,
    ): String {
        val existing = studentId?.let { students[it] }
        if (existing != null) {
            val rateChanged = abs(existing.hourlyRate - input.hourlyRate) > 0.001
            if (rateChanged) lockExistingStandardFees(existing.id)
            students[existing.id] = existing.copy(
                name = input.name,
                subject = input.subject,
                grade = input.grade,
                phone = input.phone,
                parentName = input.parentName,
                parentPhone = input.parentPhone,
                hourlyRate = input.hourlyRate,
                startDate = input.startDate,
                colorIndex = input.colorIndex,
                notes = input.notes,
            )
            if (rateChanged) applyRateToPlannedLessons(existing.id)
            return existing.id
        }

        val new = Student(
            name = input.name,
            subject = input.subject,
            grade = input.grade,
            phone = input.phone,
            parentName = input.parentName,
            parentPhone = input.parentPhone,
            hourlyRate = input.hourlyRate,
            startDate = input.startDate,
            colorIndex = input.colorIndex,
            notes = input.notes,
        )
        students[new.id] = new
        if (weeklyDays.isNotEmpty()) {
            for (weekday in LessonTemplate.weekdayOrder.filter { it in weeklyDays }) {
                // İleri tarihli başlangıçta dersler o günden itibaren planlanır.
                val startDay = startOfDay(input.startDate)
                val template = LessonTemplate(
                    studentId = new.id,
                    weekday = weekday,
                    hour = weeklyHour,
                    minute = weeklyMinute,
                    duration = weeklyDuration,
                    generatedUntil = if (startDay > now) startDay else null,
                )
                templates[template.id] = template
            }
            topUp()
        }
        return new.id
    }

    /// Ücret değişmeden önce, saatlik ücretten hesaplanan derslerin tutarı kilitlenir.
    private fun lockExistingStandardFees(studentId: String) {
        val student = students[studentId] ?: return
        for (lesson in lessons.values.filter { it.studentId == studentId && !it.usesCustomFee }) {
            if (lesson.feeOverride == null) {
                lessons[lesson.id] = lesson.copy(
                    feeOverride = Lesson.standardFee(student, lesson.duration),
                    usesCustomFee = false,
                )
            }
        }
    }

    /// Saatlik ücret değişince henüz işlenmemiş dersler yeni ücrete geçer.
    /// İşlenmiş, iptal ve derse özel ücretli dersler değişmez. Bugünden önceki
    /// planlı dersler de eski ücretle kalır; yalnız hiç fiyatlanmamış (0 ₺)
    /// olanlar yeni ücreti alır.
    private fun applyRateToPlannedLessons(studentId: String) {
        val student = students[studentId] ?: return
        val today = startOfDay(now)
        for (lesson in lessons.values.filter { it.studentId == studentId }) {
            if (lesson.isPlanned && !lesson.usesCustomFee &&
                (lesson.date >= today || (lesson.feeOverride ?: 0.0) < 0.005)
            ) {
                lessons[lesson.id] = lesson.copy(feeOverride = Lesson.standardFee(student, lesson.duration))
            }
        }
    }

    /// Arşivlenince programdan kalkacak dersler: henüz yapılmamış planlı dersler.
    fun upcomingPlannedLessons(studentId: String): List<Lesson> =
        lessons.values.filter { it.studentId == studentId && it.isPlanned && it.date > now }

    /// Arşivdeki öğrencinin dersi programda ve bildirimlerde durmasın.
    /// İşlenmiş dersler, ödemeler ve ödevler kalır; haftalık dersler de silinmez,
    /// yalnız öğrenci arşivdeyken yeni ders üretmez.
    fun archive(studentId: String) {
        upcomingPlannedLessons(studentId).forEach { lessons.remove(it.id) }
        students[studentId]?.let { students[studentId] = it.copy(isArchived = true) }
    }

    /// Aktife alınan öğrencinin haftalık dersleri bugünden itibaren yeniden planlanır.
    fun unarchive(studentId: String) {
        val student = students[studentId] ?: return
        students[studentId] = student.copy(isArchived = false)
        for (template in templates.values.filter { it.studentId == studentId }) {
            // Ders kimlikleri şablon kimliğinden türetilir. Arşivlerken silinen
            // haftalar aynı kimlikle geri gelirse sunucu onları silinmiş sayar
            // ve bir sonraki eşitlemede yeniden siler; yeni kimlik bunu önler.
            val renewed = template.copy(id = newId(), generatedUntil = null)
            templates.remove(template.id)
            templates[renewed.id] = renewed
            for (lesson in lessons.values.filter { it.templateId == template.id }) {
                lessons[lesson.id] = lesson.copy(templateId = renewed.id)
            }
        }
        topUp()
    }

    // MARK: - Ödemeler

    fun savePayment(
        paymentId: String?,
        studentId: String,
        amount: Double,
        date: Long,
        method: PaymentMethod,
        note: String,
        lessonIds: Set<String>,
    ) {
        val student = students[studentId] ?: return
        val existing = paymentId?.let { payments[it] }
        val target = existing?.copy(
            studentId = student.id, amount = amount, date = date, method = method.raw, note = note,
        ) ?: Payment(studentId = student.id, date = date, amount = amount, method = method.raw, note = note)
        payments[target.id] = target

        // Seçimden çıkarılan (ve iptal edilmiş) dersler ödemeden ayrılır.
        for (lesson in lessons.values.filter { it.paymentId == target.id && it.id !in lessonIds }) {
            lessons[lesson.id] = lesson.copy(paymentId = null)
        }
        for (id in lessonIds) {
            val lesson = lessons[id] ?: continue
            if (lesson.studentId != student.id) continue
            lessons[id] = lesson.copy(paymentId = target.id)
        }
    }

    // MARK: - Ödevler

    fun saveHomework(homeworkId: String?, studentId: String, title: String, detail: String, dueDate: Long) {
        val existing = homeworkId?.let { homeworks[it] }
        if (existing != null) {
            homeworks[existing.id] = existing.copy(studentId = studentId, title = title, detail = detail, dueDate = dueDate)
        } else {
            val new = Homework(studentId = studentId, title = title, detail = detail, assignedDate = now, dueDate = dueDate)
            homeworks[new.id] = new
        }
    }

    fun toggleHomework(homeworkId: String) {
        val hw = homeworks[homeworkId] ?: return
        val done = !hw.isDone
        homeworks[homeworkId] = hw.copy(isDone = done, doneDate = if (done) now else null)
    }

    // MARK: - Değişiklikler

    class Changes(
        val students: List<Student>,
        val lessons: List<Lesson>,
        val payments: List<Payment>,
        val homeworks: List<Homework>,
        val templates: List<LessonTemplate>,
        val deletedStudents: List<String>,
        val deletedLessons: List<String>,
        val deletedPayments: List<String>,
        val deletedHomeworks: List<String>,
        val deletedTemplates: List<String>,
    ) {
        val isEmpty: Boolean
            get() = students.isEmpty() && lessons.isEmpty() && payments.isEmpty() && homeworks.isEmpty() &&
                templates.isEmpty() && deletedStudents.isEmpty() && deletedLessons.isEmpty() &&
                deletedPayments.isEmpty() && deletedHomeworks.isEmpty() && deletedTemplates.isEmpty()
    }

    private fun <T> changed(original: Map<String, T>, current: Map<String, T>): List<T> =
        current.entries.filter { original[it.key] != it.value }.map { it.value }

    private fun <T> removed(original: Map<String, T>, current: Map<String, T>): List<String> =
        original.keys.filter { it !in current }

    fun changes(): Changes = Changes(
        students = changed(originalStudents, students),
        lessons = changed(originalLessons, lessons),
        payments = changed(originalPayments, payments),
        homeworks = changed(originalHomeworks, homeworks),
        templates = changed(originalTemplates, templates),
        deletedStudents = removed(originalStudents, students),
        deletedLessons = removed(originalLessons, lessons),
        deletedPayments = removed(originalPayments, payments),
        deletedHomeworks = removed(originalHomeworks, homeworks),
        deletedTemplates = removed(originalTemplates, templates),
    )
}

// MARK: - Haftalık ders kuralları

object Recurring {
    /// Kaç gün ilerisi için ders üretilir
    const val HORIZON_DAYS = 28

    /// Şablondan üretilen dersin kimliği şablon ve saatten türetilir.
    ///
    /// Eşitlenen iki cihaz aynı şablondan aynı haftanın dersini birbirinden
    /// habersiz üretirse, kimlik rastgele olsaydı sunucuda iki ayrı ders
    /// oluşurdu. Türetilmiş kimlikle ikisi aynı kayda düşer. Tohum iOS'takiyle
    /// aynıdır (büyük harfli UUID ve saniye).
    fun lessonId(templateId: String, slot: Long): String {
        val seed = "${templateId.uppercase(java.util.Locale.ROOT)}|${slot / 1000}"
        val b = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8)).copyOf(16)
        // RFC 4122: ad tabanlı (sürüm 5) ve standart varyant bitleri
        b[6] = ((b[6].toInt() and 0x0F) or 0x50).toByte()
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = b.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    /// [from, to) aralığındaki ders başlangıç zamanları
    fun occurrences(weekday: Int, hour: Int, minute: Int, from: Long, to: Long): List<Long> {
        val result = mutableListOf<Long>()
        var day = startOfDay(from)
        while (day < to) {
            if (calendarWeekday(day) == weekday) {
                val slot = atTime(day, hour, minute)
                if (slot in from until to) result += slot
                day = addDays(day, 7)
            } else {
                day = addDays(day, 1)
            }
        }
        return result
    }

    /// Hafta içi sıra farkı (Pazartesi başlangıçlı); şablon listesinden gün
    /// değiştirildiğinde dersleri o hafta içinde kaydırmak için.
    fun dayShift(fromWeekday: Int, toWeekday: Int): Int {
        val order = LessonTemplate.weekdayOrder
        val a = order.indexOf(fromWeekday)
        val b = order.indexOf(toWeekday)
        if (a < 0 || b < 0) return 0
        return b - a
    }

    /// Kaydedilmeden önce formda gösterilir: seçilen gün ve saatte ufuk
    /// içinde başka bir ders varsa o hafta üretilmeyecek (`topUp` atlar).
    fun clashes(
        weekdays: Set<Int>,
        hour: Int,
        minute: Int,
        duration: Int,
        ignoringTemplateId: String?,
        lessons: List<Lesson>,
        now: Long = System.currentTimeMillis(),
    ): List<Lesson> {
        val horizon = addDays(startOfDay(now), HORIZON_DAYS)
        val length = duration * 60_000L
        val slots = weekdays.flatMap { occurrences(it, hour, minute, now, horizon) }
        return lessons
            .filter { lesson ->
                !lesson.isCancelled &&
                    (ignoringTemplateId == null || lesson.templateId != ignoringTemplateId) &&
                    slots.any { it < lesson.endDate && lesson.date < it + length }
            }
            .sortedBy { it.date }
    }
}
