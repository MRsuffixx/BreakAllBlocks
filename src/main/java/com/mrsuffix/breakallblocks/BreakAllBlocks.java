package com.mrsuffix.breakallblocks;

import com.mrsuffix.breakallblocks.commands.BabCommandHandler;
import com.mrsuffix.breakallblocks.listeners.BlockBreakListener;
import com.mrsuffix.breakallblocks.listeners.BlockDropItemListener;
import com.mrsuffix.breakallblocks.listeners.ChunkLoadListener;
import com.mrsuffix.breakallblocks.listeners.EntityDeathListener;
import com.mrsuffix.breakallblocks.managers.ConfigManager;
import com.mrsuffix.breakallblocks.managers.EliminationManager;
import com.mrsuffix.breakallblocks.managers.EntityMappingManager;
import com.mrsuffix.breakallblocks.managers.UndoManager;
import com.mrsuffix.breakallblocks.managers.WaveManager;
import com.mrsuffix.breakallblocks.managers.WorldScanner;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BreakAllBlocks — Main plugin class.
 *
 * <p>Breaking a block (or killing a mapped mob) triggers a BFS wave that
 * spreads outward, eliminating every connected block of that type, permanently.</p>
 *
 * @author  MRsuffix
 * @version 1.1.0
 */
public final class BreakAllBlocks extends JavaPlugin {

    private ConfigManager       configManager;
    private EliminationManager  eliminationManager;
    private EntityMappingManager entityMappingManager;
    private WaveManager         waveManager;
    private UndoManager         undoManager;
    private WorldScanner        worldScanner;

    @Override
    public void onEnable() {
        getLogger().info("==============================================");
        getLogger().info(" BreakAllBlocks v" + getPluginMeta().getVersion());
        getLogger().info(" Author: MRsuffix  |  Wave-propagation mode");
        getLogger().info("==============================================");

        // 1. Config + persistent data
        this.configManager = new ConfigManager(this);
        configManager.loadConfig();

        this.eliminationManager = new EliminationManager(this, configManager);
        eliminationManager.load();

        // 2. Entity mapping (mob kill → blocks)
        this.entityMappingManager = new EntityMappingManager(this);
        entityMappingManager.load();

        // 3. Wave engine (UndoManager set after creation to break circular dep)
        this.waveManager = new WaveManager(this, configManager, eliminationManager);

        // 4. Undo system
        this.undoManager = new UndoManager(this, configManager, eliminationManager);
        waveManager.setUndoManager(undoManager);

        // 5. Startup scanner
        this.worldScanner = new WorldScanner(this, configManager, eliminationManager, waveManager);

        // 6. Register listeners
        var pm = getServer().getPluginManager();
        pm.registerEvents(new BlockBreakListener(this, configManager, eliminationManager, waveManager), this);
        pm.registerEvents(new ChunkLoadListener(this, configManager, eliminationManager, waveManager), this);
        pm.registerEvents(new EntityDeathListener(this, configManager, eliminationManager, entityMappingManager, waveManager), this);
        pm.registerEvents(new BlockDropItemListener(waveManager), this);

        // 7. Register /bab command
        var cmdHandler = new BabCommandHandler(this, configManager, eliminationManager,
                waveManager, undoManager, worldScanner);
        var cmd = getCommand("bab");
        if (cmd != null) {
            cmd.setExecutor(cmdHandler);
            cmd.setTabCompleter(cmdHandler);
        } else {
            getLogger().severe("Failed to register /bab command — check plugin.yml!");
        }

        // 8. Startup cleanup scan (after 3 s / 60 ticks)
        if (configManager.isEnabled() && !eliminationManager.getEliminatedMaterials().isEmpty()) {
            getLogger().info("[BAB] Scheduling startup cleanup scan for "
                    + eliminationManager.getEliminatedMaterials().size()
                    + " eliminated material(s)…");
            Bukkit.getScheduler().runTaskLater(this,
                    () -> worldScanner.scanAllWorldsForEliminated(null), 60L);
        }

        getLogger().info("[BAB] Plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (waveManager  != null) waveManager.cancelAll();
        if (worldScanner != null) worldScanner.cancelAll();
        if (eliminationManager != null) eliminationManager.save();
        getLogger().info("[BAB] Disabled. All data saved.");
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public ConfigManager       getConfigManager()       { return configManager; }
    public EliminationManager  getEliminationManager()  { return eliminationManager; }
    public EntityMappingManager getEntityMappingManager(){ return entityMappingManager; }
    public WaveManager         getWaveManager()         { return waveManager; }
    public UndoManager         getUndoManager()         { return undoManager; }
    public WorldScanner        getWorldScanner()        { return worldScanner; }
}
