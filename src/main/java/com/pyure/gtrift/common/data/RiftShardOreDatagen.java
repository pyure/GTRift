package com.pyure.gtrift.common.data;

import com.pyure.gtrift.GTRift;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates a Rift Shard type (see ShardType/ShardTypeLoader) for every ore vein GT (or an addon, or a
 * KubeJS script — all funnel through the same GTOreDefinition/GTRegistries.ORE_VEINS registry, confirmed
 * via GTOreDefinition's own kjs$-prefixed methods) has registered, replacing the old hand-authored
 * "diamond" placeholder entirely. See specs/rift-ore-shard-datagen.md for the full design.
 *
 * GTRegistries.ORE_VEINS only populates via GTOreLoader (a SimpleJsonResourceReloadListener) — later
 * than FMLConstructModEvent, when ShardTypeLoader/GTRiftItems currently register items. That's why the
 * automatic first-launch trigger (added in a later phase) hooks ServerAboutToStartEvent instead of
 * joining the reload-listener chain itself: this class doesn't need to *be* a reload listener (its own
 * output isn't datapack-driven), it just needs to run at a point confirmed to be after GT's own listener
 * has already finished — ServerAboutToStartEvent gives that without depending on registration-order
 * between two different mods' reload listeners.
 */
@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RiftShardOreDatagen {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Fixed vivid band so every generated color reads as legible regardless of the hash — only hue
    // varies per vein id.
    private static final float COLOR_SATURATION = 0.7f;
    private static final float COLOR_BRIGHTNESS = 0.9f;

    public record GenerationResult(List<String> written) {}

    private RiftShardOreDatagen() {}

    /**
     * Pure — no registry/file access. `type` is the vein's own path segment (humanizes for free via
     * ShardType.defaultDisplayName()); `color` is a deterministic hash of the vein's full id (namespace
     * included, so two differently-namespaced veins sharing a path name don't collide on color).
     * `outputs`' chances are normalized against the pre-filter sum of all materials in the vein — not
     * re-normalized after dropping materials with no raw-ore item — so a vein with a lot of
     * unrepresented material honestly shows lower total chance rather than inflating what's left. A
     * zero-sum or fully-filtered vein still returns valid JSON with an empty outputs array, never throws.
     */
    public static JsonObject buildShardTypeJson(ResourceLocation veinId, List<ObjectIntPair<Material>> materialChances) {
        JsonObject json = new JsonObject();
        json.addProperty("type", veinId.getPath());
        json.addProperty("color", colorForSeed(veinId.toString()));

        long sum = 0;
        for (ObjectIntPair<Material> pair : materialChances) {
            sum += pair.rightInt();
        }

        JsonArray outputs = new JsonArray();
        if (sum > 0) {
            for (ObjectIntPair<Material> pair : materialChances) {
                ItemStack stack = ChemicalHelper.get(TagPrefix.rawOre, pair.left());
                if (stack.isEmpty()) continue;

                JsonObject entry = new JsonObject();
                entry.addProperty("item", itemId(stack));
                entry.addProperty("chance", pair.rightInt() / (double) sum);
                outputs.add(entry);
            }
        }
        json.add("outputs", outputs);
        return json;
    }

    /**
     * Real GTRegistries.ORE_VEINS iteration + real file writes — the single shared entry point for every
     * trigger (automatic first-launch, the `fill` command, and `wipe confirm` after it clears the
     * directory). Creates `directory` if missing. "Already has a file" is resolved by content, via
     * ShardTypeLoader.loadAll(directory), not by filename — a hand-renamed file still correctly prevents
     * a duplicate write. Logs the result once, here, shared by every caller — this is the only place
     * generated names get logged; chat-facing callers only ever show a count.
     */
    public static GenerationResult generateAll(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            LOGGER.warn("[{}] Failed to create {}: {}", ShardTypeLoader.DIRECTORY, directory, e.getMessage());
            return new GenerationResult(List.of());
        }

        Set<String> alreadyPresent = ShardTypeLoader.loadAll(directory).shardTypes().stream()
                .map(ShardType::sanitizedId)
                .collect(Collectors.toSet());

        List<Map.Entry<ResourceLocation, GTOreDefinition>> veins = new ArrayList<>(GTRegistries.ORE_VEINS.entries());
        veins.sort(Map.Entry.comparingByKey());

        if (veins.isEmpty()) {
            LOGGER.warn("[{}] GTRegistries.ORE_VEINS is empty — no ore veins to generate shard types " +
                            "from (either GT worldgen is genuinely disabled, or this ran before the registry was ready).",
                    ShardTypeLoader.DIRECTORY);
            return new GenerationResult(List.of());
        }

        List<String> written = new ArrayList<>();
        Set<String> claimed = new HashSet<>(alreadyPresent);
        for (Map.Entry<ResourceLocation, GTOreDefinition> entry : veins) {
            ResourceLocation veinId = entry.getKey();
            String sanitizedId = ShardType.sanitize(veinId.getPath());
            if (!claimed.add(sanitizedId)) continue; // already on disk, or claimed earlier this pass

            JsonObject json = buildShardTypeJson(veinId, entry.getValue().veinGenerator().getValidMaterialsChances());
            try {
                Files.writeString(directory.resolve(sanitizedId + ".json"), GSON.toJson(json), StandardCharsets.UTF_8);
                written.add(json.get("type").getAsString());
            } catch (IOException e) {
                LOGGER.warn("[{}] Failed to write generated shard type for vein {}: {}",
                        ShardTypeLoader.DIRECTORY, veinId, e.getMessage());
                claimed.remove(sanitizedId);
            }
        }

        if (written.isEmpty()) {
            LOGGER.info("[{}] No new shard types generated (all {} registered ore vein(s) already have a file).",
                    ShardTypeLoader.DIRECTORY, veins.size());
        } else {
            LOGGER.info("[{}] Generated {} shard type(s) from registered ore veins: {}. Restart required " +
                            "for their items to register.",
                    ShardTypeLoader.DIRECTORY, written.size(), String.join(", ", written));
        }

        return new GenerationResult(written);
    }

    /** Deletes every *.json file currently in `directory` (not the directory itself, not non-JSON
     *  files), then runs generateAll against the now-empty directory. Destructive by design. */
    public static GenerationResult wipeAndRegenerate(Path directory) {
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
                for (Path file : stream) {
                    Files.delete(file);
                }
            } catch (IOException e) {
                LOGGER.warn("[{}] Failed to clear {} before regenerating: {}",
                        ShardTypeLoader.DIRECTORY, directory, e.getMessage());
            }
        }
        return generateAll(directory);
    }

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve(GTRift.MOD_ID).resolve(ShardTypeLoader.DIRECTORY);
    }

    /**
     * Fires once per server boot, confirmed (via the real Forge jar) to be strictly after the initial
     * datapack reload completes — i.e. after GTOreLoader has already populated GTRegistries.ORE_VEINS.
     * One-time snapshot: no-ops entirely if the folder already exists, preserving the "never touch an
     * existing folder automatically" convention this project already uses elsewhere (RiftMobPoolLoader,
     * the old ShardTypeLoader.extractDefaultsIfMissing this replaces). generateAll handles its own
     * logging (see above) — nothing further to report here.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (Files.exists(configDir())) return;
        generateAll(configDir());
    }

    private static String colorForSeed(String seed) {
        int hue = Math.floorMod(seed.hashCode(), 360);
        int rgb = Color.HSBtoRGB(hue / 360f, COLOR_SATURATION, COLOR_BRIGHTNESS) & 0xFFFFFF;
        return "#%06X".formatted(rgb);
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null ? id.toString() : "minecraft:air";
    }
}
