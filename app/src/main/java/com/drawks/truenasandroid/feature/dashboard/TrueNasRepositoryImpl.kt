package com.drawks.truenasandroid.feature.dashboard

import com.drawks.truenasandroid.core.model.ConnectionProfile
import com.drawks.truenasandroid.core.model.ConnectionStatus
import com.drawks.truenasandroid.core.model.InstanceInfo
import com.drawks.truenasandroid.core.network.JsonRpcClient
import com.drawks.truenasandroid.core.network.buildTrueNasSocketUrl
import com.drawks.truenasandroid.core.network.parseInstanceInfo
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonNull

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

        validate(profile)

        val url = buildTrueNasSocketUrl(profile.host, profile.port, profile.useTls)

        val response = runCatching {
            jsonRpcClient.call(url, SYSTEM_INFO_PRIMARY_METHOD, profile.apiToken, listOf(JsonNull))
        }.recoverCatching {
            jsonRpcClient.call(url, SYSTEM_INFO_FALLBACK_METHOD, profile.apiToken)
        }.getOrElse { throwable ->
            emit(ConnectionStatus.Error(throwable.message ?: "Unknown connection error"))
            return@flow
        }

        val (hostname, version, state) = parseInstanceInfo(response)
        emit(ConnectionStatus.Success(InstanceInfo(hostname, version, state)))
    }

    private fun validate(profile: ConnectionProfile) {
        require(profile.host.isNotBlank()) { "Host is required" }
        require(profile.apiToken.isNotBlank()) { "API token is required" }
        require(profile.port in 1..65535) { "Port must be between 1 and 65535" }
    }

    private companion object {
        const val SYSTEM_INFO_PRIMARY_METHOD = "system.info"
        const val SYSTEM_INFO_FALLBACK_METHOD = "system.general.summary"
    }
}
