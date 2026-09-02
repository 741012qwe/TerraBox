package com.terrabox;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 特殊道具系统 —— 赋予物品自定义"使用效果" (右键触发)
 *
 * 支持的效果类型 (config special-items.<key>.effect):
 *   FIREBALL   火焰弹砸地: 向准星方向地面砸下一颗火球, 不伤自己, 对范围内其他玩家
 *              造成半心(0.5)伤害, 并按原版 TNT 距离把玩家炸飞
 *   HEAL       治疗: 范围回血
 *   LIGHTNING  雷击: 准星处落雷, 对范围内敌人造成伤害
 *   SPEED      急速: 获得速度 buff
 *   JUMP       跳跃提升 buff
 *   FREEZE     冰冻: 范围敌人减速+发光
 *   SHIELD     抗性提升 buff
 *   FIRE_RES   防火 buff
 *   TNT_LAUNCH 掷出受控 TNT (不伤自己)
 *   REPULSE    击退波: 把周围玩家按击退距离炸飞 (不造成任何伤害)
 *
 * 线程模型:
 *  - 构建物品: 纯对象 (任意线程)
 *  - 触发效果 (PlayerInteractEvent): 玩家区域线程; 范围内实体操作经 Block/entity 区域线程
 *  - 特殊道具通过 PDC 标记 (terrabox_special=effectKey), 消费后减少数量
 */
public class SpecialItemManager implements Listener {
    public static final String TITLE = "特殊道具";
    private final TerraBoxPlugin plugin;
    private final NamespacedKey keySpecial;
    private final NamespacedKey keyData;
    private final NamespacedKey keyTntInstant;
    private final Map<String, SpecialDef> defs = new HashMap<>();
    // 追踪器: 使用者UUID → 被追踪者UUID; 使用者UUID → 到期时刻(ms); 使用者UUID → 追踪方向/距离文本缓存
    private final Map<UUID, UUID> trackTargets = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> trackExpiry = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, String> trackTexts = new java.util.concurrent.ConcurrentHashMap<>();
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask trackTask;

    /** 读取指定玩家的追踪方向/距离文本 (任意线程; 无则空) 供计分板合并到同一 BossBar 显示 */
    public String trackingText(UUID u) { return trackTexts.getOrDefault(u, ""); }

    /** 特殊道具定义 (从 config 解析) */
    public record SpecialDef(Material material, String key, String name, List<String> lore,
                             String effect, double radius, double damage,
                             double velocity, int durationSeconds) {}

    public SpecialItemManager(TerraBoxPlugin plugin) {
        this.plugin = plugin;
        this.keySpecial = new NamespacedKey(plugin, "special");
        this.keyData = new NamespacedKey(plugin, "special_data");
        this.keyTntInstant = new NamespacedKey(plugin, "tnt_instant");
    }

    public void load() {
        defs.clear();
        var sec = plugin.getConfig().getConfigurationSection("special-items");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            // 配置缺失/为空: 注册内置默认特殊道具 (保证功能可用, 旧 config 服务器也能用)
            plugin.getLogger().info("特殊道具表: 未检测到配置段, 已加载内置默认道具");
            registerDefaults();
            return;
        }
        for (String key : sec.getKeys(false)) {
            var s = sec.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Material mat = Material.matchMaterial(s.getString("material", "FIRE_CHARGE").toUpperCase(Locale.ROOT));
                if (mat == null || !mat.isItem()) {
                    plugin.getLogger().warning("特殊道具 [" + key + "] 材质无效: " + s.getString("material"));
                    continue;
                }
                String effect = s.getString("effect", "FIREBALL").toUpperCase(Locale.ROOT);
                String name = s.getString("name", "&e" + key);
                List<String> lore = new ArrayList<>(s.getStringList("lore"));
                double radius = s.getDouble("radius", 4.0);
                double damage = s.getDouble("damage", 0.5);
                double velocity = s.getDouble("velocity", 1.4);
                int dur = s.getInt("duration-seconds", 5);
                defs.put(key.toLowerCase(Locale.ROOT),
                        new SpecialDef(mat, key.toLowerCase(Locale.ROOT), name, lore,
                                effect, radius, damage, velocity, dur));
            } catch (Exception e) {
                plugin.getLogger().warning("特殊道具 [" + key + "] 解析失败: " + "错误";
            }
        }
        plugin.getLogger().info("特殊道具表加载完成: " + defs.size() + " 种");
    }

    public int size() { return defs.size(); }

    /** 启动追踪器后台任务 (每 10 tick 刷新一次所有追踪者的 ActionBar 方位提示) */
    public void start() {
        stop();
        trackTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> tickTracks(), 10L, 10L);
    }

    /** 停止追踪器后台任务 */
    public void stop() {
        if (trackTask != null) { trackTask.cancel(); trackTask = null; }
        trackTargets.clear();
        trackExpiry.clear();
        trackTexts.clear();
    }

    /** 追踪器主循环 (Global 线程): 给所有追踪者刷新锁定目标的方位+距离 (存入 trackTexts, 由计分板合并显示, 不单独开 BossBar/ActionBar) */
    private void tickTracks() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, UUID> e : trackTargets.entrySet()) {
            UUID hunter = e.getKey();
            UUID prey = e.getValue();
            Long exp = trackExpiry.get(hunter);
            if (exp == null || now > exp) {
                trackTargets.remove(hunter);
                trackExpiry.remove(hunter);
                trackTexts.remove(hunter);
                continue;
            }
            Player hp = Bukkit.getPlayer(hunter);
            if (hp == null || !hp.isOnline()) { trackTargets.remove(hunter); trackExpiry.remove(hunter); trackTexts.remove(hunter); continue; }
            Player pr = Bukkit.getPlayer(prey);
            if (pr == null || !pr.isOnline()) {
                // 目标离线/丢失: 提示并解除
                trackTargets.remove(hunter);
                trackExpiry.remove(hunter);
                trackTexts.remove(hunter);
                hp.sendActionBar(net.kyori.adventure.text.serializer.legacy
                        .LegacyComponentSerializer.legacyAmpersand().deserialize("§c追踪目标已消失!"));
                continue;
            }
            final Player ffp = hp;
            final Player fprey = pr;
            final long fexp = exp;
            ffp.getScheduler().run(plugin, task -> {
                try {
                    Location hl = ffp.getLocation();
                    Location pl = fprey.getLocation();
                    if (!hl.getWorld().equals(pl.getWorld())) return;
                    double dx = pl.getX() - hl.getX();
                    double dz = pl.getZ() - hl.getZ();
                    double dy = pl.getY() - hl.getY();
                    int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz + dy * dy));
                    String dir = trackCompass(dx, dz);
                    long remain = Math.max(0, (fexp - System.currentTimeMillis()) / 1000);
                    StringBuilder sb = new StringBuilder();
                    sb.append("§e§l▣追踪[§a").append(fprey.getName()).append("§e]§7→§f").append(dir)
                            .append("§7 §f").append(dist).append("格§7(高差")
                            .append(dy >= 0 ? "+" : "").append((int) dy).append(")§7剩§c")
                            .append(remain).append("秒");
                    trackTexts.put(ffp.getUniqueId(), sb.toString());
                } catch (Throwable ignored) {}
            }, () -> {});
        }
    }

    /** 移除指定玩家的追踪文本缓存 */
    private void removeTrackBar(UUID hunter) {
        trackTexts.remove(hunter);
    }
    /** 追踪罗盘方位 (从追踪者指向目标) */
    private String trackCompass(double dx, double dz) {
        String[] names = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        deg = (deg + 360) % 360;
        int idx = (int) Math.floor((deg + 22.5) / 45.0) % 8;
        return names[idx] + "§7(§f" + (int) deg + "°§7)";
    }

    /** 追踪器是否在追踪 (供清理) */
    public boolean isTracking(UUID hunter) { return trackTargets.containsKey(hunter); }
    public UUID trackingTarget(UUID hunter) { return trackTargets.get(hunter); }
    public void stopTracking(UUID hunter) { trackTargets.remove(hunter); trackExpiry.remove(hunter); trackTexts.remove(hunter); }
    /** 所有追踪中的玩家 (用于对局结束清理) */
    public java.util.Set<UUID> trackingPlayers() { return java.util.Set.copyOf(trackTargets.keySet()); }

    /** 内置默认特殊道具 (config 缺失兜底, 保证核心效果可用) */
    private void registerDefaults() {
        defs.put("fireball_tnt", new SpecialDef(Material.FIRE_CHARGE, "fireball_tnt",
                "&6&l震地火弹", List.of("&7右键朝地面砸下, 引爆范围冲击。",
                        "&7不伤自己, 对周围玩家造成 &c半心 &7伤害, 并按 TNT 力度炸飞。"),
                "FIREBALL", 4.0, 0.5, 1.6, 5));
        defs.put("fireball_big", new SpecialDef(Material.FIRE_CHARGE, "fireball_big",
                "&d&l烈焰震地弹", List.of("&7右键朝地面砸下, 大范围爆炸。",
                        "&7不伤自己, 对周围玩家造成 &c半心 &7伤害, 并炸飞得更远。"),
                "FIREBALL", 6.0, 0.5, 2.2, 5));
        defs.put("heal_potion", new SpecialDef(Material.POTION, "heal_potion",
                "&a&l回春药剂", List.of("&7右键使用, 恢复范围内生命 4~8 心。"),
                "HEAL", 5.0, 0, 0, 0));
        defs.put("lightning_wand", new SpecialDef(Material.BLAZE_ROD, "lightning_wand",
                "&b&l引雷杖", List.of("&7右键朝准星方向召来落雷, 对范围敌人造成高额伤害。"),
                "LIGHTNING", 4.0, 1.5, 0, 0));
        defs.put("speed_charge", new SpecialDef(Material.SUGAR, "speed_charge",
                "&e&l疾风冲锋", List.of("&7右键使用, 获得速度提升 12 秒。"),
                "SPEED", 0, 0, 0, 12));
        defs.put("jump_charge", new SpecialDef(Material.RABBIT_FOOT, "jump_charge",
                "&a&l弹跳强化", List.of("&7右键使用, 获得跳跃提升 12 秒。"),
                "JUMP", 0, 0, 0, 12));
        defs.put("freeze_crystal", new SpecialDef(Material.ICE, "freeze_crystal",
                "&b&l冰霜禁锢", List.of("&7右键使用, 冻结周围敌人 8 秒。"),
                "FREEZE", 5.0, 0, 0, 8));
        defs.put("shield_charge", new SpecialDef(Material.SHIELD, "shield_charge",
                "&f&l铁壁护盾", List.of("&7右键使用, 获得抗性提升 10 秒。"),
                "SHIELD", 0, 0, 0, 10));
        defs.put("fire_res_charge", new SpecialDef(Material.BLAZE_POWDER, "fire_res_charge",
                "&c&l烈焰抗性", List.of("&7右键使用, 获得防火效果 15 秒。"),
                "FIRE_RES", 0, 0, 0, 15));
        defs.put("tnt_charge", new SpecialDef(Material.TNT, "tnt_charge",
                "&6&l轰天炸药", List.of("&7右键掷出一枚不会伤及自己的 TNT。"),
                "TNT_LAUNCH", 5.0, 0, 0, 0));
        defs.put("repulse_wave", new SpecialDef(Material.FIREWORK_STAR, "repulse_wave",
                "&d&l冲击波", List.of("&7右键释放击退波, 把周围玩家炸飞 (不伤害)。"),
                "REPULSE", 6.0, 0, 2.0, 0));
        defs.put("enemy_tracker", new SpecialDef(Material.COMPASS, "enemy_tracker",
                "&e&l追踪罗盘", List.of("&7右键使用, 锁定一名随机存活敌人。",
                        "&7在其头顶持续显示方向与距离 §a25 §7秒。"),
                "TRACK", 0, 0, 0, 25));
    }

    /** 按 key 构建特殊道具 ItemStack (任意线程, 纯对象) */
    public ItemStack buildItem(String key) {
        SpecialDef def = defs.get(key.toLowerCase(Locale.ROOT));
        if (def == null) return null;
        ItemStack it = new ItemStack(def.material, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            Component nameComp = LegacyComponentSerializer.legacyAmpersand().deserialize(def.name);
            meta.displayName(nameComp);
            List<Component> loreComps = new ArrayList<>();
            for (String line : def.lore) loreComps.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
            meta.lore(loreComps);
            it.setItemMeta(meta);
        }
        // PDC 标记特殊道具 (不可被当作普通材料回收)
        it.editMeta(m -> {
            var pdc = m.getPersistentDataContainer();
            pdc.set(keySpecial, PersistentDataType.STRING, def.key);
            pdc.set(keyData, PersistentDataType.STRING, def.effect);
        });
        return it;
    }

    /** 判断物品是否为特殊道具, 返回 effectKey (非特殊返回 null) */
    public String specialKey(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return null;
        try {
            return it.getItemMeta().getPersistentDataContainer()
                    .get(keySpecial, PersistentDataType.STRING);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 判断物品是否为特殊道具 (用于回收站排除) */
    public boolean isSpecial(ItemStack it) {
        return specialKey(it) != null;
    }

    /** 随机取一个特殊道具 key (任意线程) */
    public String randomKey() {
        if (defs.isEmpty()) return null;
        List<SpecialDef> list = new ArrayList<>(defs.values());
        return list.get(ThreadLocalRandom.current().nextInt(list.size())).key;
    }

    /**
     * 触发特殊道具效果 (玩家区域线程调用, 由 PlayerInteractEvent 进入)
     * 返回 true 表示消费该道具 (减数量), false 表示不消费
     */
    public boolean trigger(Player player, ItemStack item) {
        return trigger(player, item, false);
    }

    /**
     * 触发特殊道具效果 (玩家区域线程调用, 由 PlayerInteractEvent 进入)
     * @param offHand true=道具在副手, false=主手 (徒手/主手右键)
     * 返回 true 表示消费该道具 (减数量), false 表示不消费
     */
    public boolean trigger(Player player, ItemStack item, boolean offHand) {
        String key = specialKey(item);
        if (key == null) return false;
        SpecialDef def = defs.get(key);
        if (def == null) return false;
        try {
            // 先执行效果 (可能抛异常/失败), 全部成功后最后再 consume
            // 避免"效果未生效但道具已被消耗"的错乱 (consume 在效果前执行的 bug)
            // TRACK 特判: 无目标时返回 false, 不消费道具
            boolean ok = switch (def.effect) {
                case "FIREBALL" -> { fireball(player, def); yield true; }
                case "HEAL" -> { heal(player, def); yield true; }
                case "LIGHTNING" -> { lightning(player, def); yield true; }
                case "SPEED" -> { applyPotion(player, PotionEffectType.SPEED, def); yield true; }
                case "JUMP" -> { applyPotion(player, PotionEffectType.JUMP_BOOST, def); yield true; }
                case "FREEZE" -> { freeze(player, def); yield true; }
                case "SHIELD" -> { applyPotion(player, PotionEffectType.RESISTANCE, def); yield true; }
                case "FIRE_RES" -> { applyPotion(player, PotionEffectType.FIRE_RESISTANCE, def); yield true; }
                case "TNT_LAUNCH" -> { tntLaunch(player, def); yield true; }
                case "REPULSE" -> { repulse(player, def); yield true; }
                case "TRACK" -> track(player, def);
                default -> { plugin.getLogger().warning("特殊道具 [" + key + "] 未知效果: " + def.effect); yield false; }
            };
            if (!ok) return false; // 效果未成功(如追踪无目标), 不消费
            // 效果已成功, 最后消耗道具 (一次只用一个)
            consume(player, item, def, offHand);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("特殊道具 [" + key + "] 触发异常: " + t.getClass().getSimpleName()
                    + " - " + "错误";
            return false;
        }
    }

    /** 消费道具: 数量-1 (一次只用一个, 不是整组), 为0则移除对应手 (玩家区域线程) */
    private boolean consume(Player player, ItemStack item, SpecialDef def, boolean offHand) {
        // 从执行线程操作, 需在玩家区域线程 —— 已保证 (interact 事件线程)
        item.setAmount(item.getAmount() - 1);
        if (item.getAmount() <= 0) {
            // 清空该格 (主手/副手)
            if (offHand) player.getInventory().setItemInOffHand(null);
            else player.getInventory().setItemInMainHand(null);
        } else {
            if (offHand) player.getInventory().setItemInOffHand(item);
            else player.getInventory().setItemInMainHand(item);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.6f, 1.4f);
        // 不往聊天刷道具名 (避免"聊天框显示太多物品代码"), 改用 ActionBar 即时反馈
        try {
            String name = LegacyComponentSerializer.legacyAmpersand().deserialize(def.name)
                    .toString().replace("§", "");
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize("§e使用: " + def.name));
        } catch (Throwable ignored) {}
        return true;
    }

    // ==================== 具体效果 ====================

    /** 火焰弹砸地: 自动锁定附近敌人落点, 不伤自己, 对范围内其他玩家 0.5 心伤害 + TNT级炸飞 */
    private void fireball(Player player, SpecialDef def) {
        World w = player.getWorld();
        // 玩家线程缓存位置与UUID (避免 region 线程跨区域读实体状态)
        final Location playerLoc = player.getLocation();
        final UUID myId = player.getUniqueId();
        // 目标点: 玩家准星方向延伸 focusDist 格处的探针 (纯坐标, 不读方块)
        Location probe = aimPoint(player, def.radius * 3.0 + 6);
        w.playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f);
        final Location impactBase = probe.clone();

        // 玩家区域线程查找附近敌人 (自动锁定), 决定落点
        Bukkit.getRegionScheduler().run(plugin, playerLoc, t -> {
            int lockRadius = (int) (def.radius + 6);
            Entity target = findNearestEnemy(w, playerLoc, myId, lockRadius);
            final Location aimImpact = target != null ? target.getLocation() : impactBase;
            // 落点区域线程执行爆炸
            Bukkit.getRegionScheduler().run(plugin, aimImpact, t2 -> {
                // 向下找地表
                Location found = findGround(w, aimImpact);
                final Location impact = found != null ? found : aimImpact;
                // 粒子轨迹: 从玩家位置上方向目标点
                Location from = playerLoc.clone().add(0, 2, 0);
                spawnParticleLine(w, from, impact, Particle.FLAME, 24);
                spawnParticleLine(w, from, impact, Particle.LARGE_SMOKE, 12);
                // 爆炸
                w.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.0f);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, impact, 1);
                w.spawnParticle(Particle.FLAME, impact, 40, 0.5, 0.5, 0.5, 0.02);
                // 范围: 对范围内非使用者玩家造成 0.5 心伤害 + TNT 炸飞
                double r = def.radius;
                for (Entity e : w.getNearbyEntities(impact, r, r, r)) {
                    if (!(e instanceof LivingEntity le)) continue;
                    if (le instanceof Player p && p.getUniqueId().equals(myId)) continue; // 不伤自己
                    if (!withinRadius(le.getLocation(), impact, r)) continue;
                    double dist = le.getLocation().distance(impact);
                    double factor = Math.max(0.2, 1.0 - dist / Math.max(1.0, r));
                    // 伤害: 半心 (0.5) —— 不致死, 仅结算用
                    le.getScheduler().run(plugin, s -> {
                        le.damage(0.5);
                        // TNT 级炸飞
                        Vector dir = le.getLocation().toVector().subtract(impact.toVector());
                        if (dir.lengthSquared() < 0.001) dir = new Vector(0, 1, 0);
                        dir.normalize();
                        double up = 0.8 + factor * 0.7;
                        le.setVelocity(dir.multiply(def.velocity * factor + 0.4)
                                .setY(up + def.velocity * factor * 0.6));
                        le.setFireTicks(Math.max(0, le.getFireTicks() + 20));
                    }, () -> {});
                }
            });
        });
    }

    /** 治疗: 范围回血 */
    private void heal(Player player, SpecialDef def) {
        World w = player.getWorld();
        Location loc = player.getLocation();
        w.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
        w.spawnParticle(Particle.HEART, loc.add(0, 1, 0), 20, 1, 1, 1, 0);
        for (Entity e : w.getNearbyEntities(loc, def.radius, def.radius, def.radius)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (!withinRadius(le.getLocation(), loc, def.radius)) continue;
            le.getScheduler().run(plugin, s -> {
                double amount = 4.0 + ThreadLocalRandom.current().nextDouble(4.0);
                if (le instanceof Player p) {
                    double nh = Math.min(p.getMaxHealth(), p.getHealth() + amount);
                    p.setHealth(nh);
                } else if (le.getHealth() < le.getMaxHealth()) {
                    le.setHealth(Math.min(le.getMaxHealth(), le.getHealth() + amount));
                }
            }, () -> {});
        }
    }

    /** 雷击: 自动锁定准星范围/附近最近的敌人, 落雷到其头上 (无目标则落准星地面) */
    private void lightning(Player player, SpecialDef def) {
        World w = player.getWorld();
        final UUID myId = player.getUniqueId();
        // 玩家位置缓存 (region 回调不跨区域读实体)
        final Location playerLoc = player.getLocation();
        Location probe = aimPoint(player, 30);
        Location impactBase = probe.clone();

        // 在玩家区域线程先查找附近最近敌人 (自动锁定), 再决定落雷点
        Bukkit.getRegionScheduler().run(plugin, playerLoc, t -> {
            int lockRadius = (int) (def.radius + 8);
            Entity target = findNearestEnemy(w, playerLoc, myId, lockRadius);
            final Location lockLoc = target != null ? target.getLocation() : impactBase;
            // 目标点区域线程落雷
            Bukkit.getRegionScheduler().run(plugin, lockLoc, t2 -> {
                // 若锁定敌人, 雷落其头顶 (即使区块加载, 雷击特效只需坐标)
                Location impact = lockLoc.clone();
                if (target == null) {
                    Location found = findGround(w, impactBase);
                    impact = found != null ? found : impactBase;
                } else {
                    impact.setY(impact.getY() - 1); // 雷击特效落在敌人脚下地面
                }
                w.strikeLightningEffect(impact);
                w.playSound(impact, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
                double r = Math.max(2.5, def.radius);
                for (Entity e : w.getNearbyEntities(impact, r, r, r)) {
                    if (!(e instanceof LivingEntity le)) continue;
                    if (le instanceof Player p && p.getUniqueId().equals(myId)) continue;
                    if (!withinRadius(le.getLocation(), impact, r)) continue;
                    le.getScheduler().run(plugin, s -> {
                        le.damage(def.damage * 6);
                        le.getWorld().strikeLightningEffect(le.getLocation());
                    }, () -> {});
                }
            });
        });
    }

    /**
     * 在玩家周围查找最近的非己方敌对目标 (其他玩家/敌对生物)
     * 必须在玩家所属区域线程调用; 返回最近敌人实体, 无则 null
     */
    private Entity findNearestEnemy(World w, Location center, UUID myId, int radius) {
        if (radius <= 0) return null;
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : w.getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le instanceof Player p && p.getUniqueId().equals(myId)) continue; // 跳过自己
            double d = e.getLocation().distanceSquared(center);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    /** 冰冻: 范围敌人减速+发光 */
    private void freeze(Player player, SpecialDef def) {
        World w = player.getWorld();
        Location loc = player.getLocation();
        w.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        w.spawnParticle(Particle.SNOWFLAKE, loc.add(0, 1, 0), 40, def.radius, 1, def.radius, 0.01);
        for (Entity e : w.getNearbyEntities(loc, def.radius, def.radius, def.radius)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le instanceof Player p && p.getUniqueId().equals(player.getUniqueId())) continue;
            if (!withinRadius(le.getLocation(), loc, def.radius)) continue;
            int durTicks = def.durationSeconds * 20;
            le.getScheduler().run(plugin, s -> {
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durTicks, 2));
                le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durTicks, 0));
            }, () -> {});
        }
    }

    /** 药水 buff: 对使用者 */
    private void applyPotion(Player player, PotionEffectType type, SpecialDef def) {
        int durTicks = def.durationSeconds * 20;
        player.addPotionEffect(new PotionEffect(type, durTicks, 1));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.4f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0.01);
    }

    /** 掷出受控 TNT: 像弓箭一样抛物线飞向准星方向 (不伤自己)
     *  掷出后碰到地面或实体时【瞬间引爆】 (不再等固定 fuse 时间) */
    private void tntLaunch(Player player, SpecialDef def) {
        World w = player.getWorld();
        // 玩家线程缓存位置与朝向 (region 回调不跨区域读实体)
        final Location ploc = player.getLocation();
        final Vector pdir = player.getLocation().getDirection();
        final UUID thrower = player.getUniqueId();
        Location spawn = ploc.clone().add(0, 1.4, 0);
        Location spawnL = spawn.clone();
        // 抛物线初速: 水平方向*力度 + 上仰分量 (受重力下坠形成弧线, 类似弓箭)
        Vector vel = pdir.clone().multiply(2.2);
        vel.setY(Math.max(0.5, pdir.getY() * 2.0 + 0.6)); // 上抛分量, 打出弧线
        final Vector fvel = vel.clone().normalize().multiply(1.4); // 初始速度大小
        Bukkit.getRegionScheduler().run(plugin, spawnL, t -> {
            org.bukkit.entity.TNTPrimed tnt = (org.bukkit.entity.TNTPrimed) w.spawnEntity(spawnL,
                    org.bukkit.entity.EntityType.TNT);
            // 抛物线: 向上+向前初速, 重力下坠成弧线 (类似弓箭抛出)
            tnt.setVelocity(fvel);
            // 标记此 TNT 为"投掷型即时引爆" (PDC), 供碰撞检测识别
            final org.bukkit.entity.TNTPrimed ftnt = tnt;
            try {
                ftnt.getPersistentDataContainer().set(keyTntInstant, org.bukkit.persistence.PersistentDataType.STRING, "1");
            } catch (Throwable ignore) {}
            // 实体调度器: 每 2 tick 检测是否落到地面或砸到实体 → 立即引爆
            ftnt.getScheduler().runAtFixedRate(plugin, tick -> {
                try {
                    // 已引爆/已移除则停止
                    if (!ftnt.isValid() || ftnt.isDead()) { tick.cancel(); return; }
                    boolean detonate = false;
                    // 落地 (onGround) → 引爆
                    if (ftnt.isOnGround()) detonate = true;
                    // 砸到实体 (附近 0.8 格内有活体且非抛出者/非其它TNT) → 引爆
                    if (!detonate) {
                        for (org.bukkit.entity.Entity e : ftnt.getNearbyEntities(0.8, 0.8, 0.8)) {
                            if (!(e instanceof org.bukkit.entity.LivingEntity)) continue;
                            if (e instanceof Player p2 && p2.getUniqueId().equals(thrower)) continue;
                            detonate = true;
                            break;
                        }
                    }
                    if (detonate) {
                        tick.cancel();
                        // 立即引爆: 倒计时设为 0, 走原版 TNT 爆炸逻辑 (瞬间爆开)
                        ftnt.setFuseTicks(0);
                        ftnt.getWorld().playSound(ftnt.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.2f);
                    }
                } catch (Throwable ignore) { try { tick.cancel(); } catch (Throwable ignored) {} }
            }, () -> {}, 1L, 2L);
        });
    }

    /** 击退波: 把周围玩家炸飞 (不造成任何伤害) */
    private void repulse(Player player, SpecialDef def) {
        World w = player.getWorld();
        Location loc = player.getLocation();
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        w.spawnParticle(Particle.EXPLOSION, loc.add(0, 1, 0), 30, def.radius, 1, def.radius, 0.02);
        for (Entity e : w.getNearbyEntities(loc, def.radius, def.radius, def.radius)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le instanceof Player p && p.getUniqueId().equals(player.getUniqueId())) continue;
            if (!withinRadius(le.getLocation(), loc, def.radius)) continue;
            double dist = le.getLocation().distance(loc);
            double factor = Math.max(0.2, 1.0 - dist / Math.max(1.0, def.radius));
            le.getScheduler().run(plugin, s -> {
                Vector dir = le.getLocation().toVector().subtract(loc.toVector());
                if (dir.lengthSquared() < 0.001) dir = new Vector(0, 1, 0);
                dir.normalize();
                le.setVelocity(dir.multiply(def.velocity * factor + 0.4).setY(0.7 + factor * 0.6));
            }, () -> {});
        }
    }

    /**
     * 追踪器: 随机锁定一名对局内存活敌人, 在其屏幕持续显示方向+距离 (持续 duration-seconds 秒)
     * @return true=成功开始追踪(消费道具), false=无目标(不消费)
     * 玩家区域线程调用 (由触发进入), 目标是纯玩家名单读取, 供后台任务持续刷新
     */
    private boolean track(Player player, SpecialDef def) {
        UUID me = player.getUniqueId();
        // 找到玩家所属房间 → 取该房间内存活玩家
        GameManager g = plugin.rooms().roomOf(me);
        List<UUID> alive = new ArrayList<>();
        if (g != null) {
            for (UUID u : g.inGamePlayers()) {
                if (!u.equals(me) && !g.isEliminated(u)) alive.add(u);
            }
        }
        if (alive.isEmpty()) {
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy
                    .LegacyComponentSerializer.legacyAmpersand().deserialize("§c没有可追踪的敌人!"));
            return false; // 不消费
        }
        UUID prey = alive.get(ThreadLocalRandom.current().nextInt(alive.size()));
        long durMs = Math.max(10, def.durationSeconds) * 1000L;
        trackTargets.put(me, prey);
        trackExpiry.put(me, System.currentTimeMillis() + durMs);
        Player preyP = Bukkit.getPlayer(prey);
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy
                .LegacyComponentSerializer.legacyAmpersand().deserialize(
                "§e§l▣ 追踪开启! §7目标: §a" + (preyP != null ? preyP.getName() : "?")
                + " §7持续 §e" + def.durationSeconds + " §7秒 (方向+距离实时显示)"));
        try { player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f); } catch (Throwable ignored) {}
        return true; // 追踪已开启, 消费道具
    }

    // ==================== 工具 ====================

    /** 玩家准星前方 focusDist 格处的探针坐标 (纯数学, 不读方块, 任意线程) */
    private Location aimPoint(Player player, double focusDist) {
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection();
        return start.clone().add(dir.clone().multiply(focusDist));
    }

    /** 从探针向下找地表 (必须在目标点所属 region 线程调用) */
    private Location findGround(World w, Location probe) {
        int x = probe.getBlockX(), z = probe.getBlockZ();
        int y = Math.min(probe.getBlockY(), w.getMaxHeight() - 1);
        int maxScan = 40;
        for (int i = 0; i < maxScan && y > w.getMinHeight(); i++) {
            if (!w.getBlockAt(x, y, z).getType().isAir()) {
                return new Location(w, x + 0.5, y + 1, z + 0.5);
            }
            y--;
        }
        return null;
    }

    private void spawnParticleLine(World w, Location a, Location b, Particle p, int count) {
        if (a.getWorld() == null || b.getWorld() == null) return;
        double dx = b.getX() - a.getX(), dy = b.getY() - a.getY(), dz = b.getZ() - a.getZ();
        double len = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            Location pt = a.clone().add(dx * t, dy * t, dz * t);
            w.spawnParticle(p, pt, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private boolean withinRadius(Location a, Location center, double r) {
        return a.distance(center) <= r;
    }
}
