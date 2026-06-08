package com.mrsuffix.breakallblocks.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable record of a single wave-elimination event.
 *
 * <p>Created by {@link com.mrsuffix.breakallblocks.managers.WaveManager.WaveTask}
 * when the wave completes and handed to
 * {@link com.mrsuffix.breakallblocks.managers.UndoManager} for potential undo.</p>
 *
 * <h2>Memory note</h2>
 * Each encoded block position is a {@code long} (8 bytes).  If
 * {@code undo_max_blocks} is exceeded during the wave, {@link #isUndoCapExceeded()}
 * returns {@code true} and the event cannot be fully undone.
 *
 * @author MRsuffix
 */
public final class WaveEvent {

    // ── Trigger source ─────────────────────────────────────────────────────

    public enum TriggerType {
        BLOCK_BREAK("Block Break"),
        MOB_KILL("Mob Kill"),
        STARTUP_SCAN("Startup Scan");

        private final String display;
        TriggerType(String display) { this.display = display; }
        public String getDisplay() { return display; }
    }

    // ── Fields ─────────────────────────────────────────────────────────────

    private final String      id;
    private final Material    material;
    private final TriggerType triggerType;
    private final String      triggeredBy;   // player name or "System"
    private final String      worldName;
    private final int         originX, originY, originZ;
    private final long        timestamp;     // System.currentTimeMillis()

    /**
     * Encoded block positions (WaveManager.encodePos format) of every block
     * that was broken during this wave.  All positions belong to {@link #worldName}.
     */
    private final List<Long>  brokenPositions;

    /** UUIDs of Item entities that were spawned by {@code breakNaturally()} calls. */
    private final List<UUID>  droppedItemUUIDs;

    /** True when brokenPositions was capped by undo_max_blocks. */
    private final boolean     undoCapExceeded;

    // ── Constructor (package-private — built by WaveTask) ─────────────────

    public WaveEvent(String id, Material material, TriggerType triggerType, String triggeredBy,
              String worldName, int originX, int originY, int originZ, long timestamp,
              List<Long> brokenPositions, List<UUID> droppedItemUUIDs, boolean undoCapExceeded) {
        this.id               = id;
        this.material         = material;
        this.triggerType      = triggerType;
        this.triggeredBy      = triggeredBy;
        this.worldName        = worldName;
        this.originX          = originX;
        this.originY          = originY;
        this.originZ          = originZ;
        this.timestamp        = timestamp;
        this.brokenPositions  = Collections.unmodifiableList(brokenPositions);
        this.droppedItemUUIDs = Collections.unmodifiableList(droppedItemUUIDs);
        this.undoCapExceeded  = undoCapExceeded;
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public String      getId()               { return id; }
    public Material    getMaterial()         { return material; }
    public TriggerType getTriggerType()      { return triggerType; }
    public String      getTriggeredBy()      { return triggeredBy; }
    public String      getWorldName()        { return worldName; }
    public int         getOriginX()          { return originX; }
    public int         getOriginY()          { return originY; }
    public int         getOriginZ()          { return originZ; }
    public long        getTimestamp()        { return timestamp; }
    public List<Long>  getBrokenPositions()  { return brokenPositions; }
    public List<UUID>  getDroppedItemUUIDs() { return droppedItemUUIDs; }
    public boolean     isUndoCapExceeded()   { return undoCapExceeded; }

    /** Human-readable "X seconds ago / X min ago / X h ago" string. */
    public String getAgeString() {
        long seconds = (System.currentTimeMillis() - timestamp) / 1_000;
        if (seconds < 60)   return seconds + "s ago";
        if (seconds < 3600) return (seconds / 60) + "min ago";
        if (seconds < 86400)return (seconds / 3600) + "h ago";
        return (seconds / 86400) + "d ago";
    }

    @Override
    public String toString() {
        return "WaveEvent{" + id + ", " + material + ", by=" + triggeredBy
                + ", blocks=" + brokenPositions.size() + "}";
    }
}
