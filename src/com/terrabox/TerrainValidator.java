/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Chunk
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.Block
 */
package com.terrabox;

import com.terrabox.TerraBoxPlugin;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public class TerrainValidator {
    private final TerraBoxPlugin plugin;

    public TerrainValidator(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void validateWorld() {
        World world = this.plugin.worlds().world();
        if (world == null) {
            this.plugin.getLogger().warning("\u65e0\u6cd5\u9a8c\u8bc1\u5730\u5f62: \u4e16\u754c\u672a\u52a0\u8f7d");
            return;
        }
        this.plugin.getLogger().info("\u5f00\u59cb\u5730\u5f62\u9a8c\u8bc1 (\u5f02\u6b65)...");
        int[] nArray = new int[]{0};
        AtomicInteger atomicInteger = new AtomicInteger(0);
        AtomicInteger atomicInteger2 = new AtomicInteger(0);
        double d = this.plugin.worlds().borderHalf();
        int n = 16;
        int n2 = (int)Math.floor(-d / (double)n);
        int n3 = (int)Math.ceil(d / (double)n);
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        int n4 = n2;
        while (n4 <= n3) {
            arrayList.add(new int[]{n4, n2});
            arrayList.add(new int[]{n4, n3});
            arrayList.add(new int[]{n2, n4});
            arrayList.add(new int[]{n3, n4++});
        }
        ArrayList<int[]> arrayList2 = new ArrayList<int[]>();
        for (int i = 0; i < arrayList.size(); i += 4) {
            arrayList2.add((int[])arrayList.get(i));
        }
        arrayList2.add(new int[]{0, 0});
        arrayList2.add(new int[]{8, 8});
        arrayList2.add(new int[]{-8, -8});
        for (int[] nArray2 : arrayList2) {
            atomicInteger2.incrementAndGet();
            int n5 = nArray2[0];
            int n6 = nArray2[1];
            world.getChunkAtAsync(n5, n6).whenComplete((chunk, throwable) -> {
                try {
                    if (throwable != null) {
                        this.plugin.getLogger().warning("\u5730\u5f62\u9a8c\u8bc1\u533a\u5757\u52a0\u8f7d\u5931\u8d25 (" + n5 + "," + n6 + "): " + String.valueOf(throwable));
                        return;
                    }
                    atomicInteger.incrementAndGet();
                    if (chunk != null) {
                        this.validateChunk((Chunk)chunk, nArray);
                    }
                }
                finally {
                    if (atomicInteger2.decrementAndGet() == 0) {
                        this.plugin.getLogger().info("\u5730\u5f62\u9a8c\u8bc1\u5b8c\u6210: \u62bd\u67e5 " + atomicInteger.get() + " \u533a\u5757, " + nArray[0] + " \u5904\u63d0\u793a");
                    }
                }
            });
        }
    }

    private void validateChunk(Chunk chunk, int[] nArray) {
        int[][] nArrayArray;
        World world = chunk.getWorld();
        int n = chunk.getX();
        int n2 = chunk.getZ();
        for (int[] nArray2 : nArrayArray = new int[][]{{0, 0}, {7, 7}, {15, 15}, {0, 15}, {15, 0}}) {
            try {
                int n3 = world.getHighestBlockYAt(n * 16 + nArray2[0], n2 * 16 + nArray2[1]);
                Block block = world.getBlockAt(n * 16 + nArray2[0], n3, n2 * 16 + nArray2[1]);
                this.validateBlock(block, nArray);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    private void validateBlock(Block block, int[] nArray) {
        Block block2;
        if (block == null) {
            return;
        }
        if (block.getY() < 10 && block.getType() != Material.BEDROCK && block.getType() != Material.STONE) {
            nArray[0] = nArray[0] + 1;
            this.plugin.getLogger().warning("\u6df1\u5c42\u5f02\u5e38\u65b9\u5757: " + String.valueOf(block.getType()) + " at " + block.getX() + "," + block.getY() + "," + block.getZ());
        }
        if ((block.getType().name().contains("WATER") || block.getType().name().contains("LAVA")) && (block2 = block.getRelative(0, -1, 0)).getType().isAir()) {
            nArray[0] = nArray[0] + 1;
            this.plugin.getLogger().warning("\u6db2\u4f53\u60ac\u7a7a: " + String.valueOf(block.getType()) + " at " + block.getX() + "," + block.getY() + "," + block.getZ());
        }
    }
}
