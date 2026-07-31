package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.GTRiftLoadReporter;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure logic — no world state, template = "empty". Exercises GTRiftLoadReporter.buildChatMessage
 * directly rather than an actual player join (a GameTest server has no real client connecting, so
 * PlayerEvent.PlayerLoggedInEvent never fires in this environment anyway).
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class GTRiftLoadReporterTest {

    @GameTest(template = "empty")
    public static void emptyIssuesProduceNoMessage(GameTestHelper helper) {
        Optional<String> message = GTRiftLoadReporter.buildChatMessage(List.of());
        helper.assertTrue(message.isEmpty(), "expected no chat message for an empty issues list, got %s".formatted(message));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nonEmptyIssuesProduceExactlyOneMessage(GameTestHelper helper) {
        List<String> issues = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            issues.add("issue " + i);
        }

        Optional<String> message = GTRiftLoadReporter.buildChatMessage(issues);
        helper.assertTrue(message.isPresent(), "expected a single chat message for a non-empty issues list");
        helper.assertTrue(message.get().equals("GTRift: 8 issue(s) loading config, see log"),
                "unexpected message: %s".formatted(message.get()));

        helper.succeed();
    }
}
