package com.mrsuffix.breakallblocks.listeners;

import com.mrsuffix.breakallblocks.managers.WaveManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;

/**
 * Captures UUIDs of item entities spawned by {@code block.breakNaturally()}
 * calls during a wave, associating them with the current {@link WaveManager.WaveTask}
 * for undo purposes.
 *
 * <p>{@code BlockDropItemEvent} fires synchronously within {@code breakNaturally()}.
 * The {@link WaveManager#getCurrentlyBreaking()} flag is set around each call,
 * so this listener correctly identifies wave-caused drops vs player-caused drops.</p>
 *
 * @author MRsuffix
 */
public class BlockDropItemListener implements Listener {

    private final WaveManager waveManager;

    public BlockDropItemListener(WaveManager waveManager) {
        this.waveManager = waveManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        WaveManager.WaveTask task = waveManager.getCurrentlyBreaking();
        if (task == null) return;

        // Sanity-check: only record drops for the material this task is breaking.
        Material brokenType = event.getBlockState().getType();
        if (brokenType != task.material) return;

        for (var item : event.getItems()) {
            task.recordDroppedItem(item.getUniqueId());
        }
    }
}
