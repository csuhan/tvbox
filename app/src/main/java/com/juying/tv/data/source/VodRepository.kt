package com.juying.tv.data.source

import com.juying.tv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VodRepository {

    private val apiCache = mutableMapOf<String, MacCmsApi>()

    private fun apiFor(site: Site): MacCmsApi =
        apiCache.getOrPut(site.key.ifBlank { site.api }) { MacCmsApi(site) }

    class SourceNotSupportedException(val siteName: String) :
        Exception("「$siteName」是爬虫源(type=3)，需要 Spider 环境，当前版本暂不支持，请切换其他站点")

    private fun requireSupported(site: Site): MacCmsApi {
        if (!site.isSupported) throw SourceNotSupportedException(site.name)
        return apiFor(site)
    }

    suspend fun home(site: Site): VodPage = withContext(Dispatchers.IO) {
        requireSupported(site).home()
    }

    suspend fun category(site: Site, typeId: String, page: Int): VodPage = withContext(Dispatchers.IO) {
        requireSupported(site).category(typeId, page)
    }

    suspend fun search(site: Site, keyword: String, page: Int = 1): VodPage = withContext(Dispatchers.IO) {
        requireSupported(site).search(keyword, page)
    }

    suspend fun detail(site: Site, vodId: String): VodDetail? = withContext(Dispatchers.IO) {
        requireSupported(site).detail(vodId)
    }

    suspend fun liveChannels(live: LiveSource): List<LiveChannel> = withContext(Dispatchers.IO) {
        LiveParser.load(live.url, live.ua)
    }
}
