package xyz.adilemree.dersdefteri.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/// Google Play ile Pro yönetimi: abonelikler ve ömür boyu satın alma.
///
/// Pro iki kaynaktan biriyle açılır: cihazdaki Google Play kaydı ya da
/// sunucunun bildiği abonelik. Birinin "hayır" demesi diğerinin "evet"ini
/// ezmez (iOS'taki ProStore ile aynı kural).
class ProStore(context: Context, private val scope: CoroutineScope) : PurchasesUpdatedListener {
    companion object {
        const val MONTHLY = "dersdefteri.abonelik.aylik"
        const val YEARLY = "dersdefteri.abonelik.yillik"
        /// Tek seferlik (tüketilmeyen) satın alma
        const val LIFETIME = "dersdefteri.omurboyu"
        val SUBSCRIPTION_IDS = listOf(MONTHLY, YEARLY)

        /// Ücretsiz sürümde izin verilen aktif öğrenci sayısı
        const val FREE_STUDENT_LIMIT = 2
    }

    private val prefs = context.getSharedPreferences("pro", Context.MODE_PRIVATE)

    private val _playActive = MutableStateFlow(prefs.getBoolean("playActive", false))
    private val _serverActive = MutableStateFlow(prefs.getBoolean("serverActive", false))
    private val _isLifetime = MutableStateFlow(prefs.getBoolean("lifetime", false))
    private val _hasPlaySubscription = MutableStateFlow(false)

    val isPro: StateFlow<Boolean> = combine(_playActive, _serverActive) { a, b -> a || b }
        .stateIn(scope, SharingStarted.Eagerly, _playActive.value || _serverActive.value)

    /// Cihazdaki Play kaydında ömür boyu satın alma var.
    val isLifetime: StateFlow<Boolean> = _isLifetime.asStateFlow()
    val hasPlaySubscription: StateFlow<Boolean> = _hasPlaySubscription.asStateFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _isLoadingProducts = MutableStateFlow(false)
    val isLoadingProducts: StateFlow<Boolean> = _isLoadingProducts.asStateFlow()

    val purchaseError = MutableStateFlow<String?>(null)

    /// Giriş yapılmışsa sunucudaki hesap kimliği; satın almaya damgalanır.
    var accountIdProvider: () -> String? = { null }

    /// Satın almayı sunucuya bildirir ve sunucunun Pro kararını döndürür
    /// (hesap yoksa ya da ulaşılamazsa null). Eşitleme katmanı kurar.
    var serverVerifier: (suspend (productId: String, purchaseToken: String) -> Boolean?)? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val connectMutex = Mutex()

    fun canAddStudent(activeCount: Int): Boolean = isPro.value || activeCount < FREE_STUDENT_LIMIT

    /// Ömür boyu satın almada yönetilecek bir abonelik yoktur.
    val showsSubscriptionManagement: Boolean
        get() = isPro.value && (!_isLifetime.value || _hasPlaySubscription.value)

    fun product(id: String): ProductDetails? = _products.value.firstOrNull { it.productId == id }

    // MARK: Bağlantı

    private suspend fun connect(): Boolean = connectMutex.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    // MARK: Ürünler

    suspend fun loadProducts() {
        if (_products.value.isNotEmpty()) return
        _isLoadingProducts.value = true
        try {
            if (!connect()) {
                purchaseError.value = "Google Play'e bağlanılamadı. İnternet bağlantını kontrol et."
                return
            }
            val subs = queryDetails(SUBSCRIPTION_IDS, BillingClient.ProductType.SUBS)
            val inapp = queryDetails(listOf(LIFETIME), BillingClient.ProductType.INAPP)
            _products.value = subs + inapp
            if (_products.value.isEmpty()) {
                purchaseError.value = "Ürünler yüklenemedi. İnternet bağlantını kontrol et."
            } else {
                purchaseError.value = null
            }
        } finally {
            _isLoadingProducts.value = false
        }
    }

    private suspend fun queryDetails(ids: List<String>, type: String): List<ProductDetails> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(ids.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(type).build() })
            .build()
        return suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { result, details ->
                val list = if (result.responseCode == BillingClient.BillingResponseCode.OK) details.productDetailsList else emptyList()
                if (cont.isActive) cont.resume(list)
            }
        }
    }

    // MARK: Satın alma

    fun purchase(activity: Activity, product: ProductDetails) {
        purchaseError.value = null
        val builder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
        if (product.productType == BillingClient.ProductType.SUBS) {
            val offer = PlayOffers.purchaseOffer(product)
            if (offer == null) {
                purchaseError.value = "Bu plan şu an satın alınamıyor."
                return
            }
            builder.setOfferToken(offer.offerToken)
        } else {
            product.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken?.let { builder.setOfferToken(it) }
        }
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(builder.build()))
        // Girişliyken satın alma hesaba damgalanır; sunucu bununla aboneliğin
        // başka bir hesaba taşınmasını engeller.
        accountIdProvider()?.let { flow.setObfuscatedAccountId(it) }
        val result = client.launchBillingFlow(activity, flow.build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            purchaseError.value = "Satın alma başlatılamadı. Lütfen tekrar dene."
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch {
                purchases.orEmpty().forEach { handle(it) }
                refreshEntitlements()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refreshEntitlements() }
            else -> purchaseError.value = "Satın alma tamamlanamadı. Lütfen tekrar dene."
        }
    }

    /// Satın alma onaylanır (Play üç gün içinde onaylanmayanı iade eder) ve
    /// Pro hemen açılır; sunucuya arkadan bildirilir.
    private suspend fun handle(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.isAcknowledged) acknowledge(purchase)
        setPlayActive(true)
        if (LIFETIME in purchase.products) setLifetime(true)
    }

    private suspend fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { if (cont.isActive) cont.resume(Unit) }
        }
    }

    // MARK: Hak

    /// Cihazdaki Play kaydını okur ve sunucuya bildirir.
    suspend fun refreshEntitlements() {
        if (!connect()) return
        // Sorgu başarısızsa (Play'e ulaşılamadı) bilinen durum korunur; hata
        // "satın alma yok" sayılırsa Pro yanlışlıkla kapanırdı.
        val subs = (queryPurchases(BillingClient.ProductType.SUBS) ?: return)
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.any { p -> p in SUBSCRIPTION_IDS } }
        val inapp = (queryPurchases(BillingClient.ProductType.INAPP) ?: return)
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && LIFETIME in it.products }

        (subs + inapp).filter { !it.isAcknowledged }.forEach { acknowledge(it) }

        val lifetime = inapp.firstOrNull()
        val subscription = subs.maxByOrNull { it.purchaseTime }
        setLifetime(lifetime != null)
        _hasPlaySubscription.value = subscription != null
        setPlayActive(lifetime != null || subscription != null)

        // Sunucu kullanıcı başına tek kayıt tutar. Ömür boyu varken abonelik
        // gönderilirse, abonelik bitince sunucu eşitlemeyi kapatırdı.
        val best = lifetime ?: subscription ?: return
        val productId = best.products.first()
        serverVerifier?.invoke(productId, best.purchaseToken)?.let { setServerActive(it) }
    }

    private suspend fun queryPurchases(type: String): List<Purchase>? {
        val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
        return suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { result, list ->
                if (cont.isActive) cont.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) list else null)
            }
        }
    }

    suspend fun restore() {
        purchaseError.value = null
        refreshEntitlements()
        if (!isPro.value) purchaseError.value = "Geri yüklenecek aktif satın alma bulunamadı."
    }

    fun setServerActive(active: Boolean) {
        _serverActive.value = active
        prefs.edit().putBoolean("serverActive", active).apply()
    }

    /// Oturum kapanınca sunucu durumu artık bu cihaz için geçerli değildir.
    fun clearServerState() = setServerActive(false)

    private fun setPlayActive(active: Boolean) {
        _playActive.value = active
        prefs.edit().putBoolean("playActive", active).apply()
    }

    private fun setLifetime(value: Boolean) {
        _isLifetime.value = value
        prefs.edit().putBoolean("lifetime", value).apply()
    }
}

/// Abonelik tekliflerini okuma yardımcıları.
object PlayOffers {
    /// Satın alınacak teklif: hak kazanılmışsa ücretsiz denemeli teklif, yoksa
    /// temel plan. Play yalnız kullanıcının hak kazandığı teklifleri döndürür.
    fun purchaseOffer(product: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val offers = product.subscriptionOfferDetails.orEmpty()
        return offers.firstOrNull { trialPhase(it) != null } ?: offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
    }

    fun trialPhase(offer: ProductDetails.SubscriptionOfferDetails): ProductDetails.PricingPhase? =
        offer.pricingPhases.pricingPhaseList.firstOrNull { it.priceAmountMicros == 0L }

    /// Ücretsiz deneme varsa uzunluğu ("7 gün")
    fun trialLength(product: ProductDetails): String? {
        val offer = product.subscriptionOfferDetails.orEmpty().firstOrNull { trialPhase(it) != null } ?: return null
        val phase = trialPhase(offer) ?: return null
        return periodText(phase.billingPeriod)
    }

    /// Yenilenen dönemin fiyat aşaması (son aşama)
    fun recurringPhase(product: ProductDetails): ProductDetails.PricingPhase? =
        purchaseOffer(product)?.pricingPhases?.pricingPhaseList?.lastOrNull()

    fun formattedPrice(product: ProductDetails): String =
        if (product.productType == BillingClient.ProductType.SUBS) recurringPhase(product)?.formattedPrice.orEmpty()
        else product.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
            ?: product.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()

    fun priceMicros(product: ProductDetails): Long =
        if (product.productType == BillingClient.ProductType.SUBS) recurringPhase(product)?.priceAmountMicros ?: 0
        else product.oneTimePurchaseOfferDetailsList?.firstOrNull()?.priceAmountMicros
            ?: product.oneTimePurchaseOfferDetails?.priceAmountMicros ?: 0

    fun currencyCode(product: ProductDetails): String =
        if (product.productType == BillingClient.ProductType.SUBS) recurringPhase(product)?.priceCurrencyCode ?: "TRY"
        else product.oneTimePurchaseOfferDetailsList?.firstOrNull()?.priceCurrencyCode ?: "TRY"

    /// ISO 8601 süre ("P1W", "P7D", "P1M", "P1Y") → "7 gün", "1 ay"
    fun periodText(iso: String): String {
        val m = Regex("""P(\d+)([DWMY])""").find(iso) ?: return "deneme süresi"
        val n = m.groupValues[1].toInt()
        return when (m.groupValues[2]) {
            "D" -> "$n gün"
            "W" -> "${n * 7} gün"
            "M" -> "$n ay"
            else -> "$n yıl"
        }
    }
}
