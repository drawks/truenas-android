package com.drawks.truenasandroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drawks.truenasandroid.core.model.ConnectionStatus
import com.drawks.truenasandroid.core.model.InstanceInfo
import com.drawks.truenasandroid.feature.connection.ConnectionUiState
import com.drawks.truenasandroid.feature.connection.ConnectionViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val viewModel: ConnectionViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                ConnectionScreen(
                    uiState = uiState,
                    onHostChanged = viewModel::onHostChanged,
                    onPortChanged = viewModel::onPortChanged,
                    onTokenChanged = viewModel::onApiTokenChanged,
                    onTlsChanged = viewModel::onTlsChanged,
                    onMockChanged = viewModel::onMockModeChanged,
                    onConnectClicked = viewModel::connect,
                )
            }
        }
    }
}

@Composable
fun ConnectionScreen(
    uiState: ConnectionUiState,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onTlsChanged: (Boolean) -> Unit,
    onMockChanged: (Boolean) -> Unit,
    onConnectClicked: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("TrueNAS SCALE Connection", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Use WSS whenever possible. Disable TLS only for local testing on trusted networks.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = uiState.profile.host,
                onValueChange = onHostChanged,
                label = { Text("Host or IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.portInput,
                onValueChange = onPortChanged,
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.profile.apiToken,
                onValueChange = onTokenChanged,
                label = { Text("API token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            ToggleRow(label = "Use TLS (recommended)", checked = uiState.profile.useTls, onCheckedChanged = onTlsChanged)
            ToggleRow(label = "Mock mode (no NAS required)", checked = uiState.profile.mockMode, onCheckedChanged = onMockChanged)

            Button(onClick = onConnectClicked, modifier = Modifier.fillMaxWidth()) {
                Text("Connect")
            }

            when (val status = uiState.status) {
                ConnectionStatus.Idle -> Text("Ready to connect")
                ConnectionStatus.Loading -> CircularProgressIndicator()
                is ConnectionStatus.Error -> Text(
                    text = "Connection failed: ${status.message}",
                    color = MaterialTheme.colorScheme.error,
                )
                is ConnectionStatus.Success -> DashboardContent(status.info)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChanged: (Boolean) -> Unit) {
    Column {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChanged)
    }
}

@Composable
private fun DashboardContent(info: InstanceInfo) {
    Spacer(modifier = Modifier.height(8.dp))
    Text("Connected", style = MaterialTheme.typography.titleLarge)
    Text("Hostname: ${info.hostname}")
    Text("Version: ${info.version}")
    Text("State: ${info.state}")
}

@Preview(showBackground = true)
@Composable
private fun ConnectionScreenPreview() {
    MaterialTheme {
        ConnectionScreen(
            uiState = ConnectionUiState(
                status = ConnectionStatus.Success(
                    InstanceInfo("mock-truenas.local", "SCALE-MOCK-1.0", "READY")
                )
            ),
            onHostChanged = {},
            onPortChanged = {},
            onTokenChanged = {},
            onTlsChanged = {},
            onMockChanged = {},
            onConnectClicked = {},
        )
    }
}
