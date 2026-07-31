package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.item.GTRiftItems;
import com.pyure.gtrift.common.item.RiftRichness;
import com.pyure.gtrift.common.item.RiftShardItem;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Asserts against the dynamically-registered shard-type item map (see GTRiftItems) rather than fixed
 * fields — there's no compile-time-known set of shard items anymore, only whatever loaded from
 * config/gtrift/rift_shard_types on this launch. The shipped "diamond" default (see ShardTypeLoader)
 * is the one id these tests can rely on existing.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftShardItemTest {

    @GameTest(template = "empty")
    public static void itemsAreRegistered(GameTestHelper helper) {
        helper.assertTrue(!GTRiftItems.allShardItems().isEmpty(), "expected at least 1 shard type item registered");
        helper.assertTrue(GTRiftItems.allShardItems().containsKey("diamond"),
                "expected the shipped 'diamond' default to be registered, got %s"
                        .formatted(GTRiftItems.allShardItems().keySet()));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void createStackRoundTripsRichness(GameTestHelper helper) {
        ItemStack stack = GTRiftItems.createStack("diamond", RiftRichness.RICH, 3);
        helper.assertTrue(stack.getItem() == GTRiftItems.allShardItems().get("diamond").get(),
                "createStack returned the wrong item for 'diamond'");
        helper.assertTrue(stack.getCount() == 3, "createStack did not honor the requested count");
        helper.assertTrue(RiftShardItem.getRichness(stack) == RiftRichness.RICH,
                "getRichness did not round-trip the richness set by createStack");

        ItemStack untagged = new ItemStack(GTRiftItems.allShardItems().get("diamond").get());
        helper.assertTrue(RiftShardItem.getRichness(untagged) == RiftRichness.NORMAL,
                "getRichness did not fall back to NORMAL for an untagged stack");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void createStackReturnsEmptyStackForUnknownType(GameTestHelper helper) {
        ItemStack stack = GTRiftItems.createStack("does_not_exist", RiftRichness.NORMAL, 1);
        helper.assertTrue(stack.isEmpty(),
                "expected an empty stack (logged, not an NPE/crash) for an unknown shard type id, got %s"
                        .formatted(stack));
        helper.succeed();
    }
}
