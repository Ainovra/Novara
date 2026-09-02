package com.novara.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

object NovaraBrand {
    val Background = Color(0xFF071018)
    val Surface = Color(0xFF0B1520)
    val SurfaceElevated = Color(0xFF101C2A)

    val Cyan = Color(0xFF20CFFF)
    val Purple = Color(0xFF6D3BFF)
    val Primary = Color(0xFF1265E8)

    val Text = Color(0xFFF4F7FA)
    val SecondaryText = Color(0xFF9DB0C7)
    val MutedText = Color(0xFF71849A)

    val CornerRadius = 16.dp
    val LargeCornerRadius = 24.dp
    val SheetRadius = 30.dp

    val HorizontalPadding = 24.dp
    val ButtonHeight = 50.dp
}

@Composable
fun NovaraBranding(
    logoSize: Dp = 72.dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.novara_logo),
            contentDescription = "Novara AI",
            modifier = Modifier.size(logoSize)
        )
    }
}
