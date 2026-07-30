# BilibiliDown 审查报告与优化路线图

> 审查日期：2026-07-30  
> 审查基线：`master` / `9957361`（V6.41）  
> 审查方式：源码、配置结构、构建发布脚本、测试和界面预览的静态审查；未读取本地 Cookie 内容。

## 1. 结论

BilibiliDown 已经具备较丰富的 B站资源解析和下载能力，但目前更接近面向技术用户的功能型工具，还不是普通用户能够低成本、安全、稳定使用的桌面产品。

不建议推倒重写解析和下载内核。现有 Parser、Downloader、断点下载、批量任务、字幕弹幕和 ffmpeg 合并能力值得保留。推荐采用“保留领域能力，重做产品外壳和基础设施”的演进路线。

| 维度 | 静态评价 | 主要依据 |
|---|---:|---|
| 功能覆盖 | 7/10 | 支持多类 URL、批量、断点、音视频合并、字幕弹幕 |
| 可用性 | 3/10 | 启动、登录、ffmpeg、fingerprint 依赖人工排障 |
| 易用性 | 3/10 | 主任务分散在主页、作品 Tab、配置菜单和下载页 |
| 流畅度 | 3/10 | Swing 跨线程更新、固定布局、轮询重绘、查询串行 |
| 可维护性 | 3/10 | 全局静态状态、UI 与下载器耦合、非标准构建、错误吞没 |
| 安全性 | 2/10 | 明文凭据、敏感日志、Trust All TLS、动态源码插件、自更新缺少签名 |
| 测试交付 | 2/10 | CI 只检查版本号，测试源码在打包前被删除 |

以上评分用于确定改造优先级，不代表网络环境下的性能基准。

## 2. 值得保留的能力

- Parser、Downloader 已有接口和注解扩展机制，URL 类型覆盖较广。
- 下载支持 `.part`、断点续传、多线程、重试和 ffmpeg 合并。
- 已具备下载历史仓库、批量任务、字幕和弹幕能力。
- 常规 HTTP 请求已有连接和读取超时。
- 登录辅助 HTTP 服务只绑定 loopback，没有暴露到局域网。

## 3. 必须优先处理的问题

### P0-1 凭据和日志

- Cookie 和 refresh token 明文写入 `config/cookies.config`。
- 本地 Cookie、fingerprint 文件默认可能对同机其他用户可读。
- 配置加载会打印全部 key/value；fingerprint、payload、登录 authKey 也可能进入日志。
- password、push token、GitHub token 等配置与普通配置使用同一存储和日志链路。

改进要求：

- macOS 使用 Keychain，Windows 使用 Credential Manager，Linux 使用 Secret Service。
- 在密钥库迁移完成前，敏感文件至少限制为仅当前用户读写。
- 统一日志脱敏，禁止输出 Cookie、CSRF、token、完整签名 URL 和请求 payload。
- 默认日志仅保留任务 ID、阶段、错误类型和 HTTP 状态码。

### P0-2 TLS 和运行时

项目允许全局信任任意 TLS 证书，这会削弱登录和下载请求的安全性。正确方案是迁移到并随应用打包 JDK 21，删除 `allowInsecure` 能力，不再依赖用户本机的旧 Java 8。

### P0-3 动态插件和自更新

- 启动时可编译并加载 `parsers/`、`pushers/` 下的 Java 源码，等同于执行该目录中的任意代码。
- 自更新下载 ZIP 后替换 JAR，但应用内没有可信签名校验。
- 发布仍使用 SHA-1；CI 下载 JRE 和 ffmpeg 时没有钉住 SHA-256。

改进要求：

- 普通发行版默认关闭源码插件。
- 插件采用显式启用、来源白名单、签名校验和权限警告。
- 签名更新完成前，更新功能只打开官方下载页。
- 发布使用 SHA-256；macOS 应用签名并公证，Windows 安装包签名。

### P0-4 文件路径和删除边界

- 远端标题和自定义命名格式可以产生目录分隔符，需要防止 `../` 等路径逃逸。
- 删除任务会清理 `.part`，但当前没有明确确认和影响范围说明。

改进要求：

- 写入、重命名、删除前校验 canonical path 必须位于下载根目录。
- 区分“移除任务”“删除临时文件”“删除成品”。
- 批量删除展示影响范围并二次确认，成品删除优先进入系统废纸篓。

## 4. 易用性和流畅度

### 4.1 目标用户流程

当前下载模式隐藏在配置菜单中，用户需要经过“查找 → 作品 Tab → 选规格 → 下载页”的多层流程。目标流程应收敛为：

1. 粘贴、拖入或从剪贴板识别 B站 URL。
2. 自动解析并在当前页展示标题、封面和分 P。
3. 直接选择视频/音频、质量、格式和保存目录。
4. 点击下载，在同一页看到队列、进度、速度和 ETA。

登录、字幕、弹幕、批量计划、代理和 Host 替换进入次级入口。

### 4.2 Swing 线程和布局

主窗口、详情线程和监控线程没有严格遵守 Swing EDT 模型，可能造成随机卡顿、重绘闪烁、状态竞争和难复现错误。窗口固定尺寸，大量布局依赖固定像素和占位 Label，不适合 Retina、高 DPI 和系统字体变化。

短期应把所有控件写操作收敛到 EDT；长期迁移到 JavaFX，并让 UI 只订阅任务状态。

### 4.3 查询与任务状态

- 所有下载链接查询使用单线程池，大收藏夹或多分 P 会逐个等待。
- 下载页通过固定周期全量轮询任务并直接刷新控件。
- 网络异常通常被吞掉并转化为泛化错误，用户不知道是否应重试、降级或重新登录。

目标状态机：

`QUEUED → PREPARING → DOWNLOADING → MERGING → SUCCEEDED`

并显式支持 `PAUSED / RETRYING / FAILED / CANCELLED`。

## 5. 推荐目标架构

推荐 JDK 21 + Gradle + JavaFX 21，保留现有 Java Parser/Downloader：

```text
JavaFX UI
  ↓ 命令与只读状态
Application Service
  ├─ ParseService
  ├─ DownloadQueue
  ├─ AuthService
  ├─ SettingsService
  └─ UpdateService
  ↓
Domain
  ├─ MediaSource / MediaItem
  ├─ DownloadTask / TaskState
  └─ Parser / Downloader 接口
  ↓
Infrastructure
  ├─ Bilibili HTTP Adapter
  ├─ FFmpeg Adapter
  ├─ SQLite Task Store
  ├─ OS Credential Store
  └─ Structured Logger
```

UI 控件不再作为任务标识；任务、下载器和界面之间通过稳定 task ID 与不可变状态快照通信。

## 6. 分阶段路线

### 阶段一：安全与工程基线

- 引入 Gradle、JDK 21、标准依赖声明和锁定。
- 删除敏感日志和 Trust All TLS。
- 凭据迁移到系统密钥库。
- 修复 fingerprint 无超时、路径逃逸和无确认删除。
- 禁用动态源码插件和不可信自更新。
- 建立基础单元测试、静态检查和 CI。

验收：新机器无需安装 Java；日志无凭据；网络失败可控；CI 可 clean build、test。

### 阶段二：好用的 MVP

- JavaFX 单页主流程。
- URL 粘贴、拖放、回车解析和剪贴板识别。
- 视频/音频、质量、格式、保存目录直接可见。
- 统一任务列表、进度、速度、ETA、暂停、继续、重试。
- 登录状态常驻可见，增加首次启动环境检查。

验收：从粘贴链接到下载不超过 3 次操作；网络和下载期间界面持续可响应；错误均有处理建议。

### 阶段三：稳定交付

- SQLite 持久任务、下载历史和自动恢复。
- 有界并发、限速、退避重试及 412/429 专项处理。
- 元数据和封面缓存，ffmpeg 进度与取消。
- DMG、MSI、Linux 包、签名更新、SBOM 和依赖扫描。
- Mock HTTP 契约测试和关键 UI E2E。

## 7. 产品验收指标

- 首次安装到成功下载，普通用户 3 分钟内完成。
- 再次使用时，从粘贴 URL 到开始下载不超过 3 次操作。
- 常规机器约 2 秒显示主界面，耗时检查在后台执行。
- UI 主线程不执行网络、文件下载或 ffmpeg 阻塞操作。
- 任务暂停或重启应用后可以恢复。
- 日志不出现 Cookie、token、CSRF 或完整签名 URL。
- 输出路径不能逃逸用户选择的下载目录。
- 默认不自动点赞、不关闭 TLS 校验、不绕过 DRM。
- 明确提醒仅下载用户有权保存的内容，并遵守平台规则和版权要求。

## 8. 首轮实施范围

首轮保持 Java 8 和现有 Swing 下载能力可用，先完成：

- 敏感日志清理。
- Cookie/fingerprint 文件权限收紧。
- fingerprint 请求超时。
- 输出路径 containment 校验。
- 删除任务确认。
- 主页直接选择下载模式、回车解析。
- 打开下载目录行为修复。

JDK 21、Gradle、JavaFX 和系统密钥库迁移作为后续独立变更实施，以控制回归范围。

## 9. 首轮实施结果

截至 2026-07-30，首轮改造已完成以下内容：

- 配置初始化只输出配置 key；移除 fingerprint、登录授权信息、请求 payload 和 Cookie 刷新完整响应等敏感日志。
- Cookie 与 fingerprint 文件在读写时收紧为仅当前用户可读写；现有本地文件已调整为 `0600`。
- fingerprint 首次请求增加 10 秒连接与读取超时，并确保连接关闭。
- 最终文件重命名增加 canonical path containment，拒绝 `../` 等目录逃逸。
- 主页增加“视频+音频 / 仅视频 / 仅音频”选择，URL 输入框支持回车解析。
- 移除单个或全部任务前增加确认，明确告知会删除未完成的 `.part` 临时文件、不会删除成品。
- macOS/Linux 的“打开文件夹”调整为打开成品所在目录。
- 新增不依赖 JUnit 的安全回归测试，覆盖合法路径、目录逃逸拒绝和敏感文件权限。

验证证据：

- `bash package.sh` 构建通过。
- `ResourcesUtilSecurityTest` 通过。
- 新 JAR 在 headless 模式执行 `-v` 通过，输出 `v6.41`；配置初始化日志只列 key，不再输出 key 对应的配置 value。
- `git diff --check -- src docs` 通过。

尚未完成或尚未验证：

- 未在本轮执行真实 B站解析、音频下载、视频下载和完整 GUI 手工验收。
- 全局 Trust-All TLS、不可信旧自更新、SHA-1 发布链和动态源码插件均已默认禁用。
- Swing EDT 违规、固定周期轮询、查询单线程和错误处理分散仍是流畅度与稳定性风险。
- Cookie 尚未迁移到系统密钥库；当前 `0600` 只是过渡保护。
- Gradle/JDK 21 双平台 CI 构建基线已完成；Java 8 手工构建暂时保留为回退路径。

## 10. 双平台维护基线

后续产品目标明确为 Windows 与 macOS 双平台良好运行。当前工程迁移采用以下约束：

- JDK 21 作为统一构建工具链，源码字节码兼容级别暂时保持 Java 8，降低首轮迁移风险。
- Gradle Wrapper 固定构建版本，并校验发行包 SHA-256；不要求用户全局安装 Gradle。
- Maven Central 统一声明现有第三方依赖，Gradle dependency locking 固定解析结果，并用 SHA-256 校验依赖内容。
- GitHub CI 在 `windows-latest` 与 `macos-latest` 上分别执行 clean build、安全回归测试和 headless 启动烟测。
- `package.sh` 与现有 Java 8 构建暂时保留为过渡回退路径；双平台 Gradle 基线稳定后再删除。

这一阶段只迁移工程基础，不同步重写 Parser、Downloader 或 Swing UI。后续双平台工作包括 ffmpeg 自动探测与随包分发、系统凭据存储适配、Windows/macOS 安装包以及平台 UI 验收。

## 11. TLS 安全基线

JDK 21 双平台构建通过后，应用已删除全局 Trust-All TLS 实现和 `bilibili.https.allowInsecure` 配置入口：

- HTTPS 请求始终使用 JVM 默认信任库，不再允许全局跳过证书链校验。
- SMTP 不再注入信任所有主机的 socket factory，并显式启用服务端主机名校验。
- STARTTLS 配置键修正为 `mail.smtp.starttls.enable`，同时兼容旧版误拼写键。
- 旧配置文件中的 `bilibili.https.allowInsecure` 会被忽略，并在下次保存配置时清理。
- 新增 `TlsSecurityTest`，防止后续重新暴露不安全配置或自定义 Trust-All socket factory。

剩余验证：真实 B站登录/下载和真实 SMTP 发送需在 JDK 21 的 Windows 与 macOS GUI 环境手工验收。

## 12. Windows / macOS ffmpeg 探测

应用启动时按确定顺序查找 ffmpeg：用户配置路径、应用目录、系统 `PATH`，以及 Homebrew、MacPorts、WinGet、Chocolatey 的常见安装位置。每个候选都通过不经 shell 的 `ffmpeg -version` 验证，并设置 3 秒超时、检查退出码，避免启动过程无限等待或把失败命令误判为可用。

macOS 与 Windows 缺失提示分别给出 Homebrew 和 WinGet 安装命令。旧有 Windows/Linux 自动下载已迁移到固定 SHA-256 清单，并改为暂存下载、校验后原子安装；摘要缺失或不匹配时安全失败，详见 `docs/FFMPEG_BINARY_MANIFEST.md`。

剩余发布风险：ffmpeg 二进制仍托管在原上游 Release，本仓库尚无自有 JDK 21 runtime、DMG/MSI、代码签名和公证。旧应用自更新、Release/MSI、Pre-release 和第三方上传链已安全禁用；完全独立发布前需要按 `docs/SECURE_RELEASE_MIGRATION.md` 迁移到本仓库受控资产。

## 13. 旧更新与发布链隔离

应用内更新策略改为 fail-closed：“检查更新”只显示本维护仓库 Releases 地址，不再联网查询或下载；正式版 ZIP、Beta artifact、解压替换 JAR 和重启更新的兼容入口均显式拒绝执行。旧更新源配置和 UI 选择项已移除。

四个历史发布工作流及其 Release/MSI/第三方同步脚本已替换为带 `LEGACY_PIPELINE_DISABLED` 标记的安全失败占位，避免手动或路径提交误触发。新的发布设计必须使用 JDK 21 `jpackage`、本仓库受控资产、SHA-256、最小权限与人工 Release 门禁。

这一阶段没有创建 Release，也没有向任何第三方上传内容。真实 Windows/macOS GUI、B站下载与安装包仍未验证，因此项目尚未达到完全独立发布条件。

## 14. 外部源码插件隔离

启动阶段不再扫描、编译或加载应用目录下 `parsers/`、`pushers/` 中的 Java 源码、字节码和顺序配置。旧 `Plugin`、`CustomClassLoader` API 改为 fail-closed 兼容占位，不提供配置或环境变量绕过入口；内置解析器、下载器和推送器继续由应用自身 ClassLoader 扫描。

新增安全回归覆盖旧编译 API、外部字节码加载、危险实现静态扫描以及内置 BV 解析器、M4S 下载器和消息推送器的正常发现。未来若恢复插件能力，必须采用独立低权限进程、版本化 IPC、最小数据暴露、SHA-256 和签名验证，不能直接恢复同 JVM 类加载，详见 `docs/PLUGIN_SECURITY.md`。

真实自定义插件迁移、Windows/macOS 进程隔离方案尚未实现；当前产品选择安全地不支持第三方插件。
