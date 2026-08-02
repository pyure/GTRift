package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.RiftMobPoolEntry;
import com.pyure.gtrift.common.machine.RiftEventSpawner;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * RiftEventSpawner.applyDifficultyScaling reads the mob's own vanilla base attribute values, so this
 * reads them BEFORE calling it rather than hardcoding assumed vanilla defaults — robust to those
 * defaults ever changing. Spawns a real mob (needed for real AttributeInstance objects) but calls
 * applyDifficultyScaling directly rather than going through the full trySpawnMob pipeline, so this
 * can't touch the global RiftMobPool singletons — see RiftEventSpawnerDropTest's own doc comment for
 * why that pattern is avoided in this codebase.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftDifficultyScalingTest {

    // Mirrors RiftEventSpawner's own HEALTH_BONUS_PER_TIER/DAMAGE_BONUS_PER_TIER/SPEED_BONUS_PER_TIER —
    // those are private, so this test asserts against the same values by name, not by importing them.
    private static final double HEALTH_BONUS_PER_TIER = 5.0;
    private static final double DAMAGE_BONUS_PER_TIER = 2.0;
    private static final double SPEED_BONUS_PER_TIER = 0.1;

    @GameTest(template = "empty")
    public static void multipliersApplyToTheFinalTierAdjustedValue(GameTestHelper helper) {
        Zombie mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(health != null && damage != null && speed != null,
                "expected a zombie to have MAX_HEALTH/ATTACK_DAMAGE/MOVEMENT_SPEED attributes");

        double baseHealth = health.getBaseValue();
        double baseDamage = damage.getBaseValue();
        double baseSpeed = speed.getBaseValue();

        int tier = GTValues.MV;
        RiftMobPoolEntry entry = new RiftMobPoolEntry(EntityType.ZOMBIE, 100, List.of(), Optional.empty(),
                2.0, 3.0, 4.0);

        RiftEventSpawner.applyDifficultyScaling(mob, tier, entry);

        double expectedHealth = (baseHealth + HEALTH_BONUS_PER_TIER * tier) * entry.healthMultiplier();
        double expectedDamage = (baseDamage + DAMAGE_BONUS_PER_TIER * tier) * entry.damageMultiplier();
        double expectedSpeed = (baseSpeed + SPEED_BONUS_PER_TIER * tier) * entry.speedMultiplier();

        helper.assertTrue(Math.abs(health.getBaseValue() - expectedHealth) < 0.001,
                "expected health %f, got %f".formatted(expectedHealth, health.getBaseValue()));
        helper.assertTrue(Math.abs(damage.getBaseValue() - expectedDamage) < 0.001,
                "expected damage %f, got %f".formatted(expectedDamage, damage.getBaseValue()));
        helper.assertTrue(Math.abs(speed.getBaseValue() - expectedSpeed) < 0.001,
                "expected speed %f, got %f".formatted(expectedSpeed, speed.getBaseValue()));
        helper.assertTrue(mob.getHealth() == mob.getMaxHealth(),
                "expected current health to be topped up to the new max after scaling");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void defaultMultipliersAreANoOpOnTopOfTierScaling(GameTestHelper helper) {
        Zombie mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        double baseHealth = health.getBaseValue();

        int tier = GTValues.LV;
        RiftMobPoolEntry entry = new RiftMobPoolEntry(EntityType.ZOMBIE, 100, List.of(), Optional.empty(),
                1.0, 1.0, 1.0);

        RiftEventSpawner.applyDifficultyScaling(mob, tier, entry);

        double expectedHealth = baseHealth + HEALTH_BONUS_PER_TIER * tier;
        helper.assertTrue(Math.abs(health.getBaseValue() - expectedHealth) < 0.001,
                "expected default 1.0 multiplier to leave tier scaling unchanged: expected %f, got %f"
                        .formatted(expectedHealth, health.getBaseValue()));

        helper.succeed();
    }
}
