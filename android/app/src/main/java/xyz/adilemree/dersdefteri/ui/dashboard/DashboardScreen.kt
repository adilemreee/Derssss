package xyz.adilemree.dersdefteri.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CurrencyLira
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Lesson
import xyz.adilemree.dersdefteri.data.LessonStatus
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.addDays
import xyz.adilemree.dersdefteri.data.dayOfMonth
import xyz.adilemree.dersdefteri.data.hourOf
import xyz.adilemree.dersdefteri.data.isSameDay
import xyz.adilemree.dersdefteri.data.isToday
import xyz.adilemree.dersdefteri.data.startOfDay
import xyz.adilemree.dersdefteri.data.startOfMonth
import xyz.adilemree.dersdefteri.data.startOfWeek
import xyz.adilemree.dersdefteri.ui.PaymentFormRoute
import xyz.adilemree.dersdefteri.ui.LessonFormRoute
import xyz.adilemree.dersdefteri.ui.QuickLessonRoute
import xyz.adilemree.dersdefteri.ui.ScheduleRoute
import xyz.adilemree.dersdefteri.ui.SettingsRoute
import xyz.adilemree.dersdefteri.ui.StudentFormRoute
import xyz.adilemree.dersdefteri.ui.UnmarkedLessonsRoute
import xyz.adilemree.dersdefteri.ui.common.LessonMenus
import xyz.adilemree.dersdefteri.ui.common.MarkAllCompletedDialog
import xyz.adilemree.dersdefteri.ui.common.rememberLessonMenuState
import xyz.adilemree.dersdefteri.ui.common.unmarkedLessons
import xyz.adilemree.dersdefteri.ui.components.Chalkboard
import xyz.adilemree.dersdefteri.ui.components.Chip
import xyz.adilemree.dersdefteri.ui.components.EmptyState
import xyz.adilemree.dersdefteri.ui.components.LessonRow
import xyz.adilemree.dersdefteri.ui.components.PillButton
import xyz.adilemree.dersdefteri.ui.components.PrimaryButton
import xyz.adilemree.dersdefteri.ui.components.SectionHeader
import xyz.adilemree.dersdefteri.ui.components.StudentAvatar
import xyz.adilemree.dersdefteri.ui.components.TabScaffold
import xyz.adilemree.dersdefteri.ui.components.card
import xyz.adilemree.dersdefteri.ui.nav
import xyz.adilemree.dersdefteri.ui.switchTab
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.bold
import xyz.adilemree.dersdefteri.ui.theme.semibold
import xyz.adilemree.dersdefteri.ui.theme.serif
import xyz.adilemree.dersdefteri.ui.theme.studentColor

@Composable
fun DashboardScreen() {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val teacherName by app.settings.teacherName.flow.collectAsStateWithLifecycle()
    val menu = rememberLessonMenuState()
    var confirmMarkAll by remember { mutableStateOf(false) }
    val c = AppTheme.colors
    val now = System.currentTimeMillis()

    val todayLessons = notebook.lessons.filter { isToday(it.date, now) }
    val unmarked = unmarkedLessons(notebook, now)

    TabScaffold(
        title = "Ders Defteri",
        actions = {
            IconButton(onClick = { nav.navigate(SettingsRoute) }) {
                Icon(Icons.Rounded.Settings, "Ayarlar", tint = c.accent)
            }
        },
    ) {
        if (notebook.isEmpty) {
            item { WelcomeContent(app.settings.teacherDisplayName(teacherName)) { nav.navigate(StudentFormRoute()) } }
            return@TabScaffold
        }

        // Günün dersleri en üstte: ekran açıldığında ilk bakılan şey bu.
        item { HeaderBoard(notebook, todayLessons, app.settings.teacherDisplayName(teacherName), now) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Bugünün Dersleri", Icons.Rounded.WbSunny) {
                    PillButton("Ders Ekle", Icons.Rounded.Add) { nav.navigate(QuickLessonRoute()) }
                }
                if (todayLessons.isEmpty()) {
                    EmptyState(
                        Icons.Rounded.Bedtime, "Bugün ders yok",
                        message = "Kendine bir çay ısmarla ya da hemen bir ders planla.",
                        actionTitle = "Ders Ekle", onAction = { nav.navigate(QuickLessonRoute()) },
                    )
                } else {
                    todayLessons.forEach { lesson ->
                        DashboardLessonRow(
                            lesson, notebook,
                            onDone = { app.edit { setStatus(lesson.id, LessonStatus.COMPLETED) } },
                            onOpen = { nav.navigate(LessonFormRoute(lessonId = lesson.id)) },
                            onMenu = { menu.menuFor = lesson },
                        )
                    }
                }
            }
        }

        // Saati geçmiş ama hâlâ "planlı" duran dersler. İşlendi denmeyen ders
        // bakiyeye yansımaz; haftalık otomatik derslerle bu kolayca unutulur.
        if (unmarked.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("İşaretlenmemiş Dersler", Icons.Rounded.Error) {
                        if (unmarked.size > 1) {
                            PillButton("Tümü İşlendi", Icons.Rounded.Check, tint = c.green) { confirmMarkAll = true }
                        }
                    }
                    Text(
                        "Saati geçti ama hâlâ planlı görünüyor. Yapıldıysa İşlendi de; ücret ancak öyle bakiyeye yansır.",
                        style = Type.caption,
                        color = c.inkSoft,
                    )
                    unmarked.take(4).forEach { lesson ->
                        UnmarkedLessonRow(
                            lesson, notebook,
                            onDone = { app.edit { setStatus(lesson.id, LessonStatus.COMPLETED) } },
                            onCancel = { menu.cancelFor = lesson },
                            onOpen = { nav.navigate(LessonFormRoute(lessonId = lesson.id)) },
                            onMenu = { menu.menuFor = lesson },
                        )
                    }
                    if (unmarked.size > 4) {
                        SeeAllButton("Tümünü gör (${unmarked.size})") { nav.navigate(UnmarkedLessonsRoute) }
                    }
                }
            }
        }

        item { StatsStrip(notebook, now) }

        item { UpcomingSection(notebook, now) }

        val debtors = notebook.students.filter { notebook.balance(it.id) > 0.5 }
            .sortedByDescending { notebook.balance(it.id) }
        if (debtors.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Ödeme Bekleyenler", Icons.Rounded.CurrencyLira)
                    debtors.forEach { student ->
                        val stats = notebook.stats(student.id)
                        Row(
                            Modifier.fillMaxWidth().card(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StudentAvatar(student, 40.dp, modifier = Modifier.alpha(if (student.isArchived) 0.55f else 1f))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(student.name, style = Type.subheadline.semibold(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    if (student.isArchived) Chip("Arşivde", c.inkSoft)
                                }
                                Text(
                                    "${stats.completedCount} işlenen ders • ${Fmt.money(stats.totalPaid)} ödendi",
                                    style = Type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(Fmt.money(stats.balance), style = Type.subheadline.bold(), color = c.red)
                                PillButton("Ödeme Al", tint = c.green) { nav.navigate(PaymentFormRoute(studentId = student.id)) }
                            }
                        }
                    }
                }
            }
        }

        item {
            val active = notebook.homeworks.filter { !it.isDone }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Aktif Ödevler", Icons.AutoMirrored.Rounded.MenuBook)
                if (active.isEmpty()) {
                    EmptyState(Icons.Rounded.Verified, "Bekleyen ödev yok")
                } else {
                    active.take(4).forEach { hw ->
                        val student = notebook.student(hw.studentId)
                        Row(
                            Modifier.fillMaxWidth().card(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(8.dp).background(student?.let { studentColor(it.colorIndex) } ?: c.inkSoft, CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(hw.title, style = Type.subheadline.semibold(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(student?.name ?: "—", style = Type.caption, color = c.inkSoft)
                            }
                            val late = hw.isLate(now)
                            Text(
                                if (late) "Gecikti!" else "Son: ${Fmt.dayMonthShort(hw.dueDate)}",
                                style = Type.caption.bold(),
                                color = if (late) c.red else c.inkSoft,
                            )
                        }
                    }
                }
            }
        }
    }

    LessonMenus(menu, notebook, showEdit = true, showDelete = false)
    if (confirmMarkAll) MarkAllCompletedDialog(unmarked, notebook, onDismiss = { confirmMarkAll = false })
}

// MARK: - Hoş geldin (boş başlangıç)

@Composable
private fun WelcomeContent(teacher: String, onAddStudent: () -> Unit) {
    val c = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Chalkboard {
            Text("Hoş geldin $teacher 👋", style = Type.title2, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(
                "Ders Defteri; özel ders programını, ücretleri, ödemeleri ve ödevleri tek yerden takip etmen için hazır. İlk öğrencini ekleyerek başla.",
                style = Type.subheadline.serif().copy(fontStyle = FontStyle.Italic),
                color = Color.White.copy(alpha = 0.88f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureRow(Icons.Rounded.School, c.blue, "Öğrenci kartları", "Ders, sınıf, saatlik ücret ve veli bilgileri")
            FeatureRow(Icons.Rounded.CalendarMonth, c.amber, "Haftalık program", "Dersleri planla, işle ya da iptal et")
            FeatureRow(Icons.Rounded.CurrencyLira, c.green, "Ödeme takibi", "Kim ne kadar ödedi, kimde bakiye kaldı")
            FeatureRow(Icons.Rounded.NotificationsActive, c.red, "Ders hatırlatıcıları", "Ders yaklaşınca bildirim al")
            // Ücretsiz kullanıcıya Pro özelliğini ücretsizmiş gibi göstermemek için.
            FeatureRow(Icons.Rounded.Sync, c.accent, "Hesabında yedekli", "Yeni telefonda kayıtların geri gelir", isPro = true)
        }
        PrimaryButton("İlk Öğrencini Ekle", icon = Icons.Rounded.Add, onClick = onAddStudent)
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, tint: Color, title: String, text: String, isPro: Boolean = false) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().card(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = Type.subheadline.bold().serif(), color = c.ink)
                if (isPro) Chip("Pro", c.amber, filled = true)
            }
            Text(text, style = Type.caption, color = c.inkSoft)
        }
    }
}

// MARK: - Kara tahta başlık

@Composable
private fun HeaderBoard(notebook: Notebook, todayLessons: List<Lesson>, teacher: String, now: Long) {
    val greeting = when (hourOf(now)) {
        in 5..11 -> "Günaydın $teacher 👋"
        in 12..17 -> "İyi dersler $teacher 👋"
        else -> "İyi akşamlar $teacher 👋"
    }
    val active = todayLessons.filter { !it.isCancelled }
    val summary = if (active.isEmpty()) "Bugün ders yok — kendine bir çay ısmarla ☕️"
    else "Bugün ${active.size} ders • ${Fmt.hours(active.sumOf { it.duration })} • ${Fmt.money(active.sumOf { notebook.fee(it) })}"

    Chalkboard(Modifier.padding(top = 4.dp)) {
        Text("${Fmt.long(now)} · ${Fmt.weekday(now)}", style = Type.caption.semibold(), color = Color.White.copy(alpha = 0.65f))
        Spacer(Modifier.height(6.dp))
        Text(greeting, style = Type.title2, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(summary, style = Type.subheadline.serif().copy(fontStyle = FontStyle.Italic), color = Color.White.copy(alpha = 0.88f))
    }
}

// MARK: - İstatistikler

@Composable
private fun StatsStrip(notebook: Notebook, now: Long) {
    val c = AppTheme.colors
    val weekStart = startOfWeek(now)
    val weekEnd = addDays(weekStart, 7)
    val weekMinutes = notebook.lessons.filter { it.date in weekStart until weekEnd && !it.isCancelled }.sumOf { it.duration }
    val monthStart = startOfMonth(now)
    val monthCollected = notebook.payments.filter { it.date >= monthStart }.sumOf { it.amount }
    val pending = notebook.students.sumOf { maxOf(notebook.balance(it.id), 0.0) }

    Row(Modifier.fillMaxWidth().card(12.dp), verticalAlignment = Alignment.CenterVertically) {
        StatCell("Bu hafta", Fmt.hours(weekMinutes), c.blue, Modifier.weight(1f))
        VerticalDivider(Modifier.height(36.dp), color = c.line)
        StatCell("Bu ay tahsilat", Fmt.money(monthCollected), c.green, Modifier.weight(1f))
        VerticalDivider(Modifier.height(36.dp), color = c.line)
        StatCell("Bekleyen", Fmt.money(pending), c.red, Modifier.weight(1f), valueColor = if (pending > 0.5) c.red else c.ink)
    }
}

@Composable
private fun StatCell(title: String, value: String, dot: Color, modifier: Modifier, valueColor: Color = AppTheme.colors.ink) {
    val c = AppTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = Type.headline.bold().serif(), color = valueColor, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(6.dp).background(dot, CircleShape))
            Text(title, style = Type.caption2.semibold(), color = c.inkSoft, maxLines = 1)
        }
    }
}

// MARK: - Ders satırları

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardLessonRow(lesson: Lesson, notebook: Notebook, onDone: () -> Unit, onOpen: () -> Unit, onMenu: () -> Unit) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LessonRow(
            lesson, notebook,
            hidesPlannedStatus = true,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onOpen, onLongClick = onMenu),
        )
        if (lesson.isPlanned) {
            IconButton(onClick = onDone, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.CheckCircle, "İşlendi", tint = c.green, modifier = Modifier.size(30.dp))
            }
        }
    }
}

/// Tarihli ders satırı ve yanında iptal / işlendi düğmeleri.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnmarkedLessonRow(
    lesson: Lesson,
    notebook: Notebook,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LessonRow(
            lesson, notebook,
            showDate = true,
            hidesPlannedStatus = true,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onOpen, onLongClick = onMenu),
        )
        IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Cancel, "İptal olarak işaretle", tint = c.red.copy(alpha = 0.75f), modifier = Modifier.size(30.dp))
        }
        IconButton(onClick = onDone, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.CheckCircle, "İşlendi olarak işaretle", tint = c.green, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
fun SeeAllButton(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = c.accent, modifier = Modifier.size(18.dp))
        Text(text, style = Type.subheadline.bold(), color = c.accent, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.accent, modifier = Modifier.size(18.dp))
    }
}

// MARK: - Yaklaşan dersler

/// Bugünün dersleri hemen üstte listelendiği için yaklaşan kart yarından
/// itibaren ilk ders gününü gösterir.
@Composable
private fun UpcomingSection(notebook: Notebook, now: Long) {
    val c = AppTheme.colors
    val nav = nav()
    val tomorrow = addDays(startOfDay(now), 1)
    val future = notebook.lessons.filter { it.isPlanned && it.date >= tomorrow }
    val nearestDay = future.firstOrNull()?.let { startOfDay(it.date) }
    val dayLessons = if (nearestDay == null) emptyList()
    else future.filter { it.date >= nearestDay && it.date < addDays(nearestDay, 1) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Yaklaşan Dersler", Icons.AutoMirrored.Rounded.EventNote)
        if (nearestDay == null) {
            EmptyState(
                Icons.Rounded.CalendarMonth, "Yarından itibaren planlı ders yok",
                actionTitle = "Ders Planla", onAction = { nav.navigate(QuickLessonRoute()) },
            )
            return@Column
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { nav.switchTab(ScheduleRoute(nearestDay), restore = false) }
                .card(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier
                        .size(width = 56.dp, height = 54.dp)
                        .background(c.accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(Fmt.dayLabel(nearestDay, now), style = Type.caption2.bold(), color = c.accent, maxLines = 1)
                    Text("${dayOfMonth(nearestDay)}", style = Type.title3, color = c.ink)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${Fmt.dayMonth(nearestDay)} ${Fmt.weekday(nearestDay)}", style = Type.subheadline.bold().serif(), color = c.ink)
                    Text(
                        "${dayLessons.size} ders • ${Fmt.hours(dayLessons.sumOf { it.duration })} • ${Fmt.money(dayLessons.sumOf { notebook.fee(it) })}",
                        style = Type.caption.semibold(), color = c.inkSoft,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkSoft.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
            Column(Modifier.padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                dayLessons.forEach { lesson ->
                    val student = notebook.studentOf(lesson)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Fmt.time(lesson.date), style = Type.caption.bold().serif(), color = c.ink, modifier = Modifier.width(42.dp))
                        Box(Modifier.size(7.dp).background(student?.let { studentColor(it.colorIndex) } ?: c.inkSoft, CircleShape))
                        Text(student?.name ?: "Öğrenci", style = Type.caption, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        val subject = student?.subject.orEmpty()
                        if (subject.isNotEmpty()) Text(subject, style = Type.caption2.semibold(), color = c.inkSoft, maxLines = 1)
                    }
                }
            }
        }
        SeeAllButton("Tüm programı gör", Icons.Rounded.CalendarMonth) {
            nav.switchTab(ScheduleRoute(nearestDay), restore = false)
        }
    }
}

// MARK: - Tüm işaretlenmemiş dersler

@Composable
fun UnmarkedLessonsScreen() {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val items = unmarkedLessons(notebook)
    val menu = rememberLessonMenuState()
    var confirmMarkAll by remember { mutableStateOf(false) }
    val c = AppTheme.colors

    xyz.adilemree.dersdefteri.ui.components.DetailScaffold(
        title = "İşaretlenmemiş Dersler",
        onBack = { nav.popBackStack() },
        actions = {
            if (items.size > 1) {
                androidx.compose.material3.TextButton(onClick = { confirmMarkAll = true }) {
                    Text("Tümü İşlendi", style = Type.subheadline.bold(), color = c.accent)
                }
            }
        },
        spacing = 10,
    ) {
        if (items.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.Verified, "Hepsi işaretlendi", message = "Saati geçmiş planlı ders kalmadı.", modifier = Modifier.padding(top = 40.dp))
            }
        } else {
            items(items, key = { it.id }) { lesson ->
                UnmarkedLessonRow(
                    lesson, notebook,
                    onDone = { app.edit { setStatus(lesson.id, LessonStatus.COMPLETED) } },
                    onCancel = { menu.cancelFor = lesson },
                    onOpen = { nav.navigate(LessonFormRoute(lessonId = lesson.id)) },
                    onMenu = { menu.menuFor = lesson },
                )
            }
        }
    }
    LessonMenus(menu, notebook, showEdit = true)
    if (confirmMarkAll) MarkAllCompletedDialog(items, notebook, onDismiss = { confirmMarkAll = false })
}
