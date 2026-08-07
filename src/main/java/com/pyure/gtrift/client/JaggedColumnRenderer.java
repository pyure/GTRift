package com.pyure.gtrift.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * First of the five visual directions discussed during planning (plans/rift-multi-column.md) — a
 * cracked/lightning-bolt-like silhouette rather than a smooth beacon-beam. Reuses the exact
 * quad/POSITION_COLOR pipeline RiftAmbienceRenderer's dome already uses in production — only the
 * per-column camera-relative translation is new territory, a standard, well-understood pattern.
 *
 * Radial two-tone coloring (dark violet/near-black core, bright red-orange spikes) — real playtesting
 * found the original single-color fill literally color-matched RiftAmbienceRenderer's ambient dome
 * (both were tuned from the same "muted red" starting point) and was nearly invisible against it. See
 * the color constants' own comment for the fuller reasoning.
 *
 * The actual local-space vertex math lives in {@link JaggedColumnGeometry}, deliberately separate
 * from this class — see that class's own doc comment for why (this class references client-only
 * rendering types like BufferBuilder/PoseStack, which a headless GameTest server can't safely load).
 */
public class JaggedColumnRenderer implements RiftColumnRenderer {

    // Radial two-tone: dark violet/near-black on the recessed "inner" points, bright red-orange on the
    // jagged "outer" spike points (see JaggedColumnGeometry.isOuterVertex). Deliberately NOT the same
    // RGB as RiftAmbienceRenderer's DOME_RED/GREEN/BLUE anymore — real playtesting found the column
    // literally color-matched the ambient fog it sits inside (both were tuned from the same "muted
    // red" starting point) and was nearly invisible as a result. A shape with genuine internal
    // contrast (dark core, bright edge) stays visible against a mid-tone red backdrop in a way a
    // single different hue wouldn't reliably guarantee. Both alpha values pushed up from the old
    // single 0.55 — the whole point here is more presence, not just a different color.
    private static final float INNER_RED = 0.10f;
    private static final float INNER_GREEN = 0.02f;
    private static final float INNER_BLUE = 0.16f;
    private static final float INNER_ALPHA = 0.75f; // more opaque — reads as a solid dark core, not a translucent tint

    private static final float OUTER_RED = 0.85f;
    private static final float OUTER_GREEN = 0.15f;
    private static final float OUTER_BLUE = 0.02f;
    private static final float OUTER_ALPHA = 0.65f;

    @Override
    public void appendGeometry(BufferBuilder buffer, PoseStack poseStack, BlockPos columnPos, Vec3 cameraPos,
                                float animationTime) {
        poseStack.translate(columnPos.getX() - cameraPos.x, columnPos.getY() - cameraPos.y, columnPos.getZ() - cameraPos.z);
        PoseStack.Pose pose = poseStack.last();

        // Per-column phase offset so all 30 columns don't wobble in perfect synchrony with each other
        // — see JaggedColumnGeometry.phaseOffsetForColumn's own doc comment.
        float columnAnimationTime = animationTime + JaggedColumnGeometry.phaseOffsetForColumn(columnPos);
        List<Vec3> vertices = JaggedColumnGeometry.computeLocalVertices(columnAnimationTime);

        // Connects each ring to the next with a strip of quads (standard tube/lathe-extrusion
        // connectivity) — RING_COUNT-1 ring-gaps x VERTS_PER_RING quads per column, trivial for a
        // modern GPU even across every column combined. VERTS_PER_RING is even, so i and next always
        // differ in outer/inner parity — every quad naturally gets one dark corner pair and one bright
        // corner pair, same as the jagged silhouette itself already alternates.
        for (int ring = 0; ring < JaggedColumnGeometry.RING_COUNT - 1; ring++) {
            for (int i = 0; i < JaggedColumnGeometry.VERTS_PER_RING; i++) {
                int next = (i + 1) % JaggedColumnGeometry.VERTS_PER_RING;
                boolean iOuter = JaggedColumnGeometry.isOuterVertex(i);
                boolean nextOuter = JaggedColumnGeometry.isOuterVertex(next);
                vertex(buffer, pose, vertices.get(ring * JaggedColumnGeometry.VERTS_PER_RING + i), iOuter);
                vertex(buffer, pose, vertices.get(ring * JaggedColumnGeometry.VERTS_PER_RING + next), nextOuter);
                vertex(buffer, pose, vertices.get((ring + 1) * JaggedColumnGeometry.VERTS_PER_RING + next), nextOuter);
                vertex(buffer, pose, vertices.get((ring + 1) * JaggedColumnGeometry.VERTS_PER_RING + i), iOuter);
            }
        }
    }

    private static void vertex(BufferBuilder buffer, PoseStack.Pose pose, Vec3 local, boolean outer) {
        float r = outer ? OUTER_RED : INNER_RED;
        float g = outer ? OUTER_GREEN : INNER_GREEN;
        float b = outer ? OUTER_BLUE : INNER_BLUE;
        float a = outer ? OUTER_ALPHA : INNER_ALPHA;
        buffer.vertex(pose.pose(), (float) local.x, (float) local.y, (float) local.z).color(r, g, b, a).endVertex();
    }
}
