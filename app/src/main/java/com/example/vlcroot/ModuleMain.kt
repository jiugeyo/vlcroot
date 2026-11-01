package com.example.vlcroot

import android.net.Uri
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * API 102 (libxposed) 入口。
 *
 * 作用：在 VLC 进程里
 *  1) 起一个只监听 127.0.0.1 的 HTTP 服务，用 su 读取 /data；
 *  2) hook FileUtils.getUri(Uri)，在 URI 进入 libVLC 之前换成本机 HTTP。
 */
class ModuleMain : XposedModule() {

    private var mainProcess = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        mainProcess = param.processName == VLC_PACKAGE
        log(Log.INFO, TAG, "loaded in ${param.processName}, api=$apiVersion")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!mainProcess || param.packageName != VLC_PACKAGE) return
        if (!param.isFirstPackage) return

        RootAccess.enabled = runCatching { RootServer.start() }
            .onFailure { log(Log.ERROR, TAG, "server start failed: ${it.message}") }
            .isSuccess

        log(Log.INFO, TAG, "root server enabled=${RootAccess.enabled}, port=${RootServer.port}")

        // 提前触发一次 su 授权，避免第一次播放时 Magisk 弹窗卡住 HTTP 请求
        Thread { RootShell.exec("id -u", 30_000L) }.apply { isDaemon = true }.start()

        safe("FileUtils.getUri") { hookGetUri(param.classLoader) }
        safe("validateLocation") { hookValidateLocation(param.classLoader) }
        safe("addSubtitleTrack") { hookAddSubtitle(param.classLoader) }
    }

    private fun safe(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "$name hook failed: ${t.message}")
        }
    }

    private fun hookGetUri(cl: ClassLoader) {
        val clazz = Class.forName("org.videolan.vlc.util.FileUtils", false, cl)
        val method = clazz.getDeclaredMethod("getUri", Uri::class.java)
        hook(method).intercept { chain ->
            val original = chain.proceed() as? Uri
            RootBridge.maybeRewrite(original)
        }
    }

    private fun hookValidateLocation(cl: ClassLoader) {
        val clazz = Class.forName("org.videolan.vlc.util.KextensionsKt", false, cl)
        val method = clazz.getDeclaredMethod("validateLocation", String::class.java)
        hook(method).intercept { chain ->
            val location = chain.args[0] as? String
            if (location != null && RootBridge.shouldAllow(location)) true
            else chain.proceed() as Boolean
        }
    }

    private fun hookAddSubtitle(cl: ClassLoader) {
        val clazz = Class.forName("org.videolan.vlc.media.PlayerController", false, cl)
        val method = clazz.getDeclaredMethod(
            "addSubtitleTrack",
            String::class.java,
            java.lang.Boolean.TYPE
        )
        hook(method).intercept { chain ->
            val path = chain.args[0] as? String
            if (path != null && RootAccess.enabled && PathPolicy.needsRoot(path)) {
                chain.proceed(arrayOf(RootServer.urlFor(path), chain.args[1]))
            } else {
                chain.proceed()
            }
        }
    }

    companion object {
        const val TAG = "VlcRoot"
        const val VLC_PACKAGE = "org.videolan.vlc"
    }
}
