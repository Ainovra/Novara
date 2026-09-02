package com.novara.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NovaraUpdatePopup(
    version: String,
    changelog: List<String>,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp
        ),
        tonalElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Update Available",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(6.dp))

            Text("New Version $version")

            Spacer(Modifier.height(16.dp))

            changelog.forEach {
                Text(
                    text = "• $it",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onUpdate
            ) {
                Text("Update Now  ↗")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onLater
            ) {
                Text("Later")
            }
        }
    }
}
