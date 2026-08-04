@echo off
REM
REM Windows 原生安装包构建脚本 (.exe)
REM 使用 jpackage 将 fat JAR 打包为内置 JRE 的 Windows 安装包
REM
REM 前置条件:
REM   - JDK 17+ (推荐 21)，包含 jpackage 工具
REM   - Inno Setup 6+ (用于生成 .exe 安装包)
REM     下载: https://jrssoftware.net/inno-setup-downloads/
REM   - 或 WiX Toolset 3.x (用于生成 .msi 安装包)
REM
REM 用法:
REM   set JAVA_HOME=C:\path\to\jdk
REM   .\scripts\build-windows.bat
REM
REM 如需生成 .msi 而非 .exe，将 --type exe 改为 --type msi
REM

setlocal enabledelayedexpansion

cd /d "%~dp0\.."

if "%JAVA_HOME%"=="" (
    echo 错误: 请设置 JAVA_HOME 环境变量
    exit /b 1
)

set JPACKAGE=%JAVA_HOME%\bin\jpackage.exe
if not exist "%JPACKAGE%" (
    echo 错误: 未找到 jpackage，请确认 JDK 版本 >= 17
    exit /b 1
)

set APP_VERSION=6.41.0
set DIST_DIR=build\dist
set STAGE_DIR=%TEMP%\bilibilidown-stage

echo ==^> 构建 fat JAR...
call .\gradlew --no-daemon clean jar
if errorlevel 1 (
    echo 错误: JAR 构建失败
    exit /b 1
)

echo ==^> 准备打包资源...
if exist "%STAGE_DIR%" rmdir /s /q "%STAGE_DIR%"
mkdir "%STAGE_DIR%"
copy build\libs\INeedBiliAV.jar "%STAGE_DIR%\"

set ICON_ARGS=
if exist release\config\favicon.ico (
    set ICON_ARGS=--icon release\config\favicon.ico
    echo     图标: release\config\favicon.ico
) else (
    echo     图标: 未找到 favicon.ico，使用默认图标
)

if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"

echo ==^> 运行 jpackage 构建 .exe...
"%JPACKAGE%" ^
    --type exe ^
    --input "%STAGE_DIR%" ^
    --name BilibiliDown ^
    --main-jar INeedBiliAV.jar ^
    --main-class nicelee.ui.FrameMain ^
    --app-version %APP_VERSION% ^
    --vendor BilibiliDown ^
    %ICON_ARGS% ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Dhttps.protocols=TLSv1.2" ^
    --dest "%DIST_DIR%"

if errorlevel 1 (
    echo.
    echo 构建失败。请确认已安装 Inno Setup 6+。
    echo 下载地址: https://jrssoftware.net/inno-setup-downloads/
    rmdir /s /q "%STAGE_DIR%"
    exit /b 1
)

echo ==^> 清理...
rmdir /s /q "%STAGE_DIR%"

echo.
echo 构建完成: %DIST_DIR%\BilibiliDown-%APP_VERSION%.exe
echo 双击 .exe 安装后从开始菜单启动，无需安装 Java。
