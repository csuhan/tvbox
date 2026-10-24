package com.juying.tv.data.source

import android.util.Xml
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.juying.tv.data.model.*
import com.juying.tv.data.remote.Http
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * MacCMS (苹果CMS) 采集 API 解析，支持 type=1(JSON) 与 type=0(XML)。
 * API 约定: ?ac=list 首页分类 / ?ac=videolist&t=&pg= 分类 / ?ac=videolist&wd= 搜索 / ?ac=detail&ids= 详情
 */
class MacCmsApi(private val site: Site) {

    private val headers: Map<String, String> by lazy { parseExtHeaders(site) }

    private fun url(ac: String, params: Map<String, String> = emptyMap()): String {
        val base = site.api.toHttpUrl()
        val b = base.newBuilder()
        // 清掉已有的 ac 参数再统一设置
        b.removeAllQueryParameters("ac")
        b.addQueryParameter("ac", ac)
        params.forEach { (k, v) ->
            b.removeAllQueryParameters(k)
            b.addQueryParameter(k, v)
        }
        return b.build().toString()
    }

    /** 首页：分类 + 推荐列表 */
    fun home(): VodPage = fetch(url("list"))

    /** 分类列表 */
    fun category(typeId: String, page: Int): VodPage =
        fetch(url("videolist", mapOf("t" to typeId, "pg" to page.toString())))

    /** 搜索 */
    fun search(keyword: String, page: Int = 1): VodPage =
        fetch(url("videolist", mapOf("wd" to keyword, "pg" to page.toString())))

    /** 详情（含播放地址） */
    fun detail(ids: String): VodDetail? {
        val page = fetch(url("detail", mapOf("ids" to ids)), fullDetail = true)
        return page.details.firstOrNull()
    }

    // ---------------- internal ----------------

    private class FetchResult(
        val page: VodPage,
        val details: List<VodDetail>,
    )

    private fun fetch(u: String, fullDetail: Boolean = false): FetchResult {
        val raw = Http.getString(u, headers)
        val text = raw.trim().removePrefix("﻿")
        return if (text.startsWith("{")) parseJson(text) else parseXml(text)
    }

    private fun fetch(u: String): VodPage = fetch(u, false).page

    // ---- JSON ----

    private fun parseJson(text: String): FetchResult {
        val root = JsonParser.parseString(text).asJsonObject
        val classes = root.getAsJsonArray("class")?.mapNotNull { el ->
            val o = el.asJsonObject
            val id = jstr(o, "type_id") ?: return@mapNotNull null
            VodClass(id, jstr(o, "type_name") ?: "")
        } ?: emptyList()
        val list = root.getAsJsonArray("list") ?: com.google.gson.JsonArray()
        val items = mutableListOf<VodItem>()
        val details = mutableListOf<VodDetail>()
        list.forEach { el ->
            val o = el.asJsonObject
            val id = jstr(o, "vod_id") ?: return@forEach
            items.add(
                VodItem(
                    vodId = id,
                    vodName = clean(jstr(o, "vod_name") ?: ""),
                    vodPic = jstr(o, "vod_pic") ?: "",
                    vodRemarks = clean(jstr(o, "vod_remarks") ?: ""),
                    vodYear = jstr(o, "vod_year") ?: "",
                    typeName = jstr(o, "type_name") ?: "",
                )
            )
            details.add(jsonToDetail(o))
        }
        val page = VodPage(
            page = jint(root, "page") ?: 1,
            pageCount = jint(root, "pagecount") ?: 1,
            total = jint(root, "total") ?: items.size,
            list = items,
            classes = classes,
        )
        return FetchResult(page, details)
    }

    private fun jsonToDetail(o: JsonObject): VodDetail {
        val playFrom = jstr(o, "vod_play_from") ?: ""
        val playUrl = jstr(o, "vod_play_url") ?: ""
        return VodDetail(
            vodId = jstr(o, "vod_id") ?: "",
            vodName = clean(jstr(o, "vod_name") ?: ""),
            vodPic = jstr(o, "vod_pic") ?: "",
            typeName = jstr(o, "type_name") ?: "",
            vodYear = jstr(o, "vod_year") ?: "",
            vodArea = jstr(o, "vod_area") ?: "",
            vodLang = jstr(o, "vod_lang") ?: "",
            vodActor = clean(jstr(o, "vod_actor") ?: ""),
            vodDirector = clean(jstr(o, "vod_director") ?: ""),
            vodContent = clean(jstr(o, "vod_content") ?: ""),
            vodScore = jstr(o, "vod_score") ?: "",
            vodRemarks = clean(jstr(o, "vod_remarks") ?: ""),
            sources = parsePlaySources(playFrom, playUrl),
        )
    }

    // ---- XML ----

    private fun parseXml(text: String): FetchResult {
        val items = mutableListOf<VodItem>()
        val details = mutableListOf<VodDetail>()
        val classes = mutableListOf<VodClass>()
        var page = 1; var pageCount = 1; var total = 0

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(text))

        var inVideo = false
        var cur: MutableMap<String, String>? = null
        var ddFlag: String? = null
        var tag: String? = null
        var tyId: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name.lowercase()
                    when (tag) {
                        "list" -> {
                            page = parser.getAttributeValue(null, "page")?.toIntOrNull() ?: 1
                            pageCount = parser.getAttributeValue(null, "pagecount")?.toIntOrNull() ?: 1
                            total = parser.getAttributeValue(null, "recordcount")?.toIntOrNull() ?: 0
                        }
                        "video" -> { inVideo = true; cur = mutableMapOf() }
                        "dd" -> ddFlag = parser.getAttributeValue(null, "flag")
                        "ty" -> tyId = parser.getAttributeValue(null, "id")
                    }
                }
                XmlPullParser.TEXT -> {
                    val t = parser.text ?: ""
                    if (inVideo && cur != null && tag != null) {
                        if (tag == "dd" && ddFlag != null) {
                            val key = "dd|$ddFlag"
                            cur!![key] = (cur!![key] ?: "") + t
                        } else {
                            cur!![tag!!] = (cur!![tag!!] ?: "") + t
                        }
                    } else if (tag == "ty" && tyId != null) {
                        classes.add(VodClass(tyId!!, t.trim()))
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "video" -> {
                            cur?.let { m ->
                                xmlToModels(m).let { (item, detail) ->
                                    items.add(item); details.add(detail)
                                }
                            }
                            inVideo = false; cur = null
                        }
                        "dd" -> ddFlag = null
                        "ty" -> tyId = null
                    }
                    tag = null
                }
            }
            event = parser.next()
        }
        return FetchResult(VodPage(page, pageCount, total, items, classes), details)
    }

    private fun xmlToModels(m: Map<String, String>): Pair<VodItem, VodDetail> {
        val flags = m.keys.filter { it.startsWith("dd|") }.map { it.removePrefix("dd|") }
        val playFrom = flags.joinToString("$$$")
        val playUrl = flags.joinToString("$$$") { m["dd|$it"] ?: "" }
        val item = VodItem(
            vodId = m["id"] ?: "",
            vodName = clean(m["name"] ?: ""),
            vodPic = m["pic"] ?: "",
            vodRemarks = clean(m["note"] ?: ""),
            vodYear = m["year"] ?: "",
            typeName = m["type"] ?: "",
        )
        val detail = VodDetail(
            vodId = m["id"] ?: "",
            vodName = clean(m["name"] ?: ""),
            vodPic = m["pic"] ?: "",
            typeName = m["type"] ?: "",
            vodYear = m["year"] ?: "",
            vodArea = m["area"] ?: "",
            vodLang = m["lang"] ?: "",
            vodActor = clean(m["actor"] ?: ""),
            vodDirector = clean(m["director"] ?: ""),
            vodContent = clean(m["des"] ?: ""),
            vodScore = m["score"] ?: "",
            vodRemarks = clean(m["note"] ?: ""),
            sources = parsePlaySources(playFrom, playUrl),
        )
        return item to detail
    }

    companion object {
        /** 解析 vod_play_from / vod_play_url 为线路列表 */
        fun parsePlaySources(playFrom: String, playUrl: String): List<PlaySource> {
            if (playUrl.isBlank()) return emptyList()
            val fromSep = if (playFrom.contains("$$$")) "$$$" else ","
            val names = playFrom.split(fromSep).map { it.trim() }
            val urlGroups = playUrl.split("$$$")
            val result = mutableListOf<PlaySource>()
            urlGroups.forEachIndexed { i, group ->
                val episodes = group.split("#").mapNotNull { ep ->
                    val t = ep.trim()
                    if (t.isEmpty()) return@mapNotNull null
                    val idx = t.indexOf('$')
                    if (idx > 0) Episode(t.substring(0, idx).trim(), t.substring(idx + 1).trim())
                    else Episode("第${result.size + 1}集", t)
                }
                if (episodes.isNotEmpty()) {
                    val name = names.getOrNull(i)?.ifBlank { null } ?: "线路${i + 1}"
                    result.add(PlaySource(name, episodes))
                }
            }
            return result
        }

        private fun jstr(o: JsonObject, key: String): String? {
            val el = o.get(key) ?: return null
            if (el.isJsonNull) return null
            return try { el.asString } catch (e: Exception) { null }
        }

        private fun jint(o: JsonObject, key: String): Int? {
            val el = o.get(key) ?: return null
            if (el.isJsonNull) return null
            return try { el.asInt } catch (e: Exception) {
                try { el.asString.toInt() } catch (e2: Exception) { null }
            }
        }

        /** 清理 HTML 标签与实体 */
        fun clean(s: String): String {
            if (s.isEmpty()) return s
            return s.replace(Regex("<[^>]+>"), "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
                .trim()
        }

        fun parseExtHeaders(site: Site): Map<String, String> {
            val ext = site.ext ?: return emptyMap()
            return try {
                if (ext.isJsonObject) {
                    val h = ext.asJsonObject.get("header")
                    if (h != null && h.isJsonObject) {
                        h.asJsonObject.entrySet().associate { it.key to it.value.asString }
                    } else emptyMap()
                } else emptyMap()
            } catch (e: Exception) { emptyMap() }
        }
    }
}
