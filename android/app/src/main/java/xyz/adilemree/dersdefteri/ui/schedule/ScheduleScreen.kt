package xyz.adilemree.dersdefteri.ui.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.data.CancellationReason
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Lesson
import xyz.adilemree.dersdefteri.data.LessonStatus
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.addDays
import xyz.adilemree.dersdefteri.data.addMonths
import xyz.adilemree.dersdefteri.data.calendarWeekday
import xyz.adilemree.dersdefteri.data.dayOfMonth
import xyz.adilemree.dersdefteri.data.isSameDay
import xyz.adilemree.dersdefteri.data.isToday
import xyz.adilemree.dersdefteri.data.startOfMonth
import xyz.adilemree.dersdefteri.data.startOfWeek
import xyz.adilemree.dersdefteri.ui.LessonFormRoute
import xyz.adilemree.dersdefteri.ui.RecurringLessonsRoute
import xyz.adilemree.dersdefteri.ui.common.LessonMenus
import xyz.adilemree.dersdefteri.ui.common.rememberLessonMenuState
import xyz.adilemree.dersdefteri.ui.components.Chalkboard
import xyz.adilemree.dersdefteri.ui.components.Chip
import xyz.adilemree.dersdefteri.ui.components.EmptyState
import xyz.adilemree.dersdefteri.ui.components.LessonRow
import xyz.adilemree.dersdefteri.ui.components.PillButton
import xyz.adilemree.dersdefteri.ui.components.SectionHeader
import xyz.adilemree.dersdefteri.ui.components.StatusChip
import xyz.adilemree.dersdefteri.ui.components.card
import xyz.adilemree.dersdefteri.ui.nav
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.bold
import xyz.adilemree.dersdefteri.ui.theme.semibold
import xyz.adilemree.dersdefteri.ui.theme.serif
import xyz.adilemree.dersdefteri.ui.theme.studentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(initialDate: Long?) {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    var selected by rememberSaveable { mutableLongStateOf(initialDate ?: System.currentTimeMillis()) }
    var showMonth by rememberSaveable { mutableStateOf(false) }
    val menu = rememberLessonMenuState()
    val c = AppTheme.colors

    val weekStart = startOfWeek(selected)
    val weekDays = (0 until 7).map { addDays(weekStart, it) }
    val dayLessons = notebook.lessons.filter { isSameDay(it.date, selected) }
    val weekEnd = addDays(weekStart, 7)
    val weekLessons = notebook.lessons.filter { it.date in weekStart until weekEnd && !it.isCancelled }

    Scaffold(
        containerColor = c.paper,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Program", style = Type.headline.serif(), color = c.ink) },
                navigationIcon = {
                    // Bugündeyken gizlenir; soluk bir düğme bozuk gibi görünüyordu.
                    if (!isToday(selected)) {
                        TextButton(onClick = { selected = System.currentTimeMillis() }) {
                            Text("Bugün", style = Type.body.semibold(), color = c.accent)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(RecurringLessonsRoute) }) { Icon(Icons.Rounded.Repeat, "Haftalık Dersler", tint = c.accent) }
                    IconButton(onClick = { nav.navigate(LessonFormRoute(date = selected)) }) { Icon(Icons.Rounded.Add, "Ders Ekle", tint = c.accent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.paper, scrolledContainerColor = c.paper),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                // Hafta gezinme
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selected = addDays(selected, -7) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Önceki hafta", tint = c.accent)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text("${Fmt.dayMonthShort(weekStart)} – ${Fmt.dayMonthShort(addDays(weekStart, 6))}", style = Type.headline.serif(), color = c.ink)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.12f)).clickable { showMonth = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.CalendarMonth, "Aylık takvim", tint = c.accent, modifier = Modifier.size(18.dp)) }
                    }
                    IconButton(onClick = { selected = addDays(selected, 7) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Sonraki hafta", tint = c.accent)
                    }
                }
                // Gün şeridi
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekDays.forEach { day ->
                        DayCell(day, isSameDay(day, selected), notebook, Modifier.weight(1f)) { selected = day }
                    }
                }

                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${Fmt.dayMonth(selected)} ${Fmt.weekday(selected)}", style = Type.subheadline.bold().serif(), color = c.ink)
                            if (isToday(selected)) Chip("Bugün", c.amber, filled = true)
                            Spacer(Modifier.weight(1f))
                            if (dayLessons.isNotEmpty()) Text("${dayLessons.size} ders", style = Type.caption.semibold(), color = c.inkSoft)
                        }
                    }
                    if (dayLessons.isEmpty()) {
                        item {
                            EmptyState(Icons.Rounded.Bedtime, "Bu gün ders yok", actionTitle = "Ders Ekle",
                                onAction = { nav.navigate(LessonFormRoute(date = selected)) })
                        }
                    } else {
                        items(dayLessons, key = { it.id }) { lesson ->
                            ScheduleLessonCard(
                                lesson, notebook,
                                onEdit = { nav.navigate(LessonFormRoute(lessonId = lesson.id)) },
                                onMenu = { menu.menuFor = lesson },
                                onDone = { app.edit { setStatus(lesson.id, LessonStatus.COMPLETED) } },
                            )
                        }
                    }
                    if (weekLessons.isNotEmpty()) {
                        item {
                            Chalkboard(Modifier.padding(top = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Icon(Icons.Rounded.Functions, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
                                    Column {
                                        Text("Bu haftanın özeti", style = Type.caption, color = Color.White.copy(alpha = 0.65f))
                                        Text(
                                            "${weekLessons.size} ders • ${Fmt.hours(weekLessons.sumOf { it.duration })} • ${Fmt.money(weekLessons.sumOf { notebook.fee(it) })}",
                                            style = Type.subheadline.bold().serif(), color = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LessonMenus(menu, notebook, showEdit = true)
    if (showMonth) {
        MonthCalendarDialog(selected, notebook, onSelect = { selected = it }, onDismiss = { showMonth = false })
    }
}

@Composable
private fun DayCell(day: Long, isSelected: Boolean, notebook: Notebook, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val lessons = notebook.lessons.filter { isSameDay(it.date, day) && !it.isCancelled }
    val today = isToday(day)
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) c.board else c.card)
            .border(if (today && !isSelected) 1.8.dp else 1.dp, if (today && !isSelected) c.amber else c.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(Fmt.weekdayShort(day), style = Type.caption2.semibold(), color = if (isSelected) Color.White.copy(alpha = 0.8f) else c.inkSoft, maxLines = 1)
        Text("${dayOfMonth(day)}", style = Type.headline.bold().serif(), color = if (isSelected) Color.White else c.ink)
        Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            lessons.take(3).forEach { lesson ->
                val color = notebook.studentOf(lesson)?.let { studentColor(it.colorIndex) } ?: c.inkSoft
                Box(Modifier.size(4.dp).background(if (isSelected) Color.White else color, CircleShape))
            }
        }
    }
}

// MARK: - Program ders kartı

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduleLessonCard(lesson: Lesson, notebook: Notebook, onEdit: () -> Unit, onMenu: () -> Unit, onDone: () -> Unit) {
    val c = AppTheme.colors
    val student = notebook.studentOf(lesson)
    val color = student?.let { studentColor(it.colorIndex) } ?: c.inkSoft
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onEdit, onLongClick = onMenu)
            .card(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Fmt.time(lesson.date), style = Type.headline.bold().serif(), color = c.ink)
            Text(Fmt.time(lesson.endDate), style = Type.caption, color = c.inkSoft)
        }
        Box(Modifier.width(4.dp).height(46.dp).background(color, CircleShape))
        // Üst satır: kim ve ne kadar. Alt satır: ders ve konu, sağda durum.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(student?.name ?: "—", style = Type.subheadline.bold(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(Fmt.money(notebook.fee(lesson)), style = Type.subheadline.bold(), color = c.ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (lesson.templateId != null) Icon(Icons.Rounded.Repeat, "Her hafta", tint = c.inkSoft, modifier = Modifier.size(13.dp))
                val subject = student?.subject.orEmpty()
                if (subject.isNotEmpty()) Chip(subject, color)
                if (lesson.isCancelled && lesson.reason != CancellationReason.NONE) {
                    Text(lesson.reason.shortTitle, style = Type.caption, color = c.red, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                } else if (lesson.topic.isNotEmpty()) {
                    Text(lesson.topic, style = Type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.weight(1f))
                if (!lesson.isPlanned) StatusChip(lesson.lessonStatus)
            }
        }
        if (lesson.isPlanned) {
            IconButton(onClick = onDone, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.CheckCircle, "İşlendi", tint = c.green, modifier = Modifier.size(30.dp))
            }
        }
    }
}

// MARK: - Aylık takvim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthCalendarDialog(selectedDate: Long, notebook: Notebook, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    val nav = nav()
    var visibleMonth by remember { mutableLongStateOf(startOfMonth(selectedDate)) }
    var selected by remember { mutableLongStateOf(selectedDate) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            containerColor = c.paper,
            topBar = {
                TopAppBar(
                    title = { Text("Aylık Takvim", style = Type.headline.serif(), color = c.ink) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Kapat", tint = c.ink) } },
                    actions = {
                        TextButton(onClick = { onSelect(selected); onDismiss() }) { Text("Güne Git", style = Type.body.bold(), color = c.accent) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = c.paper),
                )
            },
        ) { padding ->
            val nextMonth = addMonths(visibleMonth, 1)
            val monthLessons = notebook.lessons.filter { it.date in visibleMonth until nextMonth }
            val active = monthLessons.filter { !it.isCancelled }
            val summary = if (active.isEmpty()) "Bu ay ders yok"
            else "${active.size} ders • ${Fmt.hours(active.sumOf { it.duration })} • ${Fmt.money(active.sumOf { notebook.fee(it) })}"
            // Ayın ilk günü haftanın kaçıncı günü (Pazartesi başlangıçlı)
            val leading = (calendarWeekday(visibleMonth) - 2 + 7) % 7
            val gridStart = addDays(visibleMonth, -leading)
            val dayLessons = notebook.lessons.filter { isSameDay(it.date, selected) }

            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Chalkboard(Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RoundIcon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft) { visibleMonth = addMonths(visibleMonth, -1) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(Fmt.monthYear(visibleMonth), style = Type.title3, color = Color.White)
                            Text(summary, style = Type.caption, color = Color.White.copy(alpha = 0.75f))
                        }
                        RoundIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight) { visibleMonth = addMonths(visibleMonth, 1) }
                    }
                }

                // Durum dağılımı
                if (monthLessons.isNotEmpty()) {
                    val completed = monthLessons.count { it.isCompleted }
                    val planned = monthLessons.count { it.isPlanned }
                    val cancelled = monthLessons.count { it.isCancelled }
                    val total = monthLessons.size.toFloat()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)) {
                            if (completed > 0) Box(Modifier.weight(completed / total).fillMaxSize().background(c.green))
                            if (planned > 0) Box(Modifier.weight(planned / total).fillMaxSize().background(c.blue))
                            if (cancelled > 0) Box(Modifier.weight(cancelled / total).fillMaxSize().background(c.red))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (completed > 0) Legend(c.green, "$completed işlendi")
                            if (planned > 0) Legend(c.blue, "$planned planlı")
                            if (cancelled > 0) Legend(c.red, "$cancelled iptal")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz").forEach {
                        Text(it, style = Type.caption2.bold(), color = c.inkSoft, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    (0 until 6).forEach { week ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (0 until 7).forEach { d ->
                                val day = addDays(gridStart, week * 7 + d)
                                MonthDayCell(day, visibleMonth, selected, notebook, Modifier.weight(1f)) { selected = day }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(Fmt.dayMonth(selected), Icons.Rounded.CalendarMonth) {
                        if (isToday(selected)) Chip("Bugün", c.amber, filled = true)
                        PillButton("Ders ekle", Icons.Rounded.Add) {
                            onDismiss()
                            nav.navigate(LessonFormRoute(date = selected))
                        }
                    }
                    if (dayLessons.isEmpty()) {
                        EmptyState(Icons.Rounded.Bedtime, "Bu gün ders yok")
                    } else {
                        dayLessons.forEach { LessonRow(it, notebook) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(text, style = Type.caption2.semibold(), color = AppTheme.colors.inkSoft)
    }
}

@Composable
private fun MonthDayCell(day: Long, visibleMonth: Long, selected: Long, notebook: Notebook, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val isSelected = isSameDay(day, selected)
    val isCurrentMonth = startOfMonth(day) == visibleMonth
    val lessons = notebook.lessons.filter { isSameDay(it.date, day) && !it.isCancelled }
    // Yoğunluğa göre ısı haritası tonu
    val fill = when {
        isSelected -> c.board
        !isCurrentMonth -> c.card.copy(alpha = 0.48f)
        lessons.isEmpty() -> c.card
        lessons.size == 1 -> c.green.copy(alpha = 0.14f)
        lessons.size == 2 -> c.green.copy(alpha = 0.24f)
        else -> c.green.copy(alpha = 0.34f)
    }
    val numberColor = when {
        isSelected -> Color.White
        !isCurrentMonth -> c.inkSoft.copy(alpha = 0.45f)
        else -> c.ink
    }
    val today = isToday(day)
    Column(
        modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .border(if (today && !isSelected) 1.6.dp else 1.dp, if (today && !isSelected) c.amber else c.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("${dayOfMonth(day)}", style = Type.subheadline.bold().serif(), color = numberColor)
        Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isCurrentMonth) {
                lessons.take(3).forEach { lesson ->
                    val color = notebook.studentOf(lesson)?.let { studentColor(it.colorIndex) } ?: c.inkSoft
                    Box(Modifier.size(5.dp).background(if (isSelected) Color.White else color, CircleShape))
                }
            }
        }
    }
}
