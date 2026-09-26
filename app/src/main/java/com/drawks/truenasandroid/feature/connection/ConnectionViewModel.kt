package com.drawks.truenasandroid.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drawks.truenasandroid.core.model.ConnectionProfile
import com.drawks.truenasandroid.core.model.ConnectionStatus
import com.drawks.truenasandroid.core.storage.ConnectionProfileStore
import com.drawks.truenasandroid.feature.dashboard.TrueNasRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val profileStore: ConnectionProfileStore,
    private val trueNasRepository: TrueNasRepository,
) : ViewModel() {

    private var connectJob: Job? = null
    private var hasLocalEdits: Boolean = false

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileStore.loadProfile()
            _uiState.update { state ->
                if (hasLocalEdits) {
                    state
                } else {
                    state.copy(profile = profile, portInput = profile.port.toString())
                }
            }
        }
    }

    fun onHostChanged(value: String) = updateProfile { copy(host = value) }
    fun onUsernameChanged(value: String) = updateProfile { copy(username = value) }
    fun onPortChanged(value: String) {
        hasLocalEdits = true
        val sanitized = value.filter { it.isDigit() }
        _uiState.update {
            it.copy(
                portInput = sanitized,
                profile = it.profile.copy(port = parsePortInput(sanitized) ?: it.profile.port),
                status = ConnectionStatus.Idle,
            )
        }
    }

    fun onApiTokenChanged(value: String) = updateProfile { copy(apiToken = value) }
    fun onTlsChanged(value: Boolean) = updateProfile { copy(useTls = value) }
    fun onMockModeChanged(value: Boolean) = updateProfile { copy(mockMode = value) }

    fun connect() {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            val effectiveProfile = _uiState.value.profile.copy(
                port = parsePortInput(_uiState.value.portInput) ?: DEFAULT_PORT,
            )
            runCatching { profileStore.saveProfile(effectiveProfile) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(status = ConnectionStatus.Error(error.message ?: "Unable to save profile"))
                    }
                    return@launch
                }
            trueNasRepository.connect(effectiveProfile).collect { status ->
                _uiState.update { it.copy(status = status, profile = effectiveProfile) }
            }
        }
    }

    private fun updateProfile(block: ConnectionProfile.() -> ConnectionProfile) {
        hasLocalEdits = true
        _uiState.update { state ->
            val updatedProfile = state.profile.block()
            state.copy(
                profile = updatedProfile,
                status = ConnectionStatus.Idle,
            )
        }
    }

    private companion object {
        const val DEFAULT_PORT = 443

        fun parsePortInput(input: String): Int? {
            val parsed = input.toLongOrNull() ?: return null
            if (parsed < 1L || parsed > 65535L) return null
            return parsed.toInt()
        }
    }
}

data class ConnectionUiState(
    val profile: ConnectionProfile = ConnectionProfile(),
    val portInput: String = profile.port.toString(),
    val status: ConnectionStatus = ConnectionStatus.Idle,
)
