package com.pyure.gtrift.common.data;

import com.pyure.gtrift.GTRift;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Surfaces GTRift's own config/data load issues to OP players on join as a single combined line —
 * the per-issue detail is already in the log (each loader logs its own issues via LOGGER.warn/error as
 * they're found), so chat only needs to say something failed and point there, not repeat it. Combines
 * two independently-lifecycled sources: shard-type load issues (GTRift.SHARD_TYPE_LOAD_RESULT, loaded
 * once at mod construction — new items can't register after) and mob-pool load issues
 * (RiftMobPool.NORMAL/ELITE.issues(), refreshed on every resource reload, so this always reflects the
 * *current* config-folder state, not whatever it was at the previous join). Fires on every join while
 * any underlying issue persists; there's no "dismiss forever" mechanism, deliberately (matches
 * KubeJS's own "stays annoying until you fix it" behavior).
 */
@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GTRiftLoadReporter {

    private static final int OP_PERMISSION_LEVEL = 2;

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(OP_PERMISSION_LEVEL)) return;

        List<String> issues = new ArrayList<>();
        issues.addAll(GTRift.SHARD_TYPE_LOAD_RESULT.issues());
        issues.addAll(RiftMobPool.NORMAL.issues());
        issues.addAll(RiftMobPool.ELITE.issues());

        buildChatMessage(issues)
                .ifPresent(message -> player.sendSystemMessage(
                        Component.literal(message).withStyle(ChatFormatting.RED)));
    }

    /** Public (not private) — pure, so a GameTest can exercise this directly with a synthetic issues list. */
    public static Optional<String> buildChatMessage(List<String> issues) {
        if (issues.isEmpty()) return Optional.empty();
        return Optional.of("GTRift: %d issue(s) loading config, see log".formatted(issues.size()));
    }
}
