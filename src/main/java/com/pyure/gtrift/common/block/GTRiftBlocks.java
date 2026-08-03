package com.pyure.gtrift.common.block;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.machine.RiftBeaconMachine;
import com.pyure.gtrift.common.machine.RiftBeaconRenderState;
import com.pyure.gtrift.common.machine.RiftBeaconTierPredicate;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.common.data.GTBlocks;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

import static com.gregtechceu.gtceu.api.pattern.Predicates.*;

public class GTRiftBlocks {

    public static MultiblockMachineDefinition RIFT_BEACON;

    public static void init() {
        RIFT_BEACON = GTRift.REGISTRATE
                .multiblock("rift_beacon", RiftBeaconMachine::new)
                .modelProperty(RiftBeaconRenderState.PROPERTY, RiftBeaconRenderState.INACTIVE)
                // Structure: bottom layer (Y=0) is the controller + a fixed ULV-casing wall (with up to
                // one energy hatch swapped in anywhere); a 2-tall glass/obsidian viewing column (Y=1-2);
                // an HV-casing tier ring (Y=3, exactly 4 positions — this, not the bulk wall, is what
                // RiftBeaconTierPredicate reads to determine RiftBeaconMachine.tier); a 4-tall redstone
                // spire on top (Y=4-7). The wall being a single fixed casing (rather than any-tier, as
                // it used to be) is deliberate — see gt-addon-guide.md's texture_overrides entry for why
                // a per-instance-dynamic wall texture isn't achievable without either registering one
                // block per tier or real custom client rendering; this design sidesteps that entirely.
                //
                // Aisle order is Z, but reversed (last aisle = Z=0) and each aisle's rows are Y
                // (ascending, row 0 = Y=0) — derived from cross-referencing the previous pattern's
                // controller position against its captured test structure, not independently confirmed
                // against FactoryBlockPattern's own source. If a beacon built to this exact layout
                // doesn't form, this is the first thing to suspect (see plans/beacon-structure-redesign.md).
                .pattern(definition -> FactoryBlockPattern.start()
                        .aisle("WWW", "#G#", "#G#", "#H#", "###", "###", "###", "###") // Z=2
                        .aisle("WWW", "GOG", "GOG", "HRH", "#R#", "#R#", "#R#", "#R#") // Z=1
                        .aisle("WSW", "#G#", "#G#", "#H#", "###", "###", "###", "###") // Z=0
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('W', blocks(GTBlocks.MACHINE_CASING_ULV.get()).setMinGlobalLimited(7)
                                .or(abilities(PartAbility.INPUT_ENERGY)
                                        .setMinGlobalLimited(0).setMaxGlobalLimited(1)))
                        .where('H', RiftBeaconTierPredicate.riftCasings()
                                .setMinGlobalLimited(4).setMaxGlobalLimited(4))
                        .where('G', blocks(Blocks.GLASS))
                        .where('O', blocks(Blocks.OBSIDIAN))
                        .where('R', blocks(Blocks.REDSTONE_BLOCK))
                        .where('#', air())
                        .build())
                // no-op blockModel: prevents MachineBuilder.register() from calling simpleModel()
                // which triggers GTMachineModels.<clinit> too early. Runtime rendering uses the
                // hand-authored assets/gtrift/blockstates/rift_beacon.json + models/block/machine/
                // rift_beacon.json (no datagen in this project) — this callback would only matter if
                // a real `runData` task ever generated those files instead.
                .blockModel((ctx, prov) -> {})
                .tooltips(Component.translatable("gtceu.machine.rift_beacon.tooltip"))
                .register();
    }
}
