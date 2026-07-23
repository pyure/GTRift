package com.pyure.gtrift;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import net.minecraftforge.eventbus.api.IEventBus;

public class GTRiftRegistrate extends GTRegistrate {
    protected GTRiftRegistrate(String modid) {
        super(modid);
    }

    public static GTRiftRegistrate create(String modid, IEventBus modEventBus) {
        GTRiftRegistrate instance = new GTRiftRegistrate(modid);
        instance.registerEventListeners(modEventBus);
        return instance;
    }
}
