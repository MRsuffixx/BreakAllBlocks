package com.mrsuffix.breakallblocks.managers;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Manages {@code config.yml} and exposes typed accessors for every setting.
 *
 * <p>Also manages the <em>runtime exclusion list</em> — materials added via
 * {@code /bab exclude} at runtime without editing the config file.
 * Runtime exclusions are merged with config-file exclusions and survive
 * {@code /bab reload} (they are stored in a separate in-memory set).</p>
 *
 * @author MRsuffix
 */
public class ConfigManager {

    private final BreakAllBlocks plugin;
    private FileConfiguration config;

    // ── Cached values ──────────────────────────────────────────────────────

    private boolean enabled;
    private boolean broadcastMessages;
    private boolean countIndirectBreaks;
    private boolean countCreativeBreaks;

    // Wave
    private int     waveSpeed;
    private int     waveTickInterval;
    private int     maxActiveWaves;
    private boolean dropItems;

    // Undo
    private int     undoHistorySize;
    private int     undoMaxBlocks;

    // Filters
    private Set<String>   activeWorlds;
    private Set<Material> configExcludedMaterials;  // from config.yml
    private Set<Material> runtimeExcludedMaterials = EnumSet.noneOf(Material.class); // runtime-only

    // Messages
    private String msgPrefix;
    private String msgEliminationBroadcast;
    private String msgMobKillBroadcast;
    private String msgAlreadyEliminated;
    private String msgExcludedMaterial;
    private String msgPluginDisabled;
    private String msgWorldNotActive;
    private String msgWaveActive;
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
    private String msgUndoBroadcast;
    private String msgExcludeSuccess;
    private String msgIncludeSuccess;
    private String msgMaterialNotExcluded;

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

        waveSpeed        = Math.max(1, config.getInt("wave_speed", 10));
        waveTickInterval = Math.max(1, config.getInt("wave_tick_interval", 1));
        maxActiveWaves   = Math.max(1, config.getInt("max_active_waves", 3));
        dropItems        = config.getBoolean("drop_items", true);

        undoHistorySize  = Math.max(1, config.getInt("undo_history_size", 10));
        undoMaxBlocks    = config.getInt("undo_max_blocks", 100_000); // 0 = unlimited

        List<String> worldList = config.getStringList("worlds");
        activeWorlds = worldList.isEmpty() ? Collections.emptySet() : Set.copyOf(worldList);

        configExcludedMaterials = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("excluded_materials")) {
            try { configExcludedMaterials.add(Material.valueOf(name.toUpperCase())); }
            catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[BAB] Unknown excluded_material: " + name);
            }
        }

        // Messages
        msgPrefix                = config.getString("messages.prefix",                "");
        msgEliminationBroadcast  = config.getString("messages.elimination_broadcast", "");
        msgMobKillBroadcast      = config.getString("messages.mob_kill_broadcast",    "");
        msgAlreadyEliminated     = config.getString("messages.already_eliminated",    "");
        msgExcludedMaterial      = config.getString("messages.excluded_material",     "");
        msgPluginDisabled        = config.getString("messages.plugin_disabled",       "");
        msgWorldNotActive        = config.getString("messages.world_not_active",      "");
        msgWaveActive            = config.getString("messages.wave_active",           "");
        msgReloadSuccess         = config.getString("messages.reload_success",        "");
        msgRestoreSuccess        = config.getString("messages.restore_success",       "");
        msgRestoreNotFound       = config.getString("messages.restore_not_found",     "");
        msgResetAllSuccess       = config.getString("messages.resetall_success",      "");
        msgListHeader            = config.getString("messages.list_header",           "");
        msgListEmpty             = config.getString("messages.list_empty",            "");
        msgListEntry             = config.getString("messages.list_entry",            "");
        msgNoPermission          = config.getString("messages.no_permission",         "");
        msgUsage                 = config.getString("messages.usage",                 "");
        msgInvalidMaterial       = config.getString("messages.invalid_material",      "");
        msgScanStarted           = config.getString("messages.scan_started",          "");
        msgScanComplete          = config.getString("messages.scan_complete",         "");
        msgUndoBroadcast         = config.getString("messages.undo_broadcast",        "");
        msgExcludeSuccess        = config.getString("messages.exclude_success",       "");
        msgIncludeSuccess        = config.getString("messages.include_success",       "");
        msgMaterialNotExcluded   = config.getString("messages.material_not_excluded", "");
    }

    // ── Runtime exclusion management ───────────────────────────────────────

    /** Adds a material to the runtime exclusion list (survives reloads). */
    public void addRuntimeExclusion(Material material) {
        runtimeExcludedMaterials.add(material);
    }

    /** Removes a material from the runtime exclusion list. */
    public boolean removeRuntimeExclusion(Material material) {
        return runtimeExcludedMaterials.remove(material);
    }

    /** Returns the live (config + runtime) set of excluded materials. */
    public Set<Material> getExcludedMaterials() {
        Set<Material> combined = EnumSet.noneOf(Material.class);
        combined.addAll(configExcludedMaterials);
        combined.addAll(runtimeExcludedMaterials);
        return Collections.unmodifiableSet(combined);
    }

    /** Returns only the runtime exclusions (for /bab include tab-complete). */
    public Set<Material> getRuntimeExclusions() {
        return Collections.unmodifiableSet(runtimeExcludedMaterials);
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public boolean isEnabled()             { return enabled; }
    public boolean isBroadcastMessages()   { return broadcastMessages; }
    public boolean isCountIndirectBreaks() { return countIndirectBreaks; }
    public boolean isCountCreativeBreaks() { return countCreativeBreaks; }
    public int     getWaveSpeed()          { return waveSpeed; }
    public int     getWaveTickInterval()   { return waveTickInterval; }
    public int     getMaxActiveWaves()     { return maxActiveWaves; }
    public boolean isDropItems()           { return dropItems; }
    public int     getUndoHistorySize()    { return undoHistorySize; }
    public int     getUndoMaxBlocks()      { return undoMaxBlocks; }
    public Set<String> getActiveWorlds()   { return activeWorlds; }

    public boolean isWorldActive(String name) {
        return activeWorlds.isEmpty() || activeWorlds.contains(name);
    }
    public boolean isExcluded(Material m) {
        return configExcludedMaterials.contains(m) || runtimeExcludedMaterials.contains(m);
    }

    // Messages
    public String getPrefix()                  { return msgPrefix; }
    public String getMsgEliminationBroadcast() { return msgEliminationBroadcast; }
    public String getMsgMobKillBroadcast()     { return msgMobKillBroadcast; }
    public String getMsgAlreadyEliminated()    { return msgAlreadyEliminated; }
    public String getMsgExcludedMaterial()     { return msgExcludedMaterial; }
    public String getMsgPluginDisabled()       { return msgPluginDisabled; }
    public String getMsgWorldNotActive()       { return msgWorldNotActive; }
    public String getMsgWaveActive()           { return msgWaveActive; }
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
    public String getMsgUndoBroadcast()        { return msgUndoBroadcast; }
    public String getMsgExcludeSuccess()       { return msgExcludeSuccess; }
    public String getMsgIncludeSuccess()       { return msgIncludeSuccess; }
    public String getMsgMaterialNotExcluded()  { return msgMaterialNotExcluded; }
}
