package com.example.vlcroot

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val MAX_TOKENS = 4096
private val TOKENS = ConcurrentHashMap<String, String>()

private fun lookupToken(uri: String): String? {
    val token = uri.substringAfter("/f/", "")
        .substringBefore('/')
        .substringBefore('?')
    return TOKENS[token]
}

/**
 * 只监听 127.0.0.1 的只读文件服务，支持 Range（seek 需要）。
 * token 随机、只在内存里，进程退出即失效。
 */
object RootServer {

    private var nano: Nano? = null

    @Volatile
    var port: Int = 0
        private set

    @Synchronized
    fun start() {
        if (nano != null) return
        val server = Nano()
        server.start(4000, true)
        nano = server
        port = server.listeningPort
    }

    fun urlFor(path: String): String {
        start()
        if (TOKENS.size > MAX_TOKENS) TOKENS.clear()
        val token = UUID.randomUUID().toString().replace("-", "")
        TOKENS[token] = path
        // 末尾保留原文件名/扩展名：VLC 会据此判断媒体类型（视频/音频/播放列表）
        val name = runCatching {
            java.net.URLEncoder.encode(path.substringAfterLast('/'), "UTF-8")
        }.getOrDefault("media")
        return "http://127.0.0.1:$port/f/$token/$name"
    }

    private class Nano : NanoHTTPD("127.0.0.1", 0) {

        override fun serve(session: IHTTPSession): Response {
            val path = lookupToken(session.uri)
                ?: return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")

            if (!PathPolicy.allowed(path)) {
                return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "forbidden")
            }

            if (PathPolicy.isPlaylist(path)) {
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "audio/x-mpegurl", rewritePlaylist(path))
            }

            val total = RootShell.size(path)
            if (total < 0) {
                return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "unreadable")
            }

            val range = session.headers["range"]?.let { parseRange(it, total) }
            val start = range?.first ?: 0L
            val length = if (range == null) total else range.last - range.first + 1
            val status = if (range == null) Response.Status.OK else Response.Status.PARTIAL_CONTENT

            val response = NanoHTTPD.newFixedLengthResponse(
                status,
                mimeOf(path),
                RootShell.openRange(path, start, length),
                length
            )
            response.addHeader("Accept-Ranges", "bytes")
            if (status == Response.Status.PARTIAL_CONTENT) {
                response.addHeader("Content-Range", "bytes $start-${start + length - 1}/$total")
            }
            return response
        }

        private fun parseRange(header: String, total: Long): LongRange? {
            if (!header.startsWith("bytes=", ignoreCase = true)) return null
            val spec = header.substringAfter('=').substringBefore(',').trim()
            val start = spec.substringBefore('-').trim().toLongOrNull() ?: 0L
            val end = spec.substringAfter('-', "").trim().toLongOrNull() ?: (total - 1)
            if (start < 0 || start >= total) return null
            return start..minOf(end, total - 1)
        }

        private fun mimeOf(path: String): String {
            val ext = path.substringAfterLast('.', "").lowercase()
            return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
        }

        /** 把 m3u/m3u8 里的相对路径解析成绝对路径，root 条目换成 HTTP 条目。 */
        private fun rewritePlaylist(path: String): String {
            val text = RootShell.readText(path) ?: return ""
            val base = path.substringBeforeLast('/', "")
            val sb = StringBuilder(text.length + 256)
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                when {
                    line.isEmpty() -> sb.append('\n')
                    line.startsWith("#") -> sb.append(raw).append('\n')
                    line.contains("://") -> sb.append(line).append('\n')   // 已经是绝对 MRL，别动
                    else -> {
                        val absolute = if (line.startsWith("/")) line else "$base/$line"
                        val mrl = if (PathPolicy.needsRoot(absolute)) {
                            RootServer.urlFor(absolute)
                        } else {
                            "file://$absolute"
                        }
                        sb.append(mrl).append('\n')
                    }
                }
            }
            return sb.toString()
        }
    }
}

