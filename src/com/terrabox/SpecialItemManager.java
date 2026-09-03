/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.World
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.TNTPrimed
 *  org.bukkit.event.Listener
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataContainer
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 *  org.bukkit.util.Vector
 */
package com.terrabox;

import com.terrabox.GameManager;
import com.terrabox.TerraBoxPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class SpecialItemManager
implements Listener {
    public static final String TITLE = "\u7279\u6b8a\u9053\u5177";
    private final TerraBoxPlugin plugin;
    private final NamespacedKey keySpecial;
    private final NamespacedKey keyData;
    private final NamespacedKey keyTntInstant;
    private final Map<String, SpecialDef> defs = new HashMap<String, SpecialDef>();
    private final Map<UUID, UUID> trackTargets = new ConcurrentHashMap<UUID, UUID>();
    private final Map<UUID, Long> trackExpiry = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, String> trackTexts = new ConcurrentHashMap<UUID, String>();
    private ScheduledTask trackTask;

    public String trackingText(UUID uUID) {
        return this.trackTexts.getOrDefault(uUID, "");
    }

    public SpecialItemManager(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
        this.keySpecial = new NamespacedKey((Plugin)terraBoxPlugin, "special");
        this.keyData = new NamespacedKey((Plugin)terraBoxPlugin, "special_data");
        this.keyTntInstant = new NamespacedKey((Plugin)terraBoxPlugin, "tnt_instant");
    }

    public void load() {
        this.defs.clear();
        ConfigurationSection configurationSection = this.plugin.getConfig().getConfigurationSection("special-items");
        if (configurationSection == null || configurationSection.getKeys(false).isEmpty()) {
            this.plugin.getLogger().info("\u7279\u6b8a\u9053\u5177\u8868: \u672a\u68c0\u6d4b\u5230\u914d\u7f6e\u6bb5, \u5df2\u52a0\u8f7d\u5185\u7f6e\u9ed8\u8ba4\u9053\u5177");
            this.registerDefaults();
            return;
        }
        for (String string : configurationSection.getKeys(false)) {
            ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection(string);
            if (configurationSection2 == null) continue;
            try {
                Material material = Material.matchMaterial((String)configurationSection2.getString("material", "FIRE_CHARGE").toUpperCase(Locale.ROOT));
                if (material == null || !material.isItem()) {
                    this.plugin.getLogger().warning("\u7279\u6b8a\u9053\u5177 [" + string + "] \u6750\u8d28\u65e0\u6548: " + configurationSection2.getString("material"));
                    continue;
                }
                String string2 = configurationSection2.getString("effect", "FIREBALL").toUpperCase(Locale.ROOT);
                String string3 = configurationSection2.getString("name", "&e" + string);
                ArrayList<String> arrayList = new ArrayList<String>(configurationSection2.getStringList("lore"));
                double d = configurationSection2.getDouble("radius", 4.0);
                double d2 = configurationSection2.getDouble("damage", 0.5);
                double d3 = configurationSection2.getDouble("velocity", 1.4);
                int n = configurationSection2.getInt("duration-seconds", 5);
                this.defs.put(string.toLowerCase(Locale.ROOT), new SpecialDef(material, string.toLowerCase(Locale.ROOT), string3, arrayList, string2, d, d2, d3, n));
            }
            catch (Exception exception) {
                this.plugin.getLogger().warning("\u7279\u6b8a\u9053\u5177 [" + string + "] \u89e3\u6790\u5931\u8d25: " + exception.getMessage());
            }
        }
        this.plugin.getLogger().info("\u7279\u6b8a\u9053\u5177\u8868\u52a0\u8f7d\u5b8c\u6210: " + this.defs.size() + " \u79cd");
    }

    public int size() {
        return this.defs.size();
    }

    public void start() {
        this.stop();
        this.trackTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> this.tickTracks(), 10L, 10L);
    }

    public void stop() {
        if (this.trackTask != null) {
            this.trackTask.cancel();
            this.trackTask = null;
        }
        this.trackTargets.clear();
        this.trackExpiry.clear();
        this.trackTexts.clear();
    }

    private void tickTracks() {
        long l = System.currentTimeMillis();
        for (Map.Entry<UUID, UUID> entry : this.trackTargets.entrySet()) {
            UUID uUID = entry.getKey();
            UUID uUID2 = entry.getValue();
            Long l2 = this.trackExpiry.get(uUID);
            if (l2 == null || l > l2) {
                this.trackTargets.remove(uUID);
                this.trackExpiry.remove(uUID);
                this.trackTexts.remove(uUID);
                continue;
            }
            Player player = Bukkit.getPlayer((UUID)uUID);
            if (player == null || !player.isOnline()) {
                this.trackTargets.remove(uUID);
                this.trackExpiry.remove(uUID);
                this.trackTexts.remove(uUID);
                continue;
            }
            Player player2 = Bukkit.getPlayer((UUID)uUID2);
            if (player2 == null || !player2.isOnline()) {
                this.trackTargets.remove(uUID);
                this.trackExpiry.remove(uUID);
                this.trackTexts.remove(uUID);
                player.sendActionBar((Component)LegacyComponentSerializer.legacyAmpersand().deserialize("\u00a7c\u8ffd\u8e2a\u76ee\u6807\u5df2\u6d88\u5931!"));
                continue;
            }
            Player player3 = player;
            Player player4 = player2;
            long l3 = l2;
            player3.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                try {
                    Location location = player3.getLocation();
                    Location location2 = player4.getLocation();
                    if (!location.getWorld().equals((Object)location2.getWorld())) {
                        return;
                    }
                    double d = location2.getX() - location.getX();
                    double d2 = location2.getZ() - location.getZ();
                    double d3 = location2.getY() - location.getY();
                    int n = (int)Math.round(Math.sqrt(d * d + d2 * d2 + d3 * d3));
                    String string = this.trackCompass(d, d2);
                    long l2 = Math.max(0L, (l3 - System.currentTimeMillis()) / 1000L);
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("\u00a7e\u00a7l\u25a3\u8ffd\u8e2a[\u00a7a").append(player4.getName()).append("\u00a7e]\u00a77\u2192\u00a7f").append(string).append("\u00a77 \u00a7f").append(n).append("\u683c\u00a77(\u9ad8\u5dee").append(d3 >= 0.0 ? "+" : "").append((int)d3).append(")\u00a77\u5269\u00a7c").append(l2).append("\u79d2");
                    this.trackTexts.put(player3.getUniqueId(), stringBuilder.toString());
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }, () -> {});
        }
    }

    private void removeTrackBar(UUID uUID) {
        this.trackTexts.remove(uUID);
    }

    private String trackCompass(double d, double d2) {
        String[] stringArray = new String[]{"\u5317", "\u4e1c\u5317", "\u4e1c", "\u4e1c\u5357", "\u5357", "\u897f\u5357", "\u897f", "\u897f\u5317"};
        double d3 = Math.toDegrees(Math.atan2(d, -d2));
        d3 = (d3 + 360.0) % 360.0;
        int n = (int)Math.floor((d3 + 22.5) / 45.0) % 8;
        return stringArray[n] + "\u00a77(\u00a7f" + (int)d3 + "\u00b0\u00a77)";
    }

    public boolean isTracking(UUID uUID) {
        return this.trackTargets.containsKey(uUID);
    }

    public UUID trackingTarget(UUID uUID) {
        return this.trackTargets.get(uUID);
    }

    public void stopTracking(UUID uUID) {
        this.trackTargets.remove(uUID);
        this.trackExpiry.remove(uUID);
        this.trackTexts.remove(uUID);
    }

    public Set<UUID> trackingPlayers() {
        return Set.copyOf(this.trackTargets.keySet());
    }

    private void registerDefaults() {
        this.defs.put("fireball_tnt", new SpecialDef(Material.FIRE_CHARGE, "fireball_tnt", "&6&l\u9707\u5730\u706b\u5f39", List.of("&7\u53f3\u952e\u671d\u5730\u9762\u7838\u4e0b, \u5f15\u7206\u8303\u56f4\u51b2\u51fb\u3002", "&7\u4e0d\u4f24\u81ea\u5df1, \u5bf9\u5468\u56f4\u73a9\u5bb6\u9020\u6210 &c\u534a\u5fc3 &7\u4f24\u5bb3, \u5e76\u6309 TNT \u529b\u5ea6\u70b8\u98de\u3002"), "FIREBALL", 4.0, 0.5, 1.6, 5));
        this.defs.put("fireball_big", new SpecialDef(Material.FIRE_CHARGE, "fireball_big", "&d&l\u70c8\u7130\u9707\u5730\u5f39", List.of("&7\u53f3\u952e\u671d\u5730\u9762\u7838\u4e0b, \u5927\u8303\u56f4\u7206\u70b8\u3002", "&7\u4e0d\u4f24\u81ea\u5df1, \u5bf9\u5468\u56f4\u73a9\u5bb6\u9020\u6210 &c\u534a\u5fc3 &7\u4f24\u5bb3, \u5e76\u70b8\u98de\u5f97\u66f4\u8fdc\u3002"), "FIREBALL", 6.0, 0.5, 2.2, 5));
        this.defs.put("heal_potion", new SpecialDef(Material.POTION, "heal_potion", "&a&l\u56de\u6625\u836f\u5242", List.of("&7\u53f3\u952e\u4f7f\u7528, \u6062\u590d\u8303\u56f4\u5185\u751f\u547d 4~8 \u5fc3\u3002"), "HEAL", 5.0, 0.0, 0.0, 0));
        this.defs.put("lightning_wand", new SpecialDef(Material.BLAZE_ROD, "lightning_wand", "&b&l\u5f15\u96f7\u6756", List.of("&7\u53f3\u952e\u671d\u51c6\u661f\u65b9\u5411\u53ec\u6765\u843d\u96f7, \u5bf9\u8303\u56f4\u654c\u4eba\u9020\u6210\u9ad8\u989d\u4f24\u5bb3\u3002"), "LIGHTNING", 4.0, 1.5, 0.0, 0));
        this.defs.put("speed_charge", new SpecialDef(Material.SUGAR, "speed_charge", "&e&l\u75be\u98ce\u51b2\u950b", List.of("&7\u53f3\u952e\u4f7f\u7528, \u83b7\u5f97\u901f\u5ea6\u63d0\u5347 12 \u79d2\u3002"), "SPEED", 0.0, 0.0, 0.0, 12));
        this.defs.put("jump_charge", new SpecialDef(Material.RABBIT_FOOT, "jump_charge", "&a&l\u5f39\u8df3\u5f3a\u5316", List.of("&7\u53f3\u952e\u4f7f\u7528, \u83b7\u5f97\u8df3\u8dc3\u63d0\u5347 12 \u79d2\u3002"), "JUMP", 0.0, 0.0, 0.0, 12));
        this.defs.put("freeze_crystal", new SpecialDef(Material.ICE, "freeze_crystal", "&b&l\u51b0\u971c\u7981\u9522", List.of("&7\u53f3\u952e\u4f7f\u7528, \u51bb\u7ed3\u5468\u56f4\u654c\u4eba 8 \u79d2\u3002"), "FREEZE", 5.0, 0.0, 0.0, 8));
        this.defs.put("shield_charge", new SpecialDef(Material.SHIELD, "shield_charge", "&f&l\u94c1\u58c1\u62a4\u76fe", List.of("&7\u53f3\u952e\u4f7f\u7528, \u83b7\u5f97\u6297\u6027\u63d0\u5347 10 \u79d2\u3002"), "SHIELD", 0.0, 0.0, 0.0, 10));
        this.defs.put("fire_res_charge", new SpecialDef(Material.BLAZE_POWDER, "fire_res_charge", "&c&l\u70c8\u7130\u6297\u6027", List.of("&7\u53f3\u952e\u4f7f\u7528, \u83b7\u5f97\u9632\u706b\u6548\u679c 15 \u79d2\u3002"), "FIRE_RES", 0.0, 0.0, 0.0, 15));
        this.defs.put("tnt_charge", new SpecialDef(Material.TNT, "tnt_charge", "&6&l\u8f70\u5929\u70b8\u836f", List.of("&7\u53f3\u952e\u63b7\u51fa\u4e00\u679a\u4e0d\u4f1a\u4f24\u53ca\u81ea\u5df1\u7684 TNT\u3002"), "TNT_LAUNCH", 5.0, 0.0, 0.0, 0));
        this.defs.put("repulse_wave", new SpecialDef(Material.FIREWORK_STAR, "repulse_wave", "&d&l\u51b2\u51fb\u6ce2", List.of("&7\u53f3\u952e\u91ca\u653e\u51fb\u9000\u6ce2, \u628a\u5468\u56f4\u73a9\u5bb6\u70b8\u98de (\u4e0d\u4f24\u5bb3)\u3002"), "REPULSE", 6.0, 0.0, 2.0, 0));
        this.defs.put("enemy_tracker", new SpecialDef(Material.COMPASS, "enemy_tracker", "&e&l\u8ffd\u8e2a\u7f57\u76d8", List.of("&7\u53f3\u952e\u4f7f\u7528, \u9501\u5b9a\u4e00\u540d\u968f\u673a\u5b58\u6d3b\u654c\u4eba\u3002", "&7\u5728\u5176\u5934\u9876\u6301\u7eed\u663e\u793a\u65b9\u5411\u4e0e\u8ddd\u79bb \u00a7a25 \u00a77\u79d2\u3002"), "TRACK", 0.0, 0.0, 0.0, 25));
    }

    public ItemStack buildItem(String string) {
        SpecialDef specialDef = this.defs.get(string.toLowerCase(Locale.ROOT));
        if (specialDef == null) {
            return null;
        }
        ItemStack itemStack = new ItemStack(specialDef.material, 1);
        ItemMeta itemMeta2 = itemStack.getItemMeta();
        if (itemMeta2 != null) {
            TextComponent textComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(specialDef.name);
            itemMeta2.displayName((Component)textComponent);
            ArrayList<TextComponent> arrayList = new ArrayList<TextComponent>();
            for (String string2 : specialDef.lore) {
                arrayList.add(LegacyComponentSerializer.legacyAmpersand().deserialize(string2));
            }
            itemMeta2.lore(arrayList);
            itemStack.setItemMeta(itemMeta2);
        }
        itemStack.editMeta(itemMeta -> {
            PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();
            persistentDataContainer.set(this.keySpecial, PersistentDataType.STRING, (Object)specialDef.key);
            persistentDataContainer.set(this.keyData, PersistentDataType.STRING, (Object)specialDef.effect);
        });
        return itemStack;
    }

    public String specialKey(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        try {
            return (String)itemStack.getItemMeta().getPersistentDataContainer().get(this.keySpecial, PersistentDataType.STRING);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    public boolean isSpecial(ItemStack itemStack) {
        return this.specialKey(itemStack) != null;
    }

    public String randomKey() {
        if (this.defs.isEmpty()) {
            return null;
        }
        ArrayList<SpecialDef> arrayList = new ArrayList<SpecialDef>(this.defs.values());
        return ((SpecialDef)arrayList.get((int)ThreadLocalRandom.current().nextInt((int)arrayList.size()))).key;
    }

    public boolean trigger(Player player, ItemStack itemStack) {
        return this.trigger(player, itemStack, false);
    }

    public boolean trigger(Player player, ItemStack itemStack, boolean bl) {
        String string = this.specialKey(itemStack);
        if (string == null) {
            return false;
        }
        SpecialDef specialDef = this.defs.get(string);
        if (specialDef == null) {
            return false;
        }
        try {
            boolean bl2;
            switch (specialDef.effect) {
                case "FIREBALL": {
                    this.fireball(player, specialDef);
                    boolean bl3 = true;
                    break;
                }
                case "HEAL": {
                    this.heal(player, specialDef);
                    boolean bl3 = true;
                    break;
                }
                case "LIGHTNING": {
                    this.lightning(player, specialDef);
                    boolean bl3 = true;
                    break;
                }
                case "SPEED": {
                    this.applyPotion(player, PotionEffectType.SPEED, specialDef);
                    boolean bl3 = true;
                    break;
                }
                case "JUMP": {
                    this.applyPotion(player, PotionEffectType.JUMP_BOOST, specialDef);
                    boolean bl3 = true;
                    break;
                }
                case "FREEZE": {
                    this.freeze(player, specialDef);
                    boolean bl3 = true;
                    break;
                }
                case "SHIELD": {
                    this.applyPotion(player, PotionEffectType.RESISTANCE, specialDef);
                    boolean bl3 = true;
                    break;
                }
                case "FIRE_RES": {
                    this.applyPotion(player, PotionEffectType.FIRE_RESISTANCE, specialDef);
                    boolean bl3 = true;
                    break;
                }
                case "TNT_LAUNCH": {
                    this.tntLaunch(player, specialDef);
                    boolean bl3 = true;
                    break;
                }
                case "REPULSE": {
                    this.repulse(player, specialDef);
                    boolean bl3 = true;
                    break;
                }
                case "TRACK": {
                    boolean bl3 = this.track(player, specialDef);
                    break;
                }
                default: {
                    this.plugin.getLogger().warning("\u7279\u6b8a\u9053\u5177 [" + string + "] \u672a\u77e5\u6548\u679c: " + specialDef.effect);
                    boolean bl3 = bl2 = false;
                }
            }
            if (!bl2) {
                return false;
            }
            this.consume(player, itemStack, specialDef, bl);
            return true;
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().warning("\u7279\u6b8a\u9053\u5177 [" + string + "] \u89e6\u53d1\u5f02\u5e38: " + throwable.getClass().getSimpleName() + " - " + throwable.getMessage());
            return false;
        }
    }

    private boolean consume(Player player, ItemStack itemStack, SpecialDef specialDef, boolean bl) {
        itemStack.setAmount(itemStack.getAmount() - 1);
        if (itemStack.getAmount() <= 0) {
            if (bl) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else if (bl) {
            player.getInventory().setItemInOffHand(itemStack);
        } else {
            player.getInventory().setItemInMainHand(itemStack);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.6f, 1.4f);
        try {
            String string = LegacyComponentSerializer.legacyAmpersand().deserialize(specialDef.name).toString().replace("\u00a7", "");
            player.sendActionBar((Component)LegacyComponentSerializer.legacyAmpersand().deserialize("\u00a7e\u4f7f\u7528: " + specialDef.name));
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return true;
    }

    private void fireball(Player player, SpecialDef specialDef) {
        World world = player.getWorld();
        Location location = player.getLocation();
        UUID uUID = player.getUniqueId();
        Location location2 = this.aimPoint(player, specialDef.radius * 3.0 + 6.0);
        world.playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f);
        Location location3 = location2.clone();
        Bukkit.getRegionScheduler().run((Plugin)this.plugin, location, scheduledTask -> {
            int n = (int)(specialDef.radius + 6.0);
            Entity entity = this.findNearestEnemy(world, location, uUID, n);
            Location location3 = entity != null ? entity.getLocation() : location3;
            Bukkit.getRegionScheduler().run((Plugin)this.plugin, location3, scheduledTask2 -> {
                Location location3 = this.findGround(world, location3);
                Location location4 = location3 != null ? location3 : location3;
                Location location5 = location.clone().add(0.0, 2.0, 0.0);
                this.spawnParticleLine(world, location5, location4, Particle.FLAME, 24);
                this.spawnParticleLine(world, location5, location4, Particle.LARGE_SMOKE, 12);
                world.playSound(location4, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.0f);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, location4, 1);
                world.spawnParticle(Particle.FLAME, location4, 40, 0.5, 0.5, 0.5, 0.02);
                double d = specialDef.radius;
                for (Entity entity : world.getNearbyEntities(location4, d, d, d)) {
                    Player player;
                    LivingEntity livingEntity;
                    if (!(entity instanceof LivingEntity) || (livingEntity = (LivingEntity)entity) instanceof Player && (player = (Player)livingEntity).getUniqueId().equals(uUID) || !this.withinRadius(livingEntity.getLocation(), location4, d)) continue;
                    double d2 = livingEntity.getLocation().distance(location4);
                    double d3 = Math.max(0.2, 1.0 - d2 / Math.max(1.0, d));
                    livingEntity.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                        livingEntity.damage(0.5);
                        Vector vector = livingEntity.getLocation().toVector().subtract(location4.toVector());
                        if (vector.lengthSquared() < 0.001) {
                            vector = new Vector(0, 1, 0);
                        }
                        vector.normalize();
                        double d2 = 0.8 + d3 * 0.7;
                        livingEntity.setVelocity(vector.multiply(specialDef.velocity * d3 + 0.4).setY(d2 + specialDef.velocity * d3 * 0.6));
                        livingEntity.setFireTicks(Math.max(0, livingEntity.getFireTicks() + 20));
                    }, () -> {});
                }
            });
        });
    }

    private void heal(Player player, SpecialDef specialDef) {
        World world = player.getWorld();
        Location location = player.getLocation();
        world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
        world.spawnParticle(Particle.HEART, location.add(0.0, 1.0, 0.0), 20, 1.0, 1.0, 1.0, 0.0);
        for (Entity entity : world.getNearbyEntities(location, specialDef.radius, specialDef.radius, specialDef.radius)) {
            LivingEntity livingEntity;
            if (!(entity instanceof LivingEntity) || !this.withinRadius((livingEntity = (LivingEntity)entity).getLocation(), location, specialDef.radius)) continue;
            livingEntity.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                double d = 4.0 + ThreadLocalRandom.current().nextDouble(4.0);
                if (livingEntity instanceof Player) {
                    Player player = (Player)livingEntity;
                    double d2 = Math.min(player.getMaxHealth(), player.getHealth() + d);
                    player.setHealth(d2);
                } else if (livingEntity.getHealth() < livingEntity.getMaxHealth()) {
                    livingEntity.setHealth(Math.min(livingEntity.getMaxHealth(), livingEntity.getHealth() + d));
                }
            }, () -> {});
        }
    }

    private void lightning(Player player, SpecialDef specialDef) {
        World world = player.getWorld();
        UUID uUID = player.getUniqueId();
        Location location = player.getLocation();
        Location location2 = this.aimPoint(player, 30.0);
        Location location3 = location2.clone();
        Bukkit.getRegionScheduler().run((Plugin)this.plugin, location, scheduledTask -> {
            int n = (int)(specialDef.radius + 8.0);
            Entity entity = this.findNearestEnemy(world, location, uUID, n);
            Location location3 = entity != null ? entity.getLocation() : location3;
            Bukkit.getRegionScheduler().run((Plugin)this.plugin, location3, scheduledTask2 -> {
                Location location3 = location3.clone();
                if (entity == null) {
                    Location location4 = this.findGround(world, location3);
                    location3 = location4 != null ? location4 : location3;
                } else {
                    location3.setY(location3.getY() - 1.0);
                }
                world.strikeLightningEffect(location3);
                world.playSound(location3, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
                double d = Math.max(2.5, specialDef.radius);
                for (Entity entity2 : world.getNearbyEntities(location3, d, d, d)) {
                    Player player;
                    LivingEntity livingEntity;
                    if (!(entity2 instanceof LivingEntity) || (livingEntity = (LivingEntity)entity2) instanceof Player && (player = (Player)livingEntity).getUniqueId().equals(uUID) || !this.withinRadius(livingEntity.getLocation(), location3, d)) continue;
                    livingEntity.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                        livingEntity.damage(specialDef.damage * 6.0);
                        livingEntity.getWorld().strikeLightningEffect(livingEntity.getLocation());
                    }, () -> {});
                }
            });
        });
    }

    private Entity findNearestEnemy(World world, Location location, UUID uUID, int n) {
        if (n <= 0) {
            return null;
        }
        Entity entity = null;
        double d = Double.MAX_VALUE;
        for (Entity entity2 : world.getNearbyEntities(location, (double)n, (double)n, (double)n)) {
            double d2;
            Player player;
            LivingEntity livingEntity;
            if (!(entity2 instanceof LivingEntity) || (livingEntity = (LivingEntity)entity2) instanceof Player && (player = (Player)livingEntity).getUniqueId().equals(uUID) || !((d2 = entity2.getLocation().distanceSquared(location)) < d)) continue;
            d = d2;
            entity = entity2;
        }
        return entity;
    }

    private void freeze(Player player, SpecialDef specialDef) {
        World world = player.getWorld();
        Location location = player.getLocation();
        world.playSound(location, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        world.spawnParticle(Particle.SNOWFLAKE, location.add(0.0, 1.0, 0.0), 40, specialDef.radius, 1.0, specialDef.radius, 0.01);
        for (Entity entity : world.getNearbyEntities(location, specialDef.radius, specialDef.radius, specialDef.radius)) {
            Player player2;
            LivingEntity livingEntity;
            if (!(entity instanceof LivingEntity) || (livingEntity = (LivingEntity)entity) instanceof Player && (player2 = (Player)livingEntity).getUniqueId().equals(player.getUniqueId()) || !this.withinRadius(livingEntity.getLocation(), location, specialDef.radius)) continue;
            int n = specialDef.durationSeconds * 20;
            livingEntity.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, n, 2));
                livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, n, 0));
            }, () -> {});
        }
    }

    private void applyPotion(Player player, PotionEffectType potionEffectType, SpecialDef specialDef) {
        int n = specialDef.durationSeconds * 20;
        player.addPotionEffect(new PotionEffect(potionEffectType, n, 1));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.4f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0, 1.0, 0.0), 15, 0.4, 0.5, 0.4, 0.01);
    }

    private void tntLaunch(Player player, SpecialDef specialDef) {
        World world = player.getWorld();
        Location location = player.getLocation();
        Vector vector = player.getLocation().getDirection();
        UUID uUID = player.getUniqueId();
        Location location2 = location.clone().add(0.0, 1.4, 0.0);
        Location location3 = location2.clone();
        Vector vector2 = vector.clone().multiply(2.2);
        vector2.setY(Math.max(0.5, vector.getY() * 2.0 + 0.6));
        Vector vector3 = vector2.clone().normalize().multiply(1.4);
        Bukkit.getRegionScheduler().run((Plugin)this.plugin, location3, scheduledTask2 -> {
            TNTPrimed tNTPrimed = (TNTPrimed)world.spawnEntity(location3, EntityType.TNT);
            tNTPrimed.setVelocity(vector3);
            TNTPrimed tNTPrimed2 = tNTPrimed;
            try {
                tNTPrimed2.getPersistentDataContainer().set(this.keyTntInstant, PersistentDataType.STRING, (Object)"1");
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            tNTPrimed2.getScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> {
                try {
                    if (!tNTPrimed2.isValid() || tNTPrimed2.isDead()) {
                        scheduledTask.cancel();
                        return;
                    }
                    boolean bl = false;
                    if (tNTPrimed2.isOnGround()) {
                        bl = true;
                    }
                    if (!bl) {
                        for (Entity entity : tNTPrimed2.getNearbyEntities(0.8, 0.8, 0.8)) {
                            Player player;
                            if (!(entity instanceof LivingEntity) || entity instanceof Player && (player = (Player)entity).getUniqueId().equals(uUID)) continue;
                            bl = true;
                            break;
                        }
                    }
                    if (bl) {
                        scheduledTask.cancel();
                        tNTPrimed2.setFuseTicks(0);
                        tNTPrimed2.getWorld().playSound(tNTPrimed2.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.2f);
                    }
                }
                catch (Throwable throwable) {
                    try {
                        scheduledTask.cancel();
                    }
                    catch (Throwable throwable2) {
                        // empty catch block
                    }
                }
            }, () -> {}, 1L, 2L);
        });
    }

    private void repulse(Player player, SpecialDef specialDef) {
        World world = player.getWorld();
        Location location = player.getLocation();
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION, location.add(0.0, 1.0, 0.0), 30, specialDef.radius, 1.0, specialDef.radius, 0.02);
        for (Entity entity : world.getNearbyEntities(location, specialDef.radius, specialDef.radius, specialDef.radius)) {
            Player player2;
            LivingEntity livingEntity;
            if (!(entity instanceof LivingEntity) || (livingEntity = (LivingEntity)entity) instanceof Player && (player2 = (Player)livingEntity).getUniqueId().equals(player.getUniqueId()) || !this.withinRadius(livingEntity.getLocation(), location, specialDef.radius)) continue;
            double d = livingEntity.getLocation().distance(location);
            double d2 = Math.max(0.2, 1.0 - d / Math.max(1.0, specialDef.radius));
            livingEntity.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                Vector vector = livingEntity.getLocation().toVector().subtract(location.toVector());
                if (vector.lengthSquared() < 0.001) {
                    vector = new Vector(0, 1, 0);
                }
                vector.normalize();
                livingEntity.setVelocity(vector.multiply(specialDef.velocity * d2 + 0.4).setY(0.7 + d2 * 0.6));
            }, () -> {});
        }
    }

    private boolean track(Player player, SpecialDef specialDef) {
        UUID uUID = player.getUniqueId();
        GameManager gameManager = this.plugin.rooms().roomOf(uUID);
        ArrayList<UUID> arrayList = new ArrayList<UUID>();
        if (gameManager != null) {
            for (UUID uUID2 : gameManager.inGamePlayers()) {
                if (uUID2.equals(uUID) || gameManager.isEliminated(uUID2)) continue;
                arrayList.add(uUID2);
            }
        }
        if (arrayList.isEmpty()) {
            player.sendActionBar((Component)LegacyComponentSerializer.legacyAmpersand().deserialize("\u00a7c\u6ca1\u6709\u53ef\u8ffd\u8e2a\u7684\u654c\u4eba!"));
            return false;
        }
        UUID uUID3 = (UUID)arrayList.get(ThreadLocalRandom.current().nextInt(arrayList.size()));
        long l = (long)Math.max(10, specialDef.durationSeconds) * 1000L;
        this.trackTargets.put(uUID, uUID3);
        this.trackExpiry.put(uUID, System.currentTimeMillis() + l);
        Player player2 = Bukkit.getPlayer((UUID)uUID3);
        player.sendActionBar((Component)LegacyComponentSerializer.legacyAmpersand().deserialize("\u00a7e\u00a7l\u25a3 \u8ffd\u8e2a\u5f00\u542f! \u00a77\u76ee\u6807: \u00a7a" + (player2 != null ? player2.getName() : "?") + " \u00a77\u6301\u7eed \u00a7e" + specialDef.durationSeconds + " \u00a77\u79d2 (\u65b9\u5411+\u8ddd\u79bb\u5b9e\u65f6\u663e\u793a)"));
        try {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return true;
    }

    private Location aimPoint(Player player, double d) {
        Location location = player.getEyeLocation();
        Vector vector = location.getDirection();
        return location.clone().add(vector.clone().multiply(d));
    }

    private Location findGround(World world, Location location) {
        int n = location.getBlockX();
        int n2 = location.getBlockZ();
        int n3 = Math.min(location.getBlockY(), world.getMaxHeight() - 1);
        int n4 = 40;
        for (int i = 0; i < n4 && n3 > world.getMinHeight(); --n3, ++i) {
            if (world.getBlockAt(n, n3, n2).getType().isAir()) continue;
            return new Location(world, (double)n + 0.5, (double)(n3 + 1), (double)n2 + 0.5);
        }
        return null;
    }

    private void spawnParticleLine(World world, Location location, Location location2, Particle particle, int n) {
        if (location.getWorld() == null || location2.getWorld() == null) {
            return;
        }
        double d = location2.getX() - location.getX();
        double d2 = location2.getY() - location.getY();
        double d3 = location2.getZ() - location.getZ();
        double d4 = Math.max(0.001, Math.sqrt(d * d + d2 * d2 + d3 * d3));
        for (int i = 0; i < n; ++i) {
            double d5 = (double)i / (double)n;
            Location location3 = location.clone().add(d * d5, d2 * d5, d3 * d5);
            world.spawnParticle(particle, location3, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private boolean withinRadius(Location location, Location location2, double d) {
        return location.distance(location2) <= d;
    }

    public record SpecialDef(Material material, String key, String name, List<String> lore, String effect, double radius, double damage, double velocity, int durationSeconds) {
    }
}
