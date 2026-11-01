package com.example.vlcroot

/**
 * 判断 getUri() 当前是"入队阶段"还是"交给 libVLC 之前"。
 *
 * - 从 StartActivity / MediaUtils 进来（还没有 MediaWrapper）：保持原 URI，
 *   让历史/播放列表记录真实路径（content:// 或 file://），而不是临时 HTTP token。
 * - 从 PlaylistManager.playIndex 进来：换成 HTTP。
 *
 * 认不出时按"播放阶段"处理，保证能播；日志里可以看到 unknown caller。
 */
object CallSite {

    fun fromEntryPoint(): Boolean {
        var hasPlaylist = false
        var hasEntry = false
        for (frame in Throwable().stackTrace) {
            val name = frame.className
            // 用 contains 而不是 endsWith：Kotlin 协程/lambda 的类名常是
            // StartActivity$startPlaybackFromApp$1 这种形式
            if (name.contains("PlaylistManager")) hasPlaylist = true
            if (name.contains("StartActivity") || name.contains("MediaUtils")) hasEntry = true
        }
        return hasEntry && !hasPlaylist
    }

    fun describe(): String {
        val frames = Throwable().stackTrace
        return frames.take(8).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }
    }
}
