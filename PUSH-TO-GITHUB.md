# 用 Git 推到 GitHub（不需要在聊天里发密码）

> 工程已经在本机初始化成 git 仓库，并做好第一个提交；remote 也已指向
> `https://github.com/jiugeyo/vlcroot.git`。
> 你只需要在 GitHub 网页建一个空仓库，然后在 PowerShell 里执行 `git push`，
> 登录交给 Git Credential Manager 的浏览器弹窗。

---

## 0. 先做安全处理（重要）

不要在聊天、记事本、截图里放 GitHub 密码。已经发出去过的密码应当视为泄露：

1. 打开 <https://github.com/settings/security> → **Change password**，换一个新密码；
2. 同一个页面开启 **Two-factor authentication**；
3. 打开 <https://github.com/settings/sessions>，把不认识的登录全部 **Revoke**；
4. 如果这个密码在别的地方也用过（邮箱、QQ、游戏），一并改掉。

> 注：GitHub 早就禁止用“账号密码”做 git 操作，`git push` 时输密码也不会成功；
> 正确方式是浏览器登录（Git Credential Manager）或 Personal Access Token。

---

## 1. 建一个空仓库

打开（已登录状态下）：

<https://github.com/new?name=vlcroot>

- Repository name：`vlcroot`
- 选 Public 或 Private 都可以（Private 也能用 Actions，免费额度足够）
- **不要**勾选 "Add a README file" / .gitignore / license（要空仓库）
- 点 **Create repository**

---

## 2. 推送（PowerShell）

```powershell
cd "C:\Users\11204\Documents\deepseek-harness\default-workspace\vlcroot"
git push -u origin main
```

第一次会弹出 **Git Credential Manager** 的窗口：

- 选 **Sign in with your browser**；
- 在浏览器里登录 `jiugeyo`；
- 授权后回到终端，推送自动完成。

如果终端让你输入 username/password：

- username 填 `jiugeyo`；
- password **不要填账号密码**，去 <https://github.com/settings/tokens> 建一个
  **Fine-grained token**：
  - Repository access：Only select repositories → `vlcroot`
  - Permissions：Contents → Read and write
  - 生成后把 token 当密码粘贴（只显示一次）。

---

## 3. 触发构建

1. 打开 `https://github.com/jiugeyo/vlcroot/actions`
2. 左侧 **build-apk** → **Run workflow** → **Run workflow**
3. 等 5–10 分钟
4. 点进这次运行 → 页面底部 **Artifacts** → 下载 `vlcroot-unsigned-apk.zip`，解压后用 MT 管理器签名
5. 解压得到 `app-debug.apk`

后续步骤（安装、LSPosed 启用、测试、排错）见
[BUILD-WITHOUT-STUDIO.md](BUILD-WITHOUT-STUDIO.md)。

---

## 常见问题

| 现象 | 处理 |
|---|---|
| `remote origin already exists` | 不用管，remote 已经配好；直接 `git push` |
| `Repository not found` | 仓库还没建，或名字不是 `vlcroot`；按第 1 步建 |
| 弹出登录但浏览器打不开 | 在 Git 安装目录运行 `git-credential-manager configure`，或改用 token |
| `src refspec main does not match any` | 当前不在仓库目录；先 `cd` 到 `vlcroot` |
| 推送很慢/超时 | GitHub 在国内不稳定；多试几次，或开代理后再 push |
