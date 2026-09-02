@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.novara.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novara.app.BuildConfig
import com.novara.app.Config
import com.novara.app.ui.theme.NovaraTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NovaraTheme {
                SettingsApp(
                    onBack = { finish() }
                )
            }
        }
    }
}

private enum class SettingsPage {
    HOME,
    AI,
    CAPABILITIES,
    VOICE,
    APPEARANCE,
    PRIVACY,
    MEMORY,
    NOTIFICATIONS,
    ACCOUNT,
    HELP
}

@Composable
private fun SettingsApp(
    onBack: () -> Unit
) {
    var page by remember {
        mutableStateOf(SettingsPage.HOME)
    }

    androidx.activity.compose.BackHandler(enabled = page != SettingsPage.HOME) {
        page = SettingsPage.HOME
    }

    when (page) {
        SettingsPage.HOME -> SettingsHome(
            onBack = onBack,
            open = { page = it }
        )

        SettingsPage.AI -> SubPage(
            title = "AI & Models",
            onBack = { page = SettingsPage.HOME }
        ) {
            AiSettings()
        }

        SettingsPage.CAPABILITIES -> SubPage(
            title = "Capabilities",
            onBack = { page = SettingsPage.HOME }
        ) {
            CapabilitySettings()
        }

        SettingsPage.VOICE -> SubPage(
            title = "Voice & Language",
            onBack = { page = SettingsPage.HOME }
        ) {
            VoiceSettings()
        }

        SettingsPage.APPEARANCE -> SubPage(
            title = "Appearance",
            onBack = { page = SettingsPage.HOME }
        ) {
            AppearanceSettings()
        }

        SettingsPage.PRIVACY -> SubPage(
            title = "Privacy",
            onBack = { page = SettingsPage.HOME }
        ) {
            PrivacySettings()
        }

        SettingsPage.MEMORY -> SubPage(
            title = "Memory",
            onBack = { page = SettingsPage.HOME }
        ) {
            MemorySettings()
        }

        SettingsPage.NOTIFICATIONS -> SubPage(
            title = "Notifications",
            onBack = { page = SettingsPage.HOME }
        ) {
            NotificationSettings()
        }

        SettingsPage.ACCOUNT -> SubPage(
            title = "Account",
            onBack = { page = SettingsPage.HOME }
        ) {
            AccountSettings()
        }

        SettingsPage.HELP -> SubPage(
            title = "Help & About",
            onBack = { page = SettingsPage.HOME }
        ) {
            HelpSettings()
        }
    }
}

@Composable
private fun SettingsHome(
    onBack: () -> Unit,
    open: (SettingsPage) -> Unit
) {
    var search by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                Spacer(Modifier.height(4.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(18.dp)
                                ),
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint =
                                    MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Novara",
                                style =
                                    MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "Free plan",
                                color =
                                    MaterialTheme.colorScheme
                                        .onPrimaryContainer
                            )
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null
                        )
                    },
                    placeholder = {
                        Text("Search settings")
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }

            item {
                SettingsSectionTitle("AI & MODELS")
            }

            item {
                SettingsCard {
                    SettingsNavigationRow(
                        Icons.Default.Memory,
                        "AI & Models",
                        "Models, image generation and tools"
                    ) {
                        open(SettingsPage.AI)
                    }
                }
            }

            item {
                SettingsSectionTitle("CAPABILITIES")
            }

            item {
                SettingsCard {
                    SettingsNavigationRow(
                        Icons.Default.Tune,
                        "Capabilities",
                        "Web search, code, files and tools"
                    ) {
                        open(SettingsPage.CAPABILITIES)
                    }
                }
            }

            item {
                SettingsSectionTitle("VOICE & LANGUAGE")
            }

            item {
                SettingsCard {
                    SettingsNavigationRow(
                        Icons.Default.RecordVoiceOver,
                        "Voice & Language",
                        "Language, speech and voice controls"
                    ) {
                        open(SettingsPage.VOICE)
                    }
                }
            }

            item {
                SettingsSectionTitle("PERSONALIZATION")
            }

            item {
                SettingsCard {
                    SettingsNavigationRow(
                        Icons.Default.Palette,
                        "Appearance",
                        "Theme and appearance preferences"
                    ) {
                        open(SettingsPage.APPEARANCE)
                    }

                    SettingsDivider()

                    SettingsNavigationRow(
                        Icons.Default.Memory,
                        "Memory",
                        "Control what Novara remembers"
                    ) {
                        open(SettingsPage.MEMORY)
                    }

                    SettingsDivider()

                    SettingsNavigationRow(
                        Icons.Default.Notifications,
                        "Notifications",
                        "Notification preferences"
                    ) {
                        open(SettingsPage.NOTIFICATIONS)
                    }
                }
            }

            item {
                SettingsSectionTitle("PRIVACY & SECURITY")
            }

            item {
                SettingsCard {
                    SettingsNavigationRow(
                        Icons.Default.PrivacyTip,
                        "Privacy",
                        "Incognito, retention and AI data"
                    ) {
                        open(SettingsPage.PRIVACY)
                    }
                }
            }

            item {
                SettingsSectionTitle("ACCOUNT")
            }

            item {
                SettingsCard {
                    SettingsNavigationRow(
                        Icons.Default.Person,
                        "Account",
                        "Profile, subscription and account actions"
                    ) {
                        open(SettingsPage.ACCOUNT)
                    }

                    SettingsDivider()

                    SettingsNavigationRow(
                        Icons.Default.Help,
                        "Help & About",
                        "Help, privacy and information about Novara"
                    ) {
                        open(SettingsPage.HELP)
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))

                Text(
                    "Novara ${BuildConfig.VERSION_NAME}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style =
                        MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(
    title: String
) {
    Text(
        title,
        modifier = Modifier.padding(
            start = 4.dp,
            top = 6.dp,
            bottom = 2.dp
        ),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = content
                )
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onChange
        )
    }
}

@Composable
private fun SettingChoice(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    SettingsNavigationRow(
        Icons.Default.Settings,
        title,
        value,
        onClick
    )
}

@Composable
private fun AiSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var model by remember {
        mutableStateOf(
            NovaraSettings.getModel(context)
        )
    }

    var imageModel by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "image_model",
                "Default"
            )
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionTitle("CHAT MODEL")

        SettingsCard {
            SettingChoice(
                "Chat model",
                when (model) {
                    "thinking" -> "Thinking"
                    "omega" -> "Omega"
                    else -> "Fast"
                }
            ) {
                val next =
                    when (model) {
                        "fast" -> "thinking"
                        "thinking" -> "omega"
                        else -> "fast"
                    }

                model = next
                NovaraSettings.setModel(context, next)
            }
        }

        SettingsSectionTitle("IMAGE GENERATION")

        SettingsCard {
            SettingChoice(
                "Image generation model",
                imageModel
            ) {
                imageModel =
                    if (imageModel == "Default")
                        "FLUX.1-dev"
                    else
                        "Default"

                NovaraSettings.setString(
                    context,
                    "image_model",
                    imageModel
                )
            }
        }

        SettingsSectionTitle("MODEL BEHAVIOUR")

        SettingsCard {
            SettingSwitch(
                "Switch models when flagged",
                "Automatically move to a safer model when needed.",
                NovaraSettings.get(
                    context,
                    "switch_flagged_model",
                    true
                )
            ) {
                NovaraSettings.set(
                    context,
                    "switch_flagged_model",
                    it
                )
            }
        }
    }
}

@Composable
private fun CapabilitySettings() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var web by remember {
        mutableStateOf(
            NovaraSettings.get(context, "web_search", false)
        )
    }

    var code by remember {
        mutableStateOf(
            NovaraSettings.get(context, "code_execution", false)
        )
    }

    var files by remember {
        mutableStateOf(
            NovaraSettings.get(context, "files", true)
        )
    }

    var tools by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "tool_access",
                "Auto"
            )
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionTitle("AI TOOLS")

        SettingsCard {
            SettingSwitch(
                "Web search",
                "Allow Novara to search the web.",
                web
            ) {
                web = it
                NovaraSettings.set(
                    context,
                    "web_search",
                    it
                )
            }

            SettingsDivider()

            SettingSwitch(
                "Code execution",
                "Allow code execution and file creation.",
                code
            ) {
                code = it
                NovaraSettings.set(
                    context,
                    "code_execution",
                    it
                )
            }

            SettingsDivider()

            SettingSwitch(
                "Files & attachments",
                "Allow files and attachments in conversations.",
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

        SettingsSectionTitle("TOOL ACCESS")

        SettingsCard {
            SettingChoice(
                "Tool access",
                tools
            ) {
                tools =
                    when (tools) {
                        "Auto" -> "On demand"
                        "On demand" -> "Always available"
                        else -> "Auto"
                    }

                NovaraSettings.setString(
                    context,
                    "tool_access",
                    tools
                )
            }
        }
    }
}

@Composable
private fun VoiceSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var voice by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "voice_assistant",
                true
            )
        )
    }

    var language by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "language",
                "Automatic"
            )
        )
    }

    var style by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "voice_style",
                "Kiran"
            )
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionTitle("VOICE")

        SettingsCard {
            SettingSwitch(
                "Voice assistant",
                "Enable voice controls in chat.",
                voice
            ) {
                voice = it
                NovaraSettings.set(
                    context,
                    "voice_assistant",
                    it
                )
            }

            SettingsDivider()

            SettingChoice(
                "Voice style",
                style
            ) {
                style =
                    when (style) {
                        "Kiran" -> "Buttery"
                        "Buttery" -> "Air"
                        else -> "Kiran"
                    }

                NovaraSettings.setString(
                    context,
                    "voice_style",
                    style
                )
            }
        }

        SettingsSectionTitle("LANGUAGE")

        SettingsCard {
            SettingChoice(
                "Assistant language",
                language
            ) {
                language =
                    when (language) {
                        "Automatic" -> "English (India)"
                        "English (India)" -> "English (US)"
                        "English (US)" -> "Hindi"
                        else -> "Automatic"
                    }

                NovaraSettings.setString(
                    context,
                    "language",
                    language
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var theme by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "theme",
                "System"
            )
        )
    }

    var font by remember {
        mutableStateOf(
            NovaraSettings.getString(
                context,
                "font",
                "Default"
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

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionTitle("THEME")

        SettingsCard {
            SettingChoice(
                "Color mode",
                theme
            ) {
                theme =
                    when (theme) {
                        "System" -> "Light"
                        "Light" -> "Dark"
                        else -> "System"
                    }

                NovaraSettings.setString(
                    context,
                    "theme",
                    theme
                )
            }
        }

        SettingsSectionTitle("TEXT")

        SettingsCard {
            SettingChoice(
                "Font style",
                font
            ) {
                font =
                    when (font) {
                        "Default" -> "Inter"
                        "Inter" -> "System"
                        "System" -> "Serif"
                        else -> "Default"
                    }

                NovaraSettings.setString(
                    context,
                    "font",
                    font
                )
            }
        }

        SettingsSectionTitle("FEEDBACK")

        SettingsCard {
            SettingSwitch(
                "Haptic feedback",
                "Use vibration for supported interactions.",
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
    }
}

@Composable
private fun PrivacySettings() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var incognito by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "incognito_mode",
                false
            )
        )
    }

    var retention by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "ai_data_retention",
                true
            )
        )
    }

    var improve by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "improve_ai_model",
                false
            )
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionTitle("PRIVACY")

        SettingsCard {
            SettingSwitch(
                "Incognito mode",
                "Use privacy-focused local preferences.",
                incognito
            ) {
                incognito = it
                NovaraSettings.set(
                    context,
                    "incognito_mode",
                    it
                )
            }

            SettingsDivider()

            SettingSwitch(
                "AI data retention",
                "Control AI-related retention preferences.",
                retention
            ) {
                retention = it
                NovaraSettings.set(
                    context,
                    "ai_data_retention",
                    it
                )
            }

            SettingsDivider()

            SettingSwitch(
                "Help improve Novara",
                "Allow this preference to be used for future improvement controls.",
                improve
            ) {
                improve = it
                NovaraSettings.set(
                    context,
                    "improve_ai_model",
                    it
                )
            }
        }

        SettingsSectionTitle("LEGAL")

        SettingsCard {
            SettingsNavigationRow(
                Icons.Default.PrivacyTip,
                "Privacy policy",
                "Read Novara's privacy information"
            ) {
                openUrl(
                    context,
                    Config.WEB_BASE_URL + "/privacy"
                )
            }
        }
    }
}

@Composable
private fun MemorySettings() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var memory by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "memory",
                true
            )
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionTitle("MEMORY")

        SettingsCard {
            SettingSwitch(
                "Memory",
                "Allow Novara to remember useful information.",
                memory
            ) {
                memory = it
                NovaraSettings.set(
                    context,
                    "memory",
                    it
                )
            }

            SettingsDivider()

            SettingsNavigationRow(
                Icons.Default.Folder,
                "Your memory",
                "View and manage remembered information"
            ) {
                context.startActivity(
                    Intent(
                        context,
                        MemoryActivity::class.java
                    )
                )
            }
        }
    }
}

@Composable
private fun NotificationSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var notifications by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "notifications",
                true
            )
        )
    }

    Column {
        SettingsCard {
            SettingSwitch(
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
    }
}

@Composable
private fun AccountSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionTitle("ACCOUNT")

        SettingsCard {
            SettingsNavigationRow(
                Icons.Default.Person,
                "Profile",
                "Manage your Novara profile"
            ) {
                context.startActivity(Intent(context, ProfileActivity::class.java))
            }

            SettingsDivider()

            SettingsNavigationRow(
                Icons.Default.Settings,
                "Billing",
                "Free plan"
            ) {
                context.startActivity(Intent(context, BillingActivity::class.java))

                        }
                        SettingsDivider()

                        SettingsNavigationRow(
                            Icons.Default.VideoLibrary,
                            "Video Studio",
                            "Create and generate AI videos"
                        ) {
                            context.startActivity(
                                Intent(context, VideoStudioActivity::class.java)
                            )
                        }
            }

        SettingsSectionTitle("ACCOUNT ACTIONS")

        SettingsCard {
            SettingsNavigationRow(
                Icons.Default.ArrowBack,
                "Log out",
                "Sign out of Novara"
            ) {
                kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.Dispatchers.Main
                ).launch {
                    when (
                        val result =
                            com.novara.app.network.ApiClient.logout()
                    ) {
                        is com.novara.app.network.ApiClient.ApiResult.Success -> {
                            context.getSharedPreferences(
                                "novara_prefs",
                                Context.MODE_PRIVATE
                            ).edit()
                                .putBoolean("logged_in", false)
                                .apply()

                            toast(
                                context,
                                "Logged out successfully."
                            )

                            context.startActivity(
                                Intent(
                                    context,
                                    com.novara.app.MainActivity::class.java
                                ).apply {
                                    flags =
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                        }

                        is com.novara.app.network.ApiClient.ApiResult.Failure -> {
                            toast(context, result.message)
                        }
                    }
                }
            }

            SettingsDivider()

            SettingsNavigationRow(
                Icons.Default.Delete,
                "Delete account",
                "Request permanent account deletion"
            ) {
                context.startActivity(
                    Intent(context, WebViewActivity::class.java).apply {
                        putExtra("path", "/account/delete-request")
                    }
                )
            }
        }
    }
}

@Composable
private fun HelpSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionTitle("HELP")

        SettingsCard {
            SettingsNavigationRow(
                Icons.Default.Help,
                "Help & FAQ",
                "Get help with Novara"
            ) {
                openUrl(
                    context,
                    Config.WEB_BASE_URL + "/welcome"
                )
            }

            SettingsDivider()

            SettingsNavigationRow(
                Icons.Default.Cloud,
                "Privacy",
                "Read Novara privacy information"
            ) {
                openUrl(
                    context,
                    Config.WEB_BASE_URL + "/privacy"
                )
            }
        }

        SettingsSectionTitle("ABOUT")

        SettingsCard {
            SettingsNavigationRow(
                Icons.Default.Info,
                "About Novara",
                "Version ${BuildConfig.VERSION_NAME}"
            ) {
                toast(
                    context,
                    "Novara ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                )
            }
        }
    }
}

private fun openUrl(
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
        toast(context, "Could not open this page.")
    }
}

private fun toast(
    context: Context,
    message: String
) {
    android.widget.Toast.makeText(
        context,
        message,
        android.widget.Toast.LENGTH_SHORT
    ).show()
}
