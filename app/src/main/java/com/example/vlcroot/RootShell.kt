package com.example.vlcroot

import java.io.FilterInputStream
import java.io.InputStream
import java.io.File
import java.util.concurrent.TimeUnit

object RootShell {

    private val ABSOLUTE_CANDIDATES = listOf(
        "/system/bin/su",
        "/debug_ramdisk/su",
        "/sbin/su",
        "/system/xbin/su"
    )

    @Volatile
    private var suPath: String? = null

    private fun su(): String {
        suPath?.let { return it }
        val absolute = ABSOLUTE_CANDIDATES.firstOrNull {
            runCatching { File(it).exists() }.getOrDefault(false)
        }
        val resolved = absolute ?: "su"
        suPath = resolved
        return resolved
    }

    /**
     * 在全局 mount namespace 下启动一个长期运行的 root 进程（rootfsd）。
     * 不读取 stdout（命令内部已重定向到日志文件），进程引用由调用方保存。
     */
    fun startGlobal(command: String): Process? = runCatching {
        ProcessBuilder(su(), "-M", "-c", command)
            .redirectErrorStream(true)
            .start()
    }.getOrNull()

    /**
     * 执行一条 su 命令。
     * 用独立线程持续读 stdout，避免大输出（ls/cat）把管道写满导致死锁；
     * 等待超时则 destroy 子进程（例如 Magisk 授权弹窗一直没人点）。
     */
    fun exec(command: String, timeoutMs: Long = 15_000L): String {
        return runCatching {
            val process = ProcessBuilder(su(), "-c", command)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { reader ->
                        output.append(reader.readText())
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) process.destroy()
            reader.join(1000)
            output.toString()
        }.getOrDefault("")
    }

    /** POSIX 单引号转义：' -> '\'' */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun size(path: String): Long {
        val out = exec("stat -c %s ${quote(path)}")
        return out.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.toLongOrNull() ?: -1L
    }

    fun readText(path: String): String? =
        runCatching { exec("cat ${quote(path)}") }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** 顺序读 [offset, offset+length)，su 子进程随流关闭而销毁 */
    fun openRange(path: String, offset: Long, length: Long): InputStream {
        val cmd = "tail -c +${offset + 1} ${quote(path)} | head -c $length"
        val process = ProcessBuilder(su(), "-c", cmd).start()
        return object : FilterInputStream(process.inputStream) {
            override fun close() {
                runCatching { process.destroy() }
                super.close()
            }
        }
    }

    fun list(dir: String): List<Entry> {
        val out = exec("ls -1Ap ${quote(dir)}")
        return out.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { name ->
                val isDir = name.endsWith("/")
                val clean = name.trimEnd('/')
                Entry("$dir/$clean".replace("//", "/"), clean, isDir)
            }
            .sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.name.lowercase() })
            .toList()
    }

    data class Entry(val path: String, val name: String, val isDir: Boolean)
}
