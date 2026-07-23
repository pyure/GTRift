package com.pyure.gtrift.common.data;

import com.pyure.gtrift.GTRift;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RiftMobPoolLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");

    private final String directory;
    private final RiftMobPool target;

    public RiftMobPoolLoader(String directory, RiftMobPool target) {
        super(new Gson(), directory);
        this.directory = directory;
        this.target = target;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager resourceManager,
                          ProfilerFiller profiler) {
        List<RiftMobPoolEntry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> fileEntry : data.entrySet()) {
            try {
                JsonObject json = fileEntry.getValue().getAsJsonObject();
                String entityId = GsonHelper.getAsString(json, "entity");
                int weight = GsonHelper.getAsInt(json, "weight");
                EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityId));
                if (entityType == null) {
                    LOGGER.warn("[{}] Unknown entity '{}' in {}, skipping", directory, entityId, fileEntry.getKey());
                    continue;
                }
                entries.add(new RiftMobPoolEntry(entityType, weight));
            } catch (Exception e) {
                LOGGER.warn("[{}] Failed to parse {}, skipping: {}", directory, fileEntry.getKey(), e.getMessage());
            }
        }
        target.setEntries(entries);
        LOGGER.info("Loaded {} entries into rift mob pool '{}'", entries.size(), directory);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new RiftMobPoolLoader("rift_mobs", RiftMobPool.NORMAL));
        event.addListener(new RiftMobPoolLoader("rift_elite_mobs", RiftMobPool.ELITE));
    }
}
