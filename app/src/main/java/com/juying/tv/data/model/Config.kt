package com.juying.tv.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/** TVBox 接口配置（顶层 JSON） */
data class ConfigBean(
    val spider: String? = null,
    val wallpaper: String? = null,
    val sites: List<Site> = emptyList(),
    val parses: List<ParseBean> = emptyList(),
    val lives: List<LiveSource> = emptyList(),
    val flags: List<String> = emptyList(),
    val ads: List<String> = emptyList(),
)

data class Site(
    val key: String = "",
    val name: String = "",
    val type: Int = 3,
    val api: String = "",
    val searchable: Int = 1,
    val quickSearch: Int = 1,
    val filterable: Int = 1,
    val ext: JsonElement? = null,
    val jar: String? = null,
    val playerType: Int? = null,
    val categories: List<String>? = null,
) {
    val isSupported: Boolean get() = type == 0 || type == 1
}

data class ParseBean(
    val name: String = "",
    val type: Int = 0,
    val url: String = "",
    val ext: JsonElement? = null,
)

data class LiveSource(
    val name: String = "",
    val type: Int = 0,
    val url: String = "",
    val ua: String? = null,
    val epg: String? = null,
    val logo: String? = null,
    @SerializedName("playerType") val playerType: Int? = null,
)

/** 直播频道 */
data class LiveChannel(
    val name: String,
    val urls: List<String>,
    val group: String,
    val logo: String? = null,
)

/** 分类 */
data class VodClass(
    val typeId: String,
    val typeName: String,
)

/** 列表条目 */
data class VodItem(
    val vodId: String,
    val vodName: String,
    val vodPic: String = "",
    val vodRemarks: String = "",
    val vodYear: String = "",
    val typeName: String = "",
)

/** 一集 */
data class Episode(val name: String, val url: String)

/** 一条播放线路 */
data class PlaySource(val name: String, val episodes: List<Episode>)

/** 详情 */
data class VodDetail(
    val vodId: String,
    val vodName: String,
    val vodPic: String = "",
    val typeName: String = "",
    val vodYear: String = "",
    val vodArea: String = "",
    val vodLang: String = "",
    val vodActor: String = "",
    val vodDirector: String = "",
    val vodContent: String = "",
    val vodScore: String = "",
    val vodRemarks: String = "",
    val sources: List<PlaySource> = emptyList(),
)

/** 分页列表结果 */
data class VodPage(
    val page: Int = 1,
    val pageCount: Int = 1,
    val total: Int = 0,
    val list: List<VodItem> = emptyList(),
    val classes: List<VodClass> = emptyList(),
)
