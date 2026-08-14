package com.novara.app.network

import com.novara.app.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

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

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val BASE_URL = Config.WEB_BASE_URL

    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Failure(val message: String) : ApiResult<Nothing>()
    }

    data class AuthResponse(val ok: Boolean, val redirect: String?)

    private suspend fun postJson(path: String, body: JSONObject): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(BASE_URL + path)
                    .post(body.toString().toRequestBody(JSON))
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val json = if (text.isNotBlank()) JSONObject(text) else JSONObject()
                    if (response.isSuccessful) {
                        val redirect = if (json.has("redirect") && !json.isNull("redirect"))
                            json.getString("redirect") else null
                        ApiResult.Success(AuthResponse(ok = json.optBoolean("ok", true), redirect = redirect))
                    } else {
                        ApiResult.Failure(json.optString("error", "Something went wrong (${response.code})."))
                    }
                }
            } catch (e: IOException) {
                ApiResult.Failure("Can't reach Novara — check your connection.")
            }
        }

    suspend fun login(username: String, password: String): ApiResult<AuthResponse> =
        postJson("/api/login", JSONObject().put("username", username).put("password", password))

    suspend fun signup(username: String, password: String): ApiResult<AuthResponse> =
        postJson("/api/signup", JSONObject().put("username", username).put("password", password))

    suspend fun guestLogin(): ApiResult<AuthResponse> =
        postJson("/api/guest", JSONObject())

    suspend fun acceptTerms(): ApiResult<AuthResponse> =
        postJson("/api/accept-terms", JSONObject())

    // Exchanges the one-time ?code=... from the novara://auth-complete deep
    // link for a real session. The response is a redirect (not JSON) that
    // sets the session cookie on itself — so we read it with redirects
    // disabled and take the Location header as the next screen to show.
    suspend fun consumeAuthCode(code: String): ApiResult<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val noRedirectClient = client.newBuilder().followRedirects(false).build()
            val httpUrl = (BASE_URL + Config.MOBILE_CONSUME_PATH).toHttpUrl().newBuilder()
                .addQueryParameter("code", code)
                .build()
            val request = Request.Builder().url(httpUrl).get().build()
            noRedirectClient.newCall(request).execute().use { response ->
                when {
                    response.code in 300..399 ->
                        ApiResult.Success(AuthResponse(ok = true, redirect = response.header("Location")))
                    response.isSuccessful ->
                        ApiResult.Success(AuthResponse(ok = true, redirect = null))
                    else ->
                        ApiResult.Failure("Google sign-in couldn't be completed (${response.code}).")
                }
            }
        } catch (e: IOException) {
            ApiResult.Failure("Can't reach Novara — check your connection.")
        }
    }
    // ==== ADD everything below, right before ApiClient's final closing `}` ====

    data class ChatMessage(
        val role: String,       // "user" or "assistant"
        val text: String
    )

    data class ChatReply(
        val reply: String,
        val messageId: String,
        val conversationId: String
    )

    /**
     * Sends one chat message (text-only for now — no attachments, no
     * web-search/image-gen toggles, no model picker; those come later).
     * Passing conversationId = null starts a new conversation; the server
     * returns the real id in the response either way.
     */
    suspend fun sendMessage(text: String, conversationId: String?, model: String = "fast"): ApiResult<ChatReply> =
        withContext(Dispatchers.IO) {
            try {
                val formBuilder = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("message", text)
                    .addFormDataPart("web_search", "false")
                    .addFormDataPart("image_gen", "false")
                    .addFormDataPart("model", model)
                if (conversationId != null) {
                    formBuilder.addFormDataPart("conversation_id", conversationId)
                }
                val request = Request.Builder()
                    .url(BASE_URL + "/api/chat")
                    .post(formBuilder.build())
                    .build()
                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    val json = if (bodyText.isNotBlank()) JSONObject(bodyText) else JSONObject()
                    if (response.isSuccessful && json.has("reply")) {
                        ApiResult.Success(
                            ChatReply(
                                reply = json.getString("reply"),
                                messageId = json.optString("message_id", ""),
                                conversationId = json.optString("conversation_id", conversationId ?: "")
                            )
                        )
                    } else {
                        ApiResult.Failure(json.optString("error", "Something went wrong (${response.code})."))
                    }
                }
            } catch (e: IOException) {
                ApiResult.Failure("Can't reach Novara — check your connection.")
            }
        }

    /** Loads an existing conversation's message history (used when resuming a chat from the sidebar later). */
    suspend fun getMessages(conversationId: String): ApiResult<List<ChatMessage>> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(BASE_URL + "/api/conversations/$conversationId/messages")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use ApiResult.Failure("Couldn't load this chat (${response.code}).")
                    }
                    val json = JSONObject(bodyText)
                    val arr = json.optJSONArray("messages") ?: org.json.JSONArray()
                    val list = mutableListOf<ChatMessage>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(ChatMessage(role = obj.optString("role"), text = obj.optString("text")))
                    }
                    ApiResult.Success(list)
                }
            } catch (e: IOException) {
                ApiResult.Failure("Can't reach Novara — check your connection.")
            }
        }
}
