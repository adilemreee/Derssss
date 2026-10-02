package xyz.adilemree.dersdefteri.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.adilemree.dersdefteri.data.LessonTemplate
import xyz.adilemree.dersdefteri.data.Student
import xyz.adilemree.dersdefteri.data.TR
import xyz.adilemree.dersdefteri.data.TurkishPhoneFormat
import xyz.adilemree.dersdefteri.data.atTime
import xyz.adilemree.dersdefteri.data.startMillis
import xyz.adilemree.dersdefteri.data.toLocalDate
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.bold
import xyz.adilemree.dersdefteri.ui.theme.semibold
import xyz.adilemree.dersdefteri.ui.theme.serif
import xyz.adilemree.dersdefteri.ui.theme.studentColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// MARK: - Form ekranı iskeleti

/// iOS'taki form sayfalarının karşılığı: üstte kapat, başlık ve "Kaydet".
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScaffold(
    title: String,
    onClose: () -> Unit,
    actionTitle: String? = "Kaydet",
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
    closeIcon: ImageVector = Icons.Rounded.Close,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    Scaffold(
        containerColor = c.paper,
        topBar = {
            TopAppBar(
                title = { Text(title, style = Type.headline.serif(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(closeIcon, "Vazgeç", tint = c.ink) }
                },
                actions = {
                    if (actionTitle != null) {
                        TextButton(onClick = onAction, enabled = actionEnabled) {
                            Text(
                                actionTitle,
                                style = Type.body.bold(),
                                color = if (actionEnabled) c.accent else c.inkSoft.copy(alpha = 0.5f),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.paper, scrolledContainerColor = c.paper),
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            content = content,
        )
    }
}

/// Gruplu form bölümü: başlık, beyaz kart içinde satırlar, altında açıklama.
@Composable
fun FormSection(
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                header.uppercase(TR),
                style = Type.footnote,
                color = c.inkSoft,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.card)
                .border(1.dp, c.line, RoundedCornerShape(12.dp)),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = Type.footnote,
                color = c.inkSoft,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.6.dp, color = AppTheme.colors.line)
}

/// Form satırı: sabit en az yükseklik ve iç boşluk.
@Composable
fun FormRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun FormColumnRow(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/// Tek satırlık ya da çok satırlı metin alanı.
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    textAlign: TextAlign = TextAlign.Start,
) {
    val c = AppTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = Type.body.copy(color = c.ink, textAlign = textAlign),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        modifier = modifier,
        decorationBox = { inner ->
            Box(contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = Type.body.copy(textAlign = textAlign), color = c.inkSoft.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth())
                }
                inner()
            }
        },
    )
}

@Composable
fun FormTextRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    FormRow {
        FormTextField(
            value, onValueChange, placeholder,
            modifier = Modifier.weight(1f),
            singleLine = singleLine,
            minLines = minLines,
            keyboardType = keyboardType,
            capitalization = capitalization,
        )
    }
}

/// Telefon girişi: her tuşta "0532 123 45 67" biçimine çevrilir. Biçim
/// değişince imleç yerinde kalırsa sonraki rakamlar araya girer; bu yüzden
/// imleç her zaman sona alınır.
@Composable
fun FormPhoneRow(value: String, onValueChange: (String) -> Unit, placeholder: String = "05XX XXX XX XX") {
    val c = AppTheme.colors
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }
    FormRow {
        BasicTextField(
            value = field,
            onValueChange = { new ->
                val formatted = TurkishPhoneFormat.format(new.text)
                field = TextFieldValue(formatted, TextRange(formatted.length))
                if (formatted != value) onValueChange(formatted)
            },
            singleLine = true,
            textStyle = Type.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (field.text.isEmpty()) Text(placeholder, style = Type.body, color = c.inkSoft.copy(alpha = 0.6f))
                    inner()
                }
            },
        )
    }
}

/// "Saatlik ücret ...... 350 ₺" gibi etiketli sayı girişi.
@Composable
fun FormAmountRow(label: String, value: String, onValueChange: (String) -> Unit, suffix: String = "₺") {
    val c = AppTheme.colors
    FormRow {
        Text(label, style = Type.body, color = c.ink, modifier = Modifier.weight(1f))
        FormTextField(
            value = value,
            onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it == ',' || it == '.' }) },
            placeholder = "0",
            keyboardType = KeyboardType.Decimal,
            textAlign = TextAlign.End,
            modifier = Modifier.width(120.dp),
        )
        Text(suffix, style = Type.body, color = c.inkSoft)
    }
}

@Composable
fun FormValueRow(label: String, value: String, valueColor: Color = AppTheme.colors.inkSoft, bold: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = AppTheme.colors
    FormRow(onClick = onClick) {
        Text(label, style = Type.body, color = c.ink, modifier = Modifier.weight(1f))
        Text(value, style = if (bold) Type.body.semibold() else Type.body, color = valueColor, textAlign = TextAlign.End, maxLines = 2)
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkSoft.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun FormToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, leading: (@Composable () -> Unit)? = null) {
    val c = AppTheme.colors
    FormRow(onClick = { onCheckedChange(!checked) }) {
        if (leading != null) leading()
        Text(title, style = Type.body, color = c.ink, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.green, checkedThumbColor = Color.White),
        )
    }
}

@Composable
fun FormButtonRow(text: String, color: Color = AppTheme.colors.accent, center: Boolean = false, onClick: () -> Unit) {
    FormRow(onClick = onClick) {
        Text(
            text,
            style = Type.body,
            color = color,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
    }
}

// MARK: - Tarih ve saat

/// Material tarih seçicisi UTC gece yarısıyla çalışır; yerel güne çevrilir.
private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.utcToLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(dateMs: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = dateMs.toLocalDate().utcMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { utc ->
                    val day = utc.utcToLocalDate()
                    // Saat korunur: yalnız gün değişir.
                    val old = dateMs.toLocalDate()
                    val shifted = day.startMillis() + (dateMs - old.startMillis())
                    onPick(shifted)
                }
                onDismiss()
            }) { Text("Tamam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheet(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour, state.minute); onDismiss() }) { Text("Tamam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun FormDateRow(label: String, dateMs: Long, onChange: (Long) -> Unit) {
    val c = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    FormRow(onClick = { open = true }) {
        Text(label, style = Type.body, color = c.ink, modifier = Modifier.weight(1f))
        Text(
            xyz.adilemree.dersdefteri.data.Fmt.long(dateMs),
            style = Type.body,
            color = c.accent,
            modifier = Modifier.background(c.paper, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
    if (open) DatePickerSheet(dateMs, onDismiss = { open = false }, onPick = onChange)
}

@Composable
fun FormTimeRow(label: String, hour: Int, minute: Int, onChange: (Int, Int) -> Unit) {
    val c = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    FormRow(onClick = { open = true }) {
        Text(label, style = Type.body, color = c.ink, modifier = Modifier.weight(1f))
        Text(
            "%02d:%02d".format(hour, minute),
            style = Type.body,
            color = c.accent,
            modifier = Modifier.background(c.paper, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
    if (open) TimePickerSheet(hour, minute, onDismiss = { open = false }, onPick = onChange)
}

// MARK: - Seçiciler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, title) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = c.board,
                    activeContentColor = Color.White,
                    inactiveContainerColor = c.card,
                    inactiveContentColor = c.ink,
                    activeBorderColor = c.line,
                    inactiveBorderColor = c.line,
                ),
            ) {
                Text(title, style = Type.footnote.semibold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun FormSegmentedRow(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) { content() }
}

/// Açılır listeden seçim: satırda seçili değer, dokununca menü.
@Composable
fun <T> FormPickerRow(label: String, options: List<T>, selected: T, title: (T) -> String, onSelect: (T) -> Unit) {
    val c = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        FormRow(onClick = { open = true }) {
            Text(label, style = Type.body, color = c.ink, modifier = Modifier.weight(1f))
            Text(title(selected), style = Type.body, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
            Icon(Icons.Rounded.UnfoldMore, null, tint = c.inkSoft, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.card) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(title(option), color = c.ink, fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(option); open = false },
                )
            }
        }
    }
}

// MARK: - Çip seçiciler

@Composable
fun FormChip(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    showsChevron: Boolean = false,
    fillsWidth: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    Row(
        modifier
            .then(if (!fillsWidth) Modifier.widthIn(min = 56.dp) else Modifier)
            .clip(CircleShape)
            .background(if (isSelected) c.board else c.paper)
            .border(1.dp, if (isSelected) Color.Transparent else c.line, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (fillsWidth) 4.dp else 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Type.subheadline.semibold(), color = if (isSelected) Color.White else c.ink, maxLines = 1)
        if (showsChevron) {
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = if (isSelected) Color.White else c.ink, modifier = Modifier.size(14.dp))
        }
    }
}

/// Öğrenciler renkli avatar çipleri olarak yan yana; seçili olan kendi renginde dolar.
@Composable
fun StudentChipPicker(students: List<Student>, selectedId: String?, onSelect: (String) -> Unit) {
    val c = AppTheme.colors
    if (students.isEmpty()) {
        Text("Önce bir öğrenci eklemelisin.", style = Type.subheadline, color = c.inkSoft, modifier = Modifier.padding(4.dp))
        return
    }
    fun firstName(s: Student) = s.name.split(" ").firstOrNull { it.isNotBlank() } ?: s.name
    val names = students.map(::firstName)
    val duplicates = names.filter { n -> names.count { it == n } > 1 }.toSet()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val index = students.indexOfFirst { it.id == selectedId }
        if (index > 0) listState.scrollToItem(maxOf(index - 1, 0))
    }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(students, key = { it.id }) { student ->
            val selected = student.id == selectedId
            val color = studentColor(student.colorIndex)
            val first = firstName(student)
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(if (selected) color else c.paper)
                    .border(1.dp, if (selected) Color.Transparent else c.line, CircleShape)
                    .clickable {
                        onSelect(student.id)
                        scope.launch {
                            val index = students.indexOfFirst { it.id == student.id }
                            if (index >= 0) listState.animateScrollToItem(maxOf(index - 1, 0))
                        }
                    }
                    .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                StudentAvatar(
                    student,
                    size = 28.dp,
                    modifier = if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier,
                )
                Column {
                    Text(
                        if (first in duplicates) student.name else first,
                        style = Type.subheadline.semibold(),
                        color = if (selected) Color.White else c.ink,
                        maxLines = 1,
                    )
                    if (student.subject.isNotEmpty()) {
                        Text(
                            student.subject,
                            style = Type.caption2,
                            color = if (selected) Color.White.copy(alpha = 0.85f) else c.inkSoft,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/// En sık dört süre tek dokunuşta; diğerleri "Diğer" menüsünde.
@Composable
fun DurationChipPicker(selection: Int, onSelect: (Int) -> Unit) {
    val c = AppTheme.colors
    val common = listOf(45, 60, 90, 120)
    val all = listOf(30, 45, 60, 75, 90, 105, 120, 150, 180, 240)
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        common.forEach { minutes ->
            FormChip("$minutes", selection == minutes, Modifier.weight(1f), fillsWidth = true) { onSelect(minutes) }
        }
        Box(Modifier.weight(1f)) {
            FormChip(
                if (selection in common) "Diğer" else "$selection",
                isSelected = selection !in common,
                showsChevron = true,
                fillsWidth = true,
                modifier = Modifier.fillMaxWidth(),
            ) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.card) {
                all.forEach { minutes ->
                    DropdownMenuItem(text = { Text("$minutes dk", color = c.ink) }, onClick = { onSelect(minutes); menu = false })
                }
            }
        }
    }
}

/// Özel derslerin en sık saatleri tek dokunuşta.
@Composable
fun TimeChipPicker(hour: Int, minute: Int, onSelect: (Int) -> Unit) {
    val hours = listOf(10, 13, 15, 16, 17, 18, 19, 20)
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = hours.indexOf(hour)
        if (index > 1) listState.scrollToItem(index - 1)
    }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(hours) { h ->
            FormChip("%02d:00".format(h), isSelected = h == hour && minute == 0) { onSelect(h) }
        }
    }
}

/// Pazartesi'den başlayan kısa gün adları.
@Composable
fun WeekdayChipPicker(selection: Set<Int>, allowsMultiple: Boolean = true, onChange: (Set<Int>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LessonTemplate.weekdayOrder.forEach { day ->
            FormChip(
                LessonTemplate.shortNames[day].orEmpty(),
                isSelected = day in selection,
                fillsWidth = true,
                modifier = Modifier.weight(1f),
            ) {
                onChange(
                    if (allowsMultiple) {
                        if (day in selection) selection - day else selection + day
                    } else setOf(day),
                )
            }
        }
    }
}

// MARK: - Onay pencereleri

@Composable
fun ConfirmDialog(
    title: String,
    message: String? = null,
    confirmTitle: String,
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title, style = Type.headline, color = c.ink) },
        text = message?.let { { Text(it, style = Type.subheadline, color = c.inkSoft) } },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirmTitle, color = if (destructive) c.red else c.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = c.ink) } },
    )
}

data class SheetAction(
    val title: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/// iOS'taki uzun basma menüsünün ve seçenekli onayın karşılığı.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String? = null, message: String? = null, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.card) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = Type.subheadline.semibold(),
                    color = c.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            }
            if (message != null) {
                Text(
                    message,
                    style = Type.footnote,
                    color = c.inkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
            if (title != null || message != null) Spacer(Modifier.size(8.dp))
            actions.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss(); action.onClick() }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (action.icon != null) {
                        Icon(action.icon, null, tint = if (action.destructive) c.red else c.ink, modifier = Modifier.size(22.dp))
                    }
                    Text(action.title, style = Type.body, color = if (action.destructive) c.red else c.ink)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = c.line)
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Vazgeç", style = Type.body.semibold(), color = c.inkSoft)
            }
        }
    }
}

// MARK: - Sayı girişi

/// "1.250,50", "1250.5" ve "350" gibi girişleri sayıya çevirir.
fun parseAmount(text: String): Double? {
    val t = text.trim().replace(" ", "")
    if (t.isEmpty()) return null
    val normalized = when {
        t.contains(',') -> t.replace(".", "").replace(',', '.')
        t.count { it == '.' } > 1 -> t.replace(".", "")
        // "1.250" Türkçede bin ayracıdır; ondalık değil.
        Regex("""^\d{1,3}\.\d{3}$""").matches(t) -> t.replace(".", "")
        else -> t
    }
    return normalized.toDoubleOrNull()
}

/// Forma ilk değer olarak yazılacak metin ("350", "350,5"); sıfırsa boş.
fun amountText(value: Double): String {
    if (value <= 0.0) return ""
    return if (value == Math.rint(value)) value.toLong().toString()
    else ("%.2f".format(java.util.Locale.US, value)).trimEnd('0').trimEnd('.').replace('.', ',')
}

/// Seçili gün için başlangıç: günün tarihi, verilen saat.
fun dayAt(dayMs: Long, hour: Int, minute: Int): Long = atTime(dayMs, hour, minute)

@Composable
fun SmallCaption(text: String, color: Color = AppTheme.colors.inkSoft, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall.merge(Type.caption), color = color, modifier = modifier)
}
