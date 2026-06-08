package com.mrsuffix.breakallblocks.commands;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.managers.ConfigManager;
import com.mrsuffix.breakallblocks.managers.EliminationManager;
import com.mrsuffix.breakallblocks.managers.WaveManager;
import com.mrsuffix.breakallblocks.managers.WorldScanner;
import com.mrsuffix.breakallblocks.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles the {@code /bab} command and all its subcommands.
 *
 * <pre>
 *  /bab reload              — reload config.yml
 *  /bab list                — list eliminated materials
 *  /bab restore [material]  — remove material from eliminated list
 *  /bab resetall            — clear the entire eliminated list
 *  /bab waves               — show active wave count
 * </pre>
 *
 * All subcommands require {@code breakallblocks.admin}.
 *
 * @author MRsuffix
 */
public class BabCommandHandler implements CommandExecutor, TabCompleter {

    private static final String PERM_ADMIN = "breakallblocks.admin";
    private static final List<String> SUBCOMMANDS =
            Arrays.asList("reload", "list", "restore", "resetall", "waves");

    private final BreakAllBlocks     plugin;
    private final ConfigManager      cfg;
    private final EliminationManager eliminationManager;
    private final WaveManager        waveManager;
    private final WorldScanner       worldScanner;

    public BabCommandHandler(BreakAllBlocks plugin, ConfigManager cfg,
                              EliminationManager eliminationManager,
                              WaveManager waveManager, WorldScanner worldScanner) {
        this.plugin             = plugin;
        this.cfg                = cfg;
        this.eliminationManager = eliminationManager;
        this.waveManager        = waveManager;
        this.worldScanner       = worldScanner;
    }

    // ── CommandExecutor ────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            MessageUtil.sendParsed(sender, cfg.getMsgNoPermission(), cfg.getPrefix());
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload"   -> handleReload(sender);
            case "list"     -> handleList(sender);
            case "restore"  -> handleRestore(sender, args);
            case "resetall" -> handleResetAll(sender);
            case "waves"    -> handleWaves(sender);
            default         -> sendUsage(sender);
        }
        return true;
    }

    // ── Subcommand handlers ────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        cfg.loadConfig();
        MessageUtil.sendParsed(sender, cfg.getMsgReloadSuccess(), cfg.getPrefix());
        plugin.getLogger().info(sender.getName() + " reloaded the configuration.");
    }

    private void handleList(CommandSender sender) {
        Set<Material> eliminated = eliminationManager.getEliminatedMaterials();
        if (eliminated.isEmpty()) {
            MessageUtil.sendParsed(sender, cfg.getMsgListEmpty(), cfg.getPrefix());
            return;
        }
        MessageUtil.sendParsed(sender,
                cfg.getMsgListHeader().replace("{count}", String.valueOf(eliminated.size())),
                cfg.getPrefix());
        for (Material mat : eliminated) {
            MessageUtil.sendParsed(sender,
                    cfg.getMsgListEntry().replace("{block}", mat.name()),
                    cfg.getPrefix());
        }
    }

    private void handleRestore(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender); return; }

        String input = args[1].toUpperCase();
        Material material;
        try {
            material = Material.valueOf(input);
        } catch (IllegalArgumentException e) {
            MessageUtil.sendParsed(sender,
                    cfg.getMsgInvalidMaterial().replace("{input}", args[1]),
                    cfg.getPrefix());
            return;
        }

        if (eliminationManager.restore(material)) {
            MessageUtil.sendParsed(sender,
                    cfg.getMsgRestoreSuccess().replace("{block}", material.name()),
                    cfg.getPrefix());
            plugin.getLogger().info(sender.getName() + " restored: " + material.name());
        } else {
            MessageUtil.sendParsed(sender,
                    cfg.getMsgRestoreNotFound().replace("{block}", material.name()),
                    cfg.getPrefix());
        }
    }

    private void handleResetAll(CommandSender sender) {
        int count = eliminationManager.getCount();
        eliminationManager.resetAll();
        MessageUtil.sendParsed(sender, cfg.getMsgResetAllSuccess(), cfg.getPrefix());
        plugin.getLogger().info(sender.getName() + " reset all " + count + " eliminated material(s).");
    }

    private void handleWaves(CommandSender sender) {
        int active  = waveManager.getActiveWaveCount();
        int max     = cfg.getMaxActiveWaves();
        MessageUtil.send(sender, "<dark_gray>[<gradient:#FF6B6B:#FFD93D>BAB</gradient>]</dark_gray> "
                + "<yellow>Active waves: <gold><bold>" + active + "</bold></gold>/<gold>" + max + "</gold></yellow>"
                + " <gray>(speed=" + cfg.getWaveSpeed() + " blocks/tick,"
                + " interval=" + cfg.getWaveTickInterval() + " ticks)</gray>");
    }

    private void sendUsage(CommandSender sender) {
        MessageUtil.sendParsed(sender, cfg.getMsgUsage(), cfg.getPrefix());
    }

    // ── TabCompleter ───────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) return List.of();

        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("restore")) {
            String partial = args[1].toUpperCase();
            return eliminationManager.getEliminatedMaterials().stream()
                    .map(Material::name)
                    .filter(n -> n.startsWith(partial))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
