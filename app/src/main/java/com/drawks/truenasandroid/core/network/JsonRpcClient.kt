package com.drawks.truenasandroid.core.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface JsonRpcClient {
    suspend fun call(profileUrl: String, method: String, token: String, params: List<JsonElement> = emptyList()): JsonElement
}

class JsonRpcException(
    val code: Int,
    message: String,
) : IllegalStateException("${code}: $message")

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

        return withTimeout(15_000L) {
            suspendCancellableCoroutine { continuation ->
                lateinit var socket: WebSocket
                continuation.invokeOnCancellation {
                    if (::socket.isInitialized) {
                        socket.cancel()
                    }
                }
                socket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(requestBody)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (!continuation.isActive) return
                        val parsed = try {
                            json.decodeFromString(JsonRpcResponse.serializer(), text)
                        } catch (serializationException: SerializationException) {
                            continuation.resumeWithException(
                                IllegalStateException("Unable to parse JSON-RPC response", serializationException)
                            )
                            socket.cancel()
                            return
                        }

                        if (parsed.id != requestId) return

                        parsed.error?.let { error ->
                            continuation.resumeWithException(JsonRpcException(error.code, error.message))
                            socket.close(1000, null)
                            return
                        }

                        continuation.resume(parsed.result ?: JsonObject(mapOf("raw" to JsonPrimitive(text))))
                        socket.close(1000, null)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(t)
                        }
                    }
                })
            }
        }
    }
}

fun buildTrueNasSocketUrl(host: String, port: Int, useTls: Boolean): String {
    val scheme = if (useTls) "wss" else "ws"
    val normalizedHost = if (host.contains(":") && !host.startsWith("[") && !host.endsWith("]")) {
        "[$host]"
    } else {
        host
    }
    return "$scheme://$normalizedHost:$port/api/current"
}

fun parseInstanceInfo(result: JsonElement): Triple<String, String, String> {
    val obj = result as? JsonObject ?: return Triple("Unknown", "Unknown", "Unknown")
    val hostname = (obj["hostname"] as? JsonPrimitive)?.contentOrNull ?: "Unknown"
    val version = (obj["version"] as? JsonPrimitive)?.contentOrNull
        ?: (obj["product_version"] as? JsonPrimitive)?.contentOrNull
        ?: (obj["release"] as? JsonPrimitive)?.contentOrNull
        ?: "Unknown"
    val state = (obj["state"] as? JsonPrimitive)?.contentOrNull ?: "Unknown"
    return Triple(hostname, version, state)
}
