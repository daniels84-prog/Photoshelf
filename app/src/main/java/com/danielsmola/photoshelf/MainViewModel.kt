package com.danielsmola.photoshelf

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.danielsmola.photoshelf.data.AlbumInfo
import com.danielsmola.photoshelf.data.Category
import com.danielsmola.photoshelf.data.Grouping
import com.danielsmola.photoshelf.data.IndexStatus
import com.danielsmola.photoshelf.data.MediaKind
import com.danielsmola.photoshelf.data.NeedOtpException
import com.danielsmola.photoshelf.data.Organizer
import com.danielsmola.photoshelf.data.PhotoItem
import com.danielsmola.photoshelf.data.RemoteEntry
import com.danielsmola.photoshelf.data.Section
import com.danielsmola.photoshelf.data.SignInRequiredException
import com.danielsmola.photoshelf.data.SortOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Starting : Screen
    data object Login : Screen
    data object PickRoot : Screen
    data object Home : Screen
    data object SettingsScreen : Screen
    data class Album(val info: AlbumInfo) : Screen
    data class Viewer(val items: List<PhotoItem>, val start: Int) : Screen
}

enum class Tab(val label: String) { TIMELINE("Timeline"), ALBUMS("Albums"), FOLDERS("Folders"), FAVORITES("Favorites") }

data class Busy(val title: String, val done: Int, val total: Int)

data class LoginState(
    val address: String = "",
    val user: String = "",
    val password: String = "",
    val otp: String = "",
    val needOtp: Boolean = false,
    val stage: String? = null,
    val error: String? = null,
)

data class PickerState(
    val path: String? = null,
    val entries: List<RemoteEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val client = Graph.client
    private val repo = Graph.repo
    private val actions = Graph.actions
    private val store = Graph.store
    val settings = Graph.settings

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Starting))
    val stack: StateFlow<List<Screen>> = _stack

    val tab = MutableStateFlow(Tab.TIMELINE)
    val category = MutableStateFlow(Category.ALL)
    val query = MutableStateFlow("")
    val searching = MutableStateFlow(false)
    val folderPath = MutableStateFlow<String?>(null)

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection

    private val _busy = MutableStateFlow<Busy?>(null)
    val busy: StateFlow<Busy?> = _busy
    private var busyJob: Job? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages
    private val _launches = MutableSharedFlow<Intent>(extraBufferCapacity = 4)
    val launches: SharedFlow<Intent> = _launches
    private val _folderPicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val folderPicks: SharedFlow<Unit> = _folderPicks
    private var pendingCopy: List<PhotoItem> = emptyList()

    val login = MutableStateFlow(LoginState(address = store.lastAddress(), user = store.lastUser()))
    val picker = MutableStateFlow(PickerState())

    // Scroll positions survive tab switches and opening the viewer.
    val timelineGrid = LazyGridState()
    val albumsGrid = LazyGridState()
    val favoritesGrid = LazyGridState()

    val status: StateFlow<IndexStatus> = repo.status
    val favorites: StateFlow<Set<String>> = repo.favorites
    val allItems: StateFlow<List<PhotoItem>> = repo.items
    val route: StateFlow<String> = client.route
    val session: StateFlow<Int> = client.session

    val visible: StateFlow<List<PhotoItem>> =
        combine(repo.items, category, query, settings.includeVideos) { list, cat, q, vids ->
            Organizer.filter(list, cat, vids, q)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val timeline: StateFlow<List<Section>> =
        combine(visible, settings.grouping, settings.sort) { v, g, s ->
            Organizer.timeline(if (s == SortOrder.OLDEST) v.asReversed() else v, g)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val onThisDay: StateFlow<List<PhotoItem>> =
        visible.map { Organizer.onThisDay(it) }
            .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<AlbumInfo>> =
        combine(visible, settings.root) { v, r -> if (r == null) emptyList() else Organizer.albums(v, r) }
            .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folderTree: StateFlow<Organizer.FolderTree?> =
        combine(visible, settings.root, settings.sort) { v, r, s -> r?.let { Organizer.FolderTree(it, v, s) } }
            .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val favoriteSections: StateFlow<List<Section>> =
        combine(visible, repo.favorites, settings.sort) { v, f, s ->
            val favs = v.filter { it.path in f }
            Organizer.timeline(if (s == SortOrder.OLDEST) favs.asReversed() else favs, Grouping.MONTH)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val creds = store.load()
            val root = settings.root.value
            when {
                creds == null -> {
                    _stack.value = listOf(Screen.Login)
                }
                root == null -> {
                    _stack.value = listOf(Screen.Login)
                    login.update { it.copy(stage = "Reconnecting…") }
                    try {
                        client.restore()
                        login.update { it.copy(stage = null) }
                        openPicker()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        login.update { it.copy(stage = null, error = e.message) }
                    }
                }
                else -> {
                    repo.loadCache(root)
                    folderPath.value = root
                    _stack.value = listOf(Screen.Home)
                    refresh()
                }
            }
        }
    }

    // ---------- navigation ----------

    fun push(s: Screen) {
        _selection.value = emptySet()
        _stack.value = _stack.value + s
    }

    fun back() {
        if (_stack.value.size > 1) _stack.value = _stack.value.dropLast(1)
    }

    fun openViewer(items: List<PhotoItem>, index: Int) = push(Screen.Viewer(items, index))

    fun showInFolder(item: PhotoItem) {
        _selection.value = emptySet()
        _stack.value = listOf(Screen.Home)
        tab.value = Tab.FOLDERS
        folderPath.value = item.folder
    }

    fun selectTab(t: Tab) {
        _selection.value = emptySet()
        tab.value = t
    }

    private fun toLogin(message: String?) {
        _stack.value = listOf(Screen.Login)
        login.update { it.copy(stage = null, needOtp = false, password = "", otp = "", error = message) }
    }

    // ---------- sign in ----------

    fun signIn() {
        val s = login.value
        if (s.address.isBlank() || s.user.isBlank() || s.password.isEmpty()) {
            login.update { it.copy(error = "Fill in the NAS, username and password.") }
            return
        }
        if (s.needOtp && s.otp.isBlank()) {
            login.update { it.copy(error = "Enter your 6-digit code.") }
            return
        }
        viewModelScope.launch {
            login.update { it.copy(error = null, stage = "Finding your NAS…") }
            try {
                client.signIn(s.address.trim(), s.user.trim(), s.password, s.otp.takeIf { s.needOtp }) { stage ->
                    login.update { it.copy(stage = stage) }
                }
                login.update { it.copy(stage = null, password = "", otp = "", needOtp = false, error = null) }
                val root = settings.root.value
                if (root == null) {
                    openPicker()
                } else {
                    repo.loadCache(root)
                    folderPath.value = root
                    _stack.value = listOf(Screen.Home)
                    refresh()
                }
            } catch (e: NeedOtpException) {
                login.update { it.copy(stage = null, needOtp = true, error = if (s.needOtp) e.message else null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                login.update { it.copy(stage = null, error = e.message ?: "Couldn't sign in.") }
            }
        }
    }

    fun signOut() {
        scanJob?.cancel()
        client.signOut()
        repo.clear()
        toLogin(null)
    }

    // ---------- choosing the library folder ----------

    fun openPicker() {
        val onHome = _stack.value.any { it == Screen.Home }
        _stack.value = if (onHome) _stack.value + Screen.PickRoot else listOf(Screen.PickRoot)
        val current = settings.root.value
        browse(current?.substringBeforeLast('/')?.takeIf { it.isNotEmpty() })
    }

    fun browse(path: String?) {
        viewModelScope.launch {
            picker.value = PickerState(path = path, loading = true)
            try {
                val entries = if (path == null) client.listShares()
                else client.listFolder(path).filter { it.isDir && !it.name.startsWith(".") && !it.name.startsWith("@") && !it.name.startsWith("#") }
                picker.value = PickerState(path = path, entries = entries)
            } catch (e: SignInRequiredException) {
                toLogin(e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                picker.value = PickerState(path = path, error = e.message ?: "Couldn't list folders.")
            }
        }
    }

    fun pickerUp() {
        val p = picker.value.path ?: return
        val parent = p.substringBeforeLast('/')
        browse(parent.ifEmpty { null })
    }

    fun chooseRoot(path: String) {
        val changed = settings.root.value != path
        settings.setRoot(path)
        folderPath.value = path
        _stack.value = listOf(Screen.Home)
        viewModelScope.launch {
            if (changed) {
                scanJob?.cancel()
                repo.clear()
            }
            repo.loadCache(path)
            refresh(force = changed)
        }
    }

    // ---------- indexing ----------

    private var scanJob: Job? = null

    fun refresh(force: Boolean = false) {
        val root = settings.root.value ?: return
        if (scanJob?.isActive == true) {
            if (!force) return
            scanJob?.cancel()
        }
        scanJob = viewModelScope.launch {
            try {
                client.ensureSession()
                repo.scan(root)
                settings.lastIndexed = System.currentTimeMillis()
                if (settings.exifDates.value) repo.refineDates(settings.exifOnMobile.value)
            } catch (e: SignInRequiredException) {
                toLogin(e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (repo.items.value.isNotEmpty()) {
                    repo.resetStatus()
                    _messages.tryEmit("Can't reach the NAS right now. Showing your saved library.")
                }
            }
        }
    }

    // ---------- selection ----------

    fun toggleSelect(item: PhotoItem) {
        _selection.update { if (item.path in it) it - item.path else it + item.path }
    }

    fun selectAll(items: List<PhotoItem>) {
        _selection.update { cur ->
            val paths = items.map { it.path }
            if (cur.containsAll(paths)) cur - paths.toSet() else cur + paths
        }
    }

    fun clearSelection() { _selection.value = emptySet() }

    fun selectedItems(): List<PhotoItem> {
        val sel = _selection.value
        return repo.items.value.filter { it.path in sel }
    }

    // ---------- actions (all read-only on the NAS) ----------

    fun toggleFavorite(items: List<PhotoItem>) {
        if (items.isEmpty()) return
        val allFav = items.all { it.path in repo.favorites.value }
        repo.setFavorite(items.map { it.path }, !allFav)
    }

    fun save(items: List<PhotoItem>) = runBusy("Saving to phone", items.size) { progress ->
        val n = actions.saveToPhone(items, progress)
        clearSelection()
        val where = if (items.all { it.kind == MediaKind.VIDEO }) "Movies/PhotoShelf" else "Pictures/PhotoShelf"
        _messages.tryEmit(if (n == 1) "Saved to $where" else "Saved $n items to $where")
    }

    fun share(items: List<PhotoItem>) = runBusy("Getting ready to share", items.size) { progress ->
        val uris = actions.cacheForSharing(items, progress)
        _launches.emit(actions.shareIntent(uris, items))
        clearSelection()
    }

    fun copyToClipboard(item: PhotoItem) = runBusy("Copying", 1) { progress ->
        val uri = actions.cacheForSharing(listOf(item), progress).first()
        actions.copyToClipboard(uri, item)
        _messages.tryEmit("Copied — paste it into any app")
    }

    fun requestCopyToFolder(items: List<PhotoItem>) {
        if (items.isEmpty()) return
        pendingCopy = items
        _folderPicks.tryEmit(Unit)
    }

    fun onFolderPicked(uri: Uri?) {
        val items = pendingCopy
        pendingCopy = emptyList()
        if (uri == null || items.isEmpty()) return
        runBusy("Copying to folder", items.size) { progress ->
            val n = actions.copyToFolder(uri, items, progress)
            clearSelection()
            _messages.tryEmit(if (n == 1) "Copied 1 item" else "Copied $n items")
        }
    }

    fun play(item: PhotoItem) = runBusy("Opening video", 1) {
        _launches.emit(actions.videoIntent(item))
    }

    fun cancelBusy() { busyJob?.cancel() }

    private fun runBusy(title: String, total: Int, block: suspend (progress: (Int) -> Unit) -> Unit) {
        if (_busy.value != null) return
        busyJob = viewModelScope.launch {
            _busy.value = Busy(title, 0, total)
            try {
                block { i -> _busy.value = Busy(title, i, total) }
            } catch (e: SignInRequiredException) {
                toLogin(e.message)
            } catch (e: CancellationException) {
                _messages.tryEmit("Cancelled")
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Something went wrong.")
            } finally {
                _busy.value = null
            }
        }
    }

    fun toast(message: String) { _messages.tryEmit(message) }

    // ---------- settings ----------

    @OptIn(ExperimentalCoilApi::class)
    fun clearThumbnails() {
        val loader = getApplication<Application>().imageLoader
        loader.memoryCache?.clear()
        viewModelScope.launch(Dispatchers.IO) {
            loader.diskCache?.clear()
            _messages.tryEmit("Thumbnail cache cleared")
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            try {
                client.restore()
                _messages.tryEmit("Connected via ${client.route.value}")
            } catch (e: SignInRequiredException) {
                toLogin(e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Couldn't reach the NAS.")
            }
        }
    }
}
