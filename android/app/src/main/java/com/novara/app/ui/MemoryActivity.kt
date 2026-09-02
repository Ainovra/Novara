package com.novara.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novara.app.network.ApiClient
import com.novara.app.ui.theme.NovaraTheme
import kotlinx.coroutines.launch

class MemoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NovaraTheme {
                MemoryScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryScreen(
    onBack: () -> Unit
) {
    var memories by remember {
        mutableStateOf<List<ApiClient.MemoryItem>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var deleting by remember {
        mutableStateOf<String?>(null)
    }

    var showClearDialog by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    fun loadMemories() {
        scope.launch {
            loading = true
            error = null

            when (val result = ApiClient.getMemories()) {
                is ApiClient.ApiResult.Success -> {
                    memories = result.data
                    loading = false
                }

                is ApiClient.ApiResult.Failure -> {
                    error = result.message
                    loading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadMemories()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Your memory",
                        fontWeight = FontWeight.SemiBold
                    )
                },
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
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            Text(
                "Novara's saved memories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "Review or remove information Novara has remembered.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))

            if (memories.isNotEmpty()) {
                Button(
                    onClick = {
                        showClearDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null
                    )

                    Spacer(Modifier.padding(horizontal = 4.dp))

                    Text("Clear all memories")
                }

                Spacer(Modifier.height(12.dp))
            }

            when {
                loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(32.dp))
                        CircularProgressIndicator()
                    }
                }

                error != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            error ?: "Something went wrong.",
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(Modifier.height(12.dp))

                        TextButton(
                            onClick = { loadMemories() }
                        ) {
                            Text("Try again")
                        }
                    }
                }

                memories.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No memories yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            "When Novara remembers something useful, it will appear here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = memories,
                            key = { it.id }
                        ) { memory ->

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            memory.content,
                                            style =
                                                MaterialTheme.typography.bodyLarge
                                        )

                                        Spacer(
                                            Modifier.height(6.dp)
                                        )

                                        Text(
                                            buildString {
                                                memory.source?.let {
                                                    append(it.replaceFirstChar { c ->
                                                        c.uppercase()
                                                    })
                                                }

                                                memory.createdAt
                                                    ?.take(10)
                                                    ?.let {
                                                        if (isNotEmpty()) {
                                                            append(" • ")
                                                        }
                                                        append(it)
                                                    }
                                            },
                                            style =
                                                MaterialTheme.typography.bodySmall,
                                            color =
                                                MaterialTheme.colorScheme
                                                    .onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            deleting = memory.id
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription =
                                                "Delete memory"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleting != null) {
        AlertDialog(
            onDismissRequest = {
                deleting = null
            },
            title = {
                Text("Delete memory?")
            },
            text = {
                Text("This memory will be permanently removed.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = deleting
                        deleting = null

                        if (id != null) {
                            scope.launch {
                                when (
                                    val result =
                                        ApiClient.deleteMemory(id)
                                ) {
                                    is ApiClient.ApiResult.Success -> {
                                        memories =
                                            memories.filter {
                                                it.id != id
                                            }
                                    }

                                    is ApiClient.ApiResult.Failure -> {
                                        error = result.message
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleting = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = {
                showClearDialog = false
            },
            title = {
                Text("Clear all memories?")
            },
            text = {
                Text(
                    "All saved memories will be permanently removed."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false

                        scope.launch {
                            when (
                                val result =
                                    ApiClient.clearMemories()
                            ) {
                                is ApiClient.ApiResult.Success -> {
                                    memories = emptyList()
                                }

                                is ApiClient.ApiResult.Failure -> {
                                    error = result.message
                                }
                            }
                        }
                    }
                ) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
