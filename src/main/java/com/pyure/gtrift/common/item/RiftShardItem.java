package com.pyure.gtrift.common.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class RiftShardItem extends Item {

    private static final String RICHNESS_TAG = "Richness";

    public RiftShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        RiftRichness richness = getRichness(stack);
        return Component.translatable(richness.getTranslationKey())
                .append(" ")
                .append(Component.translatable(getDescriptionId(stack)));
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
