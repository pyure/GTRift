package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.item.GTRiftItems;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Spawns a tagged zombie (mirroring RiftEventSpawner's gtrift_mob/gtrift_tier tagging) and an
 * untagged plain zombie well apart from each other, kills both, and verifies only the tagged one's
 * drops were replaced with a Rift Shard — the untagged one is left to vanilla loot entirely.
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

        Zombie untagged = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, UNTAGGED_POS);

        tagged.kill();
        untagged.kill();

        helper.runAfterDelay(5, () -> {
            List<ItemEntity> taggedDrops = nearbyItems(helper, TAGGED_POS);
            helper.assertTrue(taggedDrops.size() == 1,
                    "expected exactly 1 item entity from the tagged mob, got %d".formatted(taggedDrops.size()));
            ItemEntity dropped = taggedDrops.get(0);
            helper.assertTrue(dropped.getItem().getCount() == 1, "expected exactly 1 Rift Shard, got %d"
                    .formatted(dropped.getItem().getCount()));
            helper.assertTrue(GTRiftItems.FERROUS_RIFT_SHARD.get() == dropped.getItem().getItem()
                            || GTRiftItems.CONDUCTIVE_RIFT_SHARD.get() == dropped.getItem().getItem()
                            || GTRiftItems.PRECIOUS_RIFT_SHARD.get() == dropped.getItem().getItem(),
                    "tagged mob's drop was not a Rift Shard: %s".formatted(dropped.getItem()));

            List<ItemEntity> untaggedDrops = nearbyItems(helper, UNTAGGED_POS);
            boolean anyRiftShard = untaggedDrops.stream().anyMatch(item -> item.getItem().getItem() == GTRiftItems.FERROUS_RIFT_SHARD.get()
                    || item.getItem().getItem() == GTRiftItems.CONDUCTIVE_RIFT_SHARD.get()
                    || item.getItem().getItem() == GTRiftItems.PRECIOUS_RIFT_SHARD.get());
            helper.assertTrue(!anyRiftShard, "untagged mob's drops were affected by the Rift Shard loot listener");

            helper.succeed();
        });
    }

    private static List<ItemEntity> nearbyItems(GameTestHelper helper, BlockPos relativePos) {
        AABB bounds = new AABB(helper.absolutePos(relativePos)).inflate(2.0);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds);
    }
}
