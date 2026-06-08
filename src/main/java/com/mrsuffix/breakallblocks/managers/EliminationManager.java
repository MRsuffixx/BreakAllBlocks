package com.mrsuffix.breakallblocks.managers;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Manages the persistent list of eliminated materials.
 *
 * <p>Data is stored in {@code eliminated_blocks.yml} inside the plugin's
 * data folder and is loaded on enable / saved on disable.</p>
 */
public class EliminationManager {

    private static final String FILE_NAME = "eliminated_blocks.yml";

    private final BreakAllBlocks plugin;
    private final ConfigManager configManager;

    private File dataFile;
    private FileConfiguration dataConfig;

    /** Thread-safe set of materials that have been permanently eliminated. */
    private final Set<Material> eliminatedMaterials = Collections.synchronizedSet(EnumSet.noneOf(Material.class));

    public EliminationManager(BreakAllBlocks plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
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

        List<String> names = dataConfig.getStringList("eliminated");
        for (String name : names) {
            try {
                eliminatedMaterials.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown material in " + FILE_NAME + ": " + name);
            }
        }
        plugin.getLogger().info("Loaded " + eliminatedMaterials.size() + " eliminated material(s).");
    }

    public void save() {
        if (dataConfig == null) return;
        List<String> names = new ArrayList<>();
        synchronized (eliminatedMaterials) {
            for (Material m : eliminatedMaterials) {
                names.add(m.name());
            }
        }
        dataConfig.set("eliminated", names);
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

    /** Returns an unmodifiable snapshot of the current eliminated set. */
    public Set<Material> getEliminatedMaterials() {
        synchronized (eliminatedMaterials) {
            return Collections.unmodifiableSet(EnumSet.copyOf(
                    eliminatedMaterials.isEmpty() ? EnumSet.noneOf(Material.class) : eliminatedMaterials));
        }
    }

    // ── Mutations ──────────────────────────────────────────────────────────

    /**
     * Marks the material as eliminated and persists the change immediately.
     *
     * @return {@code true} if this was a new elimination; {@code false} if already eliminated.
     */
    public boolean eliminate(Material material) {
        boolean added = eliminatedMaterials.add(material);
        if (added) save();
        return added;
    }

    /**
     * Removes the material from the eliminated list so it may be broken again.
     * Does NOT regenerate any blocks — just allows the type to exist again.
     *
     * @return {@code true} if the material was previously eliminated.
     */
    public boolean restore(Material material) {
        boolean removed = eliminatedMaterials.remove(material);
        if (removed) save();
        return removed;
    }

    /** Clears ALL eliminated materials and saves. */
    public void resetAll() {
        eliminatedMaterials.clear();
        save();
    }

    public int getCount() {
        return eliminatedMaterials.size();
    }
}
