package com.pyure.gtrift.client;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-client cache of currently-active beacons' ambience ramp, keyed by beacon position — deliberately
 * no dimension key, since a single client is only ever in one dimension at a time (see
 * specs/ambience.md's "map keyed by bare BlockPos" discussion). Two backstops beyond the server's
 * explicit clear packet guard against a stranded entry (the client carrying a stale tint/latch forever
 * after a dimension change or disconnect the server couldn't send a clear for): clear() wipes
 * everything on client disconnect, and tick() self-expires any entry that stops getting refreshed.
 *
 * Deliberately free of any Minecraft-client-only import (Minecraft, rendering classes, etc.) so this
 * class loads and behaves identically under a headless GameTest server — only the actual packet
 * handler and render/tick glue that calls into this needs to be Dist.CLIENT-gated.
 */
public class ClientAmbienceState {

    // 3x the server's 10-tick send interval — long enough to absorb ordinary jitter, short enough
    // that a genuinely stranded entry (dropped clear packet, crash) clears itself within ~1.5s.
    private static final int EXPIRY_TICKS = 30;

    // Fraction of the remaining gap to displayedRamp closed per tick — smooths what would otherwise
    // be a visible ~0.5s-interval "staircase" (the server only sends updates every 10 ticks) into a
    // continuous glide. Found via real playtesting: without this, both the end-of-event fade and the
    // backdrop-radius boundary cutoff read as abrupt/stepped rather than smooth.
    private static final float SMOOTHING_FACTOR = 0.15f;
    // Snap once within this distance of target, rather than asymptotically approaching forever —
    // avoids rendering a permanently-nonzero-but-imperceptible dome after everything's cleared.
    private static final float SNAP_EPSILON = 0.001f;

    private static final class Entry {
        float ramp;
        int ticksUntilExpiry;

        Entry(float ramp) {
            this.ramp = ramp;
            this.ticksUntilExpiry = EXPIRY_TICKS;
        }
    }

    private static final Map<BlockPos, Entry> ACTIVE = new HashMap<>();
    private static float displayedRamp = 0f;

    // Which beacon currently "owns" the music slot, if any — a plain field rather than a per-Entry
    // flag, so first-wins is simple and deterministic (whichever beacon claimed it first keeps it,
    // not whichever a HashMap happens to iterate first). Claimed on the first playMusic=true put(),
    // released the moment its own playMusic flips back to false in a later put() (confirmed via real
    // playtesting: playMusic goes false the instant a player leaves the encounter radius, even while
    // the beacon itself is still fully RIFT_OPEN — an earlier version only released on remove()/clear(),
    // which meant music kept playing until the player left the much larger backdrop radius instead of
    // stopping at the encounter radius boundary like it's supposed to). This doesn't conflict with the
    // event's own 5s wind-down fade: server-side, playMusic stays true for an in-range player for the
    // beacon's entire fade-out (RiftAmbienceTracker never flips it during that window), so the fade
    // still ends cleanly via the final remove() rather than this path.
    private static BlockPos musicSourceBeacon = null;

    public static void put(BlockPos beaconPos, float ramp, boolean playMusic) {
        ACTIVE.put(beaconPos, new Entry(ramp));
        if (playMusic) {
            if (musicSourceBeacon == null) {
                musicSourceBeacon = beaconPos;
            }
        } else if (beaconPos.equals(musicSourceBeacon)) {
            musicSourceBeacon = null;
        }
    }

    public static void remove(BlockPos beaconPos) {
        ACTIVE.remove(beaconPos);
        if (beaconPos.equals(musicSourceBeacon)) {
            musicSourceBeacon = null;
        }
    }

    /** Wipes all tracked beacons — call on client disconnect/logout. */
    public static void clear() {
        ACTIVE.clear();
        musicSourceBeacon = null;
    }

    /** The beacon currently holding the music slot, or null if none. */
    public static BlockPos musicSourceBeacon() {
        return musicSourceBeacon;
    }

    /** The current (unsmoothed) ramp of whichever beacon holds the music slot, or 0 if none. */
    public static float musicSourceRamp() {
        Entry entry = musicSourceBeacon != null ? ACTIVE.get(musicSourceBeacon) : null;
        return entry != null ? entry.ramp : 0f;
    }

    /**
     * Call once per client tick. When isPaused is true, both the expiry countdown and the smoothed
     * ramp are frozen in place rather than advanced — otherwise, on a singleplayer pause, Forge's
     * client tick keeps firing (so the countdown keeps counting down) while the paused integrated
     * server stops sending refresh packets, and the 30-tick expiry backstop above would wrongly fire
     * and clear perfectly valid state after a few seconds of pause. Confirmed via real playtesting,
     * not anticipated when the backstop was designed for the dimension-change/disconnect case.
     */
    public static void tick(boolean isPaused) {
        if (isPaused) return;
        ACTIVE.values().removeIf(entry -> --entry.ticksUntilExpiry <= 0);
        float target = effectiveRamp();
        float delta = target - displayedRamp;
        displayedRamp = Math.abs(delta) <= SNAP_EPSILON ? target : displayedRamp + delta * SMOOTHING_FACTOR;
    }

    /** Max ramp across every currently-tracked beacon, so overlapping events compound rather than flicker. 0 if none are active. */
    public static float effectiveRamp() {
        float max = 0f;
        for (Entry entry : ACTIVE.values()) {
            if (entry.ramp > max) max = entry.ramp;
        }
        return max;
    }

    /** The smoothed value rendering code should actually use — see tick()'s doc comment. */
    public static float smoothedRamp() {
        return displayedRamp;
    }

    public static int trackedCount() {
        return ACTIVE.size();
    }
}
