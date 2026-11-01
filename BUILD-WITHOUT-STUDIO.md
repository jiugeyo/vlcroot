# 没有 Android Studio 怎么构建（GitHub Actions 云端出包）

你不需要装 Android Studio、JDK 或 Android SDK。整个 APK 在 GitHub 的服务器上编译，你只需要一个 GitHub 账号和浏览器。

> 本工程按 **Android 16 / VLC 3.7.1** 配置：`targetSdk 35`（Android 16 可直接安装），模块 API 102（Vector 2.2.1）。
> 根目录下有 `.github/workflows/build-apk.yml`。
> 目标产物：`app-debug.apk`（debug 签名，可直接安装，LSPosed 接受）。

---

## 0. 你需要准备

- 一台能上网的电脑（Windows 即可），浏览器；
- GitHub 账号（免费）；
- 手机上：Magisk / KernelSU（root）、LSPosed 2.2.1（Vector，API 102）、VLC 3.7.1；
- 数据线 + `adb`（可选，用于看日志和快速测试；你机器上已有 `C:\adb-fastboot\adb.exe`）。

---

## 1. 把工程放到 GitHub

### 方式 A：网页上传（最简单）

1. 打开 <https://github.com/new>，新建一个仓库（Private 也可以），名字随意，例如 `vlcroot`，**不要**勾选 "Add a README file"。
2. 进入仓库，点 **Add file → Upload files**。
3. 把本目录（`vlcroot/`）里的**内容**拖进去：
   - `settings.gradle.kts`
   - `build.gradle.kts`
   - `gradle.properties`
   - `app/`（整个目录）
   - `BUILD-WITHOUT-STUDIO.md`、`README.md`
   - `.github/`（如果拖拽时被系统隐藏，见方式 B）
4. 点 **Commit changes**。

> 浏览器对以 `.` 开头的目录支持不稳定。如果 `.github` 没上传成功，用方式 B 单独建 workflow 文件。

### 方式 B：网页新建 workflow 文件

1. 仓库页面 → **Add file → Create new file**。
2. 文件名填：`.github/workflows/build-apk.yml`（输入 `/` 会自动建目录）。
3. 把本工程 `.github/workflows/build-apk.yml` 的内容整段粘贴进去。
4. Commit。

---

## 2. 触发云端构建

1. 打开仓库的 **Actions** 标签页。
2. 左侧选 **build-apk**。
3. 右侧 **Run workflow** → 分支选 `main` → **Run workflow**。
4. 等 5–10 分钟。首次构建要下载 Gradle/AGP/依赖，会慢一些。
5. 构建完成后，点进这次运行，页面底部 **Artifacts** → 下载 `vlcroot-debug-apk.zip`。
6. 解压得到 `app-debug.apk`。

> 如果 Actions 页面提示 workflow 无权限：仓库 **Settings → Actions → General → Workflow permissions** 选 "Read and write permissions" 并保存。

---

## 3. 安装与启用

1. 把 `app-debug.apk` 传到手机安装（允许"安装未知应用"）。
2. 打开 **LSPosed / Vector** 管理器：
   - 模块列表里应出现 **VLC Root**；
   - 启用它；
   - 作用域勾选 **VLC**（`org.videolan.vlc`）；
   - 强制停止 VLC（设置 → 应用 → VLC → 强行停止），再打开 VLC。
3. 首次播放 `/data` 文件时，Magisk 会弹 **VLC 请求 root**（注意：是 VLC，不是 VLC Root）——允许，并记住选择。

> 模块自己也有一个 "VLC Root" 图标（选择器界面），它读目录时用的是**模块自己的 UID**，会再弹一次 root 授权；这是正常的。

---

## 4. 验证

### 4.1 最快：用模块的选择器

1. 桌面打开 **VLC Root**；
2. 默认在 `/data`，进目录，点一个视频文件；
3. 应该直接拉起 VLC 播放。

如果列表是空的/提示 root 未授权，说明模块自己的 root 没给。

### 4.2 用 adb 验证（不依赖选择器）

```bash
# 推一个测试视频进 /data/local/tmp
adb push test.mp4 /data/local/tmp/test.mp4

# 改成 600，确保 VLC 自己读不到，才能真正验证 root 通路
adb shell su -c "chmod 600 /data/local/tmp/test.mp4"

# 让 VLC 打开它
adb shell am start -a android.intent.action.VIEW ^
  -n org.videolan.vlc/.StartActivity ^
  -d "file:///data/local/tmp/test.mp4"

# 看模块日志
adb logcat -s VlcRoot
```

Windows `cmd` 用 `^` 续行；PowerShell 用反引号或写成一行。

期望日志：

```
VlcRoot: loaded in org.videolan.vlc, api=102
VlcRoot: root server enabled=true, port=xxxxx
```

### 4.3 播放列表 / 字幕

- `/data` 下的 `.m3u`：用选择器点它，服务端会重写成绝对条目；
- 外挂字幕：目前只 hook 了 `PlayerController.addSubtitleTrack(String, Boolean)`，如果 VLC 版本签名不同，需要按日志补。

---

## 5. 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| Actions 构建失败 | 把失败步骤的日志贴出来；最常见是 SDK license 或版本号，按报错改 `build.gradle.kts` |
| LSPosed 里看不到模块 | APK 里缺 `META-INF/xposed/module.prop`；用 `unzip -l app-debug.apk | grep xposed` 检查；或 LSPosed 版本不是 API 102 |
| 模块显示已启用但 VLC 无变化 | 没勾选作用域 / 没强制停止 VLC；看 `adb logcat -s VlcRoot` 是否有 `loaded in` |
| 播放报"无效位置" | `validateLocation` hook 未生效（可能被 R8 内联）；先换 `content://` 选择器通道；仍不行把日志发出来 |
| 能播但没声音/被当音频 | provider 的 MIME 没识别对；看 `RootPathProvider.getType`，或改用 `file://` 通道 |
| 拖进度卡顿 | 每次 seek 会新起一个 `su`，低端机 100–200 ms 属正常；后续可做常驻 shell |
| 历史记录里是 `127.0.0.1` | 调用来源判定失效（VLC 改了类名）；播放不受影响，续播会失效，需要按日志调整 |
| 更新模块后装不上 | 两次构建的 debug 签名不同；workflow 已缓存 `debug.keystore`，若仍冲突就先卸载旧模块再装 |

---

## 6. 本地命令行构建（可选，需能访问 Google/Maven）

如果你不想用 GitHub，也可以只装命令行工具（不需要 Android Studio）：

1. 安装 **JDK 17**（Temurin）。
2. 下载 **Android commandline-tools**，解压后：
   ```bash
   sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
   ```
3. 安装 **Gradle 8.9**（或使用 `gradle wrapper` 生成 wrapper）。
4. 在工程根目录：
   ```bash
   gradle :app:assembleDebug
   ```
5. APK 在 `app/build/outputs/apk/debug/app-debug.apk`。

> 这条路要求网络能访问 `dl.google.com` 和 `repo.maven.apache.org`；国内网络通常需要代理。

---

## 7. 下一步

先把 APK 跑起来，把 `adb logcat -s VlcRoot` 的输出发回来。重点看：

- `loaded in org.videolan.vlc` 是否出现；
- `getUri hook failed` / `validateLocation hook failed` 是否出现；
- 播放时 `root server enabled=true` 的端口是否正常。

有这三条，就能判断是"hook 没挂上"还是"服务端/取流有问题"，再针对性修。
