package com.drawks.truenasandroid.core.network

import com.drawks.truenasandroid.core.model.InstanceInfo
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class JsonRpcClientUtilsTest {

    @Test
    fun jsonRpcRequest_usesTrueNasMsgEnvelope() {
        val encoded = Json.encodeToString(JsonRpcRequest(id = "1", method = "system.info"))

        assertThat(encoded).contains("\"msg\":\"method\"")
        assertThat(encoded).doesNotContain("jsonrpc")
    }

    @Test
    fun buildTrueNasSocketUrl_formatsHostnameAndPort() {
        val url = buildTrueNasSocketUrl(host = "nas.local", port = 443, useTls = true)

        assertThat(url).isEqualTo("wss://nas.local:443/api/current")
    }

    @Test
    fun buildTrueNasSocketUrl_wrapsIpv6Host() {
        val url = buildTrueNasSocketUrl(host = "2001:db8::1", port = 8443, useTls = false)

        assertThat(url).isEqualTo("ws://[2001:db8::1]:8443/api/current")
    }

    @Test
    fun buildTrueNasSocketUrl_preservesBracketedIpv6Host() {
        val url = buildTrueNasSocketUrl(host = "[2001:db8::1]", port = 8443, useTls = true)

        assertThat(url).isEqualTo("wss://[2001:db8::1]:8443/api/current")
    }

    @Test
    fun buildTrueNasSocketUrl_handlesHostInputWithSchemeAndPath() {
        val url = buildTrueNasSocketUrl(host = "https://nas.local/api", port = 443, useTls = true)

        assertThat(url).isEqualTo("wss://nas.local:443/api/current")
    }

    @Test
    fun buildTrueNasSocketUrl_stripsPortFromHostInput() {
        val ipv4Url = buildTrueNasSocketUrl(host = "nas.local:8443", port = 443, useTls = true)
        val ipv6Url = buildTrueNasSocketUrl(host = "[2001:db8::1]:8443", port = 443, useTls = true)

        assertThat(ipv4Url).isEqualTo("wss://nas.local:443/api/current")
        assertThat(ipv6Url).isEqualTo("wss://[2001:db8::1]:443/api/current")
    }

    @Test
    fun parseInstanceInfo_usesVersionFallbackFields() {
        val fromProductVersion = parseInstanceInfo(
            JsonObject(
                mapOf(
                    "hostname" to JsonPrimitive("nas"),
                    "product_version" to JsonPrimitive("25.04"),
                    "state" to JsonPrimitive("READY"),
                )
            )
        )
        val fromRelease = parseInstanceInfo(
            JsonObject(
                mapOf(
                    "hostname" to JsonPrimitive("nas"),
                    "release" to JsonPrimitive("24.10"),
                    "state" to JsonPrimitive("READY"),
                )
            )
        )

        assertThat(fromProductVersion).isEqualTo(InstanceInfo("nas", "25.04", "READY"))
        assertThat(fromRelease).isEqualTo(InstanceInfo("nas", "24.10", "READY"))
    }

    @Test
    fun parseInstanceInfo_handlesMissingOrInvalidShapes() {
        val fromMissingFields = parseInstanceInfo(JsonObject(emptyMap()))
        val fromArrayPayload = parseInstanceInfo(JsonArray(emptyList()))

        assertThat(fromMissingFields).isEqualTo(InstanceInfo("Unknown", "Unknown", "Unknown"))
        assertThat(fromArrayPayload).isEqualTo(InstanceInfo("Unknown", "Unknown", "Unknown"))
    }

    @Test
    fun jsonRpcResponse_authRequiresExplicitSuccess() {
        val success = JsonRpcResponse(
            msg = "result",
            result = JsonObject(mapOf("response_type" to JsonPrimitive("SUCCESS"))),
        )
        val missingEnvelope = JsonRpcResponse(
            result = JsonObject(mapOf("response_type" to JsonPrimitive("SUCCESS"))),
        )
        val missingResponseType = JsonRpcResponse(
            msg = "result",
            result = JsonObject(mapOf("user_info" to JsonPrimitive("ignored"))),
        )
        val authError = JsonRpcResponse(
            msg = "result",
            result = JsonObject(mapOf("response_type" to JsonPrimitive("AUTH_ERR"))),
        )
        val wrongEnvelope = JsonRpcResponse(
            msg = "failed",
            result = JsonObject(mapOf("response_type" to JsonPrimitive("SUCCESS"))),
        )

        assertThat(success.isSuccessfulAuthResponse()).isTrue()
        assertThat(missingEnvelope.isSuccessfulAuthResponse()).isFalse()
        assertThat(missingResponseType.isSuccessfulAuthResponse()).isFalse()
        assertThat(authError.isSuccessfulAuthResponse()).isFalse()
        assertThat(authError.authFailureMessage()).isEqualTo("TrueNAS authentication failed: AUTH_ERR")
        assertThat(wrongEnvelope.isSuccessfulAuthResponse()).isFalse()
    }
}
