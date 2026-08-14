package com.novara.app.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.novara.app.Config
import com.novara.app.network.ApiClient
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: (redirect: String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Novara", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; errorText = null },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorText = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        errorText?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    errorText = "Enter both username and password."
                } else {
                    loading = true
                    scope.launch {
                        when (val result = ApiClient.login(username.trim(), password)) {
                            is ApiClient.ApiResult.Success -> onLoggedIn(result.data.redirect)
                            is ApiClient.ApiResult.Failure -> errorText = result.message
                        }
                        loading = false
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Logging in…" else "Log in") }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                loading = true
                scope.launch {
                    when (val result = ApiClient.guestLogin()) {
                        is ApiClient.ApiResult.Success -> onLoggedIn(result.data.redirect)
                        is ApiClient.ApiResult.Failure -> errorText = result.message
                    }
                    loading = false
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continue as Guest") }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                val url = Uri.parse(Config.WEB_BASE_URL + Config.GOOGLE_LOGIN_PATH + "?mobile=1")
                CustomTabsIntent.Builder().build().launchUrl(context, url)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continue with Google") }
    }
}
