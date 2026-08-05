package com.pyure.gtrift.common.data;

import com.pyure.gtrift.GTRift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.fml.loading.FMLPaths;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads shard types from config/gtrift/rift_shard_types/*.json only — no datapack merge, and no live
 * dependency on GT's material/worldgen system, matching the spec's "GTRift is unopinionated" stance.
 * Read once at mod construction (see GTRift.java); new types need a restart to take effect since Item
 * registration can't happen after Forge's registries close.
 *
 * Two failure severities, not one uniform warn-and-skip: a bad individual `outputs` entry (malformed
 * item id) just drops that entry and the type still loads; a degenerate `chance` anywhere in the file
 * rejects the whole file as an error, surfaced in `issues` for Phase 5's player-visible report. Missing
 * `type`/`color`, and a duplicate sanitized id, are warn-and-skip-the-file but NOT added to `issues` —
 * only the error-tier (degenerate chance) rejections are, per the spec's explicit two-tier severity
 * design.
 */
public class ShardTypeLoader {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // Package-private (not private) so RiftShardOreDatagen can reuse the real folder name instead of
    // duplicating the string literal.
    static final String DIRECTORY = "rift_shard_types";

    public record ShardTypeLoadResult(List<ShardType> shardTypes, List<String> issues) {}

    private ShardTypeLoader() {}

    /** Resolves the real config folder and delegates to loadAll(Path). */
    public static ShardTypeLoadResult loadAll() {
        return loadAll(configDir());
    }

    public static ShardTypeLoadResult loadAll(Path directory) {
        List<ShardType> shardTypes = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        Map<String, Path> sanitizedIdToSource = new HashMap<>();

        if (!Files.isDirectory(directory)) {
            return new ShardTypeLoadResult(shardTypes, issues);
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : stream) files.add(file);
        } catch (IOException e) {
            LOGGER.warn("[{}] Failed to list shard type files in {}: {}", DIRECTORY, directory, e.getMessage());
            return new ShardTypeLoadResult(shardTypes, issues);
        }
        // Deterministic processing order so "first-loaded wins" (duplicate sanitized id) is repeatable.
        files.sort(Comparator.comparing(Path::getFileName));

        for (Path file : files) {
            parseFile(file, sanitizedIdToSource, issues).ifPresent(shardTypes::add);
        }

        return new ShardTypeLoadResult(shardTypes, issues);
    }

    private static Optional<ShardType> parseFile(Path file, Map<String, Path> sanitizedIdToSource,
                                                   List<String> issues) {
        JsonObject object;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            object = GSON.fromJson(reader, JsonElement.class).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.warn("[{}] Failed to parse {}, skipping: {}", DIRECTORY, file, e.getMessage());
            return Optional.empty();
        }

        String rawType;
        int color;
        try {
            rawType = GsonHelper.getAsString(object, "type");
            color = ShardType.parseColor(GsonHelper.getAsString(object, "color"));
        } catch (Exception e) {
            LOGGER.warn("[{}] Missing/unparseable 'type' or 'color' in {}, skipping file: {}",
                    DIRECTORY, file, e.getMessage());
            return Optional.empty();
        }

        Optional<List<ShardTypeOutput>> outputs = parseOutputs(object, file, issues);
        if (outputs.isEmpty()) {
            // A degenerate chance rejected the whole file; parseOutputs already logged/recorded it.
            return Optional.empty();
        }

        String sanitizedId = ShardType.sanitize(rawType);
        Path firstSource = sanitizedIdToSource.get(sanitizedId);
        if (firstSource != null) {
            LOGGER.warn("[{}] {} resolves to sanitized id '{}', already defined by {}, skipping",
                    DIRECTORY, file, sanitizedId, firstSource);
            return Optional.empty();
        }

        sanitizedIdToSource.put(sanitizedId, file);
        return Optional.of(new ShardType(rawType, sanitizedId, color, outputs.get()));
    }

    /**
     * Empty/absent "outputs" is valid (a type with nothing meaningful to produce). A malformed item id
     * on one entry just drops that entry (warn). A degenerate chance on any entry rejects the whole
     * file (error) — returns Optional.empty() in that case, having already logged/recorded it.
     */
    private static Optional<List<ShardTypeOutput>> parseOutputs(JsonObject object, Path file, List<String> issues) {
        List<ShardTypeOutput> outputs = new ArrayList<>();
        if (!object.has("outputs")) return Optional.of(outputs);

        JsonArray array;
        try {
            array = GsonHelper.getAsJsonArray(object, "outputs");
        } catch (Exception e) {
            LOGGER.warn("[{}] 'outputs' in {} is not an array, treating as empty: {}",
                    DIRECTORY, file, e.getMessage());
            return Optional.of(outputs);
        }

        for (JsonElement element : array) {
            JsonObject entry;
            double chance;
            try {
                entry = element.getAsJsonObject();
                chance = GsonHelper.getAsDouble(entry, "chance");
            } catch (Exception e) {
                LOGGER.warn("[{}] Malformed outputs entry in {}, dropping it: {}", DIRECTORY, file, e.getMessage());
                continue;
            }

            if (chance <= 0 || Double.isNaN(chance) || Double.isInfinite(chance)) {
                String message = "[%s] Degenerate chance (%s) in an outputs entry in %s - rejecting whole file"
                        .formatted(DIRECTORY, chance, file);
                LOGGER.error(message);
                issues.add(message);
                return Optional.empty();
            }

            String itemIdString;
            try {
                itemIdString = GsonHelper.getAsString(entry, "item");
            } catch (Exception e) {
                LOGGER.warn("[{}] Missing 'item' in an outputs entry in {}, dropping it: {}",
                        DIRECTORY, file, e.getMessage());
                continue;
            }

            ResourceLocation itemId;
            try {
                itemId = new ResourceLocation(itemIdString);
            } catch (Exception e) {
                LOGGER.warn("[{}] Malformed item id '{}' in an outputs entry in {}, dropping it: {}",
                        DIRECTORY, itemIdString, file, e.getMessage());
                continue;
            }

            outputs.add(new ShardTypeOutput(itemId, chance));
        }
        return Optional.of(outputs);
    }

    private static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve(GTRift.MOD_ID).resolve(DIRECTORY);
    }
}
