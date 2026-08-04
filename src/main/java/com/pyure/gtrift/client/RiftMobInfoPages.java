package com.pyure.gtrift.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
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
 * Shared data-gathering logic for every recipe-viewer plugin's "which mobs spawn where" info page
 * (JEI/EMI/REI) — reads the rift_mobs/rift_elite_mobs config-folder files directly, not the live
 * RiftMobPool.NORMAL/ELITE singletons those files feed into, since those singletons are only populated
 * by a SERVER-side reload listener and would be empty in a separate client-only JVM (same category of
 * bug already hit/fixed for RiftBeaconMachine.dimensionWarningText()). Filters weight-0 entries (can
 * never actually be picked), sorts weight-descending, formats each entry's display line.
 *
 * Deliberately free of any JEI/EMI/REI/Minecraft-client-only import, so it's exercisable from a headless
 * GameTest despite living in `client/` — same idiom as ClientAmbienceState.
 *
 * Directories are passed in rather than resolved internally (mirrors ShardTypeLoader.loadAll(Path))
 * specifically so tests can point this at synthetic scratch directories instead of the real config
 * folder — see RiftMobInfoPagesTest.
 *
 * Deliberately does NOT reuse RiftMobPoolLoader.parseDimensions — that function's "unknown dimension id
 * -&gt; skip the whole entry" behavior exists to protect the spawn pipeline from a bad id, which doesn't
 * apply to a display-only info page: silently dropping a mob because of a typo'd dimension id would be
 * worse UX here than just showing whatever string is actually in the file. This class's own parsing
 * never rejects an entry, and doesn't validate ids against any real dimension registry.
 */
public final class RiftMobInfoPages {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");
    private static final Gson GSON = new Gson();
    private static final String UNRESTRICTED_KEY = "all";

    public record InfoPage(String dimensionKey, Component title, List<Component> lines) {}

    /** Keeps weight alongside the already-styled line so pages can be sorted (weight descending)
     *  right before returning, without re-parsing anything. */
    private record MobLine(int weight, Component component) {}

    private RiftMobInfoPages() {}

    public static List<InfoPage> build(Path mobsDir, Path eliteMobsDir) {
        Map<String, List<MobLine>> pages = new LinkedHashMap<>();
        readDirectory(mobsDir, null, pages);
        readDirectory(eliteMobsDir, "Elite", pages);

        List<InfoPage> result = new ArrayList<>();
        for (Map.Entry<String, List<MobLine>> page : pages.entrySet()) {
            Component title = Component.literal(page.getKey().equals(UNRESTRICTED_KEY)
                            ? "All dimensions" : dimensionDisplayName(page.getKey()))
                    .withStyle(ChatFormatting.BOLD);

            List<MobLine> sorted = new ArrayList<>(page.getValue());
            sorted.sort(Comparator.comparingInt(MobLine::weight).reversed());
            List<Component> lines = new ArrayList<>();
            for (MobLine mobLine : sorted) {
                lines.add(mobLine.component());
            }
            result.add(new InfoPage(page.getKey(), title, lines));
        }
        return result;
    }

    private static void readDirectory(Path dir, String poolLabel, Map<String, List<MobLine>> pages) {
        if (!Files.isDirectory(dir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                readFile(file, poolLabel, pages);
            }
        } catch (IOException e) {
            LOGGER.warn("[info-pages] Failed to list config-folder entries in {}: {}", dir, e.getMessage());
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
            LOGGER.warn("[info-pages] Failed to read {} for the dimension info page, skipping: {}", file, e.getMessage());
        }
    }

    private static String entityName(String entityId) {
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityId));
        return entityType != null ? entityType.getDescription().getString() : entityId;
    }

    /**
     * Turns a raw dimension id ("minecraft:the_nether") into a readable name ("The Nether") by
     * title-casing its path — no live dimension/level registry lookup, since none of these plugins can
     * rely on one being available at their own bootstrap timing. Works the same way for vanilla and
     * modded ids alike, rather than special-casing a handful of known ones. Falls back to the raw key if
     * it isn't even a valid ResourceLocation (matches readDimensionKeys' own "never reject, just look
     * odd" philosophy).
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
