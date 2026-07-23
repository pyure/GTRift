# GameTest harness

Wired into the build (`build.gradle`: `sourceSets.test` classpath, `addModdingDependenciesTo(sourceSets.test)`,
the `gameTestServer` run config, `sourceSet(sourceSets.test)` under `legacyForge.mods.gtrift`) and now has real
tests — see `com.pyure.gtrift.gametest`. `./gradlew compileJava compileTestJava` passes, and `runGameTestServer`
itself has been exercised end-to-end: it runs headlessly (no GUI needed, unlike `runClient`), launches a
dedicated server in `run/gametest/`, runs every registered `@GameTest`, and exits — the real proof that
execution works is its console output/exit code, not this file.

`GameTestBatchRunner` pre-loads **every** structure needed by the whole batch before running *any* test in
it — one missing/malformed structure file crashes the entire server before any test gets a chance to run, not
just the ones that need that particular structure. Keep this in mind when a `runGameTestServer` run fails with
`Could not find structure file ...`: it doesn't necessarily mean the test that needs it is broken, just that
its structure resource is missing.

## Writing a new test

Same annotations, same `GameTestHelper` API as GregTech-Modern (`GregTech-Modern/src/test/README.md`), both being
Forge's GameTest framework — but verify anything you copy from that repo against the real `7.5.3` jar first (see
CLAUDE.md's "Referencing GregTech-Modern's API"); that repo's checkout has drifted to a newer API before and can
again.

For a test with **no world state** (pure registry/logic checks, like `RiftShardItemTest`), use the `empty`
template. This is **not** a vanilla built-in — `data/minecraft/structures/empty.nbt` doesn't actually exist
(confirmed by checking the real vanilla client jar contents; an earlier draft of this doc assumed it did,
which went undetected until `runGameTestServer` was actually exercised end-to-end). It resolves to
`gtrift:empty`, backed by a real (minimal, 1x1x1, zero blocks) structure file GTRift ships at
`src/test/resources/data/gtrift/structures/empty.nbt` — same resolution mechanism as any other unprefixed
`template = "..."` string, matching this project's own namespace via `@GameTestHolder(GTRift.MOD_ID)`.

```java
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class ExampleTest {

    @GameTest(template = "empty")
    public static void myTest(GameTestHelper helper) {
        helper.assertTrue(true, "true is false");
        helper.succeed();
    }
}
```

For a test that needs a **real formed machine/multiblock** (like `RiftShardCentrifugeRecipeTest`), you need your
own structure template — the gtceu *slim* jar ships no test resources at all (not even GTCEu's own `singleblock_*`
templates used in its internal tests), so there's nothing to inherit. Build one via the vanilla structure-block
workflow: place a Structure Block (`/give @s minecraft:structure_block`), set it to Save mode, type an
explicitly-namespaced name (e.g. `gtrift:rift_beacon_lv`) into the Structure Name field, size/position the
bounding box to fully cover the built structure (Detect doesn't reliably work for GTCEu multiblocks — size it
manually against the machine's actual pattern, e.g. `GTRiftBlocks.java`'s `.aisle(...)` calls), and Save. That
writes to `run/saves/<world>/generated/<namespace>/structures/<name>.nbt` (namespace/name matching whatever you
typed into the Structure Name field) — copy it from there into
`src/test/resources/data/gtrift/structures/<name>.nbt`.

To get from a `GameTestHelper.getBlockEntity(pos)` call to an actual machine object, unwrap through
`MetaMachineBlockEntity` — a direct cast to the machine class does **not** compile (confirmed against the real
jar; an earlier draft of `RiftShardCentrifugeRecipeTest` tried it and failed):

```java
BlockEntity holder = helper.getBlockEntity(new BlockPos(0, 1, 0));
if (!(holder instanceof MetaMachineBlockEntity metaMachineBlockEntity)) {
    helper.fail("wrong block at relative pos [0,1,0]!");
    return;
}
MetaMachine machine = metaMachineBlockEntity.getMetaMachine();
if (!(machine instanceof SimpleTieredMachine centrifuge)) {  // cast to whatever machine type you expect
    helper.fail("wrong machine in MetaMachineBlockEntity!");
    return;
}
```

`NotifiableItemStackHandler` lives at `com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler` —
no `.notifiable` subpackage, despite what an older reference might suggest.

## Debugging a captured structure without launching the game

`tools/read_structure_nbt.py` (repo root, plain Python, no dependencies) dumps a `.nbt` structure file's
size/palette/per-position blocks: `python tools/read_structure_nbt.py path/to/structure.nbt`. Useful for
confirming a captured multiblock's controller/machine actually ends up where a test's
`helper.getBlockEntity(relativePos)` call expects it, before spending a `runGameTestServer` cycle on it.

**The relative-position math that bit us once already**: `GameTestHelper.getBlockEntity(BlockPos)` resolves
via `structureBlockPos.offset(relativePos)` — and vanilla places structure content starting **one block above**
the structure block's own Y position (confirmed by cross-referencing a structure whose block sits at raw-NBT
Y=0 against a test that correctly finds it at relative Y=1). So `relativePos.y` in test code is always
`rawStructureNbtY + 1`; X/Z map 1:1 with no offset. Getting this wrong doesn't fail loudly — if another
machine-backed block happens to sit at the wrong-but-plausible position, you get a confusing "wrong machine in
MetaMachineBlockEntity!" instead of "wrong block", because `getBlockEntity` still finds *a* real
`MetaMachineBlockEntity`, just not the one you meant. Cross-check the raw NBT positions with the tool above
before assuming the code (rather than the position) is wrong.

Run all tests headlessly via `./gradlew.bat runGameTestServer` (or `runGameTestServer` from IntelliJ's Gradle
panel) — confirmed working from a plain terminal, no GUI needed. Run it in your own interactive terminal rather
than through automation, so you can see the live console output. `/test run <name>` (snake_case of the method
name, e.g. `bossBarTracksHealthAndClearsOnDeath` → `boss_bar_tracks_health_and_clears_on_death`) from a normal
running client works for structure-independent tests, but tests that call `helper.spawn(...)`/similar can NPE
in `GameTestHelper.absoluteVec()` when run this way against a live played world (the structure-block position
isn't set the same way it is when `GameTestBatchRunner` places it) — prefer `runGameTestServer` for anything
that spawns entities or otherwise needs the structure to actually exist.
