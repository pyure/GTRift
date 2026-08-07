package com.pyure.gtrift.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * One rift column's visual, pluggable so the five directions discussed during planning
 * (plans/rift-multi-column.md) can each become their own implementation without touching the
 * dispatcher ({@link RiftColumnVisualRenderer}) that calls them. Vertex-emission only — no
 * begin()/end()/shader/blend setup here, the dispatcher owns that once per frame for every column
 * combined, not once per column.
 *
 * Implementations are expected to translate {@code poseStack} by {@code columnPos - cameraPos}
 * themselves (the dispatcher only pushes/pops the pose around the call, it doesn't translate) before
 * emitting any vertex — the event's PoseStack is already camera-relative for this render stage, same
 * convention {@code RiftAmbienceRenderer}'s dome uses.
 */
public interface RiftColumnRenderer {

    void appendGeometry(BufferBuilder buffer, PoseStack poseStack, BlockPos columnPos, Vec3 cameraPos,
                         float animationTime);
}
