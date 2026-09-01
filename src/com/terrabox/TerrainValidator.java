package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * 地形验证器: 检查地形完整性和安全性
 *
 * Folia 线程模型: 所有世界方块访问必须走 getChunkAtAsync → RegionScheduler。
 * 本类不再在 Global/主线程同步读区块 (会导致 "Async chunk retrieval" 异常),
 * 改为对边界区块做异步轻量校验 (只抽查表面方块, 不遍历全高)。
 */
public class TerrainValidator {
    private final TerraBoxPlugin plugin;

    public TerrainValidator(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    /** 验证整个世界地形 (异步, 不阻塞调用线程) */
    public void validateWorld() {
        World world = plugin.worlds().world();
        if (world == null) {
            plugin.getLogger().warning("无法验证地形: 世界未加载");
            return;
        }
        plugin.getLogger().info("开始地形验证 (异步)...");
        final int[] sampled = {0};
        final java.util.concurrent.atomic.AtomicInteger checked = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(0);

        // 只取边界一圈的代表性区块做抽查 (避免遍历全部 65x65 区块全高)
        double borderHalf = plugin.worlds().borderHalf();
        int chunkSize = 16;
        int minCx = (int) Math.floor(-borderHalf / chunkSize);
        int maxCx = (int) Math.ceil(borderHalf / chunkSize);
        List<int[]> borderChunks = new ArrayList<>();
        for (int x = minCx; x <= maxCx; x++) {
            borderChunks.add(new int[]{x, minCx});
            borderChunks.add(new int[]{x, maxCx});
            borderChunks.add(new int[]{minCx, x});
            borderChunks.add(new int[]{maxCx, x});
        }
        // 抽样: 每 4 个边界区块取 1 个 (减少加载压力)
        List<int[]> sample = new ArrayList<>();
        for (int i = 0; i < borderChunks.size(); i += 4) sample.add(borderChunks.get(i));
        // 加上 4 个内部区块 (中心附近)
        sample.add(new int[]{0, 0});
        sample.add(new int[]{8, 8});
        sample.add(new int[]{-8, -8});

        for (int[] c : sample) {
            pending.incrementAndGet();
            final int cx = c[0], cz = c[1];
            world.getChunkAtAsync(cx, cz).whenComplete((chunk, err) -> {
                try {
                    if (err != null) {
                        plugin.getLogger().warning("地形验证区块加载失败 (" + cx + "," + cz + "): " + err);
                        return;
                    }
                    checked.incrementAndGet();
                    if (chunk != null) validateChunk(chunk, sampled);
                } finally {
                    if (pending.decrementAndGet() == 0) {
                        plugin.getLogger().info("地形验证完成: 抽查 " + checked.get() + " 区块, " + sampled[0] + " 处提示");
                    }
                }
            });
        }
    }

    /** 校验单个区块 (区域线程, 只抽查表面方块避免全高遍历) */
    private void validateChunk(Chunk chunk, int[] sampled) {
        World world = chunk.getWorld();
        int cx = chunk.getX(), cz = chunk.getZ();
        // 只对区块四角 + 中心的表面方块做抽查 (避免遍历全高 384 层)
        int[][] pts = {{0, 0}, {7, 7}, {15, 15}, {0, 15}, {15, 0}};
        for (int[] p : pts) {
            try {
                int y = world.getHighestBlockYAt(cx * 16 + p[0], cz * 16 + p[1]);
                Block b = world.getBlockAt(cx * 16 + p[0], y, cz * 16 + p[1]);
                validateBlock(b, sampled);
            } catch (Throwable t) {
                // 跨区块读取降级, 忽略
            }
        }
    }

    /** 抽查单个方块 */
    private void validateBlock(Block block, int[] sampled) {
        if (block == null) return;
        // 深层异常方块
        if (block.getY() < 10 && block.getType() != org.bukkit.Material.BEDROCK
                && block.getType() != org.bukkit.Material.STONE) {
            sampled[0]++;
            plugin.getLogger().warning("深层异常方块: " + block.getType() + " at "
                    + block.getX() + "," + block.getY() + "," + block.getZ());
        }
        // 液体悬空
        if (block.getType().name().contains("WATER") || block.getType().name().contains("LAVA")) {
            Block below = block.getRelative(0, -1, 0);
            if (below.getType().isAir()) {
                sampled[0]++;
                plugin.getLogger().warning("液体悬空: " + block.getType() + " at "
                        + block.getX() + "," + block.getY() + "," + block.getZ());
            }
        }
    }
}
