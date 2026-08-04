#!/bin/bash
#
# Mac 原生安装包构建脚本 (.dmg)
# 使用 jpackage 将 fat JAR 打包为内置 JRE 的 Mac 安装包
#
# 前置条件:
#   - JDK 17+ (推荐 21)，包含 jpackage 工具
#   - Xcode command line tools (提供 iconutil)
#
# 用法:
#   JAVA_HOME=/path/to/jdk ./scripts/build-mac.sh
#
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -z "${JAVA_HOME:-}" ]; then
  echo "错误: 请设置 JAVA_HOME 环境变量"
  exit 1
fi

JPACKAGE="$JAVA_HOME/bin/jpackage"
if [ ! -x "$JPACKAGE" ]; then
  echo "错误: 未找到 jpackage，请确认 JDK 版本 >= 17"
  exit 1
fi

APP_VERSION="6.41.0"
DIST_DIR="build/dist"
STAGE_DIR=$(mktemp -d)

echo "==> 构建 fat JAR..."
./gradlew --no-daemon clean jar

echo "==> 准备打包资源..."
cp build/libs/INeedBiliAV.jar "$STAGE_DIR/"

# 从 favicon.png 生成 .icns 图标
ICON_DIR=$(mktemp -d)/icon.iconset
mkdir -p "$ICON_DIR"
SRC_ICON="src/resources/favicon.png"
for spec in "16:16" "32:16@2x" "32:32" "64:32@2x" "128:128" "256:128@2x" "256:256" "512:256@2x" "512:512" "1024:512@2x"; do
  SIZE="${spec%%:*}"
  NAME="${spec##*:}"
  sips -z "$SIZE" "$SIZE" "$SRC_ICON" --out "$ICON_DIR/icon_${NAME}.png" >/dev/null 2>&1 || true
done
ICON_FILE="$STAGE_DIR/app.icns"
iconutil -c icns "$ICON_DIR" -o "$ICON_FILE" 2>/dev/null || true

ICON_ARGS=""
if [ -f "$ICON_FILE" ]; then
  ICON_ARGS="--icon $ICON_FILE"
  echo "    图标: 已生成 .icns"
else
  echo "    图标: 未生成 .icns，使用默认图标"
fi

echo "==> 运行 jpackage 构建 .dmg..."
mkdir -p "$DIST_DIR"

"$JPACKAGE" \
  --type dmg \
  --input "$STAGE_DIR" \
  --name BilibiliDown \
  --main-jar INeedBiliAV.jar \
  --main-class nicelee.ui.FrameMain \
  --app-version "$APP_VERSION" \
  --vendor BilibiliDown \
  $ICON_ARGS \
  --java-options "-Dfile.encoding=UTF-8" \
  --java-options "-Dhttps.protocols=TLSv1.2" \
  --dest "$DIST_DIR"

echo "==> 清理..."
rm -rf "$STAGE_DIR"

DMG_FILE="$DIST_DIR/BilibiliDown-$APP_VERSION.dmg"
echo ""
echo "构建完成: $DMG_FILE"
echo "双击 .dmg 拖入 Applications 即可使用，无需安装 Java。"
