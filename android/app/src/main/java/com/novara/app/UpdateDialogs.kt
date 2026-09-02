package com.novara.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardBg = Color(0xFF101D27)
private val CardBorder = Color(0xFF263B49)
private val TextMain = Color(0xFFF4F7F9)
private val TextMuted = Color(0xFFB6C2CA)
private val AccentBlue = Color(0xFF28B7FF)

@Composable
fun MajorUpdateSheet(
    versionName: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(24.dp)
        ) {
            Text(
                "Update Available",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "New Version $versionName",
                fontSize = 13.sp,
                color = AccentBlue
            )

            Spacer(Modifier.height(16.dp))

            Text("• New features", fontSize = 14.sp, color = TextMuted)
            Text("• Bug fixes", fontSize = 14.sp, color = TextMuted)
            Text("• Improvements", fontSize = 14.sp, color = TextMuted)

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Update Now")
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onLater,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Later", color = TextMuted)
            }
        }
    }
}
