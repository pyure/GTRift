package com.pyure.gtrift.common.data;

import net.minecraft.world.entity.EntityType;

import java.util.List;

public record RiftMobPoolEntry(EntityType<?> entityType, int weight, List<RiftDropEntry> drops) {
}
