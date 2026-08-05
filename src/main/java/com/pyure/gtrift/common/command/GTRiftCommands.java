package com.pyure.gtrift.common.command;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.RiftShardOreDatagen;
import com.pyure.gtrift.common.data.RiftShardOreDatagen.GenerationResult;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First command surface in this project. See specs/rift-ore-shard-datagen.md for the full design —
 * `/gtrift shard_types fill` (additive, safe) and `/gtrift shard_types wipe confirm` (destructive,
 * requires the explicit `confirm` literal, warns without deleting anything if omitted). Both gated at
 * the `/gtrift` root via permission level 2 (`Commands.LEVEL_GAMEMASTERS`) — the same tier most
 * admin-flavored mod commands use (matches GTCEu's own command precedent, e.g. `HazardCommands`), not
 * the stricter level 4 reserved for genuinely owner-only vanilla commands.
 *
 * Chat replies are always count-only — the individual generated names are already logged by
 * `RiftShardOreDatagen.generateAll`/`wipeAndRegenerate`, never repeated in chat.
 */
@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GTRiftCommands {

    private GTRiftCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("gtrift")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("shard_types")
                        .then(Commands.literal("fill").executes(GTRiftCommands::fill))
                        .then(Commands.literal("wipe").executes(GTRiftCommands::wipeWarning)
                                .then(Commands.literal("confirm").executes(GTRiftCommands::wipeConfirm)))));
    }

    private static int fill(CommandContext<CommandSourceStack> ctx) {
        GenerationResult result = RiftShardOreDatagen.generateAll(RiftShardOreDatagen.configDir());
        int count = result.written().size();
        if (count == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("No new ore veins found — nothing written."), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    ("Wrote %d shard type file(s) from worldgen ore veins. See the log for details. " +
                            "Restart the server for their items to register.").formatted(count)), true);
        }
        return count;
    }

    private static int wipeWarning(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "This will permanently delete every file in config/gtrift/rift_shard_types/ and " +
                        "regenerate from scratch. Re-run as '/gtrift shard_types wipe confirm' to proceed."), true);
        return 0;
    }

    private static int wipeConfirm(CommandContext<CommandSourceStack> ctx) {
        GenerationResult result = RiftShardOreDatagen.wipeAndRegenerate(RiftShardOreDatagen.configDir());
        int count = result.written().size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                ("Wiped and regenerated %d shard type file(s) from worldgen ore veins. See the log for " +
                        "details. Restart the server for their items to register.").formatted(count)), true);
        return count;
    }
}
