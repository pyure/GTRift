package com.pyure.gtrift.client;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.CommonProxy;
import com.pyure.gtrift.common.item.GTRiftItems;
import com.pyure.gtrift.common.item.RiftShardItem;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Collection;

public class ClientProxy extends CommonProxy {

    private static final ResourceLocation QUALITY_PROPERTY = new ResourceLocation(GTRift.MOD_ID, "quality");

    public ClientProxy(IEventBus modEventBus) {
        super(modEventBus);
        modEventBus.addListener(this::onAddPackFinders);
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onRegisterItemColors);
    }

    // Items are already registered by this point (FMLConstructModEvent, one modloading stage
    // earlier than AddPackFindersEvent's own client resource-pack-repository setup). Each shard
    // item's model is a full copy of rift_shard_dispatcher.json's content (parent/textures/overrides),
    // not a bare {"parent": ...} redirect — BlockModel.getOverrides() only ever reads the "overrides"
    // key from the model actually resolved for the item itself; resolveParents() only links parent
    // model *references* (for texture/element inheritance), it never merges a parent's own overrides
    // list into a child that doesn't declare one. A bare-redirect version of this compiled and loaded
    // fine but silently never re-textured by quality — every shard rendered as the flat "normal"
    // fallback texture from the dispatcher's own "textures" key regardless of actual quality. See
    // RiftShardModelPack's own doc comment for why this needs a GTRift-owned PackResources at all
    // (rather than one hand-authored model JSON file per (player-definable, build-time-unknown) shard
    // type).
    private void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        for (ItemEntry<RiftShardItem> entry : GTRiftItems.allShardItems().values()) {
            ResourceLocation itemId = entry.getId();
            ResourceLocation modelId = new ResourceLocation(itemId.getNamespace(), "item/" + itemId.getPath());
            RiftShardModelPack.addItemModel(modelId, buildDispatcherModel());
        }

        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    "gtrift_dynamic_shard_models",
                    Component.literal("GTRift Dynamic Shard Models"),
                    true,
                    name -> new RiftShardModelPack(),
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
        });
    }

    // Identical content to assets/gtrift/models/item/rift_shard_dispatcher.json — built fresh here
    // (rather than read back from that file) since each dynamically-registered item needs its own,
    // independent copy of the "overrides" list; see onAddPackFinders's doc comment for why a shared
    // "parent" reference alone doesn't work.
    private static JsonObject buildDispatcherModel() {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", "gtrift:item/rift_shard_normal");
        model.add("textures", textures);

        JsonArray overrides = new JsonArray();
        overrides.add(qualityOverride(0.0, "gtrift:item/rift_shard_sparse"));
        overrides.add(qualityOverride(1.0, "gtrift:item/rift_shard_normal"));
        overrides.add(qualityOverride(2.0, "gtrift:item/rift_shard_rich"));
        overrides.add(qualityOverride(3.0, "gtrift:item/rift_shard_extremely_rich"));
        model.add("overrides", overrides);
        return model;
    }

    private static JsonObject qualityOverride(double quality, String model) {
        JsonObject predicate = new JsonObject();
        predicate.addProperty("gtrift:quality", quality);
        JsonObject override = new JsonObject();
        override.add("predicate", predicate);
        override.addProperty("model", model);
        return override;
    }

    // ItemProperties.register requires the item to already exist in the registry, so this waits
    // for client setup rather than running from the constructor (registration happens at
    // FMLConstructModEvent, one lifecycle stage earlier).
    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (RiftShardItem item : shardItems()) {
                // Raw ordinal, not normalized to [0,1] — small integers (0,1,2,3) convert to float
                // with zero rounding error, unlike ordinal/3.0F (which rounds RICH's true 2/3 UP to
                // 0.6666667f, breaking a naive 0.667 threshold in the item models' "overrides"
                // arrays — a real bug this replaced). The model thresholds are 0.0/1.0/2.0/3.0,
                // matching RiftQuality's ordinals 1:1 with no scaling on either side to keep in
                // sync — same idiom vanilla uses for e.g. the "custom_model_data" property, which
                // also returns a raw int cast to float rather than a normalized fraction.
                ItemProperties.register(item, QUALITY_PROPERTY,
                        (stack, level, entity, seed) -> (float) RiftShardItem.getQuality(stack).ordinal());
            }
        });
    }

    // Each RiftShardItem instance carries its own fixed ShardType (see GTRiftItems), so the tint is
    // a direct lookup with no NBT involved — quality (NBT-driven) selects WHICH base texture renders
    // via the model overrides above; the shard type's color (fixed per item, from its JSON) selects
    // its COLOR here.
    private void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> ((RiftShardItem) stack.getItem()).getShardType().color(),
                shardItems());
    }

    private static RiftShardItem[] shardItems() {
        Collection<ItemEntry<RiftShardItem>> entries = GTRiftItems.allShardItems().values();
        RiftShardItem[] items = new RiftShardItem[entries.size()];
        int i = 0;
        for (ItemEntry<RiftShardItem> entry : entries) {
            items[i++] = entry.get();
        }
        return items;
    }
}
