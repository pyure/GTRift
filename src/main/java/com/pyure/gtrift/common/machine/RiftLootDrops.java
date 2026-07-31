package com.pyure.gtrift.common.machine;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.RiftDropEntry;
import com.pyure.gtrift.common.item.GTRiftItems;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Intercepts drops for GTRift-spawned mobs only (tagged by RiftEventSpawner) and replaces their
 * vanilla loot with zero or more Rift Shards, rolled independently from the mob's own "gtrift_drops"
 * NBT list (populated at spawn time from its RiftMobPoolEntry, already filtered to entries whose
 * minTier the spawn's difficulty tier satisfies). Each entry rolls its own chance independently, so a
 * single kill can yield multiple shards, one, or — intentionally — none. Mobs without the gtrift_mob
 * tag, including ordinary wild-spawned mobs of the same type, are untouched entirely.
 */
@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RiftLootDrops {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.getPersistentData().getBoolean("gtrift_mob")) return;

        event.getDrops().clear();

        boolean isElite = entity.getPersistentData().getBoolean("gtrift_elite");
        RandomSource random = entity.level().getRandom();

        ListTag dropsTag = entity.getPersistentData().getList("gtrift_drops", Tag.TAG_COMPOUND);
        for (int i = 0; i < dropsTag.size(); i++) {
            RiftDropEntry drop = RiftDropEntry.fromNbt(dropsTag.getCompound(i));

            double chance = isElite ? drop.chance() * drop.eliteChanceMultiplier() : drop.chance();
            if (random.nextDouble() >= Math.min(1.0, chance)) continue;

            int amount = drop.min() >= drop.max()
                    ? drop.min()
                    : drop.min() + random.nextInt(drop.max() - drop.min() + 1);
            if (isElite) {
                amount = (int) Math.round(amount * drop.eliteAmountMultiplier());
            }
            if (amount <= 0) continue;

            ItemStack stack = GTRiftItems.createStack(drop.type(), drop.richness(), amount);
            if (stack.isEmpty()) continue; // Unknown shard type id — createStack already logged it.
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack));
        }
    }
}
