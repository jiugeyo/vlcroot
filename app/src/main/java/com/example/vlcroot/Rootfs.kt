package com.example.vlcroot

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * 在 VLC 进程里通过 su -M 启动 rootfsd，把 /data 以只读 FUSE 方式挂到 VLC 私有目录。
 * 挂载是全局 namespace，VLC 能直接看到；不需要 Magisk 模块。
 */
object Rootfs {

    const val MOUNT = "/data/data/org.videolan.vlc/rootfs"

    @Volatile
    var mounted = false
        private set

    @Volatile
    private var daemon: Process? = null

    @Volatile
    private var starting = false

    private val statCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    @Synchronized
    fun ensure(context: Context, moduleApk: String) {
        if (mounted || starting) return
        starting = true
        try {
            if (isWorking()) {
                mounted = true
                Log.i(ModuleMain.TAG, "rootfs already mounted")
                return
            }

            val binary = extract(context, moduleApk)
            val uid = context.applicationInfo.uid
            val cmd = buildString {
                append("mkdir -p /data/local/tmp/vlcrootfs && ")
                append("cp ${RootShell.quote(binary.absolutePath)} /data/local/tmp/vlcrootfs/rootfsd && ")
                append("chmod 755 /data/local/tmp/vlcrootfs/rootfsd && ")
                append("umount -l ${RootShell.quote(MOUNT)} 2>/dev/null; ")
                append("/data/local/tmp/vlcrootfs/rootfsd")
                append(" -mount ${RootShell.quote(MOUNT)}")
                append(" -source /data")
                append(" -uid $uid -gid $uid")
                append(" >> /data/local/tmp/vlcrootfs.log 2>&1")
            }
            daemon = RootShell.startGlobal(cmd)

            repeat(50) {
                if (isWorking()) {
                    mounted = true
                    Log.i(ModuleMain.TAG, "rootfs mounted at $MOUNT")
                    return
                }
                try {
                    Thread.sleep(100)
                } catch (_: InterruptedException) {
                }
            }
            mounted = false
            Log.e(ModuleMain.TAG, "rootfs mount failed; see /data/local/tmp/vlcrootfs.log")
        } finally {
            starting = false
        }
    }

    private fun extract(context: Context, moduleApk: String): File {
        val out = File(context.codeCacheDir, "rootfsd-arm64")
        if (!out.exists() || out.length() == 0L) {
            // 注意：这里要读模块 APK 的 assets，不是 VLC 的 assets
            ZipFile(moduleApk).use { zip ->
                val entry = zip.getEntry("assets/rootfsd-arm64")
                    ?: throw IllegalStateException("rootfsd-arm64 missing in module apk")
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        out.setExecutable(true, false)
        return out
    }

    fun isWorking(): Boolean {
        val mounts = RootShell.exec("grep ' ${MOUNT} ' /proc/mounts")
        if (mounts.isBlank()) return false
        val probe = RootShell.exec("ls ${RootShell.quote("$MOUNT/data")} >/dev/null 2>&1 && echo ok")
        return probe.contains("ok")
    }

    fun isUnderMount(path: String): Boolean = path == MOUNT || path.startsWith("$MOUNT/")

    fun mapPath(path: String): String {
        if (!mounted) return path
        if (isUnderMount(path)) return path
        if (path == "/data" || path.startsWith("/data/")) return MOUNT + path
        return path
    }

    /**
     * 供 File.exists()/isDirectory() hook 使用：/data 下的路径用 root 探测。
     */
    fun stat(path: String, wantDirectory: Boolean): Boolean {
        val now = System.currentTimeMillis()
        statCache[path]?.let { (ok, at) ->
            if (now - at < 5000L) return ok
        }
        val test = if (wantDirectory) "-d" else "-e"
        val out = RootShell.exec("[ $test ${RootShell.quote(path)} ] && echo 1")
        val ok = out.trim() == "1"
        statCache[path] = ok to now
        return ok
    }
}
