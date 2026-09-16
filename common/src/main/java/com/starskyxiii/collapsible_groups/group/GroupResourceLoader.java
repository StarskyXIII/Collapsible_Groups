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
import java.util.function.Predicate;

public final class GroupResourceLoader {
    public static final String RESOURCE_DIRECTORY = "groups";
    public static final String CATALOG_RESOURCE = "assets/collapsible_groups/builtin_catalog.json";
    @FunctionalInterface
    private interface TextReader { String read() throws IOException; }

    private record Resource(GroupOrigin origin, ResourceLocation path, String expectedId, TextReader reader) {
        Document document() {
            try { return new Document(origin, reader.read(), expectedId, null); }
            catch (IOException | RuntimeException failure) { return new Document(origin, null, expectedId, failure.toString()); }
        }
    }

    public record Document(GroupOrigin origin, String json, String expectedId, String readError) {
        public Document(GroupOrigin origin, String json) { this(origin, json, null, null); }
    }

    public record Layer(List<Document> documents, boolean legacyConfig) {
        public Layer { documents = List.copyOf(documents); }
    }

    private GroupResourceLoader() {}

    public static GroupResourceData load(ResourceManager manager, Path configDirectory) {
        try {
            return load(GroupResourceLoader.class.getClassLoader(), manager == null ? List.of()
                : Services.PLATFORM.groupResourcePacks(manager), configDirectory, Services.PLATFORM::isModLoaded, Services.CONFIG.disabledBuiltinCategories());
        } catch (RuntimeException failure) {
            return assemble(List.of(new Layer(List.of(failure(GroupSource.RESOURCE_PACK, "resources", "resources", failure)), false)));
        }
    }

    static GroupResourceData load(ClassLoader loader, List<PackResources> packs, Path configDirectory, Predicate<String> modLoaded) {
        return load(loader, packs, configDirectory, modLoaded, Set.of());
    }

    static GroupResourceData load(ClassLoader loader, List<PackResources> packs, Path configDirectory,
        Predicate<String> modLoaded, Set<String> disabledCategories) {
        List<List<Resource>> sources = new ArrayList<>();
        List<Document> failures = new ArrayList<>();
        Set<String> builtinIds = new LinkedHashSet<>();
        Map<String, String> originalCategories = new LinkedHashMap<>();
        try {
            List<Resource> catalog = readBundled(loader);
            catalog.stream().map(Resource::expectedId).filter(java.util.Objects::nonNull).forEach(builtinIds::add);
            for (Resource resource : catalog) {
                ResourceLocation category = categoryId(resource.path());
                if (resource.expectedId() != null && category != null) originalCategories.put(resource.expectedId(), category.toString());
            }
            sources.add(catalog);
        } catch (IOException | RuntimeException failure) {
            failures.add(failure(GroupSource.BUILTIN, Constants.MOD_ID, CATALOG_RESOURCE, failure));
        }
        BuiltinCategoryPolicy policy = new BuiltinCategoryPolicy(originalCategories, disabledCategories);
        for (PackResources pack : packs) {
            if (ownPack(pack.packId())) continue;
            try {
                ResourceFilterSection filter = pack.getMetadataSection(ResourceFilterSection.TYPE);
                if (filter != null) sources.replaceAll(previous -> previous.stream().filter(resource ->
                    !filter.isNamespaceFiltered(resource.path().getNamespace()) || !filter.isPathFiltered(resource.path().getPath())).toList());
                sources.add(readPack(pack));
            } catch (IOException | RuntimeException failure) {
                failures.add(failure(GroupSource.RESOURCE_PACK, pack.packId(), "pack.mcmeta", failure));
            }
        }
        Map<ResourceLocation, Resource> metadata = new LinkedHashMap<>();
        List<GroupLoadProblem> problems = new ArrayList<>();
        for (List<Resource> source : sources) {
            for (Resource resource : source) {
                if (!isMetadata(resource.path())) continue;
                ResourceLocation category = categoryId(resource.path());
                if (category != null && resource.path().getPath().split("/").length == 3) metadata.put(category, resource);
                else problems.add(new GroupLoadProblem(null, resource.origin(), "Nested category metadata is ignored", false));
            }
        }
        Map<ResourceLocation, GroupCategory> categories = new LinkedHashMap<>();
        metadata.forEach((id, resource) -> {
            Document document = resource.document();
            try {
                if (document.readError() != null) throw new IllegalArgumentException(document.readError());
                categories.put(id, GroupCategory.parse(id, document.json(), modLoaded));
            } catch (RuntimeException failure) {
                failures.add(new Document(resource.origin(), null, null, failure.toString()));
            }
        });
        if (!failures.isEmpty()) {
            failures.forEach(document -> problems.add(new GroupLoadProblem(null, document.origin(), document.readError(), true)));
            return new GroupResourceData(List.of(), builtinIds, Map.of(), Map.of(), categories, problems, true, false, policy);
        }
        List<Layer> layers = new ArrayList<>();
        for (List<Resource> source : sources) {
            List<Document> documents = source.stream().filter(resource -> !isMetadata(resource.path()))
                .filter(resource -> resource.expectedId() == null || !policy.suppresses(resource.expectedId()))
                .filter(resource -> {
                    GroupCategory category = categories.get(categoryId(resource.path()));
                    return category == null || category.available();
                }).map(Resource::document).toList();
            layers.add(new Layer(documents, false));
        }
        layers.add(readDirectory(configDirectory.resolve("collapsiblegroups/groups")));
        GroupResourceData data = assemble(layers, policy);
        problems.addAll(data.problems());
        return new GroupResourceData(data.groups(), builtinIds, data.origins(), data.definitions(), categories,
            problems, data.rejected(), data.stale(), policy);
    }

    public static Map<String, GroupDisplayName> builtinCategories() {
        return builtinCategories(GroupResourceLoader.class.getClassLoader());
    }

    static Map<String, GroupDisplayName> builtinCategories(ClassLoader loader) {
        Map<String, GroupDisplayName> names = new LinkedHashMap<>();
        try {
            for (Resource resource : readBundled(loader)) {
                ResourceLocation id = categoryId(resource.path());
                if (id == null) continue;
                names.putIfAbsent(id.toString(), new GroupDisplayName.Localized(id.toString(), id.toString()));
                if (!isMetadata(resource.path())) continue;
                try {
                    names.put(id.toString(), GroupCategory.parse(id, resource.document().json(), ignored -> false).displayName());
                } catch (RuntimeException ignored) {}
            }
        } catch (IOException | RuntimeException failure) {
            Constants.LOG.warn("Could not read built-in category names", failure);
        }
        return Collections.unmodifiableMap(names);
    }

    private static boolean isMetadata(ResourceLocation path) {
        return path.getPath().endsWith("/metadata.json");
    }

    private static ResourceLocation categoryId(ResourceLocation path) {
        if (path == null) return null;
        String[] segments = path.getPath().split("/");
        return segments.length < 3 || !segments[0].equals(RESOURCE_DIRECTORY) ? null
            : new ResourceLocation(path.getNamespace(), segments[1]);
    }

    public static ResourceLocation categoryId(GroupOrigin origin) {
        return origin == null ? null : categoryId(resourceLocation(origin));
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

    private static List<Resource> readBundled(ClassLoader loader) throws IOException {
        JsonObject catalog = JsonParser.parseString(read(loader, CATALOG_RESOURCE)).getAsJsonObject();
        if (!catalog.has("version") || catalog.get("version").getAsInt() != 1 || !catalog.has("groups")) {
            throw new IOException("Unsupported built-in group catalog");
        }
        List<Resource> documents = new ArrayList<>();
        Set<String> paths = new LinkedHashSet<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement element : catalog.getAsJsonArray("groups")) {
            JsonObject entry = element.getAsJsonObject();
            String path = entry.get("path").getAsString();
            JsonElement rawId = entry.get("id");
            if (rawId == null || !rawId.isJsonPrimitive() || !rawId.getAsJsonPrimitive().isString()
                || rawId.getAsString().isBlank() || !ids.add(rawId.getAsString())) {
                throw new IOException("Invalid or duplicate built-in group ID: " + rawId);
            }
            String id = rawId.getAsString();
            if (!path.matches("assets/collapsible_groups/groups/[a-z0-9_./-]+\\.json")
                || path.contains("..") || path.endsWith("/metadata.json") || !paths.add(path)) {
                throw new IOException("Invalid or duplicate built-in resource path: " + path);
            }
            GroupOrigin origin = new GroupOrigin(GroupSource.BUILTIN, Constants.MOD_ID, path, null);
            documents.add(new Resource(origin, resourceLocation(origin), id, () -> read(loader, path)));
        }
        if (documents.isEmpty()) throw new IOException("Empty built-in group catalog");
        for (String path : List.copyOf(paths)) {
            ResourceLocation location = resourceLocation(new GroupOrigin(GroupSource.BUILTIN, Constants.MOD_ID, path, null));
            ResourceLocation category = categoryId(location);
            if (category == null) continue;
            String metadata = "assets/" + category.getNamespace() + "/groups/" + category.getPath() + "/metadata.json";
            if (paths.add(metadata)) {
                GroupOrigin origin = new GroupOrigin(GroupSource.BUILTIN, Constants.MOD_ID, metadata, null);
                documents.add(new Resource(origin, resourceLocation(origin), null, () -> read(loader, metadata)));
            }
        }
        documents.sort(Comparator.comparing(document -> document.origin().location()));
        return List.copyOf(documents);
    }

    private static String read(ClassLoader loader, String path) throws IOException {
        try (InputStream input = loader.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing built-in resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<Resource> readPack(PackResources pack) {
        List<Resource> documents = new ArrayList<>();
            pack.listResources(PackType.CLIENT_RESOURCES, Constants.MOD_ID, RESOURCE_DIRECTORY, (location, supplier) -> {
                    if (!location.getPath().endsWith(".json")) return;
                    GroupOrigin origin = new GroupOrigin(GroupSource.RESOURCE_PACK, pack.packId(), location.toString(), null);
                    documents.add(new Resource(origin, location, null, () -> {
                        try (InputStream input = supplier.get()) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
                    }));
            });
        documents.sort(Comparator.comparing(document -> document.origin().location()));
        return documents;
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
        return assemble(layers, BuiltinCategoryPolicy.EMPTY);
    }

    private static GroupResourceData assemble(List<Layer> layers, BuiltinCategoryPolicy policy) {
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
                    if (document.expectedId() == null && id != null && policy.suppresses(id)) continue;
                    if (layer.legacyConfig() && id != null && id.startsWith("__default_")) continue;
                    GroupDefinition group = GroupConfig.fromJsonChecked(document.json());
                    if (document.expectedId() != null && !document.expectedId().equals(group.id())) {
                        throw new IllegalArgumentException("Built-in group ID differs from its catalog entry");
                    }
                    if (document.origin().source() == GroupSource.BUILTIN) builtinIds.add(group.id());
                    List<GroupOrigin> earlier = layerOrigins.computeIfAbsent(group.id(), ignored -> new ArrayList<>());
                    if (definitions.containsKey(group.id())) {
                        problems.add(new GroupLoadProblem(group.id(), document.origin(),
                            "Duplicate ID also declared in " + earlier.get(0).location(), !layer.legacyConfig()));
                        rejected |= !layer.legacyConfig();
                    }
                    earlier.add(0, document.origin());
                    layerVersions.computeIfAbsent(group.id(), ignored -> new ArrayList<>()).add(0, group);
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
        return new GroupResourceData(List.copyOf(effective.values()), builtinIds, origins, versions, Map.of(), problems, rejected, false);
    }

    private static Document failure(GroupSource source, String sourceId, String location, Exception failure) {
        return new Document(new GroupOrigin(source, sourceId, location, null), null, null, failure.toString());
    }
}
