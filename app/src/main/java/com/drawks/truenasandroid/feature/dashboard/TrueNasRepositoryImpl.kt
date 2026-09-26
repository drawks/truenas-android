package com.drawks.truenasandroid.feature.dashboard

import com.drawks.truenasandroid.core.model.ConnectionProfile
import com.drawks.truenasandroid.core.model.ConnectionStatus
import com.drawks.truenasandroid.core.model.InstanceInfo
import com.drawks.truenasandroid.core.network.JsonRpcClient
import com.drawks.truenasandroid.core.network.JsonRpcException
import com.drawks.truenasandroid.core.network.buildTrueNasSocketUrl
import com.drawks.truenasandroid.core.network.parseInstanceInfo
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TrueNasRepositoryImpl @Inject constructor(
    private val jsonRpcClient: JsonRpcClient,
) : TrueNasRepository {

    override fun connect(profile: ConnectionProfile): Flow<ConnectionStatus> = flow {
        emit(ConnectionStatus.Loading)
        if (profile.mockMode) {
            delay(300)
            emit(
                ConnectionStatus.Success(
                    InstanceInfo(
                        hostname = "mock-truenas.local",
                        version = "SCALE-MOCK-1.0",
                        state = "READY",
                    )
                )
            )
            return@flow
        }

        runCatching { validate(profile) }.getOrElse { validationFailure ->
            emit(ConnectionStatus.Error(validationFailure.message ?: "Invalid connection profile"))
            return@flow
        }

        val url = runCatching {
            buildTrueNasSocketUrl(profile.host, profile.port, profile.useTls)
        }.getOrElse { throwable ->
            emit(ConnectionStatus.Error(throwable.message ?: "Unknown connection error"))
            return@flow
        }

        val response = runCatching {
            jsonRpcClient.call(url, SYSTEM_INFO_PRIMARY_METHOD, profile.username, profile.apiToken)
        }.recoverCatching { primaryFailure ->
            if (primaryFailure is JsonRpcException && primaryFailure.code == METHOD_NOT_FOUND_CODE) {
                jsonRpcClient.call(url, SYSTEM_INFO_FALLBACK_METHOD, profile.username, profile.apiToken)
            } else {
                throw primaryFailure
            }
        }.getOrElse { throwable ->
            emit(ConnectionStatus.Error(throwable.message ?: "Unknown connection error"))
            return@flow
        }

        emit(ConnectionStatus.Success(parseInstanceInfo(response)))
    }

    private fun validate(profile: ConnectionProfile) {
        require(profile.host.isNotBlank()) { "Host is required" }
        require(profile.username.isNotBlank()) { "Username is required" }
        require(profile.apiToken.isNotBlank()) { "API token is required" }
        require(profile.port in 1..65535) { "Port must be between 1 and 65535" }
    }

    private companion object {
        const val SYSTEM_INFO_PRIMARY_METHOD = "system.info"
        const val SYSTEM_INFO_FALLBACK_METHOD = "system.general.summary"
        const val METHOD_NOT_FOUND_CODE = -32601
    }
}
