package com.novara.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val SplashBg = Color(0xFF071018)
private val SplashText = Color(0xFFF4F7F9)
private val SplashMuted = Color(0xFFB6C2CA)
private val SplashBlue = Color(0xFF28B7FF)
private val SplashPurple = Color(0xFF7548FF)

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var progress by remember { mutableStateOf(0f) }
            var statusLabel by remember { mutableStateOf("Loading...") }

            LaunchedEffect(Unit) {
                delay(500)
                statusLabel = "Initializing..."
                for (i in 1..30) {
                    progress = i / 30f
                    delay(20)
                }
                delay(300)
                startActivity(
                    Intent(
                        this@SplashActivity,
                        MainActivity::class.java
                    )
                )
                finish()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SplashBg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.novara_logo),
                        contentDescription = "Novara",
                        modifier = Modifier.height(90.dp)
                    )

                    Spacer(Modifier.height(18.dp))

                    Row {
                        Text(
                            "Novara",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = SplashText
                        )
                        Text(
                            " AI",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            style = LocalTextStyle.current.copy(
                                brush = Brush.horizontalGradient(
                                    listOf(SplashPurple, SplashBlue)
                                )
                            )
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "Think • Create • Explore",
                        fontSize = 13.sp,
                        color = SplashMuted
                    )

                    Spacer(Modifier.height(40.dp))

                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .width(160.dp)
                            .height(4.dp),
                        color = SplashBlue,
                        trackColor = Color(0xFF1B2733)
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        statusLabel,
                        fontSize = 12.sp,
                        color = SplashMuted
                    )
                }
            }
        }
    }
}
