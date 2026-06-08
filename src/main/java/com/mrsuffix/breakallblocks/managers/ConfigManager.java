package com.mrsuffix.breakallblocks.managers;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Manages plugin configuration ({@code config.yml}) and provides typed accessors
 * for every setting used across the plugin.
 */
public class ConfigManager {

    private final BreakAllBlocks plugin;
    private FileConfiguration config;

    // ── Cached values ──────────────────────────────────────────────────────

    // Core toggles
    private boolean enabled;
    private boolean broadcastMessages;
    private boolean countIndirectBreaks;
    private boolean countCreativeBreaks;

    // Wave system
    private int     waveSpeed;
    private int     waveTickInterval;
    private int     maxActiveWaves;
    private boolean dropItems;

    // World / material filters
    private Set<String>   activeWorlds;
    private Set<Material> excludedMaterials;

    // Messages
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
    private String msgWaveActive;

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
        enabled             = config.getBoolean("enabled", true);
        broadcastMessages   = config.getBoolean("broadcast_messages", true);
        countIndirectBreaks = config.getBoolean("count_indirect_breaks", false);
        countCreativeBreaks = config.getBoolean("count_creative_breaks", false);

        // Wave system
        waveSpeed        = Math.max(1, config.getInt("wave_speed", 10));
        waveTickInterval = Math.max(1, config.getInt("wave_tick_interval", 1));
        maxActiveWaves   = Math.max(1, config.getInt("max_active_waves", 3));
        dropItems        = config.getBoolean("drop_items", true);

        // Active worlds
        List<String> worldList = config.getStringList("worlds");
        activeWorlds = worldList.isEmpty()
                ? Collections.emptySet()
                : Set.copyOf(worldList);

        // Excluded materials
        excludedMaterials = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("excluded_materials")) {
            try {
                excludedMaterials.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[BAB] Unknown excluded_material: " + name);
            }
        }

        // Messages
        msgPrefix               = config.getString("messages.prefix",                "{prefix}[BAB] ");
        msgEliminationBroadcast = config.getString("messages.elimination_broadcast", "");
        msgAlreadyEliminated    = config.getString("messages.already_eliminated",    "");
        msgExcludedMaterial     = config.getString("messages.excluded_material",     "");
        msgPluginDisabled       = config.getString("messages.plugin_disabled",       "");
        msgWorldNotActive       = config.getString("messages.world_not_active",      "");
        msgReloadSuccess        = config.getString("messages.reload_success",        "");
        msgRestoreSuccess       = config.getString("messages.restore_success",       "");
        msgRestoreNotFound      = config.getString("messages.restore_not_found",     "");
        msgResetAllSuccess      = config.getString("messages.resetall_success",      "");
        msgListHeader           = config.getString("messages.list_header",           "");
        msgListEmpty            = config.getString("messages.list_empty",            "");
        msgListEntry            = config.getString("messages.list_entry",            "");
        msgNoPermission         = config.getString("messages.no_permission",         "");
        msgUsage                = config.getString("messages.usage",                 "");
        msgInvalidMaterial      = config.getString("messages.invalid_material",      "");
        msgScanStarted          = config.getString("messages.scan_started",          "");
        msgScanComplete         = config.getString("messages.scan_complete",         "");
        msgWaveActive           = config.getString("messages.wave_active",           "");
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public boolean isEnabled()              { return enabled; }
    public boolean isBroadcastMessages()    { return broadcastMessages; }
    public boolean isCountIndirectBreaks()  { return countIndirectBreaks; }
    public boolean isCountCreativeBreaks()  { return countCreativeBreaks; }

    // Wave system
    public int     getWaveSpeed()           { return waveSpeed; }
    public int     getWaveTickInterval()    { return waveTickInterval; }
    public int     getMaxActiveWaves()      { return maxActiveWaves; }
    public boolean isDropItems()            { return dropItems; }

    // Filters
    public Set<String>   getActiveWorlds()       { return activeWorlds; }
    public Set<Material> getExcludedMaterials()  { return excludedMaterials; }

    public boolean isWorldActive(String worldName) {
        return activeWorlds.isEmpty() || activeWorlds.contains(worldName);
    }

    public boolean isExcluded(Material material) {
        return excludedMaterials.contains(material);
    }

    // Messages
    public String getPrefix()                   { return msgPrefix; }
    public String getMsgEliminationBroadcast()  { return msgEliminationBroadcast; }
    public String getMsgAlreadyEliminated()     { return msgAlreadyEliminated; }
    public String getMsgExcludedMaterial()      { return msgExcludedMaterial; }
    public String getMsgPluginDisabled()        { return msgPluginDisabled; }
    public String getMsgWorldNotActive()        { return msgWorldNotActive; }
    public String getMsgReloadSuccess()         { return msgReloadSuccess; }
    public String getMsgRestoreSuccess()        { return msgRestoreSuccess; }
    public String getMsgRestoreNotFound()       { return msgRestoreNotFound; }
    public String getMsgResetAllSuccess()       { return msgResetAllSuccess; }
    public String getMsgListHeader()            { return msgListHeader; }
    public String getMsgListEmpty()             { return msgListEmpty; }
    public String getMsgListEntry()             { return msgListEntry; }
    public String getMsgNoPermission()          { return msgNoPermission; }
    public String getMsgUsage()                 { return msgUsage; }
    public String getMsgInvalidMaterial()       { return msgInvalidMaterial; }
    public String getMsgScanStarted()           { return msgScanStarted; }
    public String getMsgScanComplete()          { return msgScanComplete; }
    public String getMsgWaveActive()            { return msgWaveActive; }
}
