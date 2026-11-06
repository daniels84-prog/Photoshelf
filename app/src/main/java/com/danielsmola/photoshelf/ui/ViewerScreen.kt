@file:OptIn(ExperimentalMaterial3Api::class)

package com.danielsmola.photoshelf.ui

import android.app.Activity
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.danielsmola.photoshelf.Graph
import com.danielsmola.photoshelf.MainViewModel
import com.danielsmola.photoshelf.Screen
import com.danielsmola.photoshelf.data.DECODABLE_EXT
import com.danielsmola.photoshelf.data.MediaKind
import com.danielsmola.photoshelf.data.PhotoItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val viewerDate = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy · h:mm a")
private fun fmtDate(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(viewerDate)

@Composable
fun ViewerScreen(vm: MainViewModel, screen: Screen.Viewer) {
    val items = screen.items
    if (items.isEmpty()) return
    val pager = rememberPagerState(initialPage = screen.start.coerceIn(0, items.lastIndex)) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var forceHd by remember { mutableStateOf(false) }
    val favorites by vm.favorites.collectAsState()
    val fullResSetting by vm.settings.fullRes.collectAsState()
    val autoFull = remember(fullResSetting) {
        fullResSetting && (Graph.client.isLanRoute || !Graph.repo.isMetered())
    }

    BackHandler { vm.back() }
    ImmersiveBars(showBars = chrome)

    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    LaunchedEffect(pager.currentPage) { forceHd = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            key = { items[it].path },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            ZoomablePhoto(
                item = item,
                loadFull = autoFull || (forceHd && page == pager.currentPage),
                onTap = { chrome = !chrome },
                onPlay = { vm.play(item) },
            )
        }

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(
                        fmtDate(current.dateTaken), color = Color.White,
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${current.name}  ·  ${pager.currentPage + 1} of ${items.size}",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!autoFull && current.kind == MediaKind.PHOTO && current.ext in DECODABLE_EXT) {
                    IconButton(onClick = { forceHd = true }, enabled = !forceHd) {
                        Icon(Icons.Filled.Hd, "Load full resolution", tint = if (forceHd) Color.White.copy(alpha = 0.4f) else Color.White)
                    }
                }
                IconButton(onClick = { showInfo = true }) { Icon(Icons.Outlined.Info, "Details", tint = Color.White) }
            }
        }

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val fav = current.path in favorites
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ViewerAction(if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Favorite") {
                    vm.toggleFavorite(listOf(current))
                }
                ViewerAction(Icons.Filled.Share, "Share") { vm.share(listOf(current)) }
                if (current.kind == MediaKind.PHOTO) {
                    ViewerAction(Icons.Filled.ContentCopy, "Copy") { vm.copyToClipboard(current) }
                }
                ViewerAction(Icons.Filled.Download, "Save") { vm.save(listOf(current)) }
                ViewerAction(Icons.Filled.CreateNewFolder, "Copy to…") { vm.requestCopyToFolder(listOf(current)) }
            }
        }
    }

    if (showInfo) InfoSheet(current, vm) { showInfo = false }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/** Light icons on the black viewer, and hide the system bars when the controls are hidden. */
@Composable
private fun ImmersiveBars(showBars: Boolean) {
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window ?: return
    DisposableEffect(Unit) {
        val c = WindowCompat.getInsetsController(window, view)
        val lightStatus = c.isAppearanceLightStatusBars
        val lightNav = c.isAppearanceLightNavigationBars
        c.isAppearanceLightStatusBars = false
        c.isAppearanceLightNavigationBars = false
        onDispose {
            c.show(WindowInsetsCompat.Type.systemBars())
            c.isAppearanceLightStatusBars = lightStatus
            c.isAppearanceLightNavigationBars = lightNav
        }
    }
    LaunchedEffect(showBars) {
        val c = WindowCompat.getInsetsController(window, view)
        if (showBars) {
            c.show(WindowInsetsCompat.Type.systemBars())
        } else {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun ZoomablePhoto(item: PhotoItem, loadFull: Boolean, onTap: () -> Unit, onPlay: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = boxSize.width * (s - 1f) / 2f
        val maxY = boxSize.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(item.path) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val s = 2.5f
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            scale = s
                            offset = clamp((center - tap) * (s - 1f), s)
                        }
                    },
                )
            }
            .pointerInput(item.path) {
                // Pinch to zoom; when zoomed, one finger pans. At 1x, swipes go to the pager.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1.01f) {
                            val ns = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            scale = ns
                            offset = if (ns <= 1.01f) Offset.Zero else clamp(offset + event.calculatePan(), ns)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val ctx = LocalContext.current
        val session = LocalSession.current
        val layer = Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = offset.x; translationY = offset.y
        }
        AsyncImage(
            model = thumbRequest(item, "large"),
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = layer,
        )
        if (loadFull && item.kind == MediaKind.PHOTO && item.ext in DECODABLE_EXT) {
            val full = remember(item.path, session) {
                ImageRequest.Builder(ctx)
                    .data(Graph.client.downloadUrl(item.path))
                    .memoryCacheKey("o:${item.path}")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .size(2880)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(model = full, contentDescription = null, contentScale = ContentScale.Fit, modifier = layer)
        }
        if (item.kind == MediaKind.VIDEO) {
            Box(
                Modifier
                    .size(76.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun InfoSheet(item: PhotoItem, vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text("Name") })
            ListItem(headlineContent = { Text(item.folder) }, supportingContent = { Text("Folder on NAS") })
            ListItem(
                headlineContent = { Text(fmtDate(item.dateTaken)) },
                supportingContent = { Text("Date taken (${item.dateSource.label})") },
            )
            ListItem(
                headlineContent = { Text(Formatter.formatShortFileSize(ctx, item.size)) },
                supportingContent = { Text("Size · ${item.ext.uppercase()}") },
            )
            if (item.dateSource != com.danielsmola.photoshelf.data.DateSource.MODIFIED) {
                ListItem(headlineContent = { Text(fmtDate(item.modified)) }, supportingContent = { Text("File modified") })
            }
            Row(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = { onDismiss(); vm.showInFolder(item) }) {
                    Icon(Icons.Outlined.Folder, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Show in folder")
                }
            }
        }
    }
}
