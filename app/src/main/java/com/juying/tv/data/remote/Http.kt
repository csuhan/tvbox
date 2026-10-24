package com.juying.tv.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Http {
    const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** GET 并返回字符串，失败抛异常 */
    fun getString(url: String, headers: Map<String, String> = emptyMap()): String {
        val rb = Request.Builder().url(url).get().header("User-Agent", UA)
        headers.forEach { (k, v) -> rb.header(k, v) }
        client.newCall(rb.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw java.io.IOException("empty body")
            return body.string()
        }
    }

    fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        val rb = Request.Builder().url(url).get().header("User-Agent", UA)
        headers.forEach { (k, v) -> rb.header(k, v) }
        client.newCall(rb.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            return (resp.body ?: throw java.io.IOException("empty body")).bytes()
        }
    }
}
