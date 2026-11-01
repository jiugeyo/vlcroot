package com.example.vlcroot

import android.net.Uri
import android.util.Base64

/**
 * 用 content:// 把 /data 路径从模块界面送进 VLC。
 * 真正的读取不会走这个 provider，而是被 FileUtils.getUri 的 hook 换成 HTTP。
 */
object RootUris {

    const val AUTHORITY = "com.example.vlcroot.file"

    fun forPath(path: String): Uri {
        val encoded = Base64.encodeToString(
            path.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        return Uri.parse("content://$AUTHORITY/$encoded")
    }

    fun pathOf(uri: Uri): String? {
        if (uri.scheme != "content" || uri.authority != AUTHORITY) return null
        val segment = uri.lastPathSegment ?: return null
        return runCatching {
            String(Base64.decode(segment, Base64.URL_SAFE), Charsets.UTF_8)
        }.getOrNull()
    }
}
