package xyz.adilemree.dersdefteri.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.data.CancellationReason
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Lesson
import xyz.adilemree.dersdefteri.data.LessonStatus
import xyz.adilemree.dersdefteri.data.LessonTemplate
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.NotebookEditor
import xyz.adilemree.dersdefteri.data.addDays
import xyz.adilemree.dersdefteri.data.atTime
import xyz.adilemree.dersdefteri.data.calendarWeekday
import xyz.adilemree.dersdefteri.data.hourOf
import xyz.adilemree.dersdefteri.data.minuteOf
import xyz.adilemree.dersdefteri.ui.common.LessonDeleteDialog
import xyz.adilemree.dersdefteri.ui.components.ActionSheet
import xyz.adilemree.dersdefteri.ui.components.ConfirmDialog
import xyz.adilemree.dersdefteri.ui.components.DurationChipPicker
import xyz.adilemree.dersdefteri.ui.components.FormAmountRow
import xyz.adilemree.dersdefteri.ui.components.FormButtonRow
import xyz.adilemree.dersdefteri.ui.components.FormColumnRow
import xyz.adilemree.dersdefteri.ui.components.FormDateRow
import xyz.adilemree.dersdefteri.ui.components.FormPickerRow
import xyz.adilemree.dersdefteri.ui.components.FormRow
import xyz.adilemree.dersdefteri.ui.components.FormScaffold
import xyz.adilemree.dersdefteri.ui.components.FormSection
import xyz.adilemree.dersdefteri.ui.components.FormSegmentedRow
import xyz.adilemree.dersdefteri.ui.components.FormTextRow
import xyz.adilemree.dersdefteri.ui.components.FormTimeRow
import xyz.adilemree.dersdefteri.ui.components.FormToggleRow
import xyz.adilemree.dersdefteri.ui.components.FormValueRow
import xyz.adilemree.dersdefteri.ui.components.RowDivider
import xyz.adilemree.dersdefteri.ui.components.Segmented
import xyz.adilemree.dersdefteri.ui.components.SheetAction
import xyz.adilemree.dersdefteri.ui.components.StudentChipPicker
import xyz.adilemree.dersdefteri.ui.components.TimeChipPicker
import xyz.adilemree.dersdefteri.ui.components.amountText
import xyz.adilemree.dersdefteri.ui.components.parseAmount
import xyz.adilemree.dersdefteri.ui.nav
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.semibold
import kotlin.math.abs

/// Çakışan dersler: önerilen her aralıkla çakışan, iptal edilmemiş dersler.
private fun conflicts(notebook: Notebook, slots: List<Long>, duration: Int, excludingId: String?): List<Lesson> {
    val length = duration * 60_000L
    return notebook.lessons.filter { candidate ->
        !candidate.isCancelled && candidate.id != excludingId &&
            slots.any { it < candidate.endDate && candidate.date < it + length }
    }
}

private fun conflictText(lessons: List<Lesson>, notebook: Notebook): String =
    lessons.take(3).joinToString("\n") { "${Fmt.dayMonthShort(it.date)} ${Fmt.time(it.date)} - ${notebook.studentOf(it)?.name ?: "Öğrenci"}" }

@Composable
private fun ConflictNotice(title: String, text: String) {
    val c = AppTheme.colors
    FormColumnRow {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Warning, null, tint = c.red, modifier = Modifier.size(18.dp))
            Text(title, style = Type.subheadline.semibold(), color = c.red)
        }
        Text(text, style = Type.caption, color = c.inkSoft)
    }
}

// MARK: - Ders formu (ekle / düzenle)

@Composable
fun LessonFormScreen(lessonId: String?, defaultStudentId: String?, defaultDate: Long?) {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val lesson = lessonId?.let { notebook.lessonById[it] }
    val c = AppTheme.colors

    val baseDate = lesson?.date ?: defaultDate ?: System.currentTimeMillis()
    var studentId by rememberSaveable { mutableStateOf(lesson?.studentId ?: defaultStudentId) }
    var day by rememberSaveable { mutableLongStateOf(baseDate) }
    var hour by rememberSaveable { mutableIntStateOf(if (lesson != null) hourOf(lesson.date) else 17) }
    var minute by rememberSaveable { mutableIntStateOf(if (lesson != null) minuteOf(lesson.date) else 0) }
    var duration by rememberSaveable { mutableIntStateOf(lesson?.duration ?: 60) }
    var status by rememberSaveable { mutableStateOf(lesson?.lessonStatus ?: LessonStatus.PLANNED) }
    var topic by rememberSaveable { mutableStateOf(lesson?.topic ?: "") }
    var note by rememberSaveable { mutableStateOf(lesson?.note ?: "") }
    var useCustomFee by rememberSaveable { mutableStateOf(lesson?.usesCustomFee ?: false) }
    var customFee by rememberSaveable { mutableStateOf(amountText(lesson?.let { it.feeOverride ?: notebook.fee(it) } ?: 0.0)) }
    var reason by rememberSaveable {
        mutableStateOf(lesson?.reason?.takeIf { it != CancellationReason.NONE } ?: CancellationReason.STUDENT)
    }
    /// Yeni ders ya da henüz seride olmayan ders için: bir kez mi, her hafta mı.
    var repeatsWeekly by rememberSaveable { mutableStateOf(false) }
    /// "Toplu ödemeden çıkar" denirse kaydederken ders ödemeden ayrılır.
    var detachFromPayment by rememberSaveable { mutableStateOf(false) }
    var showConflictAlert by remember { mutableStateOf(false) }
    var showSeriesChoice by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var confirmStopRepeat by remember { mutableStateOf(false) }

    val selectedStudent = notebook.student(studentId)
    /// Aktif öğrenciler; arşivlenmiş bir öğrencinin dersi düzenleniyorsa o da.
    val chipStudents = notebook.students.filter { !it.isArchived || it.id == studentId }
    val defaultFee = Lesson.standardFee(selectedStudent, duration)
    val start = atTime(day, hour, minute)
    val series = lesson?.let { notebook.templateOf(it) }
    val payment = lesson?.let { notebook.paymentOf(it) }

    // Her hafta seçiliyse ilk dört haftanın çakışması da kontrol edilir.
    val slots = (0..(if (series == null && repeatsWeekly) 3 else 0)).map { addDays(start, 7 * it) }
    val conflicting = if (status == LessonStatus.CANCELLED) emptyList() else conflicts(notebook, slots, duration, lesson?.id)

    fun save(scope: NotebookEditor.SaveScope) {
        val sid = studentId ?: return
        val input = NotebookEditor.LessonInput(
            lessonId = lesson?.id,
            studentId = sid,
            start = start,
            duration = duration,
            status = status,
            cancellationReason = reason,
            topic = topic.trim(),
            note = note.trim(),
            useCustomFee = useCustomFee,
            customFee = parseAmount(customFee) ?: 0.0,
            detachFromPayment = detachFromPayment,
            repeatsWeekly = repeatsWeekly,
        )
        app.edit { saveLesson(input, scope) }
        nav.popBackStack()
    }

    fun seriesFieldsChanged(l: Lesson): Boolean =
        abs(l.date - start) > 30_000 || l.duration != duration || l.usesCustomFee != useCustomFee ||
            (useCustomFee && abs((l.feeOverride ?: 0.0) - (parseAmount(customFee) ?: 0.0)) > 0.001)

    /// Serideki bir dersin zamanı, süresi ya da ücreti değiştiyse kapsam sorulur.
    fun proceedSave() {
        if (lesson != null && lesson.templateId != null && lesson.studentId == studentId && seriesFieldsChanged(lesson)) {
            showSeriesChoice = true
        } else {
            save(NotebookEditor.SaveScope.ONLY_THIS)
        }
    }

    FormScaffold(
        title = if (lesson == null) "Yeni Ders" else "Dersi Düzenle",
        onClose = { nav.popBackStack() },
        actionEnabled = studentId != null && selectedStudent != null,
        onAction = { if (conflicting.isEmpty()) proceedSave() else showConflictAlert = true },
    ) {
        FormSection(header = "Ders") {
            FormColumnRow { StudentChipPicker(chipStudents, studentId) { studentId = it } }
            RowDivider()
            FormDateRow("Tarih", day) { day = it }
            RowDivider()
            FormTimeRow("Saat", hour, minute) { h, m -> hour = h; minute = m }
            FormColumnRow { TimeChipPicker(hour, minute) { hour = it; minute = 0 } }
            RowDivider()
            FormColumnRow {
                Text("Süre (dakika)", style = Type.body, color = c.ink)
                DurationChipPicker(duration) { duration = it }
            }
            if (conflicting.isNotEmpty()) {
                RowDivider()
                ConflictNotice("Bu saat dolu görünüyor", conflictText(conflicting, notebook))
            }
            RowDivider()
            FormSegmentedRow {
                Segmented(LessonStatus.entries.map { it to it.title }, status, { status = it })
            }
            if (status == LessonStatus.CANCELLED) {
                RowDivider()
                FormPickerRow("İptal sebebi", CancellationReason.choices, reason, { it.title }) { reason = it }
            }
        }

        FormSection(header = "İçerik") {
            FormTextRow(topic, { topic = it }, "Konu (ör. Türev)")
            RowDivider()
            FormTextRow(note, { note = it }, "Not", singleLine = false, minLines = 2)
        }

        FormSection(header = "Ücret") {
            FormValueRow(if (useCustomFee) "Standart ücret" else "Kaydedilecek ücret", Fmt.money(defaultFee))
            RowDivider()
            FormToggleRow("Derse özel ücret", useCustomFee, { useCustomFee = it })
            if (useCustomFee) {
                RowDivider()
                FormAmountRow("Özel ücret", customFee, { customFee = it })
            }
        }

        if (payment != null) {
            FormSection(
                header = "Ödeme",
                footer = if (detachFromPayment) "Kaydedince bu ders ödemeden çıkar; ödeme tutarı değişmez, fark avans olarak kalır."
                else "Bu ders ${Fmt.dayMonthShort(payment.date)} tarihli toplu ödemeyle ödendi. Ücretini değiştirirsen ödeme tutarı değişmez; fark bakiyeye yansır.",
            ) {
                FormValueRow("Toplu ödeme", "${Fmt.dayMonthShort(payment.date)} · ${notebook.bulkSummary(payment.id)}")
                RowDivider()
                FormValueRow("Ödeme tutarı", Fmt.money(payment.amount))
                RowDivider()
                if (detachFromPayment) {
                    FormButtonRow("Vazgeç, ödemede kalsın") { detachFromPayment = false }
                } else {
                    FormButtonRow("Toplu ödemeden çıkar", color = c.red) { detachFromPayment = true }
                }
            }
        }

        FormSection(
            header = "Tekrar",
            footer = when {
                series != null -> "Gününü, saatini, süresini ya da ücretini değiştirirsen yalnızca bu dersi mi, sonrakileri de mi değiştireceğin sorulur."
                repeatsWeekly -> "Her ${LessonTemplate.weekdayName(calendarWeekday(start))} ${Fmt.time(start)} otomatik planlanır. Tatil haftasında o dersi silmen yeterli; sonraki haftalar devam eder."
                else -> null
            },
        ) {
            if (series != null) {
                FormRow {
                    Icon(Icons.Rounded.Repeat, null, tint = c.ink, modifier = Modifier.size(18.dp))
                    Text("Her ${series.weekdayName} ${series.timeText}", style = Type.body, color = c.ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = { confirmStopRepeat = true }) { Text("Tekrarı Durdur", color = c.red, style = Type.subheadline) }
                }
            } else {
                FormSegmentedRow {
                    Segmented(listOf(false to "Bir kez", true to "Her hafta"), repeatsWeekly, { repeatsWeekly = it })
                }
            }
        }

        if (lesson != null) {
            FormSection {
                FormButtonRow("Dersi Sil", color = c.red, center = true) { showDelete = true }
            }
        }
    }

    if (showConflictAlert) {
        ConfirmDialog(
            title = "Ders çakışması var",
            message = conflictText(conflicting, notebook),
            confirmTitle = "Yine de Kaydet",
            onConfirm = { proceedSave() },
            onDismiss = { showConflictAlert = false },
        )
    }
    if (showSeriesChoice) {
        ActionSheet(
            title = "Bu ders her hafta tekrarlanıyor",
            message = "Değişiklik yalnızca bu derse mi, sonraki haftalara da mı uygulansın?",
            actions = listOf(
                SheetAction("Yalnızca Bu Ders") { save(NotebookEditor.SaveScope.ONLY_THIS) },
                SheetAction("Bu ve Sonraki Dersler") { save(NotebookEditor.SaveScope.THIS_AND_FOLLOWING) },
            ),
            onDismiss = { showSeriesChoice = false },
        )
    }
    if (showDelete && lesson != null) {
        LessonDeleteDialog(lesson, onDismiss = { showDelete = false }, onDeleted = { nav.popBackStack() })
    }
    if (confirmStopRepeat && lesson != null) {
        ConfirmDialog(
            title = "Haftalık tekrar durdurulsun mu?",
            message = "Bu dersten sonraki planlı dersler silinir; bu ders ve öncekiler kalır.",
            confirmTitle = "Tekrarı Durdur",
            onConfirm = { app.edit { stopRepeating(lesson.id) } },
            onDismiss = { confirmStopRepeat = false },
        )
    }
}

// MARK: - Hızlı ders

private enum class DayChoice(val title: String) { TODAY("Bugün"), TOMORROW("Yarın"), CUSTOM("Tarih") }

@Composable
fun QuickLessonScreen(defaultDate: Long?) {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val c = AppTheme.colors
    val now = System.currentTimeMillis()

    var studentId by rememberSaveable { mutableStateOf<String?>(null) }
    var dayChoice by rememberSaveable { mutableStateOf(DayChoice.TODAY) }
    var customDate by rememberSaveable { mutableLongStateOf(defaultDate ?: now) }
    var hour by rememberSaveable { mutableIntStateOf(hourOf(now + 3_600_000L)) }
    var minute by rememberSaveable { mutableIntStateOf(0) }
    var duration by rememberSaveable { mutableIntStateOf(60) }
    var repeatsWeekly by rememberSaveable { mutableStateOf(false) }
    var showConflictAlert by remember { mutableStateOf(false) }

    val active = notebook.students.filter { !it.isArchived }
    // Tek öğrenci varsa seçmek için dokunmaya gerek yok.
    LaunchedEffect(active.size) {
        if (studentId == null && active.size == 1) studentId = active.first().id
    }
    val selectedDay = when (dayChoice) {
        DayChoice.TODAY -> now
        DayChoice.TOMORROW -> addDays(now, 1)
        DayChoice.CUSTOM -> customDate
    }
    val start = atTime(selectedDay, hour, minute)
    // Her hafta seçiliyse sonraki üç haftanın aynı saati de kontrol edilir.
    val slots = (0 until (if (repeatsWeekly) 4 else 1)).map { addDays(start, it * 7) }
    val conflicting = conflicts(notebook, slots, duration, null)

    fun save() {
        val sid = studentId ?: return
        val s = start; val d = duration; val r = repeatsWeekly
        app.edit { quickLesson(sid, s, d, r) }
        nav.popBackStack()
    }

    FormScaffold(
        title = "Hızlı Ders Ekle",
        onClose = { nav.popBackStack() },
        actionEnabled = studentId != null,
        onAction = { if (conflicting.isEmpty()) save() else showConflictAlert = true },
    ) {
        FormSection(header = "Öğrenci") {
            FormColumnRow { StudentChipPicker(active, studentId) { studentId = it } }
        }
        FormSection(header = "Zaman") {
            FormSegmentedRow { Segmented(DayChoice.entries.map { it to it.title }, dayChoice, { dayChoice = it }) }
            if (dayChoice == DayChoice.CUSTOM) {
                RowDivider()
                FormDateRow("Tarih", customDate) { customDate = it }
            }
            RowDivider()
            FormTimeRow("Saat", hour, minute) { h, m -> hour = h; minute = m }
            FormColumnRow { TimeChipPicker(hour, minute) { hour = it; minute = 0 } }
            RowDivider()
            FormColumnRow {
                Text("Süre (dakika)", style = Type.body, color = c.ink)
                DurationChipPicker(duration) { duration = it }
            }
            if (conflicting.isNotEmpty()) {
                RowDivider()
                ConflictNotice("Bu saatte ders var", conflictText(conflicting, notebook))
            }
        }
        FormSection(
            header = "Tekrar",
            footer = if (repeatsWeekly) {
                "Her ${LessonTemplate.weekdayName(calendarWeekday(start))} ${Fmt.time(start)} otomatik planlanır. Tatil haftasında o dersi silmen yeterli; sonraki haftalar devam eder."
            } else "Ders planlandı olarak kaydedilir; işlenince ödeme bakiyesine yansır.",
        ) {
            FormSegmentedRow { Segmented(listOf(false to "Bir kez", true to "Her hafta"), repeatsWeekly, { repeatsWeekly = it }) }
        }
    }

    if (showConflictAlert) {
        ConfirmDialog(
            title = "Ders çakışması var",
            message = conflictText(conflicting, notebook),
            confirmTitle = "Yine de Kaydet",
            onConfirm = { save() },
            onDismiss = { showConflictAlert = false },
        )
    }
}
