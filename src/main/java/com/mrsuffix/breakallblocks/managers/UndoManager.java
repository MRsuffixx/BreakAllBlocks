package com.mrsuffix.breakallblocks.managers;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.model.WaveEvent;
import com.mrsuffix.breakallblocks.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * UndoManager — stores a bounded history of completed wave events and
 * provides block-restoration (undo) functionality.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>History is kept in a {@link Deque} bounded by {@code undo_history_size}.</li>
 *   <li>Undo restores blocks in batches on the main thread (200 blocks/tick)
 *       to avoid TPS spikes.</li>
 *   <li>Positions in unloaded chunks are force-loaded synchronously during undo
 *       (acceptable for an infrequent admin operation).</li>
 *   <li>Dropped item entities are removed by UUID on a best-effort basis
 *       (they may have been picked up or despawned).</li>
 * </ul>
 *
 * <b>Undo history is in-memory only and is lost on server restart.</b>
 *
 * @author MRsuffix
 */
public class UndoManager {

    private static final int RESTORE_BATCH_SIZE = 200;

    private final BreakAllBlocks     plugin;
    private final ConfigManager      cfg;
    private final EliminationManager eliminationManager;

    /** Newest events are at the front (index 0). */
    private final Deque<WaveEvent> history = new ArrayDeque<>();

    public UndoManager(BreakAllBlocks plugin, ConfigManager cfg,
                       EliminationManager eliminationManager) {
        this.plugin             = plugin;
        this.cfg                = cfg;
        this.eliminationManager = eliminationManager;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Records a completed wave event at the front of the history.
     * Trims to {@code undo_history_size} automatically.
     */
    public void record(WaveEvent event) {
        history.addFirst(event);
        int max = cfg.getUndoHistorySize();
        while (history.size() > max) history.removeLast();
    }

    /**
     * Returns an unmodifiable snapshot of the undo history (newest first).
     */
    public List<WaveEvent> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public int getHistorySize() {
        return history.size();
    }

    /**
     * Undoes the last {@code count} wave events.
     * Restores block positions, removes dropped items, and removes materials
     * from the eliminated list.
     *
     * @param count  number of events to undo (clamped to available history)
     * @param sender who issued the command (receives feedback messages)
     */
    public void undo(int count, CommandSender sender) {
        if (history.isEmpty()) {
            MessageUtil.send(sender,
                    "<dark_gray>[<gradient:#FF6B6B:#FFD93D>BAB</gradient>]</dark_gray> "
                            + "<red>No undo history available.</red>");
            return;
        }

        int actualCount = Math.min(count, history.size());
        List<WaveEvent> toRestore = new ArrayList<>();
        for (int i = 0; i < actualCount; i++) {
            toRestore.add(history.pollFirst());
        }

        // Chain restoration tasks sequentially.
        restoreNext(toRestore, 0, sender);
    }

    // ── Chained restoration ────────────────────────────────────────────────

    private void restoreNext(List<WaveEvent> events, int idx, CommandSender sender) {
        if (idx >= events.size()) return;
        WaveEvent event = events.get(idx);
        startRestoreTask(event, sender, () -> restoreNext(events, idx + 1, sender));
    }

    private void startRestoreTask(WaveEvent event, CommandSender sender, Runnable onComplete) {
        World world = Bukkit.getWorld(event.getWorldName());
        if (world == null) {
            MessageUtil.send(sender,
                    "<red>Cannot undo " + event.getMaterial().name()
                            + ": world '" + event.getWorldName() + "' is not loaded.</red>");
            onComplete.run();
            return;
        }

        if (event.isUndoCapExceeded()) {
            MessageUtil.send(sender,
                    "<yellow>Warning: undo for <bold>" + event.getMaterial().name()
                            + "</bold> is partial — undo cap was exceeded during the wave.</yellow>");
        }

        // Remove material from eliminated list.
        eliminationManager.restore(event.getMaterial());

        // Remove dropped items (best effort).
        for (UUID uuid : event.getDroppedItemUUIDs()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity instanceof Item) {
                entity.remove();
            }
        }

        List<Long> positions = event.getBrokenPositions();
        if (positions.isEmpty()) {
            broadcastUndo(sender, event.getMaterial().name());
            onComplete.run();
            return;
        }

        // Restore blocks in batches.
        Material material = event.getMaterial();
        int[]    index    = {0};
        int[]    restored = {0};

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int end = Math.min(index[0] + RESTORE_BATCH_SIZE, positions.size());

            while (index[0] < end) {
                long pos = positions.get(index[0]++);
                int x = WaveManager.decodeX(pos);
                int y = WaveManager.decodeY(pos);
                int z = WaveManager.decodeZ(pos);

                // Force-load chunk if needed.
                int cx = x >> 4, cz = z >> 4;
                if (!world.isChunkLoaded(cx, cz)) {
                    world.loadChunk(cx, cz, true); // synchronous, acceptable for admin undo
                }

                Block block = world.getBlockAt(x, y, z);
                // Only restore if the slot is currently AIR (don't overwrite other blocks).
                if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) {
                    block.setType(material, false);
                    restored[0]++;
                }
            }

            if (index[0] >= positions.size()) {
                taskHolder[0].cancel();
                plugin.getLogger().info("[BAB] Undo complete for " + material.name()
                        + ": restored " + restored[0] + " block(s).");
                broadcastUndo(sender, material.name());
                onComplete.run();
            }
        }, 0L, 1L);
    }

    private void broadcastUndo(CommandSender sender, String materialName) {
        String msg = cfg.getMsgUndoBroadcast()
                .replace("{admin}", sender.getName())
                .replace("{block}", materialName);
        MessageUtil.broadcastParsed(msg, cfg.getPrefix());
    }
}
