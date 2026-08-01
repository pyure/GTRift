package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.block.GTRiftBlocks;
import com.pyure.gtrift.common.machine.BeaconState;
import com.pyure.gtrift.common.machine.RiftBeaconMachine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Regression test for a real crash: opening the beacon controller's GUI while the multiblock has
 * never formed threw ArrayIndexOutOfBoundsException from computeChargeTarget() indexing GTValues.VA
 * by selectedDifficultyTier, which defaults to -1 until onStructureFormed() first runs. Places a lone
 * controller block (no surrounding casings, so it never forms) and replicates the exact real crash
 * pathway — createUIWidget() then writeInitialData(), the same call chain LDLib's UIFactory.openUI
 * uses when a player right-clicks the block — rather than just re-testing computeChargeTarget() in
 * isolation, so a future regression in this exact wiring would be caught here too.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftBeaconUnformedGuiTest {

    @GameTest(template = "empty")
    public static void openingGuiOnNeverFormedControllerDoesNotCrash(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, GTRiftBlocks.RIFT_BEACON.getBlock());

        BlockEntity holder = helper.getBlockEntity(pos);
        if (!(holder instanceof MetaMachineBlockEntity metaMachineBlockEntity)) {
            helper.fail("expected a MetaMachineBlockEntity at " + pos);
            return;
        }
        MetaMachine machine = metaMachineBlockEntity.getMetaMachine();
        if (!(machine instanceof RiftBeaconMachine beacon)) {
            helper.fail("expected a RiftBeaconMachine, got " + machine);
            return;
        }

        helper.assertTrue(!beacon.isFormed(), "expected a lone controller block to never be formed");
        helper.assertTrue(beacon.state == BeaconState.IDLE, "expected IDLE, got " + beacon.state);
        helper.assertTrue(beacon.selectedDifficultyTier < 0,
                "expected selectedDifficultyTier to still be -1 (never configured), got "
                        + beacon.selectedDifficultyTier);

        try {
            beacon.createUIWidget().writeInitialData(new FriendlyByteBuf(Unpooled.buffer()));
        } catch (ArrayIndexOutOfBoundsException e) {
            helper.fail("opening the GUI on a never-formed controller threw " + e);
            return;
        }

        helper.succeed();
    }
}
