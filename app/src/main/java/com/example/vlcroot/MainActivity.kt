package com.example.vlcroot

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 模块首页：只展示使用说明和入口，不再暴露 /data 路径或调试列表。
 */
class MainActivity : Activity() {

    private val bgColor = Color.parseColor("#F5F7FB")
    private val cardColor = Color.WHITE
    private val primary = Color.parseColor("#0B57D0")
    private val textColor = Color.parseColor("#1B1B1F")
    private val muted = Color.parseColor("#5F6368")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bgColor

        val scroll = ScrollView(this).apply { setBackgroundColor(bgColor) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(26), dp(24), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(text("VLC 数据访问", 27f, textColor, bold = true))
        root.addView(
            text("让 VLC 通过 root 访问 /data 下的媒体文件", 14f, muted)
                .apply { setPadding(0, dp(6), 0, dp(22)) }
        )

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "1.1"

        root.addView(card {
            addView(text("●  已安装 · v$version", 15f, Color.parseColor("#137333"), bold = true))
            addView(
                text(
                    "在 LSPosed 中启用本模块，并把作用域勾选 VLC。首次启动 VLC 时给 VLC 授予 root。",
                    13f,
                    muted
                ).apply { setPadding(0, dp(8), 0, 0) }
            )
        })

        root.addView(card {
            addView(text("使用步骤", 16f, textColor, bold = true))
            addView(step("1", "LSPosed 启用模块，作用域只勾选 VLC"))
            addView(step("2", "打开 VLC，按提示给 VLC 授予 root"))
            addView(step("3", "VLC → 媒体库 → 添加自定义路径"))
            addView(step("4", "在原生浏览器或媒体库中打开文件"))
            addView(
                text(
                    "添加路径后如果暂时为空，退出媒体库再进一次。",
                    12f,
                    muted
                ).apply { setPadding(0, dp(6), 0, 0) }
            )
        })

        root.addView(card {
            addView(text("说明", 16f, textColor, bold = true))
            addView(
                text(
                    "模块只建立只读访问，不会修改或删除 /data 中的文件。VLC 升级后如果 hook 失效，请更新模块。",
                    13f,
                    muted
                ).apply { setPadding(0, dp(8), 0, 0) }
            )
        })

        root.addView(
            primaryButton("打开 VLC") { openVlc() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )
        root.addView(
            secondaryButton("查看项目主页") { openProject() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        root.addView(
            text("VLC Root · MIT License", 12f, Color.parseColor("#9AA0A6"))
                .apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(22), 0, 0)
                }
        )
    }

    private fun openVlc() {
        val intent = packageManager.getLaunchIntentForPackage(VLC_PACKAGE)
        if (intent == null) {
            Toast.makeText(this, "未安装 VLC", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "无法启动 VLC", Toast.LENGTH_SHORT).show() }
    }

    private fun openProject() {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jiugeyo/vlcroot"))
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun step(number: String, content: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(12), 0, 0)
            addView(
                text(number, 13f, Color.WHITE, bold = true).apply {
                    gravity = Gravity.CENTER
                    background = rounded(primary, 11)
                    layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                }
            )
            addView(
                text(content, 13f, textColor).apply {
                    setPadding(dp(10), dp(2), 0, 0)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private fun card(block: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(cardColor, 18)
            block(this)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
    }

    private fun primaryButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setAllCaps(false)
            textSize = 15f
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(primary)
            stateListAnimator = null
            minHeight = dp(48)
            setOnClickListener { onClick() }
        }
    }

    private fun secondaryButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setAllCaps(false)
            textSize = 15f
            setTextColor(primary)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#C7D2E8"))
            }
            stateListAnimator = null
            minHeight = dp(48)
            setOnClickListener { onClick() }
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(0f, 1.15f)
        }
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val VLC_PACKAGE = "org.videolan.vlc"
    }
}

