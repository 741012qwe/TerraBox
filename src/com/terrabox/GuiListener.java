package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * GUI 事件分派 (InventoryHolder 机制, 白皮书 §6.1):
 *  - 用 Bukkit 原生 InventoryHolder 标记 GUI 类型, 事件里 getHolder() 直接识别
 *    (不再依赖 inventory 对象比较 —— Folia 下 view top inventory 可能与创建对象不一致)
 *  - MENU: 按槽位分发玩法入口
 *  - SELL: 上区自由放取, 按钮槽位禁止移动, 关闭退回物品
 */
public class GuiListener implements Listener {
    private final TerraBoxPlugin plugin;

    public GuiListener(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    enum Type { MENU, SELL, TERRAIN, GAME, CRAFT, ARTIFACT, INVITE, ROOM }

    /** GUI 容器标识: 挂在 createInventory 的 holder 上, 跨线程只读安全 */
    public static class GuiHolder implements InventoryHolder {
        public final Type type;
        public Inventory inv;
        public int craftIndex = 0; // 工作台当前配方索引 (CRAFT 类型)
        public String inviteRoom = null; // 邀请目标房间 (INVITE 类型)
        public GuiHolder(Type type) {
            this.type = type;
        }
        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private GuiHolder holderOf(Inventory top) {
        return (top != null && top.getHolder() instanceof GuiHolder gh) ? gh : null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        GuiHolder gh = holderOf(top);
        if (gh == null) return;
        Player p = (Player) e.getWhoClicked();
        int raw = e.getRawSlot();

        if (gh.type == Type.MENU) {
            e.setCancelled(true);
            if (raw < 0 || raw >= top.getSize()) return;
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() == Material.AIR) return;
            switch (raw) {
                case 10 -> plugin.spawns().spawnPlayer(p, true);
                case 12 -> plugin.sells().open(p);
                case 14 -> plugin.hunts().hunt(p);
                case 16 -> plugin.cmd().sendTop(p);
                case 11 -> sendDistribution(p);
                case 15 -> plugin.cmd().sendStats(p, p);
                case 13 -> p.sendMessage(plugin.msg("prefix") + "§e玩法说明已列在菜单图标中, /box 也可随时查看。");
                case 18 -> plugin.gameGui().open(p); // 对局模式选择(GUI)
                case 22 -> plugin.cmd().sendGameStatus(p); // 对局状态
                case 19 -> plugin.rooms().requestReturnToLobby(p);  // 返回大厅 (对局中禁止)
                case 21 -> plugin.terrainSelect().open(p); // 选择对局地形
                case 17 -> plugin.roomGui().open(p); // 对局房间列表
                default -> {}
            }
            return;
        }

        if (gh.type == Type.GAME) {
            e.setCancelled(true);
            if (raw < 0 || raw >= top.getSize()) return;
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() == Material.AIR) return;
            switch (raw) {
                case 10 -> joinMode(p, GameManager.Mode.SOLO);   // 单人
                case 13 -> joinMode(p, GameManager.Mode.PVP);    // 多人PVP
                case 16 -> joinMode(p, GameManager.Mode.TEAM);   // 组队
                case 18 -> plugin.cmd().sendGameStatus(p);       // 状态
                case 22 -> plugin.menus().open(p);               // 返回主菜单
                case 24 -> {
                    if (p.hasPermission("terrabox.admin")) {
                        p.sendMessage(plugin.msg("prefix")
                                + "§e管理员开赛命令: §6/box room start <solo|pvp|team> <solo|pvp|team>");
                        p.sendMessage(plugin.msg("prefix")
                                + "§7例: §e/box room start pvp pvp §7(在pvp房间开多人对战)");
                    }
                }
                default -> {}
            }
            return;
        }

        if (gh.type == Type.TERRAIN) {
            e.setCancelled(true);
            if (raw < 0 || raw >= top.getSize()) return;
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() == Material.AIR) return;
            switch (raw) {
                case 10 -> selectTerrain(p, TerrainType.DEFAULT);
                case 11 -> selectTerrain(p, TerrainType.DESERT);
                case 12 -> selectTerrain(p, TerrainType.ISLANDS);
                case 13 -> selectTerrain(p, TerrainType.THE_END);
                case 14 -> selectTerrain(p, TerrainType.BADLANDS);
                case 15 -> selectTerrain(p, TerrainType.NETHER);
                case 16 -> selectTerrain(p, TerrainType.CITY);
                case 17 -> selectTerrain(p, TerrainType.NORMAL);
                case 22 -> p.closeInventory(); // 取消
                case 26 -> createNewTerrainWorld(p); // 生成新世界
                default -> {}
            }
            return;
        }

        if (gh.type == Type.CRAFT) {
            // 按钮区一律取消; 材料槽只接受合法碎片/材料(防止神器等误放)
            if (raw >= 0 && raw < top.getSize() && isCraftMatSlot(raw)) {
                // 材料槽: 禁止 Shift/Double/数字键/副手交换批量移动, 只允许普通单击放/取
                if (e.getClick().isShiftClick() || e.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK
                        || e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                        || e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
                    e.setCancelled(true);
                    return;
                }
                e.setCancelled(true); // 完全接管材料槽交互, 手动做光标↔槽放/取, 防止材料错乱飘进背包
                ItemStack cursor = e.getCursor();
                ItemStack current = e.getCurrentItem();
                boolean cursorOk = cursor == null || cursor.getType().isAir() || plugin.crafts().isCraftItem(cursor);
                if (!cursorOk) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1.2f);
                    p.sendMessage(plugin.msg("prefix") + "§c只能放入碎片/材料, 无法放置此物品。");
                    return; // 光标不合法, 什么都不做
                }
                // 手动交换: 槽内物品与光标物品互换 (光标空=取出, 槽空=放入, 都有=交换)
                ItemStack newSlot = cursor;
                ItemStack newCursor = current;
                // 若槽内已有非材料(异常残留), 禁止操作
                if (current != null && !current.getType().isAir() && !plugin.crafts().isCraftItem(current)) {
                    p.sendMessage(plugin.msg("prefix") + "§c材料槽存在异常物品, 请关闭重开。");
                    return;
                }
                e.setCurrentItem(newSlot != null && newSlot.getType().isAir() ? null : newSlot.clone());
                e.getView().setCursor(newCursor != null && newCursor.getType().isAir() ? null : newCursor.clone());
                return;
            }
            if (raw < 0 || raw >= top.getSize()) return; // 背包区放行
            e.setCancelled(true);
            if (raw == CraftGui.OUTPUT_SLOT) return; // 产物只读
            switch (raw) {
                case CraftGui.PREV_SLOT -> {
                    GuiListener.GuiHolder hh = gh;
                    // 翻页 = 切换配方: 清空材料槽并重新渲染新配方需求 (不保留旧配方材料, 避免错乱)
                    hh.craftIndex = (hh.craftIndex - 1 + plugin.crafts().recipes().size())
                            % Math.max(1, plugin.crafts().recipes().size());
                    plugin.craftsGui().render(p, top, hh);
                }
                case CraftGui.NEXT_SLOT -> {
                    GuiListener.GuiHolder hh = gh;
                    hh.craftIndex = (hh.craftIndex + 1) % Math.max(1, plugin.crafts().recipes().size());
                    plugin.craftsGui().render(p, top, hh);
                }
                case CraftGui.CRAFT_SLOT -> plugin.craftsGui().craft(p, top, gh);
                case CraftGui.CLOSE_SLOT -> p.closeInventory();
                default -> {}
            }
            return;
        }

        if (gh.type == Type.ARTIFACT) {
            // 神器图鉴: 全只读, 禁止任何点击移动
            e.setCancelled(true);
            return;
        }

        if (gh.type == Type.INVITE) {
            // 邀请面板: 只读, 点击玩家头邀请; 一切移动取消
            e.setCancelled(true);
            if (raw < 0 || raw >= top.getSize()) return;
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() == Material.AIR) return;
            String targetRoom = gh.inviteRoom != null ? gh.inviteRoom : "default";
            // 玩家头: 名字从 displayName 提取 (去掉颜色码)
            if (e.getCurrentItem().getType() == Material.PLAYER_HEAD && e.getCurrentItem().getItemMeta() != null) {
                String nm = org.bukkit.ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                Player target = Bukkit.getPlayer(nm);
                if (target != null && target.isOnline()) {
                    plugin.invites().invite(p, target, targetRoom);
                } else {
                    p.sendMessage(plugin.msg("prefix") + "§c该玩家不在线。");
                }
            }
            return;
        }

        if (gh.type == Type.ROOM) {
            // 房间列表: 点击加入/退出房间; 底部按钮创建/邀请/返回
            e.setCancelled(true);
            if (raw < 0 || raw >= top.getSize()) return;
            if (raw == RoomGui.CREATE_SLOT) {
                // 创建房间 (自动命名 room_N)
                plugin.cmd().createRoomFor(p, null);
                return;
            }
            if (raw == RoomGui.INVITE_SLOT) {
                // 打开邀请面板 (邀请到自己报名的房间, 无则 default)
                String myRoom = plugin.rooms().defaultRoom().roomId();
                for (GameManager g : plugin.rooms().joinedRooms(p.getUniqueId())) { myRoom = g.roomId(); break; }
                plugin.inviteGui().open(p, myRoom);
                return;
            }
            if (raw == RoomGui.BACK_SLOT) {
                plugin.menus().open(p);
                return;
            }
            if (raw == RoomGui.CLOSE_SLOT) {
                p.closeInventory();
                return;
            }
            // 点击房间条目 → 加入/退出报名
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() == Material.AIR) return;
            String nm = org.bukkit.ChatColor.stripColor(it.getItemMeta().getDisplayName());
            // 名字形如 "房间 <id>" 或 "§a房间 §f<id>" → 取 id
            String roomId = nm.replace("房间", "").trim();
            if (!roomId.isEmpty()) {
                GameManager g = plugin.rooms().get(roomId);
                if (g != null) {
                    if (g.isInGame(p.getUniqueId())) g.leave(p);
                    else g.join(p);
                    plugin.roomGui().render(p, top, gh); // 刷新
                }
            }
            return;
        }

        // SELL
        if (raw < top.getSize()) {
            // 上区: 按钮槽禁止动, 其余自由放取
            if (raw == SellGui.CONFIRM_SLOT) {
                e.setCancelled(true);
                if (e.getClick().isLeftClick() || e.getClick().isRightClick()) {
                    plugin.sells().settle(p, top);
                    refreshSellInfo(top);
                }
                return;
            }
            if (raw >= 45) e.setCancelled(true);
            return;
        }
        // 下区(背包): 放行
    }

    private void refreshSellInfo(Inventory top) {
        long total = 0;
        int items = 0;
        Map<String, Double> prices = plugin.sellPrices();
        for (int slot = 0; slot < 45; slot++) {
            ItemStack it = top.getItem(slot);
            if (it == null || it.getType().isAir()) continue;
            Double price = prices.get(it.getType().name());
            if (price != null && price > 0) {
                total += (long) Math.floor(price * it.getAmount());
                items += it.getAmount();
            }
        }
        ItemStack confirm = top.getItem(SellGui.CONFIRM_SLOT);
        if (confirm != null && confirm.getItemMeta() != null) {
            var meta = confirm.getItemMeta();
            meta.setDisplayName("§a§l确认出售");
            meta.setLore(java.util.Arrays.asList(
                    "§7待回收: §e" + items + " 件",
                    "§7预计获得: §e" + total + " 元",
                    "", "§e点击结算"));
            confirm.setItemMeta(meta);
        }
    }

    /** 工作台材料放置槽 */
    private boolean isCraftMatSlot(int raw) {
        for (int s : CraftGui.MAT_SLOTS) if (raw == s) return true;
        return false;
    }

    private void sendDistribution(Player p) {
        var counts = plugin.boxes().countByRarity();
        p.sendMessage(plugin.msg("prefix") + "§e地图物资箱分布 (共 §a" + plugin.boxes().count() + "§e 个):");
        for (Rarity r : Rarity.values()) {
            p.sendMessage(" §7" + r.display + ": " + r.colorCode.replace('&', '\u00A7') + counts.getOrDefault(r, 0) + " 个");
        }
        p.sendMessage(plugin.msg("prefix") + "§7下一波空投: §d约 " + (plugin.airdrops().secondsUntilNext() / 60 + 1) + " 分钟后");
    }

    /** 选择地形并切换当前对局世界 (管理员用) */
    /** 加入指定模式的对局房间 (单人/PVP/组队), 绑定当前 arena 世界 */
    private void joinMode(Player p, GameManager.Mode mode) {
        String roomId = switch (mode) {
            case SOLO -> "solo";
            case TEAM -> "team";
            default -> "pvp";
        };
        // 创建/获取该模式房间 (绑定当前 arena 世界名)
        String worldName = plugin.worlds().world() != null ? plugin.worlds().world().getName() : null;
        GameManager room = plugin.rooms().createRoom(roomId, worldName);
        room.toggleJoin(p);
        // 刷新 GUI 显示报名状态
        p.closeInventory();
        plugin.gameGui().open(p);
    }

    private void selectTerrain(Player p, TerrainType type) {
        if (!p.hasPermission("terrabox.admin")) {
            p.sendMessage(plugin.msg("no-permission"));
            return;
        }
        boolean ok = plugin.arenas().selectByTerrain(type);
        if (ok) {
            String name = plugin.arenas().current() != null ? plugin.arenas().current().getName() : type.display;
            p.sendMessage(plugin.msg("prefix") + "§a已选择地形: " + type.colorCode + type.display
                    + " §7(世界: §e" + name + "§7)");
            p.sendMessage(plugin.msg("prefix") + "§6正在重新初始化该世界地形与物资箱...");
            plugin.switchArena();
            p.sendMessage(plugin.msg("prefix") + "§e地图已就绪, 用 §a/box game start <solo|pvp|team> §e开赛。");
            p.closeInventory();
        } else {
            p.sendMessage(plugin.msg("prefix") + "§c地形世界创建失败, 请检查控制台日志。");
        }
    }

    /** 按当前选定地形额外生成一个新对局世界并加入 */
    private void createNewTerrainWorld(Player p) {
        if (!p.hasPermission("terrabox.admin")) {
            p.sendMessage(plugin.msg("no-permission"));
            return;
        }
        TerrainType type = TerrainType.DEFAULT; // 默认生成默认地形; 管理员可在命令里指定
        String arg = "default";
        org.bukkit.World w = plugin.arenas().createNew(TerrainType.parse(arg));
        if (w != null) {
            // 新世界也需要强制白天 + 预生成
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
                try {
                    w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                    w.setTime(6000);
                } catch (Throwable ignored) {}
                plugin.arenas().select(w.getName());
            });
            p.sendMessage(plugin.msg("prefix") + "§a已生成新对局世界: §e" + w.getName());
            p.closeInventory();
        } else {
            p.sendMessage(plugin.msg("prefix") + "§c新对局世界创建失败。");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        GuiHolder gh = holderOf(top);
        if (gh == null) return;
        if (gh.type == Type.MENU || gh.type == Type.TERRAIN || gh.type == Type.GAME) {
            e.setCancelled(true);
            return;
        }
        if (gh.type == Type.CRAFT) {
            // 工作台: 全面禁止拖拽(材料只能用点击放入/取出, 防拖拽导致材料丢失/复制)。
            // 拖拽涉及任何槽位(材料槽/按钮/背包)一律取消。
            e.setCancelled(true);
            return;
        }
        if (gh.type == Type.ARTIFACT) {
            // 神器图鉴: 全部只读, 禁止任何拖动
            e.setCancelled(true);
            return;
        }
        if (gh.type == Type.INVITE || gh.type == Type.ROOM) {
            // 邀请面板 / 房间列表: 全只读, 禁止拖动
            e.setCancelled(true);
            return;
        }
        // SELL: 允许在背包(>=top.size)与上区 0..44(除确认按钮)之间拖放; 触及按钮区一律取消。
        for (int raw : e.getRawSlots()) {
            if (raw >= top.getSize()) continue;                        // 玩家背包, 放行
            if (raw >= 0 && raw < 45 && raw != SellGui.CONFIRM_SLOT) continue; // 合法上区
            e.setCancelled(true);                                       // 按钮/说明等取消
            return;
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        GuiHolder gh = holderOf(e.getInventory());
        if (gh == null) return;
        if (gh.type == Type.SELL) {
            // 玩家区域线程: 退回未出售物品
            plugin.sells().returnItems(p, e.getInventory());
        }
        if (gh.type == Type.CRAFT) {
            // 工作台关闭: 材料槽中的碎片/材料退回背包
            for (int slot : CraftGui.MAT_SLOTS) {
                ItemStack it = e.getInventory().getItem(slot);
                if (it == null || it.getType().isAir()) continue;
                var m = p.getInventory().addItem(it);
                for (ItemStack r : m.values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
                e.getInventory().setItem(slot, null);
            }
        }
    }
}
