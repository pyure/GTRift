package com.pyure.gtrift.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A minimal, GTRift-owned PackResources backing dynamically-registered shard-type item models — the
 * same problem GTCEu solves for its own material-item catalog via GTDynamicResourcePack/
 * TagPrefixItemRenderer, an internal (non-API) class this deliberately doesn't depend on. Each entry
 * is a plain DelegatedModel-style redirect (a JSON object of the shape {"parent": "&lt;other model&gt;"}),
 * added via addItemModel before this pack is first read (see ClientProxy).
 */
public class RiftShardModelPack implements PackResources {

    private static final String NAME = "gtrift_dynamic_shard_models";
    private static final Gson GSON = new Gson();
    private static final Map<ResourceLocation, byte[]> RESOURCES = new HashMap<>();

    /** itemModelId is the model location (e.g. "gtrift:item/diamond_shard"), not the item's own id. */
    public static void addItemModel(ResourceLocation itemModelId, JsonElement model) {
        ResourceLocation file = new ResourceLocation(itemModelId.getNamespace(),
                "models/" + itemModelId.getPath() + ".json");
        RESOURCES.put(file, GSON.toJson(model).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        if (packType != PackType.CLIENT_RESOURCES) return null;
        byte[] bytes = RESOURCES.get(location);
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, ResourceOutput output) {
        if (packType != PackType.CLIENT_RESOURCES) return;
        for (Map.Entry<ResourceLocation, byte[]> entry : RESOURCES.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(namespace) || !location.getPath().startsWith(path)) continue;
            byte[] bytes = entry.getValue();
            output.accept(location, () -> new ByteArrayInputStream(bytes));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType packType) {
        if (packType != PackType.CLIENT_RESOURCES) return Set.of();
        Set<String> namespaces = new HashSet<>();
        for (ResourceLocation location : RESOURCES.keySet()) {
            namespaces.add(location.getNamespace());
        }
        return namespaces;
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (serializer != PackMetadataSection.TYPE) return null;
        @SuppressWarnings("unchecked")
        T section = (T) new PackMetadataSection(Component.literal("GTRift dynamic shard item models"),
                SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES));
        return section;
    }

    @Override
    public String packId() {
        return NAME;
    }

    @Override
    public void close() {}
}
