# GitHub 敏感信息与隐私审查

审查日期：2026-07-31
审查仓库：`silentax/BilibiliDown`（public fork）

## 结论

当前源码、配置值、应用 JAR 和 GitHub Actions 日志中未发现仍然有效的 B站 Cookie、用户名、密码、推送 token、私钥或 provider token。

确认存在并已在当前版本治理的公开隐私内容：

- 旧预览截图包含 B站昵称/头像、登录二维码和本机目录。
- 运行时 `config/app.config` 与发布目录配置曾处于 tracked 状态，未来可能误提交用户本地保存的账号或 token。
- 一个未参与当前 Gradle 构建的旧 JUnit 文件包含 Cookie 形态的短占位数据，会造成误报并鼓励复制真实 Cookie。
- 旧 Linux desktop 文件包含上游开发环境绝对路径。
- 独立维护阶段的 13 个提交使用非 GitHub-noreply 作者邮箱；另有一个孤立的空分支提交使用非 noreply 邮箱。

## 审查证据

- 扫描 `origin/master` 的 848 个历史提交。
- 检查 GitHub 上 2 个公开分支和首批 82 个标签。
- GitHub Secret Scanning 已启用，Push Protection 已启用，开放告警为 0。非 provider 模式和有效性检查经 API 重试后仍保持 disabled，属于当前仓库能力限制。
- 扫描 12 次 GitHub Actions run，共 12853 行日志，未命中个人目录、B站 Cookie、私钥或未遮罩 Bearer token。
- 扫描本地兼容包、tracked 发布 JAR 和 Gradle JAR，敏感 entry 命中数均为 0。
- 非注释敏感配置键在历史中只有空值；旧测试中的 Cookie 形态数据长度明显不符合真实 B站认证 Cookie。

审查过程只记录类型、位置、长度和计数，没有把命中的值复制到报告、日志或新文件。

## 当前版本修复

- 删除整套过时 `release/preview/` 资产。
- 解除运行时 `config/app.config`、`release/config/app.config` 的 Git 跟踪，保留 `src/resources/app.config` 作为无凭据默认模板。
- 删除旧 Cookie 占位测试和生成型 Linux desktop 文件。
- 扩充 `.gitignore`，覆盖 Cookie、fingerprint、临时配置、环境文件、私钥和密钥库。
- 新增 `RepositoryPrivacyTest`，扫描 tracked 文件名、文本内容、敏感配置值和本机路径，并纳入 Gradle `check`。
- 移除启动、下载目录、lock 文件和 hosts 配置诊断中的完整本机路径输出，降低用户分享日志时的隐私风险。
- CI 增加提交作者邮箱隐私门禁，要求后续提交使用 GitHub noreply 地址。
- 仓库级 Git 作者邮箱已切换为 GitHub noreply；仅含空 README 的孤立 `deleted` 远端分支已删除，不影响 `master`。

## 历史与 fork 边界

普通删除提交只能清理当前分支；旧截图和作者邮箱仍存在于 Git 历史。彻底清理需要重写提交、强制推送、删除旧标签/分支，并会改变现有提交哈希和使旧 CI/artifact 链接失去对应关系。

此外，本仓库仍属于 GitHub fork network。部分上游对象即使从本 fork 的引用中移除，也可能继续通过公开上游或 GitHub fork 对象网络访问。若目标是形成干净、可独立发布的隐私边界，推荐以当前已清理快照创建新的 standalone 仓库，再归档本 fork；不要直接把当前 fork 的 848 个上游提交复制过去。

历史重写、创建新 standalone 仓库和归档当前 fork 均未在本次自动执行，需单独确认迁移窗口和保留策略。
