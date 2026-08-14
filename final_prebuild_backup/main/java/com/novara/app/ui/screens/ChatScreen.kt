package com.novara.app.ui.screens

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.novara.app.network.ApiClient
import com.novara.app.ui.NovaraSettings
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val drawerState =
        rememberDrawerState(DrawerValue.Closed)

    val listState =
        rememberLazyListState()

    var conversations by remember {
        mutableStateOf<List<ApiClient.Conversation>>(emptyList())
    }

    var messages by remember {
        mutableStateOf<List<ApiClient.ChatMessage>>(emptyList())
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

    var model by remember {
        mutableStateOf(
            NovaraSettings.getModel(context)
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

            if (uri != null) {
                input =
                    input.ifEmpty {
                        "I selected a file. Please help me analyze it."
                    }
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

        if (text.isEmpty() || loading)
            return

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
                        model = model,
                        webSearch = webSearch,
                        imageGen = imageGen
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
                                    result.data.reply,
                                attachmentPath =
                                    result.data.generatedImage,
                                attachmentType =
                                    if (
                                        result.data.generatedImage != null
                                    )
                                        "image"
                                    else null
                            )
                }

                is ApiClient.ApiResult.Failure ->
                    error = result.message
            }

            loading = false

            when (
                val c =
                    ApiClient.getConversations()
            ) {
                is ApiClient.ApiResult.Success ->
                    conversations = c.data

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
                conversations = result.data

            else -> Unit
        }
    }

    LaunchedEffect(messages.size) {
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
                        Modifier.padding(20.dp)
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
                            .padding(horizontal = 12.dp)
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
                    Modifier.padding(vertical = 12.dp)
                )

                LazyColumn(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    items(
                        conversations,
                        key = { it.id }
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

                                            is ApiClient.ApiResult.Success ->
                                                messages =
                                                    result.data

                                            is ApiClient.ApiResult.Failure ->
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
                        contentDescription = null
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
                                Text(model)
                            }

                            DropdownMenu(
                                expanded = modelOpen,
                                onDismissRequest = {
                                    modelOpen = false
                                }
                            ) {

                                listOf(
                                    "fast",
                                    "balanced",
                                    "smart"
                                ).forEach { item ->

                                    DropdownMenuItem(
                                        text = {
                                            Text(item)
                                        },
                                        onClick = {

                                            model = item

                                            NovaraSettings
                                                .setModel(
                                                    context,
                                                    item
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
                            context = context,
                            onResult = {
                                input +=
                                    if (input.isBlank())
                                        it
                                    else
                                        " $it"
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
                                input.isNotBlank() &&
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
                                    MaterialTheme.colorScheme.errorContainer
                                )
                                .padding(12.dp)
                    )
                }

                if (messages.isEmpty()) {

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
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = 12.dp
                                )
                    ) {

                        items(
                            messages,
                            key = {
                                it.id.ifBlank {
                                    it.hashCode()
                                        .toString()
                                }
                            }
                        ) { message ->

                            MessageBubble(
                                message = message,
                                onCopy = {
                                    clipboard.setText(
                                        AnnotatedString(
                                            message.text
                                        )
                                    )
                                },
                                onSpeak = {
                                    speakText(
                                        context,
                                        message.text
                                    )
                                },
                                onFeedback = { value ->

                                    if (
                                        message.id.isNotBlank()
                                    ) {
                                        scope.launch {
                                            ApiClient.feedback(
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
                Text("Settings")
            },

            text = {

                Column {

                    SettingSwitch(
                        "Web search",
                        webSearch
                    ) {
                        webSearch = it
                        if (it) imageGen = false

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

                    SettingSwitch(
                        "Image generation",
                        imageGen
                    ) {
                        imageGen = it
                        if (it) webSearch = false

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

                            ApiClient.renameConversation(
                                id,
                                renameText.trim()
                            )

                            when (
                                val result =
                                    ApiClient
                                        .getConversations()
                            ) {
                                is ApiClient.ApiResult.Success ->
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
                Modifier.weight(1f)
        )

        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onChange
        )
    }
}

@Composable
private fun MessageBubble(
    message: ApiClient.ChatMessage,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onFeedback: (String) -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
    ) {

        Text(
            if (message.role == "user")
                "You"
            else
                "Novara",
            style =
                MaterialTheme.typography.labelLarge
        )

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
        ) {

            Text(
                message.text,
                modifier =
                    Modifier.padding(12.dp)
            )
        }

        if (message.role == "assistant") {

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
                        onFeedback("up")
                    }
                ) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription =
                            "Good"
                    )
                }

                IconButton(
                    onClick = {
                        onFeedback("down")
                    }
                ) {
                    Icon(
                        Icons.Default.ThumbDown,
                        contentDescription =
                            "Bad"
                    )
                }

                IconButton(
                    onClick = onCopy
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription =
                            "Share"
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceButton(
    context: Context,
    onResult: (String) -> Unit
) {

    var listening by remember {
        mutableStateOf(false)
    }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            listening = false

            val text =
                result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )
                    ?.firstOrNull()

            if (!text.isNullOrBlank()) {
                onResult(text)
            }
        }

    IconButton(
        onClick = {

            if (
                !SpeechRecognizer
                    .isRecognitionAvailable(context)
            ) {
                return@IconButton
            }

            listening = true

            val intent =
                Intent(
                    RecognizerIntent
                        .ACTION_RECOGNIZE_SPEECH
                ).apply {

                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent
                            .LANGUAGE_MODEL_FREE_FORM
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        Locale.getDefault()
                    )
                }

            launcher.launch(intent)
        }
    ) {

        Icon(
            Icons.Default.Mic,
            contentDescription =
                if (listening)
                    "Listening"
                else
                    "Voice"
        )
    }
}

private fun speakText(
    context: Context,
    text: String
) {

    val tts =
        TextToSpeech(context) { status ->

            if (
                status ==
                    TextToSpeech.SUCCESS
            ) {

                val engine =
                    TextToSpeech(
                        context,
                        null
                    )

                engine.language =
                    Locale.getDefault()

                engine.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "novara_reply"
                )
            }
        }
}
