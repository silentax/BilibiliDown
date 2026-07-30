# 安全发布链迁移说明

## 当前状态

截至 2026-07-30，旧应用内自更新、正式 Release、Pre-release artifact、Windows MSI 和第三方上传流程均已禁用。它们曾依赖原上游产物、SHA-1 摘要或历史第三方账号，不能作为本维护版的可信交付链。

应用的“检查更新”目前只显示本仓库 Releases 页面：

<https://github.com/silentax/BilibiliDown/releases>

在新的签名更新协议完成前，应用不会联网查询、下载、解压或替换自身 JAR。旧更新源和 Beta GitHub Token 配置也会被忽略并在下次保存时清理。当前仓库不会自动创建 GitHub Release、上传第三方存储或构建旧 MSI。

## 为什么采用安全失败

- SHA-1 不能满足新产物的完整性保护要求。
- 原上游 JRE、应用 ZIP 和 Beta artifact 不由本维护仓库控制。
- 当前没有本仓库自有的 JDK 21 runtime、Windows MSI、macOS DMG 和签名证书。
- 未经人工批准创建正式 Release 或向外部服务发布，容易造成误发布和凭据越权使用。

保留旧文件路径只是为了让历史引用得到明确错误；文件内容是不可执行的安全占位，不应在其上直接恢复旧逻辑。

## 新发布链目标

新流程应从同一受控 commit 构建并完成以下步骤：

1. Windows 与 macOS 分别执行 `clean check jar`，测试不允许跳过或吞错。
2. 使用 JDK 21 `jlink`/`jpackage` 生成仓库自有 runtime 与安装包，不下载原上游 JRE 或应用 ZIP。
3. Windows 至少输出 MSI，macOS 至少输出 DMG；按实际支持范围区分 x64 与 arm64。
4. ffmpeg 只从本仓库受控资产或用户系统安装中取得；随包分发时固定 SHA-256、许可证和来源记录。
5. 每个公开资产生成 SHA-256 清单，并生成 GitHub artifact attestation；具备证书后增加 Windows Authenticode 与 macOS Developer ID 签名、公证。
6. 构建任务仅拥有 `contents: read`，发布任务使用独立 GitHub Environment、人工批准和最小 `contents: write` 权限。
7. CI 先上传短期 artifacts；只有显式版本输入、Tag 与源码版本一致且双平台测试通过时，才允许提升为草稿 Release。
8. 默认不向 Supabase、Cloudinary、Bitbucket、Gitee、ImageKit 或 Railway 同步。

## 恢复发布前的验收门槛

- Windows 10/11 与当前 macOS 的真实 GUI 启动、登录、视频下载、仅音频下载和退出流程均完成手工验收。
- MSI 安装、升级、卸载不会删除用户的配置与下载内容；DMG 安装和首次启动通过 Gatekeeper。
- 在无系统 Java 的干净环境中可运行；内置 runtime 明确来自当前 JDK 21 构建。
- 所有下载资产均能用公布的 SHA-256 复验，且构建来源可追溯到本仓库 commit。
- 日志和 artifacts 不包含 Cookie、fingerprint、Token、签名 URL、JAR 构建残留或本地配置。
- Release 必须先创建为 draft，经人工复核文件名、版本、摘要、许可证、安装与回滚说明后再发布。

## 应用内更新的后续设计

应用内自动更新应最后恢复。最低要求是只访问本仓库固定 HTTPS API，使用版本化 manifest 描述平台、架构、大小和 SHA-256，并对 manifest 做独立签名验证。任何网络、签名、摘要、平台或路径校验失败都必须停止安装并删除暂存文件；不能提供“忽略校验继续”的选项。

在以上条件全部完成前，`LegacyUpdatePolicy` 必须保持 fail-closed，旧工作流和脚本必须保留 `LEGACY_PIPELINE_DISABLED` 安全标记。
