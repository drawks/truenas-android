package com.drawks.truenasandroid.core.model

sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Loading : ConnectionStatus
    data class Success(val info: InstanceInfo) : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}
