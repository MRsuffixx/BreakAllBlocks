package com.mrsuffix.breakallblocks.listeners;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.managers.ConfigManager;
import com.mrsuffix.breakallblocks.managers.EliminationManager;
import com.mrsuffix.breakallblocks.managers.WaveManager;
import com.mrsuffix.breakallblocks.model.WaveEvent;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Serves two purposes on every {@link ChunkLoadEvent}:
 * <ol>
 *   <li>Flushes pending positions for active waves that requested this chunk.</li>
 *   <li>Starts orphan-cleanup waves for eliminated materials found in the chunk
 *       that have no active wave (handles disconnected pockets and other worlds).</li>
 * </ol>
 *
 * @author MRsuffix
 */
public class ChunkLoadListener implements Listener {

    private final BreakAllBlocks     plugin;
    private final ConfigManager      cfg;
    private final EliminationManager eliminationManager;
    private final WaveManager        waveManager;

    public ChunkLoadListener(BreakAllBlocks plugin, ConfigManager cfg,
                              EliminationManager eliminationManager, WaveManager waveManager) {
        this.plugin             = plugin;
        this.cfg                = cfg;
        this.eliminationManager = eliminationManager;
        this.waveManager        = waveManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!cfg.isEnabled()) return;
        if (!cfg.isWorldActive(event.getWorld().getName())) return;

        Chunk chunk = event.getChunk();

        // Step 1: flush pending positions for active waves.
        waveManager.onChunkLoaded(chunk);

        Set<Material> eliminated = eliminationManager.getEliminatedMaterials();
        if (eliminated.isEmpty()) return;

        // Step 2: scan for orphaned eliminated blocks (1 tick delay to let chunk settle).
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!chunk.isLoaded()) return;

            World  world  = chunk.getWorld();
            int    minY   = world.getMinHeight();
            int    maxY   = world.getMaxHeight();
            Set<Material> wavesStarted = new HashSet<>();

            for (int lx = 0; lx < 16; lx++) {
                for (int ly = minY; ly < maxY; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        Block b = chunk.getBlock(lx, ly, lz);
                        Material type = b.getType();
                        if (!eliminated.contains(type)) continue;
                        if (wavesStarted.contains(type)) continue;
                        if (waveManager.isWaveActive(type, world)) {
                            wavesStarted.add(type);
                            continue;
                        }
                        wavesStarted.add(type);
                        final int bx = b.getX(), by = b.getY(), bz = b.getZ();
                        if (cfg.isDropItems()) b.breakNaturally(); else b.setType(Material.AIR, false);
                        waveManager.startWave(bx, by, bz, world, type, null,
                                WaveEvent.TriggerType.STARTUP_SCAN, "System");
                    }
                }
            }
        });
    }
}
