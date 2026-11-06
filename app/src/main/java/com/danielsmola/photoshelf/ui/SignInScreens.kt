@file:OptIn(ExperimentalMaterial3Api::class)

package com.danielsmola.photoshelf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.danielsmola.photoshelf.MainViewModel

@Composable
fun StartingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun LoginScreen(vm: MainViewModel) {
    val s by vm.login.collectAsState()
    var showPass by remember { mutableStateOf(false) }
    val working = s.stage != null

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("PhotoShelf", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Your Synology photos, organized.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = s.address,
                    onValueChange = { v -> vm.login.value = s.copy(address = v, error = null) },
                    label = { Text("QuickConnect ID or address") },
                    supportingText = { Text("e.g. smolanas, or 192.168.1.20 at home") },
                    singleLine = true,
                    enabled = !working,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.user,
                    onValueChange = { v -> vm.login.value = s.copy(user = v, error = null) },
                    label = { Text("DSM username") },
                    singleLine = true,
                    enabled = !working,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.password,
                    onValueChange = { v -> vm.login.value = s.copy(password = v, error = null) },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !working,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show password")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (s.needOtp) ImeAction.Next else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { vm.signIn() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (s.needOtp) {
                    OutlinedTextField(
                        value = s.otp,
                        onValueChange = { v -> vm.login.value = s.copy(otp = v.filter { it.isDigit() }.take(8), error = null) },
                        label = { Text("2-step verification code") },
                        supportingText = { Text("From your authenticator app. This phone will be remembered.") },
                        singleLine = true,
                        enabled = !working,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.signIn() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                s.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = { vm.signIn() },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (working) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(s.stage ?: "")
                    } else {
                        Text("Connect")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "PhotoShelf only reads from your NAS. It can't upload, rename, move, edit or delete anything. " +
                                "For extra peace of mind, sign in with a DSM user that has read-only access to your Photo folder.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PickRootScreen(vm: MainViewModel) {
    val p by vm.picker.collectAsState()
    val stack by vm.stack.collectAsState()
    val currentRoot by vm.settings.root.collectAsState()

    BackHandler(enabled = p.path != null) { vm.pickerUp() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Choose your photo folder")
                        Text(
                            p.path ?: "Shared folders on your NAS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (p.path != null || stack.size > 1) {
                        IconButton(onClick = { if (p.path != null) vm.pickerUp() else vm.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
            )
        },
        bottomBar = {
            val path = p.path
            if (path != null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { vm.chooseRoot(path) },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                    ) { Text("Use “${path.substringAfterLast('/')}”") }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                p.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                p.error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.error ?: "", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { vm.browse(p.path) }) { Text("Try again") }
                }
                p.entries.isEmpty() -> Text(
                    if (p.path == null) "No shared folders are visible to this account." else "No subfolders here. Tap the button below to use this folder.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(p.entries, key = { it.path }) { e ->
                        val suggested = p.path == null && (e.name.equals("Photo", true) || e.name.equals("Photos", true))
                        val note = when {
                            e.path == currentRoot -> "Current library folder"
                            suggested -> "Suggested"
                            else -> null
                        }
                        ListItem(
                            headlineContent = { Text(e.name) },
                            supportingContent = if (note != null) {
                                { Text(note, color = MaterialTheme.colorScheme.primary) }
                            } else null,
                            leadingContent = {
                                Icon(
                                    if (p.path == null) Icons.Filled.FolderShared else Icons.Filled.Folder, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                            modifier = Modifier.clickable { vm.browse(e.path) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
