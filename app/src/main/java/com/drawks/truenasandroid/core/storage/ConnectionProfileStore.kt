package com.drawks.truenasandroid.core.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.drawks.truenasandroid.core.model.ConnectionProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ConnectionProfileStore {
    suspend fun loadProfile(): ConnectionProfile
    suspend fun saveProfile(profile: ConnectionProfile)
}

@Singleton
class EncryptedConnectionProfileStore @Inject constructor(
    @ApplicationContext context: Context,
) : ConnectionProfileStore {

    private val sharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun loadProfile(): ConnectionProfile = withContext(Dispatchers.IO) {
        ConnectionProfile(
            host = sharedPreferences.getString(KEY_HOST, "") ?: "",
            port = sharedPreferences.getInt(KEY_PORT, 443),
            username = sharedPreferences.getString(KEY_USERNAME, "") ?: "",
            apiToken = sharedPreferences.getString(KEY_TOKEN, "") ?: "",
            useTls = sharedPreferences.getBoolean(KEY_USE_TLS, true),
            mockMode = sharedPreferences.getBoolean(KEY_MOCK_MODE, false),
        )
    }

    override suspend fun saveProfile(profile: ConnectionProfile) = withContext(Dispatchers.IO) {
        val committed = sharedPreferences.edit()
            .putString(KEY_HOST, profile.host.trim())
            .putInt(KEY_PORT, profile.port)
            .putString(KEY_USERNAME, profile.username.trim())
            .putString(KEY_TOKEN, profile.apiToken.trim())
            .putBoolean(KEY_USE_TLS, profile.useTls)
            .putBoolean(KEY_MOCK_MODE, profile.mockMode)
            .commit()
        check(committed) { "Failed to save connection profile" }
    }

    private companion object {
        const val FILE_NAME = "connection_profile"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_USERNAME = "username"
        const val KEY_TOKEN = "token"
        const val KEY_USE_TLS = "use_tls"
        const val KEY_MOCK_MODE = "mock_mode"
    }
}
