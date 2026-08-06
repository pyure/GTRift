package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Confirms the /gtrift shard_types and /gtrift mob_pools command trees are registered correctly —
 * structural check only (CommandNode.getChild(...) tree walks), deliberately never calling .execute()
 * or .parse().
 *
 * An earlier version of this test actually executed "/gtrift shard_types fill" against the real config
 * directory. That's real file I/O (RiftShardOreDatagen.generateAll re-parses every existing shard type
 * file via ShardTypeLoader.loadAll) running synchronously on the server thread during a GameTest batch
 * where dozens of other tests are ticking concurrently — confirmed via a real repro (100% reproducible
 * across 3 runs, went away when this test was disabled) that it was slow enough to disrupt the precise
 * tick-count-based assertions in two completely unrelated RiftLootDropTest cases. Not a bug in
 * GTRiftCommands/RiftShardOreDatagen themselves — a test-isolation problem from doing real I/O against
 * shared state inside a concurrently-scheduled batch. The actual fill/wipe logic is already covered in
 * full isolation by RiftShardOreDatagenTest/RiftShardOreDatagenWipeTest (shard types) and
 * RiftMobPoolDatagenTest (mob pools); this test only needs to confirm the Brigadier wiring itself, which
 * a structural tree check does without any side effects — same reasoning applied to mob_pools below.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class GTRiftCommandsTest {

    @GameTest(template = "empty")
    public static void commandTreeIsRegistered(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("gtrift");
        helper.assertTrue(root != null, "expected '/gtrift' to be registered");

        CommandNode<CommandSourceStack> shardTypes = root.getChild("shard_types");
        helper.assertTrue(shardTypes != null, "expected '/gtrift shard_types' to be registered");

        helper.assertTrue(shardTypes.getChild("fill") != null,
                "expected '/gtrift shard_types fill' to be registered");
        CommandNode<CommandSourceStack> shardTypesWipe = shardTypes.getChild("wipe");
        helper.assertTrue(shardTypesWipe != null, "expected '/gtrift shard_types wipe' to be registered");
        helper.assertTrue(shardTypesWipe.getChild("confirm") != null,
                "expected '/gtrift shard_types wipe confirm' to be registered");

        CommandNode<CommandSourceStack> mobPools = root.getChild("mob_pools");
        helper.assertTrue(mobPools != null, "expected '/gtrift mob_pools' to be registered");

        helper.assertTrue(mobPools.getChild("fill") != null,
                "expected '/gtrift mob_pools fill' to be registered");
        CommandNode<CommandSourceStack> mobPoolsWipe = mobPools.getChild("wipe");
        helper.assertTrue(mobPoolsWipe != null, "expected '/gtrift mob_pools wipe' to be registered");
        helper.assertTrue(mobPoolsWipe.getChild("confirm") != null,
                "expected '/gtrift mob_pools wipe confirm' to be registered");

        helper.succeed();
    }
}
