package xyz.adilemree.dersdefteri.ui.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.launch
import xyz.adilemree.dersdefteri.LocalApp
import xyz.adilemree.dersdefteri.billing.PlayOffers
import xyz.adilemree.dersdefteri.billing.ProStore
import xyz.adilemree.dersdefteri.data.TR
import xyz.adilemree.dersdefteri.ui.components.Chip
import xyz.adilemree.dersdefteri.ui.components.PrimaryButton
import xyz.adilemree.dersdefteri.ui.components.TintedButton
import xyz.adilemree.dersdefteri.ui.components.card
import xyz.adilemree.dersdefteri.ui.nav
import xyz.adilemree.dersdefteri.ui.settings.SITE
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.theme.bold
import xyz.adilemree.dersdefteri.ui.theme.semibold
import xyz.adilemree.dersdefteri.ui.theme.serif
import xyz.adilemree.dersdefteri.util.Intents
import java.text.NumberFormat
import java.util.Currency
import kotlin.math.roundToInt

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun title(product: ProductDetails): String = when (product.productId) {
    ProStore.YEARLY -> "Yıllık"
    ProStore.LIFETIME -> "Ömür Boyu"
    else -> "Aylık"
}

/// Fiyatın hangi dönem için olduğu; ömür boyu satın almada dönem yoktur.
private fun unit(product: ProductDetails): String? = when (product.productId) {
    ProStore.YEARLY -> "yıl"
    ProStore.LIFETIME -> null
    else -> "ay"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen() {
    val app = LocalApp.current
    val nav = nav()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pro = app.pro
    val products by pro.products.collectAsStateWithLifecycle()
    val loading by pro.isLoadingProducts.collectAsStateWithLifecycle()
    val error by pro.purchaseError.collectAsStateWithLifecycle()
    val isPro by pro.isPro.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf(ProStore.YEARLY) }
    val c = AppTheme.colors

    LaunchedEffect(Unit) { pro.loadProducts() }
    // Satın alma tamamlanınca ekran kendiliğinden kapanır.
    LaunchedEffect(isPro) { if (isPro) nav.popBackStack() }

    val ordered = listOf(ProStore.YEARLY, ProStore.MONTHLY, ProStore.LIFETIME).mapNotNull { id -> products.firstOrNull { it.productId == id } }
    val selected = ordered.firstOrNull { it.productId == selectedId }
    val monthly = products.firstOrNull { it.productId == ProStore.MONTHLY }
    val yearly = products.firstOrNull { it.productId == ProStore.YEARLY }
    /// Yıllık planın aylığa göre yüzde kazancı
    val savings = if (monthly != null && yearly != null) {
        val full = PlayOffers.priceMicros(monthly) * 12.0
        val saving = if (full > 0) (full - PlayOffers.priceMicros(yearly)) / full * 100 else 0.0
        saving.roundToInt().takeIf { it > 0 }
    } else null

    fun trial(product: ProductDetails): String? = if (product.productType == BillingClient.ProductType.SUBS) PlayOffers.trialLength(product) else null

    Scaffold(
        containerColor = c.paper,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, "Kapat", tint = c.ink) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.paper),
            )
        },
        bottomBar = {
            // Hemen altında ne ödeneceği yazar; ücretsiz deneme fiyat görünmeden durmaz.
            Column(
                Modifier.fillMaxWidth().background(c.paper).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HorizontalDivider(color = c.line, modifier = Modifier.padding(bottom = 6.dp))
                val t = selected?.let { trial(it) }
                PrimaryButton(
                    if (t != null) "${t.replaceFirstChar { it.titlecase(TR) }} Ücretsiz Dene" else "Pro'ya Geç",
                    enabled = selected != null,
                ) {
                    val activity = context.findActivity()
                    if (activity != null && selected != null) pro.purchase(activity, selected)
                }
                if (selected != null) {
                    val price = PlayOffers.formattedPrice(selected)
                    val u = unit(selected)
                    Text(
                        when {
                            u == null -> "$price tek seferlik ödeme. Abonelik değildir, yenilenmez."
                            t != null -> "${t.replaceFirstChar { it.titlecase(TR) }} ücretsiz, sonra $price/$u. İstediğin zaman iptal edebilirsin."
                            else -> "$price/$u. İstediğin zaman iptal edebilirsin."
                        },
                        style = Type.caption2, color = c.inkSoft, textAlign = TextAlign.Center,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Başlık
                Box(Modifier.size(60.dp).background(c.board, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.WorkspacePremium, null, tint = c.amber, modifier = Modifier.size(32.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ders Defteri Pro", style = Type.title, color = c.ink)
                    Text("Defterini sınırsız kullan", style = Type.subheadline, color = c.inkSoft)
                }

                // Özellikler
                Column(Modifier.fillMaxWidth().card(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Feature("Sınırsız öğrenci", "ücretsizde ${ProStore.FREE_STUDENT_LIMIT}")
                    Feature("Cihazlar arası eşitleme ve yedek")
                    Feature("PDF veli raporu")
                    Feature("Her sabah günlük program özeti")
                }

                // Planlar
                if (ordered.isEmpty()) {
                    Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (loading) {
                            CircularProgressIndicator(color = c.accent)
                            Text("Planlar yükleniyor…", style = Type.caption, color = c.inkSoft)
                        } else {
                            Icon(Icons.Rounded.WifiOff, null, tint = c.inkSoft)
                            Text("Planlar şu an yüklenemedi. İnternet bağlantını kontrol edip tekrar dene.", style = Type.caption, color = c.inkSoft, textAlign = TextAlign.Center)
                            TintedButton("Tekrar Dene") { scope.launch { pro.loadProducts() } }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ordered.forEach { product ->
                            PlanCard(product, product.productId == selectedId, if (product.productId == ProStore.YEARLY) savings else null, trial(product)) {
                                selectedId = product.productId
                            }
                        }
                    }
                }

                // Planlar hiç yüklenemediyse yukarıdaki açıklama zaten yeterli.
                if (ordered.isNotEmpty()) error?.let { Text(it, style = Type.caption, color = c.red, textAlign = TextAlign.Center) }

                Text(
                    "Satın Alımları Geri Yükle",
                    style = Type.caption.semibold(),
                    color = c.inkSoft,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { scope.launch { pro.restore() } }.padding(8.dp),
                )

                // Otomatik yenilenen abonelikte koşullar ve gizlilik bağlantısı bulunmalı.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Aylık ve yıllık abonelik, dönem sonunda iptal edilmediği sürece otomatik yenilenir. İstediğin zaman Google Play'den iptal edebilirsin. Ömür boyu tek seferlik ödemedir, yenilenmez. Mevcut verilerin Pro durumundan bağımsız olarak sende kalır.",
                        style = Type.caption2, color = c.inkSoft.copy(alpha = 0.8f), textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Kullanım Koşulları", style = Type.caption2.semibold(), color = c.accent,
                            modifier = Modifier.clickable { Intents.open(context, Uri.parse("$SITE/kosullar")) })
                        Text("Gizlilik Politikası", style = Type.caption2.semibold(), color = c.accent,
                            modifier = Modifier.clickable { Intents.open(context, Uri.parse("$SITE/gizlilik")) })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun Feature(title: String, note: String? = null) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.CheckCircle, null, tint = c.green, modifier = Modifier.size(18.dp))
        Text(title, style = Type.subheadline.semibold(), color = c.ink)
        if (note != null) Text(note, style = Type.caption, color = c.inkSoft)
    }
}

@Composable
private fun PlanCard(product: ProductDetails, isSelected: Boolean, savings: Int?, trial: String?, onClick: () -> Unit) {
    val c = AppTheme.colors
    val u = unit(product)
    val price = PlayOffers.formattedPrice(product)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.card)
            .border(if (isSelected) 1.8.dp else 1.dp, if (isSelected) c.accent else c.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
            null,
            tint = if (isSelected) c.accent else c.inkSoft.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title(product), style = Type.subheadline.bold(), color = c.ink)
                if (savings != null) Chip("%$savings avantajlı", c.green, filled = true)
            }
            // Denemeden sonra ne ödeneceği denemeyle aynı yerde yazmalı.
            if (trial != null && u != null) {
                Text("İlk $trial ücretsiz, sonra $price/$u", style = Type.caption, color = c.green)
            } else if (u == null) {
                Text("Bir kez öde, abonelik yok", style = Type.caption, color = c.inkSoft)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(price, style = Type.headline.bold().serif(), color = c.ink)
            Text(u?.let { "${it}da bir" } ?: "tek ödeme", style = Type.caption2, color = c.inkSoft)
            if (product.productId == ProStore.YEARLY) {
                val monthlyPrice = PlayOffers.priceMicros(product) / 1_000_000.0 / 12
                val f = NumberFormat.getCurrencyInstance(TR).apply {
                    currency = Currency.getInstance(PlayOffers.currencyCode(product))
                    maximumFractionDigits = 2
                }
                Text("ayda ${f.format(monthlyPrice)}", style = Type.caption2.semibold(), color = c.green)
            }
        }
    }
}
