package com.drawks.truenasandroid.di

import com.drawks.truenasandroid.core.network.JsonRpcClient
import com.drawks.truenasandroid.core.network.OkHttpJsonRpcClient
import com.drawks.truenasandroid.core.storage.ConnectionProfileStore
import com.drawks.truenasandroid.core.storage.EncryptedConnectionProfileStore
import com.drawks.truenasandroid.feature.dashboard.TrueNasRepository
import com.drawks.truenasandroid.feature.dashboard.TrueNasRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideJsonRpcClient(
        okHttpClient: OkHttpClient,
        json: Json,
    ): JsonRpcClient = OkHttpJsonRpcClient(okHttpClient, json)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConnectionProfileStore(
        impl: EncryptedConnectionProfileStore,
    ): ConnectionProfileStore

    @Binds
    @Singleton
    abstract fun bindTrueNasRepository(impl: TrueNasRepositoryImpl): TrueNasRepository
}
