package com.mrsuffix.breakallblocks.listeners;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.managers.ConfigManager;
import com.mrsuffix.breakallblocks.managers.EliminationManager;
import com.mrsuffix.breakallblocks.managers.EntityMappingManager;
import com.mrsuffix.breakallblocks.managers.WaveManager;
import com.mrsuffix.breakallblocks.model.WaveEvent;
import com.mrsuffix.breakallblocks.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;

/**
 * Listens for {@link EntityDeathEvent} and triggers a BFS wave for each
 * block material mapped to the killed entity type.
 *
 * <p>Wave origin = entity death location.</p>
 *
 * @author MRsuffix
 */
public class EntityDeathListener implements Listener {

    private final BreakAllBlocks      plugin;
    private final ConfigManager       cfg;
    private final EliminationManager  eliminationManager;
    private final EntityMappingManager entityMappingManager;
    private final WaveManager         waveManager;

    public EntityDeathListener(BreakAllBlocks plugin, ConfigManager cfg,
                                EliminationManager eliminationManager,
                                EntityMappingManager entityMappingManager,
                                WaveManager waveManager) {
        this.plugin               = plugin;
        this.cfg                  = cfg;
        this.eliminationManager   = eliminationManager;
        this.entityMappingManager = entityMappingManager;
        this.waveManager          = waveManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!cfg.isEnabled()) return;

        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return; // must be killed by a player

        // World filter
        if (!cfg.isWorldActive(entity.getWorld().getName())) return;

        EntityType entityType = entity.getType();
        List<Material> mappedMaterials = entityMappingManager.getMappings(entityType);

        if (mappedMaterials.isEmpty()) return; // no mapping — do nothing

        Location deathLoc = entity.getLocation();
        int ox = deathLoc.getBlockX();
        int oy = deathLoc.getBlockY();
        int oz = deathLoc.getBlockZ();

        for (Material material : mappedMaterials) {
            // Guard: excluded material
            if (cfg.isExcluded(material)) continue;

            // Guard: already eliminated
            if (eliminationManager.isEliminated(material)) {
                // Optionally notify killer that this material is already gone
                continue;
            }

            // Eliminate + start wave (1-tick delay since entity is still alive now)
            eliminationManager.eliminate(material, killer.getName(), "MOB_KILL");

            // Broadcast
            if (cfg.isBroadcastMessages()) {
                String msg = cfg.getMsgMobKillBroadcast()
                        .replace("{player}", killer.getName())
                        .replace("{entity}", formatEntityType(entityType))
                        .replace("{block}", material.name());
                MessageUtil.broadcastParsed(msg, cfg.getPrefix());
            }

            // Start the wave on next tick (entity body has settled).
            final Material mat = material;
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    waveManager.startWave(ox, oy, oz, entity.getWorld(), mat,
                            killer, WaveEvent.TriggerType.MOB_KILL, killer.getName()));
        }
    }

    // Converts e.g. "WITHER_SKELETON" → "Wither Skeleton"
    private static String formatEntityType(EntityType type) {
        String raw = type.name().replace('_', ' ');
        String[] words = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0)))
              .append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
