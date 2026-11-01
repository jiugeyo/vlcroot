package com.example.vlcroot

/**
 * MVP 先固定开启；后续可接 libxposed RemotePreferences 做开关。
 * 关闭时所有 hook 直接放行原 URI，行为与官方 VLC 一致。
 */
object RootAccess {
    @Volatile
    var enabled: Boolean = true
}
