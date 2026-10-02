package xyz.adilemree.dersdefteri.ui.weekly

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Repeat
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Lesson
import xyz.adilemree.dersdefteri.data.LessonTemplate
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.Recurring
import xyz.adilemree.dersdefteri.ui.TemplateFormRoute
import xyz.adilemree.dersdefteri.ui.components.ActionSheet
import xyz.adilemree.dersdefteri.ui.components.Chip
import xyz.adilemree.dersdefteri.ui.components.DetailScaffold
import xyz.adilemree.dersdefteri.ui.components.DurationChipPicker
import xyz.adilemree.dersdefteri.ui.components.EmptyState
import xyz.adilemree.dersdefteri.ui.components.FormAmountRow
import xyz.adilemree.dersdefteri.ui.components.FormButtonRow
import xyz.adilemree.dersdefteri.ui.components.FormColumnRow
import xyz.adilemree.dersdefteri.ui.components.FormScaffold
import xyz.adilemree.dersdefteri.ui.components.FormSection
import xyz.adilemree.dersdefteri.ui.components.FormTimeRow
import xyz.adilemree.dersdefteri.ui.components.FormToggleRow
import xyz.adilemree.dersdefteri.ui.components.FormValueRow
import xyz.adilemree.dersdefteri.ui.components.RowDivider
import xyz.adilemree.dersdefteri.ui.components.SheetAction
import xyz.adilemree.dersdefteri.ui.components.StudentChipPicker
import xyz.adilemree.dersdefteri.ui.components.TimeChipPicker
import xyz.adilemree.dersdefteri.ui.components.WeekdayChipPicker
import xyz.adilemree.dersdefteri.ui.components.amountText
import xyz.adilemree.dersdefteri.ui.components.card
import xyz.adilemree.dersdefteri.ui.components.parseAmount
import xyz.adilemree.dersdefteri.ui.nav
import xyz.adilemree.dersdefteri.ui.students.WeeklyClashNotice
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.bold
import xyz.adilemree.dersdefteri.ui.theme.studentColor

// Haftalık dersler. Kodda "şablon" (LessonTemplate) olarak geçer; kullanıcı
// yalnızca "haftalık ders" görür.

private fun sorted(templates: List<LessonTemplate>) = templates.sortedWith(
    compareBy<LessonTemplate>({ LessonTemplate.weekdayOrder.indexOf(it.weekday) }, { it.hour }, { it.minute }),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecurringLessonsScreen() {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val templates = sorted(notebook.templates)
    var menuFor by remember { mutableStateOf<LessonTemplate?>(null) }
    var deleting by remember { mutableStateOf<LessonTemplate?>(null) }
    val c = AppTheme.colors

    DetailScaffold(
        title = "Haftalık Dersler",
        onBack = { nav.popBackStack() },
        actions = { IconButton(onClick = { nav.navigate(TemplateFormRoute()) }) { Icon(Icons.Rounded.Add, "Haftalık Ders Ekle", tint = c.accent) } },
    ) {
        if (templates.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.Repeat, "Haftalık ders yok",
                    message = "\"Her Salı 17:00\" gibi bir haftalık ders ekle; dersler 4 hafta ilerisi için otomatik planlansın. Ders eklerken \"Her hafta\" seçmen de yeterli.",
                    actionTitle = "Haftalık Ders Ekle",
                    onAction = { nav.navigate(TemplateFormRoute()) },
                )
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Info, null, tint = c.inkSoft, modifier = Modifier.size(16.dp))
                    Text(
                        "Dersler ${Recurring.HORIZON_DAYS / 7} hafta ilerisi için otomatik planlanır. Tatil haftasında o dersi silmen yeterli; seri devam eder.",
                        style = Type.caption, color = c.inkSoft,
                    )
                }
            }
            items(templates, key = { it.id }) { template ->
                TemplateCard(
                    template, notebook,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).combinedClickable(
                        onClick = { nav.navigate(TemplateFormRoute(templateId = template.id)) },
                        onLongClick = { menuFor = template },
                    ),
                )
            }
        }
    }

    menuFor?.let { template ->
        ActionSheet(
            title = notebook.student(template.studentId)?.name ?: "Haftalık ders",
            message = "${template.weekdayName} ${template.timeText} • ${template.duration} dk",
            actions = listOf(
                SheetAction("Düzenle", Icons.Rounded.Edit) { nav.navigate(TemplateFormRoute(templateId = template.id)) },
                SheetAction(
                    if (template.isPaused) "Devam Ettir" else "Duraklat",
                    if (template.isPaused) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle,
                ) { app.edit { togglePause(template.id) } },
                SheetAction("Sil", Icons.Rounded.Delete, destructive = true) { deleting = template },
            ),
            onDismiss = { menuFor = null },
        )
    }
    deleting?.let { template ->
        ActionSheet(
            title = "Haftalık ders silinsin mi?",
            message = "İşlenmiş dersler her durumda korunur.",
            actions = listOf(
                SheetAction("Gelecek Planlı Derslerle Birlikte Sil", destructive = true) {
                    app.edit { deleteUpcomingLessons(template.id); deleteTemplate(template.id) }
                },
                SheetAction("Yalnızca Tekrarı Durdur", destructive = true) { app.edit { deleteTemplate(template.id) } },
            ),
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TemplateCard(template: LessonTemplate, notebook: Notebook, modifier: Modifier) {
    val c = AppTheme.colors
    val student = notebook.student(template.studentId)
    val fee = template.feeOverride ?: Lesson.standardFee(student, template.duration)
    Row(
        modifier.fillMaxWidth().card(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(4.dp).height(46.dp).background(student?.let { studentColor(it.colorIndex) } ?: c.inkSoft, CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(student?.name ?: "—", style = Type.subheadline.bold(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${template.weekdayName} ${template.timeText} • ${template.duration} dk", style = Type.caption, color = c.inkSoft)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(Fmt.money(fee), style = Type.subheadline.bold(), color = c.ink)
            if (template.isPaused) Chip("Duraklatıldı", c.amber, filled = true) else Chip("Aktif", c.green)
        }
    }
}

// MARK: - Haftalık ders formu (ekle / düzenle)

@Composable
fun TemplateFormScreen(templateId: String?, defaultStudentId: String?) {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val template = templateId?.let { notebook.templateById[it] }
    val c = AppTheme.colors

    var studentId by rememberSaveable { mutableStateOf(template?.studentId ?: defaultStudentId) }
    var weekdays by rememberSaveable { mutableStateOf(setOf(template?.weekday ?: 3)) }
    var hour by rememberSaveable { mutableIntStateOf(template?.hour ?: 17) }
    var minute by rememberSaveable { mutableIntStateOf(template?.minute ?: 0) }
    var duration by rememberSaveable { mutableIntStateOf(template?.duration ?: 60) }
    var useCustomFee by rememberSaveable { mutableStateOf(template?.usesCustomFee ?: false) }
    var customFee by rememberSaveable { mutableStateOf(amountText(template?.feeOverride ?: 0.0)) }
    var isPaused by rememberSaveable { mutableStateOf(template?.isPaused ?: false) }
    var confirmEnd by remember { mutableStateOf(false) }

    val selectedStudent = notebook.student(studentId)
    val chipStudents = notebook.students.filter { !it.isArchived || it.id == studentId }
    val clashes = Recurring.clashes(weekdays, hour, minute, duration, template?.id, notebook.lessons)
    val isNew = template == null

    FormScaffold(
        title = if (isNew) "Yeni Haftalık Ders" else "Haftalık Ders",
        onClose = { nav.popBackStack() },
        actionEnabled = selectedStudent != null && weekdays.isNotEmpty(),
        onAction = {
            val sid = studentId ?: return@FormScaffold
            val days = weekdays; val h = hour; val m = minute; val d = duration
            val custom = useCustomFee; val fee = parseAmount(customFee) ?: 0.0; val paused = isPaused
            app.edit { saveTemplate(template?.id, sid, days, h, m, d, custom, fee, paused) }
            nav.popBackStack()
        },
    ) {
        FormSection(header = "Öğrenci") {
            FormColumnRow { StudentChipPicker(chipStudents, studentId) { studentId = it } }
        }

        FormSection(
            header = if (isNew) "Günler ve saat" else "Gün ve saat",
            footer = if (isNew) "Birden çok gün seçersen her gün için ayrı haftalık ders oluşur." else null,
        ) {
            FormColumnRow { WeekdayChipPicker(weekdays, allowsMultiple = isNew) { weekdays = it } }
            RowDivider()
            FormTimeRow("Saat", hour, minute) { h, m -> hour = h; minute = m }
            FormColumnRow { TimeChipPicker(hour, minute) { hour = it; minute = 0 } }
            RowDivider()
            FormColumnRow {
                Text("Süre (dakika)", style = Type.body, color = c.ink)
                DurationChipPicker(duration) { duration = it }
            }
            if (clashes.isNotEmpty()) {
                RowDivider()
                WeeklyClashNotice(clashes, notebook)
            }
        }

        FormSection(header = "Ücret") {
            FormValueRow("Standart ücret", Fmt.money(Lesson.standardFee(selectedStudent, duration)))
            RowDivider()
            FormToggleRow("Derse özel ücret", useCustomFee, { useCustomFee = it })
            if (useCustomFee) {
                RowDivider()
                FormAmountRow("Özel ücret", customFee, { customFee = it })
            }
        }

        if (!isNew) {
            FormSection(footer = "Duraklatılan haftalık ders yeni ders planlamaz; mevcut dersler silinmez.") {
                FormToggleRow("Duraklat", isPaused, { isPaused = it })
            }
            FormSection {
                FormButtonRow("Haftalık Dersi Bitir", color = c.red, center = true) { confirmEnd = true }
            }
        }

        Text(
            if (isNew) "Dersler ${Recurring.HORIZON_DAYS / 7} hafta ilerisi için otomatik planlanır."
            else "Gün, saat, süre ya da ücret değişirse gelecekteki planlı dersler yeni düzene taşınır; konu ve notları korunur.",
            style = Type.footnote, color = c.inkSoft,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (confirmEnd && template != null) {
        ActionSheet(
            title = "Haftalık ders bitirilsin mi?",
            message = "İşlenmiş dersler her durumda korunur.",
            actions = listOf(
                SheetAction("Gelecek Planlı Dersleri de Sil", destructive = true) {
                    app.edit { endSeries(template.id, after = now) }
                    nav.popBackStack()
                },
                SheetAction("Planlı Dersler Kalsın", destructive = true) {
                    app.edit { deleteTemplate(template.id) }
                    nav.popBackStack()
                },
            ),
            onDismiss = { confirmEnd = false },
        )
    }
}
