package com.drawks.truenasandroid.feature.dashboard

import com.drawks.truenasandroid.core.model.ConnectionProfile
import com.drawks.truenasandroid.core.model.ConnectionStatus
import kotlinx.coroutines.flow.Flow

interface TrueNasRepository {
    fun connect(profile: ConnectionProfile): Flow<ConnectionStatus>
}
