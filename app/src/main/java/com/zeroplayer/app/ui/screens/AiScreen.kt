package com.zeroplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.OutLinePlayRow
import com.zeroplayer.app.ui.components.SectionHeader
import com.zeroplayer.app.ui.components.ZeroDivider
import com.zeroplayer.app.ui.components.ZeroIconButton
import com.zeroplayer.app.ui.nav.LocalBottomInset
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.theme.Zero
import kotlinx.coroutines.launch

/**
 * Optional AI assist: an OpenAI-compatible endpoint, the artist playlist
 * builder and the "name unknown artists" pass. Everything is manual and
 * reversible; nothing runs until the user opens this screen.
 */
@Composable
fun AiScreen() {
    val nav = LocalNav.current
    val prefs = Graph.prefs
    val scope = rememberCoroutineScope()
    val bottomInset = LocalBottomInset.current

    var apiKey by remember { mutableStateOf(prefs.aiApiKey) }
    var baseUrl by remember { mutableStateOf(prefs.aiBaseUrl) }
    var model by remember { mutableStateOf(prefs.aiModel) }
    var autoArtists by remember { mutableStateOf(prefs.autoArtistPlaylists) }

    var testing by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }

    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    var naming by remember { mutableStateOf(false) }
    var namingProgress by remember { mutableStateOf<String?>(null) }
    var namingMessage by remember { mutableStateOf<String?>(null) }

    val ready = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Zero.Black),
        contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZeroIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = { nav.pop() },
                )
                Spacer(Modifier.weight(1f))
                MicroLabel("AI")
                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.size(40.dp))
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                Text("AI assist", style = MaterialTheme.typography.displayMedium, color = Zero.TextPrimary)
                Spacer(Modifier.height(4.dp))
                MicroLabel("Optional · runs only when you ask")
                Spacer(Modifier.height(18.dp))
            }
        }

        item { SectionHeader(title = "Assistant", subtitle = "Any OpenAI-compatible endpoint") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Zero.Surface1)
                    .padding(16.dp),
            ) {
                AiField(
                    label = "API key",
                    value = apiKey,
                    placeholder = "Paste your provider key",
                    masked = true,
                    onChange = {
                        apiKey = it
                        prefs.aiApiKey = it.trim()
                    },
                )
                Spacer(Modifier.height(14.dp))
                AiField(
                    label = "Base URL",
                    value = baseUrl,
                    placeholder = "https://api.provider.com/v1",
                    onChange = {
                        baseUrl = it
                        prefs.aiBaseUrl = it.trim()
                    },
                )
                Spacer(Modifier.height(14.dp))
                AiField(
                    label = "Model",
                    value = model,
                    placeholder = "model-name",
                    onChange = {
                        model = it
                        prefs.aiModel = it.trim()
                    },
                )
                Spacer(Modifier.height(16.dp))
                OutLinePlayRow(
                    icon = Icons.Rounded.AutoAwesome,
                    label = when {
                        testing -> "Testing…"
                        !ready -> "Add a key to enable the assistant"
                        testOk -> "Connected · test again"
                        else -> "Test connection"
                    },
                    onClick = {
                        if (testing) return@OutLinePlayRow
                        if (!ready) {
                            testOk = false
                            testMessage = "Fill in the key, base URL and model first."
                            return@OutLinePlayRow
                        }
                        testing = true
                        testMessage = null
                        scope.launch {
                            val result = runCatching { Graph.ai.test() }
                            testing = false
                            result.onSuccess {
                                testOk = true
                                testMessage = "The model answered: ${it.take(40)}"
                            }.onFailure {
                                testOk = false
                                testMessage = it.message ?: "The request failed."
                            }
                        }
                    },
                    filled = ready,
                )
                StatusLine(testMessage, ok = testOk)
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
                Text(
                    "The key is stored in this app's private preferences on the device and is only " +
                        "sent to the endpoint above. Nothing else in ZERO PLAYER uses the network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.TextTertiary,
                )
            }
        }

        item { Spacer(Modifier.height(6.dp)) }
        item { SectionHeader(title = "Artist playlists", subtitle = "One playlist per artist, kept in sync") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Zero.Surface1)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Rebuild after every scan",
                            style = MaterialTheme.typography.titleSmall,
                            color = Zero.TextPrimary,
                        )
                        Text(
                            "Each track lands in the playlist named after its artist.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Zero.TextTertiary,
                        )
                    }
                    Switch(
                        checked = autoArtists,
                        onCheckedChange = {
                            autoArtists = it
                            prefs.autoArtistPlaylists = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Zero.Black,
                            checkedTrackColor = Zero.Silver,
                            uncheckedThumbColor = Zero.SilverDim,
                            uncheckedTrackColor = Zero.Surface3,
                        ),
                    )
                }
                Spacer(Modifier.height(14.dp))
                OutLinePlayRow(
                    icon = Icons.Rounded.Groups,
                    label = if (syncing) "Building…" else "Build artist playlists now",
                    onClick = {
                        if (syncing) return@OutLinePlayRow
                        syncing = true
                        syncMessage = null
                        scope.launch {
                            val result = runCatching { Graph.music.syncArtistPlaylists() }
                            syncing = false
                            result.onSuccess { syncMessage = it.describe() }
                                .onFailure { syncMessage = it.message ?: "Could not build playlists." }
                        }
                    },
                    filled = true,
                )
                StatusLine(syncMessage, ok = true)
            }
        }

        item { Spacer(Modifier.height(6.dp)) }
        item { SectionHeader(title = "Unknown artists", subtitle = "Let the model read the file name") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Zero.Surface1)
                    .padding(16.dp),
            ) {
                Text(
                    "Tracks whose tags say \"$UNKNOWN\" are sent one by one so the assistant can " +
                        "name the singer from the file name. Up to 40 tracks per run, then the " +
                        "artist playlists are rebuilt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.TextTertiary,
                )
                Spacer(Modifier.height(14.dp))
                OutLinePlayRow(
                    icon = Icons.Rounded.TravelExplore,
                    label = when {
                        naming -> "Asking the assistant…"
                        !ready -> "Add a key to enable this"
                        else -> "Name unknown artists"
                    },
                    onClick = {
                        if (naming) return@OutLinePlayRow
                        if (!ready) {
                            namingMessage = "Fill in the key, base URL and model first."
                            return@OutLinePlayRow
                        }
                        naming = true
                        namingMessage = null
                        namingProgress = "Starting…"
                        scope.launch {
                            var renamed = 0
                            val result = runCatching {
                                Graph.music.nameMissingArtists(Graph.ai) { done, total ->
                                    namingProgress = if (total == 0) null else "$done / $total"
                                    renamed = done
                                }
                            }
                            naming = false
                            namingProgress = null
                            result.onSuccess {
                                val sync = runCatching { Graph.music.syncArtistPlaylists() }.getOrNull()
                                namingMessage = buildString {
                                    append("Checked $renamed track(s) with unknown artist.")
                                    if (sync != null) {
                                        append(" ")
                                        append(sync.describe())
                                    }
                                }
                            }.onFailure {
                                namingMessage = it.message ?: "The request failed."
                            }
                        }
                    },
                    filled = ready,
                )
                StatusLine(namingProgress ?: namingMessage, ok = namingMessage != null)
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
                ZeroDivider()
                Spacer(Modifier.height(14.dp))
                Text(
                    "Lyrics come from the assistant too: open the lyrics screen of any track and " +
                        "tap the sparkle button. AI text is clearly marked in the lyrics header.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun AiField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    masked: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MicroLabel(label)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text(placeholder, color = Zero.TextTertiary) },
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Zero.Silver,
                unfocusedBorderColor = Zero.Hairline,
                cursorColor = Zero.Chrome,
                focusedTextColor = Zero.TextPrimary,
                unfocusedTextColor = Zero.TextPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatusLine(text: String?, ok: Boolean) {
    if (text.isNullOrBlank()) return
    Spacer(Modifier.height(10.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (ok) Zero.Chrome else Zero.Silver,
    )
}

private const val UNKNOWN = "Unknown Artist"
