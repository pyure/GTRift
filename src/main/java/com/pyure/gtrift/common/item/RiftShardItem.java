package com.pyure.gtrift.common.item;

import com.pyure.gtrift.common.data.ShardType;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class RiftShardItem extends Item {

    private static final String QUALITY_TAG = "Quality";

    private final ShardType shardType;

    public RiftShardItem(ShardType shardType, Properties properties) {
        super(properties);
        this.shardType = shardType;
    }

    /** Fixed per registered item (one RiftShardItem instance per shard type) — never read from NBT. */
    public ShardType getShardType() {
        return shardType;
    }

    /**
     * A real lang-file entry (see assets/gtrift/lang/en_us.json) is shipped for curated types like the
     * default "diamond" and takes priority when present, for proper translation; the fallback here is
     * what a genuinely player-defined type (no lang entry possible — its id doesn't exist until the
     * player writes the config file) actually shows in-game.
     */
    @Override
    public Component getName(ItemStack stack) {
        RiftQuality quality = getQuality(stack);
        String key = "item.gtrift." + shardType.sanitizedId() + "_shard";
        String fallback = shardType.defaultDisplayName() + " Rift Shard";
        return Component.translatable(quality.getTranslationKey())
                .append(" ")
                .append(Component.translatableWithFallback(key, fallback));
    }

    public static RiftQuality getQuality(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(QUALITY_TAG)) {
            return RiftQuality.NORMAL;
        }
        try {
            return RiftQuality.valueOf(tag.getString(QUALITY_TAG));
        } catch (IllegalArgumentException e) {
            return RiftQuality.NORMAL;
        }
    }

    public static void setQuality(ItemStack stack, RiftQuality quality) {
        stack.getOrCreateTag().putString(QUALITY_TAG, quality.name());
    }
}
