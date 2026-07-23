package com.pyure.gtrift.common.item;

import com.pyure.gtrift.GTRift;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

public class GTRiftItems {

    public static final ItemEntry<RiftShardItem> FERROUS_RIFT_SHARD = GTRift.REGISTRATE
            .item("ferrous_rift_shard", RiftShardItem::new)
            .register();
    public static final ItemEntry<RiftShardItem> CONDUCTIVE_RIFT_SHARD = GTRift.REGISTRATE
            .item("conductive_rift_shard", RiftShardItem::new)
            .register();
    public static final ItemEntry<RiftShardItem> PRECIOUS_RIFT_SHARD = GTRift.REGISTRATE
            .item("precious_rift_shard", RiftShardItem::new)
            .register();

    private static final Map<RiftAffinity, ItemEntry<RiftShardItem>> RIFT_SHARDS = new EnumMap<>(RiftAffinity.class);
    static {
        RIFT_SHARDS.put(RiftAffinity.FERROUS, FERROUS_RIFT_SHARD);
        RIFT_SHARDS.put(RiftAffinity.CONDUCTIVE, CONDUCTIVE_RIFT_SHARD);
        RIFT_SHARDS.put(RiftAffinity.PRECIOUS, PRECIOUS_RIFT_SHARD);
    }

    public static ItemStack createStack(RiftAffinity affinity, RiftRichness richness, int count) {
        ItemStack stack = new ItemStack(RIFT_SHARDS.get(affinity).get(), count);
        RiftShardItem.setRichness(stack, richness);
        return stack;
    }

    public static void init() {}
}
