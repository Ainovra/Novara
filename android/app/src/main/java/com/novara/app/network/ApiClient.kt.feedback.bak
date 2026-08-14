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
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ApiClient {

    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(
            url: HttpUrl,
            cookies: List<Cookie>
        ) {
            if (cookies.isNotEmpty()) {
                cookieStore[url.host] = cookies
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
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

    data class ChatMessage(
        val id: String = "",
        val role: String,
        val text: String,
        val attachmentPath: String? = null,
        val attachmentType: String? = null,
        val sources: String? = null,
        val feedback: Int? = null
    )

    data class ChatReply(
        val reply: String,
        val messageId: String,
        val conversationId: String,
        val generatedImage: String? = null,
        val sources: String? = null
    )

    data class Conversation(
        val id: String,
        val title: String,
        val createdAt: String
    )

    data class ShareResponse(
        val shareId: String?,
        val url: String?
    )

    data class UploadResult(
        val path: String,
        val type: String,
        val url: String
    )

    private fun errorMessage(
        responseCode: Int,
        body: String
    ): String {
        return try {
            val json = JSONObject(body)
            json.optString(
                "error",
                "Request failed ($responseCode)."
            )
        } catch (_: Exception) {
            "Request failed ($responseCode)."
        }
    }

    private suspend fun postJson(
        path: String,
        body: JSONObject
    ): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(BASE_URL + path)
                    .post(
                        body.toString()
                            .toRequestBody(JSON)
                    )
                    .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        val text =
                            response.body?.string().orEmpty()

                        if (!response.isSuccessful) {
                            return@withContext ApiResult.Failure(
                                errorMessage(
                                    response.code,
                                    text
                                )
                            )
                        }

                        val json =
                            if (text.isNotBlank())
                                JSONObject(text)
                            else
                                JSONObject()

                        ApiResult.Success(
                            AuthResponse(
                                ok = json.optBoolean(
                                    "ok",
                                    true
                                ),
                                redirect =
                                    if (
                                        json.has("redirect") &&
                                        !json.isNull("redirect")
                                    ) {
                                        json.getString(
                                            "redirect"
                                        )
                                    } else {
                                        null
                                    }
                            )
                        )
                    }
            } catch (e: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
                )
            } catch (e: Exception) {
                ApiResult.Failure(
                    e.message ?: "Unexpected error."
                )
            }
        }

    // =========================
    // AUTH
    // =========================

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

    suspend fun guestLogin():
        ApiResult<AuthResponse> =
        postJson(
            "/api/guest",
            JSONObject()
        )

    suspend fun acceptTerms():
        ApiResult<AuthResponse> =
        postJson(
            "/api/accept-terms",
            JSONObject()
        )

    suspend fun consumeAuthCode(
        code: String
    ): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val noRedirectClient =
                    client.newBuilder()
                        .followRedirects(false)
                        .build()

                val httpUrl =
                    (
                        BASE_URL +
                            Config.MOBILE_CONSUME_PATH
                        )
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter(
                            "code",
                            code
                        )
                        .build()

                val request =
                    Request.Builder()
                        .url(httpUrl)
                        .get()
                        .build()

                noRedirectClient.newCall(request)
                    .execute()
                    .use { response ->

                        when {
                            response.code in 300..399 ->
                                ApiResult.Success(
                                    AuthResponse(
                                        true,
                                        response.header(
                                            "Location"
                                        )
                                    )
                                )

                            response.isSuccessful ->
                                ApiResult.Success(
                                    AuthResponse(
                                        true,
                                        null
                                    )
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

    // =========================
    // CHAT
    // =========================

    suspend fun sendMessage(
        text: String,
        conversationId: String?,
        model: String = "fast",
        webSearch: Boolean = false,
        imageGeneration: Boolean = false,
        attachment: File? = null
    ): ApiResult<ChatReply> =
        withContext(Dispatchers.IO) {

            try {

                val form =
                    MultipartBody.Builder()
                        .setType(
                            MultipartBody.FORM
                        )
                        .addFormDataPart(
                            "message",
                            text
                        )
                        .addFormDataPart(
                            "web_search",
                            webSearch.toString()
                        )
                        .addFormDataPart(
                            "image_gen",
                            imageGeneration.toString()
                        )
                        .addFormDataPart(
                            "model",
                            model
                        )

                if (conversationId != null) {
                    form.addFormDataPart(
                        "conversation_id",
                        conversationId
                    )
                }

                if (attachment != null) {
                    val mime =
                        when {
                            attachment.extension
                                .lowercase() in
                                setOf(
                                    "jpg",
                                    "jpeg",
                                    "png",
                                    "webp",
                                    "gif"
                                ) ->
                                "image/*"

                            attachment.extension
                                .lowercase() in
                                setOf(
                                    "mp4",
                                    "mov",
                                    "webm",
                                    "mkv",
                                    "avi"
                                ) ->
                                "video/*"

                            else ->
                                "application/octet-stream"
                        }

                    form.addFormDataPart(
                        "file",
                        attachment.name,
                        attachment
                            .asRequestBody(
                                mime.toMediaType()
                            )
                    )
                }

                val request =
                    Request.Builder()
                        .url(BASE_URL + "/api/chat")
                        .post(form.build())
                        .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        val body =
                            response.body
                                ?.string()
                                .orEmpty()

                        if (!response.isSuccessful) {
                            return@withContext ApiResult.Failure(
                                errorMessage(
                                    response.code,
                                    body
                                )
                            )
                        }

                        val json =
                            JSONObject(body)

                        if (!json.has("reply")) {
                            return@withContext ApiResult.Failure(
                                "Novara returned an invalid chat response."
                            )
                        }

                        ApiResult.Success(
                            ChatReply(
                                reply =
                                    json.getString(
                                        "reply"
                                    ),
                                messageId =
                                    json.optString(
                                        "message_id"
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
                                    ),
                                sources =
                                    json.optString(
                                        "sources",
                                        null
                                    )
                            )
                        )
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
                )
            } catch (e: Exception) {
                ApiResult.Failure(
                    e.message ?: "Chat request failed."
                )
            }
        }

    // =========================
    // CONVERSATIONS
    // =========================

    suspend fun getConversations():
        ApiResult<List<Conversation>> =
        withContext(Dispatchers.IO) {

            try {
                val request =
                    Request.Builder()
                        .url(
                            BASE_URL +
                                "/api/conversations"
                        )
                        .get()
                        .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        val body =
                            response.body
                                ?.string()
                                .orEmpty()

                        if (!response.isSuccessful) {
                            return@withContext ApiResult.Failure(
                                errorMessage(
                                    response.code,
                                    body
                                )
                            )
                        }

                        val json =
                            JSONObject(body)

                        val array =
                            json.optJSONArray(
                                "conversations"
                            ) ?: JSONArray()

                        val result =
                            mutableListOf<Conversation>()

                        for (i in 0 until array.length()) {
                            val item =
                                array.getJSONObject(i)

                            result.add(
                                Conversation(
                                    id =
                                        item.optString(
                                            "id"
                                        ),
                                    title =
                                        item.optString(
                                            "title",
                                            "New chat"
                                        ),
                                    createdAt =
                                        item.optString(
                                            "created_at"
                                        )
                                )
                            )
                        }

                        ApiResult.Success(result)
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
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
                            response.body
                                ?.string()
                                .orEmpty()

                        if (!response.isSuccessful) {
                            return@withContext ApiResult.Failure(
                                errorMessage(
                                    response.code,
                                    body
                                )
                            )
                        }

                        val json =
                            JSONObject(body)

                        val array =
                            json.optJSONArray(
                                "messages"
                            ) ?: JSONArray()

                        val result =
                            mutableListOf<ChatMessage>()

                        for (i in 0 until array.length()) {

                            val item =
                                array.getJSONObject(i)

                            val feedback =
                                if (
                                    item.has("feedback") &&
                                    !item.isNull("feedback")
                                ) {
                                    item.optInt(
                                        "feedback"
                                    )
                                } else {
                                    null
                                }

                            result.add(
                                ChatMessage(
                                    id =
                                        item.optString(
                                            "id"
                                        ),
                                    role =
                                        item.optString(
                                            "role"
                                        ),
                                    text =
                                        item.optString(
                                            "text"
                                        ),
                                    attachmentPath =
                                        item.optString(
                                            "attachment_path",
                                            null
                                        ),
                                    attachmentType =
                                        item.optString(
                                            "attachment_type",
                                            null
                                        ),
                                    sources =
                                        item.optString(
                                            "sources",
                                            null
                                        ),
                                    feedback =
                                        feedback
                                )
                            )
                        }

                        ApiResult.Success(result)
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
                )
            }
        }

    suspend fun deleteConversation(
        conversationId: String
    ): ApiResult<Boolean> =
        withContext(Dispatchers.IO) {

            try {

                val request =
                    Request.Builder()
                        .url(
                            BASE_URL +
                                "/api/conversations/" +
                                conversationId
                        )
                        .delete()
                        .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        if (!response.isSuccessful) {
                            val body =
                                response.body
                                    ?.string()
                                    .orEmpty()

                            return@withContext ApiResult.Failure(
                                errorMessage(
                                    response.code,
                                    body
                                )
                            )
                        }

                        ApiResult.Success(true)
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
                )
            }
        }

    suspend fun renameConversation(
        conversationId: String,
        title: String
    ): ApiResult<Boolean> =
        withContext(Dispatchers.IO) {

            try {

                val request =
                    Request.Builder()
                        .url(
                            BASE_URL +
                                "/api/conversations/" +
                                conversationId +
                                "/rename"
                        )
                        .post(
                            JSONObject()
                                .put("title", title)
                                .toString()
                                .toRequestBody(JSON)
                        )
                        .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        if (!response.isSuccessful) {
                            val body =
                                response.body
                                    ?.string()
                                    .orEmpty()

                            return@withContext ApiResult.Failure(
                                errorMessage(
                                    response.code,
                                    body
                                )
                            )
                        }

                        ApiResult.Success(true)
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
                )
            }
        }

    // =========================
    // SHARE
    // =========================

    suspend fun shareConversation(
        conversationId: String
    ): ApiResult<ShareResponse> =
        withContext(Dispatchers.IO) {

            try {

                val request =
                    Request.Builder()
                        .url(
                            BASE_URL +
                                "/api/conversations/" +
                                conversationId +
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
                            response.body
                                ?.string()
                                .orEmpty()

                        if (!response.isSuccessful) {
                            return@withContext ApiResult.Failure(
                                errorMessage(
                                    response.code,
                                    body
                                )
                            )
                        }

                        val json =
                            JSONObject(
                                if (
                                    body.isNotBlank()
                                ) body
                                else "{}"
                            )

                        val shareId =
                            json.optString(
                                "share_id",
                                null
                            )

                        val url =
                            if (
                                shareId != null
                            ) {
                                "$BASE_URL/share/$shareId"
                            } else {
                                null
                            }

                        ApiResult.Success(
                            ShareResponse(
                                shareId,
                                url
                            )
                        )
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
                )
            }
        }

    suspend fun unshareConversation(
        conversationId: String
    ): ApiResult<Boolean> =
        withContext(Dispatchers.IO) {

            try {

                val request =
                    Request.Builder()
                        .url(
                            BASE_URL +
                                "/api/conversations/" +
                                conversationId +
                                "/unshare"
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

                        if (!response.isSuccessful) {
                            val body =
                                response.body
                                    ?.string()
                                    .orEmpty()

                            return@withContext ApiResult.Failure(
                                errorMessage(
                                    response.code,
                                    body
                                )
                            )
                        }

                        ApiResult.Success(true)
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
                )
            }
        }

    // =========================
    // MESSAGE FEEDBACK
    // =========================

    suspend fun sendFeedback(
        messageId: String,
        feedback: Int
    ): ApiResult<Boolean> =
        withContext(Dispatchers.IO) {

            try {

                val request =
                    Request.Builder()
                        .url(
                            BASE_URL +
                                "/api/messages/" +
                                messageId +
                                "/feedback"
                        )
                        .post(
                            JSONObject()
                                .put(
                                    "feedback",
                                    feedback
                                )
                                .toString()
                                .toRequestBody(JSON)
                        )
                        .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        if (!response.isSuccessful) {
                            val body =
                                response.body
                                    ?.string()
                                    .orEmpty()

                            return@withContext ApiResult.Failure(
                                errorMessage(
                                    response.code,
                                    body
                                )
                            )
                        }

                        ApiResult.Success(true)
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
                )
            }
        }

    // =========================
    // VIDEO
    // =========================

    suspend fun generateVideo(
        prompt: String
    ): ApiResult<String> =
        withContext(Dispatchers.IO) {

            try {

                val request =
                    Request.Builder()
                        .url(
                            BASE_URL +
                                "/api/video/generate"
                        )
                        .post(
                            JSONObject()
                                .put(
                                    "prompt",
                                    prompt
                                )
                                .toString()
                                .toRequestBody(JSON)
                        )
                        .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        val body =
                            response.body
                                ?.string()
                                .orEmpty()

                        if (!response.isSuccessful) {
                            return@withContext ApiResult.Failure(
                                errorMessage(
                                    response.code,
                                    body
                                )
                            )
                        }

                        ApiResult.Success(body)
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't reach Novara — check your connection."
                )
            }
        }

    // =========================
    // FILE UPLOAD
    // =========================

    suspend fun uploadFile(
        file: File
    ): ApiResult<UploadResult> =
        withContext(Dispatchers.IO) {

            try {

                val mime =
                    when (
                        file.extension.lowercase()
                    ) {
                        "jpg", "jpeg" ->
                            "image/jpeg"

                        "png" ->
                            "image/png"

                        "webp" ->
                            "image/webp"

                        "gif" ->
                            "image/gif"

                        "mp4" ->
                            "video/mp4"

                        "mov" ->
                            "video/quicktime"

                        "webm" ->
                            "video/webm"

                        "pdf" ->
                            "application/pdf"

                        else ->
                            "application/octet-stream"
                    }

                val multipart =
                    MultipartBody.Builder()
                        .setType(
                            MultipartBody.FORM
                        )
                        .addFormDataPart(
                            "file",
                            file.name,
                            file.asRequestBody(
                                mime.toMediaType()
                            )
                        )
                        .build()

                val request =
                    Request.Builder()
                        .url(
                            BASE_URL +
                                "/api/chat"
                        )
                        .post(multipart)
                        .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        val body =
                            response.body
                                ?.string()
                                .orEmpty()

                        if (!response.isSuccessful) {
                            return@withContext ApiResult.Failure(
                                errorMessage(
                                    response.code,
                                    body
                                )
                            )
                        }

                        val json =
                            JSONObject(
                                if (
                                    body.isNotBlank()
                                ) body
                                else "{}"
                            )

                        ApiResult.Success(
                            UploadResult(
                                path =
                                    json.optString(
                                        "attachment_path",
                                        file.name
                                    ),
                                type =
                                    json.optString(
                                        "attachment_type",
                                        "file"
                                    ),
                                url =
                                    json.optString(
                                        "attachment_url",
                                        "$BASE_URL/uploads/${file.name}"
                                    )
                            )
                        )
                    }

            } catch (_: IOException) {
                ApiResult.Failure(
                    "Can't upload file — check your connection."
                )
            }
        }

    fun uploadUrl(
        filename: String
    ): String {
        return "$BASE_URL/uploads/$filename"
    }
}
