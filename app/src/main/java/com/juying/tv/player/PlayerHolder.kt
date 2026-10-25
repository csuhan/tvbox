package com.juying.tv.player

import android.content.Context
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.juying.tv.data.remote.Http

/**
 * 全局共享播放器：小窗(详情页)与大窗(PlayerActivity)附着同一实例，
 * 切换时无缝续播，不重新加载。
 */
object PlayerHolder {
    var player: ExoPlayer? = null
        private set
    var factory: OkHttpDataSource.Factory? = null
        private set
    var vodKey: String? = null
        private set

    /** 当前已 prepare 的内容标识（vodKey#episodeIndex），避免重复 prepare */
    var currentTag: String? = null

    /** 小窗 → 大窗交接标记：交接中小窗不释放播放器 */
    var handoff: Boolean = false

    /** 大窗(共享模式)前台活动中：小窗的监听不响应 播完/出错，避免与大窗抢控制 */
    var fullscreenActive: Boolean = false

    fun keyOf(siteKey: String, vodId: String) = "$siteKey::$vodId"
    fun tagOf(vodKey: String, episodeIndex: Int) = "$vodKey#$episodeIndex"

    fun obtain(context: Context, vodKey: String): ExoPlayer {
        player?.let { if (this.vodKey == vodKey) return it }
        release()
        val f = OkHttpDataSource.Factory(Http.mediaClient).setUserAgent(Http.UA)
        factory = f
        val p = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context.applicationContext).setDataSourceFactory(f)
            )
            .build()
        player = p
        this.vodKey = vodKey
        return p
    }

    fun release() {
        player?.release()
        player = null
        factory = null
        vodKey = null
        currentTag = null
        handoff = false
        fullscreenActive = false
    }
}
