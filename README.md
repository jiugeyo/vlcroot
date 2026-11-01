# VLC Root（LSPosed 模块，libxposed API 102）

让官方 VLC for Android（3.7.x）通过 root 播放 `/data` 下的文件。
已在 Android 16 / VLC 3.7.1 的目录结构上核对过 hook 点；`targetSdk 35`，Android 16 可直接安装。

- 不重编译 VLC，不换签名；
- 原理：hook `FileUtils.getUri()`，把 `file:///data/...` 换成本机 `http://127.0.0.1:<port>/...`，模块在 VLC 进程里用 `su` 分块读；
- 支持：直接播放、Range 拖动、`.m3u` 重写、外挂字幕（视 VLC 版本）；
- 不支持：VLC 原生浏览器列 `/data`、媒体库扫描 `/data`、投屏。

## 构建

没有 Android Studio？看 **[BUILD-WITHOUT-STUDIO.md](BUILD-WITHOUT-STUDIO.md)**：用 GitHub Actions 云端出 APK。

## 使用

1. 安装 APK；
2. LSPosed/Vector 中启用模块，作用域勾选 VLC，强制停止 VLC；
3. 打开 "VLC Root" 选择器，或从文件管理器用 VLC 打开 `/data` 文件；
4. Magisk 弹窗授予 **VLC** root（不是模块）。

## 目录

```
app/src/main/java/com/example/vlcroot/
  ModuleMain.kt        API 102 入口 + 三个 hook
  RootBridge.kt        URI -> HTTP 的判定
  RootServer.kt        127.0.0.1 HTTP + Range + m3u 重写
  RootShell.kt         su 执行 / 分块读
  PathPolicy.kt        白名单与"是否需要 root"
  CallSite.kt          入队/播放阶段判定
  RootUris.kt          content:// 编码
  RootPathProvider.kt  壳 provider
  PickerActivity.kt    /data 选择器
```

详细设计与风险见仓库外层的 `vlc-lsposed-root-hook-plan.md`。
