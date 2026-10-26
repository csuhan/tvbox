/**
 * 聚影 Web — 纯客户端视频站（Vue 3 + hls.js）
 */
import {
  store, RECOMMENDED, fetchText, checkSource, aggregateConfigs, pool, MacCms, parseLive,
  saveHistory, toggleFav, isFav, looksLikeMedia, proxyUrlFor, mustProxy, fetchMedia,
} from './api.js';

const { createApp, reactive, computed, watch, onMounted, onUnmounted, ref, nextTick } = Vue;

// ---------------- 全局状态 ----------------
const G = reactive({
  route: { name: 'home', params: {} },
  configLoading: true,
  configError: '',
  sites: [],
  lives: [],
  site: null,          // 当前站点 {key,name,type,api}
  api: null,           // MacCms 实例
  sources: [],         // 接口列表
  currentSource: '',   // 当前接口 url（兼容保留）
  health: {},          // 各接口健康状态 {url:{checking,ok,siteCount,message}}
  scanning: false,
  aggInfo: '',         // 如 "聚合 5 个接口 · 62 站点"
  settingsOpen: false,
  toast: '',
  _toastTimer: 0,
});

function toast(msg) {
  G.toast = msg;
  clearTimeout(G._toastTimer);
  G._toastTimer = setTimeout(() => G.toast = '', 2400);
}

// ---------------- 哈希路由 ----------------
function parseHash() {
  const h = location.hash.replace(/^#/, '') || '/';
  const [path, query] = h.split('?');
  const seg = path.split('/').filter(Boolean);
  const q = Object.fromEntries(new URLSearchParams(query || ''));
  if (seg[0] === 'detail' && seg[1]) return { name: 'detail', params: { vodId: decodeURIComponent(seg[1]), ep: q.ep ? +q.ep : null } };
  if (seg[0] === 'search') return { name: 'search', params: { q: q.q || '' } };
  if (seg[0] === 'live') return { name: 'live', params: {} };
  if (seg[0] === 'mine') return { name: 'mine', params: {} };
  return { name: 'home', params: {} };
}
function nav(hash) { location.hash = hash; }
window.addEventListener('hashchange', () => {
  G.route = parseHash();
  window.scrollTo(0, 0);
});

// ---------------- SVG 图标 ----------------
const I = {
  play: '<svg class="ic" viewBox="0 0 24 24"><path d="M8 5.5v13l11-6.5-11-6.5z" fill="currentColor" stroke="none"/></svg>',
  pause: '<svg class="ic" viewBox="0 0 24 24"><path d="M7 5h3.5v14H7zM13.5 5H17v14h-3.5z" fill="currentColor" stroke="none"/></svg>',
  search: '<svg class="ic" viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><line x1="16.5" y1="16.5" x2="21" y2="21"/></svg>',
  full: '<svg class="ic" viewBox="0 0 24 24"><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/></svg>',
  vol: '<svg class="ic" viewBox="0 0 24 24"><path d="M4 9v6h4l5 4V5L8 9H4z"/><path d="M16.5 9a4.2 4.2 0 0 1 0 6"/></svg>',
  heart: '<svg class="ic" viewBox="0 0 24 24"><path d="M12 20s-7.5-4.6-9.3-9C1.3 7.6 3.3 4.5 6.5 4.5c2 0 3.6 1.1 4.5 2.7.9-1.6 2.5-2.7 4.5-2.7 3.2 0 5.2 3.1 3.8 6.5-1.8 4.4-9.3 9-9.3 9z"/></svg>',
  heartFill: '<svg class="ic" viewBox="0 0 24 24"><path d="M12 20s-7.5-4.6-9.3-9C1.3 7.6 3.3 4.5 6.5 4.5c2 0 3.6 1.1 4.5 2.7.9-1.6 2.5-2.7 4.5-2.7 3.2 0 5.2 3.1 3.8 6.5-1.8 4.4-9.3 9-9.3 9z" fill="currentColor" stroke="none"/></svg>',
  gear: '<svg class="ic" viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M12 2v2.5M12 19.5V22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M2 12h2.5M19.5 12H22M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8"/></svg>',
  phone: '<svg class="ic" viewBox="0 0 24 24"><rect x="7" y="2.5" width="10" height="19" rx="2.5"/><path d="M10.5 18.5h3"/></svg>',
  back: '<svg class="ic" viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7"/></svg>',
  film: '<svg class="ic" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M8 4v16M16 4v16"/></svg>',
  tv: '<svg class="ic" viewBox="0 0 24 24"><rect x="3" y="6" width="18" height="12" rx="2"/><path d="M9 21h6"/></svg>',
  clock: '<svg class="ic" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/></svg>',
  dl: '<svg class="ic" viewBox="0 0 24 24"><path d="M12 4v11M7 11l5 5 5-5M5 20h14"/></svg>',
  playLogo: '<svg viewBox="0 0 24 24"><path d="M8 5.5v13l11-6.5-11-6.5z"/></svg>',
};

// ---------------- 海报卡 ----------------
const VCard = {
  props: ['item', 'progress'],
  template: `
  <a class="vcard" :href="'#/detail/' + encodeURIComponent(item.vodId)">
    <div class="poster">
      <div class="ph" v-if="!item.vodPic">暂无海报</div>
      <img v-if="item.vodPic" :src="item.vodPic" loading="lazy" referrerpolicy="no-referrer"
           @load="e=>e.target.classList.add('ld')" @error="e=>e.target.remove()">
      <span class="badge" v-if="item.vodRemarks">{{ item.vodRemarks }}</span>
      <div class="hist-prog" v-if="progress>0"><i :style="{width:progress+'%'}"></i></div>
    </div>
    <div class="vtitle">{{ item.vodName }}</div>
    <div class="vsub" v-if="item.typeName || item.vodYear">{{ [item.typeName, item.vodYear].filter(Boolean).join(' · ') }}</div>
  </a>`,
};

// ---------------- HLS 代理 Loader ----------------
function makeProxyLoader(Hls) {
  return class ProxyLoader {
    constructor(config) {
      this.config = config;
      this.controller = null;
      this.stats = { aborted: false, loaded: 0, retry: 0, total: 0, chunkCount: 0, bwEstimate: 0, loading: { start: 0, first: 0, end: 0 }, parsing: { start: 0, end: 0 }, buffering: { start: 0, first: 0, end: 0 } };
    }
    destroy() { this.abort(); }
    abort() { if (this.controller) { this.controller.abort(); this.controller = null; } }
    load(context, config, callbacks) {
      const stats = this.stats;
      stats.loading.start = performance.now();
      const type = context.responseType === 'arraybuffer' ? 'arraybuffer' : 'text';
      const timeout = (config.timeout || 15000);
      const run = async () => {
        try {
          const { data } = await fetchMedia(context.url, type, timeout);
          stats.loading.first = stats.loading.first || performance.now();
          stats.loading.end = performance.now();
          stats.loaded = stats.total = data.byteLength ? data.byteLength : data.length;
          stats.chunkCount = 1;
          callbacks.onSuccess({ url: context.url, data }, stats, context, null);
        } catch (e) {
          stats.loading.end = performance.now();
          callbacks.onError({ code: 0, text: e.message || 'network error' }, context, null, stats);
        }
      };
      run();
    }
  };
}

// ---------------- 播放器 ----------------
const Player = {
  props: ['url', 'startPos', 'title'],
  emits: ['ended', 'timeupdate', 'error'],
  data: () => ({
    playing: false, time: 0, dur: 0, speed: 1, loading: true,
    error: '', isLive: false, showCtrl: true, muted: false,
  }),
  computed: {
    timeFmt() { return this.fmt(this.time); },
    durFmt() { return this.fmt(this.dur); },
    pct() { return this.dur > 0 ? (this.time / this.dur * 100) : 0; },
    speeds() { return [0.5, 1, 1.25, 1.5, 2, 3]; },
  },
  watch: {
    url: {
      immediate: true,
      handler(u) { nextTick(() => this.attach(u)); },
    },
  },
  methods: {
    fmt(s) {
      s = Math.max(0, Math.floor(s || 0));
      const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), ss = s % 60;
      const p = n => String(n).padStart(2, '0');
      return h > 0 ? `${h}:${p(m)}:${p(ss)}` : `${p(m)}:${p(ss)}`;
    },
    attach(u) {
      this.destroyHls();
      this.error = '';
      if (!u) { this.loading = false; return; }
      const v = this.$refs.video;
      if (!v) return;
      this.loading = true;
      this.isLive = false;
      const isHls = /\.m3u8(\?|#|$)/i.test(u);
      // http 媒体在 https 页面必须走代理
      const srcUrl = mustProxy(u) ? proxyUrlFor(u) : u;
      if (isHls && window.Hls && Hls.isSupported()) {
        const hls = new Hls({
          loader: makeProxyLoader(Hls),
          manifestLoadPolicy: { default: { maxLoadTimeMs: 30000, maxTimeToFirstByteMs: 30000 } },
          fragLoadPolicy: { default: { maxLoadTimeMs: 30000, maxTimeToFirstByteMs: 30000 } },
        });
        this.hls = hls;
        hls.on(Hls.Events.ERROR, (_, data) => {
          if (data.fatal) {
            if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
              this.error = '加载失败：视频源无法访问（可能需要 App 端嗅探）';
            } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
              hls.recoverMediaError();
              return;
            } else {
              this.error = '播放出错：' + (data.details || '未知错误');
            }
            this.loading = false;
            this.$emit('error', this.error);
          }
        });
        hls.on(Hls.Events.MANIFEST_PARSED, () => {
          this.loading = false;
          if (this.startPos > 0) v.currentTime = this.startPos;
          v.play().catch(() => {});
        });
        hls.loadSource(srcUrl);
        hls.attachMedia(v);
      } else {
        v.src = srcUrl;
        v.onloadedmetadata = () => {
          this.loading = false;
          this.isLive = !isFinite(v.duration);
          if (this.startPos > 0 && isFinite(v.duration)) v.currentTime = this.startPos;
          v.play().catch(() => {});
        };
        v.onerror = () => {
          this.loading = false;
          if (looksLikeMedia(u)) this.error = '加载失败：视频源无法访问或跨域受限';
        };
      }
    },
    destroyHls() {
      if (this.hls) { this.hls.destroy(); this.hls = null; }
      const v = this.$refs.video;
      if (v) { v.removeAttribute('src'); v.load(); }
    },
    toggle() {
      const v = this.$refs.video;
      if (!v) return;
      v.paused ? v.play().catch(() => {}) : v.pause();
    },
    seek(e) {
      const v = this.$refs.video;
      if (!v || !this.dur) return;
      const r = e.currentTarget.getBoundingClientRect();
      v.currentTime = this.dur * Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
    },
    cycleSpeed() {
      const i = this.speeds.indexOf(this.speed);
      this.speed = this.speeds[(i + 1) % this.speeds.length];
      if (this.$refs.video) this.$refs.video.playbackRate = this.speed;
    },
    toggleFull() {
      const el = this.$refs.box;
      if (!document.fullscreenElement) el.requestFullscreen?.();
      else document.exitFullscreen?.();
    },
    onTime() {
      const v = this.$refs.video;
      if (!v) return;
      this.time = v.currentTime;
      if (isFinite(v.duration) && v.duration > 0) this.dur = v.duration;
      this.$emit('timeupdate', { pos: v.currentTime, dur: v.duration });
    },
  },
  unmounted() { this.destroyHls(); },
  template: `
  <div class="player-box" ref="box" :class="{'show-ctrl': showCtrl}">
    <video ref="video" playsinline webkit-playsinline
      @click="toggle"
      @play="playing=true" @pause="playing=false"
      @timeupdate="onTime"
      @waiting="loading=true" @playing="loading=false"
      @ended="$emit('ended')"
      @volumechange="muted=$refs.video.muted"></video>
    <div class="p-msg" v-if="loading && !error"><div class="spin"></div><div>加载中…</div></div>
    <div class="p-msg" v-if="error"><div>{{ error }}</div></div>
    <div class="p-ctrl" v-show="!error">
      <button class="p-btn" @click.stop="toggle" v-html="playing ? $root.icons.pause : $root.icons.play"></button>
      <span class="p-time">{{ timeFmt }}</span>
      <div class="p-seek" @click.stop="seek" v-if="!isLive">
        <div class="rail"><div class="played" :style="{width: pct+'%'}"></div></div>
      </div>
      <span class="p-time" v-if="!isLive">{{ durFmt }}</span>
      <span class="p-time" v-else style="color:var(--accent)">直播</span>
      <button class="p-speed" @click.stop="cycleSpeed">{{ speed }}x</button>
      <button class="p-btn" @click.stop="$refs.video.muted=!$refs.video.muted" v-html="$root.icons.vol"></button>
      <button class="p-btn" @click.stop="toggleFull" v-html="$root.icons.full"></button>
    </div>
  </div>`,
};

// ---------------- 首页 ----------------
const HomeView = {
  components: { VCard },
  data: () => ({
    classes: [], items: [], cat: '', page: 1, pageCount: 1,
    loading: true, loadingMore: false, error: '',
  }),
  methods: {
    async boot() {
      this.loading = true; this.error = '';
      this.items = []; this.classes = []; this.cat = ''; this.page = 1;
      try {
        const [home, latest] = await Promise.allSettled([G.api.home(), G.api.latest(1)]);
        const lp = latest.status === 'fulfilled' ? latest.value : null;
        const hp = home.status === 'fulfilled' ? home.value : null;
        this.classes = (hp && hp.classes.length ? hp.classes : (lp ? lp.classes : [])) || [];
        if (!this.classes.length && lp) {
          const names = [...new Set(lp.items.map(i => i.typeName).filter(Boolean))];
          this.classes = names.map((n, i) => ({ id: String(i + 1), name: n }));
        }
        if (lp) { this.items = lp.items; this.page = lp.page; this.pageCount = lp.pageCount; }
        else if (hp) { this.items = hp.items; this.page = hp.page; this.pageCount = hp.pageCount; }
        if (!this.items.length) this.error = '该站点没有返回内容，换个站点试试';
      } catch (e) {
        this.error = '加载失败：' + (e.message || '网络错误');
      } finally { this.loading = false; }
    },
    async selectCat(id) {
      if (this.cat === id) return;
      this.cat = id; this.page = 1; this.loading = true; this.error = '';
      try {
        const p = id ? await G.api.category(id, 1) : await G.api.latest(1);
        this.items = p.items; this.page = p.page; this.pageCount = p.pageCount;
      } catch (e) { this.error = '加载失败：' + e.message; }
      finally { this.loading = false; }
    },
    async loadMore() {
      if (this.loadingMore || this.page >= this.pageCount) return;
      this.loadingMore = true;
      try {
        const p = this.cat ? await G.api.category(this.cat, this.page + 1) : await G.api.latest(this.page + 1);
        const seen = new Set(this.items.map(i => i.vodId));
        this.items = this.items.concat(p.items.filter(i => !seen.has(i.vodId)));
        this.page = p.page; this.pageCount = p.pageCount;
      } catch {} finally { this.loadingMore = false; }
    },
    setupSentinel() {
      if (this.ob) this.ob.disconnect();
      this.ob = new IntersectionObserver(es => {
        if (es[0].isIntersecting) this.loadMore();
      }, { rootMargin: '600px' });
      nextTick(() => {
        if (this.$refs.sentinel) this.ob.observe(this.$refs.sentinel);
      });
    },
  },
  watch: {
    'G.site': {
      immediate: true,
      handler(site) { if (site) this.boot(); },
    },
    loading(v) { if (!v) this.setupSentinel(); },
    error(v) { if (!v) this.setupSentinel(); },
  },
  mounted() { this.setupSentinel(); },
  unmounted() { this.ob && this.ob.disconnect(); },
  template: `
  <div class="page">
    <div class="cats" v-if="classes.length">
      <button class="cat" :class="{on: cat===''}" @click="selectCat('')">推荐</button>
      <button class="cat" v-for="c in classes" :key="c.id" :class="{on: cat===c.id}" @click="selectCat(c.id)">{{ c.name }}</button>
    </div>
    <div class="state" v-if="loading"><div class="spin"></div>正在加载 {{ G.site ? G.site.name : '' }}…</div>
    <div class="state" v-else-if="error">{{ error }}<br><button class="retry" @click="boot">重试</button></div>
    <template v-else>
      <div class="grid">
        <v-card v-for="it in items" :key="it.vodId + it.vodName" :item="it"></v-card>
      </div>
      <div class="loadmore" ref="sentinel">
        <span v-if="loadingMore">加载更多…</span>
        <span v-else-if="page >= pageCount && items.length">— 到底啦 —</span>
      </div>
    </template>
  </div>`,
};

// ---------------- 搜索 ----------------
const SearchView = {
  components: { VCard },
  data: () => ({ items: [], loading: true, error: '', page: 1, pageCount: 1, loadingMore: false }),
  computed: { q() { return G.route.params.q || ''; } },
  watch: {
    q: { immediate: true, handler() { this.doSearch(); } },
    'G.site': { handler() { this.doSearch(); } },
    loading(v) { if (!v) this.setupSentinel(); },
  },
  methods: {
    async doSearch() {
      if (!G.api) return;
      this.loading = true; this.error = ''; this.items = [];
      try {
        const p = await G.api.search(this.q, 1);
        this.items = p.items; this.page = p.page; this.pageCount = p.pageCount;
      } catch (e) { this.error = '搜索失败：' + e.message; }
      finally { this.loading = false; }
    },
    async loadMore() {
      if (this.loadingMore || this.page >= this.pageCount) return;
      this.loadingMore = true;
      try {
        const p = await G.api.search(this.q, this.page + 1);
        this.items = this.items.concat(p.items);
        this.page = p.page; this.pageCount = p.pageCount;
      } catch {} finally { this.loadingMore = false; }
    },
    setupSentinel() {
      if (this.ob) this.ob.disconnect();
      this.ob = new IntersectionObserver(es => { if (es[0].isIntersecting) this.loadMore(); }, { rootMargin: '600px' });
      nextTick(() => { if (this.$refs.sentinel) this.ob.observe(this.$refs.sentinel); });
    },
  },
  mounted() { this.setupSentinel(); },
  unmounted() { this.ob && this.ob.disconnect(); },
  template: `
  <div class="page" style="padding-top:22px">
    <h2 style="font-size:18px;margin-bottom:18px">「{{ q }}」的搜索结果</h2>
    <div class="state" v-if="loading"><div class="spin"></div>搜索中…</div>
    <div class="state" v-else-if="error">{{ error }}<br><button class="retry" @click="doSearch">重试</button></div>
    <div class="empty" v-else-if="!items.length" v-html="$root.icons.film + '<div>没有找到相关内容</div>'"></div>
    <template v-else>
      <div class="grid"><v-card v-for="it in items" :key="it.vodId + it.vodName" :item="it"></v-card></div>
      <div class="loadmore" ref="sentinel"><span v-if="loadingMore">加载更多…</span></div>
    </template>
  </div>`,
};

// ---------------- 详情 ----------------
const DetailView = {
  components: { Player, VCard },
  data: () => ({
    d: null, loading: true, error: '',
    lineIdx: 0, epIdx: -1, playUrl: '', startPos: 0,
    fav: false, epReversed: false, nonDirect: null,
  }),
  computed: {
    vodId() { return G.route.params.vodId; },
    lines() { return this.d ? this.d.sources : []; },
    eps() {
      const l = this.lines[this.lineIdx];
      if (!l) return [];
      return this.epReversed ? [...l.episodes].reverse() : l.episodes;
    },
    histRec() {
      return store.history.find(h => h.siteKey === (G.site ? G.site.key : '') && h.vodId === this.vodId);
    },
  },
  watch: {
    vodId: { immediate: true, handler() { this.load(); } },
    'G.site': { handler() { this.load(); } },
  },
  methods: {
    async load() {
      if (!G.api || !this.vodId) return;
      this.loading = true; this.error = ''; this.d = null; this.epIdx = -1; this.playUrl = '';
      try {
        const d = await G.api.detail(this.vodId);
        if (!d) throw new Error('未获取到详情');
        this.d = d;
        this.fav = isFav(G.site.key, d.vodId);
        // 续播 or 首集
        const h = this.histRec;
        const epFromRoute = G.route.params.ep;
        let li = 0, ei = 0, pos = 0;
        if (h && epFromRoute == null) { ei = h.episodeIndex || 0; pos = h.position || 0; }
        if (epFromRoute != null) ei = epFromRoute;
        this.playAt(li, ei, pos, true);
      } catch (e) {
        this.error = '加载失败：' + (e.message || '网络错误');
      } finally { this.loading = false; }
    },
    playAt(li, ei, pos = 0, first = false) {
      const line = this.lines[li];
      const ep = line && line.episodes[ei];
      if (!ep) { if (first) this.error = '没有可播放的剧集'; return; }
      this.lineIdx = li; this.epIdx = ei; this.startPos = pos;
      if (!looksLikeMedia(ep.url)) {
        this.playUrl = '';
        this.error = '';
        this.nonDirect = ep.url;
        toast('该地址为网页播放页，Web 端无法嗅探解析');
        return;
      }
      this.nonDirect = null;
      this.playUrl = ep.url;
      this.saveHist(pos);
    },
    saveHist(pos) {
      if (!this.d) return;
      saveHistory({
        siteKey: G.site.key, vodId: this.d.vodId, vodName: this.d.vodName, vodPic: this.d.vodPic,
        episodeIndex: this.epIdx, episodeName: (this.lines[this.lineIdx]?.episodes[this.epIdx] || {}).name || '',
        position: Math.floor(pos), duration: Math.floor(this.$refs.player?.dur || 0),
      });
    },
    onTime({ pos, dur }) {
      this._lastSave = this._lastSave || 0;
      if (Date.now() - this._lastSave > 5000) { this._lastSave = Date.now(); this.saveHist(pos); }
    },
    onEnded() {
      const line = this.lines[this.lineIdx];
      if (line && this.epIdx + 1 < line.episodes.length) this.playAt(this.lineIdx, this.epIdx + 1);
    },
    toggleFav() {
      if (!this.d) return;
      this.fav = toggleFav({ siteKey: G.site.key, vodId: this.d.vodId, vodName: this.d.vodName, vodPic: this.d.vodPic, vodRemarks: this.d.vodRemarks });
      toast(this.fav ? '已收藏' : '已取消收藏');
    },
    epClass(i) {
      const line = this.lines[this.lineIdx];
      const realIdx = this.epReversed ? line.episodes.length - 1 - i : i;
      const h = this.histRec;
      return { on: realIdx === this.epIdx, resume: realIdx !== this.epIdx && h && h.episodeIndex === realIdx };
    },
    epClick(i) {
      const line = this.lines[this.lineIdx];
      const realIdx = this.epReversed ? line.episodes.length - 1 - i : i;
      this.playAt(this.lineIdx, realIdx);
    },
  },
  template: `
  <div class="page">
    <div class="state" v-if="loading"><div class="spin"></div>获取详情…</div>
    <div class="state" v-else-if="error && !d">{{ error }}<br><button class="retry" @click="load">重试</button></div>
    <div class="detail-hero" v-else-if="d">
      <div class="detail-grid">
        <div>
          <player ref="player" :url="playUrl" :start-pos="startPos" :title="d.vodName"
                  @timeupdate="onTime" @ended="onEnded"></player>
          <div class="p-msg" v-if="nonDirect" style="position:static;background:var(--card);border-radius:12px;margin-top:14px;border:1px solid var(--line)">
            <div>该剧集是网页播放地址，Web 端无法嗅探解析。</div>
            <div style="font-size:12px">请使用聚影 App 播放（支持自动嗅探），或尝试切换其他线路。</div>
            <a class="btn btn-p" href="./about.html" style="margin-top:6px"><span v-html="$root.icons.dl"></span>下载聚影 App</a>
          </div>
          <div class="lines" v-if="lines.length > 1">
            <button class="line-tab" v-for="(l,i) in lines" :key="l.name+i" :class="{on: i===lineIdx}"
                    @click="playAt(i, 0)">{{ l.name }}</button>
          </div>
          <div style="display:flex;align-items:center;margin:6px 0 12px" v-if="eps.length">
            <span style="font-size:14px;font-weight:700">选集</span>
            <span style="font-size:12px;color:var(--text3);margin-left:10px">共 {{ lines[lineIdx] ? lines[lineIdx].episodes.length : 0 }} 集</span>
            <button class="cat" style="margin-left:auto" @click="epReversed=!epReversed">{{ epReversed ? '正序' : '倒序' }}</button>
          </div>
          <div class="eps">
            <button class="ep" v-for="(e,i) in eps" :key="e.name+i" :class="epClass(i)" @click="epClick(i)">{{ e.name }}</button>
          </div>
        </div>
        <div class="d-info">
          <h1>{{ d.vodName }}</h1>
          <div class="d-tags">
            <span v-for="t in [d.typeName, d.vodYear, d.vodArea, d.vodRemarks].filter(Boolean)" :key="t">{{ t }}</span>
          </div>
          <div class="d-actions">
            <button class="btn btn-g" :class="{on: fav}" @click="toggleFav">
              <span v-html="fav ? $root.icons.heartFill : $root.icons.heart"></span>{{ fav ? '已收藏' : '收藏' }}
            </button>
          </div>
          <div class="d-meta">
            <div v-if="d.vodDirector"><b>导演：</b>{{ d.vodDirector }}</div>
            <div v-if="d.vodActor"><b>主演：</b>{{ d.vodActor }}</div>
            <div v-if="d.vodScore"><b>评分：</b>{{ d.vodScore }}</div>
          </div>
          <div class="d-desc" v-if="d.vodContent">{{ d.vodContent }}</div>
        </div>
      </div>
    </div>
  </div>`,
};

// ---------------- 直播 ----------------
const LiveView = {
  components: { Player },
  data: () => ({
    srcIdx: 0, groups: [], groupIdx: 0, chIdx: -1, playUrl: '',
    loading: true, error: '',
  }),
  computed: {
    curGroup() { return this.groups[this.groupIdx] || { channels: [] }; },
  },
  watch: {
    'G.lives': {
      immediate: true,
      handler(lives) { if (lives && lives.length) this.loadSrc(0); },
    },
  },
  methods: {
    async loadSrc(i, autoNext = true) {
      if (!G.lives.length) { this.error = '当前接口没有直播源'; this.loading = false; return; }
      if (i >= G.lives.length) { this.error = '所有直播源均不可用'; this.loading = false; return; }
      this.srcIdx = i; this.loading = true; this.error = ''; this.groups = []; this.playUrl = ''; this.chIdx = -1;
      try {
        const text = await fetchText(G.lives[i].url);
        this.groups = parseLive(text);
        if (!this.groups.length) throw new Error('直播列表为空');
        this.groupIdx = 0;
        this.playCh(0, 0);
        this.loading = false;
      } catch (e) {
        // 空源/失败自动跳到下一个（与 App 端一致）
        if (autoNext && i + 1 < G.lives.length) {
          toast('「' + G.lives[i].name + '」不可用，自动切换下一个');
          return this.loadSrc(i + 1, autoNext);
        }
        this.error = '直播源加载失败：' + (e.message || '网络错误');
        this.loading = false;
      }
    },
    playCh(gi, ci) {
      this.groupIdx = gi; this.chIdx = ci;
      const ch = (this.groups[gi] || { channels: [] }).channels[ci];
      if (ch) this.playUrl = ch.url;
    },
  },
  template: `
  <div class="page">
    <div class="state" v-if="loading"><div class="spin"></div>加载直播源…</div>
    <div class="state" v-else-if="error">{{ error }}<br><button class="retry" @click="loadSrc(srcIdx)">重试</button></div>
    <div class="live-grid" v-else>
      <div class="live-side">
        <h4>直播源（{{ G.lives.length }}）</h4>
        <div class="live-list">
          <button class="live-item" v-for="(l,i) in G.lives" :key="l.url" :class="{on: i===srcIdx}" @click="loadSrc(i)">{{ l.name }}</button>
        </div>
      </div>
      <div>
        <player :url="playUrl" :start-pos="0" title="直播"></player>
        <div class="cats" style="padding-top:14px">
          <button class="cat" v-for="(g,i) in groups" :key="g.name+i" :class="{on: i===groupIdx}" @click="groupIdx=i">{{ g.name }}</button>
        </div>
        <div class="eps" style="max-height:340px">
          <button class="ep" v-for="(c,i) in curGroup.channels" :key="c.name+i"
                  :class="{on: i===chIdx}" @click="playCh(groupIdx, i)">{{ c.name }}</button>
        </div>
      </div>
      <div class="live-side" style="align-self:start">
        <h4>正在播放</h4>
        <div style="padding:14px 16px;font-size:13.5px">
          {{ chIdx >= 0 && curGroup.channels[chIdx] ? curGroup.channels[chIdx].name : '—' }}
          <div style="color:var(--text3);font-size:12px;margin-top:6px">{{ curGroup.name || '' }}</div>
        </div>
      </div>
    </div>
  </div>`,
};

// ---------------- 我的 ----------------
const MineView = {
  components: { VCard },
  data: () => ({ hist: [], favs: [] }),
  computed: {
    histItems() {
      return this.hist.map(h => ({
        item: { vodId: h.vodId, vodName: h.vodName, vodPic: h.vodPic, vodRemarks: h.episodeName, typeName: '', vodYear: '' },
        progress: h.duration > 0 ? Math.min(100, h.position / h.duration * 100) : 0,
      }));
    },
  },
  methods: {
    refresh() { this.hist = [...store.history]; this.favs = [...store.favs]; },
    clearHist() { store.history = []; this.refresh(); toast('观看历史已清空'); },
    clearFavs() { store.favs = []; this.refresh(); toast('收藏已清空'); },
  },
  mounted() { this.refresh(); },
  template: `
  <div class="page">
    <div class="mine-sec">
      <h3><span v-html="$root.icons.clock"></span>观看历史
        <button class="clear" v-if="hist.length" @click="clearHist">清空</button>
        <span style="font-size:12px;color:var(--text3);font-weight:400">{{ hist.length }} 条</span>
      </h3>
      <div class="empty" v-if="!histItems.length" v-html="$root.icons.film + '<div>还没有观看记录</div>'"></div>
      <div class="grid" v-else>
        <v-card v-for="h in histItems" :key="h.item.vodId" :item="h.item" :progress="h.progress"></v-card>
      </div>
    </div>
    <div class="mine-sec">
      <h3><span v-html="$root.icons.heart"></span>我的收藏
        <button class="clear" v-if="favs.length" @click="clearFavs">清空</button>
        <span style="font-size:12px;color:var(--text3);font-weight:400">{{ favs.length }} 部</span>
      </h3>
      <div class="empty" v-if="!favs.length" v-html="$root.icons.heart + '<div>收藏的影片会出现在这里</div>'"></div>
      <div class="grid" v-else>
        <v-card v-for="f in favs" :key="f.vodId"
                :item="{vodId: f.vodId, vodName: f.vodName, vodPic: f.vodPic, vodRemarks: f.vodRemarks, typeName:'', vodYear:''}"></v-card>
      </div>
    </div>
  </div>`,
};

// ---------------- 设置弹层 ----------------
const SettingsModal = {
  data: () => ({
    newName: '', newUrl: '', proxyInput: store.proxy,
    sites: computed(() => G.sites),
  }),
  computed: {
    curSiteKey: {
      get() { return G.site ? G.site.key : ''; },
      set(k) { const s = G.sites.find(x => x.key === k); if (s) selectSite(s); },
    },
  },
  methods: {
    addSource() {
      const name = this.newName.trim() || '自定义接口';
      const url = this.newUrl.trim();
      if (!/^https?:\/\//.test(url)) { toast('请输入合法的 http(s) 地址'); return; }
      if (G.sources.some(s => s.url === url)) { toast('该接口已存在'); return; }
      G.sources = [...G.sources, { name, url }];
      store.sources = G.sources;
      this.newName = ''; this.newUrl = '';
      toast('已添加');
    },
    removeSource(url) {
      G.sources = G.sources.filter(s => s.url !== url);
      store.sources = G.sources;
    },
    async rescan() {
      store.goodProxy = '';
      await bootAggregate();
      if (!G.configError) { G.settingsOpen = false; toast(G.aggInfo); }
    },
    saveProxy() {
      store.proxy = this.proxyInput.trim();
      store.goodProxy = '';
      toast(this.proxyInput.trim() ? '自定义代理已保存' : '已恢复自动代理');
    },
  },
  template: `
  <div class="mask" @click.self="G.settingsOpen=false">
    <div class="modal">
      <h3>设置与数据源</h3>
      <div class="set-card">
        <h4>当前站点</h4>
        <div class="hint">来自当前接口的可用站点（type 0/1）</div>
        <select class="site-sel" v-model="curSiteKey">
          <option v-for="s in sites" :key="s.key" :value="s.key">{{ s.name }}{{ s.sourceName ? ' · ' + s.sourceName : '' }}</option>
        </select>
      </div>
      <div class="set-card">
        <h4>数据源接口</h4>
        <div class="hint">TVBox 标准接口（JSON）。所有接口自动扫描聚合并合并站点，无需手动切换。</div>
        <div class="src-row" v-for="s in G.sources" :key="s.url" :class="{cur: G.health[s.url] && G.health[s.url].ok}">
          <span class="nm">{{ s.name }}</span>
          <span class="ur">{{ s.url }}</span>
          <span class="hb" :class="{ok: G.health[s.url] && G.health[s.url].ok, bad: G.health[s.url] && !G.health[s.url].ok && !G.health[s.url].checking}">
            {{ G.health[s.url] ? (G.health[s.url].checking ? '检测中…' : (G.health[s.url].ok ? G.health[s.url].siteCount + ' 站点' : G.health[s.url].message)) : '未检测' }}
          </span>
          <button class="del" @click="removeSource(s.url)">删除</button>
        </div>
        <div style="display:flex;justify-content:space-between;align-items:center;margin-top:4px">
          <span style="font-size:12px;color:var(--text3)">{{ G.aggInfo }}</span>
          <button class="btn btn-g" style="padding:7px 16px;font-size:12.5px" @click="rescan" :disabled="G.scanning">{{ G.scanning ? '扫描中…' : '重新扫描' }}</button>
        </div>
        <div class="add-row">
          <input v-model="newName" placeholder="名称（可选）" style="max-width:120px">
          <input v-model="newUrl" placeholder="https://… 接口地址" @keyup.enter="addSource">
          <button class="btn btn-p" @click="addSource">添加</button>
        </div>
      </div>
      <div class="set-card">
        <h4>CORS 代理</h4>
        <div class="hint">浏览器跨域限制，API 请求需经代理转发。默认自动探测公共代理；不稳定时可填自建代理前缀（{url} 为占位符，可选）。</div>
        <div class="add-row" style="margin-top:0">
          <input v-model="proxyInput" placeholder="例如 https://my-proxy.workers.dev/?{url}">
          <button class="btn btn-g" @click="saveProxy">保存</button>
        </div>
      </div>
      <div class="set-card" style="text-align:center">
        <a class="btn btn-g" href="./about.html"><span v-html="$root.icons.phone"></span>聚影 App 介绍与下载</a>
      </div>
    </div>
  </div>`,
};

// ---------------- 启动 ----------------
/** 扫描所有已保存接口并聚合健康源（与 App 端 scanAndAggregate 一致） */
async function bootAggregate() {
  if (G.scanning) return;
  G.scanning = true;
  G.configLoading = true; G.configError = '';
  // 健康状态占位
  const health = {};
  G.sources.forEach(s => health[s.url] = { checking: true, ok: false, siteCount: 0, message: '检测中…' });
  G.health = health;
  try {
    // 并发池=3：避免首个请求未探出可用代理时多路竞速导致请求爆炸
    const results = await pool(G.sources, 3, async (s) => {
      const r = await checkSource(s.url, s.name);
      G.health = Object.assign({}, G.health, {
        [s.url]: { checking: false, ok: r.ok, siteCount: r.siteCount, message: r.message },
      });
      return r;
    });
    const healthy = results.filter(r => r.ok);
    if (!healthy.length) {
      G.configError = '没有可用的接口，请检查网络或在设置中更换接口';
      toast(G.configError);
      G.settingsOpen = true;
      return;
    }
    const merged = aggregateConfigs(results);
    G.sites = merged.sites;
    G.lives = merged.lives;
    G.siteSourceName = merged.siteSourceName;
    G.aggInfo = '聚合 ' + healthy.length + ' 个接口 · ' + merged.sites.length + ' 站点';
    const keep = merged.sites.find(s => s.key === store.siteKey) || merged.sites[0];
    selectSite(keep);
  } finally {
    G.scanning = false;
    G.configLoading = false;
  }
}

function selectSite(site) {
  G.site = site;
  G.api = new MacCms(site);
  store.siteKey = site.key;
}

// ---------------- 根组件 ----------------
const App = {
  components: { HomeView, SearchView, DetailView, LiveView, MineView, SettingsModal },
  data: () => ({ G, icons: I, searchText: '' }),
  computed: {
    view() { return G.route.name; },
  },
  methods: {
    doSearch() {
      const q = this.searchText.trim();
      if (q) nav('#/search?q=' + encodeURIComponent(q));
    },
  },
  created() {
    // 初始化接口列表
    G.sources = store.sources.length ? store.sources : [...RECOMMENDED];
    if (!store.sources.length) store.sources = G.sources;
    G.route = parseHash();
    bootAggregate();
  },
  template: `
  <div>
    <div class="topbar">
      <div class="topbar-in">
        <a class="logo" href="#/"><span class="logo-mark" v-html="icons.playLogo"></span>聚影</a>
        <div class="tabs">
          <a href="#/" :class="{on: view==='home'}">首页</a>
          <a href="#/live" :class="{on: view==='live'}">直播</a>
          <a href="#/mine" :class="{on: view==='mine'}">我的</a>
        </div>
        <div class="search-box">
          <span v-html="icons.search"></span>
          <input v-model="searchText" placeholder="输入片名搜索…" @keyup.enter="doSearch">
        </div>
        <div class="top-actions">
          <a class="app-link" href="./about.html" target="_blank" rel="noopener"><span v-html="icons.phone"></span><span>App 下载</span></a>
          <button class="icon-btn" @click="G.settingsOpen=true" v-html="icons.gear"></button>
        </div>
      </div>
    </div>

    <div class="state" v-if="G.configLoading"><div class="spin"></div>正在加载数据源…</div>
    <template v-else>
      <home-view v-if="view==='home'"></home-view>
      <search-view v-else-if="view==='search'"></search-view>
      <detail-view v-else-if="view==='detail'" :key="G.route.params.vodId"></detail-view>
      <live-view v-else-if="view==='live'"></live-view>
      <mine-view v-else-if="view==='mine'"></mine-view>
    </template>

    <settings-modal v-if="G.settingsOpen"></settings-modal>
    <div class="toast" v-if="G.toast">{{ G.toast }}</div>
  </div>`,
};

// 供模板访问全局
const app = createApp(App);
app.config.globalProperties.G = G;
app.mount('#app');
