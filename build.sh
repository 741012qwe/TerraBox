#!/bin/sh
# TerraBox 物资大陆 构建脚本
# 依赖: lophine-api(Folia 26.2) + vault-api + adventure + guava + annotations
set -e
DIR="/var/minis/workspace/terrabox"
API="/tmp/foliadoc/deps/api_pack_new/构建包api"
HUNT="/tmp/foliadoc/deps/hunt_libs"

CP="$API/lophine-api-26.2.build.643-stable.jar"
CP="$CP:$API/adventure-api-5.2.0.jar"
CP="$CP:$HUNT/vault-api.jar"
CP="$CP:$HUNT/adventure-key.jar"
CP="$CP:$HUNT/adventure-text-serializer-legacy.jar"
CP="$CP:$HUNT/examination-api.jar"
CP="$CP:$HUNT/guava.jar"
CP="$CP:$HUNT/bungeecord-chat.jar"
CP="$CP:$HUNT/jetbrains-annotations.jar"

echo "== 编译 Java 25 =="
find "$DIR/src" -name "*.java" > "$DIR/srcs.txt"
rm -rf "$DIR/out"
mkdir -p "$DIR/out"
javac -d "$DIR/out" -cp "$CP" @"$DIR/srcs.txt" 2>&1 | grep -vE "deprecat|removal|uses unchecked|uses or overrides" || true

echo "== 打包 jar =="
rm -f "$DIR/TerraBox-1.0.0.jar"
cd "$DIR/out"
cp "$DIR/src/plugin.yml" .
cp "$DIR/src/config.yml" .
jar cf ../TerraBox-1.0.0.jar com plugin.yml config.yml

echo "== 完成 =="
ls -la "$DIR/TerraBox-1.0.0.jar"
echo "class 数量:"
find "$DIR/out" -name "*.class" | wc -l
