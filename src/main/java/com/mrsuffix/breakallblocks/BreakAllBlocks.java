package com.mrsuffix.breakallblocks;

import com.mrsuffix.breakallblocks.commands.BabCommandHandler;
import com.mrsuffix.breakallblocks.listeners.BlockBreakListener;
import com.mrsuffix.breakallblocks.listeners.ChunkLoadListener;
import com.mrsuffix.breakallblocks.managers.ConfigManager;
import com.mrsuffix.breakallblocks.managers.EliminationManager;
import com.mrsuffix.breakallblocks.managers.WorldScanner;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BreakAllBlocks — Main plugin class.
 *
 * <p>When a player breaks any block, every block of that same type across
 * the entire world is instantly removed, permanently and irreversibly.</p>
 *
 * @author MRsuffix
 * @version 1.0.0
 */
public final class BreakAllBlocks extends JavaPlugin {

    private ConfigManager configManager;
    private EliminationManager eliminationManager;
    private WorldScanner worldScanner;

    @Override
    public void onEnable() {
        getLogger().info("==============================================");
        getLogger().info(" BreakAllBlocks v" + getPluginMeta().getVersion());
        getLogger().info(" Author: MRsuffix");
        getLogger().info("==============================================");

        // 1. Boot config + data managers
        this.configManager = new ConfigManager(this);
        configManager.loadConfig();

        this.eliminationManager = new EliminationManager(this, configManager);
        eliminationManager.load();

        this.worldScanner = new WorldScanner(this, configManager, eliminationManager);

        // 2. Register event listeners
        getServer().getPluginManager().registerEvents(
                new BlockBreakListener(this, configManager, eliminationManager, worldScanner), this);
        getServer().getPluginManager().registerEvents(
                new ChunkLoadListener(this, configManager, eliminationManager, worldScanner), this);

        // 3. Register commands
        BabCommandHandler commandHandler = new BabCommandHandler(this, configManager, eliminationManager, worldScanner);
        var cmd = getCommand("bab");
        if (cmd != null) {
            cmd.setExecutor(commandHandler);
            cmd.setTabCompleter(commandHandler);
        } else {
            getLogger().severe("Failed to register /bab command! Check plugin.yml.");
        }

        // 4. Schedule startup re-scan for all eliminated materials
        //    (catches any blocks that regenerated while server was off)
        if (configManager.isEnabled() && !eliminationManager.getEliminatedMaterials().isEmpty()) {
            getLogger().info("Scheduling startup re-scan for " +
                    eliminationManager.getEliminatedMaterials().size() + " eliminated material(s)...");
            Bukkit.getScheduler().runTaskLater(this, () ->
                    worldScanner.scanAllWorldsForEliminated(null), 60L); // wait 3s after start
        }

        getLogger().info("Plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Cancel any running scanner tasks
        if (worldScanner != null) {
            worldScanner.cancelAll();
        }

        // Persist elimination data
        if (eliminationManager != null) {
            eliminationManager.save();
        }

        getLogger().info("BreakAllBlocks disabled. All data saved.");
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EliminationManager getEliminationManager() {
        return eliminationManager;
    }

    public WorldScanner getWorldScanner() {
        return worldScanner;
    }
}
