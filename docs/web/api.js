/**
 * 聚影 Web — 数据层
 * 纯客户端：TVBox 配置加载（CORS 代理链）+ 苹果CMS API + 直播解析 + 本地存储
 */

// ---------------- 本地存储 ----------------
const LS = {
  get(k, d) { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};

export const store = {
  get sources() { return LS.get('jy_sources', []); },
  set sources(v) { LS.set('jy_sources', v); },
  get currentSource() { return LS.get('jy_current_source', ''); },
  set currentSource(v) { LS.set('jy_current_source', v); },
  get siteKey() { return LS.get('jy_site_key', ''); },
  set siteKey(v) { LS.set('jy_site_key', v); },
  get history() { return LS.get('jy_history', []); },
  set history(v) { LS.set('jy_history', v); },
  get favs() { return LS.get('jy_favs', []); },
  set favs(v) { LS.set('jy_favs', v); },
  get proxy() { return LS.get('jy_proxy', ''); },       // 用户自定义代理前缀
  get goodProxy() { return LS.get('jy_good_proxy', ''); }, // 自动探测到的可用代理索引
  set goodProxy(v) { LS.set('jy_good_proxy', v); },
  get configCache() { return LS.get('jy_config_cache', null); }, // 上次聚合结果缓存（秒开）
  set configCache(v) { LS.set('jy_config_cache', v); },
};

/** 内置推荐接口（与 App 端一致） */
export const RECOMMENDED = [
  { name: '业余采集', url: 'https://szyyds.cn/tv/x.json' },
  { name: '欧歌', url: 'http://tv.nxog.top/m/' },
  { name: '俊于', url: 'http://home.jundie.top:81/top98.json' },
  { name: '饭太硬', url: 'http://fty.888484.xyz/tv' },
  { name: '王小二', url: 'http://tvbox.王二小放牛娃.top' },
  { name: 'noimank', url: 'https://gitlab.com/noimank/tvbox/-/raw/main/tvbox1.json' },
  { name: 'OK 接口', url: 'http://ok321.top/ok' },
  { name: '荷城茶秀', url: 'http://rihou.cc:88/荷城茶秀' },
];

// ---------------- CORS 代理链 ----------------
const PROXIES = [
  // r.jina.ai：国内可达且快（~2s），但只返回文本（带 Markdown 前缀需剥离）
  {
    name: 'jina', binary: false,
    build: u => 'https://r.jina.ai/' + u,
    unwrap: t => {
      const i = t.indexOf('Markdown Content:');
      return i >= 0 ? t.slice(i + 17).replace(/^\s+/, '') : t;
    },
  },
  { name: 'allorigins', binary: true, build: u => 'https://api.allorigins.win/raw?url=' + encodeURIComponent(u) },
  { name: 'corsfix', binary: true, build: u => 'https://proxy.corsfix.com/?' + u },
  { name: 'codetabs', binary: true, build: u => 'https://api.codetabs.com/v1/proxy?quest=' + u },
  { name: 'cors-eu', binary: true, build: u => 'https://cors.eu.org/' + u },
];

function proxyWrap(proxyPrefix, url) {
  return proxyPrefix.includes('{url}')
    ? proxyPrefix.replace('{url}', encodeURIComponent(url))
    : proxyPrefix + encodeURIComponent(url);
}

/** 用已知最优代理（探测结果 > 用户自定义 > 第一个内置）构造代理 URL */
export function proxyUrlFor(url, proxyIdx = -1) {
  if (proxyIdx >= 0 && PROXIES[proxyIdx]) return PROXIES[proxyIdx].build(url);
  const good = store.goodProxy;
  if (good !== '' && PROXIES[good]) return PROXIES[good].build(url);
  if (store.proxy) return proxyWrap(store.proxy, url);
  return PROXIES[0].build(url);
}

/** https 页面请求 http 资源必被混合内容拦截 */
export function mustProxy(url) {
  return location.protocol === 'https:' && url.startsWith('http:');
}

async function fetchOnce(u, timeoutMs = 15000, responseType = 'text') {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const r = await fetch(u, { signal: ctrl.signal });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return responseType === 'arraybuffer' ? await r.arrayBuffer() : await r.text();
  } finally { clearTimeout(t); }
}

function validate(data, responseType) {
  if (responseType !== 'text') return true;
  const t = (data || '').trim();
  if (!t) return false;
  // 代理自身的错误页不算成功
  if (/^error code: ?\d+/i.test(t)) return false;
  if (t.startsWith('Oops')) return false;
  if (t.startsWith('{') && t.includes('corsfix_error')) return false;
  return true;
}

async function tryVia(url, via, responseType, timeoutMs) {
  const u = via === -1 ? url : (via === -2 ? proxyWrap(store.proxy, url) : PROXIES[via].build(url));
  let data = await fetchOnce(u, timeoutMs, responseType);
  if (!validate(data, responseType)) throw new Error('bad content');
  if (via >= 0 && PROXIES[via].unwrap && responseType === 'text') data = PROXIES[via].unwrap(data);
  return { data, via };
}

/**
 * 抓取（文本/二进制）：
 * 1. 有缓存的可用代理：直连 + 该代理快速双发
 * 2. 否则：直连 + 全部代理 + 自定义代理 竞速，第一个成功的生效并缓存
 */
async function fetchSmart(url, { responseType = 'text', timeoutMs = 30000, direct = true } = {}) {
  const needBin = responseType === 'arraybuffer';
  const cap = i => PROXIES[i] && (!needBin || PROXIES[i].binary);
  const racers = [];
  if (direct && !mustProxy(url)) racers.push(-1);
  if (store.goodProxy !== '' && cap(store.goodProxy)) {
    racers.push(store.goodProxy);
  } else {
    for (let i = 0; i < PROXIES.length; i++) if (cap(i)) racers.push(i);
    if (store.proxy) racers.push(-2);
  }
  if (!racers.length) throw new Error('无可用请求通道');
  try {
    const res = await Promise.any(racers.map(v => tryVia(url, v, responseType, timeoutMs)));
    if (res.via >= 0 && store.goodProxy !== res.via) store.goodProxy = res.via;
    return res;
  } catch (e) {
    // 全部失败：清掉缓存代理，下次重新竞速
    store.goodProxy = '';
    const errs = (e && e.errors) ? e.errors.map(x => x.message).join('; ') : String(e);
    throw new Error('网络请求失败（可尝试在设置中配置自建代理）');
  }
}

/** 抓取文本（自动代理链） */
export async function fetchText(url) {
  const { data } = await fetchSmart(url, { responseType: 'text' });
  return data;
}

/** 供 HLS loader 使用：按 responseType 抓取（直连优先，代理兜底） */
export async function fetchMedia(url, responseType, timeoutMs) {
  return fetchSmart(url, { responseType, timeoutMs });
}

// ---------------- TVBox 配置 ----------------
export function decodeConfig(raw) {
  let t = raw.trim().replace(/^﻿/, '');
  if (t.startsWith('{') || t.startsWith('[')) return t;
  // 整体 Base64
  try {
    const dec = decodeURIComponent(escape(atob(t.replace(/\s+/g, ''))));
    if (dec.trim().startsWith('{')) return dec;
  } catch {}
  return t;
}

function absUrl(base, rel) {
  try { return new URL(rel, base).href; } catch { return rel; }
}

/** 拉取并解析单个接口配置 */
export async function fetchConfig(url) {
  const raw = await fetchText(url);
  const json = decodeConfig(raw);
  const cfg = JSON.parse(json);
  const sites = (cfg.sites || [])
    .filter(s => (s.type === 0 || s.type === 1) && s.api)
    .map(s => ({
      key: s.key || s.name || s.api,
      name: s.name || s.key || '站点',
      type: s.type,
      api: absUrl(url, s.api),
    }));
  const lives = (cfg.lives || []).filter(l => l.url).map(l => ({
    name: l.name || '直播源',
    url: absUrl(url, l.url),
    epg: l.epg || '',
  }));
  return { sites, lives };
}

export async function loadConfig(url) {
  const { sites, lives } = await fetchConfig(url);
  if (!sites.length) throw new Error('配置中没有可用站点（type 0/1）');
  return { sites, lives };
}

/** Java String.hashCode 等价实现（与 App 端命名空间规则一致） */
export function javaHash(str) {
  let h = 0;
  for (let i = 0; i < str.length; i++) {
    h = ((h * 31 + str.charCodeAt(i)) | 0);
  }
  return h;
}

/** 健康检查单个接口（与 App 端 checkSource 对应） */
export async function checkSource(url, name) {
  try {
    const { sites, lives } = await fetchConfig(url);
    if (!sites.length) {
      const msg = '配置中没有站点或全部为爬虫源';
      return { url, name, ok: false, siteCount: 0, message: msg, sites: [], lives: [] };
    }
    return { url, name, ok: true, siteCount: sites.length, message: '正常', sites, lives };
  } catch (e) {
    return { url, name, ok: false, siteCount: 0, message: e.message || '连接失败', sites: [], lives: [] };
  }
}

/**
 * 聚合多个健康配置（与 App 端 mergeConfigs 对应）：
 * 站点 key 加来源命名空间，直播按 url 去重
 */
export function aggregateConfigs(results) {
  const healthy = results.filter(r => r.ok);
  const siteSourceName = {};
  const sites = healthy.flatMap(r => r.sites.map(s => {
    const ns = javaHash(r.url) + '::' + s.key;
    siteSourceName[ns] = r.name;
    return Object.assign({}, s, { key: ns, sourceName: r.name });
  }));
  const seen = new Set();
  const lives = healthy.flatMap(r => r.lives).filter(l => {
    if (seen.has(l.url)) return false;
    seen.add(l.url);
    return true;
  });
  return { sites, lives, siteSourceName };
}

/** 并发池（限制同时进行的任务数） */
export async function pool(items, limit, fn) {
  const ret = new Array(items.length);
  let idx = 0;
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (idx < items.length) {
      const i = idx++;
      ret[i] = await fn(items[i], i);
    }
  }));
  return ret;
}

// ---------------- 苹果CMS API ----------------
function clean(s) {
  if (!s) return '';
  const d = document.createElement('div');
  d.innerHTML = s;
  return (d.textContent || '').replace(/ /g, ' ').trim();
}

export function parsePlaySources(playFrom, playUrl) {
  if (!playUrl) return [];
  const sep = playFrom.includes('$$$') ? '$$$' : ',';
  const names = playFrom.split(sep).map(x => x.trim());
  return playUrl.split('$$$').map((group, i) => {
    const episodes = group.split('#').map((t, j) => {
      t = t.trim();
      if (!t) return null;
      const idx = t.indexOf('$');
      return idx > 0
        ? { name: t.slice(0, idx).trim(), url: t.slice(idx + 1).trim() }
        : { name: '第' + (j + 1) + '集', url: t };
    }).filter(Boolean);
    return episodes.length ? { name: names[i] || ('线路' + (i + 1)), episodes } : null;
  }).filter(Boolean);
}

function jsonToItem(o) {
  return {
    vodId: String(o.vod_id ?? ''), vodName: clean(String(o.vod_name ?? '')),
    vodPic: String(o.vod_pic ?? ''), vodRemarks: clean(String(o.vod_remarks ?? '')),
    vodYear: String(o.vod_year ?? ''), typeName: String(o.type_name ?? ''),
  };
}
function jsonToDetail(o) {
  return Object.assign(jsonToItem(o), {
    vodArea: String(o.vod_area ?? ''), vodLang: String(o.vod_lang ?? ''),
    vodActor: clean(String(o.vod_actor ?? '')), vodDirector: clean(String(o.vod_director ?? '')),
    vodContent: clean(String(o.vod_content ?? '')), vodScore: String(o.vod_score ?? ''),
    sources: parsePlaySources(String(o.vod_play_from ?? ''), String(o.vod_play_url ?? '')),
  });
}

function parseJson(text) {
  const root = JSON.parse(text);
  const classes = (root.class || []).map(c => ({ id: String(c.type_id), name: String(c.type_name) }));
  const list = (root.list || []).filter(o => o.vod_id != null);
  return {
    page: parseInt(root.page) || 1, pageCount: parseInt(root.pagecount) || 1,
    total: parseInt(root.total) || list.length, classes,
    items: list.map(jsonToItem), details: list.map(jsonToDetail),
  };
}

function parseXml(text) {
  const doc = new DOMParser().parseFromString(text, 'text/xml');
  const listEl = doc.querySelector('list');
  const page = parseInt(listEl?.getAttribute('page')) || 1;
  const pageCount = parseInt(listEl?.getAttribute('pagecount')) || 1;
  const total = parseInt(listEl?.getAttribute('recordcount')) || 0;
  const classes = [...doc.querySelectorAll('ty')].map(ty => ({
    id: ty.getAttribute('id'), name: (ty.textContent || '').trim(),
  })).filter(c => c.id);
  const details = [...doc.querySelectorAll('video')].map(v => {
    const get = tag => (v.querySelector(tag)?.textContent || '').trim();
    const dds = [...v.querySelectorAll('dd')];
    const flags = dds.map(d => d.getAttribute('flag')).filter(Boolean);
    const playFrom = flags.join('$$$');
    const playUrl = dds.map(d => d.textContent || '').join('$$$');
    return {
      vodId: get('id'), vodName: clean(get('name')), vodPic: get('pic'),
      vodRemarks: clean(get('note')), vodYear: get('year'), typeName: get('type'),
      vodArea: get('area'), vodLang: get('lang'), vodActor: clean(get('actor')),
      vodDirector: clean(get('director')), vodContent: clean(get('des')), vodScore: get('score'),
      sources: parsePlaySources(playFrom, playUrl),
    };
  }).filter(d => d.vodId);
  return {
    page, pageCount, total, classes,
    items: details.map(({ sources, ...it }) => it), details,
  };
}

export class MacCms {
  constructor(site) { this.site = site; }
  url(ac, params = {}) {
    const u = new URL(this.site.api);
    u.searchParams.delete('ac');
    u.searchParams.set('ac', ac);
    for (const [k, v] of Object.entries(params)) u.searchParams.set(k, v);
    return u.href;
  }
  async fetch(u) {
    const raw = await fetchText(u);
    const text = raw.trim().replace(/^﻿/, '');
    return text.startsWith('{') ? parseJson(text) : parseXml(text);
  }
  home() { return this.fetch(this.url('list')); }
  latest(pg) { return this.fetch(this.url('videolist', { pg })); }
  category(t, pg) { return this.fetch(this.url('videolist', { t, pg })); }
  search(wd, pg = 1) { return this.fetch(this.url('videolist', { wd, pg })); }
  async detail(ids) {
    const r = await this.fetch(this.url('detail', { ids }));
    return r.details[0] || null;
  }
}

// ---------------- 直播列表解析 ----------------
export function parseLive(text) {
  const t = text.trim().replace(/^﻿/, '');
  const groups = [];
  const addCh = (g, name, url) => {
    if (!name || !url) return;
    let gr = groups.find(x => x.name === g);
    if (!gr) { gr = { name: g, channels: [] }; groups.push(gr); }
    gr.channels.push({ name, url });
  };
  if (t.startsWith('#EXTM3U')) {
    const re = /#EXTINF:-1([^,]*),([^\n]+)\n([^\n]+)/g;
    let m;
    while ((m = re.exec(t))) {
      const attrs = m[1];
      const g = (attrs.match(/group-title="([^"]*)"/) || [])[1] || '默认';
      addCh(g, m[2].trim(), m[3].trim());
    }
  } else {
    let cur = '默认';
    for (const line of t.split('\n')) {
      const l = line.trim();
      if (!l) continue;
      if (l.endsWith('#genre#')) { cur = l.replace(/,#genre#$/, '').replace(/#genre#$/, '').trim(); continue; }
      const i = l.indexOf(',');
      if (i > 0) addCh(cur, l.slice(0, i).trim(), l.slice(i + 1).trim());
    }
  }
  return groups;
}

// ---------------- 历史 / 收藏 ----------------
export function saveHistory(rec) {
  const list = store.history.filter(h => !(h.siteKey === rec.siteKey && h.vodId === rec.vodId));
  list.unshift(Object.assign({ time: Date.now() }, rec));
  store.history = list.slice(0, 100);
}
export function toggleFav(rec) {
  const i = store.favs.findIndex(f => f.siteKey === rec.siteKey && f.vodId === rec.vodId);
  if (i >= 0) store.favs = store.favs.filter((_, j) => j !== i);
  else store.favs = [Object.assign({ time: Date.now() }, rec), ...store.favs].slice(0, 100);
  return i < 0;
}
export function isFav(siteKey, vodId) {
  return store.favs.some(f => f.siteKey === siteKey && f.vodId === vodId);
}

/**
 * 海报图地址处理：
 * - https 页面下 http 图片必被混合内容拦截（手机端无此限制，这是 Web「暂无封面」的主因）
 * - 部分图床有防盗链。统一走 weserv 免费图片代理（支持任意 http/https、自带缓存）
 */
export function picUrl(u) {
  if (!u) return '';
  if (location.protocol === 'https:' && u.startsWith('http:')) {
    return 'https://images.weserv.nl/?url=' + encodeURIComponent(u.replace(/^https?:\/\//, ''));
  }
  return u;
}

/** 直连失败后的兜底代理地址 */
export function picFallback(u) {
  if (!u || u.includes('images.weserv.nl')) return '';
  return 'https://images.weserv.nl/?url=' + encodeURIComponent(u.replace(/^https?:\/\//, ''));
}

/** 是否直连可播放媒体 */
export function looksLikeMedia(u) {
  const x = (u || '').toLowerCase();
  return /\.(m3u8|mp4|flv|ts|m4s|mkv|mov)(\?|#|$)/.test(x) || x.startsWith('rtmp') || x.startsWith('rtsp');
}
