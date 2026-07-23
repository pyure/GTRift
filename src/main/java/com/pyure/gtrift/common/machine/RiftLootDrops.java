package com.pyure.gtrift.common.machine;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.RiftLootTable;
import com.pyure.gtrift.common.item.GTRiftItems;
import com.pyure.gtrift.common.item.RiftAffinity;
import com.pyure.gtrift.common.item.RiftRichness;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Intercepts drops for GTRift-spawned mobs only (tagged by RiftEventSpawner) and replaces their
 * vanilla loot with a single Rift Shard, rolled via RiftLootTable. Mobs without the gtrift_mob tag
 * — including ordinary wild-spawned mobs of the same type — are untouched entirely.
 */
@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RiftLootDrops {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.getPersistentData().getBoolean("gtrift_mob")) return;

        int difficultyTier = entity.getPersistentData().getInt("gtrift_tier");
        boolean isElite = entity.getPersistentData().getBoolean("gtrift_elite");
        RandomSource random = entity.level().getRandom();

        RiftAffinity affinity = RiftLootTable.rollAffinity(difficultyTier, random);
        RiftRichness richness = isElite
                ? RiftLootTable.rollRichnessForElite(difficultyTier, random)
                : RiftLootTable.rollRichness(difficultyTier, random);
        ItemStack stack = GTRiftItems.createStack(affinity, richness, 1);

        event.getDrops().clear();
        event.getDrops()
                .add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack));
    }
}
