package com.example.vlcroot

import android.net.Uri

object RootBridge {

    /**
     * 把不可读的 file:// 或本模块的 content:// 换成本机 HTTP。
     * 返回 null 表示原 URI 就是 null；其余情况尽量返回原 URI。
     */
    fun maybeRewrite(uri: Uri?): Uri? {
        if (uri == null) return null
        if (!RootAccess.enabled) return uri

        val path = RootUris.pathOf(uri)
            ?: if (uri.scheme == "file") uri.path else null
        if (path.isNullOrEmpty()) return uri

        if (!PathPolicy.needsBridge(path)) return uri
        if (CallSite.fromEntryPoint()) return uri

        return runCatching { Uri.parse(RootServer.urlFor(path)) }.getOrDefault(uri)
    }

    /** 给 String.validateLocation() 用：放行 /data 下的本地文件。 */
    fun shouldAllow(location: String): Boolean {
        if (!RootAccess.enabled) return false
        val path = if (location.startsWith("file://")) {
            runCatching { Uri.parse(location).path }.getOrNull()
        } else {
            location
        }
        return path != null && PathPolicy.looksLikeRootFile(path)
    }
}
