package com.danielsmola.photoshelf.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Candidate(val url: String, val label: String, val priority: Int)

/**
 * Turns a QuickConnect ID (or a typed address) into a working base URL, the same way
 * DS file / DS photo do: ask Synology's QuickConnect servers where the NAS is, then try
 * the home-network address, any direct internet address, and finally the QuickConnect relay.
 */
object QuickConnect {
    private val JSON_TYPE = "application/json".toMediaType()

    fun looksLikeAddress(input: String): Boolean {
        val s = input.trim()
        return s.contains('.') || s.contains(':') || s.contains('/')
    }

    suspend fun resolve(input: String): Candidate = coroutineScope {
        val cands = if (looksLikeAddress(input)) directCandidates(input) else candidates(input)
        val results = cands.map { c -> async(Dispatchers.IO) { c to probe(c) } }.awaitAll()
        results.filter { it.second }.minByOrNull { it.first.priority }?.first
            ?: throw IOException(
                if (looksLikeAddress(input)) "Couldn't reach the NAS at that address."
                else "Found your QuickConnect ID, but couldn't reach the NAS. Check that it's on and QuickConnect is enabled."
            )
    }

    private fun directCandidates(input: String): List<Candidate> {
        val s = input.trim().trimEnd('/')
        if (s.startsWith("http://") || s.startsWith("https://")) return listOf(Candidate(s, "Direct address", 0))
        val hasPort = Regex(":\\d+$").containsMatchIn(s)
        return if (hasPort) {
            listOf(Candidate("https://$s", "Direct address", 0), Candidate("http://$s", "Direct address", 1))
        } else {
            listOf(
                Candidate("https://$s:5001", "Direct address", 0),
                Candidate("https://$s", "Direct address", 1),
                Candidate("http://$s:5000", "Direct address", 2),
            )
        }
    }

    private suspend fun candidates(id: String): List<Candidate> = withContext(Dispatchers.IO) {
        val qid = id.trim().lowercase()
        var replies = runCatching { serv("global.quickconnect.to", qid) }.getOrElse {
            throw IOException("Couldn't reach Synology QuickConnect. Check the phone's internet connection.")
        }
        if (replies.none { it.optInt("errno", -1) == 0 }) {
            val sites = replies.flatMap { o ->
                val a = o.optJSONArray("sites") ?: return@flatMap emptyList<String>()
                (0 until a.length()).map { a.getString(it) }
            }.distinct()
            for (site in sites) {
                val r = runCatching { serv(site, qid) }.getOrNull() ?: continue
                if (r.any { it.optInt("errno", -1) == 0 }) { replies = r; break }
            }
        }
        if (replies.none { it.optInt("errno", -1) == 0 }) {
            throw IOException("QuickConnect ID \"$id\" wasn't found. Check the spelling (DSM > Control Panel > External Access > QuickConnect).")
        }

        val out = ArrayList<Candidate>()
        replies.forEachIndexed { index, o ->
            if (o.optInt("errno", -1) != 0) return@forEachIndexed
            val https = index == 0 // request order: mainapp_https, mainapp_http
            val scheme = if (https) "https" else "http"
            val server = o.optJSONObject("server") ?: JSONObject()
            val service = o.optJSONObject("service") ?: JSONObject()
            val env = o.optJSONObject("env") ?: JSONObject()
            val port = service.optInt("port", if (https) 5001 else 5000)
            val ext = service.optInt("ext_port", 0).takeIf { it > 0 } ?: port

            server.optJSONArray("interface")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val ip = arr.optJSONObject(i)?.optString("ip").orEmpty()
                    if (ip.isNotBlank() && ip != "0.0.0.0") {
                        out += Candidate("$scheme://$ip:$port", "Home network", if (https) 0 else 2)
                    }
                }
            }
            if (!https) return@forEachIndexed // never send passwords over plain http on the internet

            fun valid(s: String?) = !s.isNullOrBlank() && s != "NULL" && s != "0.0.0.0"
            server.optJSONObject("smartdns")?.optString("host")?.takeIf(::valid)?.let {
                out += Candidate("https://$it:$port", "Internet (direct)", 4)
            }
            server.optString("ddns").takeIf(::valid)?.let { out += Candidate("https://$it:$ext", "Internet (DDNS)", 4) }
            server.optString("fqdn").takeIf(::valid)?.let { out += Candidate("https://$it:$ext", "Internet (domain)", 4) }
            server.optJSONObject("external")?.optString("ip")?.takeIf(::valid)?.let {
                out += Candidate("https://$it:$ext", "Internet (direct)", 5)
            }
            val region = env.optString("relay_region")
            if (valid(region)) {
                env.optString("control_host").takeIf(::valid)?.let { host -> runCatching { requestTunnel(host, qid) } }
                out += Candidate("https://$qid.$region.quickconnect.to", "QuickConnect relay", 8)
            }
        }
        out.distinctBy { it.url }
    }

    private fun serv(host: String, qid: String): List<JSONObject> {
        val body = JSONArray()
        for (id in listOf("mainapp_https", "mainapp_http")) {
            body.put(JSONObject().apply {
                put("version", 1)
                put("command", "get_server_info")
                put("stop_when_error", false)
                put("stop_when_success", false)
                put("id", id)
                put("serverID", qid)
                put("is_gofile", false)
            })
        }
        return post("https://$host/Serv.php", body.toString())
    }

    /** Asks QuickConnect to open a relay tunnel, needed before the relay URL answers. */
    private fun requestTunnel(host: String, qid: String) {
        val body = JSONArray().put(JSONObject().apply {
            put("version", 1)
            put("command", "request_tunnel")
            put("stop_when_error", false)
            put("stop_when_success", true)
            put("id", "mainapp_https")
            put("serverID", qid)
            put("is_gofile", false)
        })
        post("https://$host/Serv.php", body.toString())
    }

    private fun post(url: String, json: String): List<JSONObject> {
        val req = Request.Builder().url(url).post(json.toRequestBody(JSON_TYPE)).build()
        val client = Net.strict.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string()?.trim().orEmpty()
            if (text.startsWith("[")) {
                val a = JSONArray(text)
                return (0 until a.length()).map { a.getJSONObject(it) }
            }
            return listOf(JSONObject(text.ifEmpty { "{}" }))
        }
    }

    /** True if a Synology web API answers at this base URL. */
    fun probe(c: Candidate): Boolean = runCatching {
        val url = c.url.toHttpUrl().newBuilder()
            .addPathSegments("webapi/query.cgi")
            .addQueryParameter("api", "SYNO.API.Info")
            .addQueryParameter("version", "1")
            .addQueryParameter("method", "query")
            .addQueryParameter("query", "SYNO.API.Auth")
            .build()
        val client = Net.clientFor(url).newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(9, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            r.isSuccessful && JSONObject(r.body?.string().orEmpty().ifEmpty { "{}" }).optBoolean("success")
        }
    }.getOrDefault(false)
}
