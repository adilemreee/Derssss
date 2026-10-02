package xyz.adilemree.dersdefteri.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.ui.components.BrandIcon
import xyz.adilemree.dersdefteri.ui.components.Chalkboard
import xyz.adilemree.dersdefteri.ui.components.Chip
import xyz.adilemree.dersdefteri.ui.components.FormTextField
import xyz.adilemree.dersdefteri.ui.components.PaidProgressBar
import xyz.adilemree.dersdefteri.ui.components.PrimaryButton
import xyz.adilemree.dersdefteri.ui.components.card
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.StudentPalette
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.bold
import xyz.adilemree.dersdefteri.ui.theme.semibold
import xyz.adilemree.dersdefteri.ui.theme.serif

/// İlk açılışta gösterilen tanıtım akışı.
@Composable
fun OnboardingScreen() {
    val app = LocalApp.current
    val c = AppTheme.colors
    val lastPage = 3
    val pager = rememberPagerState { lastPage + 1 }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(app.settings.teacherName.value) }

    fun finish() {
        app.settings.teacherName.value = name.trim()
        app.settings.hasCompletedOnboarding.value = true
    }

    Column(Modifier.fillMaxSize().background(c.paper).safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage < lastPage) {
                Text(
                    "Atla",
                    style = Type.subheadline.semibold(),
                    color = c.inkSoft,
                    modifier = Modifier.padding(end = 12.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { scope.launch { pager.animateScrollToPage(lastPage) } }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            when (page) {
                0 -> Page("Ders Defterine\nHoş Geldin", "Öğrencilerini, derslerini, ödemelerini ve ödevlerini tek bir defterde topla. Kağıt karalamalara son.") {
                    BrandIcon(104.dp)
                }
                1 -> Page("Programın\nHep Hazır", "“Her Salı 17:00” gibi haftalık derslerini bir kez gir, sonraki haftalar otomatik planlansın. Ders öncesi bildirimle hatırla.") {
                    ProgramPreview()
                }
                2 -> Page("Kazancını\nTakip Et", "İşlenen dersler bakiyeye yansır; kim ne kadar ödedi, kimde ne kaldı anında görürsün.") {
                    BalancePreview()
                }
                else -> NamePage(name) { name = it }
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.Center) {
            (0..lastPage).forEach { i ->
                val width by animateDpAsState(if (i == pager.currentPage) 22.dp else 7.dp, label = "nokta")
                Box(
                    Modifier
                        .padding(horizontal = 3.5.dp)
                        .height(7.dp)
                        .width(width)
                        .background(if (i == pager.currentPage) c.accent else c.inkSoft.copy(alpha = 0.3f), CircleShape),
                )
            }
        }

        PrimaryButton(
            if (pager.currentPage < lastPage) "Devam" else "Başla",
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
        ) {
            if (pager.currentPage < lastPage) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else finish()
        }
    }
}

@Composable
private fun Page(title: String, message: String, art: @Composable () -> Unit) {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.heightIn(min = 120.dp), contentAlignment = Alignment.Center) { art() }
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = c.ink, textAlign = TextAlign.Center, lineHeight = 34.sp)
        Text(message, style = Type.subheadline.copy(lineHeight = 21.sp), color = c.inkSoft, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
    }
}

// MARK: - İsim sayfası

@Composable
private fun NamePage(name: String, onChange: (String) -> Unit) {
    val app = LocalApp.current
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        // Ana sayfanın kara tahtası, yazılan isimle anında güncellenir.
        Chalkboard {
            Text("Günaydın ${app.settings.teacherDisplayName(name)} 👋", style = Type.title3, color = Color.White, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Text("Bugün 3 ders • 4 sa", style = Type.subheadline.serif().copy(fontStyle = FontStyle.Italic), color = Color.White.copy(alpha = 0.85f))
        }
        Text("Sana Nasıl\nHitap Edelim?", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = c.ink, textAlign = TextAlign.Center, lineHeight = 34.sp)
        Text("Adını yazarsan ana sayfa sana isminle seslenir. Boş bırakabilirsin.", style = Type.subheadline, color = c.inkSoft, textAlign = TextAlign.Center)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .background(c.card, RoundedCornerShape(14.dp))
                .border(1.dp, c.line, RoundedCornerShape(14.dp))
                .padding(vertical = 14.dp, horizontal = 12.dp),
        ) {
            FormTextField(
                name, onChange, "Adın (isteğe bağlı)",
                modifier = Modifier.fillMaxWidth(),
                capitalization = KeyboardCapitalization.Words,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(60.dp))
    }
}

// MARK: - Tanıtım görselleri

/// Program ekranından küçük bir kesit: haftalık şerit ve iki ders.
@Composable
private fun ProgramPreview() {
    val c = AppTheme.colors
    Column(Modifier.width(260.dp).card(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Pzt" to "15", "Sal" to "16", "Çar" to "17", "Per" to "18", "Cum" to "19").forEach { (day, number) ->
                val selected = day == "Sal"
                Column(
                    Modifier.weight(1f).background(if (selected) c.board else c.paper, RoundedCornerShape(9.dp)).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(day, style = Type.caption2.semibold(), color = if (selected) Color.White.copy(alpha = 0.8f) else c.inkSoft)
                    Text(number, style = Type.subheadline.bold().serif(), color = if (selected) Color.White else c.ink)
                }
            }
        }
        PreviewLesson("16:00", "Ayşe", "Matematik", StudentPalette[0])
        PreviewLesson("18:00", "Can", "Fizik", StudentPalette[1])
    }
}

@Composable
private fun PreviewLesson(time: String, name: String, subject: String, color: Color) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.paper, RoundedCornerShape(10.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(time, style = Type.caption.bold().serif(), color = c.ink)
        Box(Modifier.width(3.dp).height(22.dp).background(color, CircleShape))
        Text(name, style = Type.caption.semibold(), color = c.ink)
        Chip(subject, color)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Rounded.CheckCircle, null, tint = c.green, modifier = Modifier.size(20.dp))
    }
}

/// Ödemeler ekranından küçük bir kesit: bakiye ve ödenen oranı.
@Composable
private fun BalancePreview() {
    val c = AppTheme.colors
    Column(Modifier.width(280.dp).card(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).background(StudentPalette[0], CircleShape), contentAlignment = Alignment.Center) {
                Text("AY", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("Ayşe Yılmaz", style = Type.subheadline.semibold(), color = c.ink)
                Text("₺2.400 / ₺3.600 ödendi", style = Type.caption2, color = c.inkSoft, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₺1.200", style = Type.headline.bold().serif(), color = c.red)
                Text("kalan", style = Type.caption2.semibold(), color = c.inkSoft)
            }
        }
        PaidProgressBar(2400.0, 3600.0)
    }
}
