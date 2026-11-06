package com.danielsmola.photoshelf.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pure functions that turn the flat index into the app's different views. */
object Organizer {
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    private val shortFmt = DateTimeFormatter.ofPattern("MMM yyyy")

    fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun shortDate(millis: Long): String = localDate(millis).format(shortFmt)

    fun countLabel(n: Int) = if (n == 1) "1 item" else "%,d items".format(n)

    fun filter(items: List<PhotoItem>, category: Category, includeVideos: Boolean, query: String): List<PhotoItem> {
        val q = query.trim()
        return items.filter { item ->
            val catOk = when (category) {
                Category.ALL -> includeVideos || item.kind == MediaKind.PHOTO
                Category.PHOTOS -> item.kind == MediaKind.PHOTO && !item.isScreenshot
                Category.VIDEOS -> item.kind == MediaKind.VIDEO
                Category.SCREENSHOTS -> item.isScreenshot
            }
            catOk && (q.isEmpty() || item.path.contains(q, ignoreCase = true))
        }
    }

    fun sort(items: List<PhotoItem>, order: SortOrder): List<PhotoItem> = when (order) {
        SortOrder.NEWEST -> items.sortedByDescending { it.dateTaken }
        SortOrder.OLDEST -> items.sortedBy { it.dateTaken }
        SortOrder.NAME -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Groups date-sorted items into Day / Month / Year sections. */
    fun timeline(items: List<PhotoItem>, grouping: Grouping): List<Section> {
        val out = ArrayList<Section>()
        var curKey: String? = null
        var curTitle = ""
        var curYear = 0
        var cur = ArrayList<PhotoItem>()
        fun flush() {
            if (curKey != null && cur.isNotEmpty()) out += Section(curKey!!, curTitle, countLabel(cur.size), curYear, cur)
        }
        for (item in items) {
            val d = localDate(item.dateTaken)
            val key = when (grouping) {
                Grouping.DAY -> d.toString()
                Grouping.MONTH -> "${d.year}-${d.monthValue}"
                Grouping.YEAR -> d.year.toString()
            }
            if (key != curKey) {
                flush()
                curKey = key
                curYear = d.year
                curTitle = when (grouping) {
                    Grouping.DAY -> d.format(dayFmt)
                    Grouping.MONTH -> d.format(monthFmt)
                    Grouping.YEAR -> d.year.toString()
                }
                cur = ArrayList()
            }
            cur.add(item)
        }
        flush()
        return out
    }

    /** Same month/day as today, from earlier years. */
    fun onThisDay(items: List<PhotoItem>): List<PhotoItem> {
        val today = LocalDate.now()
        return items.filter {
            val d = localDate(it.dateTaken)
            d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth && d.year < today.year
        }.sortedByDescending { it.dateTaken }
    }

    /** Each top-level folder under the library root becomes an album. */
    fun albums(items: List<PhotoItem>, root: String): List<AlbumInfo> {
        val groups = HashMap<String, MutableList<PhotoItem>>()
        val rootSlash = "$root/"
        for (item in items) {
            val rel = item.path.removePrefix(rootSlash)
            val key = if (rel.contains('/')) rel.substringBefore('/') else ""
            groups.getOrPut(key) { ArrayList() }.add(item)
        }
        return groups.map { (key, list) ->
            val newest = list.maxBy { it.dateTaken }
            AlbumInfo(
                name = key.ifEmpty { "Main folder" },
                prefix = if (key.isEmpty()) root else "$root/$key",
                directOnly = key.isEmpty(),
                count = list.size,
                cover = newest,
                newest = newest.dateTaken,
                oldest = list.minOf { it.dateTaken },
                isYear = key.matches(Regex("(19|20)\\d{2}")),
            )
        }.sortedWith(folderOrder { it.name })
    }

    fun albumItems(items: List<PhotoItem>, prefix: String, directOnly: Boolean): List<PhotoItem> =
        if (directOnly) items.filter { it.folder == prefix }
        else items.filter { it.path.startsWith("$prefix/") }

    /** Named folders A–Z first, then year folders newest first. */
    fun <T> folderOrder(name: (T) -> String): Comparator<T> = Comparator { a, b ->
        val na = name(a); val nb = name(b)
        val ya = na.toIntOrNull(); val yb = nb.toIntOrNull()
        when {
            ya != null && yb != null -> yb.compareTo(ya)
            ya != null -> 1
            yb != null -> -1
            else -> String.CASE_INSENSITIVE_ORDER.compare(na.trimStart('_'), nb.trimStart('_'))
        }
    }

    class FolderTree(private val root: String, items: List<PhotoItem>, private val order: SortOrder) {
        private val files = HashMap<String, MutableList<PhotoItem>>()
        private val subs = HashMap<String, MutableSet<String>>()
        private val totals = HashMap<String, Int>()

        init {
            for (item in items) {
                var f = item.folder
                files.getOrPut(f) { ArrayList() }.add(item)
                while (true) {
                    totals[f] = (totals[f] ?: 0) + 1
                    if (f.length <= root.length || !f.contains('/')) break
                    val parent = f.substringBeforeLast('/')
                    subs.getOrPut(parent) { HashSet() }.add(f)
                    f = parent
                }
            }
        }

        fun node(path: String): FolderNode {
            val subNodes = (subs[path] ?: emptySet()).map { p ->
                FolderNode(p, p.substringAfterLast('/'), emptyList(), emptyList(), totals[p] ?: 0)
            }.sortedWith(folderOrder { it.name })
            return FolderNode(
                path = path,
                name = if (path == root) root.substringAfterLast('/') else path.substringAfterLast('/'),
                subfolders = subNodes,
                files = sort(files[path] ?: emptyList(), order),
                total = totals[path] ?: 0,
            )
        }
    }
}
