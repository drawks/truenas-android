package com.drawks.truenasandroid.core.network

import com.drawks.truenasandroid.core.model.InstanceInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface JsonRpcClient {
    suspend fun call(
        profileUrl: String,
        method: String,
        username: String,
        apiKey: String,
        params: List<JsonElement> = emptyList(),
    ): JsonElement
}

class JsonRpcException(
    val code: Int,
    message: String,
) : IllegalStateException("${code}: $message")

class OkHttpJsonRpcClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : JsonRpcClient {

    override suspend fun call(
        profileUrl: String,
        method: String,
        username: String,
        apiKey: String,
        params: List<JsonElement>,
    ): JsonElement {
        val requestId = UUID.randomUUID().toString()
        val authRequestId = UUID.randomUUID().toString()
        val authRequestBody = json.encodeToString(
            TrueNasApiRequest(
                id = authRequestId,
                method = AUTH_METHOD,
                params = listOf(
                    buildJsonObject {
                        put("mechanism", AUTH_MECHANISM)
                        put("username", username)
                        put("api_key", apiKey)
                        put(
                            "login_options",
                            buildJsonObject {
                                put("user_info", false)
                                put("reconnect_token", false)
                            }
                        )
                    }
                ),
            )
        )
        val requestBody = json.encodeToString(TrueNasApiRequest(id = requestId, method = method, params = params))
        val request = Request.Builder()
            .url(profileUrl)
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
                        webSocket.send(authRequestBody)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (!continuation.isActive) return
                        val parsed = runCatching {
                            json.decodeFromString(JsonRpcResponse.serializer(), text)
                        }.getOrNull() ?: run {
                            return
                        }

                        if (parsed.id == authRequestId) {
                            parsed.error?.let { error ->
                                continuation.resumeWithException(JsonRpcException(error.code, error.message))
                                socket.close(1000, null)
                                return
                            }
                            if (!parsed.isSuccessfulAuthResponse()) {
                                continuation.resumeWithException(
                                    IllegalStateException(parsed.authFailureMessage() ?: "TrueNAS authentication failed")
                                )
                                socket.close(1000, null)
                                return
                            }
                            socket.send(requestBody)
                            return
                        }
                        if (parsed.id == null || parsed.id != requestId) return

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

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("WebSocket closed before response ($code): $reason")
                            )
                        }
                    }
                })
            }
        }
    }

    private companion object {
        const val AUTH_METHOD = "auth.login_ex"
        const val AUTH_MECHANISM = "API_KEY_PLAIN"
    }
}

internal fun JsonRpcResponse.isSuccessfulAuthResponse(): Boolean {
    if (msg != RESULT_MESSAGE) return false

    val authResult = result as? JsonObject ?: return false
    val responseType = (authResult["response_type"] as? JsonPrimitive)?.contentOrNull
    return responseType == AUTH_SUCCESS_RESPONSE
}

internal fun JsonRpcResponse.authFailureMessage(): String? {
    val responseType = ((result as? JsonObject)?.get("response_type") as? JsonPrimitive)?.contentOrNull
    return responseType?.takeUnless { it == AUTH_SUCCESS_RESPONSE }?.let {
        "TrueNAS authentication failed: $it"
    } ?: error?.message
}

private const val AUTH_SUCCESS_RESPONSE = "SUCCESS"
private const val RESULT_MESSAGE = "result"

fun buildTrueNasSocketUrl(host: String, port: Int, useTls: Boolean): String {
    val scheme = if (useTls) "wss" else "ws"
    val trimmedHost = host.trim()
    val baseHost = if (trimmedHost.contains("://")) {
        URI(trimmedHost).host ?: ""
    } else {
        trimmedHost.substringBefore("/")
    }
    require(baseHost.isNotBlank()) { "Host must be a hostname or IP address" }
    val hostWithoutPort = when {
        baseHost.startsWith("[") -> baseHost.substringBefore("]") + "]"
        baseHost.count { it == ':' } == 1 -> baseHost.substringBefore(":")
        else -> baseHost
    }
    val normalizedHost = if (hostWithoutPort.contains(":") && !hostWithoutPort.startsWith("[") && !hostWithoutPort.endsWith("]")) {
        "[$hostWithoutPort]"
    } else {
        hostWithoutPort
    }
    return "$scheme://$normalizedHost:$port/api/current"
}

fun parseInstanceInfo(result: JsonElement): InstanceInfo {
    val obj = result as? JsonObject
        ?: return InstanceInfo("Unknown", "Unknown", "Unknown")
    val hostname = (obj["hostname"] as? JsonPrimitive)?.contentOrNull ?: "Unknown"
    val version = (obj["version"] as? JsonPrimitive)?.contentOrNull
        ?: (obj["product_version"] as? JsonPrimitive)?.contentOrNull
        ?: (obj["release"] as? JsonPrimitive)?.contentOrNull
        ?: "Unknown"
    val state = (obj["state"] as? JsonPrimitive)?.contentOrNull ?: "Unknown"
    return InstanceInfo(hostname, version, state)
}
