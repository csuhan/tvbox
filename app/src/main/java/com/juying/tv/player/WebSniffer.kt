package com.juying.tv.player

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.juying.tv.data.remote.Http

/**
 * 网页嗅探器：在后台 WebView 中加载播放页，拦截其中的真实视频地址（m3u8/mp4 等）。
 * 与 TVBox 的嗅探逻辑一致。
 */
class WebSniffer(private val context: Context) {

    interface Callback {
        fun onFound(url: String, headers: Map<String, String>)
        fun onFailed()
    }

    companion object {
        private val MEDIA_REGEX = Regex(
            "\\.(m3u8|mp4|flv|ts|m4s|mkv|mov)(\\?|#|$)", RegexOption.IGNORE_CASE
        )
        private val AD_HOSTS = listOf(
            "doubleclick", "googlesyndication", "adservice", "umeng", "baidustatic",
            "cnzz", "hm.baidu", "51.la", "sentry",
        )

        fun looksLikeMedia(url: String): Boolean {
            val u = url.lowercase()
            return MEDIA_REGEX.containsMatchIn(u) || u.startsWith("rtmp") || u.startsWith("rtsp")
        }
    }

    private var webView: WebView? = null
    private var done = false
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun sniff(pageUrl: String, callback: Callback, timeoutMs: Long = 20000) {
        release()
        done = false
        val wv = WebView(context)
        webView = wv
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            blockNetworkImage = true
            userAgentString = Http.UA
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url?.toString() ?: return false
                if (looksLikeMedia(u)) {
                    found(u, pageUrl, callback)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 自动点击播放：多数播放页需要触发 video.play()
                view?.evaluateJavascript(
                    """
                    (function(){
                        function tryPlay(){
                            var v=document.querySelector('video');
                            if(v){v.muted=true;try{v.play();}catch(e){}}
                            var btns=document.querySelectorAll('.play,.player-play,[class*=play],#play,.dplayer-play-icon,.xgplayer-play');
                            for(var i=0;i<btns.length;i++){try{btns[i].click();}catch(e){}}
                        }
                        tryPlay();setTimeout(tryPlay,1000);setTimeout(tryPlay,2500);
                    })();
                    """.trimIndent(), null
                )
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                val u = request?.url?.toString() ?: return null
                if (!done && looksLikeMedia(u)) {
                    found(u, pageUrl, callback)
                }
                if (AD_HOSTS.any { u.contains(it) }) {
                    return android.webkit.WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        handler.postDelayed({
            if (!done) {
                done = true
                release()
                callback.onFailed()
            }
        }, timeoutMs)
        wv.loadUrl(pageUrl)
    }

    private fun found(mediaUrl: String, pageUrl: String, callback: Callback) {
        if (done) return
        done = true
        val headers = mutableMapOf("User-Agent" to Http.UA)
        try {
            val origin = android.net.Uri.parse(pageUrl)
            headers["Referer"] = "${origin.scheme}://${origin.host}/"
        } catch (_: Exception) { }
        // shouldInterceptRequest 在后台线程回调，WebView 方法必须切回主线程
        handler.post {
            release()
            callback.onFound(mediaUrl, headers)
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        webView?.let {
            it.stopLoading()
            it.webViewClient = WebViewClient()
            it.destroy()
        }
        webView = null
    }
}
