package xyz.adilemree.dersdefteri.ui.students

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Note
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CurrencyLira
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Homework
import xyz.adilemree.dersdefteri.data.LessonTemplate
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.Payment
import xyz.adilemree.dersdefteri.data.PaymentLedger
import xyz.adilemree.dersdefteri.data.Student
import xyz.adilemree.dersdefteri.data.StudentSharing
import xyz.adilemree.dersdefteri.data.SummaryPeriod
import xyz.adilemree.dersdefteri.export.StudentReport
import xyz.adilemree.dersdefteri.ui.HomeworkFormRoute
import xyz.adilemree.dersdefteri.ui.LessonFormRoute
import xyz.adilemree.dersdefteri.ui.PaymentFormRoute
import xyz.adilemree.dersdefteri.ui.PaymentReminderRoute
import xyz.adilemree.dersdefteri.ui.PaywallRoute
import xyz.adilemree.dersdefteri.ui.StudentFormRoute
import xyz.adilemree.dersdefteri.ui.StudentSummaryRoute
import xyz.adilemree.dersdefteri.ui.TemplateFormRoute
import xyz.adilemree.dersdefteri.ui.common.LessonMenus
import xyz.adilemree.dersdefteri.ui.common.rememberLessonMenuState
import xyz.adilemree.dersdefteri.ui.components.ActionSheet
import xyz.adilemree.dersdefteri.ui.components.Chalkboard
import xyz.adilemree.dersdefteri.ui.components.Chip
import xyz.adilemree.dersdefteri.ui.components.ConfirmDialog
import xyz.adilemree.dersdefteri.ui.components.ContactActionButton
import xyz.adilemree.dersdefteri.ui.components.DetailScaffold
import xyz.adilemree.dersdefteri.ui.components.EmptyState
import xyz.adilemree.dersdefteri.ui.components.LessonRow
import xyz.adilemree.dersdefteri.ui.components.PaidProgressBar
import xyz.adilemree.dersdefteri.ui.components.PaymentRow
import xyz.adilemree.dersdefteri.ui.components.PrimaryButton
import xyz.adilemree.dersdefteri.ui.components.Segmented
import xyz.adilemree.dersdefteri.ui.components.SheetAction
import xyz.adilemree.dersdefteri.ui.components.StudentAvatar
import xyz.adilemree.dersdefteri.ui.components.TintedButton
import xyz.adilemree.dersdefteri.ui.components.card
import xyz.adilemree.dersdefteri.ui.components.strike
import xyz.adilemree.dersdefteri.ui.nav
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.WhatsAppGreen
import xyz.adilemree.dersdefteri.ui.theme.bold
import xyz.adilemree.dersdefteri.ui.theme.semibold
import xyz.adilemree.dersdefteri.ui.theme.serif
import xyz.adilemree.dersdefteri.ui.theme.studentColor
import xyz.adilemree.dersdefteri.util.Intents

private enum class DetailTab(val title: String) { LESSONS("Dersler"), PAYMENTS("Ödemeler"), HOMEWORK("Ödevler"), INFO("Bilgi") }

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun StudentDetailScreen(studentId: String) {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val student = notebook.student(studentId)
    var tab by rememberSaveable { mutableStateOf(DetailTab.LESSONS) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var archiveTarget by remember { mutableStateOf<Student?>(null) }
    var paymentMenu by remember { mutableStateOf<Payment?>(null) }
    var paymentDelete by remember { mutableStateOf<Payment?>(null) }
    var homeworkMenu by remember { mutableStateOf<Homework?>(null) }
    val lessonMenu = rememberLessonMenuState()
    val c = AppTheme.colors

    // Öğrenci başka yerden (ör. eşitlemede) silindiyse boş sayfa yerine not düşülür.
    if (student == null) {
        DetailScaffold(title = "Öğrenci", onBack = { nav.popBackStack() }) {
            item { EmptyState(Icons.Rounded.Person, "Öğrenci bulunamadı", modifier = Modifier.padding(top = 40.dp)) }
        }
        return
    }
    val stats = notebook.stats(student.id)

    fun unarchive() {
        val activeCount = notebook.students.count { !it.isArchived }
        if (app.pro.canAddStudent(activeCount)) app.edit { unarchive(student.id) } else nav.navigate(PaywallRoute)
    }

    DetailScaffold(
        title = student.name,
        onBack = { nav.popBackStack() },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Diğer", tint = c.accent) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = c.card) {
                    MenuItem("Ders Ekle", Icons.Rounded.EventAvailable) { menuOpen = false; nav.navigate(LessonFormRoute(studentId = student.id)) }
                    MenuItem("Ödeme Al", Icons.Rounded.CurrencyLira) { menuOpen = false; nav.navigate(PaymentFormRoute(studentId = student.id)) }
                    MenuItem("Toplu Ödeme Al", Icons.Rounded.Layers) { menuOpen = false; nav.navigate(PaymentFormRoute(studentId = student.id, startWithLessons = true)) }
                    MenuItem("Ödev Ver", Icons.AutoMirrored.Rounded.MenuBook) { menuOpen = false; nav.navigate(HomeworkFormRoute(studentId = student.id)) }
                    HorizontalDivider(color = c.line)
                    if (student.isArchived) {
                        MenuItem("Aktife Al", Icons.Rounded.Unarchive) { menuOpen = false; unarchive() }
                    } else {
                        MenuItem("Arşivle", Icons.Rounded.Archive) { menuOpen = false; archiveTarget = student }
                    }
                    MenuItem("Düzenle", Icons.Rounded.Edit) { menuOpen = false; nav.navigate(StudentFormRoute(student.id)) }
                    MenuItem("Öğrenciyi Sil", Icons.Rounded.Delete, tint = c.red) { menuOpen = false; confirmDelete = true }
                }
            }
        },
    ) {
        // İstatistikler ve iletişim başlık kartında; ders listesi hemen altta başlar.
        item { Header(student, notebook) }
        item { BalanceBanner(student, notebook) }
        item {
            Segmented(DetailTab.entries.map { it to it.title }, tab, { tab = it })
        }

        when (tab) {
            DetailTab.LESSONS -> {
                // Hangi dersin ödendiği bir kez hesaplanır; tutarların yanında görünür.
                val ledger = PaymentLedger(notebook, student.id)
                val lessons = notebook.lessonsOf(student.id).sortedByDescending { it.date }
                if (lessons.isEmpty()) {
                    item {
                        EmptyState(Icons.Rounded.CalendarMonth, "Ders kaydı yok", actionTitle = "Ders Ekle",
                            onAction = { nav.navigate(LessonFormRoute(studentId = student.id)) })
                    }
                }
                items(lessons, key = { it.id }) { lesson ->
                    LessonRow(
                        lesson, notebook,
                        showDate = true,
                        showStudent = false,
                        payState = ledger.state(lesson),
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).combinedClickable(
                            onClick = { nav.navigate(LessonFormRoute(lessonId = lesson.id)) },
                            onLongClick = { lessonMenu.menuFor = lesson },
                        ),
                    )
                }
            }
            DetailTab.PAYMENTS -> {
                val payments = notebook.paymentsOf(student.id).sortedByDescending { it.date }
                if (payments.isEmpty()) {
                    item {
                        EmptyState(Icons.Rounded.CurrencyLira, "Ödeme kaydı yok", actionTitle = "Ödeme Al",
                            onAction = { nav.navigate(PaymentFormRoute(studentId = student.id)) })
                    }
                }
                items(payments, key = { it.id }) { payment ->
                    PaymentRow(
                        payment, notebook, showStudent = false,
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).combinedClickable(
                            onClick = { nav.navigate(PaymentFormRoute(paymentId = payment.id)) },
                            onLongClick = { paymentMenu = payment },
                        ),
                    )
                }
            }
            DetailTab.HOMEWORK -> {
                val homeworks = notebook.homeworksOf(student.id).sortedByDescending { it.dueDate }
                if (homeworks.isEmpty()) {
                    item {
                        EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Ödev yok", actionTitle = "Ödev Ver",
                            onAction = { nav.navigate(HomeworkFormRoute(studentId = student.id)) })
                    }
                }
                items(homeworks, key = { it.id }) { hw ->
                    val late = hw.isLate()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .combinedClickable(
                                onClick = { nav.navigate(HomeworkFormRoute(homeworkId = hw.id)) },
                                onLongClick = { homeworkMenu = hw },
                            )
                            .card(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            if (hw.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            if (hw.isDone) "Tamamlandı" else "Bekliyor",
                            tint = if (hw.isDone) c.green else c.inkSoft,
                            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).clickable { app.edit { toggleHomework(hw.id) } },
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(hw.title, style = Type.subheadline.semibold(), color = c.ink, textDecoration = strike(hw.isDone))
                            Text(
                                if (late) "Gecikti! Son: ${Fmt.dayMonthShort(hw.dueDate)}" else "Son: ${Fmt.dayMonthShort(hw.dueDate)}",
                                style = Type.caption,
                                color = if (late) c.red else c.inkSoft,
                            )
                        }
                    }
                }
            }
            DetailTab.INFO -> {
                item { InfoRow(Icons.Rounded.Phone, "Öğrenci", student.phone) }
                item { InfoRow(Icons.Rounded.Person, "Veli", student.parentName) }
                item { InfoRow(Icons.Rounded.Phone, "Veli Telefonu", student.parentPhone) }
                item { InfoRow(Icons.Rounded.CalendarMonth, "Başlangıç", Fmt.long(student.startDate)) }
                if (student.notes.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().card(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.Note, null, tint = c.inkSoft, modifier = Modifier.size(16.dp))
                                Text("Notlar", style = Type.caption.bold(), color = c.inkSoft)
                            }
                            Text(student.notes, style = Type.subheadline, color = c.ink)
                        }
                    }
                }
            }
        }
    }

    LessonMenus(lessonMenu, notebook)

    if (confirmDelete) {
        ConfirmDialog(
            title = "${student.name} ve tüm ders/ödeme/ödev kayıtları silinecek. Emin misin?",
            confirmTitle = "Sil",
            onConfirm = {
                app.edit { deleteStudent(student.id) }
                nav.popBackStack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    archiveTarget?.let {
        StudentArchiveDialog(it, notebook, onDismiss = { archiveTarget = null }, onArchived = { nav.popBackStack() })
    }
    paymentMenu?.let { payment ->
        ActionSheet(
            title = "${Fmt.money(payment.amount)} • ${Fmt.dayMonthShort(payment.date)}",
            actions = listOf(
                SheetAction("Düzenle", Icons.Rounded.Edit) { nav.navigate(PaymentFormRoute(paymentId = payment.id)) },
                SheetAction("Sil", Icons.Rounded.Delete, destructive = true) { paymentDelete = payment },
            ),
            onDismiss = { paymentMenu = null },
        )
    }
    paymentDelete?.let { PaymentDeleteDialog(it, notebook, onDismiss = { paymentDelete = null }) }
    homeworkMenu?.let { hw ->
        ActionSheet(
            title = hw.title,
            actions = listOf(SheetAction("Sil", Icons.Rounded.Delete, destructive = true) { app.edit { homeworks.remove(hw.id) } }),
            onDismiss = { homeworkMenu = null },
        )
    }
}

@Composable
private fun MenuItem(title: String, icon: ImageVector, tint: Color = AppTheme.colors.ink, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, color = tint) },
        leadingIcon = { Icon(icon, null, tint = tint) },
        onClick = onClick,
    )
}

/// Ödeme uzun basıp "Sil" ile onaysız gitmesin; tutar bakiyeyi etkilediği için ne silindiği söylenir.
@Composable
fun PaymentDeleteDialog(payment: Payment, notebook: Notebook, onDismiss: () -> Unit, onDeleted: () -> Unit = {}) {
    val app = LocalApp.current
    ConfirmDialog(
        title = "Ödeme silinsin mi?",
        message = "${notebook.student(payment.studentId)?.name ?: "Öğrenci"} • ${Fmt.money(payment.amount)} • ${Fmt.dayMonthShort(payment.date)}",
        confirmTitle = "Ödemeyi Sil",
        onConfirm = {
            app.edit { deletePayment(payment.id) }
            onDeleted()
        },
        onDismiss = onDismiss,
    )
}

// MARK: - Başlık

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(student: Student, notebook: Notebook) {
    val c = AppTheme.colors
    val nav = nav()
    val context = LocalContext.current
    val stats = notebook.stats(student.id)
    val color = studentColor(student.colorIndex)
    val templates = notebook.templatesOf(student.id).sortedWith(
        compareBy<LessonTemplate>({ LessonTemplate.weekdayOrder.indexOf(it.weekday) }, { it.hour }, { it.minute }),
    )

    Column(Modifier.fillMaxWidth().padding(top = 4.dp).card(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StudentAvatar(student, 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(student.name, style = Type.title3, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(student.subject.ifEmpty { "Ders yok" }, color, filled = true)
                    if (student.grade.isNotEmpty()) Chip(student.grade, c.inkSoft)
                }
                Text(
                    "Saatlik ücret: ${Fmt.money(student.hourlyRate)} • Başlangıç: ${Fmt.dayMonthShort(student.startDate)}",
                    style = Type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Sığmayan çipler alt satıra geçer.
        if (templates.isNotEmpty() || !student.isArchived) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { template ->
                    WeeklyChip(template, student) { nav.navigate(TemplateFormRoute(templateId = template.id)) }
                }
                if (!student.isArchived) {
                    DashedAddChip(if (templates.isEmpty()) "Haftalık ders ekle" else "Ekle") {
                        nav.navigate(TemplateFormRoute(studentId = student.id))
                    }
                }
            }
        }

        HorizontalDivider(color = c.line)

        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniStat("${stats.completedCount}", "İşlenen ders", Modifier.weight(1f))
            VerticalDivider(Modifier.height(30.dp), color = c.line)
            MiniStat(Fmt.hours(stats.totalMinutes), "Toplam süre", Modifier.weight(1f))
            VerticalDivider(Modifier.height(30.dp), color = c.line)
            MiniStat(Fmt.money(stats.totalEarned), "Ders tutarı", Modifier.weight(1f))
        }

        val phone = StudentSharing.bestContactPhone(student)
        val hasPhone = StudentSharing.cleanPhone(phone).isNotEmpty()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ContactActionButton(Icons.Rounded.Call, "Ara", c.green, Modifier.weight(1f), enabled = hasPhone) {
                Intents.dial(context, StudentSharing.dialUri(phone))
            }
            ContactActionButton(Icons.Rounded.Sms, "SMS", c.blue, Modifier.weight(1f), enabled = hasPhone) {
                Intents.sms(context, StudentSharing.smsUri(phone))
            }
            ContactActionButton(Icons.AutoMirrored.Rounded.Chat, "WhatsApp", c.accent, Modifier.weight(1f), enabled = hasPhone) {
                val text = "Merhaba, ${student.name} için ders durumunu paylaşmak istiyorum."
                Intents.open(context, StudentSharing.whatsappUri(phone, text))
            }
            ContactActionButton(Icons.Rounded.Share, "Özet", c.amber, Modifier.weight(1f)) {
                nav.navigate(StudentSummaryRoute(student.id))
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = Type.headline.bold().serif(), color = c.ink, maxLines = 1)
        Text(label, style = Type.caption2, color = c.inkSoft, maxLines = 1)
    }
}

@Composable
private fun BalanceBanner(student: Student, notebook: Notebook) {
    val c = AppTheme.colors
    val nav = nav()
    val stats = notebook.stats(student.id)
    if (stats.balance > 0.5) {
        // Açık zemin + ödenen oranı çubuğu hem sakin hem bilgilendirici.
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.red.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .border(1.dp, c.red.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Bekleyen ödeme", style = Type.caption.semibold(), color = c.red)
                    Text(Fmt.money(stats.balance), style = Type.title3, color = c.ink)
                }
                Text(
                    "${Fmt.money(stats.totalPaid)} / ${Fmt.money(stats.totalEarned)} ödendi",
                    style = Type.caption, color = c.inkSoft, maxLines = 1,
                )
            }
            PaidProgressBar(stats.totalPaid, stats.totalEarned)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TintedButton("Hatırlat", Modifier.weight(1f), icon = Icons.Rounded.Notifications, tint = c.red) {
                    nav.navigate(PaymentReminderRoute(student.id))
                }
                TintedButton("Ödeme Al", Modifier.weight(1f), icon = Icons.Rounded.Add, tint = c.board, prominent = true) {
                    nav.navigate(PaymentFormRoute(studentId = student.id))
                }
            }
        }
    } else {
        val (icon, tint, text) = when {
            stats.balance < -0.5 -> Triple(Icons.Rounded.ArrowCircleDown, c.blue, "Avans var: ${Fmt.money(-stats.balance)}")
            stats.totalEarned <= 0.5 && stats.totalPaid <= 0.5 -> Triple(
                Icons.AutoMirrored.Rounded.EventNote, c.amber,
                if (stats.plannedCount > 0) "${stats.plannedCount} planlı ders var, henüz ödeme beklenmiyor"
                else "Henüz işlenen ders veya ödeme kaydı yok",
            )
            else -> Triple(Icons.Rounded.Verified, c.green, "Tüm ödemeler alındı")
        }
        Row(Modifier.fillMaxWidth().card(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Text(text, style = Type.subheadline.semibold(), color = c.ink)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().card(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
        Text(label, style = Type.subheadline, color = c.inkSoft, modifier = Modifier.weight(1f))
        Text(value.ifEmpty { "—" }, style = Type.subheadline.semibold(), color = c.ink)
    }
}

// MARK: - Öğrenci özeti

@Composable
fun StudentSummaryScreen(studentId: String) {
    val app = LocalApp.current
    val nav = nav()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val student = notebook.student(studentId) ?: return
    val isPro by app.pro.isPro.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(SummaryPeriod.WEEK) }
    var copied by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    val c = AppTheme.colors
    val text = StudentSharing.summary(notebook, student, period)
    val phone = StudentSharing.bestContactPhone(student)

    DetailScaffold(title = "Paylaşılabilir Özet", onBack = { nav.popBackStack() }, spacing = 16) {
        item { Segmented(SummaryPeriod.entries.map { it to it.title }, period, { period = it; copied = false }) }

        // Veliye gidecek mesaj, gideceği biçimde: bir mesaj balonu.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Veliye gidecek mesaj", style = Type.caption.semibold(), color = c.inkSoft)
                Text(
                    text,
                    style = Type.subheadline,
                    color = c.ink,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(start = 24.dp)
                        .background(c.green.copy(alpha = 0.14f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }

        // Ana eylem veliye WhatsApp'tan göndermek; kopyala ve paylaş ikincil.
        item {
            PrimaryButton(
                "WhatsApp'ta Gönder",
                icon = Icons.AutoMirrored.Rounded.Chat,
                tint = WhatsAppGreen,
                enabled = StudentSharing.cleanPhone(phone).isNotEmpty(),
            ) { Intents.open(context, StudentSharing.whatsappUri(phone, text)) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TintedButton(if (copied) "Kopyalandı" else "Kopyala", Modifier.weight(1f), icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy) {
                    Intents.copy(context, text); copied = true
                }
                TintedButton("Paylaş", Modifier.weight(1f), icon = Icons.Rounded.Share) { Intents.shareText(context, text) }
            }
        }
        item { HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 4.dp)) }

        // Düz metin özet herkese açık; biçimli PDF Pro'ya ait.
        item {
            if (isPro) {
                TintedButton(
                    if (preparing) "Hazırlanıyor…" else "PDF Raporu Paylaş",
                    Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Description,
                    enabled = !preparing,
                ) {
                    preparing = true
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            StudentReport.makePdf(context, notebook, student, period, app.settings.teacherName.value)
                        }
                        preparing = false
                        if (file != null) Intents.shareFiles(context, listOf(file), "application/pdf")
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { nav.navigate(PaywallRoute) }.card(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Description, null, tint = c.accent)
                    Column(Modifier.weight(1f)) {
                        Text("PDF Veli Raporu", style = Type.subheadline.semibold(), color = c.ink)
                        Text("Biçimli, yazdırılabilir rapor olarak paylaş", style = Type.caption, color = c.inkSoft)
                    }
                    Icon(Icons.Rounded.Lock, null, tint = c.amber, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// MARK: - Ödeme hatırlatma

@Composable
fun PaymentReminderScreen(studentId: String) {
    val app = LocalApp.current
    val nav = nav()
    val context = LocalContext.current
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val student = notebook.student(studentId) ?: return
    var copied by remember { mutableStateOf(false) }
    val c = AppTheme.colors
    val text = StudentSharing.paymentReminder(notebook, student)
    val phone = StudentSharing.bestContactPhone(student)

    DetailScaffold(title = "Ödeme Hatırlat", onBack = { nav.popBackStack() }, spacing = 16) {
        item {
            Chalkboard {
                Text(student.name, style = Type.title3, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text("Bekleyen ödeme: ${StudentSharing.balanceText(notebook, student)}", style = Type.subheadline, color = Color.White.copy(alpha = 0.82f))
            }
        }
        item { Text(text, style = Type.subheadline, color = c.ink, modifier = Modifier.fillMaxWidth().card(14.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TintedButton(if (copied) "Kopyalandı" else "Kopyala", Modifier.weight(1f), icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy) {
                    Intents.copy(context, text); copied = true
                }
                TintedButton(
                    "WhatsApp", Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.Chat, tint = c.green, prominent = true,
                    enabled = StudentSharing.cleanPhone(phone).isNotEmpty(),
                ) { Intents.open(context, StudentSharing.whatsappUri(phone, text)) }
            }
        }
    }
}
