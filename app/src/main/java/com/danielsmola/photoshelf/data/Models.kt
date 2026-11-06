package com.danielsmola.photoshelf.data

import java.time.LocalDateTime
import java.time.ZoneId

enum class MediaKind { PHOTO, VIDEO }

/** Where an item's "date taken" came from, best first. */
enum class DateSource(val label: String) {
    EXIF("from photo data"),
    FILENAME("from file name"),
    MODIFIED("file modified date"),
}

enum class Category(val label: String) {
    ALL("All"), PHOTOS("Photos"), VIDEOS("Videos"), SCREENSHOTS("Screenshots")
}

enum class Grouping(val label: String) { DAY("Day"), MONTH("Month"), YEAR("Year") }

enum class SortOrder(val label: String) { NEWEST("Newest first"), OLDEST("Oldest first"), NAME("Name") }

val PHOTO_EXT = setOf(
    "jpg", "jpeg", "png", "heic", "heif", "gif", "webp", "bmp",
    "dng", "cr2", "cr3", "nef", "arw", "orf", "rw2", "raf", "tif", "tiff",
)
val VIDEO_EXT = setOf("mp4", "mov", "m4v", "3gp", "avi", "mkv", "mts", "m2ts", "wmv", "webm")

/** Formats Android can decode at full size in the viewer. */
val DECODABLE_EXT = setOf("jpg", "jpeg", "png", "heic", "heif", "gif", "webp", "bmp")

fun isMediaName(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in PHOTO_EXT || ext in VIDEO_EXT
}

class PhotoItem(
    /** Full NAS path, e.g. /Photo/2019/20190704_123456.jpg */
    val path: String,
    val size: Long,
    /** Epoch millis. */
    val modified: Long,
    /** Epoch millis, best guess of when it was taken. */
    val dateTaken: Long,
    val dateSource: DateSource,
) {
    val name: String = path.substringAfterLast('/')
    val folder: String = path.substringBeforeLast('/')
    val ext: String = name.substringAfterLast('.', "").lowercase()
    val kind: MediaKind = if (ext in VIDEO_EXT) MediaKind.VIDEO else MediaKind.PHOTO
    val isScreenshot: Boolean =
        name.contains("screenshot", ignoreCase = true) || folder.contains("screenshot", ignoreCase = true)
    val mimeType: String get() = mimeFor(ext)

    fun withDate(date: Long, source: DateSource) = PhotoItem(path, size, modified, date, source)

    override fun equals(other: Any?) = other is PhotoItem && other.path == path && other.dateTaken == dateTaken
    override fun hashCode() = path.hashCode()

    companion object {
        fun create(path: String, size: Long, modified: Long, exifDate: Long?): PhotoItem {
            val name = path.substringAfterLast('/')
            if (exifDate != null && exifDate > 0) return PhotoItem(path, size, modified, exifDate, DateSource.EXIF)
            val fromName = DateParser.fromFileName(name)
            if (fromName != null) return PhotoItem(path, size, modified, fromName, DateSource.FILENAME)
            return PhotoItem(path, size, modified, modified, DateSource.MODIFIED)
        }
    }
}

fun mimeFor(ext: String): String = when (ext) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    "dng" -> "image/x-adobe-dng"
    "cr2" -> "image/x-canon-cr2"
    "cr3" -> "image/x-canon-cr3"
    "nef" -> "image/x-nikon-nef"
    "arw" -> "image/x-sony-arw"
    "orf" -> "image/x-olympus-orf"
    "rw2" -> "image/x-panasonic-rw2"
    "raf" -> "image/x-fuji-raf"
    "tif", "tiff" -> "image/tiff"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "m4v" -> "video/x-m4v"
    "3gp" -> "video/3gpp"
    "avi" -> "video/x-msvideo"
    "mkv" -> "video/x-matroska"
    "mts", "m2ts" -> "video/mp2t"
    "wmv" -> "video/x-ms-wmv"
    "webm" -> "video/webm"
    else -> "application/octet-stream"
}

object DateParser {
    // 20190704_123456, 2019-07-04 12.34.56, Screenshot_20190704-123456, PXL_20230101_123456789
    private val dateTime = Regex(
        """(?<!\d)((?:19|20)\d{2})[-_.]?(\d{2})[-_.]?(\d{2})[ _T.-]?(\d{2})[-_.:h]?(\d{2})[-_.:m]?(\d{2})"""
    )
    private val dateOnly = Regex("""(?<!\d)((?:19|20)\d{2})[-_.]?(\d{2})[-_.]?(\d{2})(?!\d)""")
    private val epochMillis = Regex("""^(1[2-9]\d{11})(?!\d)""")

    fun fromFileName(name: String): Long? {
        val base = name.substringBeforeLast('.')
        val zone = ZoneId.systemDefault()
        dateTime.find(base)?.let { m ->
            val g = m.groupValues
            build(g[1], g[2], g[3], g[4], g[5], g[6])?.let { return it.atZone(zone).toInstant().toEpochMilli() }
        }
        dateOnly.find(base)?.let { m ->
            val g = m.groupValues
            build(g[1], g[2], g[3], "12", "00", "00")?.let { return it.atZone(zone).toInstant().toEpochMilli() }
        }
        epochMillis.find(base)?.let { m ->
            val ms = m.groupValues[1].toLong()
            if (ms in 946_684_800_000L..System.currentTimeMillis() + 86_400_000L) return ms
        }
        return null
    }

    private fun build(y: String, mo: String, d: String, h: String, mi: String, s: String): LocalDateTime? {
        val year = y.toInt(); val month = mo.toInt(); val day = d.toInt()
        val hour = h.toInt(); val min = mi.toInt(); val sec = s.toInt()
        val nowYear = LocalDateTime.now().year
        if (year !in 1990..(nowYear + 1) || month !in 1..12 || day !in 1..31) return null
        if (hour > 23 || min > 59 || sec > 59) return null
        return runCatching { LocalDateTime.of(year, month, day, hour, min, sec) }.getOrNull()
    }

    private val exifFormat = java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun fromExif(value: String?): Long? {
        val v = value?.trim() ?: return null
        if (v.length < 19 || v.startsWith("0000")) return null
        return runCatching {
            LocalDateTime.parse(v.substring(0, 19), exifFormat)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }
}

data class Section(
    val key: String,
    val title: String,
    val subtitle: String,
    val year: Int,
    val items: List<PhotoItem>,
)

data class AlbumInfo(
    val name: String,
    /** Folder path this album covers. */
    val prefix: String,
    /** True for the "loose files in the main folder" album (no recursion). */
    val directOnly: Boolean,
    val count: Int,
    val cover: PhotoItem,
    val newest: Long,
    val oldest: Long,
    val isYear: Boolean,
)

data class FolderNode(
    val path: String,
    val name: String,
    val subfolders: List<FolderNode>,
    val files: List<PhotoItem>,
    val total: Int,
)

data class RemoteEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
)
