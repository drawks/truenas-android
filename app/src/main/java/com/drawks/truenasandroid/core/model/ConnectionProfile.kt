package com.drawks.truenasandroid.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionProfile(
    val host: String = "",
    val port: Int = 443,
    val apiToken: String = "",
    val useTls: Boolean = true,
    val mockMode: Boolean = false,
)
