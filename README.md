# TerraBox 物资大陆 — 多世界 PVP 吃鸡插件

Folia/Lophine 26.2 专用 · Java 25 编译 · 简体中文

## 架构（多世界）

- **大厅世界** `terra_lobby`：512x512 玩家聚集地。自动生成中心石砖广场（半径24）+ 玻璃围栏 + 外围屏障墙 + 底部基岩；玩家掉出平台由坠落保护传回；大厅内禁 PvP。
- **对局世界池** `arena_1~N`：1024x1024，每种地形独立生成、独立预生成、独立物资。GUI/命令可切换地形模板。
- **地形模板**：`默认平原`(DEFAULT) / `沙漠风格`(DESERT) / `大岛屿风格`(ISLANDS，随机小岛+海洋) / `末地岛屿`(THE_END，浮空末地石岛群+黑曜石柱+末地城塔楼，黑色天空) / `恶地`(BADLANDS，直用**原版生成器**生成真实原版地形) / `下界`(NETHER，地狱岩起伏+岩浆湖+灵魂沙峡谷+玄武岩柱，红色天空) / `城市`(CITY，街道网格+建筑群+公园绿地) / `正常主世界`(NORMAL，**原版生成器**生成真实原版主世界多样地貌，大尺寸 1056x1056，有边境墙/围墙 + 可进地狱/末地传送门)。
- **各世界战利品差异化**：`world-loot.<key>` 配置每世界的箱子数量/品质权重。正常主世界箱子少+品质低（基础世界），其余世界箱子多+品质高。
- **永远白天**：所有世界 `DO_DAYLIGHT_CYCLE=false` + 锁定正午 6000，无夜晚。

## 对局玩法（PVP 吃鸡，最后一人胜）

| 模式 | 说明 | 胜负 |
|---|---|---|
| `solo` 单人 | 无 PvP，各自搜刮 | 时间到(默认30分钟)按开箱+击杀积分结算；**无人数限制，管理员可开** |
| `pvp` 玩家对战 | 全图 PvP | 最后存活者获胜 |
| `team` 组队对战 | 自动分队，同队免伤 | 最后存活队伍获胜 |

**流程**：`/box game join` 报名 → 管理员 `/box game start <solo|pvp|team>` → 倒计时 → 传送到出生广场聚集 → 搜刮物资/PvP → 仅剩 1 人（或单人时间到）→ **自动送剩余玩家回大厅** → 自动恢复地形（清箱重投+重建建筑）。

**淘汰玩家**：死亡后提示可用 `/box lobby` 回大厅 或 `/box spectate` 旁观剩余对战。

## 对局信息显示（BossBar + ActionBar + Title，实时）

Lophine(Folia) 26.2 全面禁用 Bukkit Scoreboard API —— `getNewScoreboard()` 即使在主线程也抛 UnsupportedOperationException。因此对局信息改走**纯发包通道**，不再使用任何 Bukkit Scoreboard 类（彻底消除刷屏）：

- **BossBar**（顶部进度条）：对局倒计时数字 + 剩余时间 + 存活/击杀/死亡实时数据。
- **ActionBar**（底部）：模式 / 开箱数 / 击杀 / 死亡，淘汰后提示回大厅或旁观。
- **Title**（屏幕中央大屏）：开赛前 5 秒每秒大屏倒计时数字。

多房间时每个房间绑定独立信息显示实例，互不干扰。

## 对局房间 & 多世界

- **多世界池**：`arena_1`、`arena_desert_1`、`arena_islands_1` 等，每种地形独立生成、独立预生成、独立物资。
- **对局房间**：每个房间是一个独立的状态机，绑定一个 arena 世界。`/box room` 系列命令：
  - `/box room list` 房间列表（房主/成员/状态）
  - `/box room create <id>` 创建绑定当前世界的房间（普通玩家可建，建房者即房主）
  - `/box room join <id>` / `leave <id>` 加入/退出房间报名
  - `/box room invite <玩家> [房间]` 邀请在线玩家加入你的房间（房主）
  - `/box room info <id>` 房间详情（成员/房主）
  - `/box room start <id> <solo|pvp|team>` 在某房间开对局（管理员）
  - `/box room stop <id>` / `remove <id>`（管理员）
  - `/box room force <玩家> <房间>` 强制把玩家加入房间（管理员）
- **邀请系统**：房主邀请 → 被邀请者聊天框弹出可点击 `[接受] [拒绝]`，点击即加入/拒绝（`/box invite accept|decline`）。
- **GUI**：主菜单"对局房间" → 房间列表GUI（加入/创建/邀请）；邀请面板列出在线玩家点击邀请。
- 默认房间 `default` 绑定当前 arena 世界，行为与原单对局完全一致。

## 物资箱 & 自定义道具

- 全图随机投放 **五档稀有度** 物资箱，按权重（普通50/精良27/稀有15/传说7/绝世1）。
- **自定义品质道具**：config `loot` 段条目支持 `name` + `lore` 字段（可自定义物品名/描述，如"绝世之刃"），配合 `chance` 概率随机生成。
- **大型物资建筑**：地图随机位置建造石砖建筑，室内布置 4~6 个物资箱（稀有度倾向提升），箱子聚集点。
- 位置优化：箱子要求周围 3x3 平坦开阔（排除陡坡/树顶/水面）；45 分钟到期换位；搬空异地补货。
- 树木低密度（每区块最多 1 棵，82% 区块无树），开阔对战不挡视野。

## 神器合成（碎片/材料 → 工作台）

- **碎片/材料**从物资箱掉落（loot 表 `craft: <key>` 引用）：各神器专属碎片 + 通用材料（神器核心/秘银锭/星辰粉尘）。
- 收集足够碎片/材料后，`/box craft` 打开 **神器工作台** GUI，放入材料点击合成，重铸对应神器。
- config `crafting` 段：`fragments`（碎片/材料定义）+ `recipes`（配方：产出神器 artifact，需要 ingredients 碎片:数量）。
- 碎片/材料为合成材料，**不可回收**（商店拒绝）。

## 预生成（1200 区块/秒）

`world.pregen-batch: 20` + `world.pregen-interval-ticks: 1` = 每秒 60×20 = 1200 区块。切世界时 `ensurePregen` 保证目标世界先预生成，玩家进入不会被卡。

## 命令

| 命令 | 说明 | 权限 |
|---|---|---|
| `/box` | 主菜单 GUI | terrabox.use |
| `/box spawn` | 随机陆地出生（300s 冷却） | terrabox.use |
| `/box sell` / `prices` | 回收商店 / 价格表 | terrabox.use |
| `/box craft` | 神器合成工作台 | terrabox.use |
| `/box artifacts` | 神器图鉴（只读展示神器效果/获取途径） | terrabox.use |
| `/box hunt` | 寻宝方位提示 | terrabox.use |
| `/box top` / `stats` | 排行榜 / 我的统计 | terrabox.use |
| `/box join` | 报名对局 | terrabox.use |
| `/box lobby` | 返回大厅 | terrabox.use |
| `/box spectate` | 淘汰后旁观 | terrabox.use |
| `/box terrain` | 选择对局地形（GUI） | terrabox.admin |
| `/box game start <solo|pvp|team>` | 开始对局 | terrabox.admin |
| `/box game stop` / `status` | 终止 / 状态 | terrabox.admin |
| `/box admin boxes|fill|airdrop|wipe` | 管理 | terrabox.admin |
| `/box reload` | 重载配置 | terrabox.admin |

## 部署

1. `TerraBox-1.0.0.jar` 放 `plugins/`，**完整重启**（勿热载）。
2. 首次启动：创建大厅+对局世界 → 预生成（1200区块/s）→ 投放箱子 → 建建筑/广场。
3. 配置 `plugins/TerraBox/config.yml`，改完 `/box reload`。
4. 服务器旧 config.yml 不会被覆盖，需手动删除后重启（否则缺 lobby/arena 段）。

## 数据文件

- `playerdata/<uuid>.yml` — 玩家开箱/积分统计（异步原子写）
- `boxes.yml` — 物资箱注册表
- `pregen_<world>.done` — 各世界预生成完成标记

## 构建

```sh
sh build.sh   # javac25 + lophine-api-26.2 + vault-api + adventure (编译期依赖, 不打包)
```
