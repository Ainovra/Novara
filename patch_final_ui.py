from pathlib import Path
import re
import shutil
import sys

ROOT = Path.home() / "Novara" / "android"
JAVA = ROOT / "app" / "src" / "main" / "java" / "com" / "novara" / "app"
UI = JAVA / "ui"
SCREENS = UI / "screens"

chat_path = SCREENS / "ChatScreen.kt"
login_path = UI / "LoginScreen.kt"
settings_path = UI / "NovaraSettings.kt"
activity_path = JAVA / "ui" / "SettingsActivity.kt"
manifest_path = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
strings_path = ROOT / "app" / "src" / "main" / "res" / "values" / "strings.xml"

def fail(msg):
    print("PATCH ERROR:", msg)
    sys.exit(1)

# ----------------------------------------------------------
# 1. LOGIN / WELCOME / SIGNUP
# ----------------------------------------------------------

login_code = r'''package com.novara.app.ui

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
'''

login_path.write_text(login_code, encoding="utf-8")

# ----------------------------------------------------------
# 2. SETTINGS STORAGE
# ----------------------------------------------------------

settings_code = r'''package com.novara.app.ui

import android.content.Context

object NovaraSettings {

    private const val PREFS = "novara_settings"

    fun get(
        context: Context,
        key: String,
        default: Boolean
    ): Boolean =
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                key,
                default
            )

    fun set(
        context: Context,
        key: String,
        value: Boolean
    ) {
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                key,
                value
            )
            .apply()
    }

    fun getString(
        context: Context,
        key: String,
        default: String
    ): String =
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                key,
                default
            ) ?: default

    fun setString(
        context: Context,
        key: String,
        value: String
    ) {
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                key,
                value
            )
            .apply()
    }

    fun getModel(
        context: Context
    ): String =
        getString(
            context,
            "model",
            "fast"
        )

    fun setModel(
        context: Context,
        value: String
    ) {
        setString(
            context,
            "model",
            value
        )
    }
}
'''

settings_path.write_text(settings_code, encoding="utf-8")

# ----------------------------------------------------------
# 3. FULL SETTINGS ACTIVITY
# ----------------------------------------------------------

settings_activity = r'''package com.novara.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.novara.app.BuildConfig
import com.novara.app.Config
import com.novara.app.ui.theme.NovaraTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {
            NovaraTheme {
                NovaraSettingsScreen(
                    onBack = {
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun NovaraSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var webSearch by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "web_search",
                true
            )
        )
    }

    var imageGen by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "image_gen",
                true
            )
        )
    }

    var artifacts by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "artifacts",
                false
            )
        )
    }

    var codeExecution by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "code_execution",
                false
            )
        )
    }

    var switchFlagged by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "switch_flagged_model",
                true
            )
        )
    }

    var memory by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "memory",
                true
            )
        )
    }

    var haptic by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "haptic",
                true
            )
        )
    }

    var voice by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "voice_assistant",
                true
            )
        )
    }

    var files by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "files",
                true
            )
        )
    }

    var notifications by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "notifications",
                true
            )
        )
    }

    var aiRetention by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "ai_data_retention",
                true
            )
        )
    }

    var incognito by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "incognito_mode",
                false
            )
        )
    }

    var improveAi by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "improve_ai_model",
                false
            )
        )
    }

    var assistantEnabled by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "assistant_enabled",
                true
            )
        )
    }

    var model by remember {
        mutableStateOf(
            NovaraSettings.getModel(
                context
            )
        )
    }

    var imageModel by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "image_generation_model",
                "Default"
            )
        )
    }

    var theme by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "color_mode",
                "System"
            )
        )
    }

    var font by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "font_style",
                "Default"
            )
        )
    }

    var language by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "assistant_language",
                "Automatic (detect input)"
            )
        )
    }

    var speechLanguage by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "speech_language",
                "System language"
            )
        )
    }

    var voiceStyle by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "voice_style",
                "Kiran"
            )
        )
    }

    var voiceMode by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "voice_mode",
                "Hands-free"
            )
        )
    }

    var toolAccess by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "tool_access",
                "Auto"
            )
        )
    }

    var choiceTitle by remember {
        mutableStateOf<String?>(null)
    }

    var choiceValue by remember {
        mutableStateOf("")
    }

    var choiceOptions by remember {
        mutableStateOf(emptyList<String>())
    }

    fun openChoice(
        title: String,
        current: String,
        options: List<String>
    ) {
        choiceTitle = title
        choiceValue = current
        choiceOptions = options
    }

    fun saveChoice(
        value: String
    ) {
        when (choiceTitle) {
            "Color mode" -> {
                theme = value
                NovaraSettings.setString(
                    context,
                    "color_mode",
                    value
                )
            }

            "Font style" -> {
                font = value
                NovaraSettings.setString(
                    context,
                    "font_style",
                    value
                )
            }

            "Assistant language" -> {
                language = value
                NovaraSettings.setString(
                    context,
                    "assistant_language",
                    value
                )
            }

            "Speech recognition" -> {
                speechLanguage = value
                NovaraSettings.setString(
                    context,
                    "speech_language",
                    value
                )
            }

            "Voice style" -> {
                voiceStyle = value
                NovaraSettings.setString(
                    context,
                    "voice_style",
                    value
                )
            }

            "Voice mode" -> {
                voiceMode = value
                NovaraSettings.setString(
                    context,
                    "voice_mode",
                    value
                )
            }

            "Tool access" -> {
                toolAccess = value
                NovaraSettings.setString(
                    context,
                    "tool_access",
                    value
                )
            }

            "Image Generation Model" -> {
                imageModel = value
                NovaraSettings.setString(
                    context,
                    "image_generation_model",
                    value
                )
            }

            "Chat model" -> {
                val id =
                    when (value) {
                        "Fast" -> "fast"
                        "Thinking" -> "thinking"
                        "Omega" -> "omega"
                        else -> "fast"
                    }

                model = id
                NovaraSettings.setModel(
                    context,
                    id
                )
            }
        }

        choiceTitle = null
        choiceOptions = emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription =
                                "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showInfo(context)
                        }
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription =
                                "Information"
                        )
                    }
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(
                    horizontal = 16.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(4.dp)
        ) {

            item {
                SettingsHeader(
                    "Account"
                )
            }

            item {
                SettingsRow(
                    "Profile",
                    "Account profile",
                    enabled = true
                ) {
                    toast(
                        context,
                        "Profile management uses your Novara account."
                    )
                }
            }

            item {
                SettingsRow(
                    "Billing",
                    "Free plan",
                    enabled = true
                ) {
                    toast(
                        context,
                        "Billing is managed by your Novara account."
                    )
                }
            }

            item {
                SettingsHeader(
                    "Capabilities"
                )
            }

            item {
                SettingsToggle(
                    "Web search",
                    "Let Novara search the web when needed.",
                    webSearch
                ) {
                    webSearch = it
                    if (it) imageGen = false
                    NovaraSettings.set(
                        context,
                        "web_search",
                        it
                    )
                    if (it) {
                        NovaraSettings.set(
                            context,
                            "image_gen",
                            false
                        )
                    }
                }
            }

            item {
                SettingsToggle(
                    "Artifacts",
                    "Required by code execution.",
                    artifacts,
                    enabled = codeExecution
                ) {
                    artifacts = it
                    NovaraSettings.set(
                        context,
                        "artifacts",
                        it
                    )
                }
            }

            item {
                SettingsToggle(
                    "Code execution and file creation",
                    "Allow Novara to execute code and create files.",
                    codeExecution
                ) {
                    codeExecution = it
                    if (!it) artifacts = false

                    NovaraSettings.set(
                        context,
                        "code_execution",
                        it
                    )

                    NovaraSettings.set(
                        context,
                        "artifacts",
                        artifacts
                    )
                }
            }

            item {
                SettingsToggle(
                    "Switch models when a message is flagged",
                    "Automatically switch to a safer model when enabled.",
                    switchFlagged
                ) {
                    switchFlagged = it
                    NovaraSettings.set(
                        context,
                        "switch_flagged_model",
                        it
                    )
                }
            }

            item {
                SettingsHeader(
                    "Memory"
                )
            }

            item {
                SettingsToggle(
                    "Memory",
                    "Let Novara remember useful information.",
                    memory
                ) {
                    memory = it
                    NovaraSettings.set(
                        context,
                        "memory",
                        it
                    )
                }
            }

            item {
                SettingsRow(
                    "Your memory files",
                    "View and manage what Novara remembers.",
                    enabled = memory
                ) {
                    toast(
                        context,
                        "Memory management is ready for the connected backend."
                    )
                }
            }

            item {
                ChoiceRow(
                    title = "Tool access",
                    value = toolAccess
                ) {
                    openChoice(
                        "Tool access",
                        toolAccess,
                        listOf(
                            "Auto",
                            "On demand",
                            "Always available"
                        )
                    )
                }
            }

            item {
                SettingsHeader(
                    "Assistant"
                )
            }

            item {
                SettingsToggle(
                    "Enable assistant",
                    "Enable Novara assistant features.",
                    assistantEnabled
                ) {
                    assistantEnabled = it
                    NovaraSettings.set(
                        context,
                        "assistant_enabled",
                        it
                    )
                }
            }

            item {
                SettingsRow(
                    "How to access",
                    "Chat, voice and assistant controls."
                ) {
                    toast(
                        context,
                        "Use the Novara chat and microphone controls."
                    )
                }
            }

            item {
                SettingsRow(
                    "Permissions",
                    "Manage Android permissions."
                ) {
                    try {
                        context.startActivity(
                            Intent(
                                android.provider.Settings
                                    .ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse(
                                    "package:" +
                                        context.packageName
                                )
                            )
                        )
                    } catch (_: Exception) {
                        toast(
                            context,
                            "Android settings could not be opened."
                        )
                    }
                }
            }

            item {
                ChoiceRow(
                    title = "Assistant language",
                    value = language
                ) {
                    openChoice(
                        "Assistant language",
                        language,
                        listOf(
                            "Automatic (detect input)",
                            "English (India)",
                            "English (US)",
                            "Hindi"
                        )
                    )
                }
            }

            item {
                ChoiceRow(
                    title = "Speech recognition",
                    value = speechLanguage
                ) {
                    openChoice(
                        "Speech recognition",
                        speechLanguage,
                        listOf(
                            "System language",
                            "English (India)",
                            "English (US)",
                            "Hindi"
                        )
                    )
                }
            }

            item {
                ChoiceRow(
                    title = "Voice style",
                    value = voiceStyle
                ) {
                    openChoice(
                        "Voice style",
                        voiceStyle,
                        listOf(
                            "Kiran",
                            "Buttery",
                            "Air"
                        )
                    )
                }
            }

            item {
                ChoiceRow(
                    title = "Voice mode",
                    value = voiceMode
                ) {
                    openChoice(
                        "Voice mode",
                        voiceMode,
                        listOf(
                            "Hands-free",
                            "Press to talk"
                        )
                    )
                }
            }

            item {
                SettingsHeader(
                    "Profile & Personalize"
                )
            }

            item {
                ChoiceRow(
                    title = "Chat model",
                    value =
                        when (model) {
                            "thinking" -> "Thinking"
                            "omega" -> "Omega"
                            else -> "Fast"
                        }
                ) {
                    openChoice(
                        "Chat model",
                        when (model) {
                            "thinking" -> "Thinking"
                            "omega" -> "Omega"
                            else -> "Fast"
                        },
                        listOf(
                            "Fast",
                            "Thinking",
                            "Omega"
                        )
                    )
                }
            }

            item {
                ChoiceRow(
                    title = "Image Generation Model",
                    value = imageModel
                ) {
                    openChoice(
                        "Image Generation Model",
                        imageModel,
                        listOf(
                            "Default",
                            "FLUX.1-dev"
                        )
                    )
                }
            }

            item {
                SettingsRow(
                    "Personalize",
                    "Customize Novara for you."
                ) {
                    toast(
                        context,
                        "Personalization settings saved locally."
                    )
                }
            }

            item {
                SettingsRow(
                    "Connectors",
                    "Connect services when supported."
                ) {
                    toast(
                        context,
                        "No external connectors configured yet."
                    )
                }
            }

            item {
                SettingsHeader(
                    "Appearance"
                )
            }

            item {
                ChoiceRow(
                    title = "Color mode",
                    value = theme
                ) {
                    openChoice(
                        "Color mode",
                        theme,
                        listOf(
                            "System",
                            "Light",
                            "Dark"
                        )
                    )
                }
            }

            item {
                ChoiceRow(
                    title = "Font style",
                    value = font
                ) {
                    openChoice(
                        "Font style",
                        font,
                        listOf(
                            "Default",
                            "Inter",
                            "System",
                            "Serif"
                        )
                    )
                }
            }

            item {
                SettingsToggle(
                    "Haptic feedback",
                    "Use vibration feedback for supported actions.",
                    haptic
                ) {
                    haptic = it
                    NovaraSettings.set(
                        context,
                        "haptic",
                        it
                    )
                }
            }

            item {
                SettingsToggle(
                    "Voice assistant",
                    "Enable voice input controls in chat.",
                    voice
                ) {
                    voice = it
                    NovaraSettings.set(
                        context,
                        "voice_assistant",
                        it
                    )
                }
            }

            item {
                SettingsToggle(
                    "Files & attachments",
                    "Allow file and attachment tools.",
                    files
                ) {
                    files = it
                    NovaraSettings.set(
                        context,
                        "files",
                        it
                    )
                }
            }

            item {
                SettingsHeader(
                    "Privacy & Behaviour"
                )
            }

            item {
                SettingsToggle(
                    "Incognito Mode",
                    "Use a privacy-focused local preference.",
                    incognito
                ) {
                    incognito = it
                    NovaraSettings.set(
                        context,
                        "incognito_mode",
                        it
                    )
                }
            }

            item {
                SettingsToggle(
                    "Notifications",
                    "Allow Novara notification preferences.",
                    notifications
                ) {
                    notifications = it
                    NovaraSettings.set(
                        context,
                        "notifications",
                        it
                    )
                }
            }

            item {
                SettingsToggle(
                    "AI Data Retention",
                    "Control whether AI-related retention is enabled.",
                    aiRetention
                ) {
                    aiRetention = it
                    NovaraSettings.set(
                        context,
                        "ai_data_retention",
                        it
                    )
                }
            }

            item {
                SettingsToggle(
                    "Help us improve Novara's AI model",
                    "Allow this preference to be remembered for future AI improvement controls.",
                    improveAi
                ) {
                    improveAi = it
                    NovaraSettings.set(
                        context,
                        "improve_ai_model",
                        it
                    )
                }
            }

            item {
                SettingsRow(
                    "Time & focus",
                    "Focus-related preferences."
                ) {
                    toast(
                        context,
                        "Time & focus controls are not connected to the backend yet."
                    )
                }
            }

            item {
                SettingsRow(
                    "Privacy",
                    "Read Novara privacy information."
                ) {
                    openWeb(
                        context,
                        Config.WEB_BASE_URL +
                            "/privacy"
                    )
                }
            }

            item {
                SettingsRow(
                    "Shared links",
                    "Manage shared conversations."
                ) {
                    toast(
                        context,
                        "Shared links are managed from conversation actions."
                    )
                }
            }

            item {
                SettingsHeader(
                    "Help Center"
                )
            }

            item {
                SettingsRow(
                    "Get started",
                    "Welcome to Novara."
                ) {
                    openWeb(
                        context,
                        Config.WEB_BASE_URL +
                            "/welcome"
                    )
                }
            }

            item {
                SettingsRow(
                    "What is Pro Search",
                    "Learn about web-search style tools."
                ) {
                    toast(
                        context,
                        "Novara web search is enabled from chat."
                    )
                }
            }

            item {
                SettingsRow(
                    "Help & FAQ",
                    "Get help with Novara."
                ) {
                    toast(
                        context,
                        "Help center is available from the Novara website."
                    )
                }
            }

            item {
                SettingsHeader(
                    "Account"
                )
            }

            item {
                SettingsRow(
                    "Logout",
                    "Sign out of Novara."
                ) {
                    openWeb(
                        context,
                        Config.WEB_BASE_URL +
                            "/logout"
                    )
                    onBack()
                }
            }

            item {
                SettingsRow(
                    "Delete Account",
                    "Request permanent account deletion."
                ) {
                    openWeb(
                        context,
                        Config.WEB_BASE_URL +
                            "/account/delete-request"
                    )
                }
            }

            item {
                Text(
                    "App version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    modifier =
                        Modifier.padding(
                            vertical = 18.dp
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }
        }
    }

    choiceTitle?.let { title ->

        AlertDialog(
            onDismissRequest = {
                choiceTitle = null
                choiceOptions = emptyList()
            },
            title = {
                Text(title)
            },
            text = {
                Column {
                    choiceOptions.forEach { option ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        saveChoice(
                                            option
                                        )
                                    }
                                    .padding(
                                        vertical = 6.dp
                                    ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected =
                                    choiceValue ==
                                        option,
                                onClick = {
                                    saveChoice(
                                        option
                                    )
                                }
                            )

                            Spacer(
                                Modifier.width(8.dp)
                            )

                            Text(option)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        choiceTitle = null
                        choiceOptions = emptyList()
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsHeader(
    title: String
) {
    Text(
        title,
        modifier =
            Modifier.padding(
                top = 18.dp,
                bottom = 6.dp
            ),
        style =
            MaterialTheme.typography.titleMedium,
        color =
            MaterialTheme
                .colorScheme
                .primary
    )
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 7.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            modifier =
                Modifier.weight(1f)
        ) {
            Text(
                title,
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge
            )

            Text(
                subtitle,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )
        }

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(
                    vertical = 9.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            modifier =
                Modifier.weight(1f)
        ) {
            Text(
                title,
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge
            )

            Text(
                subtitle,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )
        }

        Text(
            "›",
            style =
                MaterialTheme
                    .typography
                    .headlineSmall
        )
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    SettingsRow(
        title = title,
        subtitle = value,
        onClick = onClick
    )
}

private fun toast(
    context: Context,
    message: String
) {
    android.widget.Toast
        .makeText(
            context,
            message,
            android.widget.Toast.LENGTH_SHORT
        )
        .show()
}

private fun openWeb(
    context: Context,
    url: String
) {
    try {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )
        )
    } catch (_: Exception) {
        toast(
            context,
            "Could not open this page."
        )
    }
}

private fun showInfo(
    context: Context
) {
    android.widget.Toast
        .makeText(
            context,
            "Novara settings",
            android.widget.Toast.LENGTH_SHORT
        )
        .show()
}
'''

activity_path.write_text(
    settings_activity,
    encoding="utf-8"
)

# ----------------------------------------------------------
# 4. PATCH CHAT SCREEN, KEEPING EXISTING CODE
# ----------------------------------------------------------

chat = chat_path.read_text(encoding="utf-8")

if "import com.novara.app.ui.SettingsActivity" not in chat:
    marker = "import com.novara.app.ui.NovaraSettings"
    if marker not in chat:
        fail("NovaraSettings import marker missing from ChatScreen.kt")
    chat = chat.replace(
        marker,
        marker + "\nimport com.novara.app.ui.SettingsActivity",
        1
    )

# Exact model list
model_pattern = re.compile(
    r"private val MODEL_OPTIONS\s*=\s*listOf\(.*?\n\)\n\nprivate fun cleanNovaraText",
    re.S
)

model_replacement = '''private val MODEL_OPTIONS =
    listOf(
        ModelOption("fast", "Fast", "⚡"),
        ModelOption("thinking", "Thinking", "💭"),
        ModelOption("omega", "Omega", "🧠")
    )

private fun cleanNovaraText'''

chat, count = model_pattern.subn(
    model_replacement,
    chat,
    count=1
)

if count != 1:
    fail("MODEL_OPTIONS block could not be safely patched")

# Stronger markdown cleanup
clean_pattern = re.compile(
    r"private fun cleanNovaraText\(raw: String\): String \{.*?\n\}\n\n@OptIn",
    re.S
)

clean_replacement = r'''private fun cleanNovaraText(raw: String): String {

    return raw
        .replace(
            Regex("(?m)^\\s*#{1,6}\\s*"),
            ""
        )
        .replace(
            Regex("(?m)^\\s*\\*{3,}\\s*$"),
            ""
        )
        .replace(
            Regex("(?m)^\\s*_{3,}\\s*$"),
            ""
        )
        .replace(
            "**",
            ""
        )
        .replace(
            "__",
            ""
        )
        .replace(
            Regex("(?m)^\\s*\\*\\s+"),
            "• "
        )
        .replace(
            Regex("(?m)^\\s*-\\s+"),
            "• "
        )
        .replace(
            Regex("(?m)^[#*]+\\s*$"),
            ""
        )
        .trim()
}

@OptIn'''

chat, count = clean_pattern.subn(
    clean_replacement,
    chat,
    count=1
)

if count != 1:
    fail("cleanNovaraText block could not be safely patched")

# Open the new full Settings screen instead of the old small dialog.
old_settings_click = '''onClick = {
                        settingsOpen = true
                    },'''

new_settings_click = '''onClick = {
                        context.startActivity(
                            Intent(
                                context,
                                SettingsActivity::class.java
                            )
                        )
                    },'''

if old_settings_click not in chat:
    fail("Settings button marker not found")

chat = chat.replace(
    old_settings_click,
    new_settings_click,
    1
)

chat_path.write_text(
    chat,
    encoding="utf-8"
)

# ----------------------------------------------------------
# 5. MANIFEST
# ----------------------------------------------------------

manifest = manifest_path.read_text(
    encoding="utf-8"
)

activity_line = '''
        <activity
            android:name=".ui.SettingsActivity"
            android:exported="false" />
'''

if 'android:name=".ui.SettingsActivity"' not in manifest:
    marker = '''        <activity
            android:name=".MainActivity"'''
    if marker not in manifest:
        fail("MainActivity manifest marker missing")
    manifest = manifest.replace(
        marker,
        activity_line + "\n" + marker,
        1
    )

manifest_path.write_text(
    manifest,
    encoding="utf-8"
)

# ----------------------------------------------------------
# 6. APP NAME
# ----------------------------------------------------------

strings = strings_path.read_text(
    encoding="utf-8"
)

if "<string name=\"app_name\">" in strings:
    strings = re.sub(
        r'<string name="app_name">.*?</string>',
        '<string name="app_name">Novara</string>',
        strings,
        count=1,
        flags=re.S
    )
else:
    strings = strings.replace(
        "<resources>",
        '<resources>\n    <string name="app_name">Novara</string>',
        1
    )

strings_path.write_text(
    strings,
    encoding="utf-8"
)

print("PATCH COMPLETE")
print("LoginScreen: Welcome + Login + Signup + Guest + Google")
print("Chat models: Fast / Thinking / Omega")
print("Markdown cleanup: enabled")
print("Full SettingsActivity: added")
print("Manifest: SettingsActivity added")
print("App label: Novara")
