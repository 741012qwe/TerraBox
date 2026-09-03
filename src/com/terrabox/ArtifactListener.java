/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Projectile
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityShootBowEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 */
package com.terrabox;

import com.terrabox.TerraBoxPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ArtifactListener
implements Listener {
    private final TerraBoxPlugin plugin;
    private final Map<UUID, ScheduledTask> passiveTasks = new ConcurrentHashMap<UUID, ScheduledTask>();

    public ArtifactListener(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        this.registerPassive(playerJoinEvent.getPlayer());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        UUID uUID = playerQuitEvent.getPlayer().getUniqueId();
        ScheduledTask scheduledTask = this.passiveTasks.remove(uUID);
        if (scheduledTask != null) {
            try {
                scheduledTask.cancel();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    private void registerPassive(Player player) {
        UUID uUID = player.getUniqueId();
        this.passiveTasks.computeIfAbsent(uUID, uUID2 -> player.getScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> {
            if (!player.isOnline() || !player.isValid()) {
                scheduledTask.cancel();
                this.passiveTasks.remove(uUID);
                return;
            }
            this.applySpeed(player);
        }, () -> {}, 20L, 30L));
        this.applySpeed(player);
    }

    private void applySpeed(Player player) {
        double d = 0.0;
        for (ItemStack itemStack : player.getInventory().getArmorContents()) {
            if (itemStack == null || !this.plugin.artifacts().isArtifact(itemStack) || !"SPEED".equalsIgnoreCase(this.plugin.artifacts().effectOf(itemStack))) continue;
            d = Math.max(d, this.plugin.artifacts().magnitudeOf(itemStack));
        }
        if (d <= 0.0) {
            return;
        }
        int n = Math.max(0, (int)Math.round(d));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, n, false, false, true));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onShoot(EntityShootBowEvent entityShootBowEvent) {
        LivingEntity livingEntity = entityShootBowEvent.getEntity();
        if (!(livingEntity instanceof Player)) {
            return;
        }
        Player player = (Player)livingEntity;
        livingEntity = player.getInventory().getItemInMainHand();
        if (livingEntity == null || livingEntity.getType().isAir()) {
            return;
        }
        if (!this.plugin.artifacts().isArtifact((ItemStack)livingEntity)) {
            return;
        }
        String string = this.plugin.artifacts().effectOf((ItemStack)livingEntity);
        if (string == null || !"STRING".equalsIgnoreCase(string)) {
            return;
        }
        Entity entity = entityShootBowEvent.getProjectile();
        if (entity instanceof Projectile) {
            Projectile projectile = (Projectile)entity;
            projectile.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "artifact_arrow"), PersistentDataType.STRING, (Object)"draco_bow");
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onAttack(EntityDamageByEntityEvent entityDamageByEntityEvent) {
        Object object;
        Projectile projectile;
        Object object2 = entityDamageByEntityEvent.getDamager();
        if (object2 instanceof Projectile) {
            projectile = (Projectile)object2;
            if (!(entityDamageByEntityEvent.getDamager() instanceof Player) && "draco_bow".equals(object2 = (String)projectile.getPersistentDataContainer().get(new NamespacedKey((Plugin)this.plugin, "artifact_arrow"), PersistentDataType.STRING)) && (object = entityDamageByEntityEvent.getEntity()) instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity)object;
                entityDamageByEntityEvent.setDamage(entityDamageByEntityEvent.getDamage() + 6.0);
                object = livingEntity;
                object.getScheduler().run((Plugin)this.plugin, arg_0 -> ArtifactListener.lambda$onAttack$0((LivingEntity)object, arg_0), () -> {});
                return;
            }
        }
        if (!((object2 = entityDamageByEntityEvent.getDamager()) instanceof Player)) {
            return;
        }
        projectile = (Player)object2;
        Entity entity = entityDamageByEntityEvent.getEntity();
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        object2 = (LivingEntity)entity;
        entity = projectile.getInventory().getItemInMainHand();
        if (entity == null || entity.getType().isAir()) {
            return;
        }
        if (!this.plugin.artifacts().isArtifact((ItemStack)entity)) {
            return;
        }
        object = this.plugin.artifacts().effectOf((ItemStack)entity);
        if (object == null || ((String)object).isEmpty()) {
            return;
        }
        double d = this.plugin.artifacts().procChanceOf((ItemStack)entity);
        double d2 = this.plugin.artifacts().magnitudeOf((ItemStack)entity);
        if (d <= 0.0) {
            return;
        }
        double d3 = Math.random();
        switch (object) {
            case "LIFESTEAL": 
            case "VAMPIRIC": {
                if (!(d3 < d)) break;
                double d4 = ((String)object).equals("VAMPIRIC") ? 0.5 : 0.25;
                double d5 = Math.max(1.0, entityDamageByEntityEvent.getDamage() * d4 * Math.min(2.0, d2));
                Projectile projectile2 = projectile;
                projectile2.getScheduler().run((Plugin)this.plugin, arg_0 -> ArtifactListener.lambda$onAttack$2((Player)projectile2, d5, arg_0), () -> {});
                break;
            }
            case "BLEED": 
            case "FROST": {
                if (!(d3 < d)) break;
                int n = 80;
                Object object3 = object2;
                object3.getScheduler().run((Plugin)this.plugin, arg_0 -> ArtifactListener.lambda$onAttack$4((LivingEntity)object3, n, (String)object, d2, arg_0), () -> {});
                object3.getWorld().spawnParticle(Particle.CRIT, object3.getLocation().add(0.0, 1.2, 0.0), 12, 0.3, 0.4, 0.3, 0.02);
                break;
            }
            case "STRENGTH": {
                double d6 = d2 * 2.0;
                if (!(d6 > 0.0)) break;
                entityDamageByEntityEvent.setDamage(entityDamageByEntityEvent.getDamage() + d6);
                break;
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onDefend(EntityDamageByEntityEvent entityDamageByEntityEvent) {
        ItemStack[] itemStackArray = entityDamageByEntityEvent.getEntity();
        if (!(itemStackArray instanceof Player)) {
            return;
        }
        Player player = (Player)itemStackArray;
        for (ItemStack itemStack : itemStackArray = player.getInventory().getArmorContents()) {
            Entity entity;
            if (itemStack == null || !this.plugin.artifacts().isArtifact(itemStack) || !"THORNS".equalsIgnoreCase(this.plugin.artifacts().effectOf(itemStack))) continue;
            double d = this.plugin.artifacts().magnitudeOf(itemStack);
            double d2 = this.plugin.artifacts().procChanceOf(itemStack);
            if (!(Math.random() < d2) || !((entity = entityDamageByEntityEvent.getDamager()) instanceof LivingEntity)) break;
            LivingEntity livingEntity = (LivingEntity)entity;
            entity = livingEntity;
            entity.getScheduler().run((Plugin)this.plugin, arg_0 -> ArtifactListener.lambda$onDefend$0((LivingEntity)entity, d, arg_0), () -> {});
            break;
        }
    }

    private static /* synthetic */ void lambda$onDefend$0(LivingEntity livingEntity, double d, ScheduledTask scheduledTask) {
        livingEntity.damage(2.0 * d);
        livingEntity.getWorld().spawnParticle(Particle.CRIT, livingEntity.getLocation().add(0.0, 1.0, 0.0), 8, 0.3, 0.4, 0.3, 0.0);
    }

    private static /* synthetic */ void lambda$onAttack$4(LivingEntity livingEntity, int n, String string, double d, ScheduledTask scheduledTask) {
        livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, n, 1));
        livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, n, 0));
        if (string.equals("BLEED")) {
            livingEntity.damage(1.5 * d);
        }
    }

    private static /* synthetic */ void lambda$onAttack$2(Player player, double d, ScheduledTask scheduledTask) {
        double d2 = player.getHealth();
        if (d2 < player.getMaxHealth()) {
            player.setHealth(Math.min(player.getMaxHealth(), d2 + d));
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0.0, 1.5, 0.0), 6, 0.3, 0.4, 0.3, 0.0);
        }
    }

    private static /* synthetic */ void lambda$onAttack$0(LivingEntity livingEntity, ScheduledTask scheduledTask) {
        livingEntity.setFireTicks(80);
        livingEntity.getWorld().spawnParticle(Particle.FLAME, livingEntity.getLocation().add(0.0, 1.2, 0.0), 14, 0.3, 0.4, 0.3, 0.02);
        livingEntity.getWorld().playSound(livingEntity.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.8f);
    }
}
