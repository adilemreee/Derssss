package xyz.adilemree.dersdefteri.ui.homework

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Homework
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.addDays
import xyz.adilemree.dersdefteri.data.daysBetween
import xyz.adilemree.dersdefteri.data.startOfDay
import xyz.adilemree.dersdefteri.ui.HomeworkFormRoute
import xyz.adilemree.dersdefteri.ui.components.ActionSheet
import xyz.adilemree.dersdefteri.ui.components.ConfirmDialog
import xyz.adilemree.dersdefteri.ui.components.EmptyState
import xyz.adilemree.dersdefteri.ui.components.FormButtonRow
import xyz.adilemree.dersdefteri.ui.components.FormDateRow
import xyz.adilemree.dersdefteri.ui.components.FormPickerRow
import xyz.adilemree.dersdefteri.ui.components.FormScaffold
import xyz.adilemree.dersdefteri.ui.components.FormSection
import xyz.adilemree.dersdefteri.ui.components.FormTextRow
import xyz.adilemree.dersdefteri.ui.components.RowDivider
import xyz.adilemree.dersdefteri.ui.components.Segmented
import xyz.adilemree.dersdefteri.ui.components.SheetAction
import xyz.adilemree.dersdefteri.ui.components.strike
import xyz.adilemree.dersdefteri.ui.nav
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.StickyNoteColors
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.bold
import xyz.adilemree.dersdefteri.ui.theme.semibold
import xyz.adilemree.dersdefteri.ui.theme.serif
import xyz.adilemree.dersdefteri.ui.theme.studentColor

private enum class HomeworkFilter(val title: String) { ACTIVE("Aktif"), LATE("Geciken"), DONE("Tamamlanan") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkScreen() {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    var filter by rememberSaveable { mutableStateOf(HomeworkFilter.ACTIVE) }
    var menuFor by remember { mutableStateOf<Homework?>(null) }
    val c = AppTheme.colors
    val now = System.currentTimeMillis()

    fun list(f: HomeworkFilter): List<Homework> = when (f) {
        HomeworkFilter.ACTIVE -> notebook.homeworks.filter { !it.isDone && !it.isLate(now) }
        HomeworkFilter.LATE -> notebook.homeworks.filter { it.isLate(now) }
        HomeworkFilter.DONE -> notebook.homeworks.filter { it.isDone }.sortedByDescending { it.doneDate ?: it.dueDate }
    }
    val filtered = list(filter)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = c.paper,
        topBar = {
            LargeTopAppBar(
                title = { Text("Ödevler", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = c.ink) },
                actions = { IconButton(onClick = { nav.navigate(HomeworkFormRoute()) }) { Icon(Icons.Rounded.Add, "Ödev Ver", tint = c.accent) } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.paper, scrolledContainerColor = c.paper),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 1000.dp).fillMaxSize()) {
                Segmented(
                    HomeworkFilter.entries.map { it to "${it.title} (${list(it).size})" },
                    filter, { filter = it },
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (filtered.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                when (filter) {
                                    HomeworkFilter.ACTIVE -> Icons.AutoMirrored.Rounded.MenuBook
                                    HomeworkFilter.LATE -> Icons.Rounded.Verified
                                    HomeworkFilter.DONE -> Icons.Rounded.Inbox
                                },
                                when (filter) {
                                    HomeworkFilter.ACTIVE -> "Aktif ödev yok"
                                    HomeworkFilter.LATE -> "Geciken ödev yok 🎉"
                                    HomeworkFilter.DONE -> "Tamamlanan ödev yok"
                                },
                                actionTitle = if (filter == HomeworkFilter.ACTIVE) "Ödev Ver" else null,
                                onAction = { nav.navigate(HomeworkFormRoute()) },
                                modifier = Modifier.padding(top = 30.dp),
                            )
                        }
                    }
                    itemsIndexed(filtered, key = { _, hw -> hw.id }) { index, hw ->
                        StickyNoteCard(
                            hw, index, notebook,
                            onEdit = { nav.navigate(HomeworkFormRoute(homeworkId = hw.id)) },
                            onMenu = { menuFor = hw },
                            onToggle = { app.edit { toggleHomework(hw.id) } },
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { hw ->
        ActionSheet(
            title = hw.title,
            actions = listOf(
                SheetAction("Düzenle", Icons.Rounded.Edit) { nav.navigate(HomeworkFormRoute(homeworkId = hw.id)) },
                SheetAction("Sil", Icons.Rounded.Delete, destructive = true) { app.edit { homeworks.remove(hw.id) } },
            ),
            onDismiss = { menuFor = null },
        )
    }
}

// MARK: - Yapışkan not kartı

/// Notun sağ üst köşesindeki üçgen işaret.
private val CornerMark = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    close()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickyNoteCard(homework: Homework, index: Int, notebook: Notebook, onEdit: () -> Unit, onMenu: () -> Unit, onToggle: () -> Unit) {
    val c = AppTheme.colors
    val (light, dark) = StickyNoteColors[index % StickyNoteColors.size]
    val noteColor = if (c.isDark) dark else light
    val student = notebook.student(homework.studentId)
    val now = System.currentTimeMillis()
    /// Teslime kalan gün; geçmişse eksi.
    val daysLeft = daysBetween(startOfDay(now), startOfDay(homework.dueDate))
    /// Geciken kırmızı, bugün/yarın teslim turuncu; diğerleri renksiz.
    val urgency: Color? = when {
        homework.isDone -> null
        daysLeft < 0 -> c.red
        daysLeft <= 1 -> c.amber
        else -> null
    }
    val footer = if (homework.isDone) {
        homework.doneDate?.let { "Yapıldı: ${Fmt.dayMonthShort(it)}" } ?: "Yapıldı"
    } else when {
        daysLeft < 0 -> "${-daysLeft} gün gecikti"
        daysLeft == 0 -> "Bugün teslim"
        daysLeft == 1 -> "Yarın teslim"
        else -> "$daysLeft gün kaldı"
    }

    Box(Modifier.rotate(if (index % 2 == 0) -0.8f else 0.8f)) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .shadow(5.dp, RoundedCornerShape(6.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                .clip(RoundedCornerShape(6.dp))
                .background(noteColor)
                .combinedClickable(onClick = onEdit, onLongClick = onMenu)
                .alpha(if (homework.isDone) 0.7f else 1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(7.dp).background(student?.let { studentColor(it.colorIndex) } ?: c.inkSoft, CircleShape))
                Text(student?.name ?: "—", style = Type.caption2.bold(), color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                homework.title,
                style = Type.subheadline.semibold().serif(),
                color = c.ink,
                textDecoration = strike(homework.isDone),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (homework.detail.isNotEmpty()) {
                Text(homework.detail, style = Type.caption2, color = c.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f, fill = false))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(footer, style = Type.caption2.bold(), color = urgency ?: if (homework.isDone) c.green else c.inkSoft, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (homework.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        if (homework.isDone) "Tamamlandı" else "Tamamlandı olarak işaretle",
                        tint = if (homework.isDone) c.green else c.inkSoft.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        // Teslimi yaklaşan ya da geçen notun köşesi renklenir.
        if (urgency != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(RoundedCornerShape(topEnd = 6.dp))
                    .background(urgency, CornerMark),
            )
        }
        Icon(
            Icons.Rounded.PushPin, null, tint = c.red,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).size(18.dp).rotate(38f),
        )
    }
}

// MARK: - Ödev formu

@Composable
fun HomeworkFormScreen(homeworkId: String?, defaultStudentId: String?) {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val homework = homeworkId?.let { notebook.homeworkById[it] }
    val c = AppTheme.colors

    var studentId by rememberSaveable { mutableStateOf(homework?.studentId ?: defaultStudentId) }
    var title by rememberSaveable { mutableStateOf(homework?.title ?: "") }
    var detail by rememberSaveable { mutableStateOf(homework?.detail ?: "") }
    var dueDate by rememberSaveable { mutableLongStateOf(homework?.dueDate ?: addDays(startOfDay(System.currentTimeMillis()), 7)) }
    var confirmDelete by remember { mutableStateOf(false) }

    val students = notebook.students.filter { !it.isArchived || it.id == studentId }
    val selected = notebook.student(studentId)

    FormScaffold(
        title = if (homework == null) "Yeni Ödev" else "Ödevi Düzenle",
        onClose = { nav.popBackStack() },
        actionEnabled = selected != null && title.isNotBlank(),
        onAction = {
            val sid = studentId ?: return@FormScaffold
            val t = title.trim(); val d = detail.trim(); val due = dueDate
            app.edit { saveHomework(homework?.id, sid, t, d, due) }
            nav.popBackStack()
        },
    ) {
        FormSection(header = "Ödev") {
            FormPickerRow("Öğrenci", listOf(null) + students, selected, { it?.name ?: "Seçiniz" }) { studentId = it?.id }
            RowDivider()
            FormTextRow(title, { title = it }, "Başlık (ör. Türev testi 1-20)")
            RowDivider()
            FormTextRow(detail, { detail = it }, "Açıklama", singleLine = false, minLines = 2)
            RowDivider()
            FormDateRow("Teslim tarihi", dueDate) { dueDate = it }
        }
        if (homework != null) {
            FormSection {
                FormButtonRow("Ödevi Sil", color = c.red, center = true) { confirmDelete = true }
            }
        }
    }

    if (confirmDelete && homework != null) {
        ConfirmDialog(
            title = "Ödev silinsin mi?",
            message = homework.title,
            confirmTitle = "Ödevi Sil",
            onConfirm = {
                app.edit { homeworks.remove(homework.id) }
                nav.popBackStack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
