package com.pyure.gtrift;

import com.pyure.gtrift.client.ClientProxy;
import com.pyure.gtrift.common.CommonProxy;
import com.pyure.gtrift.common.config.GTRiftConfig;
import com.pyure.gtrift.common.data.RiftMobPoolLoader;
import com.pyure.gtrift.common.data.ShardTypeLoader;
import com.pyure.gtrift.common.network.GTRiftNetworking;
import com.pyure.gtrift.common.sound.GTRiftSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(GTRift.MOD_ID)
public class GTRift {
    public static final String MOD_ID = "gtrift";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static GTRiftRegistrate REGISTRATE;

    /** Held here for Phase 2 (item registration) to consume; nothing reads this yet. */
    public static ShardTypeLoader.ShardTypeLoadResult SHARD_TYPE_LOAD_RESULT;

    @SuppressWarnings("removal")
    public GTRift() {
        GTRiftConfig.init();
        GTRiftNetworking.register();
        RiftMobPoolLoader.extractDefaultsIfMissing("rift_mobs");
        RiftMobPoolLoader.extractDefaultsIfMissing("rift_elite_mobs");
        // No ShardTypeLoader.extractDefaultsIfMissing() equivalent here anymore — real ore-vein-driven
        // generation (RiftShardOreDatagen.onServerAboutToStart) replaces the old hardcoded "diamond"
        // placeholder. That runs later (ServerAboutToStartEvent, after GT's own ore vein registry
        // populates), so a truly fresh install correctly finds nothing here on its first boot.
        SHARD_TYPE_LOAD_RESULT = ShardTypeLoader.loadAll();
        LOGGER.info("Loaded {} shard type(s) ({} issue(s))",
                SHARD_TYPE_LOAD_RESULT.shardTypes().size(), SHARD_TYPE_LOAD_RESULT.issues().size());
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        REGISTRATE = GTRiftRegistrate.create(MOD_ID, modEventBus);
        GTRiftSounds.register(modEventBus);
        DistExecutor.unsafeRunForDist(
                () -> () -> new ClientProxy(modEventBus),
                () -> () -> new CommonProxy(modEventBus));
    }
}
