# VLC Root（LSPosed 模块，libxposed API 102）

让官方 VLC for Android（3.7.x）通过 root 访问 `/data` 下的文件。已在 Android 16 / VLC 3.7.1 上验证。

- 不重编译 VLC，不换签名；
- 原理：LSPosed hook 校验/路径映射 + 进程内 `su -M` 启动只读 FUSE 镜像（`rootfsd`），把 `/data` 映射到 VLC 私有目录；
- 支持：VLC 原生浏览器列 `/data` 目录、媒体库扫描、直接播放、Range 拖动、`.m3u` 重写、外挂字幕（视 VLC 版本）；
- 首次启动需要给 **VLC** 授予 root（不是模块），之后自动挂载。

## 下载

GitHub Actions 的 **vlcroot-unsigned-apk** artifact 是未签名发行包（`app-release-unsigned.apk`，versionName 1.0）。

### 签名

构建产物**不含签名**，安装前用 MT 管理器注入：

1. MT 管理器里打开 `app-release-unsigned.apk`；
2. 菜单 → **APK 签名** → 选择你的 key（如 `JGjiuge.key`）或新建签名；
3. 建议勾选 **V1 + V2** 签名，保存后再安装；
4. 以后升级也用同一个 key 签，才能覆盖安装。

> 未签名 APK 不能直接安装；LSPosed 只认已签名 APK。VLC 自身的数据库/自定义路径与模块签名无关。

## 构建

没有 Android Studio？看 **[BUILD-WITHOUT-STUDIO.md](BUILD-WITHOUT-STUDIO.md)**：用 GitHub Actions 云端出 APK。

## 使用

1. 安装已用 MT 管理器签名的 APK；
2. LSPosed/Vector 中启用模块，作用域勾选 VLC，强制停止 VLC；
3. 打开 VLC，授予 root；在“媒体库 → 添加自定义路径”里填 `/data/喜鹊谋杀案` 这类路径；
4. 原生浏览器和媒体库会通过挂载点访问；播放由 VLC 直接读 FUSE 文件。

## 目录

```
app/src/main/java/com/example/vlcroot/
  ModuleMain.kt        API 102 入口 + hooks
  Rootfs.kt            su -M 启动 rootfsd / 路径映射 / 版本化重挂
  RootBridge.kt        URI -> HTTP 的兜底判定
  RootServer.kt        127.0.0.1 HTTP + Range + m3u 重写
  RootShell.kt         su 执行 / 分块读
  PathPolicy.kt        白名单与"是否需要 root"
  CallSite.kt          入队/播放阶段判定
  RootUris.kt          content:// 编码
  RootPathProvider.kt  壳 provider
  PickerActivity.kt    /data 选择器
rootfsd/               Go 只读 FUSE 镜像（go-fuse，Android 16 未知 opcode 补丁）
scripts/               构建期 go-fuse 补丁
```

详细设计与风险见仓库外层的 `vlc-lsposed-root-hook-plan.md`、`vlc-rootfs-fuse-plan.md`。
