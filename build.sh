#!/bin/sh
# TerraBox 构建脚本
# 依赖: lophine-api(Folia 26.2) + vault-api + adventure + guava + bungeecord + annotations
set -e
DIR="/var/minis/workspace/terrabox"
API_BASE="/tmp/apidez/apilibs/api_pack_new/构建包api"
HUNT="/tmp/apidez/apilibs/hunt_libs"

LOPHINE="$API_BASE/lophine-api-26.2.build.643-stable.jar"
ADVENTURE_API="$API_BASE/adventure-api-5.2.0.jar"
VAULT="$HUNT/vault-api.jar"
ADVENTURE_KEY="$HUNT/adventure-key.jar"
SERIALIZER_LEGACY="$HUNT/adventure-text-serializer-legacy.jar"
EXAMINATION_API="$HUNT/examination-api.jar"
EXAMINATION_STRING="$HUNT/examination-string.jar"
GUAVA="$HUNT/guava.jar"
BUNGEECORD="$HUNT/bungeecord-chat.jar"
ANNOTATIONS="$HUNT/jetbrains-annotations.jar"

CP="$LOPHINE:$ADVENTURE_API:$VAULT:$ADVENTURE_KEY:$SERIALIZER_LEGACY:$EXAMINATION_API:$EXAMINATION_STRING:$GUAVA:$BUNGEECORD:$ANNOTATIONS"

echo "== 检查依赖 =="
for jar in "$LOPHINE" "$ADVENTURE_API" "$VAULT" "$ADVENTURE_KEY" "$SERIALIZER_LEGACY" "$EXAMINATION_API" "$EXAMINATION_STRING" "$GUAVA" "$BUNGEECORD" "$ANNOTATIONS"; do
    if [ ! -f "$jar" ]; then
        echo "✗ 缺失: $jar"
        exit 1
    fi
    echo "✓ $(basename $jar)"
done
echo ""
echo "✓ 所有依赖就绪"

echo "== 编译 Java =="
find "$DIR/src" -name "*.java" > "$DIR/srcs.txt"
rm -rf "$DIR/out"
mkdir -p "$DIR/out"
javac -d "$DIR/out" -cp "$CP" @"$DIR/srcs.txt" 2>&1 | grep -vE "deprecat|removal|uses unchecked|uses or overrides" || true
CLASS_COUNT=$(find "$DIR/out" -name "*.class" | wc -l)
echo "✓ 编译完成: $CLASS_COUNT class files"

echo "== 打包 jar =="
rm -f "$DIR/TerraBox-1.0.0.jar"
cd "$DIR/out"
cp "$DIR/src/plugin.yml" .
cp "$DIR/src/config.yml" .
jar cf ../TerraBox-1.0.0.jar . 2>/dev/null || jar cf ../TerraBox-1.0.0.jar com plugin.yml config.yml
cd "$DIR"

echo "== 验证版本 =="
jar tf "$DIR/TerraBox-1.0.0.jar" | grep "plugin.yml" && echo "✓ plugin.yml 已包含"
echo ""
echo "== 最终产物 =="
ls -la "$DIR/TerraBox-1.0.0.jar"
echo ""
sha256sum "$DIR/TerraBox-1.0.0.jar"
