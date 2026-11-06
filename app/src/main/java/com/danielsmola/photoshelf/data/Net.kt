package com.danielsmola.photoshelf.data

import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Two HTTP clients:
 *  - strict: normal certificate checking, used for QuickConnect, the relay and any internet address.
 *  - lenient: accepts the NAS's self-signed certificate, used ONLY for home-network (private) addresses,
 *    because DSM ships with a self-signed cert that phones won't trust on the LAN.
 */
object Net {
    val strict: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val lenient: OkHttpClient by lazy {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        strict.newBuilder()
            .sslSocketFactory(ctx.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home")) return true
        val parts = h.split('.')
        if (parts.size == 4 && parts.all { (it.toIntOrNull() ?: -1) in 0..255 }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) ||
                (a == 169 && b == 254) || (a == 100 && b in 64..127)
        }
        if (h.contains(':')) return h.startsWith("fe80") || h.startsWith("fc") || h.startsWith("fd")
        return false
    }

    fun clientFor(url: HttpUrl): OkHttpClient = if (isPrivateHost(url.host)) lenient else strict

    /** Picks the right client per request; handed to the image loader. */
    val routing: Call.Factory = object : Call.Factory {
        override fun newCall(request: Request): Call = clientFor(request.url).newCall(request)
    }
}
