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
- 解析、下载卡片和监控主链路的 Swing EDT 违规已在后续核心体验里程碑修复；作品详情固定像素布局、查询限流策略和其他次要窗口的线程模型仍需继续治理。
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

## 15. 核心流畅度与任务反馈里程碑

截至 2026-07-31，Swing 主链路完成第一轮线程模型治理，保持 Java 8 字节码兼容且不增加运行时依赖：

- 新增统一 EDT 调度工具。主窗口、详情结果、下载卡片、下载监控快照、提示对话框和登录用户信息都只在 EDT 创建或更新。
- 主窗口创建后立即显示；联网校时、Cookie 刷新等待、登录、ffmpeg 探测、下载仓库初始化和解析器预扫描移到后台启动线程，主页持续显示启动阶段反馈。
- 主页点击解析后立即显示作品页和“正在解析”状态；ID 识别、作品查询与预览图加载在后台执行，解析期间禁止重复查找，失败时保留明确的失败页和提示。
- 下载任务提交后立即出现“正在获取下载地址”卡片，下载页增加“准备”计数；全部入口统一经过任务调度器，线程池拒绝和地址查询失败均有可见反馈。
- 下载监控改为“后台读取不可变快照 → EDT 合并提交”，同一时刻最多保留一个待提交快照，避免任务多时累积 UI 刷新；控件值未变化时不再重复触发重绘。
- 下载速度使用 `long` 计算并按 B/s、KB/s、MB/s、GB/s 显示，同时根据当前文件剩余字节展示 ETA；准备、队列、重试、下载、转码、暂停、完成状态均可区分。
- “全部暂停”和打开文件/目录等可能调用系统 I/O 的动作移到后台线程，Windows 保留 Explorer 选中文件，macOS 使用 Desktop 打开所在目录。
- 新增 `uiExperienceTest`，在 headless 环境验证后台线程确实切换到 EDT、异常能够回传、速度大于 2 GB/s 时不发生 `int` 截断，以及 ETA 格式化。

本里程碑的自动验收命令：

```bash
./gradlew --no-daemon --offline -PjavaToolchainVersion=8 clean check
bash package.sh
java -Djava.awt.headless=true -jar build/libs/INeedBiliAV.jar -v
```

仍需人工验收：真实 Windows/macOS GUI 连续操作、真实 B站解析与音频/视频下载、暂停/继续/失败重试、ffmpeg 合并和登录流程均为 `NOT VERIFIED`。窗口布局和查询并发在下一里程碑继续处理；系统密钥库、签名安装包及真实双平台发布验收仍在后续路线中。

## 16. 双平台窗口与查询吞吐里程碑

截至 2026-07-31，主窗口进一步采用 Windows/macOS 原生桌面行为，并移除下载地址查询的硬编码单线程瓶颈：

- 主窗口恢复系统原生标题栏和菜单栏，支持平台原生拖拽缩放、最大化、窗口快捷键和高 DPI 字体；macOS 启用屏幕菜单栏与应用名称。
- 默认主题改为系统 Look and Feel，不再把全部 UI 字体强制覆盖为固定 12px；已有用户显式配置 `default` 主题时仍保持原选择。
- 原生关闭、最小化与系统托盘行为统一：窗口关闭仍遵守“关闭到托盘”，托盘“退出”则执行真正退出，并继续保留活动任务确认。
- 主页搜索区改为可伸缩 GridBag 布局；下载页改为 BorderLayout，工具栏、滚动区和任务卡片会随窗口宽度重新分配空间。
- 主页和下载页背景绘制不再从 `paintComponent` 内再次调用 Swing UI delegate，避免重复绘制和潜在递归。
- 下载地址查询默认并发数为 2，可通过 `bilibili.download.query.poolSize` 配置；程序把异常值限制在 1–4，并使用最多 256 个等待任务的有界队列，降低大量分 P 串行等待，同时避免请求突发和无限队列。
- 下载历史仓库增加线程安全的惰性初始化，确保后台启动尚未完成时并发查询不会因仓库状态未就绪而失败。
- `uiExperienceTest` 增加响应式布局类型、并发上下限、双工作线程启动和队列满载拒绝回归。

仍需人工验收：原生窗口在真实 Windows/macOS 的缩放、最大化、系统托盘和多显示器行为，以及大量真实分 P 查询对 B站限流策略的影响均为 `NOT VERIFIED`。作品详情页内部仍保留部分固定像素布局，是下一轮响应式改造重点。

## 17. 作品详情页响应式与渐进反馈里程碑

截至 2026-07-31，作品详情页移除依赖大量空白占位组件和 1150px 固定画布的旧布局，并继续收紧解析后的 EDT 工作量：

- 作品标题、ID、简介和操作区改为 BorderLayout/GridBagLayout；预览与分集列表使用可拖动 JSplitPane，窗口缩放时会重新分配空间。
- 操作按钮不再强制固定像素尺寸，交由系统 Look and Feel 根据 Windows/macOS 字体和 DPI 计算；详情区边框改为中性系统风格。
- 页面增加独立的解析、分集卡片生成、成功、空结果和失败状态；加载期间下载按钮禁用，完成后显示实际分集数量。
- 大量分集卡片每 12 个为一批提交到 EDT，批次之间主动让出事件循环，避免一次生成全部 Swing 组件造成长时间无响应。
- 封面下载与作品详情解析解耦，作品信息和分集可以先显示；封面只允许 HTTP/HTTPS，设置 10 秒连接和 15 秒读取超时，并使用请求序号防止旧图片覆盖用户新选择。
- 预览图按当前可用空间保持宽高比绘制，不再预先缩放到固定 700×460；长按分集切换封面也统一走后台加载，不再在 EDT 发起网络请求或依赖父组件层级反查页面。
- 修复分集标题单击时尝试复制空文本的问题；当前改为双击复制，单击不会触发异常。
- `uiExperienceTest` 新增作品页布局、分隔面板、加载进度、分集生成进度、成功/失败按钮状态及分集卡片布局回归。

仍需人工验收：真实 Windows/macOS 下的窗口窄化、分隔条拖动、系统字体缩放、超长标题、数百个真实分 P、封面 CDN 超时及连续切换预览图行为均为 `NOT VERIFIED`。作品详情与下载主链路已具备自动回归保护，后续重点转向设置页、登录窗口和真实端到端下载验收。

## 18. 登录窗口响应式与凭据生命周期里程碑

截至 2026-07-31，密码登录和短信登录完成响应式布局、后台网络调度与凭据生命周期治理：

- 两个登录窗口恢复 Windows/macOS 系统原生标题栏，改用 BorderLayout/GridBagLayout 和系统 Look and Feel 尺寸计算；移除绝对定位、自绘关闭按钮和自定义拖动。
- 获取极验验证码、发送短信和短信登录均在后台线程执行；本地 HTTP 回调线程只处理网络请求，所有 Swing 状态经 SwingDispatch 回到 EDT，操作期间按钮和输入框提供明确忙碌状态。
- 密码框不再使用 `123456` 伪默认值，也不再通过 `Global` 或配置文件长期保存密码。密码只以 `char[]` 暂存，并在登录成功、失败、异常、验证码替换或窗口关闭时覆盖清零。
- 暂存密码与本次验证码 token 绑定，旧验证码回调不能消费新密码；窗口关闭后丢弃迟到的验证码和登录 UI 回调。
- 旧 `bilibili.user.password` 配置被标记为废弃：读取时忽略，后续保存配置时清理，避免历史明文密码继续生效。
- 密码登录与短信登录的意外运行时异常会转换为可见失败状态，并在 `finally` 中恢复任务标记和控件，不再让窗口永久停留在禁用状态。
- 本地回调在登录窗口已关闭、验证码失效或短信发送失败时返回明确错误码；错误消息进行 JSON 转义，不再把失败误报为成功。
- 登录窗口关闭时异步停止仅监听 loopback 的 SocketServer；即使服务器尚未完成端口绑定也可安全停止，避免关闭窗口阻塞 EDT。
- 新增 `CredentialSecurityTest` 并纳入 Gradle `check`，覆盖旧密码配置清理、全局密码字段移除、登录窗口旧布局/凭据模式防回退、窗口关闭回调错误响应和服务器绑定前关闭。

本里程碑的自动验收命令：

```bash
./gradlew --no-daemon --offline -PjavaToolchainVersion=8 credentialSecurityTest uiExperienceTest
./gradlew --no-daemon --offline -PjavaToolchainVersion=8 clean check
bash package.sh
```

以上命令已通过；Gradle 完整检查共执行 15 个任务，JAR headless smoke test 输出 `v6.41`，兼容打包脚本成功。真实密码登录、短信发送与登录、浏览器极验回调，以及登录窗口在真实 Windows/macOS 的缩放、系统字体和原生关闭行为仍为 `NOT VERIFIED`。B站可能继续调整登录接口或风控策略，失败时应优先使用二维码登录；下一轮重点治理设置页的固定宽度布局、组件三元组绑定和 EDT 同步保存。

## 19. 设置页响应式与非阻塞保存里程碑

截至 2026-07-31，设置页完成固定画布、脆弱组件索引绑定和 EDT 同步文件写入治理：

- 页面从 1150px 固定 FlowLayout 迁移到 BorderLayout/GridBagLayout；设置内容实现 Scrollable 并跟随 viewport 宽度，长路径和窗口缩放不再依赖水平固定画布。
- 每个配置项使用独立 SettingBinding 绑定 key、说明、编辑器、路径选择按钮和初始值；保存不再假设组件必须按“标签/输入/空白”三元组排列。
- 筛选直接控制配置行可见性，不再销毁并重建全部 Swing 组件，因此筛选、清空后仍保留未保存的修改；修改项会即时高亮。
- 同一时间只允许打开一个设置页；保存期间禁用编辑、筛选、重置和关闭，避免重复写入或关闭后丢失反馈；关闭未保存页面前提供明确确认。
- 推送密码或凭证在设置页使用密码框遮罩显示，读取临时 `char[]` 后立即覆盖清零；系统密钥库迁移仍作为后续独立安全任务。
- 保存时先在 EDT 采集不可变配置快照，再由 `Thread-SettingsSave` 后台写盘；完成后经 SwingDispatch 回到 EDT 更新全局配置、控件状态和成功/失败提示。
- ConfigUtil 增加快照保存入口并串行化文件替换；临时配置从创建起收紧为 owner-only 权限，优先原子替换，文件系统不支持时安全回退为 replace-existing，不再先删除旧配置。
- `uiExperienceTest` 增加设置页 BorderLayout/GridBagLayout、viewport 宽度跟随、显式 editor binding、固定 1150px 防回退、组件三元组防回退和后台保存线程检查。

自动验收命令：

```bash
./gradlew --no-daemon --offline -PjavaToolchainVersion=8 uiExperienceTest securityTest
./gradlew --no-daemon --offline -PjavaToolchainVersion=8 clean check
bash package.sh
```

仍需人工验收：真实 Windows/macOS 下设置页窄窗口、长中文说明、超长目录、系统字体缩放、文件/目录选择器、只读配置目录保存失败，以及关闭未保存页面的确认交互均为 `NOT VERIFIED`。设置保存后依旧遵循现有语义：部分运行时配置需要重启应用才会生效。

## 20. 下载协议契约与运行日志隐私里程碑

截至 2026-08-04，默认单线程下载主链增加不依赖外部网络的 loopback HTTP 契约测试，并修复隔离 GUI 验收发现的本机路径日志：

- 新增 `httpContractTest` 并纳入 Gradle `check`，使用本地 Mock HTTP 服务覆盖 GET、POST、HTTP 412、首次下载、标准 Range 续传、服务器忽略 Range、响应截断和下载路径逃逸。
- 断点文件存在但服务器返回完整响应时，不再把完整文件追加到 `.part` 后造成静默损坏；当前会清空临时文件并重新发起一次无 Range 的完整请求。
- 下载只接受 2xx 响应；响应声明长度与实际文件长度不一致时保留 `.part` 并返回失败，不再递归重试或发布不完整成品。
- 完成文件优先使用原子移动；文件名通过 canonical path containment 校验，拒绝写出保存目录。
- 下载失败日志不再输出完整异常栈、请求 Range 或服务端错误正文，降低签名 URL、响应内容和本机路径进入共享日志的风险。
- 应用启动不再输出完整 JAR 路径或用户指定数据目录路径，`RepositoryPrivacyTest` 增加对应防回退检查。

自动验收命令：

```bash
./gradlew --no-daemon --offline -PjavaToolchainVersion=8 httpContractTest repositoryPrivacyTest
./gradlew --no-daemon --offline -PjavaToolchainVersion=8 clean check
```

本地契约与隐私专项测试已通过。真实 B站 CDN、下载暂停/恢复、ffmpeg 合并和登录态下载仍需使用公开测试资源人工验收；默认关闭的实验性多线程下载实现 `HttpRequestUtilEx` 尚未纳入本轮契约测试，应在重新启用前单独治理其 Range 校验、线程等待、取消和分片合并边界。

## 21. JDK 21 与真实下载验收里程碑

截至 2026-08-04，macOS arm64 本机已安装 Homebrew OpenJDK 21.0.12，并完成自动检查、视频下载和仅音频下载验收；系统默认 Java 仍保持原 Java 8，未修改 shell 配置：

- JDK 21 下 `clean check jar` 全部通过，打包 JAR 的 headless smoke test 输出 `aarch64 / Java 21 / v6.41`。
- 第一条公开视频完成 GUI 主链验收：成功解析并下载视频/音频 M4S，ffmpeg 合并和最终重命名成功；`ffprobe` 确认为约 298 秒、44.4 MB 的 1080p HEVC + AAC 文件。
- 新增默认不接入 `check` 的 `manualE2e` 任务。任务要求显式传入无 query/fragment 的 canonical URL，使用隔离数据目录，只处理第一个分 P，并支持 `all`、`video`、`audio` 三种模式；不会保存 URL、自动点赞、播放提示音或写入下载历史。
- 第二条公开视频在 `audio` 模式完成两次真实网络验收，ffmpeg 转封装成功；最终文件为 AAC、44.1 kHz、双声道，时长约 229.37 秒，大小约 4.60 MB。
- 首次真实验收发现完整 API 响应、签名 URL、设备字段和本机 ffmpeg 路径可能进入控制台。日志出口现统一省略结构化响应、查询参数和本机绝对路径，并限制超长日志；外部进程输出也统一经过该出口。
- 第二次音频验收进一步发现查询参数内嵌 JSON 引号会让旧脱敏规则提前终止。当前规则会清除 URL 从 `?` 到空白边界的全部内容，WBI 签名材料也只记录刷新阶段；新增回归用例防止设备字段和签名值再次残留。

手动仅音频复验命令：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew --no-daemon --offline manualE2e \
  -Pe2eUrl=https://www.bilibili.com/video/BV.../ \
  -Pe2eMode=audio \
  -Pe2eDataDir=/path/to/an/isolated-directory
```

剩余真实验收：Windows JDK 21 GUI、登录态/会员资源、暂停与继续、失败重试、批量多分 P、系统凭据存储、DMG/MSI 签名安装包仍为 `NOT VERIFIED`。JDK 21 编译还会报告 `HttpRequestUtilEx` 对 `Long downloadedFileSize` 加锁的 3 条告警；该实验性多线程路径默认关闭，应作为独立并发安全专项处理，不在本次默认 M4S 下载验收中扩大重构。

## 22. 多线程下载协议与恢复安全里程碑

截至 2026-08-04，默认关闭的 `HttpRequestUtilEx` 已完成并发、Range、暂停恢复和发布边界治理：

- 下载进度改用 `AtomicLong`，总大小具备跨监控线程可见性；JDK 21 不再报告对值类型 `Long` 加锁的 3 条并发告警。
- 启动分片前使用 `bytes=0-0` 探测服务端能力，只在收到匹配的 HTTP 206、`Content-Range` 和响应长度时启用多线程；服务端忽略 Range 时安全回退单线程。
- 分片线程数限制为 16，并改用 `CountDownLatch` 等待，避免线程先结束造成永久等待；任一分片失败、主线程中断或用户暂停时都不会进入合并发布。
- 每个分片严格核对状态码、起止偏移、资源总大小、响应长度和最终文件长度；412、416、错误 `Content-Range`、截断响应均保留可续传分片并返回失败。
- `.part.meta` 只保存格式版本、资源总长度和分片数，不保存下载 URL。线程数或总长度改变时会废弃不兼容旧分片，避免按错误布局拼接。
- 全部分片先合并到 `.part.merge`，验证总长度后再原子发布成品；发布前失败不产生可见成品，发布后临时文件清理失败只告警、不把已成功任务误报为失败。
- “移除任务”现在会按真实下载目标删除 `.part`、`.partN`、布局元数据和合并暂存文件，不删除成品或其它任务文件。
- 新增 `httpMultiThreadContractTest` 并纳入 Gradle `check`，覆盖首次分片、真实续传、布局变化、Range 回退、错误区间、截断后重试、412/416、暂停后继续、线程上限和路径逃逸。

真实 macOS arm64 / JDK 21 验收：

- 公开音频资源使用 3 线程完成 CDN 分片与 ffmpeg 转封装，产物为 AAC、44.1 kHz、双声道，约 229.37 秒、4.60 MB。
- 公开视频使用 3 线程依次下载视频和音频，进度重置及 ffmpeg 合并成功，产物为 1920×1080 HEVC + AAC，约 297.89 秒、44.42 MB。
- 两次隔离验收均未残留 `.partN`、`.part.meta` 或 `.part.merge`；日志未出现完整查询参数、结构化响应或本机绝对路径。

手动多线程复验可在原 `manualE2e` 命令后增加 `-Pe2eThreads=3`。该参数默认值仍为 `0`，产品默认配置也继续关闭多线程；在 Windows 实机和真实 GUI 暂停/继续完成前，不把该实验能力改为默认开启。

## 23. 统一任务状态与暂停重试里程碑

截至 2026-08-04，下载卡片、队列任务和底层下载器之间增加独立、线程安全的任务生命周期，解决监控轮询、手动继续和自动重试可能重复提交的问题：

- 用户可见状态统一为 `PREPARING / QUEUED / DOWNLOADING / PAUSED / RETRYING / MERGING / SUCCEEDED / FAILED / CANCELLED`；底层 `StatusEnum` 继续只描述协议下载器内部状态，避免 UI 再从六个传输状态猜测完整生命周期。
- 每次进入下载线程池都必须取得唯一提交令牌。暂停、取消、手动重试会使旧令牌失效，因此已排队或正在结束的旧任务不能再次开始下载，也不能覆盖新任务的状态。
- 自动重试从 `MonitoringThread` 的 1.5 秒轮询副作用中移出，改由单独的 daemon 调度器执行；默认等待 3 秒，可通过 `bilibili.download.retry.delaySeconds` 调整。卡片显示剩余秒数、当前次数和最大次数，并提供“立即重试”。
- 手动立即重试会取消旧的定时重试资格；同一任务的重复点击只能有一个有效提交。全部暂停会同时使排队、下载中和等待重试的任务失效，继续后保留底层 `.part` / 分片续传能力。
- 下载线程池关闭或拒绝任务时不再移除卡片，任务进入 `FAILED` 并显示 `QueueUnavailable`；失败信息只保留长度受限的错误类型和处理建议，不显示 URL、响应正文或异常详情。
- 下载链接仍按原策略在手动重试或队列等待超过有效期后重新查询；清晰度改变会以 `QualityChanged` 安全失败，避免用已变化的媒体布局继续旧分片。
- 下载页汇总新增独立“重试”计数；`MonitoringThread` 只读取任务快照和发布 UI 更新，不再直接调用 `continueTask()`。下载线程检测到 ffmpeg 处理阶段时映射为 `MERGING`。
- 下载优先队列比较器改用 `Long.compare` / `Integer.compare`，修复时间戳强制转 `int` 的溢出风险和手动重试比较结果不对称的问题。

自动回归新增状态转换、首次/定时/手动提交去重、暂停与取消令牌失效、重试次数和截止时间、队列拒绝、调度器执行、失败信息过滤、过期 URL 重查防回退检查。以下两套完整验证均通过，每套执行 21 个任务：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew --no-daemon --offline -PjavaToolchainVersion=8 clean check jar manualE2eClasses

JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew --no-daemon --offline clean check jar manualE2eClasses
```

仍需人工验收：真实 macOS/Windows GUI 中对排队任务、下载中任务和等待重试任务分别执行暂停/立即重试/全部暂停；断网后恢复、B站签名 URL 过期重查、ffmpeg 合并期间暂停的产品语义也仍为 `NOT VERIFIED`。自动检查已证明过期任务不会重复执行，并复用了里程碑 20/22 的 loopback Range、截断、412/416 和暂停续传契约；本里程碑没有启用默认多线程，也没有引入任务持久化或 SQLite。

## 24. 界面视觉与交互改造里程碑

截至 2026-08-04，针对用户反馈的三项界面问题完成改造：主页背景难看、下载页排版不自适应、预览页缺少批量选择。

主页（TabIndex）：

- 移除重复平铺背景图，改为代码绘制的浅蓝-淡紫-樱粉纯净渐变，叠加柔和光晕、波浪和星点装饰。
- 标题从图片改为文字+副标题+功能引导行，整体使用卡片式布局和 AnimeUi 配色。
- 搜索输入框和按钮统一应用 AnimeUi 样式。

预览页（ClipInfoPanel + TabVideo）：

- 每个分集从固定尺寸卡片改为紧凑列表行（560×76），左侧复选框 + 分集编号徽章，中间标题和元数据，右侧操作按钮。
- 新增批量选择工具栏：全选 / 取消全选 / 反选 / 下载所选，实时显示已选数量。
- 单击分集行加载预览图，双击下载。选中行高亮为强调色。
- 分集列表行高从 178px 调整为 84px，匹配新的紧凑布局。

下载页（TabDownload + DownloadInfoPanel）：

- 移除平铺背景图，应用与主页一致的 AnimeUi 渐变背景。
- 下载任务卡片从 GridBagLayout + 红色调试边框改为 BorderLayout 紧凑行（76px），使用 AnimeUi 卡片边框和配色。
- 任务行布局：左侧文件名（粗体）+ 状态文字，下方副标题 + 进度文字，右侧操作按钮区（暂停/继续、打开文件、打开文件夹、删除任务）。
- 状态栏和功能按钮统一应用 AnimeUi 样式。
- 任务行高从 128px 缩减为 84px，提升单屏可见任务数。

新增 `AnimeUi` 视觉基线类，集中管理配色、渐变背景、卡片边框和按钮样式，供各页面统一调用。

以下两套完整验证均通过，每套执行 21 个任务：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew --no-daemon --offline -PjavaToolchainVersion=8 clean check jar manualE2eClasses

JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew --no-daemon --offline clean check jar manualE2eClasses
```

仍需人工验收：真实 GUI 中主页渐变效果、预览页批量选择下载流程、下载页任务行在不同窗口尺寸下的自适应表现。

布局修复：下载页和预览页的任务列表从 `GridLayout` 改为 `BoxLayout`（Y 轴），使每个任务行/分集行保持固定 76px 高度，不再被拉伸填满容器。下载页 `addTaskPanel` / `removeTaskPanel` 同步管理间距占位符；预览页渲染分集时在每个 `ClipInfoPanel` 后插入 8px 垂直间距。

真实 GUI 验收（macOS arm64 / JDK 21）：
- 主页渐变背景、标题区、搜索栏样式正常。
- 单分 P 视频解析后分集行保持紧凑高度，不再平铺。
- 多分 P 视频分集列表正常排列，批量选择工具栏（全选/取消全选/反选/下载所选）功能正常。
- 下载页任务行紧凑显示，完成后不再平铺，按钮整齐排列。
- 视频下载成功，ffmpeg 合并正常，产物可播放。

## 25. jpackage 原生安装包里程碑

截至 2026-08-04，使用 jpackage 生成内置 JRE 的平台原生安装包，用户无需自行安装 Java。

Mac (.dmg):

- `scripts/build-mac.sh` 构建脚本，产出 `build/dist/BilibiliDown-6.41.0.dmg` (约 57MB)。
- 包含完整 .app 包：原生启动器、内置 JRE (jlink 裁剪)、fat JAR、.icns 图标。
- 双击 .dmg 拖入 Applications 即可使用。
- 应用数据目录自动回退到 `~/Library/Application Support/BilibiliDown`。

Windows (.exe):

- `scripts/build-windows.bat` 构建脚本，需在 Windows 上运行，需安装 Inno Setup 6+。
- 产出 `build/dist/BilibiliDown-6.41.0.exe`，内置 JRE，从开始菜单启动。
- 应用数据目录自动回退到 `%APPDATA%/BilibiliDown`。

核心改动:

- `ResourcesUtil.baseDirectory()` 新增只读目录检测：当 JAR 所在目录不可写时（jpackage 安装包环境），自动回退到平台对应的用户数据目录。
- `release/INeedBiliAV.jar` 更新为最新 fat JAR。
- `release/Double-Click-to-Run-for-Win.bat` 修复：从 `launch.jar` 改为 `INeedBiliAV.jar`。
- 新增 `docs/BUILD.md` 构建与打包指南。

验证:

- Java 8 toolchain + JDK 21 各 21 项全部通过。
- Mac .dmg 构建成功，挂载验证包含完整 .app 包、Applications 快捷方式、内置 JRE。
- jpackage 不能交叉编译，Windows .exe 需在 Windows 机器上构建。
