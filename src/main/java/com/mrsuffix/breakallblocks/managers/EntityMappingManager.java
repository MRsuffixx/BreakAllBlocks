package com.mrsuffix.breakallblocks.managers;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and provides the entity-type → block-material mappings used by
 * {@link com.mrsuffix.breakallblocks.listeners.EntityDeathListener}.
 *
 * <p>Config path: {@code entity_block_mappings}</p>
 * <pre>
 * entity_block_mappings:
 *   COW: [GRASS_BLOCK, DIRT]
 *   CREEPER: [STONE, COBBLESTONE]
 * </pre>
 *
 * @author MRsuffix
 */
public class EntityMappingManager {

    private final BreakAllBlocks plugin;

    /** entity type → list of target materials (immutable after load). */
    private final Map<EntityType, List<Material>> mappings = new EnumMap<>(EntityType.class);

    public EntityMappingManager(BreakAllBlocks plugin) {
        this.plugin = plugin;
    }

    // ── Loading ────────────────────────────────────────────────────────────

    public void load() {
        mappings.clear();

        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection("entity_block_mappings");
        if (section == null) {
            plugin.getLogger().info("[BAB] No entity_block_mappings section found in config.yml.");
            return;
        }

        int totalMappings = 0;
        for (String key : section.getKeys(false)) {
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(key.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[BAB] Unknown entity type in mappings: " + key);
                continue;
            }

            List<String> rawMaterials = section.getStringList(key);
            List<Material> materials  = new ArrayList<>();
            for (String raw : rawMaterials) {
                try {
                    materials.add(Material.valueOf(raw.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[BAB] Unknown material '" + raw
                            + "' in mapping for entity: " + key);
                }
            }

            if (!materials.isEmpty()) {
                mappings.put(entityType, Collections.unmodifiableList(materials));
                totalMappings++;
            }
        }

        plugin.getLogger().info("[BAB] Loaded " + totalMappings
                + " entity-block mapping(s).");
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    /**
     * Returns the list of block materials mapped to the given entity type,
     * or an empty list if no mapping exists.
     */
    public List<Material> getMappings(EntityType entityType) {
        return mappings.getOrDefault(entityType, Collections.emptyList());
    }

    /** True if the entity type has at least one mapping. */
    public boolean hasMappings(EntityType entityType) {
        return mappings.containsKey(entityType);
    }

    public Map<EntityType, List<Material>> getAllMappings() {
        return Collections.unmodifiableMap(mappings);
    }
}
