package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.RiftDropEntry;
import com.pyure.gtrift.common.item.GTRiftItems;
import com.pyure.gtrift.common.item.RiftRichness;
import com.pyure.gtrift.common.item.RiftShardItem;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * RiftLootDrops reads mob-death loot from the mob's own "gtrift_drops" NBT list (populated by
 * RiftEventSpawner at spawn time, already filtered to tier-eligible entries — see
 * RiftEventSpawnerDropTest for that filtering). These tests set "gtrift_drops" directly on
 * manually-spawned mobs, bypassing the mob pool/spawner entirely, mirroring this project's existing
 * convention of tagging gtrift_mob/gtrift_tier by hand for loot-listener tests.
 *
 * Only "diamond" (the shipped default shard type) is guaranteed to be a real registered item at test
 * time, so every drop entry here uses that type id — tests that used to distinguish drops by affinity
 * distinguish them by count/richness instead (RiftLootDrops always creates one ItemEntity per drop
 * entry, so two entries never merge into one, even with identical item+count+NBT).
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftLootDropTest {

    private static final BlockPos TAGGED_POS = new BlockPos(1, 1, 1);
    private static final BlockPos UNTAGGED_POS = new BlockPos(10, 1, 1);

    @GameTest(template = "empty")
    public static void taggedMobDropsShardUntaggedDropsVanillaLoot(GameTestHelper helper) {
        Zombie tagged = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, TAGGED_POS);
        tagged.getPersistentData().putBoolean("gtrift_mob", true);
        tagged.getPersistentData().putInt("gtrift_tier", GTValues.LV);
        tagged.getPersistentData().put("gtrift_drops", dropsTag(
                new RiftDropEntry("diamond", RiftRichness.NORMAL, GTValues.ULV, 1.0, 1, 1, 1.0, 1.0)));

        Zombie untagged = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, UNTAGGED_POS);

        tagged.kill();
        untagged.kill();

        helper.runAfterDelay(5, () -> {
            List<ItemEntity> taggedDrops = nearbyItems(helper, TAGGED_POS);
            helper.assertTrue(taggedDrops.size() == 1,
                    "expected exactly 1 item entity from the tagged mob, got %d".formatted(taggedDrops.size()));
            ItemEntity dropped = taggedDrops.get(0);
            helper.assertTrue(dropped.getItem().getCount() == 1,
                    "expected exactly 1 Rift Shard, got %d".formatted(dropped.getItem().getCount()));
            helper.assertTrue(diamondShardItem() == dropped.getItem().getItem(),
                    "tagged mob's drop was not a Diamond Rift Shard: %s".formatted(dropped.getItem()));

            List<ItemEntity> untaggedDrops = nearbyItems(helper, UNTAGGED_POS);
            boolean anyRiftShard = untaggedDrops.stream().anyMatch(RiftLootDropTest::isRiftShard);
            helper.assertTrue(!anyRiftShard, "untagged mob's drops were affected by the Rift Shard loot listener");

            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void zeroChanceEntryNeverDropsButVanillaLootIsStillCleared(GameTestHelper helper) {
        Zombie mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, TAGGED_POS);
        mob.getPersistentData().putBoolean("gtrift_mob", true);
        mob.getPersistentData().putInt("gtrift_tier", GTValues.LV);
        mob.getPersistentData().put("gtrift_drops", dropsTag(
                new RiftDropEntry("diamond", RiftRichness.NORMAL, GTValues.ULV, 0.0, 1, 1, 1.0, 1.0)));

        mob.kill();

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(nearbyItems(helper, TAGGED_POS).isEmpty(),
                    "a chance=0.0 drop entry should never produce an item, and vanilla loot should stay cleared");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void multipleEntriesCanDropSimultaneously(GameTestHelper helper) {
        Zombie mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, TAGGED_POS);
        mob.getPersistentData().putBoolean("gtrift_mob", true);
        mob.getPersistentData().putInt("gtrift_tier", GTValues.LV);

        ListTag drops = dropsTag(
                new RiftDropEntry("diamond", RiftRichness.NORMAL, GTValues.ULV, 1.0, 1, 1, 1.0, 1.0));
        drops.add(new RiftDropEntry("diamond", RiftRichness.RICH, GTValues.ULV, 1.0, 2, 2, 1.0, 1.0)
                .toNbt());
        mob.getPersistentData().put("gtrift_drops", drops);

        mob.kill();

        helper.runAfterDelay(5, () -> {
            List<ItemEntity> results = nearbyItems(helper, TAGGED_POS);
            helper.assertTrue(results.size() == 2,
                    "expected 2 separate item entities from 2 always-triggering drop entries, got %d"
                            .formatted(results.size()));
            helper.assertTrue(results.stream().anyMatch(i -> i.getItem().getItem() == diamondShardItem()
                            && i.getItem().getCount() == 1 && RiftShardItem.getRichness(i.getItem()) == RiftRichness.NORMAL),
                    "missing the expected 1x Normal Diamond Rift Shard drop");
            helper.assertTrue(results.stream().anyMatch(i -> i.getItem().getItem() == diamondShardItem()
                            && i.getItem().getCount() == 2 && RiftShardItem.getRichness(i.getItem()) == RiftRichness.RICH),
                    "missing the expected 2x Rich Diamond Rift Shard drop");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void eliteMultipliersScaleChanceAndAmount(GameTestHelper helper) {
        // chance 0.4 * eliteChanceMultiplier 2.5 = 1.0 -> guaranteed only because gtrift_elite is set.
        Zombie mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, TAGGED_POS);
        mob.getPersistentData().putBoolean("gtrift_mob", true);
        mob.getPersistentData().putInt("gtrift_tier", GTValues.LV);
        mob.getPersistentData().putBoolean("gtrift_elite", true);
        mob.getPersistentData().put("gtrift_drops", dropsTag(
                new RiftDropEntry("diamond", RiftRichness.NORMAL, GTValues.ULV, 0.4, 1, 1, 2.5, 3.0)));

        mob.kill();

        helper.runAfterDelay(5, () -> {
            List<ItemEntity> results = nearbyItems(helper, TAGGED_POS);
            helper.assertTrue(results.size() == 1,
                    "expected exactly 1 item entity (chance*eliteChanceMultiplier clamps to 1.0), got %d"
                            .formatted(results.size()));
            helper.assertTrue(results.get(0).getItem().getCount() == 3,
                    "expected amount 1*eliteAmountMultiplier(3.0) = 3, got %d"
                            .formatted(results.get(0).getItem().getCount()));
            helper.succeed();
        });
    }

    private static ListTag dropsTag(RiftDropEntry entry) {
        ListTag tag = new ListTag();
        tag.add(entry.toNbt());
        return tag;
    }

    private static boolean isRiftShard(ItemEntity item) {
        return item.getItem().getItem() == diamondShardItem();
    }

    private static net.minecraft.world.item.Item diamondShardItem() {
        return GTRiftItems.allShardItems().get("diamond").get();
    }

    private static List<ItemEntity> nearbyItems(GameTestHelper helper, BlockPos relativePos) {
        AABB bounds = new AABB(helper.absolutePos(relativePos)).inflate(2.0);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds);
    }
}
