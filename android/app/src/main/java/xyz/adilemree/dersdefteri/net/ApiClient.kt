package xyz.adilemree.dersdefteri.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import xyz.adilemree.dersdefteri.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/// Sunucu hataları; mesajlar kullanıcıya gösterilir.
sealed class ApiException(message: String) : Exception(message) {
    class Offline : ApiException("İnternet bağlantısı yok. Değişikliklerin cihazda saklandı, bağlanınca eşitlenecek.")
    class Unauthorized : ApiException("Oturumun sona erdi. Lütfen tekrar giriş yap.")
    class Server(val code: Int, message: String?) : ApiException(message ?: "Sunucuya ulaşılamadı. Birazdan tekrar denenecek.")
    class Decoding : ApiException("Sunucudan beklenmeyen bir yanıt geldi.")
}

@Serializable
private data class ServerError(val error: String? = null, val message: String? = null)

@Serializable
private data class TokenResponse(val accessToken: String, val refreshToken: String)

/// Sunucu iletişimi: kimlik doğrulama, jeton tazeleme, hata çevirisi.
class ApiClient(context: Context) {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    private val baseUrl = BuildConfig.API_BASE_URL
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /// Jetonlar yedeklemeye girmeyen ayrı bir tercih dosyasında durur.
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    /// Aynı anda birden çok istek 401 alırsa tek bir tazeleme yapılır; aksi
    /// halde tek kullanımlık yenileme jetonu yarışa girip iptal olur.
    private val refreshMutex = Mutex()

    private var accessToken: String?
        get() = prefs.getString("accessToken", null)
        set(value) = prefs.edit().putString("accessToken", value).apply()

    private var refreshToken: String?
        get() = prefs.getString("refreshToken", null)
        set(value) = prefs.edit().putString("refreshToken", value).apply()

    val isSignedIn: Boolean get() = refreshToken != null

    /// Sunucudaki hesap kimliği. Satın almalara damgalanır.
    val accountId: String? get() = prefs.getString("accountID", null)

    fun storeTokens(access: String, refresh: String) {
        prefs.edit().putString("accessToken", access).putString("refreshToken", refresh).commit()
    }

    fun storeAccountId(id: String) {
        prefs.edit().putString("accountID", id).apply()
    }

    fun clearTokens() {
        prefs.edit().remove("accessToken").remove("refreshToken").remove("accountID").commit()
    }

    // MARK: İstekler

    /// Kimlik gerektirmeyen çağrı (giriş, jeton tazeleme).
    suspend fun requestPublic(path: String, body: JsonElement, method: String = "POST"): String =
        send(build(path, method, body, emptyMap(), token = null))

    /// Kimlik gerektiren çağrı. 401 alınırsa jeton bir kez tazelenip yeniden denenir.
    suspend fun request(path: String, method: String = "GET", body: JsonElement? = null, query: Map<String, String> = emptyMap()): String {
        return try {
            authorized(path, method, body, query)
        } catch (e: ApiException.Unauthorized) {
            // Tazeleme geçici bir sebeple başarısız olursa o hata yukarı taşınır;
            // oturum yalnızca sunucu yenileme jetonunu reddettiğinde kapanır.
            refreshTokens()
            authorized(path, method, body, query)
        }
    }

    inline fun <reified T> decode(text: String): T = try {
        json.decodeFromString<T>(text)
    } catch (e: Exception) {
        throw ApiException.Decoding()
    }

    private suspend fun authorized(path: String, method: String, body: JsonElement?, query: Map<String, String>): String {
        val token = accessToken ?: throw ApiException.Unauthorized()
        return send(build(path, method, body, query, token))
    }

    private fun build(path: String, method: String, body: JsonElement?, query: Map<String, String>, token: String?): Request {
        val url = (baseUrl + path).toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val requestBody = body?.toString()?.toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url(url)
            .method(method, requestBody ?: if (method == "POST" || method == "PUT") "{}".toRequestBody("application/json".toMediaType()) else null)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
    }

    private suspend fun send(request: Request): String = withContext(Dispatchers.IO) {
        val response = try {
            http.newCall(request).await()
        } catch (e: IOException) {
            throw ApiException.Offline()
        }
        response.use {
            val text = it.body.string()
            when (it.code) {
                in 200..299 -> text
                401 -> throw ApiException.Unauthorized()
                else -> {
                    val message = runCatching { json.decodeFromString<ServerError>(text).message }.getOrNull()
                    throw ApiException.Server(it.code, message)
                }
            }
        }
    }

    private suspend fun refreshTokens() = refreshMutex.withLock {
        val refresh = refreshToken ?: throw ApiException.Unauthorized()
        try {
            val text = requestPublic("/v1/auth/refresh", buildJsonObject { put("refreshToken", refresh) })
            val result = decode<TokenResponse>(text)
            storeTokens(result.accessToken, result.refreshToken)
        } catch (e: ApiException.Unauthorized) {
            // Sunucu yenileme jetonunu reddetti: oturum gerçekten bitmiştir.
            clearTokens()
            throw e
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
