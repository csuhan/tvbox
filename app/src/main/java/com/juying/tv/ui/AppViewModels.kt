package com.juying.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juying.tv.data.local.AppDatabase
import com.juying.tv.data.local.FavoriteEntity
import com.juying.tv.data.local.HistoryEntity
import com.juying.tv.data.model.*
import com.juying.tv.data.source.ConfigState
import com.juying.tv.data.source.SourceEntry
import com.juying.tv.data.source.SourceManager
import com.juying.tv.data.source.VodRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** 全局：配置 / 站点 / 源管理 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val configState: StateFlow<ConfigState> = SourceManager.configState
    val currentSite: StateFlow<Site?> = SourceManager.currentSite
    val sources: StateFlow<List<SourceEntry>> = SourceManager.sources
    val configUrl: StateFlow<String> = SourceManager.configUrl

    private val _homeReset = MutableStateFlow(0)
    val homeReset: StateFlow<Int> = _homeReset

    init {
        viewModelScope.launch { SourceManager.restore() }
    }

    fun loadConfig(url: String) {
        viewModelScope.launch {
            SourceManager.loadConfig(url)
            _homeReset.value += 1
        }
    }

    fun selectSite(site: Site) {
        SourceManager.selectSite(site)
        _homeReset.value += 1
    }

    fun addSource(name: String, url: String) = viewModelScope.launch { SourceManager.addSource(name, url) }
    fun removeSource(url: String) = viewModelScope.launch { SourceManager.removeSource(url) }
}

/** 首页：分类 + 列表 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val classes: List<VodClass> = emptyList(),
        val items: List<VodItem> = emptyList(),
        val page: Int = 1,
        val pageCount: Int = 1,
        val loadingMore: Boolean = false,
        val selectedType: String? = null, // null = 首页推荐
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var site: Site? = null
    private var loadJob: Job? = null

    fun bind(site: Site?) {
        if (site == this.site && _state.value.classes.isNotEmpty()) return
        this.site = site
        _state.value = UiState(loading = site != null)
        if (site != null) refresh()
    }

    fun refresh() {
        val s = site ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val sel = _state.value.selectedType
                val page = if (sel == null) VodRepository.home(s) else VodRepository.category(s, sel, 1)
                val classes = if (page.classes.isNotEmpty()) page.classes else _state.value.classes
                // 首页没分类时，从列表推断
                val finalClasses = classes.ifEmpty {
                    page.list.mapNotNull { it.typeName.takeIf { n -> n.isNotBlank() } }
                        .distinct().mapIndexed { i, n -> VodClass((i + 1).toString(), n) }
                }
                _state.value = UiState(
                    loading = false,
                    classes = finalClasses,
                    items = page.list,
                    page = page.page,
                    pageCount = if (page.pageCount <= 0) 1 else page.pageCount,
                    selectedType = sel,
                )
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun selectClass(typeId: String?) {
        if (_state.value.selectedType == typeId) return
        _state.update { it.copy(selectedType = typeId) }
        refresh()
    }

    fun loadMore() {
        val s = site ?: return
        val st = _state.value
        if (st.loading || st.loadingMore || st.page >= st.pageCount) return
        val tid = st.selectedType ?: return // 首页推荐不翻页
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = VodRepository.category(s, tid, st.page + 1)
                _state.update {
                    it.copy(
                        loadingMore = false,
                        items = it.items + page.list,
                        page = page.page,
                        pageCount = if (page.pageCount <= 0) it.pageCount else page.pageCount,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}

/** 搜索 */
class SearchViewModel(app: Application) : AndroidViewModel(app) {
    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val results: List<VodItem> = emptyList(),
        val searched: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var job: Job? = null

    val hotWords = listOf("凡人修仙传", "庆余年", "斗破苍穹", "脱口秀", "周杰伦", "流浪地球", "海贼王", "甄嬛")

    fun search(site: Site?, keyword: String) {
        if (site == null || keyword.isBlank()) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = UiState(loading = true, searched = true)
            try {
                val page = VodRepository.search(site, keyword.trim())
                _state.value = UiState(results = page.list, searched = true)
            } catch (e: Exception) {
                _state.value = UiState(error = e.message ?: "搜索失败", searched = true)
            }
        }
    }
}

/** 详情 */
class DetailViewModel(app: Application) : AndroidViewModel(app) {
    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val detail: VodDetail? = null,
        val sourceIndex: Int = 0,
        val reversed: Boolean = false,
        val history: HistoryEntity? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val db = AppDatabase.get(app)
    private var siteKey = ""
    private var vodId = ""

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    fun load(site: Site?, vodId: String) {
        if (site == null) return
        if (this.vodId == vodId && _state.value.detail != null) return
        this.vodId = vodId
        this.siteKey = site.key
        _state.value = UiState()
        viewModelScope.launch {
            launch {
                db.history().get(site.key, vodId)?.let { h -> _state.update { it.copy(history = h) } }
            }
            launch {
                db.favorite().isFavorite(site.key, vodId).collect { _isFavorite.value = it }
            }
            try {
                val d = VodRepository.detail(site, vodId)
                if (d == null) {
                    _state.update { it.copy(loading = false, error = "未找到该片源详情") }
                } else {
                    // 智能选线：优先选择地址为直连媒体(m3u8/mp4)的线路
                    val mediaRe = Regex("\\.(m3u8|mp4|flv|m4s)(\\?|#|$)", RegexOption.IGNORE_CASE)
                    val best = d.sources.indexOfFirst { src ->
                        src.episodes.firstOrNull()?.url?.let { mediaRe.containsMatchIn(it) } == true
                    }
                    _state.update { it.copy(loading = false, detail = d, sourceIndex = if (best >= 0) best else 0) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun selectSource(index: Int) = _state.update { it.copy(sourceIndex = index) }
    fun toggleReverse() = _state.update { it.copy(reversed = !it.reversed) }

    fun toggleFavorite() {
        val d = _state.value.detail ?: return
        viewModelScope.launch {
            if (_isFavorite.value) {
                db.favorite().delete(siteKey, vodId)
            } else {
                db.favorite().upsert(FavoriteEntity(siteKey, vodId, d.vodName, d.vodPic, d.vodRemarks))
            }
        }
    }
}

/** 直播 */
class LiveViewModel(app: Application) : AndroidViewModel(app) {
    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val groups: List<String> = emptyList(),
        val channels: List<LiveChannel> = emptyList(),
        val selectedGroup: String? = null,
        val liveSources: List<LiveSource> = emptyList(),
        val sourceIndex: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun bind(config: ConfigBean?) {
        val lives = config?.lives?.filter { it.type == 0 || it.type == 1 } ?: emptyList()
        if (lives.isEmpty()) {
            _state.value = UiState(loading = false, error = "当前接口没有配置直播源")
            return
        }
        if (_state.value.channels.isNotEmpty() && _state.value.liveSources == lives) return
        _state.update { it.copy(liveSources = lives, sourceIndex = 0) }
        load(lives[0], 0)
    }

    fun selectSource(index: Int) {
        val lives = _state.value.liveSources
        if (index in lives.indices) load(lives[index], index)
    }

    private fun load(live: LiveSource, index: Int) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, sourceIndex = index) }
            try {
                val channels = VodRepository.liveChannels(live)
                if (channels.isEmpty()) {
                    // 空源自动尝试下一个
                    val lives = _state.value.liveSources
                    val next = (index + 1 until lives.size).firstOrNull()
                    if (next != null) { load(lives[next], next); return@launch }
                }
                val groups = channels.map { it.group }.distinct()
                _state.update {
                    it.copy(
                        loading = false,
                        channels = channels,
                        groups = groups,
                        selectedGroup = groups.firstOrNull(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "直播源加载失败") }
            }
        }
    }

    fun selectGroup(g: String) = _state.update { it.copy(selectedGroup = g) }
}

/** 历史与收藏 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    val history: StateFlow<List<HistoryEntity>> = db.history().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites: StateFlow<List<FavoriteEntity>> = db.favorite().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistory() = viewModelScope.launch { db.history().clear() }
    fun removeHistory(siteKey: String, vodId: String) = viewModelScope.launch { db.history().delete(siteKey, vodId) }
}
