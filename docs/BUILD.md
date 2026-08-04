# 构建与打包指南

## 环境要求

- JDK 21 (推荐) 或 JDK 17+，需包含 jpackage 工具
- Gradle 8.14.3 (项目内置 wrapper，无需单独安装)
- 网络连接 (首次构建需下载依赖，后续可离线)

## 快速构建

### 构建可运行 JAR (fat JAR)

```bash
JAVA_HOME=/path/to/jdk ./gradlew --no-daemon clean jar
```

产物: `build/libs/INeedBiliAV.jar` (约 2MB，包含全部依赖)

运行方式 (需已安装 Java 8+):

```bash
java -Dfile.encoding=UTF-8 -Dhttps.protocols=TLSv1.2 -jar INeedBiliAV.jar
```

### 构建 Mac 原生安装包 (.dmg)

```bash
JAVA_HOME=/path/to/jdk ./scripts/build-mac.sh
```

前置条件:
- macOS 10.10+
- Xcode Command Line Tools (提供 iconutil)
- JDK 17+ (推荐 21)

产物: `build/dist/BilibiliDown-6.41.0.dmg` (约 57MB，内置 JRE)

安装方式: 双击 .dmg，将 BilibiliDown 拖入 Applications 文件夹。无需安装 Java。

应用数据目录: `~/Library/Application Support/BilibiliDown`

### 构建 Windows 原生安装包 (.exe)

```bat
set JAVA_HOME=C:\path\to\jdk
.\scripts\build-windows.bat
```

前置条件:
- Windows 10+
- JDK 17+ (推荐 21)
- Inno Setup 6+ (下载: https://jrssoftware.net/inno-setup-downloads/)

产物: `build\dist\BilibiliDown-6.41.0.exe`

安装方式: 双击 .exe 安装，从开始菜单启动。无需安装 Java。

应用数据目录: `%APPDATA%\BilibiliDown`

### 构建 Windows .msi 安装包

将 `scripts/build-windows.bat` 中的 `--type exe` 改为 `--type msi`，并安装 WiX Toolset 3.x。

## 运行完整测试

```bash
# Java 8 兼容性验证
JAVA_HOME=/path/to/jdk ./gradlew --no-daemon --offline -PjavaToolchainVersion=8 clean check jar manualE2eClasses

# JDK 21 原生验证
JAVA_HOME=/path/to/jdk ./gradlew --no-daemon --offline clean check jar manualE2eClasses
```

共 21 项任务，包含: 编译、安全测试、HTTP 契约测试、多线程下载契约测试、UI 回归测试、日志隐私测试、JAR 打包、smoke 测试。

## 跨平台说明

- 代码使用 Java 8 source/target，可在 Java 8+ 运行
- jpackage 不支持交叉编译: Mac 上只能构建 .dmg，Windows 上只能构建 .exe/.msi
- fat JAR 本身是跨平台的，可在任何装有 Java 的系统上运行
- ffmpeg 由应用首次运行时自动下载，无需手动安装
