package com.mrsuffix.breakallblocks.listeners;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.managers.ConfigManager;
import com.mrsuffix.breakallblocks.managers.EliminationManager;
import com.mrsuffix.breakallblocks.managers.WaveManager;
import com.mrsuffix.breakallblocks.model.WaveEvent;
import com.mrsuffix.breakallblocks.util.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

/**
 * Handles player (and optional indirect) block-break events, triggering
 * BFS wave elimination via {@link WaveManager}.
 *
 * @author MRsuffix
 */
public class BlockBreakListener implements Listener {

    private final BreakAllBlocks     plugin;
    private final ConfigManager      cfg;
    private final EliminationManager eliminationManager;
    private final WaveManager        waveManager;

    public BlockBreakListener(BreakAllBlocks plugin, ConfigManager cfg,
                               EliminationManager eliminationManager, WaveManager waveManager) {
        this.plugin             = plugin;
        this.cfg                = cfg;
        this.eliminationManager = eliminationManager;
        this.waveManager        = waveManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!cfg.isEnabled()) return;

        Player   player   = event.getPlayer();
        Block    block    = event.getBlock();
        Material material = block.getType();

        if (!cfg.isWorldActive(block.getWorld().getName())) return;
        if (player.getGameMode() == GameMode.CREATIVE && !cfg.isCountCreativeBreaks()) return;
        if (player.hasPermission("breakallblocks.bypass")) return;
        if (!player.hasPermission("breakallblocks.use")) return;

        if (cfg.isExcluded(material)) {
            event.setCancelled(true);
            MessageUtil.sendParsed(player, cfg.getMsgExcludedMaterial(), cfg.getPrefix());
            return;
        }

        if (eliminationManager.isEliminated(material)) {
            event.setCancelled(true);
            MessageUtil.sendParsed(player, cfg.getMsgAlreadyEliminated(), cfg.getPrefix());
            return;
        }

        // Wave already running → merge
        if (waveManager.isWaveActive(material, block.getWorld())) {
            eliminationManager.eliminate(material, player.getName(), "BLOCK_BREAK");
            broadcastElimination(player.getName(), material);
            final int ox = block.getX(), oy = block.getY(), oz = block.getZ();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    waveManager.startWave(ox, oy, oz, block.getWorld(), material,
                            player, WaveEvent.TriggerType.BLOCK_BREAK, player.getName()));
            return;
        }

        // New elimination
        eliminationManager.eliminate(material, player.getName(), "BLOCK_BREAK");
        broadcastElimination(player.getName(), material);

        final int ox = block.getX(), oy = block.getY(), oz = block.getZ();
        final var world = block.getWorld();
        plugin.getServer().getScheduler().runTask(plugin, () ->
                waveManager.startWave(ox, oy, oz, world, material,
                        player, WaveEvent.TriggerType.BLOCK_BREAK, player.getName()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!cfg.isEnabled() || !cfg.isCountIndirectBreaks()) return;
        for (Block block : event.blockList()) {
            Material material = block.getType();
            if (!cfg.isWorldActive(block.getWorld().getName())) continue;
            if (cfg.isExcluded(material) || eliminationManager.isEliminated(material)) continue;
            eliminationManager.eliminate(material, "Explosion", "BLOCK_BREAK");
            broadcastElimination("an Explosion", material);
            final int ox = block.getX(), oy = block.getY(), oz = block.getZ();
            final var world = block.getWorld();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    waveManager.startWave(ox, oy, oz, world, material,
                            null, WaveEvent.TriggerType.BLOCK_BREAK, "Explosion"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!cfg.isEnabled() || !cfg.isCountIndirectBreaks()) return;
        for (Block block : event.getBlocks())
            if (eliminationManager.isEliminated(block.getType())) { event.setCancelled(true); return; }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!cfg.isEnabled() || !cfg.isCountIndirectBreaks()) return;
        for (Block block : event.getBlocks())
            if (eliminationManager.isEliminated(block.getType())) { event.setCancelled(true); return; }
    }

    private void broadcastElimination(String actorName, Material material) {
        if (!cfg.isBroadcastMessages()) return;
        String msg = cfg.getMsgEliminationBroadcast()
                .replace("{player}", actorName)
                .replace("{block}", material.name());
        MessageUtil.broadcastParsed(msg, cfg.getPrefix());
    }
}
