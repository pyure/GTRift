package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.item.GTRiftItems;
import com.pyure.gtrift.common.item.RiftAffinity;
import com.pyure.gtrift.common.item.RiftRichness;
import com.pyure.gtrift.common.item.RiftShardItem;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftShardItemTest {

    @GameTest(template = "empty")
    public static void itemsAreRegistered(GameTestHelper helper) {
        helper.assertTrue(GTRiftItems.FERROUS_RIFT_SHARD.get() != null, "Ferrous Rift Shard not registered");
        helper.assertTrue(GTRiftItems.CONDUCTIVE_RIFT_SHARD.get() != null, "Conductive Rift Shard not registered");
        helper.assertTrue(GTRiftItems.PRECIOUS_RIFT_SHARD.get() != null, "Precious Rift Shard not registered");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void createStackRoundTripsRichness(GameTestHelper helper) {
        ItemStack stack = GTRiftItems.createStack(RiftAffinity.PRECIOUS, RiftRichness.RICH, 3);
        helper.assertTrue(stack.getItem() == GTRiftItems.PRECIOUS_RIFT_SHARD.get(),
                "createStack returned the wrong item for PRECIOUS affinity");
        helper.assertTrue(stack.getCount() == 3, "createStack did not honor the requested count");
        helper.assertTrue(RiftShardItem.getRichness(stack) == RiftRichness.RICH,
                "getRichness did not round-trip the richness set by createStack");

        ItemStack untagged = new ItemStack(GTRiftItems.FERROUS_RIFT_SHARD.get());
        helper.assertTrue(RiftShardItem.getRichness(untagged) == RiftRichness.NORMAL,
                "getRichness did not fall back to NORMAL for an untagged stack");
        helper.succeed();
    }
}
