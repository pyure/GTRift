package com.pyure.gtrift.common.block;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.machine.RiftBeaconMachine;
import com.pyure.gtrift.common.machine.RiftBeaconRenderState;
import com.pyure.gtrift.common.machine.RiftBeaconTierPredicate;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

import net.minecraft.network.chat.Component;

import static com.gregtechceu.gtceu.api.pattern.Predicates.*;

public class GTRiftBlocks {

    public static MultiblockMachineDefinition RIFT_BEACON;

    public static void init() {
        RIFT_BEACON = GTRift.REGISTRATE
                .multiblock("rift_beacon", RiftBeaconMachine::new)
                .modelProperty(RiftBeaconRenderState.PROPERTY, RiftBeaconRenderState.INACTIVE)
                .pattern(definition -> FactoryBlockPattern.start()
                        .aisle("CCC", "CCC", "CCC")
                        .aisle("CCC", "C#C", "CCC")
                        .aisle("CCC", "CSC", "CCC")
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('C', RiftBeaconTierPredicate.riftCasings()
                                .setMinGlobalLimited(20)
                                .or(abilities(PartAbility.INPUT_ENERGY)
                                        .setMinGlobalLimited(1).setMaxGlobalLimited(4)))
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
