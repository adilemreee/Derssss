package xyz.adilemree.dersdefteri.ui.students

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Lesson
import xyz.adilemree.dersdefteri.data.LessonTemplate
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.NotebookEditor
import xyz.adilemree.dersdefteri.data.Recurring
import xyz.adilemree.dersdefteri.data.Student
import xyz.adilemree.dersdefteri.data.TurkishPhoneFormat
import xyz.adilemree.dersdefteri.data.addDays
import xyz.adilemree.dersdefteri.data.isSameDay
import xyz.adilemree.dersdefteri.data.isToday
import xyz.adilemree.dersdefteri.ui.ArchivedStudentsRoute
import xyz.adilemree.dersdefteri.ui.PaywallRoute
import xyz.adilemree.dersdefteri.ui.StudentDetailRoute
import xyz.adilemree.dersdefteri.ui.StudentFormRoute
import xyz.adilemree.dersdefteri.ui.components.ActionSheet
import xyz.adilemree.dersdefteri.ui.components.BalanceBadge
import xyz.adilemree.dersdefteri.ui.components.Chip
import xyz.adilemree.dersdefteri.ui.components.ConfirmDialog
import xyz.adilemree.dersdefteri.ui.components.DetailScaffold
import xyz.adilemree.dersdefteri.ui.components.DurationChipPicker
import xyz.adilemree.dersdefteri.ui.components.EmptyState
import xyz.adilemree.dersdefteri.ui.components.FormAmountRow
import xyz.adilemree.dersdefteri.ui.components.FormColumnRow
import xyz.adilemree.dersdefteri.ui.components.FormDateRow
import xyz.adilemree.dersdefteri.ui.components.FormPhoneRow
import xyz.adilemree.dersdefteri.ui.components.FormScaffold
import xyz.adilemree.dersdefteri.ui.components.FormSection
import xyz.adilemree.dersdefteri.ui.components.FormTextField
import xyz.adilemree.dersdefteri.ui.components.FormTextRow
import xyz.adilemree.dersdefteri.ui.components.FormTimeRow
import xyz.adilemree.dersdefteri.ui.components.RowDivider
import xyz.adilemree.dersdefteri.ui.components.SheetAction
import xyz.adilemree.dersdefteri.ui.components.StudentAvatar
import xyz.adilemree.dersdefteri.ui.components.TabScaffold
import xyz.adilemree.dersdefteri.ui.components.TimeChipPicker
import xyz.adilemree.dersdefteri.ui.components.WeekdayChipPicker
import xyz.adilemree.dersdefteri.ui.components.amountText
import xyz.adilemree.dersdefteri.ui.components.card
import xyz.adilemree.dersdefteri.ui.components.parseAmount
import xyz.adilemree.dersdefteri.ui.nav
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.StudentPalette
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.semibold
import xyz.adilemree.dersdefteri.ui.theme.serif
import kotlin.random.Random

// MARK: - Öğrenci listesi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudentsScreen() {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val isPro by app.pro.isPro.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var archiveTarget by remember { mutableStateOf<Student?>(null) }
    var menuFor by remember { mutableStateOf<Student?>(null) }
    val c = AppTheme.colors

    val active = notebook.students.filter { !it.isArchived }
    val filtered = if (search.isBlank()) active else active.filter {
        it.name.contains(search, ignoreCase = true) ||
            it.subject.contains(search, ignoreCase = true) ||
            it.grade.contains(search, ignoreCase = true)
    }

    fun addStudent() {
        if (isPro || active.size < xyz.adilemree.dersdefteri.billing.ProStore.FREE_STUDENT_LIMIT) nav.navigate(StudentFormRoute())
        else nav.navigate(PaywallRoute)
    }

    TabScaffold(
        title = "Öğrenciler",
        actions = {
            if (notebook.students.any { it.isArchived }) {
                IconButton(onClick = { nav.navigate(ArchivedStudentsRoute) }) { Icon(Icons.Rounded.Archive, "Arşiv", tint = c.accent) }
            }
            IconButton(onClick = ::addStudent) { Icon(Icons.Rounded.Add, "Öğrenci Ekle", tint = c.accent) }
        },
        header = {
            SearchField(search, { search = it }, "Öğrenci, ders veya sınıf ara", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        },
        spacing = 12,
    ) {
        if (filtered.isNotEmpty()) {
            item { Text("${filtered.size} öğrenci", style = Type.caption.semibold(), color = c.inkSoft, modifier = Modifier.padding(top = 4.dp)) }
        }
        items(filtered, key = { it.id }) { student ->
            StudentCard(
                student, notebook,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).combinedClickable(
                    onClick = { nav.navigate(StudentDetailRoute(student.id)) },
                    onLongClick = { menuFor = student },
                ),
            )
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.School,
                    if (search.isEmpty()) "Henüz öğrenci yok" else "Sonuç bulunamadı",
                    actionTitle = if (search.isEmpty()) "Öğrenci Ekle" else null,
                    onAction = ::addStudent,
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        }
    }

    menuFor?.let { student ->
        ActionSheet(
            title = student.name,
            actions = listOf(SheetAction("Arşivle", Icons.Rounded.Archive) { archiveTarget = student }),
            onDismiss = { menuFor = null },
        )
    }
    archiveTarget?.let { student ->
        StudentArchiveDialog(student, notebook, onDismiss = { archiveTarget = null })
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.inkSoft.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.inkSoft, modifier = Modifier.size(20.dp))
        FormTextField(value, onChange, placeholder, modifier = Modifier.weight(1f), capitalization = KeyboardCapitalization.None)
        if (value.isNotEmpty()) {
            Icon(Icons.Rounded.Close, "Temizle", tint = c.inkSoft, modifier = Modifier.size(18.dp).clickable { onChange("") })
        }
    }
}

/// Gelecek planlı dersi olan öğrenci arşivlenmeden önce ne olacağını söyler.
@Composable
fun StudentArchiveDialog(student: Student, notebook: Notebook, onDismiss: () -> Unit, onArchived: () -> Unit = {}) {
    val app = LocalApp.current
    val now = System.currentTimeMillis()
    val count = notebook.lessonsOf(student.id).count { it.isPlanned && it.date > now }
    ConfirmDialog(
        title = "Öğrenci arşivlensin mi?",
        message = if (count > 0) {
            "Gelecek $count planlı ders programdan kaldırılır. İşlenmiş dersler, ödemeler ve ödevler kalır; aktife alınca haftalık dersler yeniden planlanır."
        } else "İşlenmiş dersler, ödemeler ve ödevler kalır.",
        confirmTitle = "Arşivle",
        onConfirm = {
            app.edit { archive(student.id) }
            onArchived()
        },
        onDismiss = onDismiss,
    )
}

// MARK: - Arşivlenmiş öğrenciler

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchivedStudentsScreen() {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val archived = notebook.students.filter { it.isArchived }
    var menuFor by remember { mutableStateOf<Student?>(null) }
    var deleteTarget by remember { mutableStateOf<Student?>(null) }
    val c = AppTheme.colors

    /// Aktife almak da öğrenci limitine tabidir
    fun unarchive(student: Student) {
        val activeCount = notebook.students.count { !it.isArchived }
        if (app.pro.canAddStudent(activeCount)) app.edit { unarchive(student.id) }
        else nav.navigate(PaywallRoute)
    }

    DetailScaffold(title = "Arşiv", onBack = { nav.popBackStack() }) {
        if (archived.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.Archive, "Arşiv boş",
                    message = "Mezun olan veya ara veren öğrenciler burada görünür.",
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        }
        items(archived, key = { it.id }) { student ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StudentCard(
                    student, notebook,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).combinedClickable(
                        onClick = { nav.navigate(StudentDetailRoute(student.id)) },
                        onLongClick = { menuFor = student },
                    ),
                )
                IconButton(onClick = { unarchive(student) }) {
                    Icon(Icons.Rounded.Unarchive, "Aktife Al", tint = c.green, modifier = Modifier.size(28.dp))
                }
            }
        }
    }

    menuFor?.let { student ->
        ActionSheet(
            title = student.name,
            actions = listOf(
                SheetAction("Aktife Al", Icons.Rounded.Unarchive) { unarchive(student) },
                SheetAction("Sil", Icons.Rounded.Delete, destructive = true) { deleteTarget = student },
            ),
            onDismiss = { menuFor = null },
        )
    }
    deleteTarget?.let { student ->
        ConfirmDialog(
            title = "Öğrenci silinsin mi?",
            message = "${student.name} ve tüm ders, ödeme ve ödev kayıtları kalıcı olarak silinir.",
            confirmTitle = "Sil",
            onConfirm = { app.edit { deleteStudent(student.id) } },
            onDismiss = { deleteTarget = null },
        )
    }
}

// MARK: - Öğrenci kartı

@Composable
fun StudentCard(student: Student, notebook: Notebook, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val stats = notebook.stats(student.id)
    Row(
        modifier.fillMaxWidth().card(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StudentAvatar(student, 50.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(student.name, style = Type.headline.serif(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${student.subject} • ${student.grade}", style = Type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("${Fmt.money(student.hourlyRate)}/sa", c.accent)
                Chip("${stats.completedCount} ders", c.blue)
            }
            lessonTimeline(student, notebook)?.let {
                Text(it, style = Type.caption2.semibold(), color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BalanceBadge(stats)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkSoft.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }
}

/// Asıl lazım olan sıradaki ders; yoksa son dersin tarihi gösterilir.
private fun lessonTimeline(student: Student, notebook: Notebook, now: Long = System.currentTimeMillis()): String? {
    val lessons = notebook.lessonsOf(student.id)
    val next = lessons.filter { it.isPlanned && it.date >= now }.minByOrNull { it.date }
    if (next != null) {
        val day = when {
            isToday(next.date, now) -> "Bugün"
            isSameDay(next.date, addDays(now, 1)) -> "Yarın"
            else -> Fmt.dayMonthShort(next.date)
        }
        return "Sıradaki ders: $day ${Fmt.time(next.date)}"
    }
    val last = lessons.filter { it.isCompleted && it.date <= now }.maxByOrNull { it.date }
    return last?.let { "Son ders: ${Fmt.dayMonthShort(it.date)}" }
}

// MARK: - Öğrenci formu (ekle / düzenle)

@Composable
fun StudentFormScreen(studentId: String?) {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val student = studentId?.let { notebook.student(it) }
    val c = AppTheme.colors

    var name by rememberSaveable { mutableStateOf(student?.name ?: "") }
    var subject by rememberSaveable { mutableStateOf(student?.subject ?: "") }
    var grade by rememberSaveable { mutableStateOf(student?.grade ?: "") }
    var phone by rememberSaveable { mutableStateOf(TurkishPhoneFormat.format(student?.phone ?: "")) }
    var parentName by rememberSaveable { mutableStateOf(student?.parentName ?: "") }
    var parentPhone by rememberSaveable { mutableStateOf(TurkishPhoneFormat.format(student?.parentPhone ?: "")) }
    var rate by rememberSaveable { mutableStateOf(amountText(student?.hourlyRate ?: 0.0)) }
    var startDate by rememberSaveable { mutableStateOf(student?.startDate ?: System.currentTimeMillis()) }
    var colorIndex by rememberSaveable { mutableIntStateOf(student?.colorIndex ?: Random.nextInt(StudentPalette.size)) }
    var notes by rememberSaveable { mutableStateOf(student?.notes ?: "") }
    // Yalnızca yeni öğrencide: haftalık ders günleri
    var weeklyDays by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var weeklyHour by rememberSaveable { mutableIntStateOf(17) }
    var weeklyMinute by rememberSaveable { mutableIntStateOf(0) }
    var weeklyDuration by rememberSaveable { mutableIntStateOf(60) }

    val suggestions = listOf("Matematik", "Fizik", "Kimya", "Biyoloji", "İngilizce", "Türkçe", "Edebiyat", "Tarih")
    val isNew = studentId == null
    val clashes: List<Lesson> = if (isNew && weeklyDays.isNotEmpty()) {
        Recurring.clashes(weeklyDays, weeklyHour, weeklyMinute, weeklyDuration, null, notebook.lessons)
    } else emptyList()

    FormScaffold(
        title = if (isNew) "Yeni Öğrenci" else "Öğrenciyi Düzenle",
        onClose = { nav.popBackStack() },
        actionEnabled = name.isNotBlank(),
        onAction = {
            val input = NotebookEditor.StudentInput(
                name = name.trim(),
                subject = subject.trim(),
                grade = grade.trim(),
                phone = phone,
                parentName = parentName.trim(),
                parentPhone = parentPhone,
                hourlyRate = parseAmount(rate) ?: 0.0,
                startDate = startDate,
                colorIndex = colorIndex,
                notes = notes.trim(),
            )
            val days = weeklyDays
            val h = weeklyHour; val m = weeklyMinute; val d = weeklyDuration
            app.edit { saveStudent(studentId, input, days, h, m, d) }
            nav.popBackStack()
        },
    ) {
        FormSection(header = "Öğrenci") {
            FormTextRow(name, { name = it }, "Ad Soyad", capitalization = KeyboardCapitalization.Words)
            RowDivider()
            FormTextRow(subject, { subject = it }, "Ders (ör. Matematik)", capitalization = KeyboardCapitalization.Words)
            if (subject.isEmpty()) {
                LazyRow(
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(suggestions) { s ->
                        Text(
                            s,
                            style = Type.caption.semibold(),
                            color = c.accent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(c.accent.copy(alpha = 0.12f))
                                .clickable { subject = s }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            RowDivider()
            FormTextRow(grade, { grade = it }, "Sınıf (ör. 11. Sınıf)")
        }

        FormSection(
            header = "Ücret",
            footer = if (!isNew) "Ücreti değiştirirsen planlı dersler yeni ücrete geçer; işlenmiş dersler eski ücretle kalır." else null,
        ) {
            FormAmountRow("Saatlik ücret", rate, { rate = it })
        }

        if (isNew) {
            FormSection(
                header = "Haftalık ders",
                footer = if (weeklyDays.isEmpty()) "İsteğe bağlı. Gün seçersen dersler her hafta kendiliğinden planlanır."
                else "Dersler ${Recurring.HORIZON_DAYS / 7} hafta ilerisi için planlanır; sonra kendiliğinden devam eder.",
            ) {
                FormColumnRow { WeekdayChipPicker(weeklyDays) { weeklyDays = it } }
                if (weeklyDays.isNotEmpty()) {
                    RowDivider()
                    FormTimeRow("Saat", weeklyHour, weeklyMinute) { h, m -> weeklyHour = h; weeklyMinute = m }
                    FormColumnRow { TimeChipPicker(weeklyHour, weeklyMinute) { weeklyHour = it; weeklyMinute = 0 } }
                    RowDivider()
                    FormColumnRow {
                        Text("Süre (dakika)", style = Type.body, color = c.ink)
                        DurationChipPicker(weeklyDuration) { weeklyDuration = it }
                    }
                    if (clashes.isNotEmpty()) {
                        RowDivider()
                        WeeklyClashNotice(clashes, notebook)
                    }
                }
            }
        }

        FormSection(header = "İletişim") {
            FormPhoneRow(phone, { phone = it })
            RowDivider()
            FormTextRow(parentName, { parentName = it }, "Veli adı", capitalization = KeyboardCapitalization.Words)
            RowDivider()
            FormPhoneRow(parentPhone, { parentPhone = it })
        }

        FormSection(header = "Diğer") {
            FormDateRow("Başlangıç tarihi", startDate) { startDate = it }
            RowDivider()
            FormColumnRow {
                Text("Renk", style = Type.body, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudentPalette.forEachIndexed { index, color ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(color, CircleShape)
                                .clickable { colorIndex = index },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index == colorIndex) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            RowDivider()
            FormTextRow(notes, { notes = it }, "Notlar (hedef, seviye, vb.)", singleLine = false, minLines = 3)
        }
    }
}

/// Haftalık ders formlarında: seçilen saatte başka ders olan haftalar
/// atlanır; kullanıcı bunu kaydetmeden görsün.
@Composable
fun WeeklyClashNotice(lessons: List<Lesson>, notebook: Notebook) {
    val c = AppTheme.colors
    FormColumnRow {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Warning, null, tint = c.red, modifier = Modifier.size(18.dp))
            Text("Bu saatte ders var", style = Type.subheadline.semibold(), color = c.red)
        }
        Text(
            lessons.take(3).joinToString("\n") {
                "${Fmt.dayMonthShort(it.date)} ${Fmt.time(it.date)} - ${notebook.studentOf(it)?.name ?: "Öğrenci"}"
            },
            style = Type.caption,
            color = c.inkSoft,
        )
        Text("Çakışan haftalarda ders oluşturulmaz.", style = Type.caption, color = c.inkSoft)
    }
}

/// "Her Salı 17:00 · 90 dk" çipi; dokununca haftalık ders düzenlenir.
@Composable
fun WeeklyChip(template: LessonTemplate, student: Student, onClick: () -> Unit) {
    val c = AppTheme.colors
    val color = xyz.adilemree.dersdefteri.ui.theme.studentColor(student.colorIndex)
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = if (template.isPaused) 0.05f else 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            if (template.isPaused) Icons.Rounded.Pause else Icons.Rounded.Repeat,
            null,
            tint = if (template.isPaused) c.inkSoft else color,
            modifier = Modifier.size(13.dp),
        )
        Text("${template.weekdayName} ${template.timeText}", style = Type.caption.semibold(), color = c.ink.copy(alpha = if (template.isPaused) 0.6f else 1f))
        Text("· ${template.duration} dk", style = Type.caption, color = c.inkSoft)
    }
}

/// Dar kesikli kapsül: "Haftalık ders ekle"
@Composable
fun DashedAddChip(title: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .clip(CircleShape)
            .border(1.dp, c.line, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(14.dp))
        Text(title, style = Type.caption.semibold(), color = c.accent)
    }
}

