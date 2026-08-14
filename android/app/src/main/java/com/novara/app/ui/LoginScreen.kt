package com.novara.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.novara.app.Config
import com.novara.app.R
import com.novara.app.network.ApiClient
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private enum class AuthMode {
    WELCOME,
    LOGIN,
    SIGNUP
}

@Composable
fun LoginScreen(
    onLoggedIn: (String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember {
        mutableStateOf(AuthMode.WELCOME)
    }

    var username by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var busy by remember {
        mutableStateOf(false)
    }

    fun runAuth(
        action: suspend () -> ApiClient.ApiResult<ApiClient.AuthResponse>
    ) {
        if (busy) return

        val cleanUser = username.trim()

        if (cleanUser.isBlank() || password.isBlank()) {
            error = "Please enter username and password."
            return
        }

        busy = true
        error = null

        scope.launch {
            when (val result = action()) {
                is ApiClient.ApiResult.Success -> {
                    onLoggedIn(
                        result.data.redirect ?: "/app"
                    )
                }

                is ApiClient.ApiResult.Failure -> {
                    error = result.message
                }
            }

            busy = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Image(
                painter = painterResource(
                    R.drawable.novara_logo
                ),
                contentDescription = "Novara",
                modifier = Modifier
                    .size(120.dp)
                    .clip(
                        RoundedCornerShape(24.dp)
                    ),
                contentScale = ContentScale.Crop
            )

            Spacer(
                Modifier.height(18.dp)
            )

            Text(
                "Novara",
                style =
                    MaterialTheme.typography.headlineLarge
            )

            Spacer(
                Modifier.height(6.dp)
            )

            when (mode) {

                AuthMode.WELCOME -> {

                    Text(
                        "Welcome to Novara",
                        style =
                            MaterialTheme.typography.titleLarge
                    )

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    Text(
                        "Your personal AI assistant.",
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    Spacer(
                        Modifier.height(24.dp)
                    )

                    Button(
                        onClick = {
                            error = null
                            mode = AuthMode.LOGIN
                        },
                        enabled = !busy,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text("Log in")
                    }

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            error = null
                            mode = AuthMode.SIGNUP
                        },
                        enabled = !busy,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text("Sign up")
                    }

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            if (busy) return@OutlinedButton

                            busy = true
                            error = null

                            scope.launch {
                                when (
                                    val result =
                                        ApiClient.guestLogin()
                                ) {
                                    is ApiClient.ApiResult.Success ->
                                        onLoggedIn(
                                            result.data.redirect
                                                ?: "/terms"
                                        )

                                    is ApiClient.ApiResult.Failure ->
                                        error =
                                            result.message
                                }

                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text("Continue as guest")
                    }

                    Spacer(
                        Modifier.height(4.dp)
                    )

                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            Config.WEB_BASE_URL +
                                                Config.GOOGLE_LOGIN_PATH
                                        )
                                    )
                                )
                            } catch (_: Exception) {
                                error =
                                    "Google sign-in could not be opened."
                            }
                        },
                        enabled = !busy
                    ) {
                        Text("Continue with Google")
                    }
                }

                AuthMode.LOGIN,
                AuthMode.SIGNUP -> {

                    Text(
                        if (mode == AuthMode.LOGIN)
                            "Log in to Novara"
                        else
                            "Create your Novara account",
                        style =
                            MaterialTheme.typography.titleLarge
                    )

                    Spacer(
                        Modifier.height(18.dp)
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text("Username")
                        }
                    )

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation =
                            PasswordVisualTransformation(),
                        label = {
                            Text("Password")
                        }
                    )

                    Spacer(
                        Modifier.height(16.dp)
                    )

                    Button(
                        onClick = {
                            if (mode == AuthMode.LOGIN) {
                                runAuth {
                                    ApiClient.login(
                                        username.trim(),
                                        password
                                    )
                                }
                            } else {
                                runAuth {
                                    ApiClient.signup(
                                        username.trim(),
                                        password
                                    )
                                }
                            }
                        },
                        enabled = !busy,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (mode == AuthMode.LOGIN)
                                "Log in"
                            else
                                "Create account"
                        )
                    }

                    TextButton(
                        onClick = {
                            error = null
                            mode = AuthMode.WELCOME
                        },
                        enabled = !busy
                    ) {
                        Text("Back to Welcome")
                    }

                    TextButton(
                        onClick = {
                            error = null
                            mode =
                                if (mode == AuthMode.LOGIN)
                                    AuthMode.SIGNUP
                                else
                                    AuthMode.LOGIN
                        },
                        enabled = !busy
                    ) {
                        Text(
                            if (mode == AuthMode.LOGIN)
                                "Need an account? Sign up"
                            else
                                "Already have an account? Log in"
                        )
                    }
                }
            }

            error?.let {
                Spacer(
                    Modifier.height(14.dp)
                )

                Text(
                    it,
                    color =
                        MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
