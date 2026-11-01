# 架构说明

本文说明 VLC Root 为什么需要 root + FUSE，以及各层如何配合。普通用户看 [README](../README.md) 即可。

## 1. 问题：Java hook 到不了 native 层

VLC for Android 的目录浏览、媒体库扫描和播放分别发生在：

| 层 | 行为 | 能否 Java hook |
|---|---|---|
| Kotlin/Java UI | 菜单、`StorageProvider`、`DirectoryRepository` | ✅ |
| libVLC | `MediaBrowser`（`opendir/readdir`）、播放器打开文件 | ❌ |
| libmedialibrary | C++ 扫描、索引、缩略图 | ❌ |

只把数据库里的路径改成 `/data/...`，Java 层能显示这一行，但 native 层去 `opendir()` 时仍会被 SELinux/DAC 拒绝，于是出现“能看到目录、看不到文件”或“添加路径没反应”。

所以必须让 `/data` 在 VLC 看来是一个**普通可读目录**。本项目选择 root + 只读 FUSE。

## 2. 挂载

VLC 进程启动时，模块在 `Application.onCreate` 后：

1. 从模块 APK 的 `assets/rootfsd-arm64` 解出静态二进制；
2. 复制到 `/data/local/tmp/vlcrootfs/rootfsd`；
3. 通过 `su -M` 在**全局 mount namespace** 启动：
   ```
   rootfsd -mount /data/data/org.videolan.vlc/files/rootfs -source /data -uid <VLC uid> -gid <VLC uid>
   ```
4. 轮询 `su -M` 下的 `/proc/mounts`，确认挂载成功。

挂载点是 VLC 私有目录，VLC 自己有权限；FUSE 的所有 inode 都重映射为 VLC uid/gid，避免原生层因权限丢弃文件。

## 3. rootfsd

`rootfsd` 是一个 Go 静态二进制，基于 `github.com/hanwen/go-fuse/v2`，只实现只读操作：

- `Lookup` / `Getattr`
- `Readdir` / `Open` / `Read`
- 拒绝符号链接，防止从 `/data` 跳出白名单；
- 拒绝写操作（`EROFS`）；
- 隐藏挂载点自身，避免递归访问自己造成死锁。

## 4. Java 侧路径映射

关键 hook：

- `FileUtils.getUri(Uri)`：按需把 `file:///data/...` 换成 HTTP 兜底地址；
- `File.exists()/isDirectory()/getCanonicalPath()`：让 VLC 的校验看到映射后的路径；
- `DirectoryRepository.addCustomDirectory(String)`：把 `/data/...` 改写成挂载点路径再入库；
- `PlaylistManager.playIndex` / `StartActivity.startPlaybackFromApp`：播放入口兜底；
- `PlayerController.addSubtitleTrack`：字幕路径映射（视版本）。

路径规则：

```
/data                    -> <MOUNT>
/data/喜鹊谋杀案          -> <MOUNT>/喜鹊谋杀案
```

`<MOUNT> = /data/data/org.videolan.vlc/files/rootfs`

## 5. Android 16 的未知 FUSE opcode

部分 Android 16 / 小米内核会在 `lookup` 之后发送 go-fuse 不认识的 opcode（实测 `2016`，属于新内核扩展）。原版 go-fuse 的 `parseRequest()`：

```go
h = getHandler(op)
if h == nil {
    log.Printf("Unknown opcode %d", op)
    errno = ENOSYS
    return          // 不写 reply
}
```

`handleRequest` 收到错误后直接返回，内核永远等不到 reply，`ls` 和媒体库扫描就永久卡住。

构建时 `scripts/patch_go_fuse.py` 会修改 vendored go-fuse：未知 opcode 挂一个 fallback handler，让它走正常序列化路径回 `ENOSYS`。内核收到后会 fallback 到 `getattr`，扫描继续。

## 6. 为什么不用 Magisk 模块

模块只做两件事：LSPosed hook + 在 VLC 进程里 `su -M` 起一个进程。没有常驻服务、没有开机脚本、没有 mount 配置文件需要维护。这样：

- 升级 VLC 只需更新模块 hook；
- 换 root 管理器不需要重装 Magisk 模块；
- 卸载模块后不会残留系统级挂载（重启即清）。

## 7. 安全边界

- FUSE 只读，不能通过它写/删 `/data`；
- 只镜像 `/data`，不镜像 `/`；
- 拒绝符号链接；
- HTTP 兜底只绑定 `127.0.0.1`；
- 模块本身不申请 root，root 授权发生在 VLC 进程；
- 发行包未签名，签名私钥由用户自己保管。
