package xyz.adilemree.dersdefteri.ui.payments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CurrencyLira
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Lesson
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.Payment
import xyz.adilemree.dersdefteri.data.PaymentLedger
import xyz.adilemree.dersdefteri.data.PaymentMethod
import xyz.adilemree.dersdefteri.data.Student
import xyz.adilemree.dersdefteri.data.addDays
import xyz.adilemree.dersdefteri.data.addMonths
import xyz.adilemree.dersdefteri.data.daysBetween
import xyz.adilemree.dersdefteri.data.startOfDay
import xyz.adilemree.dersdefteri.data.startOfMonth
import xyz.adilemree.dersdefteri.ui.PaymentFormRoute
import xyz.adilemree.dersdefteri.ui.PaymentReminderRoute
import xyz.adilemree.dersdefteri.ui.components.ActionSheet
import xyz.adilemree.dersdefteri.ui.components.Chip
import xyz.adilemree.dersdefteri.ui.components.ConfirmDialog
import xyz.adilemree.dersdefteri.ui.components.EmptyState
import xyz.adilemree.dersdefteri.ui.components.FormAmountRow
import xyz.adilemree.dersdefteri.ui.components.FormButtonRow
import xyz.adilemree.dersdefteri.ui.components.FormChip
import xyz.adilemree.dersdefteri.ui.components.FormDateRow
import xyz.adilemree.dersdefteri.ui.components.FormPickerRow
import xyz.adilemree.dersdefteri.ui.components.FormRow
import xyz.adilemree.dersdefteri.ui.components.FormScaffold
import xyz.adilemree.dersdefteri.ui.components.FormSection
import xyz.adilemree.dersdefteri.ui.components.FormSegmentedRow
import xyz.adilemree.dersdefteri.ui.components.FormTextRow
import xyz.adilemree.dersdefteri.ui.components.FormValueRow
import xyz.adilemree.dersdefteri.ui.components.PaidProgressBar
import xyz.adilemree.dersdefteri.ui.components.PaymentRow
import xyz.adilemree.dersdefteri.ui.components.PillButton
import xyz.adilemree.dersdefteri.ui.components.PrimaryButton
import xyz.adilemree.dersdefteri.ui.components.RowDivider
import xyz.adilemree.dersdefteri.ui.components.SectionHeader
import xyz.adilemree.dersdefteri.ui.components.Segmented
import xyz.adilemree.dersdefteri.ui.components.SheetAction
import xyz.adilemree.dersdefteri.ui.components.StudentAvatar
import xyz.adilemree.dersdefteri.ui.components.TabScaffold
import xyz.adilemree.dersdefteri.ui.components.amountText
import xyz.adilemree.dersdefteri.ui.components.card
import xyz.adilemree.dersdefteri.ui.components.parseAmount
import xyz.adilemree.dersdefteri.ui.nav
import xyz.adilemree.dersdefteri.ui.students.PaymentDeleteDialog
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.bold
import xyz.adilemree.dersdefteri.ui.theme.semibold
import xyz.adilemree.dersdefteri.ui.theme.serif
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaymentsScreen() {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    var menuFor by remember { mutableStateOf<Payment?>(null) }
    var deleteFor by remember { mutableStateOf<Payment?>(null) }
    val c = AppTheme.colors
    val payments = notebook.payments.sortedByDescending { it.date }

    // Arşivdeki öğrencinin borcu da burada kalır; üstteki "Bekleyen" toplamı onu sayıyor.
    val balances = notebook.students.filter { abs(notebook.balance(it.id)) > 0.5 }.sortedByDescending { notebook.balance(it.id) }

    TabScaffold(
        title = "Ödemeler",
        actions = { IconButton(onClick = { nav.navigate(PaymentFormRoute()) }) { Icon(Icons.Rounded.Add, "Ödeme Al", tint = c.accent) } },
    ) {
        item { CollectionCard(notebook, Modifier.padding(top = 4.dp)) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Bakiyeler", Icons.Rounded.Balance)
                if (balances.isEmpty()) {
                    EmptyState(Icons.Rounded.Verified, "Tüm hesaplar kapalı 🎉", message = "Hiçbir öğrencinin borcu veya avansı yok.")
                } else {
                    balances.forEach { BalanceRow(it, notebook) }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Son Ödemeler", Icons.Rounded.History)
                if (payments.isEmpty()) {
                    EmptyState(Icons.Rounded.CurrencyLira, "Henüz ödeme kaydı yok", actionTitle = "Ödeme Ekle",
                        onAction = { nav.navigate(PaymentFormRoute()) })
                } else {
                    // Ay ay gruplanır; her ayın başında o ayın toplamı yazar.
                    val groups = payments.take(25).groupBy { startOfMonth(it.date) }.toSortedMap(compareByDescending { it })
                    groups.forEach { (month, list) ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Text(Fmt.monthYear(month), style = Type.caption.bold(), color = c.inkSoft, modifier = Modifier.weight(1f))
                            Text(Fmt.money(monthTotal(notebook, month)), style = Type.caption.bold(), color = c.green)
                        }
                        list.sortedByDescending { it.date }.forEach { payment ->
                            PaymentRow(
                                payment, notebook,
                                modifier = Modifier.clip(RoundedCornerShape(18.dp)).combinedClickable(
                                    onClick = { nav.navigate(PaymentFormRoute(paymentId = payment.id)) },
                                    onLongClick = { menuFor = payment },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { payment ->
        ActionSheet(
            title = "${notebook.student(payment.studentId)?.name ?: "Öğrenci"} • ${Fmt.money(payment.amount)}",
            actions = listOf(
                SheetAction("Düzenle", Icons.Rounded.Edit) { nav.navigate(PaymentFormRoute(paymentId = payment.id)) },
                SheetAction("Sil", Icons.Rounded.Delete, destructive = true) { deleteFor = payment },
            ),
            onDismiss = { menuFor = null },
        )
    }
    deleteFor?.let { PaymentDeleteDialog(it, notebook, onDismiss = { deleteFor = null }) }
}

private fun monthTotal(notebook: Notebook, month: Long): Double {
    val next = addMonths(month, 1)
    return notebook.payments.filter { it.date in month until next }.sumOf { it.amount }
}

// MARK: - Tahsilat kartı

/// Üstte bu ayın tahsilatı ve geçen ayla kıyası, altta bekleyen / avans /
/// bu ay işlenen. Grafik bilerek yok.
@Composable
private fun CollectionCard(notebook: Notebook, modifier: Modifier) {
    val c = AppTheme.colors
    val now = System.currentTimeMillis()
    val monthStart = startOfMonth(now)
    val monthCollected = notebook.payments.filter { it.date >= monthStart }.sumOf { it.amount }
    val lastStart = addMonths(monthStart, -1)
    val lastTotal = monthTotal(notebook, lastStart)
    // Ay bitmeden tam geçen ayla kıyaslamak yanıltıcı olurdu; geçen ayın aynı gününe kadarki tahsilatla kıyaslanır.
    val dayOffset = daysBetween(monthStart, now)
    val lastToDate = minOf(addDays(lastStart, dayOffset + 1), monthStart)
    val previous = notebook.payments.filter { it.date >= lastStart && it.date < lastToDate }.sumOf { it.amount }
    val delta = if (previous > 0.5) (monthCollected - previous) / previous else null

    val pendingTotal = notebook.students.sumOf { maxOf(notebook.balance(it.id), 0.0) }
    val pendingCount = notebook.students.count { notebook.balance(it.id) > 0.5 }
    val advanceTotal = notebook.students.sumOf { maxOf(-notebook.balance(it.id), 0.0) }
    val advanceCount = notebook.students.count { notebook.balance(it.id) < -0.5 }
    val monthEnd = addMonths(monthStart, 1)
    val monthCompleted = notebook.lessons.filter { it.isCompleted && it.date in monthStart until monthEnd && notebook.studentOf(it) != null }

    Column(modifier.fillMaxWidth().card(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${Fmt.monthName(now)} tahsilatı", style = Type.caption.semibold(), color = c.inkSoft)
                Text(Fmt.money(monthCollected), style = Type.largeTitle, color = c.ink, maxLines = 1)
                if (lastTotal > 0.5) {
                    Text("${Fmt.monthName(lastStart)} toplamı ${Fmt.money(lastTotal)}", style = Type.caption, color = c.inkSoft)
                }
            }
            if (delta != null) DeltaChip(delta)
        }
        HorizontalDivider(color = c.line)
        Row(verticalAlignment = Alignment.Top) {
            Kpi("Bekleyen", Fmt.money(pendingTotal), if (pendingCount > 0) "$pendingCount öğrenci" else "herkes ödedi",
                Icons.Rounded.HourglassEmpty, if (pendingTotal > 0.5) c.red else c.inkSoft, if (pendingTotal > 0.5) c.red else c.ink, Modifier.weight(1f))
            Kpi("Avans", Fmt.money(advanceTotal), if (advanceCount > 0) "$advanceCount öğrenci" else "yok",
                Icons.Rounded.ArrowCircleDown, c.blue, c.ink, Modifier.weight(1f))
            Kpi("Bu ay işlenen", Fmt.money(monthCompleted.sumOf { notebook.fee(it) }), "${monthCompleted.size} ders",
                Icons.Rounded.CheckCircle, c.green, c.ink, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Kpi(title: String, value: String, detail: String, icon: ImageVector, tint: Color, valueTint: Color, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
            Text(title, style = Type.caption2.semibold(), color = c.inkSoft, maxLines = 1)
        }
        Text(value, style = Type.subheadline.bold().serif(), color = valueTint, maxLines = 1)
        Text(detail, style = Type.caption2, color = c.inkSoft, maxLines = 1)
    }
}

/// Geçen ayın aynı gününe göre değişim
@Composable
private fun DeltaChip(percent: Double) {
    val c = AppTheme.colors
    val up = percent >= 0
    val tint = if (up) c.green else c.red
    Row(
        Modifier.background(tint.copy(alpha = 0.12f), CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown, null, tint = tint, modifier = Modifier.size(13.dp))
        Text("%${(abs(percent) * 100).roundToInt()}", style = Type.caption.bold(), color = tint)
    }
}

/// Üstte kim ve ne kadar, altta ödenen oranı ve eylemler.
@Composable
private fun BalanceRow(student: Student, notebook: Notebook) {
    val c = AppTheme.colors
    val nav = nav()
    val stats = notebook.stats(student.id)
    val owes = stats.balance > 0
    Column(Modifier.fillMaxWidth().card(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StudentAvatar(student, 42.dp, modifier = Modifier.alpha(if (student.isArchived) 0.55f else 1f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(student.name, style = Type.subheadline.semibold(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (student.isArchived) Chip("Arşivde", c.inkSoft)
                }
                Text(
                    if (owes) "${Fmt.money(stats.totalPaid)} / ${Fmt.money(stats.totalEarned)} ödendi"
                    else "Derslerden ${Fmt.money(-stats.balance)} fazla ödendi",
                    style = Type.caption, color = c.inkSoft, maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Fmt.money(abs(stats.balance)), style = Type.headline.bold().serif(), color = if (owes) c.red else c.blue)
                Text(if (owes) "kalan" else "avans", style = Type.caption2.semibold(), color = c.inkSoft)
            }
        }
        if (!owes) {
            PaidProgressBar(1.0, 1.0, tint = c.blue)
        } else {
            PaidProgressBar(stats.totalPaid, stats.totalEarned)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                PillButton("Hatırlat", Icons.Rounded.Notifications, tint = c.red) { nav.navigate(PaymentReminderRoute(student.id)) }
                PillButton("Ödeme Al", Icons.Rounded.Add, tint = c.green) { nav.navigate(PaymentFormRoute(studentId = student.id)) }
            }
        }
    }
}

// MARK: - Ödeme formu

@Composable
fun PaymentFormScreen(defaultStudentId: String?, paymentId: String?, startWithLessons: Boolean) {
    val app = LocalApp.current
    val nav = nav()
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val payment = paymentId?.let { notebook.paymentById[it] }
    val c = AppTheme.colors

    var studentId by rememberSaveable { mutableStateOf(payment?.studentId ?: defaultStudentId) }
    var amount by rememberSaveable { mutableStateOf(amountText(payment?.amount ?: 0.0)) }
    var date by rememberSaveable { mutableLongStateOf(payment?.date ?: System.currentTimeMillis()) }
    var method by rememberSaveable { mutableStateOf(payment?.paymentMethod ?: PaymentMethod.TRANSFER) }
    var note by rememberSaveable { mutableStateOf(payment?.note ?: "") }
    /// Toplu ödemenin kapsadığı dersler
    var lessonIds by rememberSaveable {
        mutableStateOf(payment?.let { p -> notebook.coveredLessons(p.id).filter { !it.isCancelled }.map { it.id }.toSet() } ?: emptySet())
    }
    var showLessonPicker by rememberSaveable { mutableStateOf(startWithLessons && payment == null && defaultStudentId != null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val selectedStudent = notebook.student(studentId)
    /// Aktif öğrenciler; arşivdeki bir öğrenciden ödeme alınıyorsa o da.
    val pickerStudents = notebook.students.filter { !it.isArchived || it.id == studentId }
    val selectedLessons = selectedStudent?.let { s -> notebook.lessonsOf(s.id).filter { it.id in lessonIds }.sortedBy { it.date } }.orEmpty()
    val lessonsTotal = selectedLessons.sumOf { notebook.fee(it) }
    val amountValue = parseAmount(amount) ?: 0.0

    /// Düzenlenen ödeme bakiyede zaten sayılı; "bakiyenin tamamı" onu hariç tutar.
    fun balanceExcludingThis(s: Student): Double {
        val balance = notebook.balance(s.id)
        return if (payment != null && payment.studentId == s.id) balance + payment.amount else balance
    }

    val title = if (payment != null) {
        if (lessonIds.isEmpty()) "Ödemeyi Düzenle" else "Toplu Ödeme"
    } else if (lessonIds.isEmpty()) "Ödeme Al" else "Toplu Ödeme Al"

    FormScaffold(
        title = title,
        onClose = { nav.popBackStack() },
        actionEnabled = selectedStudent != null && amountValue > 0,
        onAction = {
            val sid = studentId ?: return@FormScaffold
            val ids = lessonIds; val a = amountValue; val d = date; val m = method; val n = note.trim()
            app.edit { savePayment(payment?.id, sid, a, d, m, n, ids) }
            nav.popBackStack()
        },
    ) {
        FormSection(header = "Öğrenci") {
            FormPickerRow(
                "Öğrenci",
                listOf<Student?>(null) + pickerStudents,
                selectedStudent,
                { it?.name ?: "Seçiniz" },
            ) { s ->
                if (s?.id != studentId) lessonIds = emptySet()
                studentId = s?.id
            }
            if (selectedStudent != null) {
                RowDivider()
                val stats = notebook.stats(selectedStudent.id)
                val (text, tint) = when {
                    stats.balance > 0.5 -> "${Fmt.money(stats.balance)} borç" to c.red
                    stats.balance < -0.5 -> "${Fmt.money(-stats.balance)} avans" to c.blue
                    stats.totalEarned <= 0.5 && stats.totalPaid <= 0.5 -> "Borç yok" to c.inkSoft
                    else -> "Ödendi" to c.green
                }
                FormValueRow("Güncel bakiye", text, valueColor = tint, bold = true)
            }
        }

        if (selectedStudent != null) {
            var footer = if (lessonIds.isEmpty()) {
                "İsteğe bağlı. Ödemenin kapsadığı dersleri seçersen tutar onların toplamı olur; dersler ayrı ayrı kalır ve \"toplu ödendi\" olarak görünür."
            } else "Dersler ayrı ayrı kalır; her birinde bu ödemeyle ödendiği yazar."
            if (payment != null) {
                val cancelled = notebook.coveredLessons(payment.id).count { it.isCancelled }
                if (cancelled > 0) footer += " Bu ödemenin $cancelled dersi iptal edildi; tutarı avans olarak duruyor. Kaydedersen iptal dersler ödemeden çıkar."
            }
            FormSection(header = "Toplu ödeme", footer = footer) {
                FormRow(onClick = { showLessonPicker = true }) {
                    Icon(Icons.Rounded.Layers, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Text(
                        if (lessonIds.isEmpty()) "Derslerden seç" else "${lessonIds.size} ders seçildi",
                        style = Type.body, color = c.accent, modifier = Modifier.weight(1f),
                    )
                    if (lessonIds.isNotEmpty()) Text(Fmt.money(lessonsTotal), style = Type.body, color = c.inkSoft)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkSoft.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
                selectedLessons.take(6).forEach { lesson ->
                    RowDivider()
                    FormRow {
                        Text("${Fmt.dayMonthShort(lesson.date)} ${Fmt.weekdayShort(lesson.date)}", style = Type.subheadline, color = c.ink)
                        Text(if (lesson.isPlanned) "Planlı" else "İşlendi", style = Type.caption, color = if (lesson.isPlanned) c.blue else c.inkSoft, modifier = Modifier.weight(1f))
                        Text(Fmt.money(notebook.fee(lesson)), style = Type.subheadline, color = c.inkSoft)
                    }
                }
                if (selectedLessons.size > 6) {
                    RowDivider()
                    FormRow { Text("+${selectedLessons.size - 6} ders daha", style = Type.caption, color = c.inkSoft) }
                }
            }
        }

        // Tutar elle değiştirildiyse farkın ne olacağı
        val diff = amountValue - lessonsTotal
        val differenceText = if (lessonIds.isEmpty() || abs(diff) <= 0.5) null
        else if (diff > 0) "Tutar derslerin toplamından ${Fmt.money(diff)} fazla; fark avans olarak kalır."
        else "Tutar derslerin toplamından ${Fmt.money(-diff)} eksik; fark borç olarak kalır."

        FormSection(header = "Ödeme", footer = differenceText) {
            FormAmountRow("Tutar", amount, { amount = it })
            if (lessonIds.isEmpty() && selectedStudent != null && balanceExcludingThis(selectedStudent) > 0.5) {
                val full = balanceExcludingThis(selectedStudent)
                RowDivider()
                FormButtonRow("Bakiyenin tamamı: ${Fmt.money(full)}") { amount = amountText(full) }
            }
            RowDivider()
            FormDateRow("Tarih", date) { date = it }
            RowDivider()
            FormSegmentedRow { Segmented(PaymentMethod.entries.map { it to it.title }, method, { method = it }) }
        }

        FormSection(header = "Not") {
            FormTextRow(note, { note = it }, "Not (ör. Ekim dersleri)")
        }

        if (payment != null) {
            FormSection {
                FormButtonRow("Ödemeyi Sil", color = c.red, center = true) { confirmDelete = true }
            }
        }
    }

    if (showLessonPicker && selectedStudent != null) {
        LessonSelectionDialog(
            student = selectedStudent,
            notebook = notebook,
            excludingPaymentId = payment?.id,
            initial = lessonIds,
            onDone = { ids ->
                lessonIds = ids
                // Tutar seçilen derslerin toplamıdır; sonra elle değiştirilebilir.
                if (ids.isNotEmpty()) {
                    val total = notebook.lessonsOf(selectedStudent.id).filter { it.id in ids }.sumOf { notebook.fee(it) }
                    amount = amountText(Math.round(total * 100) / 100.0)
                }
                showLessonPicker = false
            },
            onDismiss = { showLessonPicker = false },
        )
    }
    if (confirmDelete && payment != null) {
        ConfirmDialog(
            title = "Ödeme silinsin mi?",
            message = if (lessonIds.isEmpty()) "${Fmt.money(amountValue)} tutarındaki ödeme silinir ve öğrencinin bakiyesi buna göre değişir."
            else "${Fmt.money(amountValue)} tutarındaki toplu ödeme silinir; kapsadığı ${lessonIds.size} ders yeniden ödenmemiş görünür.",
            confirmTitle = "Ödemeyi Sil",
            onConfirm = {
                app.edit { deletePayment(payment.id) }
                nav.popBackStack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

// MARK: - Ders seçimi (toplu ödeme)

/// Toplu ödemenin kapsayacağı dersler. Ödenmemiş işlenmiş dersler ve (peşin
/// ödeme için) planlı dersler seçilebilir.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonSelectionDialog(
    student: Student,
    notebook: Notebook,
    excludingPaymentId: String?,
    initial: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    var selection by remember { mutableStateOf(initial) }
    val candidates = remember(notebook, student.id, excludingPaymentId) { PaymentLedger(notebook, student.id, excludingPaymentId).candidates }
    val months = candidates.groupBy { startOfMonth(it.date) }.toSortedMap()
    val total = candidates.filter { it.id in selection }.sumOf { notebook.fee(it) }
    val now = System.currentTimeMillis()
    val thisMonth = startOfMonth(now)
    val lastMonth = addMonths(thisMonth, -1)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            containerColor = c.paper,
            topBar = {
                TopAppBar(
                    title = { Text("Dersleri Seç", style = Type.headline.serif(), color = c.ink) },
                    actions = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = c.ink) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = c.paper),
                )
            },
            bottomBar = {
                if (candidates.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().background(c.card).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${selection.size} ders seçildi", style = Type.caption.semibold(), color = c.inkSoft)
                            Text(Fmt.money(total), style = Type.title3, color = c.ink)
                        }
                        PrimaryButton("Tamam", Modifier.fillMaxWidth(0.4f)) { onDone(selection) }
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (candidates.isEmpty()) {
                    item {
                        Text(
                            "Ödenmemiş ders yok. İşlenen dersler de planlı dersler de ödenmiş görünüyor.",
                            style = Type.subheadline, color = c.inkSoft, modifier = Modifier.fillMaxWidth().card(14.dp),
                        )
                    }
                    return@LazyColumn
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Hızlı seçim mevcut seçimi değiştirir; aynı seçim tekrar dokunulunca kalkar.
                        val picks = listOf<Pair<String, (Lesson) -> Boolean>>(
                            "Bu ay" to { it.date >= thisMonth && it.date < addMonths(thisMonth, 1) },
                            "Geçen ay" to { it.date >= lastMonth && it.date < thisMonth },
                            "İşlenenler" to { it.isCompleted },
                            "Gelecek 2 hafta" to { it.date >= startOfDay(now) && it.date < addDays(startOfDay(now), 14) },
                            "Tümü" to { true },
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(picks) { (title, filter) ->
                                val ids = candidates.filter(filter).map { it.id }.toSet()
                                val isOn = ids.isNotEmpty() && ids == selection
                                FormChip(title, isOn, Modifier.alpha(if (ids.isEmpty()) 0.45f else 1f)) {
                                    if (ids.isNotEmpty()) selection = if (isOn) emptySet() else ids
                                }
                            }
                            item {
                                FormChip("Temizle", false, Modifier.alpha(if (selection.isEmpty()) 0.45f else 1f)) { selection = emptySet() }
                            }
                        }
                        Text(
                            "Planlı dersleri seçersen peşin ödeme olur; ders iptal edilirse tutarı avansa geçer.",
                            style = Type.footnote, color = c.inkSoft,
                        )
                    }
                }
                months.forEach { (month, lessons) ->
                    item(key = month) {
                        val allSelected = lessons.all { it.id in selection }
                        Column {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Fmt.monthYear(month).uppercase(xyz.adilemree.dersdefteri.data.TR), style = Type.footnote, color = c.inkSoft, modifier = Modifier.weight(1f))
                                Text(
                                    if (allSelected) "Hiçbiri" else "Tümü",
                                    style = Type.caption.semibold(), color = c.accent,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                        val ids = lessons.map { it.id }
                                        selection = if (allSelected) selection - ids.toSet() else selection + ids
                                    }.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            FormSection {
                                lessons.forEachIndexed { index, lesson ->
                                    if (index > 0) RowDivider()
                                    val isOn = lesson.id in selection
                                    FormRow(onClick = { selection = if (isOn) selection - lesson.id else selection + lesson.id }) {
                                        Icon(
                                            if (isOn) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            null,
                                            tint = if (isOn) c.accent else c.inkSoft.copy(alpha = 0.5f),
                                            modifier = Modifier.size(24.dp),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "${Fmt.dayMonthShort(lesson.date)} ${Fmt.weekdayShort(lesson.date)} · ${Fmt.time(lesson.date)}",
                                                style = Type.subheadline.semibold(), color = c.ink,
                                            )
                                            val parts = mutableListOf(if (lesson.isPlanned) "Planlı" else "İşlendi", "${lesson.duration} dk")
                                            if (lesson.topic.isNotEmpty()) parts += lesson.topic
                                            Text(parts.joinToString(" · "), style = Type.caption, color = if (lesson.isPlanned) c.blue else c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(Fmt.money(notebook.fee(lesson)), style = Type.subheadline.semibold(), color = c.ink)
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.size(8.dp)) }
            }
        }
    }
}
