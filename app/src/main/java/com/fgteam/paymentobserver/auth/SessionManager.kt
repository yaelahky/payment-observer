package com.fgteam.paymentobserver.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Suppress("DEPRECATION")
class SessionManager private constructor(context: Context) {
    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "admin_session_encrypted",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val mutableSession = MutableStateFlow(read())

    val session: StateFlow<AdminSession?> = mutableSession.asStateFlow()

    fun current(): AdminSession? = mutableSession.value

    @Synchronized
    fun save(value: AdminSession) {
        check(value.role == ADMIN_ROLE)
        val committed = preferences.edit()
            .putString(ACCESS_TOKEN, value.accessToken)
            .putString(REFRESH_TOKEN, value.refreshToken)
            .putString(ACCESS_EXPIRES, value.accessTokenExpiresIn)
            .putString(REFRESH_EXPIRES, value.refreshTokenExpiresIn)
            .putLong(ACCESS_EXPIRES_UNIX, value.accessTokenExpiresInUnix)
            .putLong(REFRESH_EXPIRES_UNIX, value.refreshTokenExpiresInUnix)
            .putString(FULL_NAME, value.fullName)
            .putString(ROLE, value.role)
            .commit()
        check(committed) { "Session terenkripsi gagal disimpan" }
        mutableSession.value = value
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().commit()
        mutableSession.value = null
    }

    private fun read(): AdminSession? {
        val accessToken = preferences.getString(ACCESS_TOKEN, null) ?: return null
        val refreshToken = preferences.getString(REFRESH_TOKEN, null) ?: return null
        val role = preferences.getString(ROLE, null)
        if (role != ADMIN_ROLE) {
            preferences.edit().clear().commit()
            return null
        }
        return AdminSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresIn = preferences.getString(ACCESS_EXPIRES, "").orEmpty(),
            refreshTokenExpiresIn = preferences.getString(REFRESH_EXPIRES, "").orEmpty(),
            accessTokenExpiresInUnix = preferences.getLong(ACCESS_EXPIRES_UNIX, 0),
            refreshTokenExpiresInUnix = preferences.getLong(REFRESH_EXPIRES_UNIX, 0),
            fullName = preferences.getString(FULL_NAME, "Admin").orEmpty(),
            role = role
        )
    }

    companion object {
        const val ADMIN_ROLE = "admin"
        private const val ACCESS_TOKEN = "accessToken"
        private const val REFRESH_TOKEN = "refreshToken"
        private const val ACCESS_EXPIRES = "accessTokenExpiresIn"
        private const val REFRESH_EXPIRES = "refreshTokenExpiresIn"
        private const val ACCESS_EXPIRES_UNIX = "accessTokenExpiresInUnix"
        private const val REFRESH_EXPIRES_UNIX = "refreshTokenExpiresInUnix"
        private const val FULL_NAME = "fullName"
        private const val ROLE = "role"

        @Volatile private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager = instance ?: synchronized(this) {
            instance ?: SessionManager(context).also { instance = it }
        }
    }
}
