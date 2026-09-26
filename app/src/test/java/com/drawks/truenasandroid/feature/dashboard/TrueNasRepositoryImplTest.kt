package com.drawks.truenasandroid.feature.dashboard

import com.drawks.truenasandroid.core.model.ConnectionProfile
import com.drawks.truenasandroid.core.model.ConnectionStatus
import com.drawks.truenasandroid.core.model.InstanceInfo
import com.drawks.truenasandroid.core.network.JsonRpcClient
import com.drawks.truenasandroid.core.network.JsonRpcException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class TrueNasRepositoryImplTest {

    @Test
    fun connect_usesPrimaryMethodWhenAvailable() = runTest {
        val client = FakeJsonRpcClient(
            responses = mapOf(
                "system.info" to jsonInfo("nas", "24.10", "READY")
            )
        )

        val emissions = TrueNasRepositoryImpl(client)
            .connect(validProfile())
            .toList()

        assertThat(client.calls).containsExactly("system.info")
        assertThat(emissions.last())
            .isEqualTo(ConnectionStatus.Success(InstanceInfo("nas", "24.10", "READY")))
    }

    @Test
    fun connect_fallsBackWhenPrimaryMethodMissing() = runTest {
        val client = FakeJsonRpcClient(
            failures = mapOf("system.info" to JsonRpcException(-32601, "Method not found")),
            responses = mapOf(
                "system.general.summary" to jsonInfo("fallback", "25.04", "READY")
            )
        )

        val emissions = TrueNasRepositoryImpl(client)
            .connect(validProfile())
            .toList()

        assertThat(client.calls).containsExactly("system.info", "system.general.summary").inOrder()
        assertThat(emissions.last())
            .isEqualTo(ConnectionStatus.Success(InstanceInfo("fallback", "25.04", "READY")))
    }

    @Test
    fun connect_doesNotFallbackForTransportErrors() = runTest {
        val client = FakeJsonRpcClient(
            failures = mapOf("system.info" to IllegalStateException("socket closed"))
        )

        val emissions = TrueNasRepositoryImpl(client)
            .connect(validProfile())
            .toList()

        assertThat(client.calls).containsExactly("system.info")
        assertThat(emissions).containsExactly(
            ConnectionStatus.Loading,
            ConnectionStatus.Error("socket closed"),
        ).inOrder()
    }

    private fun validProfile() = ConnectionProfile(host = "nas.local", apiToken = "token")

    private fun jsonInfo(hostname: String, version: String, state: String) = JsonObject(
        mapOf(
            "hostname" to JsonPrimitive(hostname),
            "version" to JsonPrimitive(version),
            "state" to JsonPrimitive(state),
        )
    )

    private class FakeJsonRpcClient(
        private val responses: Map<String, JsonElement> = emptyMap(),
        private val failures: Map<String, Throwable> = emptyMap(),
    ) : JsonRpcClient {

        val calls = mutableListOf<String>()

        override suspend fun call(
            profileUrl: String,
            method: String,
            token: String,
            params: List<JsonElement>,
        ): JsonElement {
            calls += method
            failures[method]?.let { throw it }
            return responses[method] ?: JsonObject(emptyMap())
        }
    }
}
