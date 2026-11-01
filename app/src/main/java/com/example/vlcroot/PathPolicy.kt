package com.example.vlcroot

import java.io.File

object PathPolicy {

    /** 只处理这些前缀，避免模块变成任意文件读取器 */
    private val ROOT_PREFIXES = listOf(
        "/data/",
        "/mnt/media_rw/",
        "/mnt/expand/"
    )

    // MVP 先把 m3u/m3u8 处理好；pls/xspf 的条目格式不同，后续单独实现
    private val PLAYLIST_EXTENSIONS = setOf("m3u", "m3u8")

    fun looksLikeRootFile(path: String): Boolean =
        ROOT_PREFIXES.any { path.startsWith(it) }

    fun needsRoot(path: String): Boolean {
        // FUSE 挂载点下的文件对 VLC 已经是普通可读文件，不要再走 HTTP
        if (Rootfs.isUnderMount(path)) return false
        if (!looksLikeRootFile(path)) return false
        val file = File(path)
        return !file.canRead() || (file.isDirectory && file.list() == null)
    }

    fun isPlaylist(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in PLAYLIST_EXTENSIONS

    /**
     * 普通文件：读不到的才桥接。
     * 播放列表：即使可读也桥接，否则 libVLC 会自己解析本地 m3u，
     * 其中指向 /data 的条目会在 native 层 open 失败。
     */
    fun needsBridge(path: String): Boolean {
        if (Rootfs.isUnderMount(path)) return false
        return needsRoot(path) || isPlaylist(path)
    }

    fun allowed(path: String): Boolean =
        Rootfs.isUnderMount(path) || looksLikeRootFile(path) || isPlaylist(path)
}
