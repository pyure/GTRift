package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.machine.RiftEliteTracker;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraft.server.level.ServerBossEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Doesn't need the beacon structure — RiftEliteTracker's boss-bar tracking is independent of any
 * beacon, mirroring how it behaves in practice (an elite's boss bar keeps working after its beacon
 * is destroyed, per spec).
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftEliteTrackerTest {

    private static final Logger LOGGER = LogManager.getLogger("gtrift-test");

    @GameTest(template = "empty")
    public static void bossBarTracksHealthAndClearsOnDeath(GameTestHelper helper) {
        try {
            runTest(helper);
        } catch (Throwable t) {
            // GameTestInfo.startTest() swallows this and hands it to ReportGameListener, whose own
            // spawnBeacon() NPEs on a null structure-block pos before it ever logs/shows the real
            // message (confirmed via decompiled source — a pre-existing vanilla/Forge bug, not ours).
            // Log the real cause here first so it survives that crash.
            LOGGER.error("bossBarTracksHealthAndClearsOnDeath threw before GameTest's own reporting could show it", t);
            throw t;
        }
    }

    private static void runTest(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        ServerBossEvent bossEvent =
                new ServerBossEvent(Component.literal("Test Elite"), BossEvent.BossBarColor.RED,
                        BossEvent.BossBarOverlay.NOTCHED_10);
        RiftEliteTracker.track(zombie, bossEvent);

        float maxHealth = zombie.getMaxHealth();
        boolean hurtApplied = zombie.hurt(helper.getLevel().damageSources().generic(), maxHealth * 0.5F);
        LOGGER.info("setup: maxHealth={}, hurtApplied={}, healthAfterHurt={}, trackedCount={}",
                maxHealth, hurtApplied, zombie.getHealth(), RiftEliteTracker.trackedCount());

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    float expectedProgress = zombie.getHealth() / zombie.getMaxHealth();
                    LOGGER.info("checkpoint 1: health={}, maxHealth={}, expectedProgress={}, bossBarProgress={}",
                            zombie.getHealth(), zombie.getMaxHealth(), expectedProgress, bossEvent.getProgress());
                    helper.assertTrue(Math.abs(bossEvent.getProgress() - expectedProgress) < 0.01F,
                            "boss bar progress (%.3f) did not reflect damaged health ratio (%.3f)"
                                    .formatted(bossEvent.getProgress(), expectedProgress));
                    zombie.kill();
                    LOGGER.info("checkpoint 1: killed zombie, isAlive={}", zombie.isAlive());
                })
                .thenExecuteAfter(2, () -> {
                    LOGGER.info("checkpoint 2: isAlive={}, bossBarPlayers={}, trackedCount={}",
                            zombie.isAlive(), bossEvent.getPlayers().size(), RiftEliteTracker.trackedCount());
                    helper.assertTrue(bossEvent.getPlayers().isEmpty(),
                            "boss bar still had players registered after the tracked entity died");
                    helper.assertTrue(RiftEliteTracker.trackedCount() == 0,
                            "tracker entry was not removed after the tracked entity died");
                })
                .thenSucceed();
    }
}
