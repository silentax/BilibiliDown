# INeedBiliAV - BilibiliDown
> 本仓库为个人持续维护版本，不自动同步上游。当前优先改进安全性、易用性、构建质量和桌面端使用体验。

![语言java](https://img.shields.io/badge/Require-java-green.svg)
![支持系统 Win/Linux/Mac](https://img.shields.io/badge/Platform-%20win%20|%20linux%20|%20mac-lightgrey.svg)
![测试版本64位Win10系统, jre 1.8.0_101](https://img.shields.io/badge/TestPass-Win10%20x64__java__1.8.0__101-green.svg)
![开源协议Apache2.0](https://img.shields.io/badge/license-apache--2.0-green.svg)  
![当前版本](https://img.shields.io/github/release/silentax/BilibiliDown.svg?style=flat-square)
[![CI](https://github.com/silentax/BilibiliDown/actions/workflows/ci.yml/badge.svg)](https://github.com/silentax/BilibiliDown/actions/workflows/ci.yml)
![最近更新](https://img.shields.io/github/last-commit/silentax/BilibiliDown.svg?style=flat-square&color=FF9900)

Bilibili 视频下载器，用于下载B站视频。  
===============================

## Windows / macOS 构建与运行

### 环境
- 推荐使用 JDK 21；构建过程通过 Gradle Wrapper 固定版本
- Windows 与 macOS 均由 GitHub CI 执行构建、安全回归测试和 headless 烟测
- ffmpeg（用于音视频合并与转换）：macOS 使用 `brew install ffmpeg`，Windows 使用 `winget install Gyan.FFmpeg`
- 应用会依次探测配置路径、程序目录、系统 `PATH`，并兼容 Homebrew、MacPorts、WinGet 与 Chocolatey 常见路径
- 旧版 Windows/Linux ffmpeg 自动下载使用固定 SHA-256 清单，校验成功前不会进入可执行路径
- 项目路径：下文以 `/path/to/BilibiliDown` 代指本项目根目录

### 启动
```bash
cd /path/to/BilibiliDown
./gradlew clean check jar
java -Dfile.encoding=utf-8 -jar build/libs/INeedBiliAV.jar
```

Windows PowerShell 使用：

```powershell
cd C:\path\to\BilibiliDown
.\gradlew.bat clean check jar
java -Dfile.encoding=utf-8 -jar build\libs\INeedBiliAV.jar
```

旧版 `package.sh` 暂时保留，仅作为 Java 8 回退构建路径。

### 已修复的问题
| 问题 | 状态 | 方案 |
|------|:----:|------|
| 登录弹窗无反应 | ✅ | Cookie 注入（浏览器复制 SESSDATA/bili_jct/DedeUserID → config/cookies.config） |
| 文件命名以收藏夹开头 | ✅ | app.config: `bilibili.name.format = avTitle-clipTitle` |
| ffmpeg 缺失 | ✅ | Windows/macOS 自动探测；未找到时显示对应平台的安装命令 |
| Java HTTPS 证书太旧 | ⚠️ | 请使用 JDK 21 运行；后续安装包将内置运行时，避免依赖本机旧 Java |
| 跳过 TLS 证书验证 | ✅ | 已移除 Trust-All 实现和配置入口，HTTPS/SMTP 使用 JVM 默认信任库与主机名校验 |

### 下载音频流程
1. 粘贴 B站 URL（视频/收藏夹/UP主页面均可）
2. 勾选「仅下载音频」
3. 点下载，默认输出目录 `download/`

### 安全提醒
- `config/cookies.config` 明文存储登录凭据，用完退出登录或删除
- 不要将此文件提交到 Git

---

登录后的凭证明文保存在`config`文件夹下的`cookies.config`。
如有需要请直接删除，或`操作->登录相关->退出登录`  
更多详情请参考[帮助文档](https://nICEnnnnnnnLee.github.io/BilibiliDown) (如果访问不太顺畅的话，可以试试[备用帮助文档](https://bili.nicelee.top/BilibiliDown))  

## :smile:第三方库使用声明  
* AV和BV转换参考了[Colerar/abv](https://github.com/Colerar/abv)[![](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/Colerar/abv/blob/master/LICENSE-MIT)  
* 使用[JSON.org](https://github.com/stleary/JSON-java)库做简单的Json解析[![](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/stleary/JSON-java/blob/master/LICENSE)
* 使用[zxing](https://github.com/zxing/zxing)库生成链接二维码图片[![](https://img.shields.io/badge/license-Apache%202-green.svg)](https://raw.githubusercontent.com/zxing/zxing/master/LICENSE)  
* 以外部库的方式调用[ffmpeg](http://www.ffmpeg.org)进行转码(短片段flv未使用ffmpeg，仅多flv合并及m4s转换mp4格式需要用到)[![](https://img.shields.io/badge/license-depends-orange.svg)](http://www.ffmpeg.org/legal.html)  
* geetest验证码实现参考了[geetest-validator](https://github.com/kuresaru/geetest-validator)[![](https://img.shields.io/badge/license-unknown-gray.svg)](https://github.com/kuresaru/geetest-validator)
* cookie刷新代码的wasm逆向实现参考了[SocialSisterYi/bilibili-API-collect#524](https://github.com/SocialSisterYi/bilibili-API-collect/issues/524#issuecomment-1537519232)[![](https://img.shields.io/badge/license-CC%20BY%20NC%204.0-green.svg)](https://github.com/SocialSisterYi/bilibili-API-collect/issues/524#issuecomment-1537519232)

## :smile:其它  
* **上游历史文档**: <https://nICEnnnnnnnLee.github.io/BilibiliDown/guide/quick-start/download>   
* **GitHub**: [https://github.com/silentax/BilibiliDown](https://github.com/silentax/BilibiliDown)  
* **Github Release**: <https://github.com/silentax/BilibiliDown/releases>  
* **Bitbucket**: [https://bitbucket.org/NiceLeeee/BilibiliDown](https://bitbucket.org/NiceLeeee/BilibiliDown)  
* **Gitee码云**: [https://gitee.com/NiceLeee/BilibiliDown](https://gitee.com/NiceLeee/BilibiliDown)  
* [**更新日志**](https://github.com/silentax/BilibiliDown/blob/master/UPDATE.md)


## :smile:LICENSE  
+ [第三方LICENSE](https://github.com/silentax/BilibiliDown/tree/master/release/LICENSE/third-party)  
+ 本项目提供的`ffmpeg.exe`基于[nICEnnnnnnnLee/FFmpeg-Builds](https://github.com/nICEnnnnnnnLee/FFmpeg-Builds/blob/master/SPECIFIC_CHANGES.md)进行编译。  
    设置Github secret `FF_SPECIFIC_CONFIGURE`如下：  
```
--disable-debug --disable-doc --disable-ffplay --disable-ffprobe --enable-static --disable-shared --disable-network --disable-autodetect --disable-decoders --disable-gpl --disable-version3 --enable-decoder='h264,aac*,mp3*,mp4,eac3,flac' --disable-encoders --disable-demuxers --enable-demuxer='concat,mov,m4v,flv,mp3,aac,m4a' --disable-muxers --enable-muxer='flv,mp4,mp3,adts' --enable-encoder='libmp3lame,mp3,aac' --disable-parsers --enable-parser=h264 --disable-protocols --enable-protocol='concat,file' --disable-bsfs --enable-bsf='h264_metadata,h264_mp4toannexb' --disable-filters --enable-filter='concat,aresample' --disable-iconv --enable-small
```

+ 本项目遵守开源协议`Apache 2.0`。  
```
Copyright (C) 2019-2024 NiceLee. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```


