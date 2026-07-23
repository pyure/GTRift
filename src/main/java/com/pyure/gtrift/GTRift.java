package com.pyure.gtrift;

import com.pyure.gtrift.client.ClientProxy;
import com.pyure.gtrift.common.CommonProxy;
import com.pyure.gtrift.common.config.GTRiftConfig;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(GTRift.MOD_ID)
public class GTRift {
    public static final String MOD_ID = "gtrift";
    public static GTRiftRegistrate REGISTRATE;

    @SuppressWarnings("removal")
    public GTRift() {
        GTRiftConfig.init();
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        REGISTRATE = GTRiftRegistrate.create(MOD_ID, modEventBus);
        DistExecutor.unsafeRunForDist(
                () -> () -> new ClientProxy(modEventBus),
                () -> () -> new CommonProxy(modEventBus));
    }
}
