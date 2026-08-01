package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.item.GTRiftItems;
import com.pyure.gtrift.common.item.RiftQuality;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Requires a structure template at src/test/resources/data/gtrift/structures/singleblock_centrifuge.nbt
 * (a single formed Centrifuge with its item input/output slots accessible at relative position (0,1,0)) —
 * build it via the in-game structure-block workflow documented in src/test/README.md before running this test.
 *
 * The shipped "diamond" default's outputs (specs/rift-shard-types.md) are
 * [raw_diamond chance=0.05, raw_graphite chance=0.30, raw_coal chance=0.10]. At RICH
 * (x4 multiplier, see GTRiftRecipes.QUALITY_MULTIPLIER), effective chances are
 * [0.2, 1.2, 0.4] — only raw_graphite's floor(1.2)=1 whole unit is a guaranteed (100%, non-RNG)
 * output; the other two outputs and graphite's own 20% fractional unit are real chance rolls, which
 * is why this test only asserts on the guaranteed unit rather than the chance-driven ones (see the
 * plan's own Interactive-testing step for how those get eyeballed manually instead). Output slot
 * assignment in NotifiableItemStackHandler is "first empty slot", not a fixed mapping from each
 * Content's position in the recipe's output list — which slot the guaranteed unit lands in varies run
 * to run depending on which of the chance-driven outputs also fired, so this scans every output slot
 * rather than asserting a specific index.
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
    public static void richDiamondShardYieldsGuaranteedGraphite(GameTestHelper helper) {
        SimpleTieredMachine machine = getCentrifuge(helper);
        if (machine == null) return;

        NotifiableItemStackHandler itemIn = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemOut = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP).get(0);

        ItemStack input = GTRiftItems.createStack("diamond", RiftQuality.RICH, 1);
        itemIn.setStackInSlot(0, input);
        itemIn.onContentsChanged();

        helper.runAfterDelay(150, () -> {
            Item rawGraphite = ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtceu", "raw_graphite"));
            int graphiteCount = 0;
            for (int slot = 0; slot < itemOut.getSlots(); slot++) {
                ItemStack stack = itemOut.getStackInSlot(slot);
                if (stack.getItem() == rawGraphite) graphiteCount += stack.getCount();
            }
            helper.assertTrue(graphiteCount >= 1,
                    "expected at least 1x raw_graphite (graphite's guaranteed unit at RICH) somewhere in output, got %d across all slots"
                            .formatted(graphiteCount));
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

        itemIn.setStackInSlot(0, new ItemStack(GTRiftItems.allShardItems().get("diamond").get()));
        itemIn.onContentsChanged();

        helper.runAfterDelay(150, () -> {
            helper.assertTrue(itemOut.getStackInSlot(0).isEmpty() || itemOut.getStackInSlot(0).is(Items.AIR),
                    "an untagged Rift Shard should not have matched any Centrifuge recipe");
            helper.succeed();
        });
    }
}
