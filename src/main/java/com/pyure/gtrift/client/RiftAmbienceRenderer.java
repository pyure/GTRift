package com.pyure.gtrift.client;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.sound.GTRiftSounds;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client-only rendering for the ambience effects driven by ClientAmbienceState.effectiveRamp() — a
 * translucent red world-space overlay (real geometry, not a screen-space tint, so it reads correctly
 * from every camera direction and against the End's flat void backdrop) plus a matching fog-color
 * shift. Racing clouds are deliberately not attempted here — see specs/ambience.md and
 * plans/ambience.md for why that was unscoped from this pass.
 */
@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RiftAmbienceRenderer {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");

    private static final float DOME_RADIUS = 60f;
    // Bumped from 0.35 and darkened/desaturated from the original 0.55/0.05/0.05 after real
    // playtesting reported the translucent red reading as "a touch pink-ish" — a saturated red at low
    // alpha over the bright vanilla sky blends toward pink/magenta rather than a deep ominous red.
    private static final float DOME_MAX_ALPHA = 0.42f;
    private static final float DOME_RED = 0.4f;
    private static final float DOME_GREEN = 0.03f;
    private static final float DOME_BLUE = 0.04f;

    private static final float FOG_RED_TARGET = 0.35f;
    private static final float FOG_GREEN_TARGET = 0.05f;
    private static final float FOG_BLUE_TARGET = 0.04f;

    // Routed through vanilla's own MusicManager (startPlaying/stopPlaying/isPlayingMusic) rather than
    // a raw custom SoundInstance. That earlier approach (real positional 3D falloff via
    // Attenuation.LINEAR) got as far as reliably STARTING but never reliably STOPPED — confirmed via
    // real playtesting + debug logging + reading MusicManager/SoundEngine's own decompiled source:
    // SoundManager.stop(instance) only asks the engine's ticking-sound pump to tell the channel to
    // stop, and that channel never actually reported itself stopped afterward for our looping,
    // streaming track, so the cleanup pass that would remove it (and the instance itself) never fired
    // — it just kept ticking, and audibly playing, indefinitely. MusicManager's own stopPlaying() goes
    // through that same underlying SoundManager.stop() call, but has been provably reliable all
    // session (it's the exact mechanism already used to suppress vanilla music, which never once
    // failed) — using it for our own track too trades away positional falloff (flat volume for anyone
    // in range, like vanilla background music) for actually-working start/stop, per explicit decision.
    private static Music riftMusic;

    private static Music riftMusic() {
        if (riftMusic == null) {
            riftMusic = new Music(Holder.direct(GTRiftSounds.BLOOD_MOON_ADVANCE.get()), 0, 0, true);
        }
        return riftMusic;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean paused = Minecraft.getInstance().isPaused();
        ClientAmbienceState.tick(paused);
        if (paused) return;

        MusicManager musicManager = Minecraft.getInstance().getMusicManager();
        BlockPos musicSource = ClientAmbienceState.musicSourceBeacon();
        if (musicSource != null) {
            // isPlayingMusic guards against restarting from the beginning every single tick — only
            // (re)assert ours when it isn't already the one playing (nothing playing yet, or vanilla's
            // own tick() swapped something else in). stopPlaying() first so whatever's there gets a
            // real teardown before we discard the reference to it, matching vanilla's own tick() doing
            // the same sequence rather than just overwriting silently.
            if (!musicManager.isPlayingMusic(riftMusic())) {
                LOGGER.debug("Asserting rift ambience music for beacon {} (wasn't already playing it)", musicSource);
                musicManager.stopPlaying();
                musicManager.startPlaying(riftMusic());
            }
        } else {
            musicManager.stopPlaying();
        }
    }

    // Backstop for the case a "clear" packet can never arrive because the connection is already
    // gone — see ClientAmbienceState's own doc comment. The 30-tick self-expiry in tick() above would
    // eventually cover this too, but wiping immediately on disconnect avoids even that brief window.
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAmbienceState.clear();
        Minecraft.getInstance().getMusicManager().stopPlaying();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        float ramp = ClientAmbienceState.smoothedRamp();
        if (ramp <= 0f) return;
        renderDome(event.getPoseStack(), ramp);
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        float ramp = ClientAmbienceState.smoothedRamp();
        if (ramp <= 0f) return;
        event.setRed(lerp(event.getRed(), FOG_RED_TARGET, ramp));
        event.setGreen(lerp(event.getGreen(), FOG_GREEN_TARGET, ramp));
        event.setBlue(lerp(event.getBlue(), FOG_BLUE_TARGET, ramp));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /**
     * A cube centered on the camera, drawn from the inside, translucent, depth-tested but not
     * depth-writing so later opaque terrain naturally draws over/occludes it without it occluding
     * anything itself. A cube rather than a true sphere — far simpler to build (6 flat quads vs. a
     * subdivided mesh) and at this alpha/radius the flat-vs-curved difference isn't perceptible for a
     * soft ambient tint.
     */
    private static void renderDome(PoseStack poseStack, float ramp) {
        float alpha = DOME_MAX_ALPHA * ramp;
        float r = DOME_RADIUS;

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        PoseStack.Pose pose = poseStack.last();

        // +X, -X, +Y, -Y, +Z, -Z faces of a cube centered on the origin (the event's PoseStack is
        // already camera-relative for this render stage, same convention vanilla's own sky/cloud
        // rendering uses), each wound so its visible side faces inward toward the camera.
        quad(buffer, pose, r, -r, -r, r, -r, r, r, r, r, r, r, -r, alpha);
        quad(buffer, pose, -r, -r, r, -r, -r, -r, -r, r, -r, -r, r, r, alpha);
        quad(buffer, pose, -r, r, -r, r, r, -r, r, r, r, -r, r, r, alpha);
        quad(buffer, pose, -r, -r, r, r, -r, r, r, -r, -r, -r, -r, -r, alpha);
        quad(buffer, pose, -r, -r, -r, r, -r, -r, r, r, -r, -r, r, -r, alpha);
        quad(buffer, pose, r, -r, r, -r, -r, r, -r, r, r, r, r, r, alpha);

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private static void quad(BufferBuilder buffer, PoseStack.Pose pose,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              float x3, float y3, float z3, float x4, float y4, float z4, float alpha) {
        buffer.vertex(pose.pose(), x1, y1, z1).color(DOME_RED, DOME_GREEN, DOME_BLUE, alpha).endVertex();
        buffer.vertex(pose.pose(), x2, y2, z2).color(DOME_RED, DOME_GREEN, DOME_BLUE, alpha).endVertex();
        buffer.vertex(pose.pose(), x3, y3, z3).color(DOME_RED, DOME_GREEN, DOME_BLUE, alpha).endVertex();
        buffer.vertex(pose.pose(), x4, y4, z4).color(DOME_RED, DOME_GREEN, DOME_BLUE, alpha).endVertex();
    }
}
