package com.novara.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novara.app.network.ApiClient
import com.novara.app.ui.theme.NovaraTheme
import kotlinx.coroutines.launch

class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NovaraTheme {
                ProfileScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(onBack: () -> Unit) {
    var profile by remember { mutableStateOf<ApiClient.UserProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            when (val result = ApiClient.getMe()) {
                is ApiClient.ApiResult.Success -> {
                    profile = result.data
                    loading = false
                }
                is ApiClient.ApiResult.Failure -> {
                    error = result.message
                    loading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            when {
                loading -> {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Text(
                        "Couldn't load profile: $error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                profile != null -> {
                    val p = profile!!
                    ProfileRow("Username", p.username ?: "—")
                    ProfileRow("Email", p.email ?: "Not set")
                    ProfileRow("Phone number", p.phoneNumber ?: "Not set")
                    ProfileRow(
                        "Account type",
                        when {
                            p.isGuest -> "Guest"
                            p.signedInWithGoogle -> "Google account"
                            else -> "Email account"
                        }
                    )
                    ProfileRow("Member since", p.createdAt?.take(10) ?: "—")
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
    Divider()
}
