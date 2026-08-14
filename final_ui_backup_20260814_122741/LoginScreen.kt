package com.novara.app.ui

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
import androidx.compose.runtime.*
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

    var busy by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    fun runAuth(action: suspend () -> ApiClient.ApiResult<ApiClient.AuthResponse>) {
        if (busy) return

        if (
            username.trim().isEmpty() ||
            password.isEmpty()
        ) {
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Image(
                painter = painterResource(
                    R.drawable.novara_logo
                ),
                contentDescription = "Novara",
                modifier = Modifier
                    .size(150.dp)
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

            when (mode) {

                AuthMode.WELCOME -> {

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    Text(
                        "Your personal AI assistant.",
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    Spacer(
                        Modifier.height(26.dp)
                    )

                    Button(
                        onClick = {
                            mode = AuthMode.LOGIN
                            error = null
                        },
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
                            mode = AuthMode.SIGNUP
                            error = null
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text("Create account")
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
                                        error = result.message
                                }

                                busy = false
                            }
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text("Continue as guest")
                    }

                    Spacer(
                        Modifier.height(10.dp)
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
                        }
                    ) {
                        Text("Continue with Google")
                    }
                }

                AuthMode.LOGIN,
                AuthMode.SIGNUP -> {

                    Spacer(
                        Modifier.height(22.dp)
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

                            if (
                                mode ==
                                    AuthMode.LOGIN
                            ) {
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
                            if (
                                mode ==
                                    AuthMode.LOGIN
                            )
                                "Log in"
                            else
                                "Create account"
                        )
                    }

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    TextButton(
                        onClick = {
                            mode = AuthMode.WELCOME
                            error = null
                        }
                    ) {
                        Text("Back")
                    }

                    TextButton(
                        onClick = {
                            mode =
                                if (
                                    mode ==
                                        AuthMode.LOGIN
                                )
                                    AuthMode.SIGNUP
                                else
                                    AuthMode.LOGIN

                            error = null
                        }
                    ) {
                        Text(
                            if (
                                mode ==
                                    AuthMode.LOGIN
                            )
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
