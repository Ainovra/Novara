package com.novara.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.novara.app.network.ApiClient
import com.novara.app.ui.LoginScreen
import com.novara.app.ui.TermsScreen
import com.novara.app.ui.screens.ChatScreen
import com.novara.app.ui.theme.NovaraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var loggedInRedirect by mutableStateOf<String?>(null)
    private var authError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NovaraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val redirect = loggedInRedirect
                    if (redirect != null) {
                        if (redirect == "/terms") {
                            TermsScreen(onAccepted = { loggedInRedirect = "/app" })
                        } else {
                            ChatScreen()
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            authError?.let { Text(it, modifier = Modifier.align(Alignment.CenterHorizontally)) }
                            LoginScreen(onLoggedIn = { r -> loggedInRedirect = r ?: "/app" })
                        }
                    }
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == Config.AUTH_HANDOFF_SCHEME && data.host == Config.AUTH_HANDOFF_HOST) {
            val code = data.getQueryParameter("code") ?: return
            authError = null
            lifecycleScope.launch {
                when (val result = ApiClient.consumeAuthCode(code)) {
                    is ApiClient.ApiResult.Success -> loggedInRedirect = result.data.redirect ?: "/app"
                    is ApiClient.ApiResult.Failure -> authError = result.message
                }
            }
        }
    }
}
