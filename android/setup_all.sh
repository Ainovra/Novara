#!/data/data/com.termux/files/usr/bin/bash
set -e

ROOT="$HOME/Novara/android"
PKG="$ROOT/app/src/main/java/com/novara/app"

echo "=== NOVARA FINAL NATIVE SETUP ==="

mkdir -p "$PKG/ui/screens"
mkdir -p "$PKG/ui/components"
mkdir -p "$PKG/network"

# ------------------------------------------------------------
# MANIFEST
# ------------------------------------------------------------

cat > "$ROOT/app/src/main/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <queries>
        <intent>
            <action android:name="android.speech.RecognitionService" />
        </intent>
    </queries>

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <uses-feature
        android:name="android.hardware.camera"
        android:required="false" />

    <application
        android:name=".NovaraApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:theme="@style/Theme.Novara"
        android:usesCleartextTraffic="false"
        android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true">

        <activity
            android:name=".SplashActivity"
            android:theme="@style/Theme.Novara.Splash"
            android:exported="true">

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

        </activity>

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:windowSoftInputMode="adjustResize"
            android:configChanges="orientation|screenSize|keyboardHidden|keyboard|uiMode">

            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />

                <data
                    android:scheme="novara"
                    android:host="auth-complete" />
            </intent-filter>

        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">

            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />

        </provider>

    </application>

</manifest>
EOF

# ------------------------------------------------------------
# API CLIENT
# ------------------------------------------------------------

cat > "$PKG/network/ApiClient.kt" <<'EOF'
package com.novara.app.network

import com.novara.app.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ApiClient {

    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    val client: OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

    private val JSON =
        "application/json; charset=utf-8".toMediaType()

    private val BASE_URL =
        Config.WEB_BASE_URL.trimEnd('/')

    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Failure(val message: String) : ApiResult<Nothing>()
    }

    data class AuthResponse(
        val ok: Boolean,
        val redirect: String?
    )

    data class Conversation(
        val id: String,
        val title: String,
        val createdAt: String
    )

    data class ChatMessage(
        val id: String = "",
        val role: String,
        val text: String,
        val attachmentPath: String? = null,
        val attachmentType: String? = null,
        val sources: String? = null,
        val feedback: String? = null
    )

    data class ChatReply(
        val reply: String,
        val messageId: String,
        val conversationId: String,
        val generatedImage: String? = null
    )

    private suspend fun postJson(
        path: String,
        body: JSONObject
    ): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder()
                        .url(BASE_URL + path)
                        .post(body.toString().toRequestBody(JSON))
                        .build()

                client.newCall(request).execute().use { response ->

                    val text =
                        response.body?.string().orEmpty()

                    val json =
                        if (text.isNotBlank())
                            JSONObject(text)
                        else
                            JSONObject()

                    if (response.isSuccessful) {

                        val redirect =
                            if (
                                json.has("redirect") &&
                                !json.isNull("redirect")
                            )
                                json.getString("redirect")
                            else null

                        ApiResult.Success(
                            AuthResponse(
                                json.optBoolean("ok", true),
                                redirect
                            )
                        )

                    } else {
                        ApiResult.Failure(
                            json.optString(
                                "error",
                                "Something went wrong (${response.code})."
                            )
                        )
                    }
                }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
                )
            }
        }

    suspend fun login(
        username: String,
        password: String
    ): ApiResult<AuthResponse> =
        postJson(
            "/api/login",
            JSONObject()
                .put("username", username)
                .put("password", password)
        )

    suspend fun signup(
        username: String,
        password: String
    ): ApiResult<AuthResponse> =
        postJson(
            "/api/signup",
            JSONObject()
                .put("username", username)
                .put("password", password)
        )

    suspend fun guestLogin(): ApiResult<AuthResponse> =
        postJson("/api/guest", JSONObject())

    suspend fun acceptTerms(): ApiResult<AuthResponse> =
        postJson("/api/accept-terms", JSONObject())

    suspend fun consumeAuthCode(
        code: String
    ): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val noRedirect =
                    client.newBuilder()
                        .followRedirects(false)
                        .build()

                val url =
                    (BASE_URL + Config.MOBILE_CONSUME_PATH)
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("code", code)
                        .build()

                val request =
                    Request.Builder()
                        .url(url)
                        .get()
                        .build()

                noRedirect.newCall(request)
                    .execute()
                    .use { response ->

                        when {
                            response.code in 300..399 ->
                                ApiResult.Success(
                                    AuthResponse(
                                        true,
                                        response.header("Location")
                                    )
                                )

                            response.isSuccessful ->
                                ApiResult.Success(
                                    AuthResponse(true, null)
                                )

                            else ->
                                ApiResult.Failure(
                                    "Google sign-in couldn't be completed (${response.code})."
                                )
                        }
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
                )
            }
        }

    suspend fun sendMessage(
        text: String,
        conversationId: String?,
        model: String,
        webSearch: Boolean,
        imageGen: Boolean
    ): ApiResult<ChatReply> =
        withContext(Dispatchers.IO) {
            try {

                val form =
                    MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("message", text)
                        .addFormDataPart(
                            "web_search",
                            webSearch.toString()
                        )
                        .addFormDataPart(
                            "image_gen",
                            imageGen.toString()
                        )
                        .addFormDataPart("model", model)
                        .apply {
                            if (conversationId != null) {
                                addFormDataPart(
                                    "conversation_id",
                                    conversationId
                                )
                            }
                        }
                        .build()

                val request =
                    Request.Builder()
                        .url(BASE_URL + "/api/chat")
                        .post(form)
                        .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        val textBody =
                            response.body?.string().orEmpty()

                        val json =
                            if (textBody.isNotBlank())
                                JSONObject(textBody)
                            else
                                JSONObject()

                        if (
                            response.isSuccessful &&
                            json.has("reply")
                        ) {

                            ApiResult.Success(
                                ChatReply(
                                    reply =
                                        json.getString("reply"),

                                    messageId =
                                        json.optString(
                                            "message_id",
                                            ""
                                        ),

                                    conversationId =
                                        json.optString(
                                            "conversation_id",
                                            conversationId ?: ""
                                        ),

                                    generatedImage =
                                        json.optString(
                                            "generated_image",
                                            null
                                        )
                                )
                            )

                        } else {
                            ApiResult.Failure(
                                json.optString(
                                    "error",
                                    "Something went wrong (${response.code})."
                                )
                            )
                        }
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
                )
            }
        }

    suspend fun getConversations():
        ApiResult<List<Conversation>> =
        withContext(Dispatchers.IO) {
            try {

                val request =
                    Request.Builder()
                        .url(BASE_URL + "/api/conversations")
                        .get()
                        .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        val body =
                            response.body?.string().orEmpty()

                        if (!response.isSuccessful) {
                            return@use ApiResult.Failure(
                                "Couldn't load conversations (${response.code})."
                            )
                        }

                        val json = JSONObject(body)
                        val arr =
                            json.optJSONArray("conversations")

                        val list = mutableListOf<Conversation>()

                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)

                                list += Conversation(
                                    id = o.optString("id"),
                                    title = o.optString(
                                        "title",
                                        "New chat"
                                    ),
                                    createdAt =
                                        o.optString("created_at")
                                )
                            }
                        }

                        ApiResult.Success(list)
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara."
                )
            }
        }

    suspend fun getMessages(
        conversationId: String
    ): ApiResult<List<ChatMessage>> =
        withContext(Dispatchers.IO) {
            try {

                val request =
                    Request.Builder()
                        .url(
                            BASE_URL +
                                "/api/conversations/" +
                                conversationId +
                                "/messages"
                        )
                        .get()
                        .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        val body =
                            response.body?.string().orEmpty()

                        if (!response.isSuccessful) {
                            return@use ApiResult.Failure(
                                "Couldn't load this chat (${response.code})."
                            )
                        }

                        val json = JSONObject(body)
                        val arr =
                            json.optJSONArray("messages")

                        val list = mutableListOf<ChatMessage>()

                        if (arr != null) {
                            for (i in 0 until arr.length()) {

                                val o =
                                    arr.getJSONObject(i)

                                list += ChatMessage(
                                    id =
                                        o.optString("id"),
                                    role =
                                        o.optString("role"),
                                    text =
                                        o.optString("text"),
                                    attachmentPath =
                                        o.optString(
                                            "attachment_path",
                                            null
                                        ),
                                    attachmentType =
                                        o.optString(
                                            "attachment_type",
                                            null
                                        ),
                                    sources =
                                        o.optString(
                                            "sources",
                                            null
                                        ),
                                    feedback =
                                        o.optString(
                                            "feedback",
                                            null
                                        )
                                )
                            }
                        }

                        ApiResult.Success(list)
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara."
                )
            }
        }

    suspend fun deleteConversation(
        id: String
    ): ApiResult<Boolean> =
        simpleRequest(
            "/api/conversations/$id",
            "DELETE"
        )

    suspend fun renameConversation(
        id: String,
        title: String
    ): ApiResult<Boolean> =
        simpleRequest(
            "/api/conversations/$id/rename",
            "POST",
            JSONObject().put("title", title)
        )

    suspend fun shareConversation(
        id: String
    ): ApiResult<String?> =
        withContext(Dispatchers.IO) {
            try {

                val request =
                    Request.Builder()
                        .url(
                            BASE_URL +
                                "/api/conversations/" +
                                id +
                                "/share"
                        )
                        .post(
                            JSONObject()
                                .toString()
                                .toRequestBody(JSON)
                        )
                        .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        val body =
                            response.body?.string().orEmpty()

                        if (!response.isSuccessful) {
                            return@use ApiResult.Failure(
                                "Couldn't share this chat."
                            )
                        }

                        val json = JSONObject(body)

                        ApiResult.Success(
                            json.optString(
                                "share_url",
                                null
                            )
                        )
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara."
                )
            }
        }

    suspend fun unshareConversation(
        id: String
    ): ApiResult<Boolean> =
        simpleRequest(
            "/api/conversations/$id/unshare",
            "POST"
        )

    suspend fun feedback(
        messageId: String,
        value: String
    ): ApiResult<Boolean> =
        simpleRequest(
            "/api/messages/$messageId/feedback",
            "POST",
            JSONObject().put("feedback", value)
        )

    private suspend fun simpleRequest(
        path: String,
        method: String,
        body: JSONObject? = null
    ): ApiResult<Boolean> =
        withContext(Dispatchers.IO) {
            try {

                val builder =
                    Request.Builder()
                        .url(BASE_URL + path)

                if (method == "DELETE") {
                    builder.delete()
                } else {
                    builder.post(
                        (
                            body ?: JSONObject()
                        ).toString()
                            .toRequestBody(JSON)
                    )
                }

                client.newCall(builder.build())
                    .execute()
                    .use { response ->

                        if (response.isSuccessful)
                            ApiResult.Success(true)
                        else
                            ApiResult.Failure(
                                "Request failed (${response.code})."
                            )
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara."
                )
            }
        }
}
EOF

# ------------------------------------------------------------
# SETTINGS
# ------------------------------------------------------------

cat > "$PKG/ui/NovaraSettings.kt" <<'EOF'
package com.novara.app.ui

import android.content.Context

object NovaraSettings {

    private const val PREFS = "novara_settings"

    fun get(context: Context, key: String, default: Boolean): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key, default)

    fun set(
        context: Context,
        key: String,
        value: Boolean
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    fun getModel(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("model", "fast") ?: "fast"

    fun setModel(
        context: Context,
        value: String
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("model", value)
            .apply()
    }
}
EOF

# ------------------------------------------------------------
# MAIN ACTIVITY
# ------------------------------------------------------------

cat > "$PKG/MainActivity.kt" <<'EOF'
package com.novara.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.novara.app.network.ApiClient
import com.novara.app.ui.LoginScreen
import com.novara.app.ui.TermsScreen
import com.novara.app.ui.screens.ChatScreen
import com.novara.app.ui.theme.NovaraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var redirect by mutableStateOf<String?>(null)
    private var authError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NovaraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {

                    when {
                        redirect == "/terms" -> {
                            TermsScreen(
                                onAccepted = {
                                    redirect = "/app"
                                }
                            )
                        }

                        redirect != null -> {
                            ChatScreen()
                        }

                        else -> {
                            LoginScreen(
                                onLoggedIn = {
                                    redirect =
                                        it ?: "/app"
                                }
                            )
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

        val data =
            intent?.data ?: return

        if (
            data.scheme ==
                Config.AUTH_HANDOFF_SCHEME &&
            data.host ==
                Config.AUTH_HANDOFF_HOST
        ) {

            val code =
                data.getQueryParameter("code")
                    ?: return

            authError = null

            lifecycleScope.launch {

                when (
                    val result =
                        ApiClient.consumeAuthCode(code)
                ) {

                    is ApiClient.ApiResult.Success ->
                        redirect =
                            result.data.redirect ?: "/app"

                    is ApiClient.ApiResult.Failure ->
                        authError = result.message
                }
            }
        }
    }
}
EOF

# ------------------------------------------------------------
# FINAL CHAT SCREEN
# ------------------------------------------------------------

cat > "$PKG/ui/screens/ChatScreen.kt" <<'EOF'
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
EOF

# ------------------------------------------------------------
# GRADLE SAFETY
# ------------------------------------------------------------

python3 - <<'PY'
from pathlib import Path

p = Path("app/build.gradle")
s = p.read_text()

if 'implementation "androidx.lifecycle:lifecycle-runtime-ktx' not in s:
    s = s.replace(
        'implementation "androidx.activity:activity-compose:1.9.3"',
        'implementation "androidx.activity:activity-compose:1.9.3"\n'
        '    implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.8.6"'
    )

p.write_text(s)
PY

echo
echo "=== SETUP COMPLETE ==="
echo
echo "Kotlin files:"
find app/src/main/java/com/novara/app -type f -name "*.kt" | sort
echo
echo "Next: run ./gradlew :app:assembleDebug"
echo "DO NOT BUILD RELEASE APK YET."
