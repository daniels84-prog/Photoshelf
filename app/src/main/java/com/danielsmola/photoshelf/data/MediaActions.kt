package com.danielsmola.photoshelf.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Everything that gets a photo OFF the NAS: save to the phone's gallery, copy to a folder
 * the user picks, share, or put on the clipboard. Every one of these only READS from the NAS.
 */
class MediaActions(private val context: Context, private val client: SynologyClient) {
    private val authority = context.packageName + ".files"
    private val shareDir = File(context.cacheDir, "shared")

    /** Saves originals into Pictures/PhotoShelf (videos into Movies/PhotoShelf). */
    suspend fun saveToPhone(items: List<PhotoItem>, progress: (Int) -> Unit): Int = withContext(Dispatchers.IO) {
        var saved = 0
        items.forEachIndexed { i, item ->
            progress(i)
            saveOne(item)
            saved++
        }
        saved
    }

    private suspend fun saveOne(item: PhotoItem) {
        val resolver = context.contentResolver
        val isVideo = item.kind == MediaKind.VIDEO
        val (collection, dir) = when {
            isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MOVIES
            item.ext in DECODABLE_EXT || item.ext == "tif" || item.ext == "tiff" || item.ext == "dng" ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_PICTURES
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/PhotoShelf")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (collection != MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)) {
                put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTaken)
            }
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("The phone refused to create ${item.name}.")
        try {
            client.download(item.path).use { resp ->
                val body = resp.body ?: throw IOException("Empty download")
                resolver.openOutputStream(uri)?.use { out -> body.byteStream().copyTo(out) }
                    ?: throw IOException("Couldn't write ${item.name}.")
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /** Copies originals into a folder chosen with the system folder picker (SD card, Drive, etc.). */
    suspend fun copyToFolder(tree: Uri, items: List<PhotoItem>, progress: (Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var copied = 0
        items.forEachIndexed { i, item ->
            progress(i)
            val doc = DocumentsContract.createDocument(resolver, parent, item.mimeType, item.name)
                ?: throw IOException("Couldn't create ${item.name} in that folder.")
            try {
                client.download(item.path).use { resp ->
                    val body = resp.body ?: throw IOException("Empty download")
                    resolver.openOutputStream(doc)?.use { out -> body.byteStream().copyTo(out) }
                        ?: throw IOException("Couldn't write ${item.name}.")
                }
                copied++
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, doc) } // only our own half-written copy
                throw e
            }
        }
        copied
    }

    /** Downloads originals into the app's private cache and returns shareable content:// URIs. */
    suspend fun cacheForSharing(items: List<PhotoItem>, progress: (Int) -> Unit): List<Uri> = withContext(Dispatchers.IO) {
        shareDir.deleteRecursively()
        shareDir.mkdirs()
        items.mapIndexed { i, item ->
            progress(i)
            val dir = File(shareDir, i.toString()).apply { mkdirs() }
            val f = File(dir, item.name)
            client.download(item.path).use { resp ->
                val body = resp.body ?: throw IOException("Empty download")
                f.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            FileProvider.getUriForFile(context, authority, f)
        }
    }

    fun shareIntent(uris: List<Uri>, items: List<PhotoItem>): Intent {
        val types = items.map { it.mimeType }.distinct()
        val type = when {
            types.size == 1 -> types[0]
            items.all { it.kind == MediaKind.PHOTO } -> "image/*"
            items.all { it.kind == MediaKind.VIDEO } -> "video/*"
            else -> "*/*"
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = type
        val clip = ClipData.newRawUri(items.first().name, uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        send.clipData = clip
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, if (uris.size == 1) "Share photo" else "Share ${uris.size} items")
    }

    fun copyToClipboard(uri: Uri, item: PhotoItem) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newUri(context.contentResolver, item.name, uri))
    }

    /** Intent to play a video in the phone's video player. */
    suspend fun videoIntent(item: PhotoItem): Intent {
        client.ensureSession()
        return if (!client.isLanRoute) {
            // Stream straight from the NAS (valid HTTPS certificate on this route).
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(client.downloadUrl(item.path, open = true)), item.mimeType)
        } else {
            // On the home network the NAS uses a self-signed cert other apps won't accept,
            // so download to cache first.
            val uri = cacheForSharing(listOf(item)) {}.first()
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
