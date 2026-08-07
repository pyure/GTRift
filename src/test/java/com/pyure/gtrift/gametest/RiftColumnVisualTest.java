package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.client.JaggedColumnGeometry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Covers JaggedColumnGeometry's pure vertex math — the only piece of the multi-column visual
 * (plans/rift-multi-column.md Phase 4) that's GameTest-testable at all, since actual GL output isn't
 * verifiable headlessly. Real visual correctness (does it look right, does it wobble smoothly, does
 * it render from every angle) needs a human on runClient, per the plan's own Testing section.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftColumnVisualTest {

    @GameTest(template = "empty")
    public static void computeLocalVerticesReturnsExactlyRingCountTimesVertsPerRing(GameTestHelper helper) {
        List<Vec3> vertices = JaggedColumnGeometry.computeLocalVertices(0f);
        int expected = JaggedColumnGeometry.RING_COUNT * JaggedColumnGeometry.VERTS_PER_RING;
        helper.assertTrue(vertices.size() == expected,
                "expected %d vertices (RING_COUNT * VERTS_PER_RING), got %d".formatted(expected, vertices.size()));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wobbleActuallyChangesVertexPositionOverTime(GameTestHelper helper) {
        List<Vec3> atZero = JaggedColumnGeometry.computeLocalVertices(0f);
        List<Vec3> atLater = JaggedColumnGeometry.computeLocalVertices(5f);

        boolean anyDifferent = false;
        for (int i = 0; i < atZero.size(); i++) {
            if (!atZero.get(i).equals(atLater.get(i))) {
                anyDifferent = true;
                break;
            }
        }
        helper.assertTrue(anyDifferent,
                "expected at least one vertex position to differ between animationTime=0 and animationTime=5 — geometry looks static, not wobbling");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void isOuterVertexAlternatesStartingWithOuterAtIndexZero(GameTestHelper helper) {
        helper.assertTrue(JaggedColumnGeometry.isOuterVertex(0), "expected index 0 to be an outer (spike) vertex");
        for (int i = 0; i < JaggedColumnGeometry.VERTS_PER_RING; i++) {
            boolean expectedOuter = i % 2 == 0;
            helper.assertTrue(JaggedColumnGeometry.isOuterVertex(i) == expectedOuter,
                    "expected index %d to be %s".formatted(i, expectedOuter ? "outer" : "inner"));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void phaseOffsetForColumnIsStablePerPositionAndVariesAcrossColumns(GameTestHelper helper) {
        BlockPos a = new BlockPos(10, 64, 10);
        BlockPos b = new BlockPos(-30, 70, 200);

        float aFirst = JaggedColumnGeometry.phaseOffsetForColumn(a);
        float aSecond = JaggedColumnGeometry.phaseOffsetForColumn(a);
        helper.assertTrue(aFirst == aSecond,
                "expected the same column position to always yield the same phase offset (stable across frames)");

        float bOffset = JaggedColumnGeometry.phaseOffsetForColumn(b);
        helper.assertTrue(aFirst != bOffset,
                "expected two different column positions to yield different phase offsets (desynchronized wobble)");

        helper.succeed();
    }
}
