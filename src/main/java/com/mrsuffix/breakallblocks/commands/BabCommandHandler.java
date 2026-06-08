package com.mrsuffix.breakallblocks.commands;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.managers.ConfigManager;
import com.mrsuffix.breakallblocks.managers.EliminationManager;
import com.mrsuffix.breakallblocks.managers.UndoManager;
import com.mrsuffix.breakallblocks.managers.WaveManager;
import com.mrsuffix.breakallblocks.managers.WorldScanner;
import com.mrsuffix.breakallblocks.model.WaveEvent;
import com.mrsuffix.breakallblocks.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles all {@code /bab} subcommands with smart tab-completion.
 *
 * <pre>
 *  /bab help [page]
 *  /bab list [page]
 *  /bab status
 *  /bab reload
 *  /bab restore <material>
 *  /bab resetall [confirm]
 *  /bab waves
 *  /bab exclude <material>
 *  /bab include <material>
 *  /bab undo [N|history]
 * </pre>
 *
 * All subcommands require {@code breakallblocks.admin}.
 *
 * @author MRsuffix
 */
public class BabCommandHandler implements CommandExecutor, TabCompleter {

    private static final String PERM = "breakallblocks.admin";
    private static final int    PAGE_SIZE = 10;
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "list", "status", "reload", "restore",
            "resetall", "waves", "exclude", "include", "undo");

    // ── Help entries ───────────────────────────────────────────────────────

    private record HelpEntry(String usage, String description) {}

    private static final List<HelpEntry> HELP = Arrays.asList(
            new HelpEntry("/bab help [page]",         "Show this help screen"),
            new HelpEntry("/bab list [page]",          "List eliminated block types with metadata"),
            new HelpEntry("/bab status",               "Plugin status, TPS & wave info"),
            new HelpEntry("/bab reload",               "Reload config.yml without restart"),
            new HelpEntry("/bab restore <material>",   "Restore a material to breakable"),
            new HelpEntry("/bab resetall [confirm]",   "Clear ALL eliminated materials"),
            new HelpEntry("/bab waves",                "Show currently active wave tasks"),
            new HelpEntry("/bab exclude <material>",   "Protect a material at runtime"),
            new HelpEntry("/bab include <material>",   "Remove runtime-exclusion from a material"),
            new HelpEntry("/bab undo [N]",             "Undo last N wave events and restore blocks"),
            new HelpEntry("/bab undo history",         "List recent wave events available to undo")
    );

    // ── Fields ─────────────────────────────────────────────────────────────

    private final BreakAllBlocks     plugin;
    private final ConfigManager      cfg;
    private final EliminationManager eliminationManager;
    private final WaveManager        waveManager;
    private final UndoManager        undoManager;
    private final WorldScanner       worldScanner;

    public BabCommandHandler(BreakAllBlocks plugin, ConfigManager cfg,
                              EliminationManager eliminationManager, WaveManager waveManager,
                              UndoManager undoManager, WorldScanner worldScanner) {
        this.plugin             = plugin;
        this.cfg                = cfg;
        this.eliminationManager = eliminationManager;
        this.waveManager        = waveManager;
        this.undoManager        = undoManager;
        this.worldScanner       = worldScanner;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CommandExecutor
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            MessageUtil.sendParsed(sender, cfg.getMsgNoPermission(), cfg.getPrefix());
            return true;
        }
        if (args.length == 0) { handleHelp(sender, 1); return true; }

        switch (args[0].toLowerCase()) {
            case "help"     -> handleHelp(sender,    parsePage(args, 1));
            case "list"     -> handleList(sender,    parsePage(args, 1));
            case "status"   -> handleStatus(sender);
            case "reload"   -> handleReload(sender);
            case "restore"  -> handleRestore(sender, args);
            case "resetall" -> handleResetAll(sender, args);
            case "waves"    -> handleWaves(sender);
            case "exclude"  -> handleExclude(sender, args);
            case "include"  -> handleInclude(sender, args);
            case "undo"     -> handleUndo(sender, args);
            default         -> handleHelp(sender, 1);
        }
        return true;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  /bab help [page]
    // ──────────────────────────────────────────────────────────────────────

    private void handleHelp(CommandSender sender, int page) {
        int totalPages = (int) Math.ceil((double) HELP.size() / PAGE_SIZE);
        page = clamp(page, 1, totalPages);
        int start = (page - 1) * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, HELP.size());

        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        send(sender, "  <gradient:#FF6B6B:#FFD93D><bold>BreakAllBlocks</bold></gradient> "
                + "<gray>Help <white>(" + page + "/" + totalPages + ")</white></gray>");
        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");

        for (int i = start; i < end; i++) {
            HelpEntry h = HELP.get(i);
            send(sender, " <gold>" + h.usage() + "</gold>");
            send(sender, "   <gray>→ " + h.description() + "</gray>");
        }

        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        if (totalPages > 1) {
            send(sender, " <dark_gray>Page " + page + "/" + totalPages
                    + " — /bab help " + (page < totalPages ? page + 1 : 1) + " for more</dark_gray>");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  /bab list [page]
    // ──────────────────────────────────────────────────────────────────────

    private void handleList(CommandSender sender, int page) {
        List<Map.Entry<Material, EliminationManager.EliminationRecord>> sorted =
                eliminationManager.getSortedEliminated();

        if (sorted.isEmpty()) {
            MessageUtil.sendParsed(sender, cfg.getMsgListEmpty(), cfg.getPrefix());
            return;
        }

        int totalPages = (int) Math.ceil((double) sorted.size() / PAGE_SIZE);
        page = clamp(page, 1, totalPages);
        int start = (page - 1) * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, sorted.size());

        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        send(sender, "  <gradient:#FF6B6B:#FFD93D><bold>Eliminated Materials</bold></gradient> "
                + "<gray>(" + sorted.size() + " total, page "
                + page + "/" + totalPages + ")</gray>");
        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");

        for (int i = start; i < end; i++) {
            var entry = sorted.get(i);
            Material mat = entry.getKey();
            EliminationManager.EliminationRecord rec = entry.getValue();
            String age = (rec != null && rec.timestamp > 0) ? rec.getAgeString() : "unknown";
            String by  = (rec != null) ? rec.triggeredBy : "Unknown";
            String type= (rec != null) ? rec.triggerType : "UNKNOWN";
            send(sender, " <gold>#" + (i + 1) + "</gold> <white>" + mat.name() + "</white>"
                    + " <dark_gray>|</dark_gray> <gray>by " + by
                    + "</gray> <dark_gray>|</dark_gray> <aqua>" + type
                    + "</aqua> <dark_gray>|</dark_gray> <yellow>" + age + "</yellow>");
        }

        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        if (totalPages > 1)
            send(sender, " <dark_gray>/bab list " + (page < totalPages ? page + 1 : 1)
                    + " for next page</dark_gray>");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  /bab status
    // ──────────────────────────────────────────────────────────────────────

    private void handleStatus(CommandSender sender) {
        int eliminated = eliminationManager.getCount();
        int active     = waveManager.getActiveWaveCount();
        int pending    = waveManager.getPendingWaveCount();
        int undoHistory= undoManager.getHistorySize();
        String state   = cfg.isEnabled()
                ? "<green><bold>ENABLED</bold></green>"
                : "<red><bold>DISABLED</bold></red>";

        // Rough TPS estimate from Bukkit (Paper exposes TPS; fall back gracefully).
        double tps;
        try {
            double[] tpsArr = Bukkit.getServer().getTPS();
            tps = Math.round(tpsArr[0] * 100.0) / 100.0;
        } catch (Exception e) {
            tps = -1;
        }
        String tpsStr = tps < 0 ? "<gray>N/A</gray>"
                : (tps >= 19 ? "<green>" + tps + "</green>" : "<red>" + tps + "</red>");

        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        send(sender, "  <gradient:#FF6B6B:#FFD93D><bold>BreakAllBlocks</bold></gradient> <gray>Status</gray>");
        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        send(sender, " <gray>Plugin:</gray>        " + state);
        send(sender, " <gray>TPS:</gray>           " + tpsStr);
        send(sender, " <gray>Eliminated:</gray>    <gold><bold>" + eliminated + "</bold></gold>");
        send(sender, " <gray>Active waves:</gray>  <aqua>" + active + "/" + cfg.getMaxActiveWaves() + "</aqua>"
                + (pending > 0 ? " <yellow>(+" + pending + " pending)</yellow>" : ""));
        send(sender, " <gray>Wave speed:</gray>    <white>" + cfg.getWaveSpeed()
                + " blocks / " + cfg.getWaveTickInterval() + " tick(s)</white>");
        send(sender, " <gray>Drop items:</gray>    " + (cfg.isDropItems()
                ? "<green>yes</green>" : "<gray>no</gray>"));
        send(sender, " <gray>Undo history:</gray>  <white>" + undoHistory
                + "/" + cfg.getUndoHistorySize() + "</white>");
        send(sender, " <gray>Undo cap:</gray>      <white>"
                + (cfg.getUndoMaxBlocks() <= 0 ? "unlimited" : cfg.getUndoMaxBlocks() + " blocks")
                + "</white>");
        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  /bab reload
    // ──────────────────────────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        cfg.loadConfig();
        MessageUtil.sendParsed(sender, cfg.getMsgReloadSuccess(), cfg.getPrefix());
        plugin.getLogger().info(sender.getName() + " reloaded the configuration.");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  /bab restore <material>
    // ──────────────────────────────────────────────────────────────────────

    private void handleRestore(CommandSender sender, String[] args) {
        if (args.length < 2) { handleHelp(sender, 1); return; }
        Material mat = parseMaterial(sender, args[1]);
        if (mat == null) return;

        if (eliminationManager.restore(mat)) {
            MessageUtil.sendParsed(sender,
                    cfg.getMsgRestoreSuccess().replace("{block}", mat.name()), cfg.getPrefix());
            plugin.getLogger().info(sender.getName() + " restored: " + mat.name());
        } else {
            MessageUtil.sendParsed(sender,
                    cfg.getMsgRestoreNotFound().replace("{block}", mat.name()), cfg.getPrefix());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  /bab resetall [confirm]
    // ──────────────────────────────────────────────────────────────────────

    private void handleResetAll(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            send(sender, "<red><bold>⚠ WARNING:</bold></red>"
                    + " <yellow>This will clear ALL " + eliminationManager.getCount()
                    + " eliminated materials!</yellow>");
            send(sender, " <gray>Type <white>/bab resetall confirm</white> to proceed.</gray>");
            return;
        }
        int count = eliminationManager.getCount();
        eliminationManager.resetAll();
        MessageUtil.sendParsed(sender, cfg.getMsgResetAllSuccess(), cfg.getPrefix());
        plugin.getLogger().info(sender.getName() + " reset all " + count + " eliminated material(s).");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  /bab waves
    // ──────────────────────────────────────────────────────────────────────

    private void handleWaves(CommandSender sender) {
        List<WaveManager.WaveTaskInfo> infos = waveManager.getActiveWaveInfo();
        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        send(sender, "  <gradient:#FF6B6B:#FFD93D><bold>Active Waves</bold></gradient> "
                + "<gray>(" + infos.size() + "/" + cfg.getMaxActiveWaves() + ")"
                + (waveManager.getPendingWaveCount() > 0
                ? " <yellow>+" + waveManager.getPendingWaveCount() + " pending</yellow>" : "")
                + "</gray>");
        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");

        if (infos.isEmpty()) {
            send(sender, " <gray>No active waves.</gray>");
        } else {
            for (int i = 0; i < infos.size(); i++) {
                var w = infos.get(i);
                send(sender, " <gold>#" + (i + 1) + "</gold> "
                        + "<white>" + w.material().name() + "</white>"
                        + " <dark_gray>|</dark_gray> <gray>" + w.worldName() + "</gray>"
                        + " <dark_gray>|</dark_gray> <green>+" + w.blocksDestroyed() + " broken</green>"
                        + " <dark_gray>|</dark_gray> <aqua>~" + w.queueSize() + " in queue</aqua>"
                        + " <dark_gray>|</dark_gray> <yellow>" + w.pendingChunks() + " chunks pending</yellow>"
                        + " <dark_gray>|</dark_gray> <gray>by " + w.triggeredBy() + "</gray>");
            }
        }
        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  /bab exclude <material>
    // ──────────────────────────────────────────────────────────────────────

    private void handleExclude(CommandSender sender, String[] args) {
        if (args.length < 2) { handleHelp(sender, 1); return; }
        Material mat = parseMaterial(sender, args[1]);
        if (mat == null) return;

        cfg.addRuntimeExclusion(mat);
        MessageUtil.sendParsed(sender,
                cfg.getMsgExcludeSuccess().replace("{block}", mat.name()), cfg.getPrefix());
        plugin.getLogger().info(sender.getName() + " runtime-excluded: " + mat.name());
    }

    // ──────────────────────────────────────────────────────────────────────
    //  /bab include <material>
    // ──────────────────────────────────────────────────────────────────────

    private void handleInclude(CommandSender sender, String[] args) {
        if (args.length < 2) { handleHelp(sender, 1); return; }
        Material mat = parseMaterial(sender, args[1]);
        if (mat == null) return;

        if (cfg.removeRuntimeExclusion(mat)) {
            MessageUtil.sendParsed(sender,
                    cfg.getMsgIncludeSuccess().replace("{block}", mat.name()), cfg.getPrefix());
        } else {
            MessageUtil.sendParsed(sender,
                    cfg.getMsgMaterialNotExcluded().replace("{block}", mat.name()), cfg.getPrefix());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  /bab undo [N|history]
    // ──────────────────────────────────────────────────────────────────────

    private void handleUndo(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("history")) {
            handleUndoHistory(sender);
            return;
        }

        int count = 1;
        if (args.length >= 2) {
            try {
                count = Integer.parseInt(args[1]);
                if (count < 1) count = 1;
            } catch (NumberFormatException e) {
                send(sender, "<red>Invalid number: " + args[1] + "</red>");
                return;
            }
        }
        undoManager.undo(count, sender);
    }

    private void handleUndoHistory(CommandSender sender) {
        List<WaveEvent> history = undoManager.getHistory();
        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        send(sender, "  <gradient:#FF6B6B:#FFD93D><bold>Undo History</bold></gradient> "
                + "<gray>(" + history.size() + "/" + cfg.getUndoHistorySize() + ")</gray>");
        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");

        if (history.isEmpty()) {
            send(sender, " <gray>No undo history available (lost on server restart).</gray>");
        } else {
            for (int i = 0; i < history.size(); i++) {
                WaveEvent ev = history.get(i);
                String capFlag = ev.isUndoCapExceeded() ? " <red>(partial)</red>" : "";
                send(sender, " <gold>#" + (i + 1) + (i == 0 ? " (latest)" : "") + "</gold>"
                        + " <white>" + ev.getMaterial().name() + "</white>"
                        + " <dark_gray>|</dark_gray> <gray>by " + ev.getTriggeredBy() + "</gray>"
                        + " <dark_gray>|</dark_gray> <aqua>" + ev.getTriggerType().getDisplay() + "</aqua>"
                        + " <dark_gray>|</dark_gray> <yellow>" + ev.getAgeString() + "</yellow>"
                        + " <dark_gray>|</dark_gray> <green>" + ev.getBrokenPositions().size() + " blocks</green>"
                        + capFlag);
            }
        }
        send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        send(sender, " <dark_gray>Note: undo history is in-memory only (lost on restart).</dark_gray>");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TabCompleter
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd,
                                      String alias, String[] args) {
        if (!sender.hasPermission(PERM)) return List.of();

        if (args.length == 1) {
            return filterPrefix(SUBCOMMANDS, args[0]);
        }

        return switch (args[0].toLowerCase()) {
            case "restore" -> {
                if (args.length == 2) {
                    // Show eliminated materials, newest first, with age label.
                    List<String> opts = eliminationManager.getSortedEliminated().stream()
                            .map(e -> {
                                String age = (e.getValue() != null && e.getValue().timestamp > 0)
                                        ? " (" + e.getValue().getAgeString() + ")" : "";
                                return e.getKey().name() + age;
                            })
                            .filter(s -> s.toUpperCase().startsWith(args[1].toUpperCase()))
                            .collect(Collectors.toList());
                    yield opts;
                }
                yield List.of();
            }
            case "exclude" -> {
                if (args.length == 2) {
                    // All solid block materials.
                    yield Arrays.stream(Material.values())
                            .filter(m -> m.isBlock() && !m.isAir())
                            .map(Material::name)
                            .filter(n -> n.startsWith(args[1].toUpperCase()))
                            .sorted()
                            .limit(50)
                            .collect(Collectors.toList());
                }
                yield List.of();
            }
            case "include" -> {
                if (args.length == 2) {
                    yield cfg.getRuntimeExclusions().stream()
                            .map(Material::name)
                            .filter(n -> n.startsWith(args[1].toUpperCase()))
                            .sorted()
                            .collect(Collectors.toList());
                }
                yield List.of();
            }
            case "undo" -> {
                if (args.length == 2) {
                    List<String> opts = new ArrayList<>();
                    opts.add("history");
                    int histSize = undoManager.getHistorySize();
                    for (int i = 1; i <= histSize; i++) opts.add(String.valueOf(i));
                    yield filterPrefix(opts, args[1]);
                }
                yield List.of();
            }
            case "resetall" -> {
                if (args.length == 2) yield filterPrefix(List.of("confirm"), args[1]);
                yield List.of();
            }
            default -> List.of();
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════

    private void send(CommandSender sender, String miniMessage) {
        MessageUtil.send(sender, miniMessage);
    }

    private Material parseMaterial(CommandSender sender, String input) {
        try {
            return Material.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException e) {
            MessageUtil.sendParsed(sender,
                    cfg.getMsgInvalidMaterial().replace("{input}", input), cfg.getPrefix());
            return null;
        }
    }

    private static int parsePage(String[] args, int argIndex) {
        if (args.length <= argIndex) return 1;
        try { return Math.max(1, Integer.parseInt(args[argIndex])); }
        catch (NumberFormatException e) { return 1; }
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String up = prefix.toUpperCase();
        return options.stream()
                .filter(s -> s.toUpperCase().startsWith(up))
                .collect(Collectors.toList());
    }
}
