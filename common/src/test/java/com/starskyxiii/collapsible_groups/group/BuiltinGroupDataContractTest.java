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
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BuiltinGroupDataContractTest {
    private static final Path ROOT = Path.of(System.getProperty("collapsibleGroupsRoot"));

    @Test void generatedCatalogsCoverEachLoadersSourceRootsInResourceOrder() throws Exception {
        var roots = Map.of("fabric", List.of("common", "fabric-neoforge"), "forge", List.of("common"),
            "neoforge", List.of("common", "fabric-neoforge", "neoforge"));
        for (var loader : roots.entrySet()) {
            List<JsonObject> expected = new ArrayList<>();
            var ids = new HashSet<String>();
            var paths = new HashSet<String>();
            for (String name : loader.getValue()) {
                Path root = ROOT.resolve("builtin-groups").resolve(name);
                try (var files = Files.walk(root)) {
                    for (Path file : files.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(".json")).toList()) {
                        String path = root.relativize(file).toString().replace('\\', '/');
                        assertTrue(path.matches("assets/collapsible_groups/groups/[a-z0-9_./-]+\\.json"), path);
                        String filename = file.getFileName().toString();
                        assertTrue(filename.matches("[0-9]+_.*\\.json"), filename);
                        var source = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                        var group = GroupConfig.fromJsonChecked(source.toString());
                        assertEquals(GroupDocumentFormat.V1, group.documentFormat());
                        assertTrue(ids.add(group.id()), group.id());
                        assertTrue(paths.add(path), path);
                        assertEquals(source, resource(path));
                        var entry = new JsonObject();
                        entry.addProperty("path", path);
                        entry.addProperty("id", group.id());
                        expected.add(entry);
                    }
                }
            }
            expected.sort(java.util.Comparator.comparing(entry -> entry.get("path").getAsString()));
            assertFalse(expected.isEmpty());
            var actual = JsonParser.parseString(Files.readString(ROOT.resolve(loader.getKey())
                .resolve("build/generated/builtin-groups/assets/collapsible_groups/builtin_catalog.json")))
                .getAsJsonObject().getAsJsonArray("groups");
            assertEquals(expected, actual.asList());
        }
    }

    @Test void bundledDefinitionsHaveSeparateEnglishFallbacksAndBuiltinOrigins() throws Exception {
        var bundled = GroupResourceLoader.readBundled(getClass().getClassLoader());
        var data = GroupResourceLoader.assemble(List.of(new GroupResourceLoader.Layer(bundled, false)));
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
        assertEquals(keys, english.keySet());
    }

    private static JsonObject resource(String path) throws Exception {
        try (var reader = new InputStreamReader(BuiltinGroupDataContractTest.class.getResourceAsStream("/" + path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
