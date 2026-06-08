package com.mrsuffix.breakallblocks.managers;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
import com.mrsuffix.breakallblocks.model.WaveEvent;
import com.mrsuffix.breakallblocks.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * WaveManager — BFS wave-propagation engine.
 *
 * <p>Updated in v1.1: each WaveTask now records broken block positions and
 * dropped item UUIDs for the {@link UndoManager}.  A {@code currentlyBreaking}
 * field allows {@link com.mrsuffix.breakallblocks.listeners.BlockDropItemListener}
 * to associate dropped item entities with the correct active wave.</p>
 *
 * @author MRsuffix
 */
public class WaveManager {

    private final BreakAllBlocks     plugin;
    private final ConfigManager      cfg;
    private final EliminationManager eliminationManager;
    private       UndoManager        undoManager; // set after construction (circular dep)

    /** "MATERIAL:worldName" → active wave task. */
    private final Map<String, WaveTask> activeTasks = new LinkedHashMap<>();
    /** Overflow queue when max_active_waves is reached. */
    private final Deque<PendingWave>    pendingQueue = new ArrayDeque<>();

    /**
     * The WaveTask currently executing {@code breakNaturally()} inside its tick.
     * Set/cleared synchronously on the main thread so no volatile needed.
     */
    private WaveTask currentlyBreaking = null;

    // ── Constructor ────────────────────────────────────────────────────────

    public WaveManager(BreakAllBlocks plugin, ConfigManager cfg,
                       EliminationManager eliminationManager) {
        this.plugin             = plugin;
        this.cfg                = cfg;
        this.eliminationManager = eliminationManager;
    }

    /** Called after UndoManager is created to break the circular dependency. */
    public void setUndoManager(UndoManager undoManager) {
        this.undoManager = undoManager;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Starts (or merges into) a wave for the given material.
     *
     * @param ox            origin X (the now-broken block)
     * @param oy            origin Y
     * @param oz            origin Z
     * @param world         world where the wave propagates
     * @param material      target material
     * @param initiator     player/console that triggered it (for messages; may be null)
     * @param triggerType   BLOCK_BREAK | MOB_KILL | STARTUP_SCAN
     * @param triggeredBy   display name ("Steve", "System", …)
     */
    public void startWave(int ox, int oy, int oz, World world, Material material,
                          CommandSender initiator,
                          WaveEvent.TriggerType triggerType, String triggeredBy) {
        if (!cfg.isEnabled()) return;
        if (!cfg.isWorldActive(world.getName())) return;

        String key = waveKey(material, world);
        WaveTask existing = activeTasks.get(key);
        if (existing != null) {
            existing.seedNeighbors(ox, oy, oz);
            plugin.getLogger().info("[BAB] Merged break of " + material.name()
                    + " into existing wave in '" + world.getName() + "'.");
            return;
        }

        if (activeTasks.size() >= cfg.getMaxActiveWaves()) {
            pendingQueue.addLast(new PendingWave(ox, oy, oz, world, material,
                    initiator, triggerType, triggeredBy));
            plugin.getLogger().info("[BAB] Wave for " + material.name()
                    + " queued (max=" + cfg.getMaxActiveWaves() + ").");
            return;
        }

        launchTask(key, ox, oy, oz, world, material, initiator, triggerType, triggeredBy);
    }

    /** Notifies active waves that a chunk has loaded so they can flush pending positions. */
    public void onChunkLoaded(Chunk chunk) {
        for (WaveTask task : activeTasks.values()) {
            if (task.world.equals(chunk.getWorld())) {
                task.onChunkLoaded(chunk);
            }
        }
    }

    public boolean isWaveActive(Material material, World world) {
        return activeTasks.containsKey(waveKey(material, world));
    }

    public int getActiveWaveCount() { return activeTasks.size(); }
    public int getPendingWaveCount() { return pendingQueue.size(); }

    /** Returns info snapshots for all active wave tasks (for /bab waves). */
    public List<WaveTaskInfo> getActiveWaveInfo() {
        List<WaveTaskInfo> info = new ArrayList<>();
        for (WaveTask t : activeTasks.values()) {
            info.add(new WaveTaskInfo(t.material, t.world.getName(),
                    t.triggeredBy, t.blocksDestroyed, t.queue.size(), t.pendingByChunk.size()));
        }
        return info;
    }

    public record WaveTaskInfo(Material material, String worldName, String triggeredBy,
                                int blocksDestroyed, int queueSize, int pendingChunks) {}

    /** Used by BlockDropItemListener to record dropped item UUIDs mid-wave. */
    public WaveTask getCurrentlyBreaking() { return currentlyBreaking; }

    public void cancelAll() {
        for (WaveTask t : activeTasks.values()) t.cancel();
        activeTasks.clear();
        pendingQueue.clear();
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private void launchTask(String key, int ox, int oy, int oz, World world, Material material,
                             CommandSender initiator, WaveEvent.TriggerType triggerType, String triggeredBy) {
        WaveTask task = new WaveTask(key, material, world, initiator, triggerType, triggeredBy, ox, oy, oz);
        task.seedNeighbors(ox, oy, oz);
        activeTasks.put(key, task);
        task.schedule();
        plugin.getLogger().info("[BAB] Wave launched for " + material.name()
                + " in '" + world.getName() + "' (trigger=" + triggerType + ").");
    }

    void onTaskComplete(WaveTask task) {
        activeTasks.remove(task.key);
        plugin.getLogger().info("[BAB] Wave complete for " + task.material.name()
                + " in '" + task.world.getName() + "'. Destroyed: " + task.blocksDestroyed);

        // Build and register WaveEvent for undo.
        if (undoManager != null) {
            WaveEvent event = new WaveEvent(
                    UUID.randomUUID().toString(),
                    task.material, task.triggerType, task.triggeredBy,
                    task.world.getName(), task.originX, task.originY, task.originZ,
                    task.startTimestamp,
                    task.brokenPositions, task.droppedItems, task.undoCapExceeded);
            undoManager.record(event);
        }

        if (task.initiator != null) {
            MessageUtil.sendParsed(task.initiator,
                    cfg.getMsgScanComplete()
                            .replace("{block}", task.material.name())
                            .replace("{count}", String.valueOf(task.blocksDestroyed)),
                    cfg.getPrefix());
        }

        // Drain pending queue.
        while (!pendingQueue.isEmpty() && activeTasks.size() < cfg.getMaxActiveWaves()) {
            PendingWave pw = pendingQueue.pollFirst();
            launchTask(waveKey(pw.material, pw.world), pw.ox, pw.oy, pw.oz, pw.world,
                    pw.material, pw.initiator, pw.triggerType, pw.triggeredBy);
        }
    }

    private static String waveKey(Material material, World world) {
        return material.name() + ":" + world.getName();
    }

    // ── Coordinate encoding ────────────────────────────────────────────────

    public static long encodePos(int x, int y, int z) {
        return ((long)(x + 30_000_000) << 38)
                | ((long)(y + 2048)     << 26)
                | (long)(z + 30_000_000);
    }

    public static int decodeX(long pos) { return (int)(pos >>> 38)           - 30_000_000; }
    public static int decodeY(long pos) { return (int)((pos >>> 26) & 0xFFF) - 2048; }
    public static int decodeZ(long pos) { return (int)(pos & 0x3FF_FFFFL)    - 30_000_000; }

    static long encodeChunk(int cx, int cz) {
        return ((long)(cx + 2_000_000) << 32) | (long)(cz + 2_000_000);
    }

    // ── Inner record: pending wave ─────────────────────────────────────────

    private record PendingWave(int ox, int oy, int oz, World world, Material material,
                                CommandSender initiator,
                                WaveEvent.TriggerType triggerType, String triggeredBy) {}

    // ══════════════════════════════════════════════════════════════════════
    //  WaveTask
    // ══════════════════════════════════════════════════════════════════════

    public final class WaveTask {

        public final String               key;
        public final Material             material;
        public final World                world;
        public final CommandSender        initiator;
        public final WaveEvent.TriggerType triggerType;
        public final String               triggeredBy;
        public final int                  originX, originY, originZ;
        public final long                 startTimestamp = System.currentTimeMillis();

        private final Deque<Long>        queue          = new ArrayDeque<>(512);
        private final Set<Long>          visited        = new HashSet<>(1024);
        final         Map<Long, Set<Long>> pendingByChunk = new HashMap<>();

        // Undo tracking
        final List<Long>  brokenPositions = new ArrayList<>();
        final List<UUID>  droppedItems    = new ArrayList<>();
        boolean           undoCapExceeded = false;

        BukkitTask timerTask;
        int        blocksDestroyed = 0;
        private boolean finished   = false;

        WaveTask(String key, Material material, World world, CommandSender initiator,
                 WaveEvent.TriggerType triggerType, String triggeredBy,
                 int originX, int originY, int originZ) {
            this.key        = key;
            this.material   = material;
            this.world      = world;
            this.initiator  = initiator;
            this.triggerType = triggerType;
            this.triggeredBy = triggeredBy;
            this.originX    = originX;
            this.originY    = originY;
            this.originZ    = originZ;
        }

        void seedNeighbors(int ox, int oy, int oz) {
            visited.add(encodePos(ox, oy, oz));
            considerNeighbor(ox + 1, oy, oz);
            considerNeighbor(ox - 1, oy, oz);
            considerNeighbor(ox, oy + 1, oz);
            considerNeighbor(ox, oy - 1, oz);
            considerNeighbor(ox, oy, oz + 1);
            considerNeighbor(ox, oy, oz - 1);
        }

        void schedule() {
            timerTask = Bukkit.getScheduler().runTaskTimer(
                    plugin, this::tick, 1L, cfg.getWaveTickInterval());
        }

        void cancel() {
            finished = true;
            if (timerTask != null) timerTask.cancel();
        }

        // ── Tick ──────────────────────────────────────────────────────────

        private void tick() {
            if (finished) return;

            final int     speed = cfg.getWaveSpeed();
            final boolean drop  = cfg.isDropItems();
            int processed = 0;

            while (processed < speed && !queue.isEmpty()) {
                long pos = queue.pollFirst();
                int x = decodeX(pos), y = decodeY(pos), z = decodeZ(pos);

                int cx = x >> 4, cz = z >> 4;
                if (!world.isChunkLoaded(cx, cz)) {
                    long ck = encodeChunk(cx, cz);
                    pendingByChunk.computeIfAbsent(ck, k -> new HashSet<>()).add(pos);
                    world.getChunkAtAsync(cx, cz);
                    continue;
                }

                Block block = world.getBlockAt(x, y, z);
                if (block.getType() != material) continue;

                // --- Break ---
                currentlyBreaking = this;
                if (drop) {
                    block.breakNaturally();
                } else {
                    block.setType(Material.AIR, false);
                }
                currentlyBreaking = null;

                blocksDestroyed++;
                processed++;

                // Undo tracking
                if (!undoCapExceeded) {
                    brokenPositions.add(pos);
                    if (cfg.getUndoMaxBlocks() > 0
                            && brokenPositions.size() >= cfg.getUndoMaxBlocks()) {
                        undoCapExceeded = true;
                        plugin.getLogger().warning("[BAB] Undo cap (" + cfg.getUndoMaxBlocks()
                                + ") reached for " + material.name()
                                + ". Wave continues but undo will be partial.");
                    }
                }

                considerNeighbor(x + 1, y, z);
                considerNeighbor(x - 1, y, z);
                considerNeighbor(x, y + 1, z);
                considerNeighbor(x, y - 1, z);
                considerNeighbor(x, y, z + 1);
                considerNeighbor(x, y, z - 1);
            }

            if (queue.isEmpty() && pendingByChunk.isEmpty()) {
                finish();
            }
        }

        private void considerNeighbor(int x, int y, int z) {
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) return;
            long pos = encodePos(x, y, z);
            if (!visited.add(pos)) return;

            int cx = x >> 4, cz = z >> 4;
            if (!world.isChunkLoaded(cx, cz)) {
                long ck = encodeChunk(cx, cz);
                pendingByChunk.computeIfAbsent(ck, k -> new HashSet<>()).add(pos);
                world.getChunkAtAsync(cx, cz);
                return;
            }

            if (world.getBlockAt(x, y, z).getType() == material) {
                queue.addLast(pos);
            }
        }

        void onChunkLoaded(Chunk chunk) {
            long ck = encodeChunk(chunk.getX(), chunk.getZ());
            Set<Long> pending = pendingByChunk.remove(ck);
            if (pending == null) return;
            for (long pos : pending) {
                int x = decodeX(pos), y = decodeY(pos), z = decodeZ(pos);
                if (world.getBlockAt(x, y, z).getType() == material) {
                    queue.addLast(pos);
                }
            }
        }

        /** Records a dropped item UUID (called from BlockDropItemListener). */
        public void recordDroppedItem(UUID uuid) {
            droppedItems.add(uuid);
        }

        private void finish() {
            if (finished) return;
            finished = true;
            if (timerTask != null) timerTask.cancel();
            WaveManager.this.onTaskComplete(this);
        }
    }
}
