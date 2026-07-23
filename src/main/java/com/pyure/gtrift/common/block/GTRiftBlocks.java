package com.pyure.gtrift.common.block;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.machine.RiftBeaconMachine;
import com.pyure.gtrift.common.machine.RiftBeaconTierPredicate;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.common.data.GTBlocks;

import net.minecraft.network.chat.Component;

import static com.gregtechceu.gtceu.api.pattern.Predicates.*;

public class GTRiftBlocks {

    public static MultiblockMachineDefinition RIFT_BEACON;

    public static void init() {
        RIFT_BEACON = GTRift.REGISTRATE
                .multiblock("rift_beacon", RiftBeaconMachine::new)
                .appearanceBlock(() -> GTBlocks.MACHINE_CASING_LV.get())
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
                // which triggers GTMachineModels.<clinit> too early. Runtime rendering uses
                // assets/gtrift/blockstates/rift_beacon.json (visually overridden by appearanceBlock
                // anyway, but a valid model must still exist to avoid a missing-model warning).
                .blockModel((ctx, prov) -> {})
                .tooltips(Component.translatable("gtceu.machine.rift_beacon.tooltip"))
                .register();
    }
}
