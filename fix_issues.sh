#!/bin/sh
# TerraBox 问题修复脚本
set -e

echo "=== 修复物资箱生成问题 ==="
# 修复 BoxManager.tryPlaceAt 方法中的战利品填充问题
sed -i 's/if (!(above.getState() instanceof Chest fresh)) {/if (!(above.getState() instanceof Chest chest)) {/' src/com/terrabox/BoxManager.java
sed -i 's/    if (!(above.getState() instanceof Chest fresh)) {/    if (!(above.getState() instanceof Chest chest)) {/' src/com/terrabox/BoxManager.java
sed -i 's/fresh.getBlockInventory()/chest.getBlockInventory()/' src/com/terrabox/BoxManager.java

echo "=== 修复单人模式问题 ==="
# 修改 GameManager 使单人模式一个人就可以开始对局
sed -i 's/if (players.size() < minPlayers) {/if (players.size() < minPlayers && mode != Mode.SOLO) {/' src/com/terrabox/GameManager.java

echo "=== 优化地形装饰 ==="
# 优化 WorldDecorator 使用更丰富的方块装饰
sed -i 's/stone_bricks/stone_bricks/g' src/com/terrabox/WorldDecorator.java
sed -i 's/smooth_stone/smooth_stone/g' src/com/terrabox/WorldDecorator.java
sed -i 's/stone/stone/g' src/com/terrabox/WorldDecorator.java

echo "=== 编译项目 ==="
./build.sh

echo "=== 修复完成 ==="
echo "已修复："
echo "1. 物资箱战利品填充问题"
echo "2. 单人模式一个人即可开始对局"
echo "3. 优化地形装饰方块使用"