@file:OptIn(ExperimentalMaterial3Api::class)

package com.danielsmola.photoshelf.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.danielsmola.photoshelf.Graph
import com.danielsmola.photoshelf.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s = vm.settings
    val route by vm.route.collectAsState()
    val root by s.root.collectAsState()
    val all by vm.allItems.collectAsState()
    val exif by s.exifDates.collectAsState()
    val exifMobile by s.exifOnMobile.collectAsState()
    val fullRes by s.fullRes.collectAsState()
    val videos by s.includeVideos.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Header("Connection") }
            item { ListItem(headlineContent = { Text(Graph.store.lastAddress()) }, supportingContent = { Text("NAS (QuickConnect ID or address)") }) }
            item { ListItem(headlineContent = { Text(Graph.store.lastUser()) }, supportingContent = { Text("Signed in as") }) }
            item {
                ListItem(
                    headlineContent = { Text(route.ifEmpty { "Not connected" }) },
                    supportingContent = { Text("Connected via") },
                    trailingContent = { TextButton(onClick = { vm.reconnect() }) { Text("Reconnect") } },
                )
            }

            item { SectionDivider() }
            item { Header("Library") }
            item {
                ListItem(
                    headlineContent = { Text(root ?: "Not chosen") },
                    supportingContent = { Text("Photo folder on the NAS") },
                    trailingContent = { TextButton(onClick = { vm.openPicker() }) { Text("Change") } },
                )
            }
            item {
                val last = s.lastIndexed
                val whenText = if (last == 0L) "never" else DateUtils.getRelativeTimeSpanString(last).toString()
                ListItem(
                    headlineContent = { Text("%,d photos and videos".format(all.size)) },
                    supportingContent = { Text("Last full scan: $whenText") },
                    trailingContent = { TextButton(onClick = { vm.refresh(force = true); vm.back() }) { Text("Rescan") } },
                )
            }

            item { SectionDivider() }
            item { Header("Dates") }
            item {
                SwitchItem(
                    "Read dates from photo data",
                    "For files without a date in the name (like iPhone IMG_1234.HEIC), read the camera's date once. Only a small part of each file is read.",
                    exif,
                ) { s.setExifDates(it); if (it) vm.refresh(force = true) }
            }
            item {
                SwitchItem("Also on mobile data", "Otherwise date reading waits for Wi-Fi.", exifMobile, enabled = exif) {
                    s.setExifOnMobile(it)
                }
            }

            item { SectionDivider() }
            item { Header("Viewing") }
            item {
                SwitchItem(
                    "Full resolution",
                    "Load the original photo when on Wi-Fi or your home network. On mobile data, tap HD in the viewer.",
                    fullRes,
                ) { s.setFullRes(it) }
            }
            item { SwitchItem("Show videos", "Include videos in the library.", videos) { s.setIncludeVideos(it) } }
            item {
                ListItem(
                    headlineContent = { Text("Thumbnail cache") },
                    supportingContent = { Text("Thumbnails are kept on the phone so browsing is fast and works offline.") },
                    trailingContent = { TextButton(onClick = { vm.clearThumbnails() }) { Text("Clear") } },
                )
            }

            item { SectionDivider() }
            item { Header("About") }
            item {
                ListItem(
                    headlineContent = { Text("Read-only by design") },
                    supportingContent = {
                        Text(
                            "PhotoShelf only lists, previews and downloads. It has no way to upload, rename, move, edit or delete " +
                                "anything on your NAS. Favorites live on this phone. Saved photos go to Pictures/PhotoShelf, " +
                                "videos to Movies/PhotoShelf.",
                        )
                    },
                )
            }
            item { ListItem(headlineContent = { Text("PhotoShelf $version") }) }
            item {
                OutlinedButton(
                    onClick = { confirmSignOut = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                ) { Text("Sign out") }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your saved password and library index will be removed from this phone. Nothing on the NAS is affected.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionDivider() = HorizontalDivider(Modifier.padding(top = 8.dp))

@Composable
private fun SwitchItem(title: String, body: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
    )
}
