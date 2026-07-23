package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.item.GTRiftItems;
import com.pyure.gtrift.common.item.RiftAffinity;
import com.pyure.gtrift.common.item.RiftRichness;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Requires a structure template at src/test/resources/data/gtrift/structures/singleblock_centrifuge.nbt
 * (a single formed Centrifuge with its item input/output slots accessible at relative position (0,1,0)) —
 * build it via the in-game structure-block workflow documented in src/test/README.md before running this test.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftShardCentrifugeRecipeTest {

    private static SimpleTieredMachine getCentrifuge(GameTestHelper helper) {
        BlockEntity holder = helper.getBlockEntity(new BlockPos(0, 1, 0));
        if (!(holder instanceof MetaMachineBlockEntity metaMachineBlockEntity)) {
            helper.fail("wrong block at relative pos [0,1,0]!");
            return null;
        }
        MetaMachine machine = metaMachineBlockEntity.getMetaMachine();
        if (!(machine instanceof SimpleTieredMachine centrifuge)) {
            helper.fail("wrong machine in MetaMachineBlockEntity!");
            return null;
        }
        return centrifuge;
    }

    @GameTest(template = "singleblock_centrifuge", timeoutTicks = 200)
    public static void richFerrousShardCentrifuges(GameTestHelper helper) {
        SimpleTieredMachine machine = getCentrifuge(helper);
        if (machine == null) return;

        NotifiableItemStackHandler itemIn = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemOut = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP).get(0);

        ItemStack input = GTRiftItems.createStack(RiftAffinity.FERROUS, RiftRichness.RICH, 1);
        itemIn.setStackInSlot(0, input);
        itemIn.onContentsChanged();

        helper.runAfterDelay(150, () -> {
            ItemStack ironDust = itemOut.getStackInSlot(0);
            ItemStack steelDust = itemOut.getStackInSlot(1);
            helper.assertTrue(ironDust.getCount() == 4,
                    "expected 4 Iron dust, got %d".formatted(ironDust.getCount()));
            helper.assertTrue(steelDust.getCount() == 4,
                    "expected 4 Steel dust, got %d".formatted(steelDust.getCount()));
            helper.succeed();
        });
    }

    @GameTest(template = "singleblock_centrifuge", timeoutTicks = 200)
    public static void untaggedShardDoesNotMatchAnyRecipe(GameTestHelper helper) {
        SimpleTieredMachine machine = getCentrifuge(helper);
        if (machine == null) return;

        NotifiableItemStackHandler itemIn = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemOut = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP).get(0);

        itemIn.setStackInSlot(0, new ItemStack(GTRiftItems.FERROUS_RIFT_SHARD.get()));
        itemIn.onContentsChanged();

        helper.runAfterDelay(150, () -> {
            helper.assertTrue(itemOut.getStackInSlot(0).isEmpty() || itemOut.getStackInSlot(0).is(Items.AIR),
                    "an untagged Rift Shard should not have matched any Centrifuge recipe");
            helper.succeed();
        });
    }
}
