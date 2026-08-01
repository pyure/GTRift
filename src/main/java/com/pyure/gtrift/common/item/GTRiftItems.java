package com.pyure.gtrift.common.item;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.ShardType;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GTRiftItems {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");

    /** Keyed by ShardType.sanitizedId(). Populated once by init(), from Phase 1's loaded shard types. */
    private static final Map<String, ItemEntry<RiftShardItem>> RIFT_SHARDS = new LinkedHashMap<>();

    public static void init() {
        for (ShardType type : GTRift.SHARD_TYPE_LOAD_RESULT.shardTypes()) {
            ItemEntry<RiftShardItem> entry = GTRift.REGISTRATE
                    .item(type.sanitizedId() + "_shard", p -> new RiftShardItem(type, p))
                    .register();
            RIFT_SHARDS.put(type.sanitizedId(), entry);
        }
        LOGGER.info("Registered {} rift shard item(s)", RIFT_SHARDS.size());
    }

    /** Unmodifiable — for ClientProxy to iterate when wiring rendering for every registered shard item. */
    public static Map<String, ItemEntry<RiftShardItem>> allShardItems() {
        return Collections.unmodifiableMap(RIFT_SHARDS);
    }

    public static ItemStack createStack(String shardTypeId, RiftQuality quality, int count) {
        ItemEntry<RiftShardItem> entry = RIFT_SHARDS.get(shardTypeId);
        if (entry == null) {
            LOGGER.warn("Unknown shard type id '{}', cannot create a stack for it", shardTypeId);
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(entry.get(), count);
        RiftShardItem.setQuality(stack, quality);
        return stack;
    }
}
