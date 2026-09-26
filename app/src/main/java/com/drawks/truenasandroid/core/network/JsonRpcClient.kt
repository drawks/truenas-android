package com.drawks.truenasandroid.core.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface JsonRpcClient {
    suspend fun call(profileUrl: String, method: String, token: String, params: List<JsonElement> = emptyList()): JsonElement
}

class OkHttpJsonRpcClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : JsonRpcClient {

    override suspend fun call(profileUrl: String, method: String, token: String, params: List<JsonElement>): JsonElement {
        val requestId = UUID.randomUUID().toString()
        val requestBody = json.encodeToString(JsonRpcRequest(id = requestId, method = method, params = params))
        val request = Request.Builder()
            .url(profileUrl)
            .addHeader("Authorization", "Bearer ".plus(token))
            .build()

        val latch = CountDownLatch(1)
        var responsePayload: String? = null
        var failure: Throwable? = null

        val socket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(requestBody)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                responsePayload = text
                latch.countDown()
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failure = t
                latch.countDown()
            }
        })

        val completed = latch.await(15, TimeUnit.SECONDS)
        socket.cancel()

        if (!completed) {
            throw IllegalStateException("Timed out waiting for TrueNAS response")
        }

        failure?.let { throw it }

        val payload = responsePayload ?: throw IllegalStateException("No response received from TrueNAS server")
        val parsed = try {
            json.decodeFromString(JsonRpcResponse.serializer(), payload)
        } catch (serializationException: SerializationException) {
            throw IllegalStateException("Unable to parse JSON-RPC response", serializationException)
        }

        parsed.error?.let { error ->
            throw IllegalStateException("${error.code}: ${error.message}")
        }

        return parsed.result ?: JsonObject(mapOf("raw" to JsonPrimitive(payload)))
    }
}

fun buildTrueNasSocketUrl(host: String, port: Int, useTls: Boolean): String {
    val scheme = if (useTls) "wss" else "ws"
    return "$scheme://$host:$port/api/current"
}

fun parseInstanceInfo(result: JsonElement): Triple<String, String, String> {
    val obj = result as? JsonObject ?: return Triple("Unknown", "Unknown", "Unknown")
    val hostname = (obj["hostname"] as? JsonPrimitive)?.contentOrNull ?: "Unknown"
    val version = (obj["version"] as? JsonPrimitive)?.contentOrNull
        ?: (obj["buildtime"] as? JsonPrimitive)?.contentOrNull
        ?: "Unknown"
    val state = (obj["state"] as? JsonPrimitive)?.contentOrNull ?: "Unknown"
    return Triple(hostname, version, state)
}
