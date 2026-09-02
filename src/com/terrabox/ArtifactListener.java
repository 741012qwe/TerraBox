package com.terrabox;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 神器效果触发监听 —— 通过 PDC 识别神器, 施加被动/主动效果
 *
 * 线程模型 (白皮书 §6.1): 事件在所属区域线程触发, 实体操作经 getScheduler 包裹
 *
 * 触发时机:
 *  - onAttack (EntityDamageByEntityEvent): 攻击者手持神器 → 攻击类效果
 *      LIFESTEAL / VAMPIRIC  攻击吸血
 *      BLEED / FROST         冰/流血(减速+发光+持续伤害)
 *      STRENGTH              近战命中追加伤害
 *      STRING(远程/屠龙圣弓) 箭命中加伤害+火焰 (onShoot 打标记)
 *  - onDefend (EntityDamageByEntityEvent): 受害玩家穿神器护甲 → THORNS 反弹
 *  - onJoin / onQuit: SPEED(疾风之靴) 被动移速 —— 玩家区域周期刷新, 穿戴保持/脱下过期
 */
public class ArtifactListener implements Listener {
    private final TerraBoxPlugin plugin;
    private final Map<UUID, ScheduledTask> passiveTasks = new ConcurrentHashMap<>();

    public ArtifactListener(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- SPEED 被动移速 (疾风之靴): 玩家上线注册周期刷新 ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        registerPassive(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        ScheduledTask t = passiveTasks.remove(id);
        if (t != null) { try { t.cancel(); } catch (Throwable ignored) {} }
    }

    private void registerPassive(Player p) {
        UUID id = p.getUniqueId();
        passiveTasks.computeIfAbsent(id, k -> {
            // 每 30 tick(1.5s)刷新一次 SPEED 被动: 穿上疾风之靴保持速度, 脱下自然过期
            return p.getScheduler().runAtFixedRate(plugin, task -> {
                if (!p.isOnline() || !p.isValid()) { task.cancel(); passiveTasks.remove(id); return; }
                applySpeed(p);
            }, () -> {}, 20L, 30L);
        });
        // 立即刷新一次
        applySpeed(p);
    }

    /** 若玩家穿戴 SPEED 神器(疾风之靴), 维持速度加成; 否则不补(已有效果自然过期) */
    private void applySpeed(Player p) {
        double mag = 0;
        for (ItemStack it : p.getInventory().getArmorContents()) {
            if (it != null && plugin.artifacts().isArtifact(it)
                    && "SPEED".equalsIgnoreCase(plugin.artifacts().effectOf(it))) {
                mag = Math.max(mag, plugin.artifacts().magnitudeOf(it));
            }
        }
        if (mag <= 0) return; // 未穿疾风之靴: 不主动补药水, 旧效果自然过期
        int level = Math.max(0, (int) Math.round(mag)); // mag=0.25 → level 0(速度I)
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, level, false, false, true));
    }

    // ---------- 远程神器 (屠龙圣弓): 射箭时给箭打标记 ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player shooter)) return;
        ItemStack bow = shooter.getInventory().getItemInMainHand();
        if (bow == null || bow.getType().isAir()) return;
        if (!plugin.artifacts().isArtifact(bow)) return;
        String effect = plugin.artifacts().effectOf(bow);
        if (effect == null || !"STRING".equalsIgnoreCase(effect)) return;
        // 给被射出的箭打神器标记 (PDC), 命中时按效果处理
        if (e.getProjectile() instanceof Projectile proj) {
            proj.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "artifact_arrow"),
                    org.bukkit.persistence.PersistentDataType.STRING, "draco_bow");
        }
    }

    /** 攻击命中: 触发攻击类神器效果 (实体区域线程) */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        // 远程: 箭命中且箭带标记 (屠龙圣弓)
        if (e.getDamager() instanceof Projectile proj
                && !(e.getDamager() instanceof Player)) {
            String arrowTag = proj.getPersistentDataContainer().get(
                    new org.bukkit.NamespacedKey(plugin, "artifact_arrow"),
                    org.bukkit.persistence.PersistentDataType.STRING);
            if ("draco_bow".equals(arrowTag) && e.getEntity() instanceof LivingEntity target) {
                // 高额伤害 + 火焰 (目标区域线程)
                e.setDamage(e.getDamage() + 6.0);
                LivingEntity tg = target;
                tg.getScheduler().run(plugin, s -> {
                    tg.setFireTicks(80); // 4秒燃烧
                    tg.getWorld().spawnParticle(Particle.FLAME,
                            tg.getLocation().add(0, 1.2, 0), 14, 0.3, 0.4, 0.3, 0.02);
                    tg.getWorld().playSound(tg.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.8f);
                }, () -> {});
                return;
            }
        }

        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) return;
        if (!plugin.artifacts().isArtifact(held)) return;

        String effect = plugin.artifacts().effectOf(held);
        if (effect == null || effect.isEmpty()) return;
        double chance = plugin.artifacts().procChanceOf(held);
        double mag = plugin.artifacts().magnitudeOf(held);
        if (chance <= 0) return;

        // 按效果类型处理 (概率判定)
        double roll = Math.random();
        switch (effect) {
            case "LIFESTEAL", "VAMPIRIC" -> {
                if (roll < chance) {
                    // 吸血: 回复量 = 造成伤害 × 吸血系数 × 强度 (attacker 区域线程)
                    double ratio = effect.equals("VAMPIRIC") ? 0.5 : 0.25;
                    double heal = Math.max(1.0, e.getDamage() * ratio * Math.min(2.0, mag));
                    Player atk = attacker;
                    atk.getScheduler().run(plugin, s -> {
                        double cur = atk.getHealth();
                        if (cur < atk.getMaxHealth()) {
                            atk.setHealth(Math.min(atk.getMaxHealth(), cur + heal));
                            atk.getWorld().spawnParticle(Particle.HEART,
                                    atk.getLocation().add(0, 1.5, 0), 6, 0.3, 0.4, 0.3, 0);
                        }
                    }, () -> {});
                }
            }
            case "BLEED", "FROST" -> {
                if (roll < chance) {
                    int durTicks = 80; // 4 秒
                    LivingEntity lt = target;
                    lt.getScheduler().run(plugin, s -> {
                        lt.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durTicks, 1));
                        lt.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durTicks, 0));
                        if (effect.equals("BLEED")) {
                            lt.damage(1.5 * mag);
                        }
                    }, () -> {});
                    lt.getWorld().spawnParticle(Particle.CRIT,
                            lt.getLocation().add(0, 1.2, 0), 12, 0.3, 0.4, 0.3, 0.02);
                }
            }
            case "STRENGTH" -> {
                // 近战攻击加成: 每次命中按强度追加伤害
                double bonus = mag * 2.0;
                if (bonus > 0) e.setDamage(e.getDamage() + bonus);
            }
            default -> {}
        }
    }

    /** 受击: 穿戴神器护甲 → THORNS 反弹 (实体区域线程) */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDefend(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        // 检查受害者身上每件护甲是否为 THORNS 神器
        ItemStack[] armor = victim.getInventory().getArmorContents();
        for (ItemStack it : armor) {
            if (it != null && plugin.artifacts().isArtifact(it)
                    && "THORNS".equalsIgnoreCase(plugin.artifacts().effectOf(it))) {
                double mag = plugin.artifacts().magnitudeOf(it);
                double chance = plugin.artifacts().procChanceOf(it);
                if (Math.random() < chance && e.getDamager() instanceof LivingEntity damager) {
                    final LivingEntity dg = damager;
                    dg.getScheduler().run(plugin, s -> {
                        dg.damage(2.0 * mag);
                        dg.getWorld().spawnParticle(Particle.CRIT, dg.getLocation().add(0, 1, 0),
                                8, 0.3, 0.4, 0.3, 0);
                    }, () -> {});
                }
                break;
            }
        }
    }
}
