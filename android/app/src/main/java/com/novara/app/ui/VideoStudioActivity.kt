package com.novara.app.ui

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.novara.app.network.ApiClient
import com.novara.app.ui.theme.NovaraTheme
import kotlinx.coroutines.launch

class VideoStudioActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NovaraTheme {
                VideoStudioScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoStudioScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var videoType by remember { mutableStateOf("silent") }
    var isGenerating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Studio") },
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

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text(
                text = "Create an AI video",
                fontSize = 24.sp
            )

            Text(
                text = "Describe the video you want Novara to generate."
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Video prompt") },
                placeholder = {
                    Text("Example: A cinematic sunset over the mountains")
                },
                minLines = 5
            )

            Text(
                text = "Video type",
                fontSize = 18.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                FilterChip(
                    selected = videoType == "silent",
                    onClick = { videoType = "silent" },
                    label = { Text("Silent video") }
                )

                FilterChip(
                    selected = videoType == "audio_video",
                    onClick = { videoType = "audio_video" },
                    label = { Text("Audio + video") }
                )
            }

            Button(
                onClick = {
                    if (prompt.trim().isEmpty()) {
                        status = "Please enter a video description first."
                        return@Button
                    }

                    scope.launch {
                        isGenerating = true
                        status = "Generating video..."
                        videoUrl = null

                        when (
                            val result = ApiClient.generateVideo(
                                prompt = prompt.trim(),
                                videoType = videoType
                            )
                        ) {
                            is ApiClient.ApiResult.Success -> {
                                val response = result.data

                                if (!response.videoUrl.isNullOrBlank()) {
                                    videoUrl = response.videoUrl
                                    status = "Video generated successfully."
                                } else {
                                    status = "Video was generated, but no video URL was returned."
                                }
                            }

                            is ApiClient.ApiResult.Failure -> {
                                status = result.message
                            }
                        }

                        isGenerating = false
                    }
                },
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isGenerating) "Generating..." else "Generate Video"
                )
            }

            if (status.isNotBlank()) {
                Text(
                    text = status,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!videoUrl.isNullOrBlank()) {
                Text(
                    text = "Generated video",
                    fontSize = 20.sp
                )

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    factory = { context ->
                        VideoView(context).apply {
                            val controller = MediaController(context)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                        }
                    },
                    update = { view ->
                        view.setVideoURI(Uri.parse(videoUrl))
                        view.setOnPreparedListener { player ->
                            player.isLooping = false
                        }
                    }
                )
            }
        }
    }
}
