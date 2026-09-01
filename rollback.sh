#!/bin/bash
# TerraBox 版本回退脚本
# 使用方法: ./rollback.sh <版本号> 或 ./rollback.sh latest

set -e

WORKSPACE="/var/minis/workspace/terrabox"
DEPLOY="/var/minis/mounts/AI工作目录/物资大陆TerraBox"

TARGET="${1:-latest}"

echo "=== TerraBox 版本回退 ==="
echo "目标版本: $TARGET"
echo ""

cd "$WORKSPACE"

# 检查是否是有效标签
if [ "$TARGET" = "latest" ]; then
    TARGET=$(git tag --sort=-creatordate | head -1)
fi

if ! git show-ref --tags "refs/tags/$TARGET" >/dev/null 2>&1; then
    echo "❌ 错误: 找不到标签 $TARGET"
    echo ""
    echo "可用标签:"
    git tag
    exit 1
fi

echo "✅ 目标版本: $TARGET"
echo ""

# 显示版本信息
echo "=== 版本信息 ==="
git show "$TARGET" --stat --format="%H%n%ai%n%s" 2>/dev/null | head -10
echo ""

# 回退源码
echo "=== 回退源码 ==="
git checkout "$TARGET" -- src/ 2>/dev/null || git checkout "$TARGET" -- . 
echo "✅ 源码已回退"
echo ""

# 编译新版本
echo "=== 重新编译 ==="
sh build.sh
echo "✅ 编译完成"
echo ""

# 同步到部署目录
echo "=== 同步到部署目录 ==="
cp TerraBox-1.0.0.jar "$DEPLOY/"
cp src/*.yml "$DEPLOY/" 2>/dev/null || true
cp -r src/* "$DEPLOY/src/" 2>/dev/null || true
echo "✅ 已同步到 $DEPLOY"
echo ""

# 显示新SHA256
echo "=== 新版本信息 ==="
echo "JAR: TerraBox-1.0.0.jar"
echo "SHA256: $(sha256sum TerraBox-1.0.0.jar | cut -d' ' -f1)"
echo ""

echo "=== 回退完成 ==="
echo "请重启服务器生效。"
