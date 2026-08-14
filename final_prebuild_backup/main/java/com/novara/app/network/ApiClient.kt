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
