package com.juying.tv.data.source

import com.juying.tv.data.model.LiveChannel
import com.juying.tv.data.remote.Http

object LiveParser {

    fun load(url: String, ua: String? = null): List<LiveChannel> {
        val headers = if (ua != null) mapOf("User-Agent" to ua) else emptyMap()
        val bytes = Http.getBytes(url, headers)
        val text = decodeSmart(bytes).trim().removePrefix("﻿")
        return if (text.startsWith("#EXTM3U", ignoreCase = true)) parseM3u(text) else parseTxt(text)
    }

    /** 直播列表常见 GBK 编码，自动识别 */
    private fun decodeSmart(bytes: ByteArray): String {
        return try {
            val s = String(bytes, Charsets.UTF_8)
            if (s.contains('�')) String(bytes, charset("GBK")) else s
        } catch (e: Exception) {
            try { String(bytes, charset("GBK")) } catch (e2: Exception) { String(bytes) }
        }
    }

    /** 标准 m3u: #EXTINF:-1 tvg-name=".." tvg-logo=".." group-title="..",频道名 */
    fun parseM3u(text: String): List<LiveChannel> {
        val channels = mutableListOf<LiveChannel>()
        var name: String? = null
        var group = "默认"
        var logo: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val info = line.substringAfter(":", "")
                    name = info.substringAfterLast(",").trim().ifBlank { null }
                    group = attr(info, "group-title") ?: "默认"
                    logo = attr(info, "tvg-logo")
                    if (name == null) name = attr(info, "tvg-name")
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val n = name
                    if (n != null) {
                        val urls = line.split("#").map { it.trim() }.filter { it.isNotEmpty() }
                        channels.add(LiveChannel(n, urls, group, logo))
                    }
                    name = null; group = "默认"; logo = null
                }
            }
        }
        return channels
    }

    /** txt 格式: 分组,#genre# 然后每行 频道名,url (多线路用#分隔) */
    fun parseTxt(text: String): List<LiveChannel> {
        val channels = mutableListOf<LiveChannel>()
        var group = "默认"
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split(",", limit = 2)
            if (parts.size < 2) return@forEach
            if (parts[1].trim().equals("#genre#", ignoreCase = true)) {
                group = parts[0].trim()
            } else {
                val urls = parts[1].split("#").map { it.trim() }.filter { it.startsWith("http") || it.contains("://") }
                if (urls.isNotEmpty()) channels.add(LiveChannel(parts[0].trim(), urls, group))
            }
        }
        return channels
    }

    private fun attr(info: String, key: String): String? {
        val m = Regex("$key=\"([^\"]*)\"").find(info) ?: return null
        return m.groupValues[1].ifBlank { null }
    }
}
