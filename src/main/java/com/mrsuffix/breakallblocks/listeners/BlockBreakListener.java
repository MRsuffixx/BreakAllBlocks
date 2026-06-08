package com.mrsuffix.breakallblocks.listeners;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.managers.ConfigManager;
import com.mrsuffix.breakallblocks.managers.EliminationManager;
import com.mrsuffix.breakallblocks.managers.WaveManager;
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
 * Listens for block-break events and kicks off a BFS wave via {@link WaveManager}.
 *
 * <p>When a player breaks a block:</p>
 * <ol>
 *   <li>Excluded / already-eliminated materials are rejected.</li>
 *   <li>The material is immediately marked as eliminated in {@link EliminationManager}
 *       so no other player can trigger a second wave.</li>
 *   <li>A broadcast is sent to all online players.</li>
 *   <li>{@link WaveManager#startWave} is called with the broken block's position
 *       as the wave origin. The event is <em>not</em> cancelled — Minecraft handles
 *       breaking the origin block normally; the wave then spreads to its neighbors.</li>
 * </ol>
 *
 * @author MRsuffix
 */
public class BlockBreakListener implements Listener {

    private final BreakAllBlocks    plugin;
    private final ConfigManager     cfg;
    private final EliminationManager eliminationManager;
    private final WaveManager       waveManager;

    public BlockBreakListener(BreakAllBlocks plugin, ConfigManager cfg,
                               EliminationManager eliminationManager, WaveManager waveManager) {
        this.plugin             = plugin;
        this.cfg                = cfg;
        this.eliminationManager = eliminationManager;
        this.waveManager        = waveManager;
    }

    // ── Player block break ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!cfg.isEnabled()) return;

        Player   player   = event.getPlayer();
        Block    block    = event.getBlock();
        Material material = block.getType();

        // World filter
        if (!cfg.isWorldActive(block.getWorld().getName())) return;

        // Creative mode filter
        if (player.getGameMode() == GameMode.CREATIVE && !cfg.isCountCreativeBreaks()) return;

        // Bypass permission: player can break eliminated blocks freely
        if (player.hasPermission("breakallblocks.bypass")) return;

        // Needs use permission to trigger elimination
        if (!player.hasPermission("breakallblocks.use")) return;

        // Protected material
        if (cfg.isExcluded(material)) {
            event.setCancelled(true);
            MessageUtil.sendParsed(player, cfg.getMsgExcludedMaterial(), cfg.getPrefix());
            return;
        }

        // Already eliminated: block should not exist, but just in case
        if (eliminationManager.isEliminated(material)) {
            event.setCancelled(true);
            MessageUtil.sendParsed(player, cfg.getMsgAlreadyEliminated(), cfg.getPrefix());
            return;
        }

        // Wave already running for this material in this world → merge
        if (waveManager.isWaveActive(material, block.getWorld())) {
            // Mark as eliminated so nobody else can trigger again, then merge.
            eliminationManager.eliminate(material);
            waveManager.startWave(block.getX(), block.getY(), block.getZ(),
                    block.getWorld(), material, player);
            // Let the break event proceed — origin block destroyed by player.
            broadcastElimination(player.getName(), material);
            return;
        }

        // ── Trigger new elimination ───────────────────────────────────────

        // 1. Persist immediately so no concurrent player can trigger again.
        eliminationManager.eliminate(material);

        // 2. Broadcast to all players.
        broadcastElimination(player.getName(), material);

        // 3. Start the BFS wave from the broken block's position.
        //    We delay by 1 tick so the BlockBreakEvent fully resolves (block becomes AIR)
        //    before the wave's seedNeighbors() runs.
        final int ox = block.getX(), oy = block.getY(), oz = block.getZ();
        plugin.getServer().getScheduler().runTask(plugin, () ->
                waveManager.startWave(ox, oy, oz, block.getWorld(), material, player));

        // 4. Let the event proceed — player breaks the origin block naturally.
    }

    // ── Indirect breaks: Explosions ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!cfg.isEnabled() || !cfg.isCountIndirectBreaks()) return;

        for (Block block : event.blockList()) {
            Material material = block.getType();
            if (!cfg.isWorldActive(block.getWorld().getName())) continue;
            if (cfg.isExcluded(material) || eliminationManager.isEliminated(material)) continue;

            eliminationManager.eliminate(material);
            broadcastElimination("an Explosion", material);

            final int ox = block.getX(), oy = block.getY(), oz = block.getZ();
            final var world = block.getWorld();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    waveManager.startWave(ox, oy, oz, world, material, null));
        }
    }

    // ── Indirect breaks: Pistons ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!cfg.isEnabled() || !cfg.isCountIndirectBreaks()) return;
        // Prevent pistons from moving eliminated-material blocks (they should be gone).
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

    // ── Helpers ────────────────────────────────────────────────────────────

    private void broadcastElimination(String actorName, Material material) {
        if (!cfg.isBroadcastMessages()) return;
        String msg = cfg.getMsgEliminationBroadcast()
                .replace("{player}", actorName)
                .replace("{block}", material.name());
        MessageUtil.broadcastParsed(msg, cfg.getPrefix());
    }
}
