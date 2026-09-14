package com.starskyxiii.collapsible_groups.group;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.persistence.GroupConfig;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BuiltinGroupDataContractTest {
    private static final Path ROOT = Path.of(System.getProperty("collapsibleGroupsRoot"));

    @Test void generatedCatalogsCoverCategoryLoaderMembershipInResourceOrder() throws Exception {
        Path root = ROOT.resolve("builtin-groups");
        for (String loader : List.of("fabric", "forge", "neoforge")) {
            List<JsonObject> expected = new ArrayList<>();
            var ids = new HashSet<String>();
            var paths = new HashSet<String>();
            try (var categories = Files.list(root)) {
                for (Path category : categories.filter(Files::isDirectory).toList()) {
                    var metadata = JsonParser.parseString(Files.readString(category.resolve("metadata.json"))).getAsJsonObject();
                    boolean included = metadata.getAsJsonArray("loaders").asList().stream()
                        .anyMatch(value -> loader.equals(value.getAsString()));
                    if (!included) continue;
                    try (var files = Files.list(category)) {
                        for (Path file : files.filter(Files::isRegularFile)
                            .filter(f -> f.toString().endsWith(".json") && !f.getFileName().toString().equals("metadata.json")).toList()) {
                            String path = "assets/collapsible_groups/groups/" + category.getFileName() + "/" + file.getFileName();
                            var source = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                            var group = GroupConfig.fromJsonChecked(source.toString());
                            assertEquals(GroupDocumentFormat.V1, group.documentFormat());
                            assertTrue(ids.add(group.id()), group.id());
                            assertTrue(paths.add(path), path);
                            assertEquals(source, resource(path));
                            Path output = ROOT.resolve(loader).resolve("build/generated/builtin-groups");
                            assertEquals(source, JsonParser.parseString(Files.readString(output.resolve(path))));
                            assertEquals(metadata, JsonParser.parseString(Files.readString(output.resolve("assets/collapsible_groups/groups/"
                                + category.getFileName() + "/metadata.json"))));
                            var entry = new JsonObject();
                            entry.addProperty("path", path);
                            entry.addProperty("id", group.id());
                            expected.add(entry);
                        }
                    }
                }
            }
            expected.sort(java.util.Comparator.comparing(entry -> entry.get("path").getAsString()));
            assertFalse(expected.isEmpty());
            var actual = JsonParser.parseString(Files.readString(ROOT.resolve(loader)
                .resolve("build/generated/builtin-groups/assets/collapsible_groups/builtin_catalog.json")))
                .getAsJsonObject().getAsJsonArray("groups");
            assertEquals(expected, actual.asList());
        }
    }

    @Test void bundledDefinitionsHaveSeparateEnglishFallbacksAndBuiltinOrigins() throws Exception {
        var data = GroupResourceLoader.load(getClass().getClassLoader(), List.of(), ROOT.resolve("build/empty-config"), mod -> true);
        assertTrue(data.complete(), data.problems().toString());
        assertFalse(data.groups().isEmpty());
        var english = resource("assets/collapsible_groups/group_lang/en_us.json");
        var ui = resource("assets/collapsible_groups/lang/en_us.json");
        var keys = new HashSet<String>();
        for (GroupDefinition group : data.groups()) {
            String key = group.displayName().key();
            keys.add(key);
            assertEquals(group.displayName().fallback(), english.get(key).getAsString(), group.id());
            assertFalse(ui.has(key), key);
            assertEquals(GroupSource.BUILTIN, data.origin(group.id()).source());
        }
        for (GroupCategory category : data.categories().values()) {
            String key = category.displayName().key();
            keys.add(key);
            assertEquals(category.displayName().fallback(), english.get(key).getAsString());
            assertFalse(ui.has(key), key);
        }
        assertEquals(keys, english.keySet());
    }

    private static JsonObject resource(String path) throws Exception {
        try (var reader = new InputStreamReader(BuiltinGroupDataContractTest.class.getResourceAsStream("/" + path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
