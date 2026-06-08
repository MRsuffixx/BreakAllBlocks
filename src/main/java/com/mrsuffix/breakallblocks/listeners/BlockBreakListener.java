package com.mrsuffix.breakallblocks.listeners;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.managers.ConfigManager;
import com.mrsuffix.breakallblocks.managers.EliminationManager;
import com.mrsuffix.breakallblocks.managers.WorldScanner;
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
 * Listens for block break events (player-caused and optionally indirect)
 * and triggers mass elimination of the broken block type.
 */
public class BlockBreakListener implements Listener {

    private final BreakAllBlocks plugin;
    private final ConfigManager cfg;
    private final EliminationManager eliminationManager;
    private final WorldScanner worldScanner;

    public BlockBreakListener(BreakAllBlocks plugin,
                               ConfigManager cfg,
                               EliminationManager eliminationManager,
                               WorldScanner worldScanner) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.eliminationManager = eliminationManager;
        this.worldScanner = worldScanner;
    }

    // ── Player block break ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!cfg.isEnabled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();

        // World check
        if (!cfg.isWorldActive(block.getWorld().getName())) return;

        // Creative mode check
        if (player.getGameMode() == GameMode.CREATIVE && !cfg.isCountCreativeBreaks()) return;

        // Bypass permission
        if (player.hasPermission("breakallblocks.bypass")) return;

        // Play-use permission
        if (!player.hasPermission("breakallblocks.use")) return;

        // Excluded material check
        if (cfg.isExcluded(material)) {
            MessageUtil.sendParsed(player, cfg.getMsgExcludedMaterial(), cfg.getPrefix());
            event.setCancelled(true);
            return;
        }

        // Already eliminated?
        if (eliminationManager.isEliminated(material)) {
            event.setCancelled(true);
            MessageUtil.sendParsed(player, cfg.getMsgAlreadyEliminated(), cfg.getPrefix());
            return;
        }

        // --- Trigger elimination ---
        // 1. Mark as eliminated (persists immediately)
        eliminationManager.eliminate(material);

        // 2. Remove the broken block right now (it's still there until the event finishes)
        //    The event will naturally remove it, so we just let it proceed.

        // 3. Broadcast to all players
        if (cfg.isBroadcastMessages()) {
            String msg = cfg.getMsgEliminationBroadcast()
                    .replace("{player}", player.getName())
                    .replace("{block}", material.name());
            MessageUtil.broadcastParsed(msg, cfg.getPrefix());
        }

        // 4. Sweep all worlds asynchronously for remaining blocks of this type
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                worldScanner.sweepAllWorlds(material, player), 1L);
    }

    // ── Indirect breaks: Explosions ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!cfg.isEnabled() || !cfg.isCountIndirectBreaks()) return;

        for (Block block : event.blockList()) {
            Material material = block.getType();
            if (!cfg.isWorldActive(block.getWorld().getName())) continue;
            if (cfg.isExcluded(material)) continue;
            if (eliminationManager.isEliminated(material)) continue;

            eliminationManager.eliminate(material);

            if (cfg.isBroadcastMessages()) {
                String msg = cfg.getMsgEliminationBroadcast()
                        .replace("{player}", "an Explosion")
                        .replace("{block}", material.name());
                MessageUtil.broadcastParsed(msg, cfg.getPrefix());
            }

            final Material finalMat = material;
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    worldScanner.sweepAllWorlds(finalMat, null), 1L);
        }
    }

    // ── Indirect breaks: Pistons ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!cfg.isEnabled() || !cfg.isCountIndirectBreaks()) return;
        // Pistons move blocks — this would be a complex elimination trigger.
        // For simplicity: if a piston tries to move an eliminated-material block, cancel it.
        for (Block block : event.getBlocks()) {
            if (eliminationManager.isEliminated(block.getType())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!cfg.isEnabled() || !cfg.isCountIndirectBreaks()) return;
        for (Block block : event.getBlocks()) {
            if (eliminationManager.isEliminated(block.getType())) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
