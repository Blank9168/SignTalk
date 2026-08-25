package com.example.signtalk.ui.dictionary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.signtalk.domain.model.DictionaryEntry
import com.example.signtalk.domain.repository.DictionaryRepository
import com.example.signtalk.ui.components.SignTalkTopBar
import kotlinx.coroutines.launch

@Composable
fun DictionaryDetailScreen(
    entryId: Long,
    repository: DictionaryRepository,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    var entry by remember { mutableStateOf<DictionaryEntry?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(entryId) {
        entry = repository.getEntry(entryId)
    }

    Scaffold(topBar = { SignTalkTopBar(entry?.displayName ?: "Sign", onBack) }) { padding ->
        val currentEntry = entry
        if (currentEntry == null) {
            Text(
                "Loading...",
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
        ) {
            Text(currentEntry.emoji, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text(currentEntry.category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(currentEntry.description, style = MaterialTheme.typography.bodyLarge)

            if (currentEntry.isUserAdded) {
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteEntry(currentEntry.id)
                            onDeleted()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete entry")
                }
            }
        }
    }
}
