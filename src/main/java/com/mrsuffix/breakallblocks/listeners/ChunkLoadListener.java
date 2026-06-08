package com.mrsuffix.breakallblocks.listeners;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.managers.ConfigManager;
import com.mrsuffix.breakallblocks.managers.EliminationManager;
import com.mrsuffix.breakallblocks.managers.WaveManager;
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
 * Listens for chunk load events and serves two purposes:
 *
 * <ol>
 *   <li><b>Wave continuation</b> — notifies {@link WaveManager} so any active
 *       waves waiting on this chunk can flush their pending-position queues.</li>
 *   <li><b>Orphan cleanup</b> — if the newly loaded chunk contains blocks of an
 *       eliminated material but there is no active wave for that material in this
 *       world, starts a fresh cleanup wave. This handles disconnected pockets that
 *       were not reached by the original wave (e.g. the ore vein was isolated).</li>
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

        // Step 1 — notify active waves so they can flush pending positions.
        waveManager.onChunkLoaded(chunk);

        Set<Material> eliminated = eliminationManager.getEliminatedMaterials();
        if (eliminated.isEmpty()) return;

        // Step 2 — scan for orphaned eliminated blocks (defer by 1 tick so the
        // chunk's block data is fully settled before we read it).
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!chunk.isLoaded()) return;

            World  world        = chunk.getWorld();
            int    minY         = world.getMinHeight();
            int    maxY         = world.getMaxHeight();
            // Track which materials we've already started a wave for in this pass.
            Set<Material> wavesStartedThisScan = new HashSet<>();

            for (int lx = 0; lx < 16; lx++) {
                for (int ly = minY; ly < maxY; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        Block b = chunk.getBlock(lx, ly, lz);
                        Material type = b.getType();

                        if (!eliminated.contains(type)) continue;
                        if (wavesStartedThisScan.contains(type)) continue;
                        if (waveManager.isWaveActive(type, world)) {
                            wavesStartedThisScan.add(type); // no need to check more of this type
                            continue;
                        }

                        // Start a fresh wave from this orphan block.
                        wavesStartedThisScan.add(type);
                        final int bx = b.getX(), by = b.getY(), bz = b.getZ();
                        if (cfg.isDropItems()) {
                            b.breakNaturally();
                        } else {
                            b.setType(Material.AIR, false);
                        }
                        waveManager.startWave(bx, by, bz, world, type, null);
                    }
                }
            }
        });
    }
}
