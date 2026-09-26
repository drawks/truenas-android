package com.drawks.truenasandroid.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionProfile(
    val host: String = "",
    val port: Int = 443,
    val username: String = "",
    val apiToken: String = "",
    val useTls: Boolean = true,
    val mockMode: Boolean = false,
)
