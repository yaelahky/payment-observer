package com.fgteam.paymentobserver.network

import com.fgteam.paymentobserver.BuildConfig
import com.fgteam.paymentobserver.auth.AdminSession
import com.fgteam.paymentobserver.auth.ApiException
import com.fgteam.paymentobserver.auth.AuthenticationRequiredException
import com.fgteam.paymentobserver.auth.ObserverAccessDeniedException
import com.fgteam.paymentobserver.auth.SessionManager
import com.fgteam.paymentobserver.data.IncomingPayment
import com.fgteam.paymentobserver.data.LocalDateTimeConverter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class RemotePayment(
    val id: String,
    val matchStatus: String,
    val orderId: String?,
    val matchedAt: LocalDateTime?
)

data class RemotePaymentStatus(
    val id: String,
    val status: String,
    val orderId: String?,
    val matchedAt: LocalDateTime?
)

class PaymentObserverApi private constructor(
    private val sessionManager: SessionManager,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val refreshMutex = Mutex()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val basicAuthorization = Credentials.basic(
        BuildConfig.BASIC_AUTH_USERNAME,
        BuildConfig.BASIC_AUTH_PASSWORD
    )

    suspend fun login(email: String, password: String): AdminSession {
        val body = JSONObject().put("email", email).put("password", password)
        val response = execute("v1/users/login", "POST", body, basicAuthorization)
        val data = response.getJSONObject("data")
        val parsed = parseSession(data, previous = null)
        if (parsed.role != SessionManager.ADMIN_ROLE) throw ObserverAccessDeniedException()
        sessionManager.save(parsed)
        return parsed
    }

    suspend fun validateProfile(): AdminSession {
        val response = authenticatedRequest("v1/users/profile", "GET")
        val data = response.getJSONObject("data")
        if (data.optString("role") != SessionManager.ADMIN_ROLE) {
            sessionManager.clear()
            throw ObserverAccessDeniedException()
        }
        val current = sessionManager.current() ?: throw AuthenticationRequiredException()
        val updated = current.copy(fullName = data.optString("fullName", current.fullName), role = "admin")
        sessionManager.save(updated)
        return updated
    }

    suspend fun postPayment(payment: IncomingPayment): RemotePayment {
        val body = JSONObject()
            .put("id", payment.id)
            .put("sourceNotificationKey", payment.sourceNotificationKey)
            .put("amount", payment.amount)
            .put("rawAmount", payment.rawAmount)
            .put("sender", payment.sender)
            .put("title", payment.title)
            .put("body", payment.body)
            .put("appName", payment.appName)
            .put("packageName", payment.packageName)
            .put("createdAt", LocalDateTimeConverter.fromLocalDateTime(payment.createdAt))
            .put("updatedAt", LocalDateTimeConverter.fromLocalDateTime(payment.updatedAt))
        val data = authenticatedRequest("v1/payment-observer/payments", "POST", body)
            .getJSONObject("data")
        return RemotePayment(
            id = data.getString("id"),
            matchStatus = data.getString("matchStatus"),
            orderId = data.nullableString("orderId"),
            matchedAt = data.nullableDateTime("matchedAt")
        )
    }

    suspend fun getStatuses(ids: List<String>): List<RemotePaymentStatus> {
        if (ids.isEmpty()) return emptyList()
        val body = JSONObject().put("ids", JSONArray(ids))
        val data = authenticatedRequest("v1/payment-observer/payments/status", "POST", body)
            .getJSONArray("data")
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.getJSONObject(index)
                add(
                    RemotePaymentStatus(
                        id = item.getString("id"),
                        status = item.getString("status"),
                        orderId = item.nullableString("orderId"),
                        matchedAt = item.nullableDateTime("matchedAt")
                    )
                )
            }
        }
    }

    private suspend fun authenticatedRequest(
        path: String,
        method: String,
        body: JSONObject? = null
    ): JSONObject {
        val token = validAccessToken(forceRefresh = false)
        return try {
            execute(path, method, body, "Bearer $token")
        } catch (error: ApiException) {
            if (error.statusCode == 403) {
                sessionManager.clear()
                throw AuthenticationRequiredException("Akun tidak lagi memiliki akses admin")
            }
            if (error.statusCode != 401) throw error
            val refreshedToken = validAccessToken(forceRefresh = true, rejectedToken = token)
            execute(path, method, body, "Bearer $refreshedToken")
        }
    }

    private suspend fun validAccessToken(forceRefresh: Boolean, rejectedToken: String? = null): String {
        val current = sessionManager.current() ?: throw AuthenticationRequiredException()
        val now = System.currentTimeMillis() / 1000
        if (!forceRefresh && current.accessTokenExpiresInUnix > now + 30) return current.accessToken
        return refreshMutex.withLock {
            val latest = sessionManager.current() ?: throw AuthenticationRequiredException()
            val lockedNow = System.currentTimeMillis() / 1000
            if (rejectedToken != null && latest.accessToken != rejectedToken) {
                return@withLock latest.accessToken
            }
            if (!forceRefresh && latest.accessTokenExpiresInUnix > lockedNow + 30) {
                return@withLock latest.accessToken
            }
            if (latest.refreshTokenExpiresInUnix <= lockedNow) {
                sessionManager.clear()
                throw AuthenticationRequiredException("Refresh token kedaluwarsa")
            }
            val body = JSONObject().put("refreshToken", latest.refreshToken)
            val response = try {
                execute("v1/users/token", "POST", body, basicAuthorization)
            } catch (error: ApiException) {
                if (error.statusCode == 401 || error.statusCode == 403 || error.statusCode == 409) {
                    sessionManager.clear()
                    throw AuthenticationRequiredException("Sesi admin telah berakhir")
                }
                throw error
            }
            val refreshed = parseSession(response.getJSONObject("data"), latest)
            sessionManager.save(refreshed)
            refreshed.accessToken
        }
    }

    private suspend fun execute(
        path: String,
        method: String,
        body: JSONObject? = null,
        authorization: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/" + path.trimStart('/'))
            .header("Authorization", authorization)
        if (method == "GET") builder.get()
        else builder.method(method, (body ?: JSONObject()).toString().toRequestBody(jsonMediaType))
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            if (!response.isSuccessful) {
                throw ApiException(response.code, json.optString("message", "HTTP ${response.code}"))
            }
            if (!json.optBoolean("success", true)) {
                throw ApiException(response.code, json.optString("message", "Request gagal"))
            }
            json
        }
    }

    private fun parseSession(data: JSONObject, previous: AdminSession?): AdminSession = AdminSession(
        accessToken = data.getString("accessToken"),
        refreshToken = data.getString("refreshToken"),
        accessTokenExpiresIn = data.optString("accessTokenExpiresIn", previous?.accessTokenExpiresIn.orEmpty()),
        refreshTokenExpiresIn = data.optString("refreshTokenExpiresIn", previous?.refreshTokenExpiresIn.orEmpty()),
        accessTokenExpiresInUnix = data.optLong("accessTokenExpiresInUnix", 0),
        refreshTokenExpiresInUnix = data.optLong("refreshTokenExpiresInUnix", previous?.refreshTokenExpiresInUnix ?: 0),
        fullName = data.optString("fullName", previous?.fullName ?: "Admin"),
        role = data.optString("role", previous?.role ?: SessionManager.ADMIN_ROLE)
    )

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.nullableDateTime(key: String): LocalDateTime? {
        val raw = nullableString(key) ?: return null
        return runCatching {
            LocalDateTime.parse(raw.replace('T', ' ').take(23), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        }.getOrNull()
    }

    companion object {
        @Volatile private var instance: PaymentObserverApi? = null

        fun getInstance(context: android.content.Context): PaymentObserverApi = instance ?: synchronized(this) {
            instance ?: PaymentObserverApi(SessionManager.getInstance(context)).also { instance = it }
        }
    }
}
