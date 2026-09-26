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

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileStore.loadProfile()
            _uiState.update { it.copy(profile = profile, portInput = profile.port.toString()) }
        }
    }

    fun onHostChanged(value: String) = updateProfile { copy(host = value) }
    fun onPortChanged(value: String) {
        val sanitized = value.filter { it.isDigit() }
        _uiState.update {
            it.copy(
                portInput = sanitized,
                profile = it.profile.copy(port = sanitized.toIntOrNull() ?: DEFAULT_PORT),
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
                port = _uiState.value.portInput.toIntOrNull() ?: DEFAULT_PORT,
            )
            profileStore.saveProfile(effectiveProfile)
            trueNasRepository.connect(effectiveProfile).collect { status ->
                _uiState.update { it.copy(status = status, profile = effectiveProfile) }
            }
        }
    }

    private fun updateProfile(block: ConnectionProfile.() -> ConnectionProfile) {
        _uiState.update { state ->
            val updatedProfile = state.profile.block()
            state.copy(
                profile = updatedProfile,
            )
        }
    }

    private companion object {
        const val DEFAULT_PORT = 443
    }
}

data class ConnectionUiState(
    val profile: ConnectionProfile = ConnectionProfile(),
    val portInput: String = profile.port.toString(),
    val status: ConnectionStatus = ConnectionStatus.Idle,
)
