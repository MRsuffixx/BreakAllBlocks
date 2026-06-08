package com.mrsuffix.breakallblocks.managers;

import com.mrsuffix.breakallblocks.BreakAllBlocks;
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
import java.util.logging.Level;

/**
 * WaveManager — BFS wave-propagation engine for BreakAllBlocks.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>A player breaks a block. The origin position is recorded.</li>
 *   <li>A {@link WaveTask} is created for (Material × World). The origin's six
 *       orthogonal neighbors are seeded into the BFS queue.</li>
 *   <li>Each {@code wave_tick_interval} ticks, up to {@code wave_speed} blocks
 *       are dequeued, broken ({@code breakNaturally()} or {@code setType(AIR)}),
 *       and their six neighbors are inspected and enqueued if they match the
 *       target material and are in a loaded chunk.</li>
 *   <li>When the BFS reaches an unloaded chunk boundary,
 *       {@link World#getChunkAtAsync} is called so the chunk eventually loads.
 *       {@link ChunkLoadListener} then calls {@link #onChunkLoaded(Chunk)} to
 *       add the pending blocks to the queue.</li>
 *   <li>When the queue AND the pending-chunks map are both empty the wave is
 *       done. This covers the entire connected component of the target material
 *       reachable from the origin. Disconnected pockets in other chunks are
 *       handled by {@link ChunkLoadListener} which starts a fresh wave whenever
 *       a loading chunk contains an eliminated material.</li>
 * </ol>
 *
 * <h2>Memory notes</h2>
 * The visited set uses a {@code HashSet<Long>} with positions encoded in 64 bits.
 * For very common materials (stone, dirt) in large worlds the set may grow
 * substantially; tune {@code wave_speed} and {@code max_active_waves} to balance
 * throughput vs. RAM use.
 *
 * @author MRsuffix
 */
public class WaveManager {

    private final BreakAllBlocks plugin;
    private final ConfigManager cfg;
    private final EliminationManager eliminationManager;

    /**
     * All currently running wave tasks, keyed by {@code "MATERIAL:worldName"}.
     * LinkedHashMap preserves insertion order for predictable logging.
     */
    private final Map<String, WaveTask> activeTasks = new LinkedHashMap<>();

    /** Waves queued because {@code max_active_waves} was reached. */
    private final Deque<PendingWave> pendingQueue = new ArrayDeque<>();

    // ── Constructor ────────────────────────────────────────────────────────

    public WaveManager(BreakAllBlocks plugin, ConfigManager cfg, EliminationManager eliminationManager) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.eliminationManager = eliminationManager;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Start (or merge into) a wave for the given material from the given origin
     * position. The origin block is assumed already broken/AIR; the wave seeds
     * its six orthogonal neighbors.
     *
     * <p>Thread-safety: must be called from the main server thread.</p>
     *
     * @param ox        origin X coordinate
     * @param oy        origin Y coordinate
     * @param oz        origin Z coordinate
     * @param world     the world in which the wave propagates
     * @param material  the target material to eliminate
     * @param initiator player or console that triggered the break (may be null)
     */
    public void startWave(int ox, int oy, int oz, World world, Material material,
                          CommandSender initiator) {
        if (!cfg.isEnabled()) return;
        if (!cfg.isWorldActive(world.getName())) return;

        String key = waveKey(material, world);
        WaveTask existing = activeTasks.get(key);
        if (existing != null) {
            // Merge: add origin neighbors into the running wave.
            existing.seedNeighbors(ox, oy, oz);
            plugin.getLogger().info("[BAB] Merged new break of " + material.name()
                    + " into existing wave in '" + world.getName() + "'.");
            return;
        }

        if (activeTasks.size() >= cfg.getMaxActiveWaves()) {
            pendingQueue.addLast(new PendingWave(ox, oy, oz, world, material, initiator));
            plugin.getLogger().info("[BAB] Wave for " + material.name()
                    + " queued (max_active_waves=" + cfg.getMaxActiveWaves() + " reached).");
            return;
        }

        launchTask(key, ox, oy, oz, world, material, initiator);
    }

    /**
     * Called by {@link com.mrsuffix.breakallblocks.listeners.ChunkLoadListener}
     * whenever a chunk finishes loading. Notifies all active waves for that world
     * so they can flush their pending-chunk queues.
     */
    public void onChunkLoaded(Chunk chunk) {
        for (WaveTask task : activeTasks.values()) {
            if (task.world.equals(chunk.getWorld())) {
                task.onChunkLoaded(chunk);
            }
        }
    }

    /** Returns {@code true} if a wave is currently active for the given material+world. */
    public boolean isWaveActive(Material material, World world) {
        return activeTasks.containsKey(waveKey(material, world));
    }

    public int getActiveWaveCount() {
        return activeTasks.size();
    }

    /** Cancels all active and pending waves (called from {@code onDisable}). */
    public void cancelAll() {
        for (WaveTask task : activeTasks.values()) task.cancel();
        activeTasks.clear();
        pendingQueue.clear();
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private void launchTask(String key, int ox, int oy, int oz, World world,
                             Material material, CommandSender initiator) {
        WaveTask task = new WaveTask(key, material, world, initiator);
        task.seedNeighbors(ox, oy, oz);
        activeTasks.put(key, task);
        task.schedule();

        plugin.getLogger().info("[BAB] Wave launched for " + material.name()
                + " in '" + world.getName() + "'. Speed=" + cfg.getWaveSpeed()
                + " blocks/tick, interval=" + cfg.getWaveTickInterval() + " ticks.");
    }

    /** Called by {@link WaveTask} when the BFS queue and pending map are both empty. */
    private void onTaskComplete(WaveTask task) {
        activeTasks.remove(task.key);

        plugin.getLogger().info("[BAB] Wave complete for " + task.material.name()
                + " in '" + task.world.getName() + "'. Blocks destroyed: " + task.blocksDestroyed);

        if (task.initiator != null) {
            MessageUtil.sendParsed(task.initiator,
                    cfg.getMsgScanComplete()
                            .replace("{block}", task.material.name())
                            .replace("{count}", String.valueOf(task.blocksDestroyed)),
                    cfg.getPrefix());
        }

        // Start next pending wave (if any).
        while (!pendingQueue.isEmpty() && activeTasks.size() < cfg.getMaxActiveWaves()) {
            PendingWave pw = pendingQueue.pollFirst();
            launchTask(waveKey(pw.material, pw.world),
                    pw.ox, pw.oy, pw.oz, pw.world, pw.material, pw.initiator);
        }
    }

    // ── Static helpers ─────────────────────────────────────────────────────

    private static String waveKey(Material material, World world) {
        return material.name() + ":" + world.getName();
    }

    // ── Coordinate encoding (packed into a single long) ────────────────────
    //
    //  Bit layout (total 64 bits):
    //    bits 63..38  (26 bits) → x + 30_000_000   (range 0..60_000_000 ≤ 2^26-1)
    //    bits 37..26  (12 bits) → y + 2048          (range 0..4095;  MC max Y ~320)
    //    bits 25..0   (26 bits) → z + 30_000_000
    //
    static long encodePos(int x, int y, int z) {
        return ((long)(x + 30_000_000) << 38)
                | ((long)(y + 2048)     << 26)
                | (long)(z + 30_000_000);
    }

    static int decodeX(long pos) { return (int)(pos >>> 38)           - 30_000_000; }
    static int decodeY(long pos) { return (int)((pos >>> 26) & 0xFFF) - 2048; }
    static int decodeZ(long pos) { return (int)(pos & 0x3FF_FFFFL)    - 30_000_000; }

    // Chunk key: upper 32 bits = cx+2_000_000, lower 32 bits = cz+2_000_000
    static long encodeChunk(int cx, int cz) {
        return ((long)(cx + 2_000_000) << 32) | (long)(cz + 2_000_000);
    }

    // ── Inner record: pending wave descriptor ──────────────────────────────

    private record PendingWave(int ox, int oy, int oz, World world,
                                Material material, CommandSender initiator) {}

    // ══════════════════════════════════════════════════════════════════════
    //  WaveTask — one BFS spreading wave for a single (Material × World).
    // ══════════════════════════════════════════════════════════════════════

    final class WaveTask {

        final String          key;
        final Material        material;
        final World           world;
        final CommandSender   initiator;   // may be null

        /** BFS frontier: encoded positions of blocks confirmed to be target material. */
        private final Deque<Long> queue          = new ArrayDeque<>(512);
        /** Every position ever enqueued (or marked visited). Prevents re-processing. */
        private final Set<Long>   visited        = new HashSet<>(1024);
        /**
         * Positions waiting for a chunk to load.
         * Key: encoded chunk (cx, cz) — Value: encoded block positions in that chunk.
         */
        private final Map<Long, Set<Long>> pendingByChunk = new HashMap<>();

        BukkitTask timerTask;
        int        blocksDestroyed = 0;
        private boolean finished   = false;

        WaveTask(String key, Material material, World world, CommandSender initiator) {
            this.key       = key;
            this.material  = material;
            this.world     = world;
            this.initiator = initiator;
        }

        // ── Setup ──────────────────────────────────────────────────────────

        /**
         * Marks the origin as visited (it is already broken) and seeds its six
         * orthogonal neighbors into the queue / pending map.
         */
        void seedNeighbors(int ox, int oy, int oz) {
            visited.add(encodePos(ox, oy, oz)); // origin is air — don't re-break
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

        // ── BFS tick ───────────────────────────────────────────────────────

        private void tick() {
            if (finished) return;

            final int     speed = cfg.getWaveSpeed();
            final boolean drop  = cfg.isDropItems();
            int processed = 0;

            while (processed < speed && !queue.isEmpty()) {
                long pos = queue.pollFirst();
                int x = decodeX(pos), y = decodeY(pos), z = decodeZ(pos);

                // Guard: chunk may have unloaded since we enqueued this block.
                int cx = x >> 4, cz = z >> 4;
                if (!world.isChunkLoaded(cx, cz)) {
                    // Re-pend it for when the chunk comes back.
                    long ck = encodeChunk(cx, cz);
                    pendingByChunk.computeIfAbsent(ck, k -> new HashSet<>()).add(pos);
                    world.getChunkAtAsync(cx, cz); // request reload
                    continue;
                }

                Block block = world.getBlockAt(x, y, z);
                if (block.getType() != material) continue; // already changed

                // --- Break the block ---
                if (drop) {
                    block.breakNaturally();
                } else {
                    block.setType(Material.AIR, false);
                }
                blocksDestroyed++;
                processed++;

                // Propagate BFS to 6 orthogonal neighbors.
                considerNeighbor(x + 1, y, z);
                considerNeighbor(x - 1, y, z);
                considerNeighbor(x, y + 1, z);
                considerNeighbor(x, y - 1, z);
                considerNeighbor(x, y, z + 1);
                considerNeighbor(x, y, z - 1);
            }

            // Wave complete when queue AND pending-chunk map are both empty.
            if (queue.isEmpty() && pendingByChunk.isEmpty()) {
                finish();
            }
        }

        // ── Neighbor logic ─────────────────────────────────────────────────

        private void considerNeighbor(int x, int y, int z) {
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) return;

            long pos = encodePos(x, y, z);
            if (!visited.add(pos)) return; // already queued / visited

            int cx = x >> 4, cz = z >> 4;
            if (!world.isChunkLoaded(cx, cz)) {
                // Park the position until the chunk loads.
                long ck = encodeChunk(cx, cz);
                pendingByChunk.computeIfAbsent(ck, k -> new HashSet<>()).add(pos);
                // Request async load — fires ChunkLoadEvent → onChunkLoaded().
                world.getChunkAtAsync(cx, cz);
                return;
            }

            Block b = world.getBlockAt(x, y, z);
            if (b.getType() == material) {
                queue.addLast(pos);
            }
        }

        // ── Chunk-load callback ────────────────────────────────────────────

        /**
         * Called by {@link WaveManager#onChunkLoaded(Chunk)} when a chunk we
         * requested finishes loading. Flushes pending positions for that chunk.
         */
        void onChunkLoaded(Chunk chunk) {
            long ck = encodeChunk(chunk.getX(), chunk.getZ());
            Set<Long> pending = pendingByChunk.remove(ck);
            if (pending == null || pending.isEmpty()) return;

            for (long pos : pending) {
                int x = decodeX(pos), y = decodeY(pos), z = decodeZ(pos);
                Block b = world.getBlockAt(x, y, z);
                if (b.getType() == material) {
                    queue.addLast(pos);
                }
                // If the block changed type: pos is already in visited; simply discard it.
            }
        }

        // ── Completion ─────────────────────────────────────────────────────

        private void finish() {
            if (finished) return;
            finished = true;
            if (timerTask != null) timerTask.cancel();
            WaveManager.this.onTaskComplete(this);
        }
    }
}
