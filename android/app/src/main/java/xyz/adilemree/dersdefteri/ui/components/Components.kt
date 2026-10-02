package xyz.adilemree.dersdefteri.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.adilemree.dersdefteri.R
import xyz.adilemree.dersdefteri.data.CancellationReason
import xyz.adilemree.dersdefteri.data.Fmt
import xyz.adilemree.dersdefteri.data.Lesson
import xyz.adilemree.dersdefteri.data.LessonPayState
import xyz.adilemree.dersdefteri.data.LessonStatus
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.data.Payment
import xyz.adilemree.dersdefteri.data.PaymentMethod
import xyz.adilemree.dersdefteri.data.Student
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.bold
import xyz.adilemree.dersdefteri.ui.theme.semibold
import xyz.adilemree.dersdefteri.ui.theme.serif
import xyz.adilemree.dersdefteri.ui.theme.studentColor

// MARK: - Kart

/// Beyaz kart: yuvarlak köşe, ince çizgi, hafif gölge.
fun Modifier.card(padding: Dp = 16.dp, radius: Dp = 18.dp): Modifier = composed {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(radius)
    this
        .shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.06f))
        .background(c.card, shape)
        .border(1.dp, c.line, shape)
        .padding(padding)
}

/// Kesikli çerçeve
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.2.dp, dash: Float = 6f, inset: Dp = 0.dp): Modifier =
    drawBehind {
        val w = width.toPx()
        val i = inset.toPx() + w / 2
        drawRoundRect(
            color = color,
            topLeft = Offset(i, i),
            size = Size(size.width - 2 * i, size.height - 2 * i),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * density, dash * density))),
        )
    }

// MARK: - Kara tahta paneli

@Composable
fun Chalkboard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, spotColor = c.boardDark.copy(alpha = 0.45f))
            .background(Brush.linearGradient(listOf(c.board, c.boardDark)), shape)
            .dashedBorder(Color.White.copy(alpha = 0.22f), radius = 13.dp, width = 1.5.dp, dash = 7f, inset = 7.dp)
            .padding(20.dp),
        content = content,
    )
}

// MARK: - Bölüm başlığı

@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(icon, null, tint = c.amber, modifier = Modifier.size(18.dp))
        Text(title, style = Type.title3, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

// MARK: - Etiketler

@Composable
fun Chip(text: String, tint: Color = AppTheme.colors.inkSoft, filled: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Type.caption.semibold(),
        color = if (filled) Color.White else tint,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .background(if (filled) tint else tint.copy(alpha = 0.13f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

fun statusIcon(status: LessonStatus): ImageVector = when (status) {
    LessonStatus.PLANNED -> Icons.Rounded.Schedule
    LessonStatus.COMPLETED -> Icons.Rounded.CheckCircle
    LessonStatus.CANCELLED -> Icons.Rounded.Cancel
}

@Composable
fun statusTint(status: LessonStatus): Color = when (status) {
    LessonStatus.PLANNED -> AppTheme.colors.blue
    LessonStatus.COMPLETED -> AppTheme.colors.green
    LessonStatus.CANCELLED -> AppTheme.colors.red
}

@Composable
fun StatusChip(status: LessonStatus) {
    val tint = statusTint(status)
    Row(
        Modifier.background(tint.copy(alpha = 0.14f), CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(statusIcon(status), null, tint = tint, modifier = Modifier.size(10.dp))
        Text(status.title, style = Type.caption2.bold(), color = tint, maxLines = 1)
    }
}

@Composable
fun BalanceBadge(stats: Notebook.Stats) {
    val c = AppTheme.colors
    val balance = stats.balance
    when {
        balance > 0.5 -> Chip("${Fmt.money(balance)} borç", c.red)
        balance < -0.5 -> Chip("${Fmt.money(-balance)} avans", c.blue)
        stats.totalEarned <= 0.5 && stats.totalPaid <= 0.5 && stats.plannedCount > 0 ->
            Chip("${stats.plannedCount} planlı ders", c.amber)
        stats.totalEarned <= 0.5 && stats.totalPaid <= 0.5 -> Chip("Borç yok", c.inkSoft)
        else -> Chip("Ödendi ✓", c.green)
    }
}

/// Ders tutarının ne kadarının ödendiğini gösteren ince çubuk.
@Composable
fun PaidProgressBar(paid: Double, total: Double, tint: Color = AppTheme.colors.green, modifier: Modifier = Modifier) {
    val ratio = if (total > 0) (paid / total).coerceIn(0.0, 1.0).toFloat() else 0f
    val track = AppTheme.colors.inkSoft.copy(alpha = 0.15f)
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(Modifier.fillMaxWidth(ratio).height(6.dp).clip(CircleShape).background(tint))
    }
}

// MARK: - Uygulama ikonu ve avatar

@Composable
fun BrandIcon(size: Dp = 92.dp) {
    Box(
        Modifier
            .size(size)
            .shadow(size * 0.12f, RoundedCornerShape(size * 0.2237f), spotColor = AppTheme.colors.boardDark.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(size * 0.2237f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.brand_icon),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.requiredSize(size * 1.08f),
        )
    }
}

@Composable
fun StudentAvatar(student: Student, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val color = studentColor(student.colorIndex)
    Box(
        modifier
            .size(size)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            student.initials,
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp,
            maxLines = 1,
        )
    }
}

// MARK: - Boş durum

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String = "",
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .dashedBorder(c.inkSoft.copy(alpha = 0.35f), radius = 18.dp)
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = c.inkSoft.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
        Text(title, style = Type.headline.serif(), color = c.ink, textAlign = TextAlign.Center)
        if (message.isNotEmpty()) {
            Text(message, style = Type.caption, color = c.inkSoft, textAlign = TextAlign.Center)
        }
        if (actionTitle != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            FilledPill(actionTitle, Icons.Rounded.Add, onClick = onAction)
        }
    }
}

// MARK: - Düğmeler

/// Bölüm başlığının yanındaki küçük renkli düğme ("Ders Ekle").
@Composable
fun PillButton(text: String, icon: ImageVector? = null, tint: Color = AppTheme.colors.accent, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, style = Type.caption.bold(), color = tint, maxLines = 1, softWrap = false)
    }
}

/// Dolu kapsül düğme (kara tahta yeşili)
@Composable
fun FilledPill(text: String, icon: ImageVector? = null, tint: Color = AppTheme.colors.board, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(tint)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(text, style = Type.subheadline.semibold(), color = Color.White, maxLines = 1)
    }
}

/// Ekranın ana eylemi: geniş, dolu, serif yazılı kapsül.
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = AppTheme.colors.board,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (enabled) tint else tint.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Type.headline.bold().serif(), color = Color.White)
    }
}

/// Açık zeminli, renkli yazılı düğme (iOS'taki .bordered)
@Composable
fun TintedButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = AppTheme.colors.accent,
    enabled: Boolean = true,
    prominent: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (prominent) tint else tint.copy(alpha = 0.12f)
    val fg = if (prominent) Color.White else tint
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) fg else fg.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Type.subheadline.semibold(), color = if (enabled) fg else fg.copy(alpha = 0.5f), maxLines = 1)
    }
}

/// Öğrenci sayfasındaki Ara / SMS / WhatsApp / Özet düğmeleri
@Composable
fun ContactActionButton(
    icon: ImageVector,
    title: String,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val color = if (enabled) tint else c.inkSoft
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(36.dp).background(color.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(
            title,
            style = Type.caption2.bold(),
            color = if (enabled) c.ink else c.inkSoft,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 80.dp),
        )
    }
}

// MARK: - Ders satırı (ortak)

@Composable
fun LessonRow(
    lesson: Lesson,
    notebook: Notebook,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    /// Öğrencinin kendi sayfasında her satırda adını tekrarlamak yerine dersin konusu başlık olur.
    showStudent: Boolean = true,
    /// Satırın yanında "işlendi" tik düğmesi varsa "Planlandı" etiketi tekrar olur.
    hidesPlannedStatus: Boolean = false,
    /// Öğrenci sayfasında hesaplanan ödeme durumu. Verilmezse yalnızca toplu ödeme bağı gösterilir.
    payState: LessonPayState? = null,
) {
    val c = AppTheme.colors
    val student = notebook.studentOf(lesson)
    val resolved: LessonPayState? = when {
        lesson.isCancelled -> null
        payState != null -> payState
        lesson.paymentId != null && notebook.paymentOf(lesson) != null -> LessonPayState.Bulk(lesson.paymentId)
        else -> null
    }
    val isBulkPaid = resolved is LessonPayState.Bulk
    val isPaid = isBulkPaid || resolved == LessonPayState.Paid
    val subject = student?.subject.orEmpty()

    val title = when {
        showStudent -> student?.name ?: "—"
        lesson.topic.isNotEmpty() -> lesson.topic
        else -> subject.ifEmpty { "Ders" }
    }
    val subtitle = if (!showStudent) {
        val parts = mutableListOf<String>()
        if (lesson.topic.isNotEmpty() && subject.isNotEmpty()) parts += subject
        parts += "${lesson.duration} dk"
        if (lesson.isCancelled && lesson.reason != CancellationReason.NONE) parts += lesson.reason.shortTitle
        if (isBulkPaid) parts += "Toplu ödendi"
        parts.joinToString(" • ")
    } else if (lesson.isCancelled && lesson.reason != CancellationReason.NONE) {
        if (subject.isEmpty()) lesson.reason.title else "$subject • ${lesson.reason.shortTitle}"
    } else if (lesson.topic.isEmpty()) subject
    else if (subject.isEmpty()) lesson.topic else "$subject • ${lesson.topic}"

    Row(
        modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(14.dp))
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (showDate) Fmt.dayMonthShort(lesson.date) else Fmt.time(lesson.date),
                style = Type.subheadline.bold().serif(),
                color = c.ink,
                maxLines = 1,
            )
            Text(
                if (showDate) Fmt.time(lesson.date) else "${lesson.duration} dk",
                style = Type.caption2,
                color = c.inkSoft,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .background(student?.let { studentColor(it.colorIndex) } ?: c.inkSoft, RoundedCornerShape(2.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Type.subheadline.semibold(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = Type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (isPaid) {
                    Icon(
                        if (isBulkPaid) Icons.Rounded.Layers else Icons.Rounded.Check,
                        null,
                        tint = c.green,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Text(
                    Fmt.money(notebook.fee(lesson)),
                    style = Type.caption.bold(),
                    color = if (isPaid) c.green else c.ink,
                    maxLines = 1,
                )
            }
            if (!(hidesPlannedStatus && lesson.isPlanned)) StatusChip(lesson.lessonStatus)
        }
    }
}

// MARK: - Ödeme satırı (ortak)

fun methodIcon(method: PaymentMethod): ImageVector = when (method) {
    PaymentMethod.CASH -> Icons.Rounded.Payments
    PaymentMethod.TRANSFER -> Icons.Rounded.SwapHoriz
    PaymentMethod.OTHER -> Icons.Rounded.CreditCard
}

@Composable
fun PaymentRow(payment: Payment, notebook: Notebook, modifier: Modifier = Modifier, showStudent: Boolean = true) {
    val c = AppTheme.colors
    val isBulk = notebook.isBulk(payment.id)
    val parts = mutableListOf(Fmt.dayMonthShort(payment.date))
    if (isBulk) parts += "${notebook.bulkSummary(payment.id)} toplu"
    if (showStudent) parts += payment.paymentMethod.title
    if (payment.note.isNotEmpty()) parts += payment.note

    Row(
        modifier.fillMaxWidth().card(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(c.green.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (isBulk) Icons.Rounded.Layers else methodIcon(payment.paymentMethod), null, tint = c.green, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (showStudent) notebook.student(payment.studentId)?.name ?: "—" else payment.paymentMethod.title,
                style = Type.subheadline.semibold(),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(parts.joinToString(" • "), style = Type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("+${Fmt.money(payment.amount)}", style = Type.subheadline.bold(), color = c.green, maxLines = 1)
    }
}

/// Üstü çizili metin (tamamlanan ödev)
fun strike(done: Boolean): TextDecoration? = if (done) TextDecoration.LineThrough else null
