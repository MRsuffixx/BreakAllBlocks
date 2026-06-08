package com.mrsuffix.breakallblocks.managers;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages the persistent list of eliminated block materials.
 *
 * <p>Data is stored in {@code eliminated_blocks.yml} in the plugin's data folder.
 * Each entry now records who triggered the elimination, when, and via what mechanism.</p>
 *
 * <h2>File format</h2>
 * <pre>
 * eliminated:
 *   OAK_LOG:
 *     by: Steve
 *     when: 1718000000000
 *     type: BLOCK_BREAK
 *   IRON_ORE:
 *     by: Alex
 *     when: 1718000100000
 *     type: MOB_KILL
 * </pre>
 *
 * @author MRsuffix
 */
public class EliminationManager {

    private static final String FILE_NAME = "eliminated_blocks.yml";

    private final BreakAllBlocks plugin;
    private final ConfigManager  configManager;

    private File              dataFile;
    private FileConfiguration dataConfig;

    /** Thread-safe set of eliminated materials. */
    private final Set<Material>                    eliminatedMaterials = Collections.synchronizedSet(EnumSet.noneOf(Material.class));
    /** Metadata per eliminated material: who eliminated it and when. */
    private final Map<Material, EliminationRecord> eliminationRecords  = Collections.synchronizedMap(new EnumMap<>(Material.class));

    // ── Inner record ───────────────────────────────────────────────────────

    public static class EliminationRecord {
        public final String triggeredBy;
        public final long   timestamp;
        public final String triggerType; // "BLOCK_BREAK" | "MOB_KILL" | "STARTUP_SCAN"

        public EliminationRecord(String triggeredBy, long timestamp, String triggerType) {
            this.triggeredBy = triggeredBy;
            this.timestamp   = timestamp;
            this.triggerType = triggerType;
        }

        public String getAgeString() {
            long s = (System.currentTimeMillis() - timestamp) / 1_000;
            if (s < 60)    return s + "s ago";
            if (s < 3600)  return (s / 60) + "min ago";
            if (s < 86400) return (s / 3600) + "h ago";
            return (s / 86400) + "d ago";
        }
    }

    // ── Constructor ────────────────────────────────────────────────────────

    public EliminationManager(BreakAllBlocks plugin, ConfigManager configManager) {
        this.plugin         = plugin;
        this.configManager  = configManager;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    public void load() {
        dataFile = new File(plugin.getDataFolder(), FILE_NAME);
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create " + FILE_NAME, e);
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        eliminatedMaterials.clear();
        eliminationRecords.clear();

        // Support new map format AND old plain-list format.
        if (dataConfig.isConfigurationSection("eliminated")) {
            var section = dataConfig.getConfigurationSection("eliminated");
            assert section != null;
            for (String key : section.getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    eliminatedMaterials.add(mat);
                    String by   = section.getString(key + ".by",   "Unknown");
                    long   when = section.getLong(key + ".when",   0L);
                    String type = section.getString(key + ".type", "UNKNOWN");
                    eliminationRecords.put(mat, new EliminationRecord(by, when, type));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("[BAB] Unknown material in " + FILE_NAME + ": " + key);
                }
            }
        } else {
            // Legacy plain-list format
            for (String name : dataConfig.getStringList("eliminated")) {
                try {
                    Material mat = Material.valueOf(name.toUpperCase());
                    eliminatedMaterials.add(mat);
                    eliminationRecords.put(mat, new EliminationRecord("Unknown", 0L, "UNKNOWN"));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("[BAB] Unknown material in " + FILE_NAME + ": " + name);
                }
            }
        }

        plugin.getLogger().info("[BAB] Loaded " + eliminatedMaterials.size()
                + " eliminated material(s).");
    }

    public void save() {
        if (dataConfig == null) return;

        // Clear and rewrite
        dataConfig.set("eliminated", null);
        synchronized (eliminatedMaterials) {
            for (Material mat : eliminatedMaterials) {
                EliminationRecord rec = eliminationRecords.get(mat);
                String prefix = "eliminated." + mat.name();
                dataConfig.set(prefix + ".by",   rec != null ? rec.triggeredBy : "Unknown");
                dataConfig.set(prefix + ".when",  rec != null ? rec.timestamp  : 0L);
                dataConfig.set(prefix + ".type",  rec != null ? rec.triggerType : "UNKNOWN");
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + FILE_NAME, e);
        }
    }

    // ── State queries ──────────────────────────────────────────────────────

    public boolean isEliminated(Material material) {
        return eliminatedMaterials.contains(material);
    }

    /** Unmodifiable snapshot of eliminated materials. */
    public Set<Material> getEliminatedMaterials() {
        synchronized (eliminatedMaterials) {
            return Collections.unmodifiableSet(
                    eliminatedMaterials.isEmpty()
                            ? EnumSet.noneOf(Material.class)
                            : EnumSet.copyOf(eliminatedMaterials));
        }
    }

    /** Returns the record for the given material, or null if not found. */
    public EliminationRecord getRecord(Material material) {
        return eliminationRecords.get(material);
    }

    /**
     * Returns eliminated materials sorted newest-first (for /bab list).
     */
    public List<Map.Entry<Material, EliminationRecord>> getSortedEliminated() {
        List<Map.Entry<Material, EliminationRecord>> result;
        synchronized (eliminationRecords) {
            result = new ArrayList<>(eliminationRecords.entrySet());
        }
        result.sort((a, b) -> Long.compare(
                b.getValue() != null ? b.getValue().timestamp : 0L,
                a.getValue() != null ? a.getValue().timestamp : 0L));
        return result;
    }

    public int getCount() {
        return eliminatedMaterials.size();
    }

    // ── Mutations ──────────────────────────────────────────────────────────

    /**
     * Marks a material as eliminated with metadata.
     *
     * @return {@code true} if newly eliminated; {@code false} if already in list.
     */
    public boolean eliminate(Material material, String triggeredBy, String triggerType) {
        boolean added = eliminatedMaterials.add(material);
        if (added) {
            eliminationRecords.put(material,
                    new EliminationRecord(triggeredBy, System.currentTimeMillis(), triggerType));
            save();
        }
        return added;
    }

    /** Convenience overload for backward compatibility. */
    public boolean eliminate(Material material) {
        return eliminate(material, "Unknown", "UNKNOWN");
    }

    /**
     * Removes a material from the eliminated list.
     *
     * @return {@code true} if it was previously eliminated.
     */
    public boolean restore(Material material) {
        boolean removed = eliminatedMaterials.remove(material);
        if (removed) {
            eliminationRecords.remove(material);
            save();
        }
        return removed;
    }

    /** Clears ALL eliminated materials. */
    public void resetAll() {
        eliminatedMaterials.clear();
        eliminationRecords.clear();
        save();
    }
}
