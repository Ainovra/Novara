package com.novara.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NovaraUpdateSheet(
    version: String,
    changelog: List<String>,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NovaraBrand.Surface,
        shape = RoundedCornerShape(
            topStart = NovaraBrand.SheetRadius,
            topEnd = NovaraBrand.SheetRadius
        ),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 12.dp,
                    bottom = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF536273))
            )

            Spacer(Modifier.height(20.dp))

            Image(
                painter = painterResource(R.drawable.novara_logo),
                contentDescription = "Novara",
                modifier = Modifier.size(76.dp)
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Update Available",
                color = NovaraBrand.Text,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(7.dp))

            Surface(
                color = NovaraBrand.Primary,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "New Version $version",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 6.dp
                    )
                )
            }

            Spacer(Modifier.height(18.dp))

            if (changelog.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    changelog.take(5).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                color = NovaraBrand.Cyan,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(end = 9.dp)
                            )

                            Text(
                                text = item,
                                color = NovaraBrand.SecondaryText,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovaraBrand.Primary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    "Update Now",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(9.dp))

            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = onLater,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFB8C5D3)
                )
            ) {
                Text(
                    "Later",
                    fontSize = 14.sp
                )
            }
        }
    }
}
