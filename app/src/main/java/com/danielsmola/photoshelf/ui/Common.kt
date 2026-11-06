@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.danielsmola.photoshelf.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.danielsmola.photoshelf.Busy
import com.danielsmola.photoshelf.Graph
import com.danielsmola.photoshelf.MainViewModel
import com.danielsmola.photoshelf.data.Category
import com.danielsmola.photoshelf.data.IndexStatus
import com.danielsmola.photoshelf.data.MediaKind
import com.danielsmola.photoshelf.data.PhotoItem
import com.danielsmola.photoshelf.data.Section

/** Changes whenever the NAS session changes so image URLs get rebuilt. */
val LocalSession = compositionLocalOf { 0 }

@Composable
fun thumbRequest(item: PhotoItem, size: String): ImageRequest {
    val ctx = LocalContext.current
    val session = LocalSession.current
    return remember(item.path, size, session) {
        ImageRequest.Builder(ctx)
            .data(Graph.client.thumbUrl(item.path, size))
            .memoryCacheKey("t:$size:${item.path}")
            .diskCacheKey("t:$size:${item.path}")
            .build()
    }
}

@Composable
fun Thumb(
    item: PhotoItem,
    selected: Boolean,
    selectionMode: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var failed by remember(item.path) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (selected) 0.85f else 1f, label = "select")
    Box(
        modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = thumbRequest(item, "medium"),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            onSuccess = { failed = false },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(if (selected) 10.dp else 0.dp)),
        )
        if (failed) {
            Text(
                item.ext.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (item.kind == MediaKind.VIDEO) {
            Icon(
                Icons.Filled.PlayCircle, contentDescription = "Video", tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp),
            )
        }
        if (favorite && !selectionMode) {
            Icon(
                Icons.Filled.Favorite, contentDescription = "Favorite", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp),
            )
        }
        if (selectionMode) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(if (selected) Color.White else Color.Black.copy(alpha = 0.2f), CircleShape),
            )
        }
    }
}

@Composable
fun SectionHeader(section: Section, selectionMode: Boolean, onSelectAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selectionMode) TextButton(onClick = onSelectAll) { Text("Select") }
    }
}

/** The main photo grid used by every view. Tap opens the viewer, long-press starts selecting. */
@Composable
fun PhotoGrid(
    sections: List<Section>,
    vm: MainViewModel,
    state: LazyGridState,
    modifier: Modifier = Modifier,
    showHeaders: Boolean = true,
    header: LazyGridScope.() -> Unit = {},
) {
    val columns by vm.settings.columns.collectAsState()
    val selection by vm.selection.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val flat = remember(sections) { sections.flatMap { it.items } }
    val offsets = remember(sections) {
        var acc = 0
        sections.map { s -> acc.also { acc += s.items.size } }
    }
    val selectionMode = selection.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        header()
        sections.forEachIndexed { si, sec ->
            if (showHeaders) {
                item(key = "h:" + sec.key, span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    SectionHeader(sec, selectionMode) { vm.selectAll(sec.items) }
                }
            }
            val base = offsets[si]
            items(
                count = sec.items.size,
                key = { "p:" + sec.items[it].path },
                contentType = { "photo" },
            ) { i ->
                val item = sec.items[i]
                Thumb(
                    item = item,
                    selected = item.path in selection,
                    selectionMode = selectionMode,
                    favorite = item.path in favorites,
                    onClick = { if (selectionMode) vm.toggleSelect(item) else vm.openViewer(flat, base + i) },
                    onLongClick = { vm.toggleSelect(item) },
                )
            }
        }
    }
}

@Composable
fun IndexBanner(status: IndexStatus, onRetry: () -> Unit) {
    when (status) {
        is IndexStatus.Scanning -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                "Indexing your library… %,d found".format(status.found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is IndexStatus.ReadingDates -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                "Reading photo dates %,d of %,d".format(status.done, status.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { if (status.total == 0) 0f else status.done.toFloat() / status.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is IndexStatus.Error -> Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
        IndexStatus.Idle -> {}
    }
}

@Composable
fun CategoryRow(current: Category, includeVideos: Boolean, onSelect: (Category) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Category.entries.filter { includeVideos || it != Category.VIDEOS }.forEach { c ->
            FilterChip(selected = c == current, onClick = { onSelect(c) }, label = { Text(c.label) })
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Top bar shown while items are selected. */
@Composable
fun SelectionTopBar(vm: MainViewModel, count: Int) {
    var menu by remember { mutableStateOf(false) }
    val favorites by vm.favorites.collectAsState()
    val selection by vm.selection.collectAsState()
    val allFav = selection.isNotEmpty() && selection.all { it in favorites }
    TopAppBar(
        navigationIcon = { IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Filled.Close, "Cancel selection") } },
        title = { Text("$count selected") },
        actions = {
            IconButton(onClick = { vm.toggleFavorite(vm.selectedItems()) }) {
                Icon(if (allFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Favorite")
            }
            IconButton(onClick = { vm.share(vm.selectedItems()) }) { Icon(Icons.Filled.Share, "Share") }
            IconButton(onClick = { vm.save(vm.selectedItems()) }) { Icon(Icons.Filled.Download, "Save to phone") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy to folder…") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                        onClick = { menu = false; vm.requestCopyToFolder(vm.selectedItems()) },
                    )
                    DropdownMenuItem(
                        text = { Text("Save to phone") },
                        leadingIcon = { Icon(Icons.Filled.Download, null) },
                        onClick = { menu = false; vm.save(vm.selectedItems()) },
                    )
                }
            }
        },
    )
}

@Composable
fun BusyDialog(busy: Busy, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(busy.title) },
        text = {
            Column {
                if (busy.total > 1) {
                    Text("${busy.done + 1} of ${busy.total}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { busy.done.toFloat() / busy.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
fun SmallLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
    )
}
