package com.drawks.truenasandroid.core.model

import kotlinx.serialization.Serializable

@Serializable
data class InstanceInfo(
    val hostname: String,
    val version: String,
    val state: String,
)
