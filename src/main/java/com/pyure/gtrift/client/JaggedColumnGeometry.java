package com.pyure.gtrift.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure local-space vertex math for JaggedColumnRenderer's shape — a vertical stack of jagged
 * (alternating in/out radius) rings, every vertex wobbling via a time-driven sine offset. Deliberately
 * free of any Minecraft-client-only import (Vec3 is a common, not client-only, class — confirmed by
 * its use in server-side code like RiftAmbienceTracker's lightning-strike positioning), same reasoning
 * as ClientAmbienceState/ClientColumnState: this is the only way the geometry gets any real automated
 * coverage, since a headless GameTest server can't safely load a class that references actual
 * rendering types like BufferBuilder/PoseStack (which is why those stay in JaggedColumnRenderer
 * itself, not here).
 */
public class JaggedColumnGeometry {

    public static final int RING_COUNT = 5;
    public static final int VERTS_PER_RING = 6;
    private static final double HEIGHT_STEP = 1.0;
    private static final double BASE_RADIUS = 0.6;
    private static final double JAGGED_RADIUS_VARIANCE = 0.3; // odd-indexed verts sit this much closer in
    private static final double WOBBLE_AMPLITUDE = 0.15;
    // Was 2.0 — real playtesting found that read as a rapid, mechanical jitter (~6.4 full cycles/sec)
    // rather than an atmospheric shimmer. 0.2 rad/tick is a full cycle roughly every 1.6s (~0.64 Hz).
    private static final double WOBBLE_FREQUENCY = 0.2;
    private static final double WOBBLE_PHASE_STEP = 0.5; // radians of phase offset per vertex, so verts don't move in lockstep

    /** Returns RING_COUNT * VERTS_PER_RING positions, ring-major (all of ring 0, then ring 1, ...). */
    public static List<Vec3> computeLocalVertices(float animationTime) {
        List<Vec3> vertices = new ArrayList<>(RING_COUNT * VERTS_PER_RING);
        for (int ring = 0; ring < RING_COUNT; ring++) {
            double y = ring * HEIGHT_STEP;
            for (int i = 0; i < VERTS_PER_RING; i++) {
                double baseAngle = i * (2 * Math.PI / VERTS_PER_RING);
                double radius = isOuterVertex(i) ? BASE_RADIUS : (BASE_RADIUS - JAGGED_RADIUS_VARIANCE);
                double phase = (ring * VERTS_PER_RING + i) * WOBBLE_PHASE_STEP;
                radius += Math.sin(animationTime * WOBBLE_FREQUENCY + phase) * WOBBLE_AMPLITUDE;

                double x = Math.cos(baseAngle) * radius;
                double z = Math.sin(baseAngle) * radius;
                vertices.add(new Vec3(x, y, z));
            }
        }
        return vertices;
    }

    /**
     * Whether the given index within a ring (0..VERTS_PER_RING-1) is one of the "outer" jagged spike
     * points (BASE_RADIUS) rather than a recessed "inner" point closer to the column's own center axis
     * — the same alternation that creates the jagged silhouette above, exposed here as the single
     * source of truth so JaggedColumnRenderer's per-vertex coloring (dark core / bright spikes) can
     * reuse the exact same convention rather than duplicating an "i % 2" check in a second file.
     */
    public static boolean isOuterVertex(int indexWithinRing) {
        return indexWithinRing % 2 == 0;
    }

    /**
     * A deterministic per-column offset (same column position always yields the same value across
     * frames, so wobble stays stable rather than jumping around), added to animationTime before
     * calling computeLocalVertices — desynchronizes columns from each other. Without this, every
     * column receives the exact same animationTime each frame and each vertex's phase depends only on
     * its position within its own ring/column (identical across every column), so all 30 columns were
     * mathematically guaranteed to wobble in perfect lockstep — found via real playtesting, read as
     * "possibly synchronous" and mechanical rather than organic.
     */
    public static float phaseOffsetForColumn(BlockPos columnPos) {
        return Math.floorMod(columnPos.hashCode(), 1000);
    }
}
