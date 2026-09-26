package com.drawks.truenasandroid.feature.connection

import com.drawks.truenasandroid.core.model.ConnectionProfile
import com.drawks.truenasandroid.core.model.ConnectionStatus
import com.drawks.truenasandroid.core.model.InstanceInfo
import com.drawks.truenasandroid.core.storage.ConnectionProfileStore
import com.drawks.truenasandroid.feature.dashboard.TrueNasRepository
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun connect_updatesUiWithSuccess() = runTest {
        val lastSaved = AtomicReference<ConnectionProfile?>(null)
        val store = object : ConnectionProfileStoreFake() {
            override suspend fun saveProfile(profile: ConnectionProfile) {
                lastSaved.set(profile)
            }
        }
        val repository = object : TrueNasRepository {
            override fun connect(profile: ConnectionProfile): Flow<ConnectionStatus> = flow {
                emit(ConnectionStatus.Loading)
                emit(ConnectionStatus.Success(InstanceInfo("nas.local", "24.10", "READY")))
            }
        }

        val viewModel = ConnectionViewModel(store, repository)
        viewModel.onHostChanged("nas.local")
        viewModel.onPortChanged("443")
        viewModel.onApiTokenChanged("token")

        viewModel.connect()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.status)
            .isEqualTo(ConnectionStatus.Success(InstanceInfo("nas.local", "24.10", "READY")))
        assertThat(lastSaved.get()?.host).isEqualTo("nas.local")
    }

    @Test
    fun connect_defaultsInvalidPortTo443() = runTest {
        val savedProfile = AtomicReference<ConnectionProfile?>(null)
        val repoProfile = AtomicReference<ConnectionProfile?>(null)
        val store = object : ConnectionProfileStoreFake() {
            override suspend fun saveProfile(profile: ConnectionProfile) {
                savedProfile.set(profile)
            }
        }
        val repository = object : TrueNasRepository {
            override fun connect(profile: ConnectionProfile): Flow<ConnectionStatus> = flow {
                repoProfile.set(profile)
                emit(ConnectionStatus.Success(InstanceInfo("nas.local", "24.10", "READY")))
            }
        }

        val viewModel = ConnectionViewModel(store, repository)
        viewModel.onHostChanged("nas.local")
        viewModel.onPortChanged("abc")
        viewModel.onApiTokenChanged("token")
        viewModel.connect()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(savedProfile.get()?.port).isEqualTo(443)
        assertThat(repoProfile.get()?.port).isEqualTo(443)
    }

    @Test
    fun connect_secondAttemptReplacesFirstInFlightRequest() = runTest {
        val store = ConnectionProfileStoreFake()
        val repository = object : TrueNasRepository {
            override fun connect(profile: ConnectionProfile): Flow<ConnectionStatus> = flow {
                emit(ConnectionStatus.Loading)
                if (profile.host == "first") {
                    delay(5_000)
                    emit(ConnectionStatus.Success(InstanceInfo("first", "old", "READY")))
                } else {
                    emit(ConnectionStatus.Success(InstanceInfo("second", "new", "READY")))
                }
            }
        }

        val viewModel = ConnectionViewModel(store, repository)
        viewModel.onApiTokenChanged("token")
        viewModel.onHostChanged("first")
        viewModel.connect()

        dispatcher.scheduler.advanceTimeBy(100)

        viewModel.onHostChanged("second")
        viewModel.connect()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.status)
            .isEqualTo(ConnectionStatus.Success(InstanceInfo("second", "new", "READY")))
    }
}

open class ConnectionProfileStoreFake : ConnectionProfileStore {
    override suspend fun loadProfile(): ConnectionProfile = ConnectionProfile()
    override suspend fun saveProfile(profile: ConnectionProfile) = Unit
}
