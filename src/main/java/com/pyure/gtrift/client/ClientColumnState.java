package com.pyure.gtrift.client;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-client cache of currently-active beacons' column positions, keyed by beacon position —
 * deliberately no dimension key, same reasoning as ClientAmbienceState (a single client is only ever
 * in one dimension at a time). No expiry/smoothing logic of its own, unlike ClientAmbienceState's
 * continuously-refreshed ramp — RiftAmbienceTracker's dispatch loop sends an explicit clear whenever a
 * beacon stops being RIFT_OPEN for any reason, so there's nothing here to self-expire against; the
 * only backstop needed is clear() on disconnect (see RiftAmbienceRenderer.onLoggingOut), same as
 * ClientAmbienceState's own disconnect backstop.
 *
 * Deliberately free of any Minecraft-client-only import so this class loads and behaves identically
 * under a headless GameTest, same reasoning as ClientAmbienceState.
 */
public class ClientColumnState {

    private static final Map<BlockPos, List<BlockPos>> ACTIVE = new HashMap<>();

    public static void put(BlockPos beaconPos, List<BlockPos> columns) {
        ACTIVE.put(beaconPos, columns);
    }

    public static void remove(BlockPos beaconPos) {
        ACTIVE.remove(beaconPos);
    }

    /** Wipes all tracked beacons — call on client disconnect/logout. */
    public static void clear() {
        ACTIVE.clear();
    }

    /**
     * One flattened list of every column across every currently-tracked beacon — the renderer doesn't
     * need to know which beacon a column belongs to.
     */
    public static List<BlockPos> allColumns() {
        List<BlockPos> all = new ArrayList<>();
        for (List<BlockPos> columns : ACTIVE.values()) {
            all.addAll(columns);
        }
        return all;
    }

    public static int trackedCount() {
        return ACTIVE.size();
    }
}
