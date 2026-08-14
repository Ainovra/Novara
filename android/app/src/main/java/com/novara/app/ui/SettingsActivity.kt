@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.novara.app.ui

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
