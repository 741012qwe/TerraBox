# TerraBox v2.2.0 修复报告（第二轮）

> 本轮针对用户反馈的 8 类问题全部修复。产物 `TerraBox-1.0.0.jar`（52 class）。

## 1. 特殊道具使用——逻辑和事件修复
- **效果先执行、成功后最后才消耗**：原逻辑 `consume` 在效果之前执行，若效果抛异常，道具已被扣但没生效（数量错乱）。改为：效果全部成功后才 `consume`，异常时不消耗。
- **阻止特殊道具被原版饮用/食用**：新增 `PlayerItemConsumeEvent` 监听，对 POTION/可食用材质特殊道具（如 heal_potion 药水）禁止被当原版药水喝掉，改为走特殊道具效果。
- 使用特殊道具不再刷聊天（改 ActionBar 提示），已在前轮处理。

## 2. 玩家无法正常 PVP 攻击
- `GameListener.onDamage` 重写，按**受害玩家所在房间**判定，且要求**双方都正在对局内且未淘汰**：
  - PVP：正常互相伤害
  - SOLO：禁互伤
  - TEAM：同队免伤
  - 淘汰/旁观玩家不能对幸存者造成伤害
- 修复了原逻辑只查默认房间/不校验双方对局状态的隐患。

## 3. 重置地图后箱子物品掉出来
- `BoxManager.wipeAll` / `removeBoxAt` 拆箱（`setType(AIR)`）前**先清空箱子库存**，防止战利品被拆箱弹出成为掉落物。

## 4. 对局时间
- 所有模式统一设置 `endAt` 时间上限。多人 PVP / 组队默认 30 分钟到时间结算（PVP 按击杀数、组队按存活队）。
- 配置 `game.no-timeout: false`——设为 true 则多人模式回到纯淘汰制（无时间上限）。

## 5. 多人模式GUI & 单人模式GUI互动
- 新增 **GameGui（对局模式选择）**：主菜单"参加对局"点击进入，可选**单人 / 多人PVP / 组队**三种报名（各自独立模式房间）。
- 每个模式房间显示状态/参战/存活人数，玩家点击即加入/退出；管理员可用命令开赛。

## 6. 对局结束后玩家背包不清空
- `startRunning`（开局）清空参战玩家背包——吃鸡从零开始，公平。
- `finish`（结束回大厅）清空背包；`resetWorld` 再次清空兜底。

## 7. 重置地图前不清空所有实体/掉落物
- `resetWorld` 现在清空**所有非玩家实体**（掉落物/箭矢/TNT/物品等），不只 Item。

## 8. 箱子物品平衡性
- `LootManager.fillInventory` 增加**每箱目标堆数区间**（按稀有度：普通3~6、精良4~7、稀有5~8、传说6~9、绝世7~10），可用 `loot.<key>.min-stacks/max-stacks` 覆盖。
- 表格**随机打乱**再填充，保证不同箱子物品多样性；达到上限防爆；未达保底下限自动补充（不补特殊道具，防平衡崩坏）。

## 验证
- 编译 51 class，无错误。无 Bukkit Scoreboard API 残留。GameGui 已打包。
- jar sha256 已与 AI工作目录产物一致。

## 部署
- 覆盖 jar，完整重启（勿热载）。旧 config 需补 `game.no-timeout` 键（默认 false 已兼容）。

## 说明
- 新增模式房间（solo/pvp/team）与默认房间 default 并存。玩家经 GUI 报名到模式房间，管理员用 `/box room start <solo|pvp|team> <mode>` 开赛。
- 物资箱/建筑等仍作用于当前 arena 世界（默认房间完整；多房间的独立物资投放需更大改动，如需要可下轮做）。

---

# 第三轮：对局退出处理 & 大厅限制

## 1. 对局中玩家退出游戏自动踢出返回大厅
- `onPlayerQuit`（玩家下线）：若对局运行中且是参战者 → 标记淘汰（非SOLO）+ 清除计分板 + 广播淘汰。
- `onJoin`（重连）：**非参战未淘汰玩家一律强制送回大厅**（原先只在 `!isEliminated` 时回大厅，导致淘汰/退出玩家滞留在对局世界——已修复）。参战未淘汰玩家才送回对局世界。

## 2. 对局中不能传送到大厅
- 新增 `GameManager.canLeaveToLobby(p)`：对局进行中（RUNNING/COUNTDOWN）且参战未淘汰玩家 → 禁止返回大厅。
- 新增 `requestReturnToLobby(p)`：主动回大厅走此方法，被阻止时提示"对局进行中, 不能返回大厅"。
- `/box lobby` 命令、主菜单"返回大厅"按钮 均改用 `requestReturnToLobby`（对局中存活玩家被阻止；淘汰/非参战玩家仍可回大厅）。

---

# 第四轮：死亡后自动旁观

## 需求
死亡淘汰后**自动进入旁观，不返回大厅**；玩家可自行用指令返回大厅，或继续旁观对局。

## 实现
- `offerSpectateOrLobby`（死亡淘汰后）：从"默认送大厅"改为**默认自动进入旁观**（`spectate`），提示玩家可在 `/box lobby` 回大厅或保持旁观。
- `onRespawn`（重生）：对局运行中，把淘汰玩家重生点设为**死亡位置**（旁观起点），并调用 `autoSpectateAfterDeath`。
- 新增 `GameManager.autoSpectateAfterDeath(p)`：已淘汰玩家重生后 5 tick 自动设为 SPECTATOR 旁观。
- `spectate(p)`：设 SPECTATOR 留在对局世界观战。
- 玩家指令：`/box lobby` 回大厅（淘汰玩家允许）、`/box spectate` 继续旁观。

---

# 第五轮：退出清背包 / 加大厅清背包 / 淘汰返回淘汰位旁观

## 1. 玩家中途退出自动清理背包数据
- `onQuit`：对局运行中且该玩家在对局名单(含淘汰)内 → 退出时自动清空背包，防止对局搜刮物品被带离。

## 2. 玩家加入大厅自动清理背包数据
- `sendToLobby`：所有回大厅路径统一**先清空背包**再传送（对局结束/淘汰回大厅/主动回大厅均清）。

## 3. 被淘汰后自动返回淘汰位旁观（而非原版出生点）
- **修复**：`PlayerRespawnEvent` 里 `p.getLocation()` 可能已被原版重置为出生点，导致设置无效。
  改为在 `onDeath`（PlayerDeathEvent，实体位置是真实死亡点）**记录死亡位置**（`lastDeathLoc`），
  `onRespawn` 用记录的死亡位置设为重生点 → 淘汰玩家重生在淘汰位, 再自动切旁观。
- 仅淘汰玩家重生到死亡位; SOLO 不淘汰, 保持正常重生。

---

# 第六轮：道具触发范围/锁定优化

## 1. TNT 投掷抛物线 (像弓箭)
- `tntLaunch`：初速 = 准星方向水平分量 + 上仰分量相对加大, 由重力下坠形成**弧线**（类似弓箭抛射），
  由原来的直线小力度改为更强的抛射强度（水平*2.2 + 上抬），飞得更远且有抛物线。

## 2. 引雷自动锁定附近玩家
- `lightning`：先在玩家区域线程用 `findNearestEnemy` 查找**附近最近敌人**（锁定半径=def.radius+8），
  锁定到则落雷**到其位置**（头顶/脚下），并对目标额外雷击 + 周围范围伤害；
  无目标则回退原准星方向落雷。

## 3. 火焰弹同理 (自动锁定)
- `fireball`：同样先用 `findNearestEnemy` 锁定最近敌人，落点=敌人位置（玩家区域内查对象 → 落点区域爆炸），
  无目标回退准星方向。爆炸伤害/炸飞逻辑不变。

## 新增方法
- `SpecialItemManager.findNearestEnemy(World, Location, myId, radius)`：在玩家所属区域线程查找最近的非己方敌对实体
  （其他玩家/敌对生物），返回最近者，无则 null。
- 所有查找/落雷/爆炸均通过 `Bukkit.getRegionScheduler().run(plugin, position, ...)` 在**所属区域线程**执行，
  符合 Folia 线程模型。

---

# 第十四轮：神器GUI拖动修复 + GUI更新问题 + 神器效果与平衡

> 本轮针对用户反馈三类问题全部修复。产物 `TerraBox-1.0.0.jar`（65 class，新增 ArtifactGui）。

## 1. 神器GUI物品可以拖动的问题
- **新增只读《神器图鉴》GUI（`ArtifactGui`）**：`/box artifacts` 由原来发送文本列表改为打开图鉴，
  只读展示全部神器的名称/描述/效果/获取途径，**所有物品禁止点击与拖动**（`GuiListener` 新增 `Type.ARTIFACT`，
  `onClick/onDrag` 统一 `setCancelled(true)`）。主菜单 slot 23 增加"神器图鉴"入口。
- **工作台材料槽限材**：`CraftGui` 材料槽现在**只接受碎片/材料**（`plugin.crafts().isCraftItem` 校验）。
  神器、其他物品点击放入材料槽会被拒收（取消 + 提示"只能放入碎片/材料"），防止素材被误放成废料。

## 2. GUI 更新/刷新问题
- **修复拖拽判定 bug**：`GuiListener.onDrag` 原来对 CRAFT / SELL 的**背包槽（raw≥top.size）一律取消**，
  导致玩家**无法把碎片从背包拖进材料槽、无法把战利品拖进商店**。现在背包槽统一放行，仅 GUI 内非法槽
  （按钮区/输出槽/装饰区）取消。SELL 同理修复。
- **修复工作台翻页丢失材料**：翻页（上一/下一配方）原来调用 `render()` 会 `inv.clear()` 清空玩家已放材料。
  新增 `CraftGui.saveMats()/restoreMats()`，翻页前保存材料槽、渲染后恢复，已放材料不丢失。
- **ON_CLOSE 工作台退回材料** 逻辑保留（关闭时材料槽碎片/材料退回背包）。

## 3. 神器效果与平衡
- **疾风之靴 SPEED（原来的空效果）真正实现**：新增玩家上线注册区域周期任务（每 1.5s），
  穿戴疾风之靴则持续维持速度加成（level = magnitude），脱下自然过期。`PlayerJoinEvent/PlayerQuitEvent` 注册/清理任务。
- **屠龙圣弓（远程弓箭效果）真正实现**：新增 `EntityShootBowEvent` 监听——手持屠龙圣弓（effect=STRING）射出时
  给箭打 PDC 标记；命中（`EntityDamageByEntityEvent` damager 为带标记的 Projectile）时追加 +6 伤害并点燃目标 4 秒 + 火焰粒子。
- **吸血平衡**：VAMPIRIC 系数下调（0.8→0.5）、吸血獠牙 magnitude 2.0→1.2；LIFESTEAL 稳定 0.25 系数。
  避免单次命中回血过强。
- **STRENGTH 统一**：近战命中按强度追加伤害（保留），精确用于 aegis_axe（FROST 已单独生效）。
- **Folia 合规加固**：LIFESTEAL 的 attacker、远程的 target 实体操作（setHealth/setFireTicks/粒子/音效）
  均用 `getScheduler().run()` 包裹到各自所属区域线程，跨区域读写安全。
- **config.yml**：`draco_bow.effect` 由 STRENGTH 改为 STRING（对应远程弓箭新效果），`vampire_fang.magnitude` 2.0→1.2。

---

# 第十五轮：恶地 / 正常主世界改用原版自带生成器

> 用户要求：恶地和原版正常主世界**直接使用原版自带的生成器**，不再手搓方块算法。

## 实现
- **`ArenaManager.create()`**：对 `TerrainType.NORMAL` 和 `TerrainType.BADLANDS` 两种地形，**不再设置自定义 `ChunkGenerator`**
  （`WorldCreator` 不调用 `.generator(...)`），直接使用原版自带生成器创建真实原版世界。
- 其余地形（默认/沙漠/岛屿/末地/下界/城市）仍用 `CustomTerrainGenerator` 手搓，保持不变。
- 世界创建后仍执行：`applyBorder`（尺寸 1056/1024）、强制白天、预生成、传送门（NORMAL 有地狱/末地门）、`world-loot` 差异化——这些核心玩法逻辑不变，仅地形生成改为原版。

## 说明（原版限制）
- **正常主世界 (NORMAL)**：原版生成器 → 真实主世界（平原/森林/山脉/河流/恶地/沙漠等多样群系、树林、原版结构）。
- **恶地 (BADLANDS)**：原版生成器无法指定"整片恶地"（Minecraft 原版机制不允许在创建世界时锁定单一群系），
  因此恶地世界用原版生成后是**原版多样群系世界**（会自然包含恶地/沙漠/平原等区域），而非纯恶地。
  若需要"整片恶地"，需回退到自定义 `generateBadlands` 方案，可随时在 `ArenaManager.create()` 调整。

## 适配注意
- 原版生成器会生成原版怪物/结构/丰富树木植被（之前自定义生成器 `shouldGenerateMobs=false` 无怪物），
  但世界仍强制白天（光照充足），怪物主要出现在洞穴、夜间不生成。若需关闭怪物可后续加配置。

## 验证
- 编译 65 class 无错误。jar sha256 `38c639a20129f0b47b978893da30a2a4460587be792b4b51b6c6c314df2db773`（构建后更新）。

## 部署
- 覆盖 jar，**完整重启**。已有的 `arena_normal_N` / `arena_badlands_N` 世界若已用旧自定义生成器生成，
  需**删除对应世界文件夹**（worlds/arena_normal_1 等）后重启，才能以原版生成器重新生成；仅覆盖 jar 不会改变已存在的世界地形。

---

# 第十六轮：GUI按钮锁定 + 配方/材料检查 + 掉落平衡 + 道具使用方式 + 房间邀请系统

> 用户需求共 8 项全部实现。产物 `TerraBox-1.0.0.jar`（69 class，新增 InviteManager / InviteGui / RoomGui）。

## 1. 修复工作台(神器配方GUI)按钮可拖动到背包
- `GuiListener.onClick` CRAFT：材料槽屏蔽 Shift/DoubleClick/数字键/副手交换批量移动；按钮区/输出槽/装饰区
  一律 `setCancelled(true)` 锁定，任何点击/Shift 都无法移动物品到背包。
- `onDrag` CRAFT：拖拽道具必须是碎片/材料（非材料整体取消），触及按钮区/输出/装饰一律取消，背包槽仅作拖放终点放行。

## 2. GUI 配方和材料检查
- 材料槽只接受合法碎片/材料（`crafts().isCraftItem`），神器等其他物品点击放入被拒收并提示"只能放入碎片/材料"。
- 材料槽禁批量(Shift/双击/数字键)，防材料错乱；关闭时材料槽退回背包。

## 3. 配方材料物资箱刷新平衡性
- config.yml 各档碎片/材料掉落重新平衡：碎片数量与概率随稀有度递增（精良 1-2/12%、稀有 1-3/20%、
  传说 2-5/32%、绝世 3-6/50%），通用材料(核心/秘银/星辰)同步上调，保证收集节奏合理且不挤占装备格子。

## 4. 道具改为副手/徒手手持右键直接使用一个
- `SpecialItemListener.onInteract`：不再仅主手——主手为空/非特殊道具时自动检测**副手**；对空气/方块
  右键均可直接触发（不依赖准星目标），触发后 `setCancelled` 阻止原版交互(如打开箱子/放置)。
- `SpecialItemManager.trigger(player,item,offHand)`：新增副手标志；`consume` 一次只减 1 个（不是整组），
  按主/副手正确扣减，归零时清空对应手。

## 5. 房间系统（创建 / GUI邀请 / 指令邀请 / 查找 / 查看）
- **创建房间**：`/box room create <id>`（普通玩家可用，绑定当前对局世界，创建者即房主）；主菜单"对局房间"入口。
- **GUI**：新增 `RoomGui`（房间列表：状态/模式/参战/房主/成员，点击加入/退出；底部创建/邀请/返回/关闭）
  与 `InviteGui`（在线玩家列表，点击邀请到指定房间）。
- **指令邀请**：`/box room invite <玩家> [房间]`；**加入** `/box room join <id>`；**退出** `/box room leave <id>`；
  **查看** `/box room list` / `/box room info <id>`。
- **玩家自主建房**：`GameManager` 新增 `owner`/`isOwner`/`join`/`leave`；`RoomManager` 新增
  `joinedRooms`/`onlinePlayersIn`/`ownerOf`。

## 6. 管理员强制玩家加入某房间
- `/box room force <玩家> <房间>`（管理员）：把指定玩家强制加入指定房间报名，双方收到提示。

## 7. 点击聊天框接受/拒绝邀请
- 新增 `InviteManager`：房主邀请 → 被邀请者收到可点击文本 `[ 接受 ] [ 拒绝 ]`（Adventure ClickEvent 执行
  `/box invite accept|decline`），30 秒有效，每玩家同一时刻一个待处理邀请；离线/对局开始自动清理。

## 8. Tab 补全（玩家名 + 房间 + 在线房间）
- `onTabComplete` 扩展：`/box room` 补全子命令；`invite/join/force` 补全**在线玩家名**；`room join/leave/info/start/stop/remove/status/force` 补全**房间 id**；`/box invite` 补全 accept/decline。

## 新增文件
- `InviteManager.java`（邀请状态+可点击文本）、`InviteGui.java`（邀请玩家GUI）、`RoomGui.java`（房间列表GUI）。
- `GuiListener` 新增 `Type.INVITE` / `Type.ROOM`（全只读，禁止拖动）与 `GuiHolder.inviteRoom`。
- `TerraBoxPlugin` 新增 `invites/inviteGui/roomGui` + getter。

## 关键
- Adventure ClickEvent：`Component.clickEvent(ClickEvent.runCommand("/box invite accept"))` + hoverEvent。
- 邀请仅房主可发（`rg.owner().equals(owner)` 校验）；default 房间 owner 为 null 时放行。
- Java 25 编译，69 class（原 65 + InviteManager/InviteGui/RoomGui 等）。

## 验证
- 编译 69 class 无错误。jar sha256 `0fb3205731bbb5b089c3939d8dd59d054dfa7f16244375a91dab182e32a73597`（构建后更新）。

## 部署
- 覆盖 jar，**完整重启**（勿热载）。旧 config 无需改动（掉落平衡可选同步新 config.yml，否则用旧值）。

---

# 第十七轮：死亡重生修复 + 正常主世界原版地貌 + 工作台材料拖拽限制

> 用户反馈 3 个问题，全部处理。产物 `TerraBox-1.0.0.jar`（69 class）。

## 1. 玩家死亡后不能正常重生 / 切换旁观 / 返回大厅
- **修复 `GameListener.onRespawn`**：原逻辑把淘汰玩家重生点设为"死亡位置"，若死亡点在对局危险区（毒圈/虚空/危险地形）会导致重生后仍卡死/连死。
  现改为：
  - **对局淘汰玩家（PVP/TEAM）**：重生到**房间世界出生广场上空（安全高度）** → `autoSpectateAfterDeath` 自动进旁观；若对局已结束则**直接送回大厅**正常生存。
  - **对局内未淘汰玩家（SOLO 等）死亡**：重生回出生广场上空，不落危险区。
  - **非对局死亡**：按 `spawn.on-respawn` 配置正常重生。
- **修复 `RoomManager.roomOf`**：原逻辑对淘汰玩家返回 default 房间（因 `isInGame` 为 false）——导致淘汰玩家重生到错误世界。现改为匹配 `playersSet/eliminatedSet`，淘汰玩家回到**自己所在的对局房间世界**。

## 2. 正常主世界矿洞/地形/矿物/树木生成不了
- **`ArenaManager.create`**：对 NORMAL / BADLANDS 世界显式 `creator.type(WorldType.NORMAL)`（原版类型，非超平坦），并**不设自定义 generator** → 用原版生成器生成**完整原版地貌**（矿洞/矿物/山地/平原/河流/树林/结构）。
- 根因确认：旧世界若由自定义生成器生成（`shouldGenerateCaves/Structures/Mobs/Decorations` 全 false，仅铺地表），会导致无矿洞/矿物/树。**必须删除旧 `arena_normal_*`/`arena_badlands_*` 世界文件夹**后重启，才会以原版生成器重新生成完整地貌；仅覆盖 jar 不会改变已存在世界。

## 3. 工作台(神器配方GUI)材料能拖到背包
- **`GuiListener.onDrag` CRAFT**：**全面禁止拖拽**（材料槽/按钮/背包之间任何拖拽一律 `setCancelled(true)`），材料只能用**点击**放入/取出——彻底杜绝材料被拖到背包导致丢失/复制错乱。
- `onClick` 材料槽仍支持点击放入碎片/材料（校验 `isCraftItem`）、点击取出；按钮/输出/装饰保持锁定。

## 验证
- 编译 69 class 无错误。jar sha256 `b1a7f9845db446c706074f9b33b61f853d2154b2d6dab57baa72559f5a97c815`（构建后更新）。

## 部署
- 覆盖 jar，**完整重启**。正常主世界/恶地若先前用旧自定义生成器生成，需**删除对应世界文件夹**（`worlds/arena_normal_1`、`arena_badlands_1` 等）后重启，才以原版生成器生成完整原版地貌。

## 验证
- 编译 65 class，无错误（新增 ArtifactGui）。
- jar sha256 `08d1ecfb000dbe80f1984e508f3649240655c6ef0ed3f326a5dbd5316216aa99`。

## 部署
- 覆盖 jar，**完整重启**（勿热载）。旧 config 需把 `draco_bow.effect` 手动改为 `STRING`（否则屠龙圣弓远程效果不触发）；
  `vampire_fang.magnitude` 建议改 1.2（可选，但不改则吸血偏强）。
