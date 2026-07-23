package com.pyure.gtrift.common;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.pyure.gtrift.common.block.GTRiftBlocks;
import com.pyure.gtrift.common.item.GTRiftItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

public class CommonProxy {
    public CommonProxy(IEventBus modEventBus) {
        modEventBus.addListener(this::onConstruct);
        // GTCEuAPI.RegisterEvent fires on the mod bus immediately before
        // GTRegistries.MACHINES.freeze() — the only valid window for addon machine registration.
        modEventBus.addGenericListener(MachineDefinition.class,
                (GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) -> GTRiftBlocks.init());
    }

    private void onConstruct(FMLConstructModEvent event) {
        event.enqueueWork(GTRiftItems::init);
    }
}
