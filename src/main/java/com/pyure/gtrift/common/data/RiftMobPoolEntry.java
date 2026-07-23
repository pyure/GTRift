package com.pyure.gtrift.common.data;

import net.minecraft.world.entity.EntityType;

public record RiftMobPoolEntry(EntityType<?> entityType, int weight) {
}
