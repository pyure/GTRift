package com.pyure.gtrift.client;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.block.GTRiftBlocks;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-only — JEI itself is a client-side mod, so this class and its discovery (the @JeiPlugin
 * annotation, scanned by JEI's own classpath scan at its own bootstrap — no manual registration needed
 * anywhere else in this codebase) never runs on a dedicated server. Confirmed against
 * GregTech-Modern's GTJEIPlugin precedent (same JEI release line, 15.20.0.115 there vs GTRift's
 * 15.20.0.130) — that class is never manually registered either.
 *
 * Reads the rift_mobs/rift_elite_mobs config-folder files directly, rather than the live
 * RiftMobPool.NORMAL/ELITE singletons those same files feed into — deliberately, because RiftMobPool is
 * only ever populated by a SERVER-side reload listener (RiftMobPoolLoader, via AddReloadListenerEvent).
 * On a real dedicated-server install, this plugin runs in a separate client JVM that never runs that
 * listener, so RiftMobPool would be empty there even though the config folder itself (a real on-disk
 * file, identical across worlds on one install) is always readable. This is the same category of bug
 * already hit and fixed for the controller GUI's dimension-warning label — see
 * RiftBeaconMachine.dimensionWarningText()'s own doc comment.
 *
 * Deliberately does NOT reuse RiftMobPoolLoader.parseDimensions — that function's "unknown dimension id
 * -&gt; skip the whole entry" behavior exists to protect the spawn pipeline from a bad id, which doesn't
 * apply to a display-only info page: silently dropping a mob because of a typo'd dimension id would be
 * worse UX here than just showing whatever string is actually in the file. This class's own parsing
 * never rejects an entry, and doesn't validate ids against any real dimension registry.
 *
 * Not refreshed when the mob pool reloads (world join / /reload) — registered once, statically, the
 * conventional way JEI plugins register content, matching specs/rift-mob-dimensions.md's own accepted
 * tradeoff.
 */
@JeiPlugin
public class RiftJeiPlugin implements IModPlugin {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");
    private static final ResourceLocation PLUGIN_UID = new ResourceLocation(GTRift.MOD_ID, "jei_plugin");
    private static final Gson GSON = new Gson();
    private static final String UNRESTRICTED_KEY = "all";

    /** Keeps weight alongside the already-styled line so pages can be sorted (weight descending)
     *  right before display, without re-parsing anything. */
    private record MobLine(int weight, Component component) {}

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (ModList.get().isLoaded("rei") || ModList.get().isLoaded("emi")) return;

        Map<String, List<MobLine>> pages = new LinkedHashMap<>();
        readDirectory("rift_mobs", null, pages);
        readDirectory("rift_elite_mobs", "Elite", pages);

        for (Map.Entry<String, List<MobLine>> page : pages.entrySet()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(page.getKey().equals(UNRESTRICTED_KEY)
                            ? "All dimensions" : dimensionDisplayName(page.getKey()))
                    .withStyle(ChatFormatting.BOLD));

            List<MobLine> sorted = new ArrayList<>(page.getValue());
            sorted.sort(Comparator.comparingInt(MobLine::weight).reversed());
            for (MobLine mobLine : sorted) {
                lines.add(mobLine.component());
            }
            // addIngredientInfo internally builds one IJeiIngredientInfoRecipe per call and registers
            // it against JEI's own built-in RecipeTypes.INFORMATION category — confirmed by decompiling
            // the real mezz.jei.library.load.registration.RecipeRegistration implementation. The recipe
            // carries its own ingredient, so JEI's normal "uses" (U-key) indexing finds it automatically
            // — no separate registerRecipeCatalysts call is needed.
            registration.addIngredientInfo(GTRiftBlocks.RIFT_BEACON.getBlock(), lines.toArray(Component[]::new));
        }
    }

    private static void readDirectory(String directory, String poolLabel, Map<String, List<MobLine>> pages) {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(GTRift.MOD_ID).resolve(directory);
        if (!Files.isDirectory(dir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                readFile(file, poolLabel, pages);
            }
        } catch (IOException e) {
            LOGGER.warn("[jei] Failed to list config-folder entries in {}: {}", dir, e.getMessage());
        }
    }

    private static void readFile(Path file, String poolLabel, Map<String, List<MobLine>> pages) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject object = GSON.fromJson(reader, JsonObject.class);
            String entityId = GsonHelper.getAsString(object, "entity");
            int weight = GsonHelper.getAsInt(object, "weight");
            // A weight-0 entry can never actually be picked (see RiftMobPool's weighted-random roll) —
            // listing it here would just be noise, not a real possibility players should expect.
            if (weight <= 0) return;
            String displayName = entityName(entityId);

            MutableComponent component = Component.literal("%s (weight %d)".formatted(displayName, weight));
            if (poolLabel != null) {
                component.append(Component.literal(" "))
                        .append(Component.literal("[%s]".formatted(poolLabel)).withStyle(ChatFormatting.DARK_RED));
            }
            MobLine mobLine = new MobLine(weight, component);

            for (String dimensionKey : readDimensionKeys(object)) {
                pages.computeIfAbsent(dimensionKey, k -> new ArrayList<>()).add(mobLine);
            }
        } catch (Exception e) {
            LOGGER.warn("[jei] Failed to read {} for the dimension info page, skipping: {}", file, e.getMessage());
        }
    }

    private static String entityName(String entityId) {
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityId));
        return entityType != null ? entityType.getDescription().getString() : entityId;
    }

    /**
     * Turns a raw dimension id ("minecraft:the_nether") into a readable name ("The Nether") by
     * title-casing its path — no live dimension/level registry lookup, since this plugin can't rely on
     * one being available (see class doc). Works the same way for vanilla and modded ids alike, rather
     * than special-casing a handful of known ones. Falls back to the raw key if it isn't even a valid
     * ResourceLocation (matches readDimensionKeys' own "never reject, just look odd" philosophy).
     */
    private static String dimensionDisplayName(String dimensionKey) {
        ResourceLocation id = ResourceLocation.tryParse(dimensionKey);
        String path = id != null ? id.getPath() : dimensionKey;
        StringBuilder result = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? dimensionKey : result.toString();
    }

    /**
     * Never rejects an entry — an unrecognized/typo'd id just becomes its own (oddly named) page,
     * which is a better failure mode for a display-only tool than silently dropping the mob.
     */
    private static List<String> readDimensionKeys(JsonObject object) {
        if (!object.has("dimensions")) return List.of(UNRESTRICTED_KEY);

        JsonElement element = object.get("dimensions");
        List<String> ids = new ArrayList<>();
        if (element.isJsonPrimitive()) {
            ids.add(element.getAsString());
        } else {
            for (JsonElement item : element.getAsJsonArray()) {
                ids.add(item.getAsString());
            }
        }

        if (ids.isEmpty()) return List.of();
        if (ids.stream().anyMatch(id -> id.equalsIgnoreCase(UNRESTRICTED_KEY))) return List.of(UNRESTRICTED_KEY);
        return ids;
    }
}
