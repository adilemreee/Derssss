package xyz.adilemree.dersdefteri

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.adilemree.dersdefteri.auth.AuthManager
import xyz.adilemree.dersdefteri.billing.ProStore
import xyz.adilemree.dersdefteri.data.AppDatabase
import xyz.adilemree.dersdefteri.data.AppSettings
import xyz.adilemree.dersdefteri.data.NotebookEditor
import xyz.adilemree.dersdefteri.data.NotebookRepository
import xyz.adilemree.dersdefteri.net.ApiClient
import xyz.adilemree.dersdefteri.notifications.AppNotifications
import xyz.adilemree.dersdefteri.sync.SyncEngine
import xyz.adilemree.dersdefteri.widget.TodayWidget

@Serializable
private data class ServerEntitlement(val isPro: Boolean = false, val productId: String? = null, val status: String = "none")

/// Uygulama genelindeki nesneler. Ekranlar `LocalApp` ile erişir.
class AppContainer(val context: Context) {
    /// Uygulama ömrü boyunca yaşayan kapsam: ekran kapansa da kayıt yarım kalmaz.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = AppDatabase.build(context)
    val repository = NotebookRepository(database, scope)
    val settings = AppSettings(context)
    val api = ApiClient(context)
    val pro = ProStore(context, scope)
    val auth = AuthManager(context, api)
    val sync = SyncEngine(context, repository, api)
    val notifications = AppNotifications(context, repository, settings, { pro.isPro.value }, scope)

    /// Ayarlar'daki "Eşitleme ve Hesap" satırının özeti
    val syncSummary: StateFlow<String> = combine(pro.isPro, auth.state) { isPro, state ->
        when {
            !isPro -> "Kapalı"
            state == AuthManager.State.SIGNED_IN -> "Açık"
            else -> "Giriş gerekli"
        }
    }.stateIn(scope, SharingStarted.Eagerly, "Kapalı")

    /// Defteri değiştirir; ekran kapansa da iş tamamlanır.
    fun edit(block: NotebookEditor.() -> Unit): Job = scope.launch { repository.edit(block) }

    fun syncNow(): Job = scope.launch { sync.sync(pro.isPro.value) }

    init {
        pro.accountIdProvider = { api.accountId }
        pro.serverVerifier = ::verifyWithServer
        auth.onSignedOut = {
            sync.reset()
            pro.clearServerState()
        }
        sync.onSessionExpired = { auth.sessionExpired() }
        notifications.createChannels()

        // Her kayıttan sonra bildirimler ve widget baştan kurulur (kısa beklemeyle).
        scope.launch { repository.saved.collect { notifications.resyncSoon() } }

        // Satın alma biter bitmez ilk eşitleme başlar; günlük özet de kurulur.
        scope.launch {
            pro.isPro.drop(1).distinctUntilChanged().collect { isPro ->
                if (isPro) sync.sync(true)
                notifications.resyncSoon()
            }
        }

        // Giriş yapılınca önce cihazdaki satın alma sunucuya bildirilir: hesapsızken
        // abone olup sonra giriş yapan kullanıcıyı sunucu ancak böyle tanır.
        scope.launch {
            auth.state.collect { state ->
                when (state) {
                    AuthManager.State.SIGNED_IN -> {
                        pro.refreshEntitlements()
                        refreshFromServer()
                        sync.sync(pro.isPro.value)
                    }
                    AuthManager.State.SIGNED_OUT -> pro.clearServerState()
                    AuthManager.State.CHECKING -> Unit
                }
            }
        }

        // Uygulama öne geldiğinde ve arkaya giderken eşitlenir; başka cihazdaki
        // değişiklik açılışta görünür, bu cihazdaki değişiklik kapanmadan gider.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> scope.launch {
                        sync.sync(pro.isPro.value)
                        TodayWidget.refresh(context)
                    }
                    Lifecycle.Event.ON_STOP -> scope.launch { sync.sync(pro.isPro.value) }
                    else -> Unit
                }
            },
        )

        // Açılış: oturum, eksik haftalık dersler, bildirimler.
        scope.launch {
            auth.restoreSession()
            // Eşitleme açıksa bu işi eşitleme motoru, sunucudan güncel hâli aldıktan sonra yapar.
            if (!sync.handlesRecurringLessons(pro.isPro.value)) repository.edit { topUp() }
            notifications.resync()
            if (auth.state.value != AuthManager.State.SIGNED_IN) pro.refreshEntitlements()
        }
    }

    /// Satın almayı sunucuya bildirir; hesap yoksa ya da ulaşılamazsa null.
    private suspend fun verifyWithServer(productId: String, purchaseToken: String): Boolean? {
        if (!api.isSignedIn) return null
        return runCatching {
            val text = api.request("/v1/subscription/verify-google", method = "POST", body = buildJsonObject {
                put("productId", productId)
                put("purchaseToken", purchaseToken)
            })
            api.decode<ServerEntitlement>(text).isPro
        }.getOrNull()
    }

    /// Sunucudaki kaydı doğrudan okur. Abonelik iptal veya iade edildiğinde
    /// cihazdaki kayıt hemen düşmeyebilir.
    suspend fun refreshFromServer() {
        if (!api.isSignedIn) {
            pro.setServerActive(false)
            return
        }
        runCatching {
            val text = api.request("/v1/subscription")
            pro.setServerActive(api.decode<ServerEntitlement>(text).isPro)
        }
    }
}

class DersDefteriApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        java.util.Locale.setDefault(xyz.adilemree.dersdefteri.data.TR)
        container = AppContainer(this)
    }
}

val Context.app: AppContainer get() = (applicationContext as DersDefteriApp).container

val LocalApp = staticCompositionLocalOf<AppContainer> { error("AppContainer yok") }
