package com.pyure.gtrift.common.item;

import com.pyure.gtrift.common.data.ShardType;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class RiftShardItem extends Item {

    private static final String RICHNESS_TAG = "Richness";

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
        RiftRichness richness = getRichness(stack);
        String key = "item.gtrift." + shardType.sanitizedId() + "_shard";
        String fallback = shardType.defaultDisplayName() + " Rift Shard";
        return Component.translatable(richness.getTranslationKey())
                .append(" ")
                .append(Component.translatableWithFallback(key, fallback));
    }

    public static RiftRichness getRichness(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(RICHNESS_TAG)) {
            return RiftRichness.NORMAL;
        }
        try {
            return RiftRichness.valueOf(tag.getString(RICHNESS_TAG));
        } catch (IllegalArgumentException e) {
            return RiftRichness.NORMAL;
        }
    }

    public static void setRichness(ItemStack stack, RiftRichness richness) {
        stack.getOrCreateTag().putString(RICHNESS_TAG, richness.name());
    }
}
