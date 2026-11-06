package com.danielsmola.photoshelf.data

import android.content.Context
import android.net.ConnectivityManager
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

sealed interface IndexStatus {
    data object Idle : IndexStatus
    data class Scanning(val folders: Int, val found: Int) : IndexStatus
    data class ReadingDates(val done: Int, val total: Int) : IndexStatus
    data class Error(val message: String) : IndexStatus
}

/**
 * Builds a local index of every photo/video under the chosen NAS folder by walking it with
 * File Station "list" calls. The index (and dates read from photo EXIF) is cached on the phone
 * so the library opens instantly; nothing is ever written to the NAS.
 */
class PhotoRepository(private val context: Context, private val client: SynologyClient) {
    private val _items = MutableStateFlow<List<PhotoItem>>(emptyList())
    val items: StateFlow<List<PhotoItem>> = _items

    private val _status = MutableStateFlow<IndexStatus>(IndexStatus.Idle)
    val status: StateFlow<IndexStatus> = _status

    private val favPrefs = context.getSharedPreferences("photoshelf_favorites", Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow(favPrefs.getStringSet("paths", emptySet())!!.toSet())
    val favorites: StateFlow<Set<String>> = _favorites

    /** "path|mtime" -> epoch millis from EXIF, or 0 when the photo had no usable date. */
    private val exifCache = ConcurrentHashMap<String, Long>()
    private val indexFile = File(context.filesDir, "index.json")
    private val exifFile = File(context.filesDir, "exif_dates.json")

    init {
        runCatching {
            if (exifFile.exists()) {
                val o = JSONObject(exifFile.readText())
                o.keys().forEach { k -> exifCache[k] = o.getLong(k) }
            }
        }
    }

    private fun exifKey(path: String, modified: Long) = "$path|$modified"

    // ---------- favorites (stored on the phone only) ----------

    fun setFavorite(paths: Collection<String>, favorite: Boolean) {
        val next = _favorites.value.toMutableSet()
        if (favorite) next.addAll(paths) else next.removeAll(paths.toSet())
        _favorites.value = next
        favPrefs.edit().putStringSet("paths", next).apply()
    }

    // ---------- cache ----------

    suspend fun loadCache(root: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (!indexFile.exists()) return@runCatching
            val o = JSONObject(indexFile.readText())
            if (o.optString("root") != root) return@runCatching
            val arr = o.getJSONArray("items")
            val list = ArrayList<PhotoItem>(arr.length())
            for (i in 0 until arr.length()) {
                val a = arr.getJSONArray(i)
                val path = a.getString(0); val size = a.getLong(1); val mod = a.getLong(2)
                list += PhotoItem.create(path, size, mod, exifCache[exifKey(path, mod)])
            }
            _items.value = list.sortedByDescending { it.dateTaken }
        }
    }

    private fun saveCache(root: String, list: List<PhotoItem>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { arr.put(JSONArray().put(it.path).put(it.size).put(it.modified)) }
            val tmp = File(indexFile.path + ".tmp")
            tmp.writeText(JSONObject().put("root", root).put("items", arr).toString())
            tmp.renameTo(indexFile)
        }
    }

    private fun saveExif() {
        runCatching {
            val o = JSONObject()
            exifCache.forEach { (k, v) -> o.put(k, v) }
            exifFile.writeText(o.toString())
        }
    }

    fun clear() {
        _items.value = emptyList()
        indexFile.delete()
    }

    // ---------- scan ----------

    suspend fun scan(root: String) = withContext(Dispatchers.IO) {
        val found = ConcurrentLinkedQueue<PhotoItem>()
        val folders = AtomicInteger(0)
        val count = AtomicInteger(0)
        val gate = Semaphore(4)
        _status.value = IndexStatus.Scanning(0, 0)
        try {
            coroutineScope {
                fun CoroutineScope.walk(path: String): Job = launch {
                    // A folder this account can't open is skipped rather than stopping the whole scan.
                    val entries = try {
                        gate.withPermit { client.listFolder(path) }
                    } catch (e: SynologyException) {
                        emptyList()
                    }
                    for (e in entries) {
                        if (e.isDir) {
                            if (!skipFolder(e.name)) walk(e.path)
                        } else if (isMediaName(e.name)) {
                            found += PhotoItem.create(e.path, e.size, e.modified, exifCache[exifKey(e.path, e.modified)])
                            count.incrementAndGet()
                        }
                    }
                    _status.value = IndexStatus.Scanning(folders.incrementAndGet(), count.get())
                }
                walk(root)
            }
            val list = found.sortedByDescending { it.dateTaken }
            _items.value = list
            saveCache(root, list)
            _status.value = IndexStatus.Idle
        } catch (e: Exception) {
            _status.value = IndexStatus.Error(e.message ?: "Indexing failed.")
            throw e
        }
    }

    private fun skipFolder(name: String) =
        name.startsWith(".") || name.startsWith("@") || name.startsWith("#") || name.equals("\$RECYCLE.BIN", true)

    // ---------- better dates from EXIF ----------

    fun isMetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm?.isActiveNetworkMetered ?: true
    }

    /**
     * For photos whose date only came from the file's modified time (e.g. iPhone IMG_1234.HEIC),
     * read just the first few hundred KB of the original to get the real "date taken".
     */
    suspend fun refineDates(allowMetered: Boolean) = withContext(Dispatchers.IO) {
        if (!allowMetered && isMetered()) return@withContext
        val targets = _items.value.filter {
            it.dateSource == DateSource.MODIFIED && it.kind == MediaKind.PHOTO && !exifCache.containsKey(exifKey(it.path, it.modified))
        }
        if (targets.isEmpty()) return@withContext
        val done = AtomicInteger(0)
        val gate = Semaphore(4)
        _status.value = IndexStatus.ReadingDates(0, targets.size)
        coroutineScope {
            for (item in targets) {
                launch {
                    gate.withPermit {
                        if (!isActive) return@withPermit
                        // Only remember "no date" when the read worked; a network hiccup is retried next time.
                        val result = runCatching { readExifDate(item) }
                        if (result.isSuccess) exifCache[exifKey(item.path, item.modified)] = result.getOrNull() ?: 0L
                        val n = done.incrementAndGet()
                        _status.value = IndexStatus.ReadingDates(n, targets.size)
                        if (n % 150 == 0) applyExif()
                    }
                }
            }
        }
        applyExif()
        _status.value = IndexStatus.Idle
    }

    @Synchronized
    private fun applyExif() {
        saveExif()
        _items.value = _items.value.map { item ->
            if (item.dateSource != DateSource.MODIFIED) item
            else {
                val d = exifCache[exifKey(item.path, item.modified)]
                if (d != null && d > 0) item.withDate(d, DateSource.EXIF) else item
            }
        }.sortedByDescending { it.dateTaken }
    }

    private suspend fun readExifDate(item: PhotoItem): Long? {
        val limit = if (item.ext == "heic" || item.ext == "heif") 768L * 1024 else 256L * 1024
        val bytes = client.download(item.path, firstBytes = limit).use { resp ->
            val src = resp.body?.source() ?: return null
            val buf = Buffer()
            var total = 0L
            while (total < limit) {
                val r = src.read(buf, limit - total)
                if (r == -1L) break
                total += r
            }
            buf.readByteArray()
        }
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        return DateParser.fromExif(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            ?: DateParser.fromExif(exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED))
            ?: DateParser.fromExif(exif.getAttribute(ExifInterface.TAG_DATETIME))
    }

    fun resetStatus() { _status.value = IndexStatus.Idle }
}
