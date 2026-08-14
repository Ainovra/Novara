package com.novara.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novara.app.network.ApiClient
import kotlinx.coroutines.launch

private val TERMS_SECTIONS = listOf(
    "1. Acceptable use" to "By using Novara, you agree not to use it for anything illegal, harmful, or intended to hurt others.",
    "2. AI responses aren't guaranteed to be correct" to "Novara generates AI responses that can sometimes be wrong or incomplete. Verify anything important yourself before relying on it.",
    "3. Your data" to "Your chats are linked to your account so you can revisit them later. They may be used to improve the service.",
    "4. Your content" to "You're responsible for what you do with any content you generate (text, video, images).",
    "5. Chat sharing" to "When you share a chat, a public link is created that anyone with the link can view. Only share chats that don't contain sensitive information.",
    "6. Changes to the service" to "Features may be updated, changed, or discontinued at any time without prior notice.",
    "7. Minimum age" to "You must be at least 13 years old (or the minimum age required in your country) to use Novara."
)

@Composable
fun TermsScreen(onAccepted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var checked by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Terms & Conditions", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Before you continue, please read our Terms & Conditions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            TERMS_SECTIONS.forEach { (title, body) ->
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            Text("I have read the Terms & Conditions and I agree to them.")
        }

        errorText?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                loading = true
                scope.launch {
                    val result = ApiClient.acceptTerms()
                    loading = false
                    when (result) {
                        is ApiClient.ApiResult.Success -> onAccepted()
                        is ApiClient.ApiResult.Failure -> errorText = result.message
                    }
                }
            },
            enabled = checked && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Please wait..." else "Continue")
        }
    }
}
