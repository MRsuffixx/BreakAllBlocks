package com.mrsuffix.breakallblocks.listeners;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.managers.ConfigManager;
import com.mrsuffix.breakallblocks.managers.EliminationManager;
import com.mrsuffix.breakallblocks.managers.WorldScanner;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Listens for chunk load events and cleans any eliminated block types
 * that may have generated (or been present) in newly-loaded chunks.
 *
 * <p>This is the "lazy" counterpart to the startup re-scan:
 * unloaded chunks are handled here so we never need to force-load them.</p>
 */
public class ChunkLoadListener implements Listener {

    private final BreakAllBlocks plugin;
    private final ConfigManager cfg;
    private final EliminationManager eliminationManager;
    private final WorldScanner worldScanner;

    public ChunkLoadListener(BreakAllBlocks plugin,
                              ConfigManager cfg,
                              EliminationManager eliminationManager,
                              WorldScanner worldScanner) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.eliminationManager = eliminationManager;
        this.worldScanner = worldScanner;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!cfg.isEnabled()) return;
        if (eliminationManager.getEliminatedMaterials().isEmpty()) return;
        if (!cfg.isWorldActive(event.getWorld().getName())) return;

        // Run on next tick so the chunk is fully settled before we scan it
        Chunk chunk = event.getChunk();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) {
                worldScanner.scanChunk(chunk);
            }
        });
    }
}
