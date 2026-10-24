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
import kotlinx.coroutines.async
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

/** 接口健康状态 */
data class SourceHealth(
    val url: String,
    val checking: Boolean = false,
    val ok: Boolean = false,
    val siteCount: Int = 0,
    val message: String = "",
)

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
        SourceEntry("业余采集", "https://szyyds.cn/tv/x.json"),
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

    private val _health = MutableStateFlow<Map<String, SourceHealth>>(emptyMap())
    val health: StateFlow<Map<String, SourceHealth>> = _health

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    /** 聚合后：站点 key -> 来源接口名（用于换源弹窗分组） */
    val siteSourceName = mutableMapOf<String, String>()

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
        var effectiveUrl = url
        try {
            val raw = try {
                Http.getString(effectiveUrl)
            } catch (e: Exception) {
                // 127.0.0.1/localhost 在模拟器中指向模拟器自身，回退到宿主机别名 10.0.2.2
                val alt = localhostAlt(effectiveUrl)
                if (alt != null) {
                    effectiveUrl = alt
                    Http.getString(alt)
                } else throw e
            }
            val json = decodeConfig(raw)
            val cfg = parseConfigOrSiteApi(json, effectiveUrl)
            val resolved = resolveRelative(cfg, effectiveUrl)
            appContext.dataStore.edit {
                it[KEY_CONFIG_URL] = effectiveUrl
                it[KEY_CACHE_CONFIG] = gson.toJson(resolved)
                it[KEY_CACHE_URL] = effectiveUrl
            }
            _configUrl.value = effectiveUrl
            val savedSite = appContext.dataStore.data.first()[KEY_SITE_KEY]
            applyConfig(resolved, effectiveUrl, savedSite)
        } catch (e: Exception) {
            _configState.value = ConfigState.Error(e.message ?: "加载失败", effectiveUrl)
        }
    }

    /** 若是 localhost/127.0.0.1 则返回 10.0.2.2 版本，否则 null */
    private fun localhostAlt(url: String): String? {
        val host = url.toHttpUrlOrNull()?.host ?: return null
        return if (host == "127.0.0.1" || host.equals("localhost", true)) {
            url.replaceFirst(host, "10.0.2.2")
        } else null
    }

    /**
     * 解析配置：标准 TVBox 配置(含 sites)直接用；
     * 若返回的是苹果CMS站点API响应(含 list)，自动包装成单站点配置
     */
    private fun parseConfigOrSiteApi(json: String, url: String): ConfigBean {
        val obj = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("无法识别的配置格式")
        }
        return when {
            obj.has("sites") -> gson.fromJson(json, ConfigBean::class.java)
                ?: throw IllegalArgumentException("配置为空")
            obj.has("list") -> {
                val host = url.toHttpUrlOrNull()?.host ?: "本地"
                ConfigBean(
                    sites = listOf(
                        Site(
                            key = "siteapi_$url",
                            name = "本地源·$host",
                            type = 1,
                            api = url,
                        )
                    )
                )
            }
            else -> throw IllegalArgumentException("既不是 TVBox 配置也不是站点 API")
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

    // ---------------- 扫描与聚合 ----------------

    private data class CheckResult(
        val url: String,
        val name: String,
        val ok: Boolean,
        val siteCount: Int,
        val message: String,
        val config: ConfigBean?,
        val effectiveUrl: String = url,
    )

    /** 逐个扫描已保存接口并聚合健康源，替代手动切换 */
    suspend fun scanAndAggregate() = withContext(Dispatchers.IO) {
        if (_scanning.value) return@withContext
        _scanning.value = true
        try {
            val nameOf = mutableMapOf<String, String>()
            _sources.value.forEach { nameOf[it.url] = it.name }
            val urls = (_sources.value.map { it.url } + _configUrl.value).distinct()
            _health.value = urls.associateWith {
                SourceHealth(it, checking = true, message = "检测中…")
            }
            val results = urls.map { u ->
                async {
                    try {
                        checkSource(u, nameOf[u] ?: u.toHttpUrlOrNull()?.host ?: "当前接口")
                    } catch (e: Exception) {
                        CheckResult(u, nameOf[u] ?: u, false, 0, e.message ?: "检测失败", null)
                    }
                }
            }.map { it.await() }
            _health.value = results.associate {
                it.url to SourceHealth(it.url, checking = false, ok = it.ok, siteCount = it.siteCount, message = it.message)
            }
            // 同一有效地址（如 127.0.0.1 与 10.0.2.2 指向同一服务）去重
            val healthy = results
                .filter { it.ok && it.config != null }
                .distinctBy { it.effectiveUrl }
            if (healthy.isNotEmpty()) {
                val merged = mergeConfigs(healthy)
                _configState.value = ConfigState.Success(merged, "聚合 ${healthy.size} 个接口")
                val savedSite = appContext.dataStore.data.first()[KEY_SITE_KEY]
                val keep = merged.sites.firstOrNull { it.key == savedSite }
                _currentSite.value = keep
                    ?: merged.sites.firstOrNull { it.isSupported }
                    ?: merged.sites.firstOrNull()
            } else {
                _configState.value = ConfigState.Error("没有可用的接口，请检查网络或更换接口", _configUrl.value)
            }
        } finally {
            _scanning.value = false
        }
    }

    private suspend fun checkSource(url: String, name: String): CheckResult {
        return kotlinx.coroutines.withTimeoutOrNull(25_000) {
            var effective = url
            try {
                val raw = try {
                    Http.getString(effective)
                } catch (e: Exception) {
                    val alt = localhostAlt(effective)
                    if (alt != null) { effective = alt; Http.getString(alt) } else throw e
                }
                val cfg = resolveRelative(parseConfigOrSiteApi(decodeConfig(raw), effective), effective)
                val supported = cfg.sites.count { it.isSupported }
                when {
                    cfg.sites.isEmpty() -> CheckResult(url, name, false, 0, "配置中没有站点", null)
                    supported == 0 -> CheckResult(url, name, false, 0, "全部为爬虫源，无可用站点", null)
                    else -> CheckResult(url, name, true, supported, "正常", cfg, effective)
                }
            } catch (e: Exception) {
                CheckResult(url, name, false, 0, e.message ?: "连接失败", null)
            }
        } ?: CheckResult(url, name, false, 0, "检测超时", null)
    }

    /** 聚合多个健康配置：站点 key 加来源命名空间，直播/解析按 url 去重 */
    private fun mergeConfigs(inputs: List<CheckResult>): ConfigBean {
        siteSourceName.clear()
        val sites = inputs.flatMap { input ->
            input.config!!.sites.map { s ->
                val ns = "${input.url.hashCode()}::${s.key}"
                siteSourceName[ns] = input.name
                s.copy(key = ns)
            }
        }
        val lives = inputs.flatMap { it.config!!.lives }.distinctBy { it.url }
        val parses = inputs.flatMap { it.config!!.parses }.distinctBy { it.url }
        return ConfigBean(sites = sites, lives = lives, parses = parses)
    }
}
