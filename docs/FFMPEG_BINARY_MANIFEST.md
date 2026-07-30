# ffmpeg 二进制完整性清单

应用保留的旧版自动下载仅覆盖 Windows/Linux 的 amd64、arm64。二进制版本为 `ffmpeg-20240123`，来源为原项目 GitHub Release `V4.5`。

2026-07-30 迁移 SHA-256 时，四个远端文件均先通过旧代码中既有 SHA-1 值的连续性核验，再计算以下 SHA-256：

| 平台 | 文件 | 字节数 | SHA-256 |
|------|------|-------:|---------|
| Linux amd64 | `ffmpeg-20240123-linux-amd64` | 3,709,312 | `aa12dbb9636129f658a869b3600ec835f850a000a4d14e16411e38d37ce00d68` |
| Linux arm64 | `ffmpeg-20240123-linux-arm64` | 3,213,616 | `9ac9288410fdb7ee9b31db88d07b4e2af4fff24d90fd7cfbd9a627cbbd786bd1` |
| Windows amd64 | `ffmpeg-20240123-win-amd64.exe` | 3,748,864 | `48fd5da3bf9d628c2f065fdf3f0fdc908ef86441ede1b26be9bc0e1da9a67505` |
| Windows arm64 | `ffmpeg-20240123-win-arm64.exe` | 2,995,200 | `23d66785e463a07ab548559209496008f2d29611a6fa3d4c344daa9b97024063` |

下载文件先写入不可作为 ffmpeg 候选执行的 `.download` 暂存路径。只有 SHA-256 匹配后才会设置执行权限并原子移动到最终路径；摘要缺失或不匹配时拒绝安装并删除暂存文件。

macOS 没有对应的可信旧版二进制清单，因此不启用自动下载，继续使用 Homebrew、MacPorts 或用户配置的 ffmpeg。
