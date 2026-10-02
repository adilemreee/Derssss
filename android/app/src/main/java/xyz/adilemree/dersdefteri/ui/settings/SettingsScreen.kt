package xyz.adilemree.dersdefteri.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.data.Notebook
import xyz.adilemree.dersdefteri.export.AppDataExport
import xyz.adilemree.dersdefteri.ui.AccountRoute
import xyz.adilemree.dersdefteri.ui.PaywallRoute
import xyz.adilemree.dersdefteri.ui.components.ConfirmDialog
import xyz.adilemree.dersdefteri.ui.components.FormPickerRow
import xyz.adilemree.dersdefteri.ui.components.FormRow
import xyz.adilemree.dersdefteri.ui.components.FormScaffold
import xyz.adilemree.dersdefteri.ui.components.FormSection
import xyz.adilemree.dersdefteri.ui.components.FormTextField
import xyz.adilemree.dersdefteri.ui.components.FormToggleRow
import xyz.adilemree.dersdefteri.ui.components.RowDivider
import xyz.adilemree.dersdefteri.ui.nav
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.SettingsGray
import xyz.adilemree.dersdefteri.ui.theme.StudentPalette
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.bold
import xyz.adilemree.dersdefteri.ui.theme.semibold
import xyz.adilemree.dersdefteri.util.Intents

const val SITE = "https://dersdefteri.adilemree.xyz"

/// Telefon Ayarları'ndaki gibi renkli kare içinde ikon
@Composable
fun SettingsIcon(icon: ImageVector, color: Color) {
    Box(Modifier.size(28.dp).background(color, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun LinkRow(title: String, icon: ImageVector, color: Color, titleColor: Color = AppTheme.colors.ink, trailing: String? = null, onClick: () -> Unit) {
    val c = AppTheme.colors
    FormRow(onClick = onClick) {
        SettingsIcon(icon, color)
        Text(title, style = Type.body, color = titleColor, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = Type.caption, color = c.inkSoft)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkSoft.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val nav = nav()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = app.settings
    val c = AppTheme.colors
    val notebook = app.repository.notebook.collectAsStateWithLifecycle().value ?: Notebook.EMPTY
    val isPro by app.pro.isPro.collectAsStateWithLifecycle()
    val isLifetime by app.pro.isLifetime.collectAsStateWithLifecycle()
    val teacherName by s.teacherName.flow.collectAsStateWithLifecycle()
    val remindersEnabled by s.remindersEnabled.flow.collectAsStateWithLifecycle()
    val reminderMinutes by s.reminderMinutes.flow.collectAsStateWithLifecycle()
    val donePrompt by s.lessonDonePromptEnabled.flow.collectAsStateWithLifecycle()
    val homeworkReminders by s.homeworkRemindersEnabled.flow.collectAsStateWithLifecycle()
    val homeworkHour by s.homeworkReminderHour.flow.collectAsStateWithLifecycle()
    val digestEnabled by s.dailyDigestEnabled.flow.collectAsStateWithLifecycle()
    val digestHour by s.dailyDigestHour.flow.collectAsStateWithLifecycle()
    val syncSummary by app.syncSummary.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }
    var exactAllowed by remember { mutableStateOf(true) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    // Ayarlara her dönüşte bildirim izni yeniden okunur.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            permissionDenied = !NotificationManagerCompat.from(context).areNotificationsEnabled()
            val allowed = app.notifications.canScheduleExact
            if (allowed != exactAllowed) app.notifications.resyncSoon()
            exactAllowed = allowed
        }
    }

    fun changed() = app.notifications.resyncSoon()

    FormScaffold(title = "Ayarlar", onClose = { nav.popBackStack() }, actionTitle = null) {
        FormSection(header = "Abonelik") {
            if (isPro) {
                FormRow {
                    SettingsIcon(Icons.Rounded.Verified, c.green)
                    Text(if (isLifetime) "Ders Defteri Pro · Ömür boyu" else "Ders Defteri Pro aktif", style = Type.body.semibold(), color = c.ink)
                }
            } else {
                FormRow(onClick = { nav.navigate(PaywallRoute) }) {
                    SettingsIcon(Icons.Rounded.WorkspacePremium, c.amber)
                    Column(Modifier.weight(1f)) {
                        Text("Ders Defteri Pro'ya Geç", style = Type.body.bold(), color = c.ink)
                        Text("Eşitleme, sınırsız öğrenci ve PDF veli raporu", style = Type.caption, color = c.inkSoft)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkSoft.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }
        }

        FormSection(header = "Profil", footer = "Boş bırakırsan ana sayfada Öğretmenim hitabı kullanılır.") {
            FormRow {
                SettingsIcon(Icons.Rounded.Person, c.blue)
                FormTextField(teacherName, { s.teacherName.value = it }, "Öğretmen adı", Modifier.weight(1f), capitalization = KeyboardCapitalization.Words)
            }
            RowDivider()
            FormRow { Text("Ana sayfada ${s.teacherDisplayName(teacherName)} olarak görünür.", style = Type.caption, color = c.inkSoft) }
        }

        FormSection(
            header = "Ders Bildirimleri",
            footer = "Hatırlatma ders saatinden önce gelir. \"Ders bitince sor\" açıksa ders bitiminde gelen bildirimdeki İşlendi düğmesiyle dersi uygulamayı açmadan işaretleyebilirsin.",
        ) {
            FormToggleRow("Ders hatırlatıcıları", remindersEnabled, { s.remindersEnabled.value = it; changed() }) {
                SettingsIcon(Icons.Rounded.Notifications, c.red)
            }
            if (remindersEnabled) {
                RowDivider()
                FormPickerRow("Ne kadar önce?", listOf(15, 30, 60, 120), reminderMinutes, { m ->
                    if (m >= 60) "${m / 60} saat önce" else "$m dakika önce"
                }) { s.reminderMinutes.value = it; changed() }
            }
            RowDivider()
            FormToggleRow("Ders bitince sor", donePrompt, { s.lessonDonePromptEnabled.value = it; changed() }) {
                SettingsIcon(Icons.Rounded.CheckCircle, c.green)
            }
            if (!exactAllowed && (remindersEnabled || donePrompt) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RowDivider()
                FormRow(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }) {
                    SettingsIcon(Icons.Rounded.AlarmOn, c.blue)
                    Column(Modifier.weight(1f)) {
                        Text("Tam zamanında hatırlat", style = Type.body, color = c.ink)
                        Text("İzin vermezsen hatırlatmalar 10 dakikaya kadar gecikebilir.", style = Type.caption, color = c.inkSoft)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkSoft.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }
            if (permissionDenied) {
                RowDivider()
                FormRow(onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Rounded.Warning, null, tint = c.red, modifier = Modifier.size(18.dp))
                    Text(
                        "Bildirim izni kapalı. Dokunup telefon ayarlarından Ders Defteri'ne izin verebilirsin.",
                        style = Type.caption, color = c.red, modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        FormSection(
            header = "Ödev Bildirimleri",
            footer = "Aktif ödevler için teslimden bir gün önce, teslim günü ve gecikirse ertesi gün hatırlatma kurulur.",
        ) {
            FormToggleRow("Ödev hatırlatıcıları", homeworkReminders, { s.homeworkRemindersEnabled.value = it; changed() }) {
                SettingsIcon(Icons.AutoMirrored.Rounded.MenuBook, c.blue)
            }
            if (homeworkReminders) {
                RowDivider()
                FormPickerRow("Bildirim saati", listOf(9, 12, 18, 21), homeworkHour, { "%02d:00".format(it) }) {
                    s.homeworkReminderHour.value = it; changed()
                }
            }
        }

        FormSection(
            header = "Günlük Özet",
            footer = if (isPro) "Ders olan her sabah, o günün programı tek bildirimde gelir."
            else "Günlük program özeti Ders Defteri Pro ile kullanılabilir.",
        ) {
            if (isPro) {
                FormToggleRow("Günlük program özeti", digestEnabled, { s.dailyDigestEnabled.value = it; changed() }) {
                    SettingsIcon(Icons.Rounded.WbTwilight, c.amber)
                }
                if (digestEnabled) {
                    RowDivider()
                    FormPickerRow("Bildirim saati", listOf(6, 7, 8, 9), digestHour, { "%02d:00".format(it) }) {
                        s.dailyDigestHour.value = it; changed()
                    }
                }
            } else {
                FormRow(onClick = { nav.navigate(PaywallRoute) }) {
                    SettingsIcon(Icons.Rounded.WbTwilight, c.amber)
                    Column(Modifier.weight(1f)) {
                        Text("Günlük Program Özeti", style = Type.body.semibold(), color = c.ink)
                        Text("Sabah tek bildirimde günün tüm dersleri", style = Type.caption, color = c.inkSoft)
                    }
                    Icon(Icons.Rounded.Lock, null, tint = c.amber, modifier = Modifier.size(16.dp))
                }
            }
        }

        FormSection(
            header = "Eşitleme",
            footer = if (isPro) "Kayıtların cihazında saklanır ve hesabına eşitlenir."
            else "Defterin yalnızca bu cihazda tutuluyor. Eşitleme Ders Defteri Pro ile açılır.",
        ) {
            LinkRow("Eşitleme ve Hesap", Icons.Rounded.Sync, c.green, trailing = syncSummary) { nav.navigate(AccountRoute) }
        }

        FormSection(header = "Hakkında") {
            LinkRow("Destek", Icons.AutoMirrored.Rounded.HelpOutline, c.blue) { Intents.open(context, Uri.parse("$SITE/destek")) }
            RowDivider()
            LinkRow("Gizlilik Politikası", Icons.Rounded.PrivacyTip, StudentPalette[4]) { Intents.open(context, Uri.parse("$SITE/gizlilik")) }
            RowDivider()
            LinkRow("Kullanım Koşulları", Icons.Rounded.Description, SettingsGray) { Intents.open(context, Uri.parse("$SITE/kosullar")) }
        }

        // Ücretsiz kullanıcının başka yedek yolu yok; dışa aktarma herkese açık.
        FormSection(
            header = "Dışa Aktar",
            footer = "Öğrenci, ders, ödeme ve ödev kayıtları ayrı CSV dosyaları olarak hazırlanır; Excel ve Google E-Tablolar ile açılır. Dosyaları Google Drive'a ya da e-postana kaydedersen yedeğin olur.",
        ) {
            LinkRow(if (exporting) "Hazırlanıyor…" else "CSV Dosyalarını Paylaş", Icons.Rounded.TableChart, c.green) {
                if (exporting) return@LinkRow
                exporting = true
                scope.launch {
                    val files = withContext(Dispatchers.IO) { AppDataExport.makeCsvFiles(context, notebook) }
                    exporting = false
                    Intents.shareFiles(context, files, "text/csv")
                }
            }
        }

        FormSection(footer = "Bu cihazdaki tüm öğrenci, ders, ödeme ve ödev kayıtları silinir. Eşitleme açıksa hesabındaki kayıtlar da silinir.") {
            FormRow(onClick = { showResetConfirm = true }) {
                SettingsIcon(Icons.Rounded.Delete, c.red)
                Text("Tüm Verileri Sil", style = Type.body, color = c.red)
            }
        }
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = "Tüm öğrenciler, dersler, ödemeler ve ödevler silinecek. Emin misin?",
            confirmTitle = "Hepsini Sil",
            onConfirm = { app.edit { deleteAll() } },
            onDismiss = { showResetConfirm = false },
        )
    }
}
