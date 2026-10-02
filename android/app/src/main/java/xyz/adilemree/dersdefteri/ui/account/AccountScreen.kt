package xyz.adilemree.dersdefteri.ui.account

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.auth.AuthManager
import xyz.adilemree.dersdefteri.sync.SyncEngine
import xyz.adilemree.dersdefteri.ui.PaywallRoute
import xyz.adilemree.dersdefteri.ui.SignInRoute
import xyz.adilemree.dersdefteri.ui.components.Chalkboard
import xyz.adilemree.dersdefteri.ui.components.ConfirmDialog
import xyz.adilemree.dersdefteri.ui.components.FormRow
import xyz.adilemree.dersdefteri.ui.components.FormScaffold
import xyz.adilemree.dersdefteri.ui.components.FormSection
import xyz.adilemree.dersdefteri.ui.components.RowDivider
import xyz.adilemree.dersdefteri.ui.nav
import xyz.adilemree.dersdefteri.ui.pro.findActivity
import xyz.adilemree.dersdefteri.ui.settings.SITE
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.bold
import xyz.adilemree.dersdefteri.ui.theme.semibold
import xyz.adilemree.dersdefteri.util.Intents
import androidx.compose.material.icons.automirrored.rounded.ArrowBack

// MARK: - Eşitleme ve hesap

@Composable
fun AccountScreen() {
    val app = LocalApp.current
    val nav = nav()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val isPro by app.pro.isPro.collectAsStateWithLifecycle()
    val authState by app.auth.state.collectAsStateWithLifecycle()
    val name by app.auth.displayName.collectAsStateWithLifecycle()
    val email by app.auth.email.collectAsStateWithLifecycle()
    val status by app.sync.status.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    // Göreli zaman ("2 dakika önce") yarım dakikada bir tazelenir.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    FormScaffold(title = "Eşitleme", onClose = { nav.popBackStack() }, actionTitle = null, closeIcon = Icons.AutoMirrored.Rounded.ArrowBack) {
        // Eşitlemenin üç hâli var: abonelik yok, abonelik var ama hesap yok,
        // ve çalışır durumda. Her biri tek bir sonraki adım gösterir.
        when {
            !isPro -> FormSection(
                header = "Eşitleme",
                footer = "Şu an defterin yalnızca bu cihazda tutuluyor. Telefonun kaybolur ya da uygulama silinirse kayıtların geri getirilemez.",
            ) {
                FormRow(onClick = { nav.navigate(PaywallRoute) }) {
                    Icon(Icons.Rounded.WorkspacePremium, null, tint = c.amber, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Eşitlemeyi Aç", style = Type.body.bold(), color = c.ink)
                        Text("Ders Defteri Pro ile kayıtların hesabına yedeklenir", style = Type.caption, color = c.inkSoft)
                    }
                    Icon(Icons.Rounded.Lock, null, tint = c.amber, modifier = Modifier.size(16.dp))
                }
            }
            authState != AuthManager.State.SIGNED_IN -> FormSection(
                header = "Eşitleme",
                footer = "Pro'n aktif. Eşitlemeyi başlatmak için giriş yapman yeterli; mevcut kayıtların hesabına yüklenir.",
            ) {
                FormRow(onClick = { nav.navigate(SignInRoute) }) {
                    GoogleMark()
                    Text("Google ile Giriş Yap", style = Type.body.semibold(), color = c.accent)
                }
            }
            else -> FormSection(
                header = "Eşitleme",
                footer = "Kayıtların cihazında saklanır ve hesabına eşitlenir. Yeni bir telefona geçtiğinde aynı Google hesabıyla giriş yapman yeterli.",
            ) {
                SyncStatusRow(status, now)
                RowDivider()
                FormRow(onClick = { if (status !is SyncEngine.Status.Syncing) app.syncNow() }) {
                    Icon(Icons.Rounded.Sync, null, tint = if (status is SyncEngine.Status.Syncing) c.inkSoft else c.accent, modifier = Modifier.size(20.dp))
                    Text("Şimdi Eşitle", style = Type.body, color = if (status is SyncEngine.Status.Syncing) c.inkSoft else c.accent)
                }
            }
        }

        if (authState == AuthManager.State.SIGNED_IN) {
            FormSection(header = "Hesap", footer = "Çıkış yapsan da defterin cihazında kalır.") {
                FormRow {
                    GoogleMark(40)
                    Column(Modifier.weight(1f)) {
                        Text(name?.takeIf { it.isNotBlank() } ?: "Google hesabı", style = Type.subheadline.semibold(), color = c.ink)
                        Text(email ?: "Google ile giriş yapıldı", style = Type.caption, color = c.inkSoft)
                    }
                }
                RowDivider()
                FormRow(onClick = { confirmSignOut = true }) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Text("Çıkış Yap", style = Type.body, color = c.accent)
                }
            }

            FormSection(
                footer = "Hesabın ve sunucudaki tüm kayıtların kalıcı olarak silinir. Bu işlem geri alınamaz. Cihazındaki defter silinmez.\n\nHesabı silmek aboneliği iptal etmez. Aktif bir aboneliğin varsa Google Play'den ayrıca iptal etmelisin.",
            ) {
                FormRow(onClick = { if (!deleting) confirmDelete = true }) {
                    if (deleting) CircularProgressIndicator(Modifier.size(20.dp), color = c.red, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Delete, null, tint = c.red, modifier = Modifier.size(20.dp))
                    Text("Hesabı Sil", style = Type.body, color = c.red)
                }
                deleteError?.let {
                    RowDivider()
                    FormRow { Text(it, style = Type.caption, color = c.red) }
                }
                if (app.pro.showsSubscriptionManagement) {
                    RowDivider()
                    FormRow(onClick = {
                        Intents.open(context, Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}"))
                    }) {
                        Icon(Icons.Rounded.CreditCard, null, tint = c.accent, modifier = Modifier.size(20.dp))
                        Text("Aboneliği Yönet", style = Type.body, color = c.accent)
                    }
                }
            }
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Çıkış yapılsın mı? Defterin cihazda kalır.",
            confirmTitle = "Çıkış Yap",
            onConfirm = { scope.launch { app.auth.signOut() } },
            onDismiss = { confirmSignOut = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Hesabın ve sunucudaki tüm kayıtların kalıcı olarak silinecek. Aboneliğin varsa bu işlem onu iptal etmez. Emin misin?",
            confirmTitle = "Hesabı Sil",
            onConfirm = {
                deleting = true
                deleteError = null
                scope.launch {
                    try {
                        app.auth.deleteAccount()
                    } catch (e: Exception) {
                        deleteError = e.message ?: "Hesap silinemedi. Lütfen tekrar dene."
                    }
                    deleting = false
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SyncStatusRow(status: SyncEngine.Status, now: Long) {
    val c = AppTheme.colors
    val (icon, tint, title) = when (status) {
        is SyncEngine.Status.Synced -> Triple(Icons.Rounded.CloudDone, c.green, "Eşitlendi")
        SyncEngine.Status.Syncing -> Triple(Icons.Rounded.CloudSync, c.inkSoft, "Eşitleniyor…")
        is SyncEngine.Status.Failed -> Triple(Icons.Rounded.ErrorOutline, c.red, "Eşitlenemedi")
        SyncEngine.Status.Disabled -> Triple(Icons.Rounded.CloudOff, c.inkSoft, "Eşitleme kapalı")
        SyncEngine.Status.NeedsAccount -> Triple(Icons.Rounded.CloudOff, c.inkSoft, "Giriş gerekli")
        SyncEngine.Status.Idle -> Triple(Icons.Rounded.CloudSync, c.inkSoft, "Eşitleme bekliyor")
    }
    val detail = when (status) {
        is SyncEngine.Status.Synced ->
            if (now - status.at < 60_000) "Az önce"
            else DateUtils.getRelativeTimeSpanString(status.at, now, DateUtils.MINUTE_IN_MILLIS).toString()
        is SyncEngine.Status.Failed -> status.message
        else -> null
    }
    FormRow {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.subheadline.semibold(), color = c.ink)
            if (detail != null) Text(detail, style = Type.caption, color = c.inkSoft)
        }
    }
}

/// Google'ın çok renkli "G" harfi yerine sade, marka renkli daire.
@Composable
fun GoogleMark(size: Int = 24) {
    Box(
        Modifier.size(size.dp).background(Color.White, CircleShape).border(1.dp, Color(0x22000000), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = (size * 0.55f).sp)
    }
}

// MARK: - Giriş

/// Bu ekran bir kapı değil: uygulama hesapsız da tam çalışır. Buraya yalnızca
/// Pro kullanıcısı eşitlemeyi açmak istediğinde gelinir.
@Composable
fun SignInScreen() {
    val app = LocalApp.current
    val nav = nav()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val state by app.auth.state.collectAsStateWithLifecycle()
    val error by app.auth.errorMessage.collectAsStateWithLifecycle()
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(state) { if (state == AuthManager.State.SIGNED_IN) nav.popBackStack() }

    FormScaffold(title = "Eşitleme", onClose = { nav.popBackStack() }, actionTitle = null) {
        Chalkboard {
            Icon(Icons.Rounded.Sync, null, tint = Color.White, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(12.dp))
            Text("Eşitlemeyi Aç", style = Type.title, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Defterin hesabına yedeklensin, yeni telefonda kaldığın yerden devam et.", style = Type.subheadline, color = Color.White.copy(alpha = 0.85f))
        }
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Feature(Icons.Rounded.PhoneAndroid, "Yeni telefona geçiş", "Giriş yap, öğrencilerin ve kayıtların geri gelsin.")
            Feature(Icons.Rounded.VerifiedUser, "Şifre yok", "Google hesabınla tek dokunuşta giriş.")
            Feature(Icons.Rounded.Delete, "Dilediğinde sil", "Hesabını ve sunucudaki tüm kayıtlarını uygulamadan kalıcı silebilirsin.")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(if (c.isDark) Color.White else Color(0xFF1F1F1F))
                    .clickable(enabled = !working) {
                        val activity = context.findActivity() ?: return@clickable
                        working = true
                        scope.launch {
                            app.auth.signInWithGoogle(activity)
                            working = false
                        }
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (working) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = if (c.isDark) Color.Black else Color.White, strokeWidth = 2.dp)
                } else {
                    GoogleMark(22)
                    Spacer(Modifier.width(10.dp))
                    Text("Google ile Giriş Yap", style = Type.headline, color = if (c.isDark) Color.Black else Color.White)
                }
            }
            error?.let { Text(it, style = Type.caption, color = c.red, textAlign = TextAlign.Center) }
            Text("Giriş yaparak Kullanım Koşulları'nı ve Gizlilik Politikası'nı kabul edersin.", style = Type.caption2, color = c.inkSoft, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Gizlilik", style = Type.caption2.semibold(), color = c.accent, modifier = Modifier.clickable { Intents.open(context, Uri.parse("$SITE/gizlilik")) })
                Text("Koşullar", style = Type.caption2.semibold(), color = c.accent, modifier = Modifier.clickable { Intents.open(context, Uri.parse("$SITE/kosullar")) })
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, detail: String) {
    val c = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = Type.subheadline.bold(), color = c.ink)
            Text(detail, style = Type.caption, color = c.inkSoft)
        }
    }
}
