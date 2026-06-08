package com.mrsuffix.breakallblocks;

import com.mrsuffix.breakallblocks.commands.BabCommandHandler;
import com.mrsuffix.breakallblocks.listeners.BlockBreakListener;
import com.mrsuffix.breakallblocks.listeners.ChunkLoadListener;
import com.mrsuffix.breakallblocks.managers.ConfigManager;
import com.mrsuffix.breakallblocks.managers.EliminationManager;
import com.mrsuffix.breakallblocks.managers.WaveManager;
import com.mrsuffix.breakallblocks.managers.WorldScanner;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BreakAllBlocks — Main plugin class.
 *
 * <p>When a player breaks any block, a BFS wave propagates outward from that
 * location, breaking every connected block of the same type one by one in a
 * cinematic cascade. The material is then permanently eliminated.</p>
 *
 * @author  MRsuffix
 * @version 1.0.0
 */
public final class BreakAllBlocks extends JavaPlugin {

    private ConfigManager      configManager;
    private EliminationManager eliminationManager;
    private WaveManager        waveManager;
    private WorldScanner       worldScanner;

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

        // 2. Wave engine
        this.waveManager = new WaveManager(this, configManager, eliminationManager);

        // 3. Startup scanner (finds leftover eliminated blocks and starts cleanup waves)
        this.worldScanner = new WorldScanner(this, configManager, eliminationManager, waveManager);

        // 4. Register event listeners
        getServer().getPluginManager().registerEvents(
                new BlockBreakListener(this, configManager, eliminationManager, waveManager), this);
        getServer().getPluginManager().registerEvents(
                new ChunkLoadListener(this, configManager, eliminationManager, waveManager), this);

        // 5. Register /bab command
        BabCommandHandler cmdHandler =
                new BabCommandHandler(this, configManager, eliminationManager, waveManager, worldScanner);
        var cmd = getCommand("bab");
        if (cmd != null) {
            cmd.setExecutor(cmdHandler);
            cmd.setTabCompleter(cmdHandler);
        } else {
            getLogger().severe("Failed to register /bab command — check plugin.yml!");
        }

        // 6. Schedule startup re-scan (60 ticks = 3 s after enable, so the world
        //    finishes loading before we scan chunks).
        if (configManager.isEnabled() && !eliminationManager.getEliminatedMaterials().isEmpty()) {
            getLogger().info("Scheduling startup cleanup scan for "
                    + eliminationManager.getEliminatedMaterials().size()
                    + " eliminated material(s)…");
            Bukkit.getScheduler().runTaskLater(this,
                    () -> worldScanner.scanAllWorldsForEliminated(null), 60L);
        }

        getLogger().info("Plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (waveManager  != null) waveManager.cancelAll();
        if (worldScanner != null) worldScanner.cancelAll();
        if (eliminationManager != null) eliminationManager.save();
        getLogger().info("BreakAllBlocks disabled. All data saved.");
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public ConfigManager      getConfigManager()      { return configManager; }
    public EliminationManager getEliminationManager() { return eliminationManager; }
    public WaveManager        getWaveManager()        { return waveManager; }
    public WorldScanner       getWorldScanner()       { return worldScanner; }
}
