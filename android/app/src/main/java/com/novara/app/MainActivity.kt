package com.novara.app

import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.novara.app.network.ApiClient
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.novara.app.ui.screens.ChatScreen
import com.novara.app.ui.theme.NovaraTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val NovaraBg = NovaraBrand.Background
private val NovaraCard = Color(0xFF101D27)
private val NovaraCard2 = Color(0xFF142432)
private val NovaraBorder = Color(0xFF263B49)
private val NovaraText = Color(0xFFF4F7F9)
private val NovaraMuted = Color(0xFFB6C2CA)
private val NovaraYellow = Color(0xFFFFC400)
private val NovaraBlue = Color(0xFF28B7FF)
private val NovaraPurple = Color(0xFF7548FF)



class MainActivity : ComponentActivity() {
    private val novaraDownloadReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: android.content.Context?,
                intent: Intent?
            ) {
                if (
                    intent?.action !=
                    DownloadManager.ACTION_DOWNLOAD_COMPLETE
                ) return

                val id =
                    intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID,
                        -1L
                    )

                
    val prefs =
                    getSharedPreferences(
                        "novara_updates",
                        MODE_PRIVATE
                    )

                val expected =
                    prefs.getLong(
                        "download_id",
                        -2L
                    )

                if (id != expected) return

                val manager =
                    getSystemService(
                        DOWNLOAD_SERVICE
                    ) as DownloadManager

                val uri =
                    manager.getUriForDownloadedFile(id)

                val version =
                    prefs.getString(
                        "download_version",
                        ""
                    ) ?: ""

                if (uri != null) {
                    NovaraUpdateManager.showUpdatedNotification(
                        this@MainActivity,
                        version
                    )

                    NovaraUpdateManager.installDownloadedApk(
                        this@MainActivity,
                        uri
                    )
                }
            }
        }


    private var authError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NovaraUpdateManager.createChannel(this)

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (
                androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    "android.permission.POST_NOTIFICATIONS"
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf("android.permission.POST_NOTIFICATIONS"),
                    1702
                )
            }
        }

        val filter =
            IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE
            )

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                novaraDownloadReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                novaraDownloadReceiver,
                filter
            )
        }

        handleIntent(intent)

        setContent {
            NovaraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NovaraBg
                ) {
                    NovaraNativeApp(
                        onGoogleLogin = {
                            openGoogleLogin()
                        },
                        onAuthCode = { code ->
                            consumeAuthCode(code)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(
                novaraDownloadReceiver
            )
        } catch (_: Exception) {
        }

        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return

        if (
            data.scheme == Config.AUTH_HANDOFF_SCHEME &&
            data.host == Config.AUTH_HANDOFF_HOST
        ) {
            val code = data.getQueryParameter("code") ?: return
            consumeAuthCode(code)
        }
    }

    private fun consumeAuthCode(code: String) {
        lifecycleScope.launch {
            when (val result = ApiClient.consumeAuthCode(code)) {
                is ApiClient.ApiResult.Success -> {
                    authError = null
                }

                is ApiClient.ApiResult.Failure -> {
                    authError = result.message
                }
            }
        }
    }

    private fun openGoogleLogin() {
        val url =
            Config.WEB_BASE_URL.trimEnd('/') +
                    Config.GOOGLE_LOGIN_PATH

        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaraNativeApp(
    onGoogleLogin: () -> Unit,
    onAuthCode: (String) -> Unit
) {
    val context = LocalContext.current

    val prefs = context.getSharedPreferences(
        "novara_prefs",
        android.content.Context.MODE_PRIVATE
    )

    var novaraFirstLaunchSplash by remember {
        mutableStateOf(
            !prefs.getBoolean("first_launch_splash_done", false)
        )
    }

    var onboardingDone by remember {
        mutableStateOf(
            prefs.getBoolean("onboarding_done", false)
        )
    }

    var onboardingPage by remember {
        mutableIntStateOf(0)
    }

    var termsDone by remember {
        mutableStateOf(
            prefs.getBoolean("terms_done", false)
        )
    }

    var loggedIn by remember {
        mutableStateOf(
            prefs.getBoolean("logged_in", false)
        )
    }

    var showNovaraSplash by remember {
        mutableStateOf(false)
    }

    val guestScope = rememberCoroutineScope()

    var remoteNovaraConfig by remember {
        mutableStateOf<NovaraUpdateManager.RemoteConfig?>(null)
    }

    var novaraUpdateInfo by remember {
        mutableStateOf<NovaraUpdateManager.UpdateInfo?>(null)
    }

    var showNovaraUpdateSheet by remember {
        mutableStateOf(false)
    }

    var minorUpdateStarted by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        val remote = NovaraUpdateManager.refresh(
            Config.WEB_BASE_URL
        )

        if (remote != null) {
            remoteNovaraConfig = remote

            val update = remote.update

            if (
                update.versionCode > NovaraVersion.VERSION_CODE &&
                update.apkUrl.isNotBlank() &&
                !minorUpdateStarted
            ) {
                novaraUpdateInfo = update

                if (update.isMajor) {
                    showNovaraUpdateSheet = true
                } else {
                    minorUpdateStarted = true

                    NovaraUpdateManager.downloadUpdate(
                        context = context,
                        baseUrl = Config.WEB_BASE_URL,
                        apkUrl = update.apkUrl,
                        versionName = update.versionName
                    )
                }
            }
        }
    }

    // =========================================================
    // NOVARA UPDATE ENGINE — SINGLE STARTUP INSTANCE
    // =========================================================
    if (showNovaraUpdateSheet &&
        novaraUpdateInfo != null) {

        ModalBottomSheet(
            onDismissRequest = {
                showNovaraUpdateSheet = false
            },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            )
        ) {
            NovaraUpdateSheet(
                version =
                    novaraUpdateInfo!!.versionName,
                changelog =
                    novaraUpdateInfo!!.changelog,
                onUpdate = {
                    NovaraUpdateManager.downloadUpdate(
                        context = context,
                        baseUrl = Config.WEB_BASE_URL,
                        apkUrl = novaraUpdateInfo!!.apkUrl,
                        versionName = novaraUpdateInfo!!.versionName
                    )
                },
                onLater = {
                    showNovaraUpdateSheet = false
                }
            )
        }
    }

    if (novaraFirstLaunchSplash) {
        NovaraLogoSplash(
            onFinished = {
                prefs.edit()
                    .putBoolean(
                        "first_launch_splash_done",
                        true
                    )
                    .apply()

                novaraFirstLaunchSplash = false
            }
        )
        return
    }

    when {
        !onboardingDone -> {
            OnboardingScreen(
                page = onboardingPage,
                onNext = {
                    if (onboardingPage < 4) {
                        onboardingPage++
                    } else {
                        prefs.edit()
                            .putBoolean("onboarding_done", true)
                            .apply()
                        onboardingDone = true
                    }
                },
                onSkip = {
                    prefs.edit()
                        .putBoolean("onboarding_done", true)
                        .apply()
                    onboardingDone = true
                }
            )
        }

        !termsDone -> {
            TermsScreenNative(
                onContinue = {
                    prefs.edit()
                        .putBoolean("terms_done", true)
                        .apply()
                    termsDone = true
                }
            )
        }

        !loggedIn -> {
            LoginScreenNative(
                onGoogle = {
                    onGoogleLogin()
                },
                onSignupSuccess = {
                    prefs.edit()
                        .putBoolean("logged_in", true)
                        .apply()
                    loggedIn = true
                    showNovaraSplash = true
                },
                onGuest = { onDone ->
                    guestScope.launch {
                        var attempt = 0
                        var guestReady = false

                        while (attempt < 5 && !guestReady) {
                            val result = apiPost("/api/guest", JSONObject())

                            if (result.first) {
                                val termsResult =
                                    apiPost("/api/accept-terms", JSONObject())

                                guestReady = termsResult.first

                                if (!guestReady) {
                                    android.util.Log.e(
                                        "Welcome to Novara",
                                        "Guest session created but terms acceptance failed: ${termsResult.second}"
                                    )
                                }
                            }

                            if (!guestReady) {
                                attempt++
                                if (attempt < 5) {
                                    kotlinx.coroutines.delay(2000)
                                }
                            }
                        }

                        if (guestReady) {
                            prefs.edit()
                                .putBoolean("logged_in", true)
                                .putBoolean("terms_done", true)
                                .apply()

                            loggedIn = true
                            showNovaraSplash = true
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Couldn't start guest session. Please try again.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            onDone()
                        }
                    }
                }
            )
        }

        else -> {
            if (showNovaraSplash) {
                NovaraLogoSplash(
                    onFinished = {
                        showNovaraSplash = false
                    }
                )
            } else {
                            ChatScreen()

            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    page: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val slides = listOf(
        Triple(
            "✨",
            "Welcome to Novara",
            "Your AI assistant for chatting, learning, creating, and getting things done."
        ),
        Triple(
            "💬",
            "Chat naturally",
            "Ask questions, brainstorm ideas, learn something new, or simply have a conversation."
        ),
        Triple(
            "🎨",
            "Create with Novara",
            "Work with images, PDFs, and other content directly in your conversations."
        ),
        Triple(
            "🎙",
            "Use your voice",
            "Speak naturally with Novara when you don't want to type."
        ),
        Triple(
            "🚀",
            "You're ready",
            "Ask anything — text, photo, PDF, voice, or ask Novara to create an image."
        )
    )

    val slide = slides[page]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        NovaraBrand.Background,
                        Color(0xFF091823),
                        Color(0xFF050B10)
                    )
                )
            )
    ) {
        Text(
            text = "Skip",
            color = NovaraMuted,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp, end = 24.dp)
                .clickable { onSkip() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF101E43),
                                Color(0xFF08182A)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Color(0xFF244A67),
                        RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = slide.first,
                    fontSize = 25.sp,
                    color = Color(0xFFF5F5FA)
                )
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = slide.second,
                color = NovaraText,
                fontSize = 29.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = slide.third,
                color = NovaraText,
                fontSize = 17.sp,
                lineHeight = 25.sp,
                modifier = Modifier.widthIn(max = 350.dp)
            )

            Spacer(Modifier.height(38.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                repeat(slides.size) { index ->
                    Box(
                        modifier = Modifier
                            .height(7.dp)
                            .width(
                                if (index == page) 25.dp
                                else 7.dp
                            )
                            .clip(CircleShape)
                            .background(
                                if (index == page)
                                    NovaraBlue
                                else
                                    Color(0xFF56636C)
                            )
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onNext,
                modifier = Modifier
                    .width(150.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovaraBlue,
                    contentColor = Color(0xFF302500)
                )
            ) {
                Text(
                    if (page == slides.lastIndex)
                        "Get Started"
                    else
                        "Next",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TermsScreenNative(
    onContinue: () -> Unit
) {
    var checked by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovaraBg),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(25.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF101B24)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                NovaraBorder
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Image(
                    painter = painterResource(R.drawable.novara_logo),
                    contentDescription = "Welcome to Novara",
                    modifier = Modifier.size(52.dp)
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    "Welcome to Novara",
                    color = NovaraText,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "Before continuing, please read these\nTerms & Conditions",
                    color = NovaraText,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )

                Spacer(Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(NovaraCard2)
                        .border(
                            1.dp,
                            NovaraBorder,
                            RoundedCornerShape(15.dp)
                        )
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(17.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            TermsSection(
                                "1. Acceptable use",
                                "By using Novara, you agree not to use it in any illegal, harmful, or abusive way."
                            )
                        }

                        item {
                            TermsSection(
                                "2. Accuracy of AI responses",
                                "Novara generates AI responses that can sometimes be incorrect or incomplete. Please verify important information yourself before relying on it for health, legal, or financial decisions."
                            )
                        }

                        item {
                            TermsSection(
                                "3. Your data",
                                "Your chats stay linked to your account so you can revisit them later. They may be used to improve the service."
                            )
                        }

                        item {
                            TermsSection(
                                "4. Your content",
                                "You are fully responsible for how you use any content you generate (text, video, images)."
                            )
                        }

                        item {
                            TermsSection(
                                "5. Chat sharing",
                                "When you share a chat, a public link is created that anyone with the link can view. Only share chats that don't contain sensitive information."
                            )
                        }

                        item {
                            TermsSection(
                                "6. Changes to the service",
                                "Novara's features may be updated, changed, or discontinued at any time without prior notice."
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { checked = !checked },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = NovaraBlue,
                            uncheckedColor = NovaraMuted,
                            checkmarkColor = Color(0xFF342900)
                        )
                    )

                    Text(
                        "I have read the Terms & Conditions\nand I agree to them.",
                        color = NovaraText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = onContinue,
                    enabled = checked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NovaraBlue,
                        disabledContainerColor = Color(0xFF705F20),
                        contentColor = Color(0xFF302500),
                        disabledContentColor = Color(0xFFB4A976)
                    )
                ) {
                    Text(
                        "Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun TermsSection(
    title: String,
    body: String
) {
    Column {
        Text(
            title,
            color = NovaraText,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        Spacer(Modifier.height(5.dp))

        Text(
            body,
            color = NovaraMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
    }
}

private val NOVARA_COMMON_PASSWORDS = setOf(
    "password",
    "password1",
    "password123",
    "password1234",
    "123456",
    "1234567",
    "12345678",
    "123456789",
    "1234567890",
    "qwerty",
    "qwerty123",
    "abc123",
    "letmein",
    "welcome",
    "welcome123",
    "admin",
    "admin123",
    "iloveyou",
    "monkey",
    "dragon",
    "football",
    "login",
    "passw0rd",
    "changeme"
)

private suspend fun apiGet(
    path: String
): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder()
            .url(BuildConfig.WEB_BASE_URL.trimEnd('/') + path)
            .get()
            .build()

        com.novara.app.network.ApiClient.client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            Pair(response.isSuccessful, text)
        }
    } catch (e: Exception) {
        Pair(false, e.message ?: "Network error")
    }
}

private suspend fun apiPost(
    path: String,
    json: JSONObject
): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    try {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(BuildConfig.WEB_BASE_URL.trimEnd('/') + path)
            .post(body)
            .build()

        com.novara.app.network.ApiClient.client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            Pair(response.isSuccessful, text)
        }
    } catch (e: Exception) {
        Pair(false, e.message ?: "Network error")
    }
}

private fun extractApiError(raw: String, fallback: String): String {
    return try {
        JSONObject(raw).optString("error", fallback)
    } catch (_: Exception) {
        if (raw.isNotBlank()) raw.take(120) else fallback
    }
}

@Composable
private fun LoginScreenNative(
    onGoogle: () -> Unit,
    onSignupSuccess: () -> Unit,
    onGuest: (onDone: () -> Unit) -> Unit
) {
    var step by remember { mutableStateOf("home") }

    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var signupEmail by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var code by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var guestBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val credentialManager = remember { androidx.credentials.CredentialManager.create(context) }

    val hasMinLen = password.length >= 8
    val hasUpper = password.any { it.isUpperCase() }
    val hasLower = password.any { it.isLowerCase() }
    val hasDigit = password.any { it.isDigit() }
    val hasSpecial = password.any { !it.isLetterOrDigit() }
    val notCommon = password.lowercase() !in NOVARA_COMMON_PASSWORDS
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword
    val passwordValid = hasMinLen && hasUpper && hasLower && hasDigit && hasSpecial && notCommon

    androidx.activity.compose.BackHandler(enabled = step != "home") {
        errorMessage = null
        step = when (step) {
            "login" -> "home"
            "signupInfo" -> "home"
            "signupPassword" -> "signupInfo"
            "verifyEmail" -> "verifyEmail"
            else -> "home"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovaraBg),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF101B24)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                NovaraBorder
            )
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
    painter = painterResource(R.drawable.novara_logo),
    contentDescription = "Novara AI",
    modifier = Modifier.size(72.dp)
)

                Spacer(Modifier.height(15.dp))

                Text(
                    when (step) {
                        "login" -> "Log in"
                        "signupInfo" -> "Create your account"
                        "signupPassword" -> "Set a password"
                        "verifyEmail" -> "Verify your email"
                        else -> "Novara"
                    },
                    color = NovaraText,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    when (step) {
                        "login" -> "Enter your email and password"
                        "signupInfo" -> "Join Novara in seconds"
                        "signupPassword" -> "Almost done"
                        "verifyEmail" -> "Enter the 6-digit code we emailed you"
                        else -> "Log in to your account"
                    },
                    color = NovaraMuted,
                    fontSize = 15.sp
                )

                Spacer(Modifier.height(24.dp))

                if (errorMessage != null) {
                    Text(
                        errorMessage ?: "",
                        color = Color(0xFFEF5350),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                when (step) {
                    "home" -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId("918089443296-0hlo2rm15apjvnse7krrhijpr0cgbfh5.apps.googleusercontent.com")
                                            .build()

                                        val request = androidx.credentials.GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()

                                        val cmResult = credentialManager.getCredential(
                                            request = request,
                                            context = context
                                        )

                                        val credential = cmResult.credential
                                        if (credential is androidx.credentials.CustomCredential &&
                                            credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                        ) {
                                            val googleIdTokenCredential =
                                                com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)

                                            val apiResult = apiPost(
                                                "/api/auth/google-native",
                                                JSONObject().put("id_token", googleIdTokenCredential.idToken)
                                            )

                                            if (apiResult.first) {
                                                apiPost("/api/accept-terms", JSONObject())
                                                onSignupSuccess()
                                            } else {
                                                errorMessage = extractApiError(apiResult.second, "Google sign-in failed")
                                            }
                                        } else {
                                            errorMessage = "Google sign-in failed"
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "${e.javaClass.simpleName}: ${e.message ?: "No error message"}"
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF7F8F8),
                                contentColor = Color(0xFF182027)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "G",
                                    color = Color(0xFF4285F4),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Continue with Google", fontSize = 15.sp)
                            }
                        }

                        Spacer(Modifier.height(11.dp))

                        LoginButton(
                            text = "Continue with email",
                            light = true,
                            onClick = { errorMessage = null; step = "login" }
                        )

                        Spacer(Modifier.height(18.dp))

                        Row {
                            Text(
                                "Don't have an account? ",
                                color = NovaraMuted,
                                fontSize = 14.sp
                            )
                            Text(
                                "Sign up",
                                color = NovaraBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { errorMessage = null; step = "signupInfo" }
                            )
                        }

                        Spacer(Modifier.height(17.dp))

                        Text(
                            if (guestBusy) "Starting..." else "Skip - continue as guest",
                            color = NovaraMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable(enabled = !guestBusy) {
                                guestBusy = true
                                onGuest { guestBusy = false }
                            }
                        )
                    }

                    "login" -> {
                        OutlinedTextField(
                            value = loginEmail,
                            onValueChange = { loginEmail = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Email") },
                            shape = RoundedCornerShape(13.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = { loginPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Password") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            shape = RoundedCornerShape(13.dp)
                        )

                        Spacer(Modifier.height(17.dp))

                        val loginValid = loginEmail.trim().isNotEmpty() && loginPassword.isNotEmpty()

                        Button(
                            onClick = {
                                if (!loginValid || busy) return@Button
                                errorMessage = null
                                busy = true

                                scope.launch {
                                    val result = apiPost(
                                        "/api/login",
                                        JSONObject()
                                            .put("username", loginEmail.trim())
                                            .put("password", loginPassword)
                                    )

                                    busy = false

                                    if (result.first) {
                                        apiPost("/api/accept-terms", JSONObject())
                                        onSignupSuccess()
                                    } else {
                                        errorMessage = extractApiError(result.second, "Log in failed")
                                    }
                                }
                            },
                            enabled = !busy && loginValid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NovaraBlue,
                                contentColor = Color(0xFF302500)
                            )
                        ) {
                            Text(
                                if (busy) "Logging in..." else "Log in",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(13.dp))

                        Text(
                            "Back",
                            color = NovaraMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { errorMessage = null; step = "home" }
                        )
                    }

                    "signupInfo" -> {
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("First name") },
                            shape = RoundedCornerShape(13.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Last name") },
                            shape = RoundedCornerShape(13.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Username") },
                            shape = RoundedCornerShape(13.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = signupEmail,
                            onValueChange = { signupEmail = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Email") },
                            shape = RoundedCornerShape(13.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Phone (optional)") },
                            shape = RoundedCornerShape(13.dp)
                        )

                        Spacer(Modifier.height(17.dp))

                        Button(
                            onClick = {
                                errorMessage = when {
                                    firstName.trim().isEmpty() -> "First name is required."
                                    lastName.trim().isEmpty() -> "Last name is required."
                                    username.trim().length < 3 -> "Username must be at least 3 characters."
                                    !signupEmail.contains("@") || !signupEmail.contains(".") -> "Enter a valid email."
                                    else -> null
                                }

                                if (errorMessage == null) {
                                    step = "signupPassword"
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NovaraBlue,
                                contentColor = Color(0xFF302500)
                            )
                        ) {
                            Text(
                                "Next",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(13.dp))

                        Text(
                            "Already have an account? Log in",
                            color = NovaraMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { errorMessage = null; step = "login" }
                        )
                    }

                    "signupPassword" -> {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Password") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            shape = RoundedCornerShape(13.dp)
                        )

                        Spacer(Modifier.height(10.dp))

                        Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
                            PasswordRuleLine("At least 8 characters", hasMinLen)
                            PasswordRuleLine("An uppercase letter", hasUpper)
                            PasswordRuleLine("A lowercase letter", hasLower)
                            PasswordRuleLine("A number", hasDigit)
                            PasswordRuleLine("A special character", hasSpecial)
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Re-enter your password") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            shape = RoundedCornerShape(13.dp)
                        )

                        Spacer(Modifier.height(17.dp))

                        Button(
                            onClick = {
                                if (!passwordValid) {
                                    errorMessage = "Please meet all password requirements."
                                    return@Button
                                }

                                if (!passwordsMatch) {
                                    errorMessage = "Passwords don't match."
                                    return@Button
                                }

                                if (busy) return@Button
                                errorMessage = null
                                busy = true

                                scope.launch {
                                    val result = apiPost(
                                        "/api/signup",
                                        JSONObject()
                                            .put("first_name", firstName.trim())
                                            .put("last_name", lastName.trim())
                                            .put("username", username.trim())
                                            .put("email", signupEmail.trim())
                                            .put("phone_number", phone.trim())
                                            .put("password", password)
                                            .put("confirm_password", confirmPassword)
                                    )

                                    busy = false

                                    if (result.first) {
                                        step = "verifyEmail"
                                    } else {
                                        errorMessage = extractApiError(result.second, "Sign up failed")
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NovaraBlue,
                                contentColor = Color(0xFF302500)
                            )
                        ) {
                            Text(
                                if (busy) "Creating account..." else "Create account",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(13.dp))

                        Text(
                            "Back",
                            color = NovaraMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { errorMessage = null; step = "signupInfo" }
                        )
                    }

                    "verifyEmail" -> {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("6-digit code") },
                            shape = RoundedCornerShape(13.dp)
                        )

                        Spacer(Modifier.height(17.dp))

                        Button(
                            onClick = {
                                if (code.length != 6 || busy) return@Button
                                errorMessage = null
                                busy = true

                                scope.launch {
                                    val result = apiPost(
                                        "/api/verify-email",
                                        JSONObject().put("code", code)
                                    )

                                    busy = false

                                    if (result.first) {
                                        apiPost("/api/accept-terms", JSONObject())
                                        onSignupSuccess()
                                    } else {
                                        errorMessage = extractApiError(result.second, "Verification failed")
                                    }
                                }
                            },
                            enabled = !busy && code.length == 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NovaraBlue,
                                contentColor = Color(0xFF302500)
                            )
                        ) {
                            Text(
                                if (busy) "Verifying..." else "Verify",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(13.dp))

                        Text(
                            "Resend code",
                            color = NovaraMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val result = apiPost("/api/resend-verification", JSONObject())
                                    Toast.makeText(
                                        context,
                                        if (result.first) "Code resent" else extractApiError(result.second, "Could not resend code"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordRuleLine(label: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (met) "\u2713" else "\u2022",
            color = if (met) Color(0xFF4CAF50) else NovaraMuted,
            fontSize = 13.sp,
            modifier = Modifier.width(18.dp)
        )
        Text(
            label,
            color = if (met) Color(0xFF4CAF50) else NovaraMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun LoginButton(
    text: String,
    light: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor =
                if (light) Color(0xFFF7F8F8)
                else NovaraCard2,
            contentColor =
                if (light) Color(0xFF182027)
                else NovaraText
        )
    ) {
        Text(
            text,
            fontSize = 15.sp
        )
    }
}



@Composable
private fun FeatureChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NovaraCard)
            .border(
                1.dp,
                NovaraBorder,
                RoundedCornerShape(20.dp)
            )
            .padding(
                horizontal = 14.dp,
                vertical = 8.dp
            )
    ) {
        Text(
            text,
            color = NovaraText,
            fontSize = 14.sp
        )
    }
}


@Composable
private fun NovaraLogoSplash(
    onFinished: () -> Unit
) {
    var stage by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(900)
        stage = 1

        kotlinx.coroutines.delay(1100)
        stage = 2

        kotlinx.coroutines.delay(900)
        onFinished()
    }

    val status = when (stage) {
        0 -> "Loading"
        1 -> "Initializing"
        else -> "Ready"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF030912),
                        Color(0xFF071A32),
                        Color(0xFF02060B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.novara_logo),
                contentDescription = "Novara AI",
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(28.dp))
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Welcome to Novara",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = status,
                color = Color(0xFF9DB0C7),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == stage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == stage)
                                    Color(0xFF20CFFF)
                                else
                                    Color(0xFF43566C)
                            )
                    )
                }
            }
        }
    }
}
