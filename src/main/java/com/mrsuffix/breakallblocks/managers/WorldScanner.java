package com.mrsuffix.breakallblocks.managers;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * WorldScanner — responsible for scanning worlds and removing eliminated block types.
 *
 * <p>Strategy:</p>
 * <ol>
 *   <li>For <strong>loaded chunks</strong>: iterate all blocks asynchronously, collect
 *       those matching the target material, then set them to AIR on the main thread
 *       in configurable batches to stay lag-free.</li>
 *   <li>For <strong>unloaded chunks</strong>: {@link ChunkLoadListener} calls
 *       {@link #scanChunk} whenever a new chunk loads, so they are cleaned up lazily.</li>
 * </ol>
 */
public class WorldScanner {

    private final BreakAllBlocks plugin;
    private final ConfigManager cfg;
    private final EliminationManager eliminationManager;

    /** Tracks currently running sweep tasks so we can cancel them on disable. */
    private final Map<Integer, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    private int taskIdCounter = 0;

    public WorldScanner(BreakAllBlocks plugin, ConfigManager cfg, EliminationManager eliminationManager) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.eliminationManager = eliminationManager;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Begins a full sweep across all active worlds for the given material.
     * The {@code initiator} (may be null) receives progress/completion messages.
     */
    public void sweepAllWorlds(Material material, CommandSender initiator) {
        for (World world : Bukkit.getWorlds()) {
            if (!cfg.isWorldActive(world.getName())) continue;
            sweepWorld(world, material, initiator);
        }
    }

    /**
     * Triggered on server startup to re-remove all eliminated materials
     * from currently loaded chunks in case anything regenerated.
     */
    public void scanAllWorldsForEliminated(CommandSender initiator) {
        Set<Material> eliminated = eliminationManager.getEliminatedMaterials();
        if (eliminated.isEmpty()) return;

        for (Material mat : eliminated) {
            sweepAllWorlds(mat, initiator);
        }
    }

    /**
     * Scans a single chunk for any eliminated materials and removes them.
     * Called by ChunkLoadListener each time a chunk loads.
     */
    public void scanChunk(Chunk chunk) {
        if (!cfg.isEnabled()) return;
        if (!cfg.isWorldActive(chunk.getWorld().getName())) return;

        Set<Material> eliminated = eliminationManager.getEliminatedMaterials();
        if (eliminated.isEmpty()) return;

        // Collect matching blocks in the newly loaded chunk (sync — chunk is loaded)
        List<Block> toRemove = new ArrayList<>();
        int maxY = chunk.getWorld().getMaxHeight();
        int minY = chunk.getWorld().getMinHeight();

        for (int x = 0; x < 16; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = 0; z < 16; z++) {
                    Block b = chunk.getBlock(x, y, z);
                    if (eliminated.contains(b.getType())) {
                        toRemove.add(b);
                    }
                }
            }
        }

        if (toRemove.isEmpty()) return;

        // Remove in batches on the main thread (we're already on it for chunk events,
        // but batch it to avoid micro-stutter if the chunk is very dirty)
        removeBatched(toRemove, null, null);
    }

    /** Cancel all running sweep tasks (called from onDisable). */
    public void cancelAll() {
        for (BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private void sweepWorld(World world, Material material, CommandSender initiator) {
        Chunk[] loadedChunks = world.getLoadedChunks();
        int totalChunks = loadedChunks.length;

        if (initiator != null) {
            MessageUtil.sendParsed(initiator,
                    cfg.getMsgScanStarted()
                            .replace("{block}", material.name())
                            .replace("{chunks}", String.valueOf(totalChunks)),
                    cfg.getPrefix());
        }

        plugin.getLogger().info("Starting sweep for " + material.name() +
                " in world '" + world.getName() + "' (" + totalChunks + " loaded chunks).");

        // Async: collect all matching block positions
        AtomicInteger totalRemoved = new AtomicInteger(0);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Block> collected = new ArrayList<>();

            for (Chunk chunk : loadedChunks) {
                if (!chunk.isLoaded()) continue; // may have unloaded since snapshot
                try {
                    collectFromChunk(chunk, material, collected);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Error scanning chunk " + chunk.getX() + "," + chunk.getZ(), e);
                }
            }

            if (collected.isEmpty()) {
                plugin.getLogger().info("Sweep complete for " + material.name() +
                        " in '" + world.getName() + "': 0 blocks found.");
                if (initiator != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            MessageUtil.sendParsed(initiator,
                                    cfg.getMsgScanComplete()
                                            .replace("{block}", material.name())
                                            .replace("{count}", "0"),
                                    cfg.getPrefix()));
                }
                return;
            }

            plugin.getLogger().info("Sweep for " + material.name() + " collected " +
                    collected.size() + " blocks in '" + world.getName() + "'. Removing...");

            // Back on main thread — remove in batches
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    removeBatched(collected, initiator, material));
        });
    }

    /**
     * Iterates all blocks in a chunk looking for the target material.
     * Must only be called from an async context when the chunk is loaded.
     */
    private void collectFromChunk(Chunk chunk, Material material, List<Block> result) {
        World world = chunk.getWorld();
        int maxY = world.getMaxHeight();
        int minY = world.getMinHeight();

        for (int x = 0; x < 16; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = 0; z < 16; z++) {
                    Block b = chunk.getBlock(x, y, z);
                    if (b.getType() == material) {
                        result.add(b);
                    }
                }
            }
        }
    }

    /**
     * Removes blocks from the supplied list in {@code cfg.batchSize} batches,
     * separated by {@code cfg.batchDelayTicks} ticks on the main thread.
     */
    private void removeBatched(List<Block> blocks, CommandSender initiator, Material material) {
        int batchSize = cfg.getBatchSize();
        int delayTicks = cfg.getBatchDelayTicks();
        AtomicInteger index = new AtomicInteger(0);
        AtomicLong removed = new AtomicLong(0);
        int taskKey = taskIdCounter++;

        BukkitTask[] taskHolder = new BukkitTask[1];

        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int start = index.get();
            if (start >= blocks.size()) {
                // Done
                taskHolder[0].cancel();
                activeTasks.remove(taskKey);
                long count = removed.get();
                plugin.getLogger().info("Batch removal complete: " + count + " blocks removed" +
                        (material != null ? " for " + material.name() : "") + ".");
                if (initiator != null && material != null) {
                    MessageUtil.sendParsed(initiator,
                            cfg.getMsgScanComplete()
                                    .replace("{block}", material.name())
                                    .replace("{count}", String.valueOf(count)),
                            cfg.getPrefix());
                }
                return;
            }

            int end = Math.min(start + batchSize, blocks.size());
            for (int i = start; i < end; i++) {
                Block b = blocks.get(i);
                // Re-verify the block is still the target type before removing
                if (material == null || b.getType() == material) {
                    b.setType(Material.AIR, false); // false = don't apply physics (performance)
                    removed.incrementAndGet();
                }
            }
            index.set(end);

        }, 0L, delayTicks);

        activeTasks.put(taskKey, taskHolder[0]);
    }
}
