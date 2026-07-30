#!/usr/bin/env bash

set -eu

# cd 到脚本所在目录
cd "$(dirname "$0")"
project_dir=$(pwd)

# 使用独立临时目录，避免与 Gradle 或上一次中断构建的输出相互污染。
legacy_build_dir=$(mktemp -d "${TMPDIR:-/tmp}/bilibili-down-package.XXXXXX")
trap 'rm -rf "$legacy_build_dir"' EXIT

# 复制整个文件夹
cp -r src/. "$legacy_build_dir"

# 删除不需要的java文件
rm -rf "$legacy_build_dir/nicelee/test"

# 获取java文件列表
find "$legacy_build_dir" -name "*.java" > "$legacy_build_dir/sources.txt"

# 获取环境变量,解压lib包
cd libs
find "$(pwd)" -name "*.jar" > "$legacy_build_dir/libs.txt"
cd "$legacy_build_dir"
jclasspath=""
while IFS= read -r dependency_jar
do
    jclasspath="$dependency_jar:$jclasspath"
    jar xf "$dependency_jar"
done < "$legacy_build_dir/libs.txt"
cd "$project_dir"

# 编译java
javac -cp "$jclasspath" -encoding UTF-8 @"$legacy_build_dir/sources.txt"

# 删除所有.java文件
find "$legacy_build_dir" -name "*.java" -delete
rm -f "$legacy_build_dir/sources.txt" "$legacy_build_dir/libs.txt"

# 打包
jar cfe INeedBiliAV.jar nicelee.ui.FrameMain -C "$legacy_build_dir" .
