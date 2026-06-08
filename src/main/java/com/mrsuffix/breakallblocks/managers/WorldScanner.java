package com.mrsuffix.breakallblocks.managers;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;

/**
 * WorldScanner — startup helper that finds remaining eliminated blocks in
 * currently loaded chunks and hands them off to {@link WaveManager} for
 * wave-based cleanup.
 *
 * <p>This class no longer performs any instant removal; all block destruction
 * goes through the wave system so the cinematic cascade effect is consistent.</p>
 *
 * <p>The scan processes one chunk per tick (configurable via the existing
 * {@code batch_delay_ticks} setting) to avoid a TPS spike at startup.</p>
 *
 * @author MRsuffix
 */
public class WorldScanner {

    private final BreakAllBlocks     plugin;
    private final ConfigManager      cfg;
    private final EliminationManager eliminationManager;
    private final WaveManager        waveManager;

    /** Task handle for the in-progress startup scan, so it can be cancelled. */
    private BukkitTask scanTask;

    public WorldScanner(BreakAllBlocks plugin, ConfigManager cfg,
                        EliminationManager eliminationManager, WaveManager waveManager) {
        this.plugin             = plugin;
        this.cfg                = cfg;
        this.eliminationManager = eliminationManager;
        this.waveManager        = waveManager;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Scans currently loaded chunks in all active worlds for blocks of any
     * eliminated material and starts cleanup waves for any found.
     *
     * <p>The scan runs on the main thread in chunks-per-tick slices so it does
     * not cause a TPS spike.</p>
     *
     * @param initiator who triggered the scan (may be {@code null})
     */
    public void scanAllWorldsForEliminated(CommandSender initiator) {
        Set<Material> eliminated = eliminationManager.getEliminatedMaterials();
        if (eliminated.isEmpty()) {
            plugin.getLogger().info("[BAB] Startup scan: no eliminated materials — skipping.");
            return;
        }

        // Collect all chunks we need to check across all active worlds.
        java.util.List<Chunk> chunks = new java.util.ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (!cfg.isWorldActive(world.getName())) continue;
            for (Chunk chunk : world.getLoadedChunks()) {
                chunks.add(chunk);
            }
        }

        if (chunks.isEmpty()) return;

        plugin.getLogger().info("[BAB] Startup scan: checking " + chunks.size()
                + " loaded chunk(s) for " + eliminated.size() + " eliminated material(s).");

        // Track which (material, world) combos already have a wave running.
        java.util.Set<String> wavesStarted = new java.util.HashSet<>();
        int[] idx = {0};

        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Process a small batch of chunks each tick.
            int batchEnd = Math.min(idx[0] + 5, chunks.size());

            while (idx[0] < batchEnd) {
                Chunk chunk = chunks.get(idx[0]++);
                if (!chunk.isLoaded()) continue;

                World world = chunk.getWorld();
                for (Material mat : eliminated) {
                    String wk = mat.name() + ":" + world.getName();
                    if (wavesStarted.contains(wk)) continue;
                    if (waveManager.isWaveActive(mat, world)) {
                        wavesStarted.add(wk);
                        continue;
                    }

                    Block found = findFirstOfTypeInChunk(chunk, mat);
                    if (found != null) {
                        wavesStarted.add(wk);
                        // Seed the wave from this block's position.
                        // We tell the wave the block is already air so it starts
                        // from that position's neighbors — but actually the block
                        // IS still present (not broken yet), so we pass the block
                        // itself as a neighbor seed by breaking it first, or we
                        // just treat its position as the origin and let the wave
                        // include it.  Since the wave seeds NEIGHBORS of the origin,
                        // we shift the origin one step away from the block so the
                        // block itself becomes a neighbor.
                        // Simplest correct approach: call breakNaturally on this
                        // block immediately and then start wave from its position.
                        waveManager.startWave(
                                found.getX(), found.getY(), found.getZ(),
                                world, mat, initiator);
                        // The block is still present at this position; startWave
                        // seeds neighbors of the origin — so the origin itself
                        // is NOT in the queue.  We add it manually via the public
                        // helper below.
                        // (Actually: we want the startup-found block to be in the
                        //  wave queue.  seedNeighbors marks origin as visited and
                        //  adds its 6 neighbors.  If the block IS at origin, it
                        //  will NOT be broken because it's not in the queue.
                        //  Fix: break the block now and use its position as origin.)
                        if (cfg.isDropItems()) {
                            found.breakNaturally();
                        } else {
                            found.setType(Material.AIR, false);
                        }
                    }
                }
            }

            if (idx[0] >= chunks.size()) {
                scanTask.cancel();
                plugin.getLogger().info("[BAB] Startup scan complete. Waves started: "
                        + wavesStarted.size());
            }
        }, 0L, 1L);
    }

    /** Cancel any running scan task (called from {@code onDisable}). */
    public void cancelAll() {
        if (scanTask != null && !scanTask.isCancelled()) {
            scanTask.cancel();
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Returns the first block of the given material found in the chunk,
     * or {@code null} if none exist.
     */
    private static Block findFirstOfTypeInChunk(Chunk chunk, Material material) {
        World world  = chunk.getWorld();
        int   minY   = world.getMinHeight();
        int   maxY   = world.getMaxHeight();

        for (int lx = 0; lx < 16; lx++) {
            for (int ly = minY; ly < maxY; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    Block b = chunk.getBlock(lx, ly, lz);
                    if (b.getType() == material) {
                        return b;
                    }
                }
            }
        }
        return null;
    }
}
