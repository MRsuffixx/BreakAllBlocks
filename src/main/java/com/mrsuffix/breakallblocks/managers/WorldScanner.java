package com.mrsuffix.breakallblocks.managers;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.model.WaveEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Startup helper: finds eliminated-material blocks in loaded chunks and
 * hands them to {@link WaveManager} for cleanup waves.
 *
 * @author MRsuffix
 */
public class WorldScanner {

    private final BreakAllBlocks     plugin;
    private final ConfigManager      cfg;
    private final EliminationManager eliminationManager;
    private final WaveManager        waveManager;

    private BukkitTask scanTask;

    public WorldScanner(BreakAllBlocks plugin, ConfigManager cfg,
                        EliminationManager eliminationManager, WaveManager waveManager) {
        this.plugin             = plugin;
        this.cfg                = cfg;
        this.eliminationManager = eliminationManager;
        this.waveManager        = waveManager;
    }

    public void scanAllWorldsForEliminated(CommandSender initiator) {
        Set<Material> eliminated = eliminationManager.getEliminatedMaterials();
        if (eliminated.isEmpty()) return;

        List<Chunk> chunks = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (!cfg.isWorldActive(world.getName())) continue;
            for (Chunk chunk : world.getLoadedChunks()) chunks.add(chunk);
        }
        if (chunks.isEmpty()) return;

        plugin.getLogger().info("[BAB] Startup scan: " + chunks.size()
                + " chunk(s), " + eliminated.size() + " eliminated material(s).");

        Set<String> wavesStarted = new HashSet<>();
        int[] idx = {0};

        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int batchEnd = Math.min(idx[0] + 5, chunks.size());
            while (idx[0] < batchEnd) {
                Chunk chunk = chunks.get(idx[0]++);
                if (!chunk.isLoaded()) continue;
                World world = chunk.getWorld();
                for (Material mat : eliminated) {
                    String wk = mat.name() + ":" + world.getName();
                    if (wavesStarted.contains(wk) || waveManager.isWaveActive(mat, world)) {
                        wavesStarted.add(wk);
                        continue;
                    }
                    Block found = findFirst(chunk, mat);
                    if (found != null) {
                        wavesStarted.add(wk);
                        if (cfg.isDropItems()) found.breakNaturally();
                        else found.setType(Material.AIR, false);
                        waveManager.startWave(found.getX(), found.getY(), found.getZ(),
                                world, mat, initiator, WaveEvent.TriggerType.STARTUP_SCAN, "System");
                    }
                }
            }
            if (idx[0] >= chunks.size()) { scanTask.cancel(); }
        }, 0L, 1L);
    }

    public void cancelAll() {
        if (scanTask != null && !scanTask.isCancelled()) scanTask.cancel();
    }

    private static Block findFirst(Chunk chunk, Material material) {
        World world = chunk.getWorld();
        for (int lx = 0; lx < 16; lx++)
            for (int ly = world.getMinHeight(); ly < world.getMaxHeight(); ly++)
                for (int lz = 0; lz < 16; lz++) {
                    Block b = chunk.getBlock(lx, ly, lz);
                    if (b.getType() == material) return b;
                }
        return null;
    }
}
