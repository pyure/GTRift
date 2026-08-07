package com.pyure.gtrift.client;

import com.pyure.gtrift.GTRift;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Dispatches every currently-known rift column (ClientColumnState.allColumns(), populated by
 * RiftColumnSyncPacket — see plans/rift-multi-column.md Phase 3) to the currently-active
 * {@link RiftColumnRenderer}, once per frame, in a single combined draw call. Rendering cost is flat
 * regardless of column count (no per-tick server involvement, no distance culling needed) — see the
 * spec's explicit call on this.
 *
 * The whole "try a different visual direction" swap point is {@link #ACTIVE_RENDERER} — trying
 * another of the five directions discussed during planning means writing a class and changing this
 * one field, touching nothing else here.
 */
@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RiftColumnVisualRenderer {

    private static RiftColumnRenderer ACTIVE_RENDERER = new JaggedColumnRenderer();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Deliberately NOT AFTER_SKY (what RiftAmbienceRenderer's dome uses) — that stage fires
        // BEFORE terrain/entities render, and since columns don't write depth (depthMask(false)
        // below), anything drawn afterward (terrain, entities, more distant geometry) simply paints
        // over an already-drawn column regardless of true relative distance. Real playtesting
        // confirmed this exactly: a column only ever survived when nothing rendered "behind" it
        // afterward at all (e.g. looking straight up at open sky). AFTER_TRANSLUCENT_BLOCKS fires
        // after every terrain layer AND entities/block entities, so real depth data already exists
        // for this draw to composite against correctly in both directions.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        List<BlockPos> columns = ClientColumnState.allColumns();
        if (columns.isEmpty()) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        // No separate clock to maintain — the world's own game time plus this frame's partial tick
        // is a monotonically increasing value every renderer can drive wobble/animation math from.
        float animationTime = Minecraft.getInstance().level.getGameTime() + event.getPartialTick();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        // The tube's quads have one winding direction and were never solved for arbitrary external
        // viewing angles (unlike the dome, always centered ON the camera, which only ever needs to
        // look right from inside) — disabling cull avoids chasing exact winding correctness for a
        // thin hollow shape. Kept even after the real angle-vanishing root cause turned out to be the
        // AFTER_SKY render-stage timing bug fixed above, not culling — this is still a correct
        // belt-and-suspenders fix for genuinely edge-on viewing angles, just not the main story.
        RenderSystem.disableCull();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // One shared begin/end/upload for every column combined — exactly one draw call per frame
        // regardless of how many columns are active, not one per column.
        PoseStack poseStack = event.getPoseStack();
        for (BlockPos columnPos : columns) {
            poseStack.pushPose();
            ACTIVE_RENDERER.appendGeometry(buffer, poseStack, columnPos, cameraPos, animationTime);
            poseStack.popPose();
        }

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }
}
