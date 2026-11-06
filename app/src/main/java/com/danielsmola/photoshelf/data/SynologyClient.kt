package com.danielsmola.photoshelf.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class SynologyException(val code: Int, message: String) : Exception(message)
class NeedOtpException : Exception("Enter the 6-digit code from your authenticator app.")
class SignInRequiredException(message: String) : Exception(message)

/**
 * READ-ONLY Synology File Station client.
 *
 * The only APIs this class can call are:
 *   SYNO.API.Info (query), SYNO.API.Auth (login), SYNO.FileStation.List (list_share, list),
 *   SYNO.FileStation.Thumb (get) and SYNO.FileStation.Download (download).
 * There is deliberately no code path for upload, rename, move, delete, copy-on-NAS or edit.
 */
class SynologyClient(private val store: SecureStore, private val settings: Settings) {
    @Volatile var base: String? = null
        private set
    @Volatile private var sid: String? = null
    private val mutex = Mutex()

    private val _route = MutableStateFlow("")
    /** Human label for how we're connected, e.g. "Home network" or "QuickConnect relay". */
    val route: StateFlow<String> = _route

    private val _session = MutableStateFlow(0)
    /** Bumps whenever the session id changes, so images can rebuild their URLs. */
    val session: StateFlow<Int> = _session

    val isSignedIn: Boolean get() = sid != null

    /** True when connected to a home-network address (self-signed cert accepted). */
    val isLanRoute: Boolean get() = base?.toHttpUrlOrNull()?.let { Net.isPrivateHost(it.host) } ?: false

    // ---------- sign in ----------

    suspend fun signIn(address: String, user: String, password: String, otp: String?, onStage: (String) -> Unit) {
        mutex.withLock {
            onStage("Finding your NAS…")
            val c = QuickConnect.resolve(address)
            setBase(c)
            onStage("Signing in…")
            val savedDid = store.load()?.takeIf { it.user.equals(user, true) && it.address == address }?.deviceId
            val (newSid, did) = io { login(c.url, user, password, otp, savedDid) }
            store.save(Credentials(address, user, password, did ?: savedDid))
            setSid(newSid)
        }
    }

    /** Re-establishes a session from saved credentials (app start, expired session, network change). */
    suspend fun restore() {
        mutex.withLock { restoreLocked(forceResolve = true) }
    }

    private suspend fun restoreLocked(forceResolve: Boolean) {
        val creds = store.load() ?: throw SignInRequiredException("Please sign in.")
        if (forceResolve || base == null) setBase(QuickConnect.resolve(creds.address))
        try {
            val (newSid, did) = io { login(base!!, creds.user, creds.password, null, creds.deviceId) }
            if (did != null && did != creds.deviceId) store.save(creds.copy(deviceId = did))
            setSid(newSid)
        } catch (e: NeedOtpException) {
            throw SignInRequiredException("Your NAS asked for a new two-factor code. Please sign in again.")
        } catch (e: SynologyException) {
            throw SignInRequiredException(e.message ?: "Please sign in again.")
        }
    }

    fun signOut() {
        sid = null
        _session.value++
        store.clearPassword()
    }

    private fun setBase(c: Candidate) {
        base = c.url
        _route.value = c.label
        settings.lastBase = c.url
    }

    private fun setSid(s: String) {
        sid = s
        _session.value++
    }

    private fun login(base: String, user: String, pass: String, otp: String?, did: String?): Pair<String, String?> {
        val form = FormBody.Builder()
            .add("api", "SYNO.API.Auth")
            .add("version", "6")
            .add("method", "login")
            .add("account", user)
            .add("passwd", pass)
            .add("session", "FileStation")
            .add("format", "sid")
            .add("device_name", "PhotoShelf-Android")
        if (!otp.isNullOrBlank()) {
            form.add("otp_code", otp.trim())
            form.add("enable_device_token", "yes")
        }
        if (!did.isNullOrBlank()) form.add("device_id", did)
        val url = base.toHttpUrl().newBuilder().addPathSegments("webapi/entry.cgi").build()
        val json = exec(Request.Builder().url(url).post(form.build()).build())
        if (json.optBoolean("success")) {
            val d = json.getJSONObject("data")
            return d.getString("sid") to d.optString("did").takeIf { it.isNotBlank() }
        }
        val code = json.optJSONObject("error")?.optInt("code") ?: -1
        throw when (code) {
            403 -> NeedOtpException()
            404 -> SynologyException(code, "That two-factor code didn't work. Try the newest code.")
            406 -> SynologyException(code, "This account must finish setting up 2-step verification in DSM first.")
            400 -> SynologyException(code, "Wrong username or password.")
            401 -> SynologyException(code, "This DSM account is disabled.")
            402 -> SynologyException(code, "This account isn't allowed to use File Station. Enable it in DSM > Control Panel > Application Privileges.")
            407 -> SynologyException(code, "The NAS has blocked this connection after failed attempts. Unblock it in DSM > Security > Account.")
            408, 409, 410 -> SynologyException(code, "The password has expired. Change it in DSM first.")
            else -> SynologyException(code, "Sign-in failed (Synology error $code).")
        }
    }

    // ---------- read-only file station calls ----------

    suspend fun listShares(): List<RemoteEntry> {
        val d = api(
            listOf(
                "api" to "SYNO.FileStation.List", "version" to "2", "method" to "list_share",
                "additional" to "[\"time\"]", "sort_by" to "name",
            )
        )
        val shares = d.optJSONArray("shares") ?: JSONArray()
        return (0 until shares.length()).map { parseEntry(shares.getJSONObject(it)) }
    }

    suspend fun listFolder(path: String): List<RemoteEntry> {
        val out = ArrayList<RemoteEntry>()
        var offset = 0
        while (true) {
            val d = api(
                listOf(
                    "api" to "SYNO.FileStation.List", "version" to "2", "method" to "list",
                    "folder_path" to path, "offset" to offset.toString(), "limit" to "1000",
                    "sort_by" to "name", "additional" to "[\"size\",\"time\"]", "filetype" to "all",
                )
            )
            val files = d.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) out += parseEntry(files.getJSONObject(i))
            offset += files.length()
            val total = d.optInt("total", offset)
            if (files.length() == 0 || offset >= total) break
        }
        return out
    }

    private fun parseEntry(o: JSONObject): RemoteEntry {
        val add = o.optJSONObject("additional")
        val time = add?.optJSONObject("time")
        return RemoteEntry(
            name = o.optString("name"),
            path = o.optString("path"),
            isDir = o.optBoolean("isdir"),
            size = add?.optLong("size") ?: 0L,
            modified = (time?.optLong("mtime") ?: 0L) * 1000L,
        )
    }

    private fun entryUrl(): HttpUrl.Builder =
        (base ?: settings.lastBase ?: "https://nas.invalid").toHttpUrl().newBuilder().addPathSegments("webapi/entry.cgi")

    /** size: small | medium | large | original */
    fun thumbUrl(path: String, size: String): String = entryUrl()
        .addQueryParameter("api", "SYNO.FileStation.Thumb")
        .addQueryParameter("version", "2")
        .addQueryParameter("method", "get")
        .addQueryParameter("path", path)
        .addQueryParameter("size", size)
        .addQueryParameter("_sid", sid.orEmpty())
        .build().toString()

    fun downloadUrl(path: String, open: Boolean = false): String = entryUrl()
        .addQueryParameter("api", "SYNO.FileStation.Download")
        .addQueryParameter("version", "2")
        .addQueryParameter("method", "download")
        // A plain path is split on commas by DSM, so wrap those in a JSON array.
        .addQueryParameter("path", if (path.contains(',')) JSONArray().put(path).toString() else path)
        .addQueryParameter("mode", if (open) "open" else "download")
        .addQueryParameter("_sid", sid.orEmpty())
        .build().toString()

    /** Opens a stream of the ORIGINAL file. Caller must close the response. */
    suspend fun download(path: String, firstBytes: Long? = null): Response {
        var reconnected = false
        var relogged = false
        while (true) {
            ensureSession()
            val usedSid = sid
            val req = Request.Builder().url(downloadUrl(path)).apply {
                if (firstBytes != null) header("Range", "bytes=0-${firstBytes - 1}")
            }.build()
            val resp = try {
                io { Net.clientFor(req.url).newCall(req).execute() }
            } catch (e: IOException) {
                if (!reconnected) { reconnected = true; reconnect(); continue } else throw e
            }
            val type = resp.header("Content-Type").orEmpty()
            if (resp.isSuccessful && !type.contains("json")) return resp
            resp.close()
            if (!relogged) { relogged = true; relogin(usedSid); continue }
            throw IOException("Couldn't download ${path.substringAfterLast('/')} (HTTP ${resp.code}).")
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException()
    }

    private suspend fun api(params: List<Pair<String, String>>): JSONObject {
        var reconnected = false
        var relogged = false
        while (true) {
            ensureSession()
            val usedSid = sid
            val url = (base ?: throw IOException("Not connected")).toHttpUrl().newBuilder()
                .addPathSegments("webapi/entry.cgi")
                .apply { params.forEach { addQueryParameter(it.first, it.second) } }
                .addQueryParameter("_sid", usedSid.orEmpty())
                .build()
            val json = try {
                io { exec(Request.Builder().url(url).build()) }
            } catch (e: IOException) {
                if (!reconnected) { reconnected = true; reconnect(); continue } else throw e
            }
            if (json.optBoolean("success")) return json.optJSONObject("data") ?: JSONObject()
            val code = json.optJSONObject("error")?.optInt("code") ?: -1
            if (code in SESSION_ERRORS && !relogged) { relogged = true; relogin(usedSid); continue }
            throw SynologyException(code, describe(code))
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException()
    }

    suspend fun ensureSession() {
        if (sid != null && base != null) return
        mutex.withLock { if (sid == null || base == null) restoreLocked(forceResolve = base == null) }
    }

    /** Network changed (e.g. left home Wi-Fi): find the best route again, keep the session. */
    private suspend fun reconnect() {
        mutex.withLock {
            val creds = store.load() ?: throw SignInRequiredException("Please sign in.")
            setBase(QuickConnect.resolve(creds.address))
        }
    }

    private suspend fun relogin(oldSid: String?) {
        mutex.withLock { if (sid == oldSid) restoreLocked(forceResolve = false) }
    }

    private fun exec(req: Request): JSONObject =
        Net.clientFor(req.url).newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            JSONObject(r.body?.string().orEmpty().ifEmpty { "{}" })
        }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun describe(code: Int) = when (code) {
        105 -> "This account doesn't have permission for that folder."
        408 -> "That folder no longer exists on the NAS."
        400, 401 -> "The NAS rejected the request (error $code)."
        else -> "Synology error $code."
    }

    companion object {
        private val SESSION_ERRORS = setOf(106, 107, 119)
    }
}
