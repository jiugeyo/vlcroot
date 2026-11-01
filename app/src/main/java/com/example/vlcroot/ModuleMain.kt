package com.example.vlcroot

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.io.File

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

        // FUSE：让 VLC 原生浏览器/媒体库把 /data 当普通目录
        safe("File.exists") { hookFileExists() }
        safe("File.isDirectory") { hookFileIsDirectory() }
        safe("File.getCanonicalPath") { hookCanonicalPath() }
        safe("DirectoryRepository.addCustomDirectory") { hookAddCustomDirectory(param.classLoader) }
        safe("Application.onCreate") { hookApplicationOnCreate(param.classLoader) }
    }

    private fun hookApplicationOnCreate(cl: ClassLoader) {
        val clazz = Class.forName("android.app.Application", false, cl)
        val method = clazz.getDeclaredMethod("onCreate")
        hook(method).intercept { chain ->
            chain.proceed()
            val app = chain.thisObject as? Context
            val apk = runCatching { moduleApplicationInfo.sourceDir }.getOrNull()
            if (app != null && apk != null) {
                Thread {
                    runCatching { Rootfs.ensure(app, apk) }
                        .onFailure { log(Log.ERROR, TAG, "rootfs: ${it.message}") }
                }.apply { isDaemon = true }.start()
            }
        }
    }

    private fun hookFileExists() {
        val method = File::class.java.getDeclaredMethod("exists")
        hook(method).intercept { chain ->
            val file = chain.thisObject as File
            if (Rootfs.mounted && PathPolicy.looksLikeRootFile(file.path) && !Rootfs.isUnderMount(file.path)) {
                Rootfs.stat(file.path, false)
            } else {
                chain.proceed() as Boolean
            }
        }
    }

    private fun hookFileIsDirectory() {
        val method = File::class.java.getDeclaredMethod("isDirectory")
        hook(method).intercept { chain ->
            val file = chain.thisObject as File
            if (Rootfs.mounted && PathPolicy.looksLikeRootFile(file.path) && !Rootfs.isUnderMount(file.path)) {
                Rootfs.stat(file.path, true)
            } else {
                chain.proceed() as Boolean
            }
        }
    }

    private fun hookCanonicalPath() {
        val method = File::class.java.getDeclaredMethod("getCanonicalPath")
        hook(method).intercept { chain ->
            val file = chain.thisObject as File
            if (Rootfs.mounted && PathPolicy.looksLikeRootFile(file.path) && !Rootfs.isUnderMount(file.path)) {
                // /data 深层目录 App 无法 realpath，直接返回原路径；后续由 addCustomDirectory 映射到挂载点
                file.path
            } else {
                chain.proceed() as String
            }
        }
    }

    private fun hookAddCustomDirectory(cl: ClassLoader) {
        val clazz = Class.forName("org.videolan.vlc.repository.DirectoryRepository", false, cl)
        val method = clazz.getDeclaredMethod("addCustomDirectory", String::class.java)
        hook(method).intercept { chain ->
            val path = chain.args[0] as? String
            if (path != null && Rootfs.mounted) {
                chain.proceed(arrayOf(Rootfs.mapPath(path)))
            } else {
                chain.proceed()
            }
        }
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
