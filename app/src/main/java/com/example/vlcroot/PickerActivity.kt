package com.example.vlcroot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * 极简 /data 文件选择器：
 *  - 用 su 列目录；
 *  - 点文件 -> content:// 显式 intent 给 VLC -> 模块 hook 换成 HTTP 播放。
 */
class PickerActivity : Activity() {

    private lateinit var pathView: TextView
    private lateinit var listView: ListView
    private var current = "/data"
    private var entries: List<RootShell.Entry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val header = TextView(this).apply {
            text = "VLC Root：以 root 浏览 /data"
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 32, 32, 8)
        }

        pathView = TextView(this).apply {
            textSize = 13f
            setPadding(32, 0, 32, 12)
        }

        listView = ListView(this)

        root.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            pathView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        listView.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                goUp()
            } else {
                val entry = entries.getOrNull(position - 1) ?: return@setOnItemClickListener
                if (entry.isDir) {
                    current = entry.path
                    refresh()
                } else {
                    play(entry.path)
                }
            }
        }

        refresh()
    }

    private fun goUp() {
        if (current == "/" || current.isEmpty()) return
        current = current.substringBeforeLast('/', "/").ifEmpty { "/" }
        refresh()
    }

    private fun refresh() {
        pathView.text = current
        Thread {
            val result = runCatching { RootShell.list(current) }.getOrElse { emptyList() }
            runOnUiThread {
                entries = result
                val names = ArrayList<String>(result.size + 1)
                names.add("..  上级目录")
                result.forEach { names.add((if (it.isDir) "[目录] " else "[文件] ") + it.name) }
                listView.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    names
                )
                if (result.isEmpty()) {
                    Toast.makeText(this, "空目录，或 root 未授权", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun play(path: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setData(RootUris.forPath(path))
            setClassName("org.videolan.vlc", "org.videolan.vlc.StartActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, "无法启动 VLC：${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}
