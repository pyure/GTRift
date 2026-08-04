package com.pyure.gtrift.common;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.block.GTRiftBlocks;

import com.gregtechceu.gtceu.common.data.GTCreativeModeTabs;

import com.tterrag.registrate.util.entry.RegistryEntry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;

/**
 * GTRift's own registrate instance never had a creative tab set on it, so everything registered
 * through it (the controller, all shard items) had no tab membership at all — invisible to it, though
 * not obviously so: Creative's own "Search" tab bypasses tab membership entirely and shows every
 * registered item regardless, so it looked fine there. JEI's real sidebar list is built from actual
 * tab contents (GTCreativeModeTabs.RegistrateDisplayItemsGenerator, reused here), which is why it
 * never showed up in JEI search despite existing and working normally everywhere else.
 */
public class GTRiftCreativeTabs {

    public static final RegistryEntry<CreativeModeTab> RIFT = GTRift.REGISTRATE.defaultCreativeTab("rift",
                    builder -> builder
                            .displayItems(new GTCreativeModeTabs.RegistrateDisplayItemsGenerator("rift", GTRift.REGISTRATE))
                            .icon(() -> GTRiftBlocks.RIFT_BEACON.asStack())
                            .title(Component.translatable("itemGroup.gtrift.rift"))
                            .build())
            .register();

    public static void init() {}
}
