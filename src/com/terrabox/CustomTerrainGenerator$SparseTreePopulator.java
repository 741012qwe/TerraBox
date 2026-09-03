/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Chunk
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.generator.BlockPopulator
 */
package com.terrabox;

import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;

private static class CustomTerrainGenerator.SparseTreePopulator
extends BlockPopulator {
    private CustomTerrainGenerator.SparseTreePopulator() {
    }

    public void populate(World world, Random random, Chunk chunk) {
        if (random.nextDouble() > 0.18) {
            return;
        }
        int n = 1;
        for (int i = 0; i < n; ++i) {
            int n2 = random.nextInt(16);
            int n3 = random.nextInt(16);
            int n4 = world.getMinHeight();
            Block block = world.getBlockAt(chunk.getX() * 16 + n2, n4 + 60, chunk.getZ() * 16 + n3);
            Material material = block.getType();
            if (material == Material.GRASS_BLOCK) {
                this.growOak(world, block.getLocation().add(0.0, 1.0, 0.0), random);
                continue;
            }
            if (material != Material.SAND || !(random.nextDouble() < 0.4)) continue;
            world.getBlockAt(block.getX(), block.getY() + 1, block.getZ()).setType(Material.CACTUS, false);
        }
    }

    private void growOak(World world, Location location, Random random) {
        int n;
        int n2 = 2 + random.nextInt(3);
        for (n = 0; n < n2; ++n) {
            Block block = world.getBlockAt(location.getBlockX(), location.getBlockY() + n, location.getBlockZ());
            if (!block.getType().isAir() && block.getType().isSolid()) continue;
            block.setType(Material.OAK_LOG, false);
        }
        n = location.getBlockY() + n2;
        for (int i = -2; i <= 2; ++i) {
            for (int j = -2; j <= 2; ++j) {
                for (int k = -1; k <= 1; ++k) {
                    int n3 = n + k;
                    Block block = world.getBlockAt(location.getBlockX() + i, n3, location.getBlockZ() + j);
                    if (!block.getType().isAir() && block.getType().isSolid() || Math.abs(i) == 2 && Math.abs(j) == 2 && random.nextDouble() < 0.5) continue;
                    block.setType(Material.OAK_LEAVES, false);
                }
            }
        }
    }
}
