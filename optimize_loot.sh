#!/bin/sh
# 优化道具生成逻辑

echo "=== 优化道具生成逻辑 ==="

# 优化 LootManager，增加更多道具种类
sed -i 's/    - material: IRON_INGOT/    - material: IRON_INGOT/' src/com/terrabox/LootManager.java
sed -i 's/    - material: GOLD_INGOT/    - material: GOLD_INGOT/' src/com/terrabox/LootManager.java
sed -i 's/    - material: DIAMOND/    - material: DIAMOND/' src/com/terrabox/LootManager.java
sed -i 's/    - material: ENCHANTED_BOOK/    - material: ENCHANTED_BOOK/' src/com/terrabox/LootManager.java

# 添加更多道具种类
sed -i '/- material: IRON_INGOT/a\
    - material: STONE_SWORD\
      min: 1\
      max: 1\
      chance: 30\
      enchants: {SHARPNESS:1-2}' src/com/terrabox/LootManager.java

sed -i '/- material: GOLD_INGOT/a\
    - material: IRON_SWORD\
      min: 1\
      max: 1\
      chance: 25\
      enchants: {SHARPNESS:2-3}' src/com/terrabox/LootManager.java

sed -i '/- material: DIAMOND/a\
    - material: DIAMOND_SWORD\
      min: 1\
      max: 1\
      chance: 20\
      enchants: {SHARPNESS:3-4}' src/com/terrabox/LootManager.java

sed -i '/- material: ENCHANTED_BOOK/a\
    - material: BOW\
      min: 1\
      max: 1\
      chance: 15\
      enchants: {POWER:1-3}' src/com/terrabox/LootManager.java

sed -i '/- material: BOW/a\
    - material: ARROW\
      min: 16\
      max: 32\
      chance: 40' src/com/terrabox/LootManager.java

sed -i '/- material: ARROW/a\
    - material: GOLDEN_APPLE\
      min: 1\
      max: 1\
      chance: 10' src/com/terrabox/LootManager.java

sed -i '/- material: GOLDEN_APPLE/a\
    - material: POTION\
      material: POTION\
      min: 1\
      max: 2\
      chance: 25\
      enchants: {SPEED:1-2, JUMP:1-2}' src/com/terrabox/LootManager.java

sed -i '/- material: POTION/a\
    - material: SHIELD\
      min: 1\
      max: 1\
      chance: 15' src/com/terrabox/LootManager.java

sed -i '/- material: SHIELD/a\
    - material: IRON_HELMET\
      min: 1\
      max: 1\
      chance: 20\
      enchants: {PROTECTION_ENVIRONMENTAL:1-2}' src/com/terrabox/LootManager.java

sed -i '/- material: IRON_HELMET/a\
    - material: IRON_CHESTPLATE\
      min: 1\
      max: 1\
      chance: 20\
      enchants: {PROTECTION_ENVIRONMENTAL:1-2}' src/com/terrabox/LootManager.java

sed -i '/- material: IRON_CHESTPLATE/a\
    - material: IRON_LEGGINGS\
      min: 1\
      max: 1\
      chance: 20\
      enchants: {PROTECTION_ENVIRONMENTAL:1-2}' src/com/terrabox/LootManager.java

sed -i '/- material: IRON_LEGGINGS/a\
    - material: IRON_BOOTS\
      min: 1\
      max: 1\
      chance: 20\
      enchants: {PROTECTION_ENVIRONMENTAL:1-2}' src/com/terrabox/LootManager.java

echo "✅ 道具种类已优化，添加了武器、盔甲、药水等"

# 重新编译项目
echo "=== 重新编译项目 ==="
./build.sh

echo "=== 优化完成 ==="
echo "已添加的道具种类："
echo "- 武器：石剑、铁剑、钻石剑、弓"
echo "- 盔甲：铁头盔、铁胸甲、铁护腿、铁靴"
echo "- 药水：速度药水、跳跃药水"
echo "- 其他：盾牌、金苹果、箭"