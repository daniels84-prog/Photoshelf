@file:OptIn(ExperimentalMaterial3Api::class)

package com.danielsmola.photoshelf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.danielsmola.photoshelf.MainViewModel
import com.danielsmola.photoshelf.Screen
import com.danielsmola.photoshelf.Tab
import com.danielsmola.photoshelf.data.AlbumInfo
import com.danielsmola.photoshelf.data.FolderNode
import com.danielsmola.photoshelf.data.Grouping
import com.danielsmola.photoshelf.data.IndexStatus
import com.danielsmola.photoshelf.data.Organizer
import com.danielsmola.photoshelf.data.PhotoItem
import com.danielsmola.photoshelf.data.Section
import com.danielsmola.photoshelf.data.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun tabIcon(t: Tab, selected: Boolean): ImageVector = when (t) {
    Tab.TIMELINE -> if (selected) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth
    Tab.ALBUMS -> if (selected) Icons.Filled.Collections else Icons.Outlined.Collections
    Tab.FOLDERS -> if (selected) Icons.Filled.Folder else Icons.Outlined.Folder
    Tab.FAVORITES -> if (selected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
}

@Composable
fun HomeScreen(vm: MainViewModel) {
    val tab by vm.tab.collectAsState()
    val selection by vm.selection.collectAsState()
    val searching by vm.searching.collectAsState()
    val status by vm.status.collectAsState()
    val category by vm.category.collectAsState()
    val includeVideos by vm.settings.includeVideos.collectAsState()
    var showOptions by remember { mutableStateOf(false) }

    BackHandler(enabled = selection.isNotEmpty()) { vm.clearSelection() }
    BackHandler(enabled = searching && selection.isEmpty()) {
        vm.searching.value = false
        vm.query.value = ""
    }

    Scaffold(
        topBar = {
            when {
                selection.isNotEmpty() -> SelectionTopBar(vm, selection.size)
                searching -> SearchTopBar(vm)
                else -> TopAppBar(
                    title = { Text(tab.label) },
                    actions = {
                        IconButton(onClick = { vm.searching.value = true }) { Icon(Icons.Filled.Search, "Search") }
                        IconButton(onClick = { showOptions = true }) { Icon(Icons.Filled.Tune, "View options") }
                        IconButton(onClick = { vm.push(Screen.SettingsScreen) }) { Icon(Icons.Filled.Settings, "Settings") }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { vm.selectTab(t) },
                        icon = { Icon(tabIcon(t, t == tab), null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            IndexBanner(status) { vm.refresh(force = true) }
            CategoryRow(category, includeVideos) { vm.category.value = it }
            when (tab) {
                Tab.TIMELINE -> TimelineTab(vm)
                Tab.ALBUMS -> AlbumsTab(vm)
                Tab.FOLDERS -> FoldersTab(vm)
                Tab.FAVORITES -> FavoritesTab(vm)
            }
        }
    }

    if (showOptions) ViewOptionsSheet(vm) { showOptions = false }
}

@Composable
private fun SearchTopBar(vm: MainViewModel) {
    val query by vm.query.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = { vm.searching.value = false; vm.query.value = "" }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("Search file and folder names") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        actions = {
            if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Close, "Clear") }
        },
    )
}

/** Shared empty / first-run message for any view with nothing to show. */
@Composable
private fun LibraryEmpty(vm: MainViewModel, emptyTitle: String, emptyBody: String) {
    val status by vm.status.collectAsState()
    val all by vm.allItems.collectAsState()
    val query by vm.query.collectAsState()
    when {
        all.isEmpty() && status is IndexStatus.Scanning -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text("Building your library", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "The first scan of a big photo folder can take a few minutes. After that, the app opens instantly and only checks for new photos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        query.isNotBlank() -> EmptyState(Icons.Outlined.ImageSearch, "No matches", "Nothing in your library matches “$query”.")
        else -> EmptyState(Icons.Filled.PhotoLibrary, emptyTitle, emptyBody)
    }
}

// ---------------- Timeline ----------------

@Composable
private fun TimelineTab(vm: MainViewModel) {
    val sections by vm.timeline.collectAsState()
    val otd by vm.onThisDay.collectAsState()
    val selection by vm.selection.collectAsState()
    if (sections.isEmpty()) {
        LibraryEmpty(vm, "No photos yet", "Nothing was found in your photo folder with the current filters.")
        return
    }
    val extra = if (otd.isNotEmpty() && selection.isEmpty()) 1 else 0
    // grid index of the first section of each year
    val years = remember(sections) {
        val out = ArrayList<Pair<Int, Int>>()
        var idx = 0
        for (s in sections) {
            if (out.isEmpty() || out.last().first != s.year) out += s.year to idx
            idx += 1 + s.items.size
        }
        out
    }
    val grid = vm.timelineGrid
    val scope = rememberCoroutineScope()
    val currentYear by remember(years, extra) {
        derivedStateOf {
            val first = grid.firstVisibleItemIndex - extra
            years.lastOrNull { it.second <= first }?.first ?: years.firstOrNull()?.first
        }
    }
    Column {
        if (years.size > 1) {
            val rowState = rememberLazyListState()
            LaunchedEffect(currentYear) {
                val i = years.indexOfFirst { it.first == currentYear }
                if (i >= 0) rowState.animateScrollToItem((i - 2).coerceAtLeast(0))
            }
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(years, key = { _, y -> y.first }) { _, (year, index) ->
                    FilterChip(
                        selected = year == currentYear,
                        onClick = { scope.launch { grid.scrollToItem(index + extra) } },
                        label = { Text(year.toString()) },
                    )
                }
            }
        }
        PhotoGrid(sections, vm, grid, header = {
            if (extra == 1) {
                item(key = "on-this-day", span = { GridItemSpan(maxLineSpan) }) { OnThisDay(otd, vm) }
            }
        })
    }
}

@Composable
private fun OnThisDay(items: List<PhotoItem>, vm: MainViewModel) {
    val shown = remember(items) { items.take(40) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            "On this day",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Text(
            if (items.size == 1) "1 memory from a past year" else "${items.size} memories from past years",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(shown, key = { _, p -> "otd:" + p.path }) { i, item ->
                Box(Modifier.size(132.dp).clip(RoundedCornerShape(14.dp))) {
                    Thumb(
                        item = item, selected = false, selectionMode = false, favorite = false,
                        onClick = { vm.openViewer(shown, i) }, onLongClick = { vm.openViewer(shown, i) },
                    )
                    val yearsAgo = java.time.LocalDate.now().year - Organizer.localDate(item.dateTaken).year
                    Text(
                        if (yearsAgo == 1) "1 year ago" else "$yearsAgo years ago",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------- Albums ----------------

@Composable
private fun AlbumsTab(vm: MainViewModel) {
    val albums by vm.albums.collectAsState()
    if (albums.isEmpty()) {
        LibraryEmpty(vm, "No albums", "Each folder inside your photo folder shows up here as an album.")
        return
    }
    val collections = remember(albums) { albums.filter { !it.isYear } }
    val years = remember(albums) { albums.filter { it.isYear } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = vm.albumsGrid,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (collections.isNotEmpty()) {
            item(key = "hdr-collections", span = { GridItemSpan(maxLineSpan) }) { SmallLabel("Collections") }
            items(collections, key = { it.prefix }) { a -> AlbumCard(a) { vm.push(Screen.Album(a)) } }
        }
        if (years.isNotEmpty()) {
            item(key = "hdr-years", span = { GridItemSpan(maxLineSpan) }) { SmallLabel("Years") }
            items(years, key = { it.prefix }) { a -> AlbumCard(a) { vm.push(Screen.Album(a)) } }
        }
    }
}

@Composable
private fun AlbumCard(album: AlbumInfo, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        AsyncImage(
            model = thumbRequest(album.cover, "medium"),
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
        val from = Organizer.shortDate(album.oldest)
        val to = Organizer.shortDate(album.newest)
        Text(
            Organizer.countLabel(album.count) + " · " + if (from == to) from else "$from – $to",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
fun AlbumScreen(vm: MainViewModel, album: AlbumInfo) {
    val visible by vm.visible.collectAsState()
    val sort by vm.settings.sort.collectAsState()
    val grouping by vm.settings.grouping.collectAsState()
    val selection by vm.selection.collectAsState()
    val category by vm.category.collectAsState()
    val includeVideos by vm.settings.includeVideos.collectAsState()
    val sections by produceState(initialValue = emptyList<Section>(), visible, album, sort, grouping) {
        value = withContext(Dispatchers.Default) {
            val items = Organizer.albumItems(visible, album.prefix, album.directOnly)
            if (sort == SortOrder.NAME) {
                listOf(Section("all", "", Organizer.countLabel(items.size), 0, Organizer.sort(items, SortOrder.NAME)))
            } else {
                Organizer.timeline(if (sort == SortOrder.OLDEST) items.asReversed() else items, grouping)
            }
        }
    }
    val count = sections.sumOf { it.items.size }
    val grid = remember { LazyGridState() }

    BackHandler(enabled = selection.isNotEmpty()) { vm.clearSelection() }

    Scaffold(
        topBar = {
            if (selection.isNotEmpty()) {
                SelectionTopBar(vm, selection.size)
            } else {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = {
                        Column {
                            Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                Organizer.countLabel(count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            CategoryRow(category, includeVideos) { vm.category.value = it }
            PhotoGrid(
                sections, vm, grid,
                showHeaders = sort != SortOrder.NAME,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

// ---------------- Folders ----------------

@Composable
private fun FoldersTab(vm: MainViewModel) {
    val tree by vm.folderTree.collectAsState()
    val root by vm.settings.root.collectAsState()
    val pathState by vm.folderPath.collectAsState()
    val selection by vm.selection.collectAsState()
    val r = root ?: return
    val current = pathState?.takeIf { it == r || it.startsWith("$r/") } ?: r
    val node = remember(tree, current) { tree?.node(current) }

    BackHandler(enabled = current != r && selection.isEmpty()) {
        vm.folderPath.value = current.substringBeforeLast('/')
    }

    Column(Modifier.fillMaxSize()) {
        Breadcrumbs(r, current) { vm.folderPath.value = it }
        if (node == null || (node.files.isEmpty() && node.subfolders.isEmpty())) {
            LibraryEmpty(vm, "Empty folder", "There are no photos or videos in this folder.")
            return@Column
        }
        val grid = remember(current) { LazyGridState() }
        PhotoGrid(
            sections = listOf(Section("files", "", "", 0, node.files)),
            vm = vm,
            state = grid,
            showHeaders = false,
            header = {
                items(node.subfolders, key = { "f:" + it.path }, span = { GridItemSpan(maxLineSpan) }) { f ->
                    FolderRow(f) { vm.folderPath.value = f.path }
                }
                if (node.files.isNotEmpty() && node.subfolders.isNotEmpty()) {
                    item(key = "files-hdr", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "${Organizer.countLabel(node.files.size)} in this folder",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun FolderRow(folder: FolderNode, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(Organizer.countLabel(folder.total)) },
        leadingContent = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Breadcrumbs(root: String, current: String, onNavigate: (String) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(current) { scroll.animateScrollTo(scroll.maxValue) }
    val crumbs = remember(root, current) {
        val out = mutableListOf(root.substringAfterLast('/') to root)
        val rel = current.removePrefix(root).trim('/')
        if (rel.isNotEmpty()) {
            var acc = root
            rel.split('/').forEach { part -> acc = "$acc/$part"; out += part to acc }
        }
        out
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { i, (name, path) ->
            if (i > 0) Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onNavigate(path) }, enabled = path != current) {
                Text(name, fontWeight = if (path == current) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

// ---------------- Favorites ----------------

@Composable
private fun FavoritesTab(vm: MainViewModel) {
    val sections by vm.favoriteSections.collectAsState()
    if (sections.isEmpty()) {
        EmptyState(
            Icons.Outlined.FavoriteBorder,
            "No favorites yet",
            "Tap the heart on any photo to keep it here. Favorites are saved on this phone only; nothing on your NAS changes.",
        )
        return
    }
    PhotoGrid(sections, vm, vm.favoritesGrid)
}

// ---------------- View options ----------------

@Composable
private fun ViewOptionsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val grouping by vm.settings.grouping.collectAsState()
    val sort by vm.settings.sort.collectAsState()
    val columns by vm.settings.columns.collectAsState()
    val videos by vm.settings.includeVideos.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("View options", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Text("Group by", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Grouping.entries.forEachIndexed { i, g ->
                    SegmentedButton(
                        selected = g == grouping,
                        onClick = { vm.settings.setGrouping(g) },
                        shape = SegmentedButtonDefaults.itemShape(i, Grouping.entries.size),
                    ) { Text(g.label) }
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("Sort", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SortOrder.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = s == sort,
                        onClick = { vm.settings.setSort(s) },
                        shape = SegmentedButtonDefaults.itemShape(i, SortOrder.entries.size),
                    ) { Text(s.label, maxLines = 1) }
                }
            }
            Text(
                "Timeline is always by date; name sorting applies to albums and folders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))

            Text("Photos per row", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            val options = listOf(2, 3, 4, 5, 6)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, c ->
                    SegmentedButton(
                        selected = c == columns,
                        onClick = { vm.settings.setColumns(c) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(c.toString()) }
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show videos", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = videos, onCheckedChange = { vm.settings.setIncludeVideos(it) })
            }
        }
    }
}
