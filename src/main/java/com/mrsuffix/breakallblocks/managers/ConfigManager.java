package com.mrsuffix.breakallblocks.managers;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Manages plugin configuration (config.yml) and provides typed accessors
 * for every setting used across the plugin.
 */
public class ConfigManager {

    private final BreakAllBlocks plugin;
    private FileConfiguration config;

    // ── Cached values ──────────────────────────────────────────────────────
    private boolean enabled;
    private int batchSize;
    private int batchDelayTicks;
    private boolean broadcastMessages;
    private boolean countIndirectBreaks;
    private boolean countCreativeBreaks;
    private Set<String> activeWorlds;
    private Set<Material> excludedMaterials;

    // Message cache
    private String msgPrefix;
    private String msgEliminationBroadcast;
    private String msgAlreadyEliminated;
    private String msgExcludedMaterial;
    private String msgPluginDisabled;
    private String msgWorldNotActive;
    private String msgReloadSuccess;
    private String msgRestoreSuccess;
    private String msgRestoreNotFound;
    private String msgResetAllSuccess;
    private String msgListHeader;
    private String msgListEmpty;
    private String msgListEntry;
    private String msgNoPermission;
    private String msgUsage;
    private String msgInvalidMaterial;
    private String msgScanStarted;
    private String msgScanComplete;

    public ConfigManager(BreakAllBlocks plugin) {
        this.plugin = plugin;
    }

    // ── Loading ────────────────────────────────────────────────────────────

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        cacheValues();
    }

    private void cacheValues() {
        enabled = config.getBoolean("enabled", true);
        batchSize = Math.max(1, config.getInt("batch_size", 500));
        batchDelayTicks = Math.max(1, config.getInt("batch_delay_ticks", 1));
        broadcastMessages = config.getBoolean("broadcast_messages", true);
        countIndirectBreaks = config.getBoolean("count_indirect_breaks", false);
        countCreativeBreaks = config.getBoolean("count_creative_breaks", false);

        // Active worlds
        List<String> worldList = config.getStringList("worlds");
        activeWorlds = worldList.isEmpty()
                ? Collections.emptySet()
                : Set.copyOf(worldList);

        // Excluded materials
        excludedMaterials = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("excluded_materials")) {
            try {
                Material mat = Material.valueOf(name.toUpperCase());
                excludedMaterials.add(mat);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown excluded_material: " + name);
            }
        }

        // Messages
        msgPrefix              = config.getString("messages.prefix", "[BAB] ");
        msgEliminationBroadcast = config.getString("messages.elimination_broadcast", "");
        msgAlreadyEliminated   = config.getString("messages.already_eliminated", "");
        msgExcludedMaterial    = config.getString("messages.excluded_material", "");
        msgPluginDisabled      = config.getString("messages.plugin_disabled", "");
        msgWorldNotActive      = config.getString("messages.world_not_active", "");
        msgReloadSuccess       = config.getString("messages.reload_success", "");
        msgRestoreSuccess      = config.getString("messages.restore_success", "");
        msgRestoreNotFound     = config.getString("messages.restore_not_found", "");
        msgResetAllSuccess     = config.getString("messages.resetall_success", "");
        msgListHeader          = config.getString("messages.list_header", "");
        msgListEmpty           = config.getString("messages.list_empty", "");
        msgListEntry           = config.getString("messages.list_entry", "");
        msgNoPermission        = config.getString("messages.no_permission", "");
        msgUsage               = config.getString("messages.usage", "");
        msgInvalidMaterial     = config.getString("messages.invalid_material", "");
        msgScanStarted         = config.getString("messages.scan_started", "");
        msgScanComplete        = config.getString("messages.scan_complete", "");
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public boolean isEnabled()            { return enabled; }
    public int getBatchSize()             { return batchSize; }
    public int getBatchDelayTicks()       { return batchDelayTicks; }
    public boolean isBroadcastMessages()  { return broadcastMessages; }
    public boolean isCountIndirectBreaks(){ return countIndirectBreaks; }
    public boolean isCountCreativeBreaks(){ return countCreativeBreaks; }
    public Set<String> getActiveWorlds()  { return activeWorlds; }
    public Set<Material> getExcludedMaterials() { return excludedMaterials; }

    /** Returns true if the plugin should operate in the given world name. */
    public boolean isWorldActive(String worldName) {
        return activeWorlds.isEmpty() || activeWorlds.contains(worldName);
    }

    /** Returns true if the material is on the exclusion list. */
    public boolean isExcluded(Material material) {
        return excludedMaterials.contains(material);
    }

    // ── Message accessors ──────────────────────────────────────────────────

    public String getPrefix()                  { return msgPrefix; }
    public String getMsgEliminationBroadcast() { return msgEliminationBroadcast; }
    public String getMsgAlreadyEliminated()    { return msgAlreadyEliminated; }
    public String getMsgExcludedMaterial()     { return msgExcludedMaterial; }
    public String getMsgPluginDisabled()       { return msgPluginDisabled; }
    public String getMsgWorldNotActive()       { return msgWorldNotActive; }
    public String getMsgReloadSuccess()        { return msgReloadSuccess; }
    public String getMsgRestoreSuccess()       { return msgRestoreSuccess; }
    public String getMsgRestoreNotFound()      { return msgRestoreNotFound; }
    public String getMsgResetAllSuccess()      { return msgResetAllSuccess; }
    public String getMsgListHeader()           { return msgListHeader; }
    public String getMsgListEmpty()            { return msgListEmpty; }
    public String getMsgListEntry()            { return msgListEntry; }
    public String getMsgNoPermission()         { return msgNoPermission; }
    public String getMsgUsage()                { return msgUsage; }
    public String getMsgInvalidMaterial()      { return msgInvalidMaterial; }
    public String getMsgScanStarted()          { return msgScanStarted; }
    public String getMsgScanComplete()         { return msgScanComplete; }
}
