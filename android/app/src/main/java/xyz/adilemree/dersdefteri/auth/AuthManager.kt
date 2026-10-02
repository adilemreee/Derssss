package xyz.adilemree.dersdefteri.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.adilemree.dersdefteri.BuildConfig
import xyz.adilemree.dersdefteri.net.ApiClient
import xyz.adilemree.dersdefteri.net.ApiException
import java.security.SecureRandom

@Serializable
private data class SignInResponse(val accessToken: String, val refreshToken: String, val user: User) {
    @Serializable
    data class User(val id: String, val email: String? = null, val name: String? = null)
}

/// Google ile giriş, oturum durumu ve hesap silme.
///
/// Uygulama hesapsız da tam çalışır; hesap yalnızca eşitleme için gerekir ve
/// o da Pro'ya aittir.
class AuthManager(context: Context, private val api: ApiClient) {
    enum class State { CHECKING, SIGNED_OUT, SIGNED_IN }

    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val credentials = CredentialManager.create(context)

    private val _state = MutableStateFlow(State.CHECKING)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _displayName = MutableStateFlow(prefs.getString("accountName", null))
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    private val _email = MutableStateFlow(prefs.getString("accountEmail", null))
    val email: StateFlow<String?> = _email.asStateFlow()

    val errorMessage = MutableStateFlow<String?>(null)

    /// Çıkışta eşitleme defteri ve sunucu Pro durumu da sıfırlanır.
    var onSignedOut: () -> Unit = {}

    val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    fun restoreSession() {
        _state.value = if (api.isSignedIn) State.SIGNED_IN else State.SIGNED_OUT
    }

    /// Sunucu oturumu reddettiğinde (yenileme jetonu geçersiz).
    fun sessionExpired() {
        if (_state.value == State.SIGNED_IN) finishSignOut()
    }

    // MARK: Google ile giriş

    suspend fun signInWithGoogle(activity: Activity) {
        errorMessage.value = null
        if (!isConfigured) {
            errorMessage.value = "Google ile giriş henüz yapılandırılmadı."
            return
        }
        val nonce = randomNonce()
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val idToken = try {
            val result = credentials.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                errorMessage.value = "Google kimlik bilgisi okunamadı."
                return
            }
        } catch (e: GetCredentialCancellationException) {
            // Kullanıcı vazgeçtiyse hata gösterme.
            return
        } catch (e: NoCredentialException) {
            errorMessage.value = "Bu cihazda Google hesabı bulunamadı. Telefon ayarlarından bir Google hesabı ekleyip tekrar dene."
            return
        } catch (e: GetCredentialException) {
            errorMessage.value = "Google girişi tamamlanamadı. Lütfen tekrar dene."
            return
        }

        try {
            val text = api.requestPublic("/v1/auth/google", buildJsonObject {
                put("idToken", idToken)
                put("nonce", nonce)
            })
            val response = api.decode<SignInResponse>(text)
            api.storeTokens(response.accessToken, response.refreshToken)
            api.storeAccountId(response.user.id)
            _displayName.value = response.user.name
            _email.value = response.user.email
            prefs.edit().putString("accountName", response.user.name).putString("accountEmail", response.user.email).apply()
            _state.value = State.SIGNED_IN
        } catch (e: ApiException) {
            errorMessage.value = e.message ?: "Girişte bir sorun oldu. Lütfen tekrar dene."
        }
    }

    // MARK: Çıkış ve silme

    suspend fun signOut() {
        runCatching { api.request("/v1/auth/logout", method = "POST") }
        finishSignOut()
    }

    /// Sunucudaki hesabı ve tüm kayıtları kalıcı olarak siler.
    suspend fun deleteAccount() {
        api.request("/v1/account", method = "DELETE")
        finishSignOut()
    }

    private fun finishSignOut() {
        api.clearTokens()
        _displayName.value = null
        _email.value = null
        prefs.edit().clear().apply()
        _state.value = State.SIGNED_OUT
        onSignedOut()
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
