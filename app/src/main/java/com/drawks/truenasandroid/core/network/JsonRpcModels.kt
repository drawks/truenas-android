package com.drawks.truenasandroid.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcRequest(
    val id: String,
    val method: String,
    val params: List<JsonElement> = emptyList(),
    val jsonrpc: String = "2.0",
)

@Serializable
data class TrueNasApiRequest(
    val id: String,
    val method: String,
    val params: List<JsonElement> = emptyList(),
    val msg: String = "method",
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
)

@Serializable
data class JsonRpcResponse(
    val msg: String? = null,
    val id: String? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
    @SerialName("response_type") val responseType: String? = null,
    @SerialName("jsonrpc") val jsonRpcVersion: String? = null,
)
