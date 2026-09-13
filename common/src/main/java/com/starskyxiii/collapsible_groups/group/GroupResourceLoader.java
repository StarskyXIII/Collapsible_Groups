package com.starskyxiii.collapsible_groups.group;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.persistence.GroupConfig;
import com.starskyxiii.collapsible_groups.platform.Services;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceFilterSection;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GroupResourceLoader {
    public static final String RESOURCE_DIRECTORY = "groups";
    public static final String CATALOG_RESOURCE = "assets/collapsible_groups/builtin_catalog.json";
    private static volatile List<Document> bundled;

    public record Document(GroupOrigin origin, String json, String expectedId, String readError) {
        public Document(GroupOrigin origin, String json) { this(origin, json, null, null); }
    }

    public record Layer(List<Document> documents, boolean legacyConfig) {
        public Layer { documents = List.copyOf(documents); }
    }

    private GroupResourceLoader() {}

    public static GroupResourceData load(ResourceManager manager, Path configDirectory) {
        List<Layer> layers = new ArrayList<>();
        List<Document> catalog = bundled();
        layers.add(new Layer(catalog, false));
        if (manager != null) {
            try {
                for (PackResources pack : Services.PLATFORM.groupResourcePacks(manager)) {
                    if (ownPack(pack.packId())) continue;
                    applyPackFilter(layers, pack);
                    layers.add(readPack(pack));
                }
            } catch (RuntimeException failure) {
                layers.add(new Layer(List.of(failure(GroupSource.RESOURCE_PACK, "resources", "resources", failure)), false));
            }
        }
        layers.add(readDirectory(configDirectory.resolve("collapsiblegroups/groups")));
        GroupResourceData data = assemble(layers);
        Set<String> builtinIds = new LinkedHashSet<>(data.builtinIds());
        catalog.stream().map(Document::expectedId).filter(java.util.Objects::nonNull).forEach(builtinIds::add);
        return new GroupResourceData(data.groups(), builtinIds, data.origins(), data.definitions(), data.problems(), data.rejected(), data.stale());
    }

    private static void applyPackFilter(List<Layer> layers, PackResources pack) {
        try {
            ResourceFilterSection filter = pack.getMetadataSection(ResourceFilterSection.TYPE);
            if (filter == null) return;
            for (int i = 0; i < layers.size(); i++) {
                Layer previous = layers.get(i);
                layers.set(i, new Layer(previous.documents().stream().filter(document -> {
                    ResourceLocation path = resourceLocation(document.origin());
                    return document.readError() != null || path == null || !filter.isNamespaceFiltered(path.getNamespace()) || !filter.isPathFiltered(path.getPath());
                }).toList(), previous.legacyConfig()));
            }
        } catch (IOException | RuntimeException failure) {
            layers.add(new Layer(List.of(failure(GroupSource.RESOURCE_PACK, pack.packId(), "pack.mcmeta", failure)), false));
        }
    }

    private static ResourceLocation resourceLocation(GroupOrigin origin) {
        if (origin.source() == GroupSource.BUILTIN && origin.location().startsWith("assets/")) {
            String path = origin.location().substring("assets/".length());
            int slash = path.indexOf('/');
            return slash < 0 ? null : ResourceLocation.tryParse(path.substring(0, slash) + ":" + path.substring(slash + 1));
        }
        return origin.source() == GroupSource.RESOURCE_PACK && origin.location().contains(":")
            ? ResourceLocation.tryParse(origin.location()) : null;
    }

    static boolean ownPack(String id) {
        return Constants.MOD_ID.equals(id) || ("mod/" + Constants.MOD_ID).equals(id)
            || ("mod:" + Constants.MOD_ID).equals(id);
    }

    private static List<Document> bundled() {
        List<Document> result = bundled;
        if (result != null) return result;
        synchronized (GroupResourceLoader.class) {
            if (bundled != null) return bundled;
            try {
                result = readBundled(GroupResourceLoader.class.getClassLoader());
                bundled = result;
                return result;
            } catch (IOException | RuntimeException failure) {
                return List.of(failure(GroupSource.BUILTIN, Constants.MOD_ID, CATALOG_RESOURCE, failure));
            }
        }
    }

    public static List<Document> readBundled(ClassLoader loader) throws IOException {
        JsonObject catalog = JsonParser.parseString(read(loader, CATALOG_RESOURCE)).getAsJsonObject();
        if (!catalog.has("version") || catalog.get("version").getAsInt() != 1 || !catalog.has("groups")) {
            throw new IOException("Unsupported built-in group catalog");
        }
        List<Document> documents = new ArrayList<>();
        Set<String> paths = new LinkedHashSet<>();
        for (JsonElement element : catalog.getAsJsonArray("groups")) {
            JsonObject entry = element.getAsJsonObject();
            String path = entry.get("path").getAsString();
            String id = entry.get("id").getAsString();
            if (!path.matches("assets/collapsible_groups/groups/[a-z0-9_./-]+\\.json")
                || path.contains("..") || !paths.add(path)) {
                throw new IOException("Invalid or duplicate built-in resource path: " + path);
            }
            GroupOrigin origin = new GroupOrigin(GroupSource.BUILTIN, Constants.MOD_ID, path, null);
            documents.add(new Document(origin, read(loader, path), id, null));
        }
        if (documents.isEmpty()) throw new IOException("Empty built-in group catalog");
        documents.sort(Comparator.comparing(document -> document.origin().location()));
        return List.copyOf(documents);
    }

    private static String read(ClassLoader loader, String path) throws IOException {
        try (InputStream input = loader.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing built-in resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Layer readPack(PackResources pack) {
        List<Document> documents = new ArrayList<>();
        try {
            pack.listResources(PackType.CLIENT_RESOURCES, Constants.MOD_ID, RESOURCE_DIRECTORY, (location, supplier) -> {
                    if (!location.getPath().endsWith(".json")) return;
                    GroupOrigin origin = new GroupOrigin(GroupSource.RESOURCE_PACK, pack.packId(), location.toString(), null);
                    try (InputStream input = supplier.get()) {
                        documents.add(new Document(origin, new String(input.readAllBytes(), StandardCharsets.UTF_8)));
                    } catch (IOException | RuntimeException failure) {
                        documents.add(new Document(origin, null, null, failure.toString()));
                    }
            });
        } catch (RuntimeException failure) {
            documents.add(failure(GroupSource.RESOURCE_PACK, pack.packId(), pack.packId(), failure));
        }
        documents.sort(Comparator.comparing(document -> document.origin().location()));
        return new Layer(documents, false);
    }

    public static Layer readDirectory(Path directory) {
        List<Document> documents = new ArrayList<>();
        if (Files.notExists(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return new Layer(documents, true);
        if (!Files.isDirectory(directory)) return new Layer(List.of(failure(GroupSource.USER, GroupSource.USER.name(), directory.toString(),
            new IOException("Group source directory is unavailable or is not a directory"))), true);
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                GroupOrigin origin = new GroupOrigin(GroupSource.USER, GroupSource.USER.name(), path.toAbsolutePath().normalize().toString(), path);
                try {
                    if (!path.toRealPath().startsWith(directory.toRealPath())) throw new IOException("Group file is outside its source directory");
                    documents.add(new Document(origin, Files.readString(path, StandardCharsets.UTF_8)));
                } catch (IOException | RuntimeException failure) {
                    documents.add(new Document(origin, null, null, failure.toString()));
                }
            }
        } catch (IOException | RuntimeException failure) {
            documents.add(failure(GroupSource.USER, GroupSource.USER.name(), directory.toString(), failure));
        }
        return new Layer(documents, true);
    }

    public static GroupResourceData assemble(List<Layer> layers) {
        Map<String, GroupDefinition> effective = new LinkedHashMap<>();
        Map<String, List<GroupOrigin>> origins = new LinkedHashMap<>();
        Map<String, List<GroupDefinition>> versions = new LinkedHashMap<>();
        Set<String> builtinIds = new LinkedHashSet<>();
        List<GroupLoadProblem> problems = new ArrayList<>();
        boolean rejected = false;
        for (Layer layer : layers) {
            Map<String, GroupDefinition> definitions = new LinkedHashMap<>();
            Map<String, List<GroupOrigin>> layerOrigins = new LinkedHashMap<>();
            Map<String, List<GroupDefinition>> layerVersions = new LinkedHashMap<>();
            Map<String, Document> failedIds = new LinkedHashMap<>();
            for (Document document : layer.documents()) {
                String id = document.expectedId();
                try {
                    if (document.readError() != null) throw new IllegalArgumentException(document.readError());
                    JsonObject raw = JsonParser.parseString(document.json()).getAsJsonObject();
                    if (raw.has("id") && raw.get("id").isJsonPrimitive() && raw.get("id").getAsJsonPrimitive().isString()) {
                        id = raw.get("id").getAsString();
                    }
                    if (layer.legacyConfig() && id != null && id.startsWith("__default_")) continue;
                    GroupDefinition group = GroupConfig.fromJsonChecked(document.json());
                    if (document.expectedId() != null && !document.expectedId().equals(group.id())) {
                        throw new IllegalArgumentException("Built-in group ID differs from its catalog entry");
                    }
                    if (document.origin().source() == GroupSource.BUILTIN) builtinIds.add(group.id());
                    List<GroupOrigin> earlier = layerOrigins.computeIfAbsent(group.id(), ignored -> new ArrayList<>());
                    if (definitions.containsKey(group.id())) {
                        problems.add(new GroupLoadProblem(group.id(), document.origin(),
                            "Duplicate ID also declared in " + earlier.getFirst().location(), !layer.legacyConfig()));
                        rejected |= !layer.legacyConfig();
                    }
                    earlier.addFirst(document.origin());
                    layerVersions.computeIfAbsent(group.id(), ignored -> new ArrayList<>()).addFirst(group);
                    definitions.put(group.id(), group);
                } catch (RuntimeException failure) {
                    if (layer.legacyConfig() && id != null && id.startsWith("__default_")) continue;
                    problems.add(new GroupLoadProblem(id, document.origin(), failure.getMessage(), true));
                    rejected |= !layer.legacyConfig();
                    if (id != null && !id.isBlank()) failedIds.put(id, document);
                }
            }
            if (layer.legacyConfig()) {
                failedIds.forEach((id, document) -> {
                    if (definitions.containsKey(id)) return;
                    JsonObject invalid = new JsonObject();
                    invalid.addProperty("id", id);
                    invalid.addProperty("schema_version", "invalid");
                    definitions.put(id, GroupConfig.fromJsonChecked(invalid.toString()));
                    layerOrigins.put(id, List.of(document.origin()));
                    layerVersions.put(id, List.of(definitions.get(id)));
                });
            }
            definitions.forEach((id, definition) -> {
                effective.put(id, definition);
                List<GroupOrigin> history = new ArrayList<>(layerOrigins.get(id));
                history.addAll(origins.getOrDefault(id, List.of()));
                origins.put(id, history);
                List<GroupDefinition> previous = new ArrayList<>(layerVersions.get(id));
                previous.addAll(versions.getOrDefault(id, List.of()));
                versions.put(id, previous);
            });
        }
        return new GroupResourceData(List.copyOf(effective.values()), builtinIds, origins, versions, problems, rejected, false);
    }

    private static Document failure(GroupSource source, String sourceId, String location, Exception failure) {
        return new Document(new GroupOrigin(source, sourceId, location, null), null, null, failure.toString());
    }
}
