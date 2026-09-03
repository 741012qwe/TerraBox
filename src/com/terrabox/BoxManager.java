/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Particle
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockState
 *  org.bukkit.block.Chest
 *  org.bukkit.block.TileState
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.LootAuditLogger;
import com.terrabox.Rarity;
import com.terrabox.TerraBoxPlugin;
import com.terrabox.TerrainType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.lang.invoke.CallSite;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class BoxManager {
    private final TerraBoxPlugin plugin;
    private final NamespacedKey keyRarity;
    private final NamespacedKey keyBorn;
    private final NamespacedKey keyAirdrop;
    private final CopyOnWriteArrayList<BoxEntry> registry = new CopyOnWriteArrayList();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicInteger placedSinceBatchLog = new AtomicInteger(0);
    private static final int BATCH_LOG_INTERVAL = 20;
    private ScheduledTask maintainTask;
    private ScheduledTask particleTask;

    public BoxManager(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
        this.keyRarity = new NamespacedKey((Plugin)terraBoxPlugin, "rarity");
        this.keyBorn = new NamespacedKey((Plugin)terraBoxPlugin, "born");
        this.keyAirdrop = new NamespacedKey((Plugin)terraBoxPlugin, "airdrop");
    }

    public void start() {
        this.loading.set(true);
        Bukkit.getAsyncScheduler().runNow((Plugin)this.plugin, scheduledTask -> {
            this.registry.addAll(this.loadRegistry());
            this.plugin.getLogger().info("\u7269\u8d44\u7bb1\u6ce8\u518c\u8868\u52a0\u8f7d\u5b8c\u6210: " + this.registry.size() + " \u4e2a");
            this.loading.set(false);
        });
        long l = 1200L;
        this.maintainTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> this.maintain(), l, l);
        if (this.plugin.getConfig().getBoolean("particles.enabled", true)) {
            long l2 = Math.max(20, this.plugin.getConfig().getInt("particles.interval-ticks", 60));
            this.particleTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> this.spawnParticles(), l2, l2);
        }
    }

    public void shutdown() {
        int n;
        if (this.maintainTask != null) {
            this.maintainTask.cancel();
        }
        if (this.particleTask != null) {
            this.particleTask.cancel();
        }
        if ((n = this.placedSinceBatchLog.getAndSet(0)) > 0) {
            this.plugin.getLogger().info("\u7269\u8d44\u7bb1\u6295\u653e\u6c47\u603b: \u6700\u8fd1 " + n + " \u4e2a\u7bb1\u5b50\u5df2\u767b\u8bb0");
        }
        this.saveRegistryNow();
    }

    public void spawnRandomBox(Rarity rarity, boolean bl, Consumer<BoxEntry> consumer) {
        this.spawnRandomBox(rarity, bl, consumer, 0);
    }

    private void spawnRandomBox(Rarity rarity, boolean bl, Consumer<BoxEntry> consumer, int n) {
        double d;
        int n2;
        World world = this.plugin.worlds().world();
        if (world == null) {
            return;
        }
        int n3 = Math.max(4, this.plugin.getConfig().getInt("spawn.tries", 10));
        double d2 = this.plugin.worlds().borderHalf();
        int n4 = Math.max(8, this.plugin.getConfig().getInt("boxes.edge-padding", 24));
        double d3 = Math.max(32.0, d2 - (double)n4);
        int n5 = ThreadLocalRandom.current().nextInt((int)(-d3), (int)d3 + 1);
        if (this.tooClose(n5, n2 = ThreadLocalRandom.current().nextInt((int)(-d3), (int)d3 + 1), d = this.plugin.getConfig().getDouble("boxes.min-distance", 24.0))) {
            this.retryOrGiveUp(rarity, bl, consumer, n, n3, "\u8ddd\u79bb\u8fc7\u8fd1");
            return;
        }
        int n6 = n5 >> 4;
        int n7 = n2 >> 4;
        int n8 = n5;
        int n9 = n2;
        int n10 = n;
        int n11 = n3;
        world.getChunkAtAsync(n6, n7).whenComplete((chunk, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("\u7269\u8d44\u7bb1\u533a\u5757\u52a0\u8f7d\u5931\u8d25 (" + n6 + "," + n7 + "): " + String.valueOf(throwable));
                this.retryOrGiveUp(rarity, bl, consumer, n10, n11, "\u533a\u5757\u52a0\u8f7d\u5931\u8d25");
                return;
            }
            Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask2 -> {
                try {
                    world.setChunkForceLoaded(n6, n7, true);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                Bukkit.getRegionScheduler().run((Plugin)this.plugin, world, n6, n7, scheduledTask -> {
                    try {
                        this.plugin.getLogger().info("\u7269\u8d44\u7bb1\u653e\u7f6e\u4efb\u52a1\u6267\u884c: \u5c1d\u8bd5(" + n8 + "," + n9 + ")");
                        this.tryPlace(world, n8, n9, rarity, bl, consumer, n10, n11);
                    }
                    catch (Throwable throwable) {
                        this.plugin.getLogger().warning("\u7269\u8d44\u7bb1\u653e\u7f6e\u5f02\u5e38 (" + n8 + "," + n9 + "): " + String.valueOf(throwable));
                        throwable.printStackTrace();
                        this.retryOrGiveUp(rarity, bl, consumer, n10, n11, "\u653e\u7f6e\u5f02\u5e38");
                    }
                    finally {
                        try {
                            world.setChunkForceLoaded(n6, n7, false);
                        }
                        catch (Throwable throwable) {}
                    }
                });
            });
        });
    }

    public void spawnBoxAt(int n, int n2, Rarity rarity, boolean bl, Consumer<BoxEntry> consumer) {
        World world = this.plugin.worlds().world();
        if (world == null) {
            return;
        }
        int n3 = n >> 4;
        int n4 = n2 >> 4;
        world.getChunkAtAsync(n3, n4).whenComplete((chunk, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("\u56fa\u5b9a\u7269\u8d44\u7bb1\u533a\u5757\u52a0\u8f7d\u5931\u8d25 (" + n3 + "," + n4 + "): " + String.valueOf(throwable));
                return;
            }
            Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask2 -> {
                try {
                    world.setChunkForceLoaded(n3, n4, true);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                Bukkit.getRegionScheduler().run((Plugin)this.plugin, world, n3, n4, scheduledTask -> {
                    try {
                        this.tryPlaceAt(world, n, n2, rarity, bl, consumer);
                    }
                    catch (Throwable throwable) {
                        this.plugin.getLogger().warning("\u56fa\u5b9a\u7269\u8d44\u7bb1\u653e\u7f6e\u5f02\u5e38 (" + n + "," + n2 + "): " + String.valueOf(throwable));
                        throwable.printStackTrace();
                    }
                    finally {
                        try {
                            world.setChunkForceLoaded(n3, n4, false);
                        }
                        catch (Throwable throwable) {}
                    }
                });
            });
        });
    }

    private void retryOrGiveUp(Rarity rarity, boolean bl, Consumer<BoxEntry> consumer, int n, int n2, String string) {
        if (n < n2) {
            Bukkit.getAsyncScheduler().runNow((Plugin)this.plugin, scheduledTask -> this.spawnRandomBox(rarity, bl, consumer, n + 1));
        } else {
            this.plugin.getLogger().info("\u7269\u8d44\u7bb1\u6295\u653e\u653e\u5f03(" + string + ", \u91cd\u8bd5 " + n + " \u6b21)");
        }
    }

    private void tryPlace(World world, int n, int n2, Rarity rarity, boolean bl, Consumer<BoxEntry> consumer, int n3, int n4) {
        if (!world.getWorldBorder().isInside(new Location(world, (double)n, 64.0, (double)n2))) {
            this.retryOrGiveUp(rarity, bl, consumer, n3, n4, "\u8d8a\u754c");
            return;
        }
        if (!this.isOpenGround(world, n, n2)) {
            this.retryOrGiveUp(rarity, bl, consumer, n3, n4, "\u5730\u5f62\u4e0d\u5e73\u5766");
            return;
        }
        this.tryPlaceAt(world, n, n2, rarity, bl, consumer);
    }

    private void tryPlaceAt(World world, int n, int n2, Rarity rarity, boolean bl, Consumer<BoxEntry> consumer) {
        int n3 = world.getHighestBlockYAt(n, n2);
        Block block = world.getBlockAt(n, n3, n2);
        if (!this.validGround(block)) {
            return;
        }
        int n4 = n3 + 1;
        Block block2 = world.getBlockAt(n, n4, n2);
        if (!block2.getType().isAir()) {
            return;
        }
        block2.setType(Material.CHEST, false);
        Block block3 = world.getBlockAt(n, n4, n2);
        BlockState blockState = block3.getState();
        if (!(blockState instanceof Chest)) {
            this.plugin.getLogger().warning("\u7269\u8d44\u7bb1\u653e\u7f6e\u5931\u8d25(" + n + "," + n2 + "): \u7bb1\u5b50\u72b6\u6001\u65e0\u6548");
            return;
        }
        Chest chest = (Chest)blockState;
        blockState = chest.getPersistentDataContainer();
        long l = System.currentTimeMillis();
        blockState.set(this.keyRarity, PersistentDataType.STRING, (Object)rarity.name());
        blockState.set(this.keyBorn, PersistentDataType.LONG, (Object)l);
        if (bl) {
            blockState.set(this.keyAirdrop, PersistentDataType.BYTE, (Object)1);
        }
        chest.customName((Component)Component.text((String)(rarity.display + "\u7269\u8d44\u7bb1"), (TextColor)rarity.color));
        chest.update();
        BlockState blockState2 = world.getBlockAt(n, n4, n2).getState();
        if (!(blockState2 instanceof Chest)) {
            this.plugin.getLogger().warning("\u7269\u8d44\u7bb1\u72b6\u6001\u5237\u65b0\u5931\u8d25(" + n + "," + n2 + ")");
            return;
        }
        Chest chest2 = (Chest)blockState2;
        int n5 = this.plugin.loot().fillInventory(chest2.getBlockInventory(), rarity);
        BoxEntry boxEntry = new BoxEntry(n, n4, n2, rarity, l, bl);
        this.registry.add(boxEntry);
        this.markDirty();
        int n6 = this.placedSinceBatchLog.incrementAndGet();
        if (n6 % 20 == 0) {
            int n7 = this.placedSinceBatchLog.getAndSet(0);
            this.plugin.getLogger().info("\u7269\u8d44\u7bb1\u6295\u653e\u6c47\u603b: \u6700\u8fd1 " + n7 + " \u4e2a\u7bb1\u5b50\u5df2\u767b\u8bb0");
        } else if (n6 == 1) {
            // empty if block
        }
        try {
            this.auditLootGeneration(n5);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().warning("\u9053\u5177\u5ba1\u8ba1\u5931\u8d25: " + String.valueOf(throwable));
        }
        if (bl) {
            world.strikeLightningEffect(new Location(world, (double)n + 0.5, (double)n4, (double)n2 + 0.5));
        }
        if (consumer != null) {
            consumer.accept(boxEntry);
        }
    }

    private void auditLootGeneration(int n) {
        this.plugin.lootAuditLogger().logBoxGeneration(LootAuditLogger.SYSTEM, n);
    }

    private boolean isOpenGround(World world, int n, int n2) {
        try {
            boolean bl = false;
            if (this.plugin.arenas() != null) {
                TerrainType terrainType = this.plugin.arenas().terrainOf(world.getName());
                bl = terrainType == TerrainType.BADLANDS || terrainType == TerrainType.NORMAL || terrainType == TerrainType.NETHER || terrainType == TerrainType.THE_END;
            }
            int n3 = bl ? 6 : 3;
            int n4 = world.getHighestBlockYAt(n, n2);
            for (int i = -1; i <= 1; ++i) {
                for (int j = -1; j <= 1; ++j) {
                    int n5 = world.getHighestBlockYAt(n + i, n2 + j);
                    if (Math.abs(n5 - n4) > n3) {
                        return false;
                    }
                    Block block = world.getBlockAt(n + i, n5, n2 + j);
                    if (this.validGround(block)) continue;
                    return false;
                }
            }
            return true;
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    private boolean validGround(Block block) {
        if (block == null) {
            return false;
        }
        Material material = block.getType();
        if (!material.isSolid()) {
            return false;
        }
        String string = material.name();
        return !string.contains("LEAVES") && !string.contains("LOG") && !string.contains("STEM") && !string.contains("WATER") && !string.contains("ICE") && !string.contains("LILY");
    }

    private boolean tooClose(int n, int n2, double d) {
        double d2 = d * d;
        for (BoxEntry boxEntry : this.registry) {
            long l = boxEntry.x - n;
            long l2 = boxEntry.z - n2;
            if (!((double)(l * l + l2 * l2) < d2)) continue;
            return true;
        }
        return false;
    }

    private void maintain() {
        int n;
        World world = this.plugin.worlds().world();
        if (world == null || this.loading.get()) {
            return;
        }
        long l = this.plugin.getConfig().getLong("boxes.refresh-minutes", 45L) * 60000L;
        long l2 = System.currentTimeMillis();
        ArrayList<BoxEntry> arrayList = new ArrayList<BoxEntry>();
        for (BoxEntry boxEntry : this.registry) {
            if (l2 - boxEntry.born <= l) continue;
            arrayList.add(boxEntry);
        }
        int n2 = Math.min(30, arrayList.size());
        for (n = 0; n < n2; ++n) {
            BoxEntry boxEntry = (BoxEntry)arrayList.get(n);
            this.removeBoxAt(world, boxEntry, () -> {
                if (this.plugin.getConfig().getBoolean("boxes.refill-on-open", true)) {
                    this.spawnRandomBox(boxEntry.rarity, false, null);
                }
            });
        }
        n = Math.min(this.plugin.boxRefillPerCycle(), this.plugin.boxMaxCount() - this.registry.size());
        for (int i = 0; i < n; ++i) {
            this.spawnRandomBox(this.plugin.weightedPickForWorld(), false, null);
        }
    }

    public BoxEntry registeredAt(Block block) {
        if (block.getType() != Material.CHEST) {
            return null;
        }
        BlockState blockState = block.getState();
        if (!(blockState instanceof TileState)) {
            return null;
        }
        TileState tileState = (TileState)blockState;
        blockState = tileState.getPersistentDataContainer();
        String string = (String)blockState.get(this.keyRarity, PersistentDataType.STRING);
        if (string == null) {
            return null;
        }
        Rarity rarity = Rarity.parse(string);
        if (rarity == null) {
            return null;
        }
        long l = (Long)blockState.getOrDefault(this.keyBorn, PersistentDataType.LONG, (Object)0L);
        boolean bl = (Byte)blockState.getOrDefault(this.keyAirdrop, PersistentDataType.BYTE, (Object)0) == 1;
        Location location = block.getLocation();
        return new BoxEntry(location.getBlockX(), location.getBlockY(), location.getBlockZ(), rarity, l, bl);
    }

    public void handleChestEmptied(Block block, BoxEntry boxEntry) {
        this.registry.removeIf(boxEntry2 -> boxEntry2.x == boxEntry.x && boxEntry2.y == boxEntry.y && boxEntry2.z == boxEntry.z);
        this.markDirty();
        block.setType(Material.AIR, false);
        if (this.plugin.getConfig().getBoolean("boxes.refill-on-open", true)) {
            this.spawnRandomBox(boxEntry.rarity, false, null);
        }
    }

    private void removeBoxAt(World world, BoxEntry boxEntry, Runnable runnable) {
        this.registry.removeIf(boxEntry2 -> boxEntry2.x == boxEntry.x && boxEntry2.y == boxEntry.y && boxEntry2.z == boxEntry.z);
        this.markDirty();
        int n = boxEntry.x >> 4;
        int n2 = boxEntry.z >> 4;
        world.getChunkAtAsync(n, n2).thenAccept(chunk -> Bukkit.getRegionScheduler().run((Plugin)this.plugin, world, n, n2, scheduledTask -> {
            BoxEntry boxEntry2;
            Block block = world.getBlockAt(boxEntry.x, boxEntry.y, boxEntry.z);
            if (block.getType() == Material.CHEST && (boxEntry2 = this.registeredAt(block)) != null && boxEntry2.born == boxEntry.born) {
                BlockState blockState = block.getState();
                if (blockState instanceof Chest) {
                    Chest chest = (Chest)blockState;
                    try {
                        chest.getBlockInventory().clear();
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                }
                block.setType(Material.AIR, false);
                if (runnable != null) {
                    runnable.run();
                }
            }
        }));
    }

    public void wipeAll(Runnable runnable) {
        World world = this.plugin.worlds().world();
        if (world == null) {
            return;
        }
        ArrayList<BoxEntry> arrayList = new ArrayList<BoxEntry>(this.registry);
        this.registry.clear();
        this.markDirty();
        int[] nArray = new int[]{arrayList.size()};
        if (arrayList.isEmpty()) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }
        for (BoxEntry boxEntry : arrayList) {
            int n = boxEntry.x >> 4;
            int n2 = boxEntry.z >> 4;
            world.getChunkAtAsync(n, n2).thenAccept(chunk -> Bukkit.getRegionScheduler().run((Plugin)this.plugin, world, n, n2, scheduledTask -> {
                Object object;
                Block block = world.getBlockAt(boxEntry.x, boxEntry.y, boxEntry.z);
                if (block.getType() == Material.CHEST) {
                    BlockState blockState = block.getState();
                    if (blockState instanceof Chest) {
                        object = (Chest)blockState;
                        try {
                            object.getBlockInventory().clear();
                        }
                        catch (Throwable throwable) {
                            // empty catch block
                        }
                    }
                    block.setType(Material.AIR, false);
                }
                object = nArray;
                synchronized (nArray) {
                    nArray[0] = nArray[0] - 1;
                    if (nArray[0] <= 0 && runnable != null) {
                        runnable.run();
                    }
                    // ** MonitorExit[var6_8 /* !! */ ] (shouldn't be in output)
                    return;
                }
            }));
        }
    }

    private void spawnParticles() {
        World world = this.plugin.worlds().world();
        if (world == null) {
            return;
        }
        ArrayList<Rarity> arrayList = new ArrayList<Rarity>();
        for (String object : this.plugin.getConfig().getStringList("particles.rarities")) {
            Rarity rarity = Rarity.parse(object);
            if (rarity == null) continue;
            arrayList.add(rarity);
        }
        if (arrayList.isEmpty()) {
            return;
        }
        for (BoxEntry boxEntry : this.registry) {
            if (!arrayList.contains((Object)boxEntry.rarity)) continue;
            Bukkit.getRegionScheduler().run((Plugin)this.plugin, world, boxEntry.x >> 4, boxEntry.z >> 4, scheduledTask -> {
                Location location = new Location(world, (double)boxEntry.x + 0.5, (double)boxEntry.y + 1.2, (double)boxEntry.z + 0.5);
                world.spawnParticle(Particle.END_ROD, location, 3, 0.2, 0.3, 0.2, 0.01);
            });
        }
    }

    public int count() {
        return this.registry.size();
    }

    public Map<Rarity, Integer> countByRarity() {
        EnumMap<Rarity, Integer> enumMap = new EnumMap<Rarity, Integer>(Rarity.class);
        for (BoxEntry boxEntry : this.registry) {
            enumMap.merge(boxEntry.rarity, 1, Integer::sum);
        }
        return enumMap;
    }

    public BoxEntry randomOf(List<Rarity> list) {
        ArrayList<BoxEntry> arrayList = new ArrayList<BoxEntry>();
        for (BoxEntry boxEntry : this.registry) {
            if (!list.contains((Object)boxEntry.rarity)) continue;
            arrayList.add(boxEntry);
        }
        if (arrayList.isEmpty()) {
            return null;
        }
        return (BoxEntry)arrayList.get(ThreadLocalRandom.current().nextInt(arrayList.size()));
    }

    public World worldOf(BoxEntry boxEntry) {
        return this.plugin.worlds().world();
    }

    private void markDirty() {
        if (this.saveQueued.compareAndSet(false, true)) {
            Bukkit.getAsyncScheduler().runDelayed((Plugin)this.plugin, scheduledTask -> {
                this.saveQueued.set(false);
                this.saveRegistryNow();
            }, 3L, TimeUnit.SECONDS);
        }
    }

    private void saveRegistryNow() {
        try {
            File file = new File(this.plugin.getDataFolder(), "boxes.yml");
            if (!this.plugin.getDataFolder().exists()) {
                this.plugin.getDataFolder().mkdirs();
            }
            YamlConfiguration yamlConfiguration = new YamlConfiguration();
            ArrayList<CallSite> arrayList = new ArrayList<CallSite>();
            for (BoxEntry boxEntry : this.registry) {
                arrayList.add((CallSite)((Object)(boxEntry.x + ";" + boxEntry.y + ";" + boxEntry.z + ";" + boxEntry.rarity.name() + ";" + boxEntry.born + ";" + (boxEntry.airdrop ? 1 : 0))));
            }
            yamlConfiguration.set("boxes", arrayList);
            yamlConfiguration.save(file);
        }
        catch (Exception exception) {
            this.plugin.getLogger().warning("\u7269\u8d44\u7bb1\u6ce8\u518c\u8868\u4fdd\u5b58\u5931\u8d25: " + exception.getMessage());
        }
    }

    private List<BoxEntry> loadRegistry() {
        ArrayList<BoxEntry> arrayList = new ArrayList<BoxEntry>();
        File file = new File(this.plugin.getDataFolder(), "boxes.yml");
        if (!file.isFile()) {
            return arrayList;
        }
        try {
            YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration((File)file);
            for (String string : yamlConfiguration.getStringList("boxes")) {
                try {
                    String[] stringArray = string.split(";");
                    Rarity rarity = Rarity.parse(stringArray[3]);
                    if (rarity == null) continue;
                    arrayList.add(new BoxEntry(Integer.parseInt(stringArray[0]), Integer.parseInt(stringArray[1]), Integer.parseInt(stringArray[2]), rarity, Long.parseLong(stringArray[4]), stringArray.length > 5 && "1".equals(stringArray[5])));
                }
                catch (Exception exception) {}
            }
        }
        catch (Exception exception) {
            this.plugin.getLogger().warning("\u7269\u8d44\u7bb1\u6ce8\u518c\u8868\u52a0\u8f7d\u5931\u8d25: " + exception.getMessage());
        }
        return arrayList;
    }

    public static final class BoxEntry {
        public final int x;
        public final int y;
        public final int z;
        public final Rarity rarity;
        public final long born;
        public final boolean airdrop;

        public BoxEntry(int n, int n2, int n3, Rarity rarity, long l, boolean bl) {
            this.x = n;
            this.y = n2;
            this.z = n3;
            this.rarity = rarity;
            this.born = l;
            this.airdrop = bl;
        }
    }
}
