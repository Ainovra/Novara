package com.novara.app.ui.screens

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novara.app.network.ApiClient
import com.novara.app.ui.NovaraSettings
import kotlinx.coroutines.launch
import java.util.Locale

private data class ModelOption(
    val id: String,
    val label: String,
    val icon: String
)

private val MODEL_OPTIONS =
    listOf(
        ModelOption("fast", "Fast", "⚡"),
        ModelOption("thinking", "Thinking", "💭"),
        ModelOption("omega", "Omega", "🧠")
    )

private fun cleanNovaraText(raw: String): String {

    return raw
        .replace(
            Regex("(?m)^\\s*#{1,6}\\s*"),
            ""
        )
        .replace(
            "**",
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
            Regex("(?m)^\\s*_{3,}\\s*$"),
            ""
        )
        .replace(
            Regex("(?m)^[*#]+\\s*$"),
            ""
        )
        .trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val drawerState =
        rememberDrawerState(
            DrawerValue.Closed
        )

    val listState =
        rememberLazyListState()

    var conversations by remember {
        mutableStateOf<List<ApiClient.Conversation>>(
            emptyList()
        )
    }

    var messages by remember {
        mutableStateOf<List<ApiClient.ChatMessage>>(
            emptyList()
        )
    }

    var currentConversation by remember {
        mutableStateOf<String?>(null)
    }

    var input by remember {
        mutableStateOf("")
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

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

    var filesEnabled by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "files",
                true
            )
        )
    }

    var memoryEnabled by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "memory",
                true
            )
        )
    }

    var voiceEnabled by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "voice_assistant",
                true
            )
        )
    }

    var hapticEnabled by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "haptic",
                true
            )
        )
    }

    var artifactsEnabled by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "artifacts",
                false
            )
        )
    }

    var codeExecutionEnabled by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "code_execution",
                false
            )
        )
    }

    var switchFlaggedModel by remember {
        mutableStateOf(
            NovaraSettings.get(
                context,
                "switch_flagged_model",
                true
            )
        )
    }

    var modelId by remember {
        mutableStateOf(
            NovaraSettings.getModel(
                context
            )
        )
    }

    var settingsOpen by remember {
        mutableStateOf(false)
    }

    var modelOpen by remember {
        mutableStateOf(false)
    }

    var renameId by remember {
        mutableStateOf<String?>(null)
    }

    var renameText by remember {
        mutableStateOf("")
    }

    val clipboard =
        LocalClipboardManager.current

    val filePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null && filesEnabled) {
                input =
                    if (input.isBlank())
                        "I selected a file. Please analyze it."
                    else
                        input
            }
        }

    fun newChat() {
        currentConversation = null
        messages = emptyList()
        input = ""
        error = null
    }

    fun send() {

        val text =
            input.trim()

        if (
            text.isEmpty() ||
            loading
        ) return

        input = ""
        loading = true
        error = null

        messages =
            messages +
                ApiClient.ChatMessage(
                    role = "user",
                    text = text
                )

        scope.launch {

            when (
                val result =
                    ApiClient.sendMessage(
                        text = text,
                        conversationId =
                            currentConversation,
                        model = modelId,
                        webSearch = webSearch,
                        imageGeneration = imageGen
                    )
            ) {

                is ApiClient.ApiResult.Success -> {

                    currentConversation =
                        result.data.conversationId

                    messages =
                        messages +
                            ApiClient.ChatMessage(
                                id =
                                    result.data.messageId,
                                role = "assistant",
                                text =
                                    cleanNovaraText(
                                        result.data.reply
                                    ),
                                attachmentPath =
                                    result.data.generatedImage,
                                attachmentType =
                                    if (
                                        result.data
                                            .generatedImage != null
                                    )
                                        "image"
                                    else
                                        null
                            )
                }

                is ApiClient.ApiResult.Failure ->
                    error = result.message
            }

            loading = false

            when (
                val result =
                    ApiClient.getConversations()
            ) {
                is ApiClient.ApiResult.Success ->
                    conversations =
                        result.data

                else -> Unit
            }
        }
    }

    LaunchedEffect(Unit) {

        when (
            val result =
                ApiClient.getConversations()
        ) {

            is ApiClient.ApiResult.Success ->
                conversations =
                    result.data

            else -> Unit
        }
    }

    LaunchedEffect(
        messages.size
    ) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(
                messages.lastIndex
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {

            ModalDrawerSheet {

                Text(
                    "Novara",
                    modifier =
                        Modifier.padding(20.dp),
                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall
                )

                Button(
                    onClick = {
                        newChat()

                        scope.launch {
                            drawerState.close()
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 12.dp
                            )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null
                    )

                    Spacer(
                        Modifier.width(8.dp)
                    )

                    Text("New chat")
                }

                Divider(
                    Modifier.padding(
                        vertical = 12.dp
                    )
                )

                LazyColumn(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    items(
                        conversations,
                        key = {
                            it.id
                        }
                    ) { conversation ->

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 8.dp,
                                        vertical = 4.dp
                                    ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            TextButton(
                                onClick = {

                                    currentConversation =
                                        conversation.id

                                    scope.launch {

                                        when (
                                            val result =
                                                ApiClient
                                                    .getMessages(
                                                        conversation.id
                                                    )
                                        ) {

                                            is ApiClient
                                                .ApiResult
                                                .Success ->
                                                messages =
                                                    result.data.map {
                                                        it.copy(
                                                            text =
                                                                if (
                                                                    it.role ==
                                                                        "assistant"
                                                                )
                                                                    cleanNovaraText(
                                                                        it.text
                                                                    )
                                                                else
                                                                    it.text
                                                        )
                                                    }

                                            is ApiClient
                                                .ApiResult
                                                .Failure ->
                                                error =
                                                    result.message
                                        }

                                        drawerState.close()
                                    }
                                },
                                modifier =
                                    Modifier.weight(1f)
                            ) {
                                Text(
                                    conversation.title,
                                    maxLines = 1
                                )
                            }

                            IconButton(
                                onClick = {
                                    renameId =
                                        conversation.id

                                    renameText =
                                        conversation.title
                                }
                            ) {
                                Text("✎")
                            }

                            IconButton(
                                onClick = {

                                    scope.launch {

                                        ApiClient
                                            .deleteConversation(
                                                conversation.id
                                            )

                                        conversations =
                                            conversations.filter {
                                                it.id !=
                                                    conversation.id
                                            }

                                        if (
                                            currentConversation ==
                                                conversation.id
                                        ) {
                                            newChat()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription =
                                        "Delete"
                                )
                            }
                        }
                    }
                }

                Divider()

                TextButton(
                    onClick = {
                        settingsOpen = true
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                ) {

                    Icon(
                        Icons.Default.Settings,
                        contentDescription =
                            null
                    )

                    Spacer(
                        Modifier.width(8.dp)
                    )

                    Text("Settings")
                }
            }
        }
    ) {

        Scaffold(

            topBar = {

                TopAppBar(

                    title = {
                        Text("Novara")
                    },

                    navigationIcon = {

                        IconButton(
                            onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription =
                                    "Menu"
                            )
                        }
                    },

                    actions = {

                        Box {

                            TextButton(
                                onClick = {
                                    modelOpen = true
                                }
                            ) {

                                val model =
                                    MODEL_OPTIONS
                                        .firstOrNull {
                                            it.id ==
                                                modelId
                                        }
                                        ?: MODEL_OPTIONS[0]

                                Text(
                                    model.label,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                )
                            }

                            DropdownMenu(
                                expanded =
                                    modelOpen,
                                onDismissRequest = {
                                    modelOpen = false
                                }
                            ) {

                                MODEL_OPTIONS.forEach {
                                    option ->

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "${option.icon}  ${option.label}"
                                            )
                                        },
                                        onClick = {

                                            modelId =
                                                option.id

                                            NovaraSettings
                                                .setModel(
                                                    context,
                                                    option.id
                                                )

                                            modelOpen =
                                                false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                newChat()
                            }
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription =
                                    "New chat"
                            )
                        }
                    }
                )
            },

            bottomBar = {

                Column(
                    modifier =
                        Modifier
                            .navigationBarsPadding()
                            .padding(8.dp)
                ) {

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(6.dp)
                    ) {

                        TextButton(
                            onClick = {

                                webSearch =
                                    !webSearch

                                if (webSearch)
                                    imageGen = false

                                NovaraSettings.set(
                                    context,
                                    "web_search",
                                    webSearch
                                )

                                NovaraSettings.set(
                                    context,
                                    "image_gen",
                                    imageGen
                                )
                            }
                        ) {
                            Text(
                                if (webSearch)
                                    "Web ✓"
                                else
                                    "Web"
                            )
                        }

                        TextButton(
                            onClick = {

                                imageGen =
                                    !imageGen

                                if (imageGen)
                                    webSearch = false

                                NovaraSettings.set(
                                    context,
                                    "image_gen",
                                    imageGen
                                )

                                NovaraSettings.set(
                                    context,
                                    "web_search",
                                    webSearch
                                )
                            }
                        ) {
                            Text(
                                if (imageGen)
                                    "Image ✓"
                                else
                                    "Image"
                            )
                        }
                    }

                    Row(
                        verticalAlignment =
                            Alignment.Bottom
                    ) {

                        IconButton(
                            onClick = {

                                if (
                                    !filesEnabled
                                ) return@IconButton

                                filePicker.launch(
                                    arrayOf(
                                        "image/*",
                                        "application/pdf",
                                        "text/*",
                                        "video/*",
                                        "*/*"
                                    )
                                )
                            }
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription =
                                    "Attach"
                            )
                        }

                        VoiceButton(
                            context =
                                context,
                            enabled =
                                voiceEnabled,
                            onResult = {
                                input =
                                    if (
                                        input.isBlank()
                                    )
                                        it
                                    else
                                        "$input $it"
                            }
                        )

                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                            },
                            modifier =
                                Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    if (imageGen)
                                        "Describe the image..."
                                    else
                                        "Message Novara..."
                                )
                            },
                            maxLines = 5
                        )

                        IconButton(
                            onClick = {
                                send()
                            },
                            enabled =
                                input
                                    .isNotBlank() &&
                                    !loading
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription =
                                    "Send"
                            )
                        }
                    }
                }
            }

        ) { padding ->

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
            ) {

                error?.let {
                    Text(
                        it,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme
                                        .colorScheme
                                        .errorContainer
                                )
                                .padding(12.dp)
                    )
                }

                if (
                    messages.isEmpty()
                ) {

                    Box(
                        modifier =
                            Modifier.fillMaxSize(),
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Text(
                                "How can I help?",
                                style =
                                    MaterialTheme
                                        .typography
                                        .headlineMedium
                            )

                            Spacer(
                                Modifier.height(8.dp)
                            )

                            Text(
                                "Chat, search, analyze files or generate images."
                            )
                        }
                    }

                } else {

                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(12.dp)
                    ) {

                        items(
                            messages,
                            key = {
                                if (
                                    it.id.isNotBlank()
                                )
                                    it.id
                                else
                                    it.hashCode()
                                        .toString()
                            }
                        ) { message ->

                            MessageBubble(
                                message = message,
                                onCopy = {
                                    clipboard.setText(
                                        AnnotatedString(
                                            cleanNovaraText(
                                                message.text
                                            )
                                        )
                                    )
                                },
                                onSpeak = {
                                    speakText(
                                        context,
                                        cleanNovaraText(
                                            message.text
                                        )
                                    )
                                },
                                onFeedback = { value ->

                                    if (
                                        message.id
                                            .isNotBlank()
                                    ) {
                                        scope.launch {
                                            ApiClient
                                                .sendFeedback(
                                                    message.id,
                                                    value
                                                )
                                        }
                                    }
                                }
                            )
                        }

                        if (loading) {
                            item {
                                Text(
                                    "Novara is thinking...",
                                    modifier =
                                        Modifier.padding(
                                            12.dp
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (settingsOpen) {

        AlertDialog(

            onDismissRequest = {
                settingsOpen = false
            },

            title = {
                Text("Novara Settings")
            },

            text = {

                LazyColumn(
                    modifier =
                        Modifier.heightIn(
                            max = 500.dp
                        )
                ) {

                    item {
                        SettingSwitch(
                            "Haptic feedback",
                            hapticEnabled
                        ) {
                            hapticEnabled = it
                            NovaraSettings.set(
                                context,
                                "haptic",
                                it
                            )
                        }
                    }

                    item {
                        SettingSwitch(
                            "Web search",
                            webSearch
                        ) {
                            webSearch = it

                            if (it)
                                imageGen = false

                            NovaraSettings.set(
                                context,
                                "web_search",
                                webSearch
                            )

                            NovaraSettings.set(
                                context,
                                "image_gen",
                                imageGen
                            )
                        }
                    }

                    item {
                        SettingSwitch(
                            "Image generation",
                            imageGen
                        ) {
                            imageGen = it

                            if (it)
                                webSearch = false

                            NovaraSettings.set(
                                context,
                                "image_gen",
                                imageGen
                            )

                            NovaraSettings.set(
                                context,
                                "web_search",
                                webSearch
                            )
                        }
                    }

                    item {
                        SettingSwitch(
                            "Files & attachments",
                            filesEnabled
                        ) {
                            filesEnabled = it

                            NovaraSettings.set(
                                context,
                                "files",
                                it
                            )
                        }
                    }

                    item {
                        SettingSwitch(
                            "Memory",
                            memoryEnabled
                        ) {
                            memoryEnabled = it

                            NovaraSettings.set(
                                context,
                                "memory",
                                it
                            )
                        }
                    }

                    item {
                        SettingSwitch(
                            "Voice assistant",
                            voiceEnabled
                        ) {
                            voiceEnabled = it

                            NovaraSettings.set(
                                context,
                                "voice_assistant",
                                it
                            )
                        }
                    }

                    item {
                        SettingSwitch(
                            "Code execution",
                            codeExecutionEnabled
                        ) {
                            codeExecutionEnabled = it

                            if (!it)
                                artifactsEnabled = false

                            NovaraSettings.set(
                                context,
                                "code_execution",
                                it
                            )

                            NovaraSettings.set(
                                context,
                                "artifacts",
                                artifactsEnabled
                            )
                        }
                    }

                    item {
                        SettingSwitch(
                            "Artifacts",
                            artifactsEnabled,
                            enabled =
                                codeExecutionEnabled
                        ) {
                            artifactsEnabled = it

                            NovaraSettings.set(
                                context,
                                "artifacts",
                                it
                            )
                        }
                    }

                    item {
                        SettingSwitch(
                            "Switch flagged model",
                            switchFlaggedModel
                        ) {
                            switchFlaggedModel = it

                            NovaraSettings.set(
                                context,
                                "switch_flagged_model",
                                it
                            )
                        }
                    }

                    item {
                        Spacer(
                            Modifier.height(8.dp)
                        )

                        Text(
                            "Current model: ${
                                MODEL_OPTIONS
                                    .firstOrNull {
                                        it.id == modelId
                                    }
                                    ?.label
                                    ?: "Fast"
                            }",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )
                    }
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        settingsOpen = false
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }

    renameId?.let { id ->

        AlertDialog(

            onDismissRequest = {
                renameId = null
            },

            title = {
                Text("Rename chat")
            },

            text = {

                OutlinedTextField(
                    value = renameText,
                    onValueChange = {
                        renameText = it
                    },
                    singleLine = true
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        scope.launch {

                            ApiClient
                                .renameConversation(
                                    id,
                                    renameText.trim()
                                )

                            when (
                                val result =
                                    ApiClient
                                        .getConversations()
                            ) {
                                is ApiClient
                                    .ApiResult
                                    .Success ->
                                    conversations =
                                        result.data

                                else -> Unit
                            }

                            renameId = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        renameId = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            title,
            modifier =
                Modifier.weight(1f),
            color =
                if (enabled)
                    MaterialTheme
                        .colorScheme
                        .onSurface
                else
                    MaterialTheme
                        .colorScheme
                        .onSurface
                        .copy(
                            alpha = 0.45f
                        )
        )

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange
        )
    }
}

@Composable
private fun MessageBubble(
    message: ApiClient.ChatMessage,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onFeedback: (Int) -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 8.dp
                )
    ) {

        Text(
            if (message.role == "user")
                "You"
            else
                "Novara",
            style =
                MaterialTheme
                    .typography
                    .labelLarge
        )

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 4.dp
                    ),
            shape =
                RoundedCornerShape(14.dp)
        ) {

            Text(
                text =
                    if (
                        message.role ==
                            "assistant"
                    )
                        cleanNovaraText(
                            message.text
                        )
                    else
                        message.text,
                modifier =
                    Modifier.padding(14.dp),
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
        }

        if (
            message.role == "assistant"
        ) {

            Row {

                IconButton(
                    onClick = onCopy
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription =
                            "Copy"
                    )
                }

                IconButton(
                    onClick = onSpeak
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription =
                            "Speak"
                    )
                }

                IconButton(
                    onClick = {
                        onFeedback(1)
                    }
                ) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription =
                            "Like"
                    )
                }

                IconButton(
                    onClick = {
                        onFeedback(-1)
                    }
                ) {
                    Icon(
                        Icons.Default.ThumbDown,
                        contentDescription =
                            "Unlike"
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceButton(
    context: Context,
    enabled: Boolean,
    onResult: (String) -> Unit
) {

    if (!enabled) {
        return
    }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val text =
                result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )
                    ?.firstOrNull()

            if (
                !text.isNullOrBlank()
            ) {
                onResult(text)
            }
        }

    IconButton(
        onClick = {

            if (
                !SpeechRecognizer
                    .isRecognitionAvailable(
                        context
                    )
            ) return@IconButton

            val intent =
                Intent(
                    RecognizerIntent
                        .ACTION_RECOGNIZE_SPEECH
                ).apply {

                    putExtra(
                        RecognizerIntent
                            .EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent
                            .LANGUAGE_MODEL_FREE_FORM
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_LANGUAGE,
                        Locale.getDefault()
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_PROMPT,
                        "Speak to Novara"
                    )
                }

            launcher.launch(intent)
        }
    ) {

        Icon(
            Icons.Default.Mic,
            contentDescription =
                "Voice input"
        )
    }
}

private fun speakText(
    context: Context,
    text: String
) {

    if (text.isBlank())
        return

    val tts =
        TextToSpeech(
            context
        ) { status ->

            if (
                status ==
                    TextToSpeech.SUCCESS
            ) {

                val locale =
                    Locale.getDefault()

                ttsSetLanguageAndSpeak(
                    context,
                    text,
                    locale
                )
            }
        }
}

private fun ttsSetLanguageAndSpeak(
    context: Context,
    text: String,
    locale: Locale
) {

    lateinit var speaker: TextToSpeech

    speaker =
        TextToSpeech(
            context
        ) { status ->

            if (
                status ==
                    TextToSpeech.SUCCESS
            ) {

                speakerLanguage(
                    speaker,
                    locale
                )

                speaker.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "novara_reply"
                )
            }
        }
}

private fun speakerLanguage(
    speaker: TextToSpeech,
    locale: Locale
) {
    speaker.language = locale
}
