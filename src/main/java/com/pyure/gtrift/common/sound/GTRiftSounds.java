package com.pyure.gtrift.common.sound;

import com.pyure.gtrift.GTRift;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Plain DeferredRegister, not Registrate — this project has no datagen pipeline (see CLAUDE.md), and
 * SoundEvent has no public constructor in this MC version, only the createVariableRangeEvent/
 * createFixedRangeEvent static factories (confirmed via decompile), so there's nothing a Registrate
 * builder would buy over the standard Forge registration idiom here.
 */
public class GTRiftSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GTRift.MOD_ID);

    // Named after the actual track ("Blood Moon Advance" by tektoon) rather than a generic
    // "rift_ambience" placeholder — a future rotation through several tracks just adds more
    // RegistryObject fields here, no rename needed on this one.
    public static final RegistryObject<SoundEvent> BLOOD_MOON_ADVANCE = SOUNDS.register("blood_moon_advance",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(GTRift.MOD_ID, "blood_moon_advance")));

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
