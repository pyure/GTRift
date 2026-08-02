package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.RiftMobPool;
import com.pyure.gtrift.common.data.RiftMobPoolEntry;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pure logic — no world state, template = "empty". Exercises RiftMobPool.eligibleEntries/pickRandom
 * against a fresh, locally-constructed RiftMobPool instance — deliberately NOT the global
 * RiftMobPool.NORMAL/ELITE singletons, matching the lesson learned in RiftEventSpawnerDropTest's own
 * doc comment (mutating those globals mid-test leaked stray mobs into other concurrently-running
 * tests that read the same shared pools).
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftMobPoolEligibilityTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("minecraft:the_nether"));

    private static final RiftMobPoolEntry OVERWORLD_ONLY = new RiftMobPoolEntry(
            EntityType.ZOMBIE, 100, List.of(), Optional.of(Set.of(OVERWORLD)), 1.0, 1.0, 1.0);
    private static final RiftMobPoolEntry NETHER_ONLY = new RiftMobPoolEntry(
            EntityType.SKELETON, 100, List.of(), Optional.of(Set.of(NETHER)), 1.0, 1.0, 1.0);
    private static final RiftMobPoolEntry EVERYWHERE = new RiftMobPoolEntry(
            EntityType.SPIDER, 100, List.of(), Optional.empty(), 1.0, 1.0, 1.0);
    private static final RiftMobPoolEntry NEVER = new RiftMobPoolEntry(
            EntityType.HUSK, 100, List.of(), Optional.of(Set.of()), 1.0, 1.0, 1.0);

    @GameTest(template = "empty")
    public static void eligibleEntriesFiltersToMatchingDimension(GameTestHelper helper) {
        RiftMobPool pool = new RiftMobPool();
        pool.setEntries(List.of(OVERWORLD_ONLY, NETHER_ONLY, EVERYWHERE, NEVER));

        List<RiftMobPoolEntry> overworldEligible = pool.eligibleEntries(OVERWORLD);
        helper.assertTrue(overworldEligible.equals(List.of(OVERWORLD_ONLY, EVERYWHERE)),
                "expected {overworldOnly, everywhere} for OVERWORLD, got %s".formatted(overworldEligible));

        List<RiftMobPoolEntry> netherEligible = pool.eligibleEntries(NETHER);
        helper.assertTrue(netherEligible.equals(List.of(NETHER_ONLY, EVERYWHERE)),
                "expected {netherOnly, everywhere} for NETHER, got %s".formatted(netherEligible));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pickRandomNeverPicksAnIneligibleEntry(GameTestHelper helper) {
        RiftMobPool pool = new RiftMobPool();
        pool.setEntries(List.of(OVERWORLD_ONLY, NETHER_ONLY));
        RandomSource random = RandomSource.create(12345L);

        for (int i = 0; i < 200; i++) {
            RiftMobPoolEntry picked = pool.pickRandom(random, OVERWORLD);
            helper.assertTrue(picked == OVERWORLD_ONLY,
                    "expected only overworldOnly to ever be picked for OVERWORLD, got %s".formatted(picked));
        }

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pickRandomReturnsNullWhenNothingIsEligible(GameTestHelper helper) {
        RiftMobPool pool = new RiftMobPool();
        pool.setEntries(List.of(NETHER_ONLY, NEVER));
        RandomSource random = RandomSource.create(6789L);

        RiftMobPoolEntry picked = pool.pickRandom(random, OVERWORLD);
        helper.assertTrue(picked == null,
                "expected null — no entry is eligible for OVERWORLD, got %s".formatted(picked));

        helper.succeed();
    }
}
