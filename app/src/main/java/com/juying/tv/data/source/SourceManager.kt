package com.juying.tv.data.source

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.juying.tv.data.model.ConfigBean
import com.juying.tv.data.model.Site
import com.juying.tv.data.remote.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val Context.dataStore by preferencesDataStore(name = "tvbox")

data class SourceEntry(val name: String, val url: String)

sealed interface ConfigState {
    data object Idle : ConfigState
    data object Loading : ConfigState
    data class Success(val config: ConfigBean, val sourceUrl: String) : ConfigState
    data class Error(val message: String, val sourceUrl: String) : ConfigState
}

object SourceManager {
    private val gson = Gson()
    private lateinit var appContext: Context

    private val KEY_CONFIG_URL = stringPreferencesKey("config_url")
    private val KEY_SITE_KEY = stringPreferencesKey("site_key")
    private val KEY_SOURCES = stringPreferencesKey("source_list")
    private val KEY_CACHE_CONFIG = stringPreferencesKey("cache_config")
    private val KEY_CACHE_URL = stringPreferencesKey("cache_url")

    /** 推荐接口（来自聚玩盒子等公开列表） */
    val recommended = listOf(
        SourceEntry("🔞 业余采集", "https://szyyds.cn/tv/x.json"),
        SourceEntry("欧歌", "http://tv.nxog.top/m/"),
        SourceEntry("俊于", "http://home.jundie.top:81/top98.json"),
        SourceEntry("饭太硬", "http://fty.888484.xyz/tv"),
        SourceEntry("王小二", "http://tvbox.王二小放牛娃.top"),
        SourceEntry("noimank", "https://gitlab.com/noimank/tvbox/-/raw/main/tvbox1.json"),
        SourceEntry("OK 接口", "http://ok321.top/ok"),
        SourceEntry("荷城茶秀", "http://rihou.cc:88/荷城茶秀"),
    )

    private val DEFAULT_URL = recommended[0].url

    private val _configState = MutableStateFlow<ConfigState>(ConfigState.Idle)
    val configState: StateFlow<ConfigState> = _configState

    private val _currentSite = MutableStateFlow<Site?>(null)
    val currentSite: StateFlow<Site?> = _currentSite

    private val _sources = MutableStateFlow<List<SourceEntry>>(emptyList())
    val sources: StateFlow<List<SourceEntry>> = _sources

    private val _configUrl = MutableStateFlow(DEFAULT_URL)
    val configUrl: StateFlow<String> = _configUrl

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun restore() = withContext(Dispatchers.IO) {
        val prefs = appContext.dataStore.data.first()
        _configUrl.value = prefs[KEY_CONFIG_URL] ?: DEFAULT_URL
        _sources.value = parseSources(prefs[KEY_SOURCES])
        // 先用缓存秒开，再后台刷新
        val cached = prefs[KEY_CACHE_CONFIG]
        val cachedUrl = prefs[KEY_CACHE_URL]
        if (cached != null && cachedUrl == _configUrl.value) {
            try {
                val cfg = gson.fromJson(cached, ConfigBean::class.java)
                applyConfig(cfg, cachedUrl, prefs[KEY_SITE_KEY])
            } catch (_: Exception) { }
        }
        loadConfig(_configUrl.value)
    }

    /** 加载并解析接口配置 */
    suspend fun loadConfig(url: String) = withContext(Dispatchers.IO) {
        _configState.value = ConfigState.Loading
        try {
            val raw = Http.getString(url)
            val json = decodeConfig(raw)
            val cfg = gson.fromJson(json, ConfigBean::class.java)
                ?: throw IllegalArgumentException("配置为空")
            val resolved = resolveRelative(cfg, url)
            appContext.dataStore.edit {
                it[KEY_CONFIG_URL] = url
                it[KEY_CACHE_CONFIG] = gson.toJson(resolved)
                it[KEY_CACHE_URL] = url
            }
            _configUrl.value = url
            val savedSite = appContext.dataStore.data.first()[KEY_SITE_KEY]
            applyConfig(resolved, url, savedSite)
        } catch (e: Exception) {
            _configState.value = ConfigState.Error(e.message ?: "加载失败", url)
        }
    }

    private fun applyConfig(cfg: ConfigBean, url: String, preferredSiteKey: String?) {
        _configState.value = ConfigState.Success(cfg, url)
        val sites = cfg.sites
        val keep = sites.firstOrNull { it.key == preferredSiteKey }
        val first = sites.firstOrNull { it.isSupported } ?: sites.firstOrNull()
        _currentSite.value = keep ?: first
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun selectSite(site: Site) {
        _currentSite.value = site
        scope.launch {
            appContext.dataStore.edit { it[KEY_SITE_KEY] = site.key }
        }
    }

    suspend fun addSource(name: String, url: String) = withContext(Dispatchers.IO) {
        val list = _sources.value.toMutableList()
        list.removeAll { it.url == url }
        list.add(0, SourceEntry(name.ifBlank { url }, url))
        _sources.value = list
        appContext.dataStore.edit { it[KEY_SOURCES] = gson.toJson(list) }
    }

    suspend fun removeSource(url: String) = withContext(Dispatchers.IO) {
        val list = _sources.value.toMutableList().also { l -> l.removeAll { it.url == url } }
        _sources.value = list
        appContext.dataStore.edit { it[KEY_SOURCES] = gson.toJson(list) }
    }

    private fun parseSources(json: String?): List<SourceEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<SourceEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    /** 配置可能是纯 JSON，也可能整体 Base64 */
    private fun decodeConfig(raw: String): String {
        val t = raw.trim().removePrefix("﻿")
        if (t.startsWith("{")) return t
        // 有些配置前面带垃圾字符，找第一个 {
        val idx = t.indexOf('{')
        if (idx >= 0) return t.substring(idx)
        return try {
            String(Base64.decode(t, Base64.DEFAULT))
        } catch (e: Exception) {
            throw IllegalArgumentException("无法识别的配置格式")
        }
    }

    /** 将配置内相对地址解析为绝对地址 */
    private fun resolveRelative(cfg: ConfigBean, configUrl: String): ConfigBean {
        val base = configUrl.toHttpUrlOrNull() ?: return cfg
        fun resolve(u: String): String {
            if (u.isBlank()) return u
            val t = u.trim()
            if (t.startsWith("http", ignoreCase = true) || t.contains("://")) return t
            return try { base.resolve(t)?.toString() ?: t } catch (e: Exception) { t }
        }
        return cfg.copy(
            sites = cfg.sites.map { s ->
                if (s.api.startsWith("csp_", true) || s.api.startsWith("clan", true)) s
                else s.copy(api = resolve(s.api))
            },
            lives = cfg.lives.map { l ->
                if (l.url.startsWith("http", true)) l else l.copy(url = resolve(l.url))
            },
        )
    }
}
