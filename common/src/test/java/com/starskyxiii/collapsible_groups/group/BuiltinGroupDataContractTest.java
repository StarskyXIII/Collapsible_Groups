package com.starskyxiii.collapsible_groups.group;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.starskyxiii.collapsible_groups.persistence.GroupConfig;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinGroupDataContractTest {
    private static final Path ROOT = Path.of(System.getProperty("collapsibleGroupsRoot"));

    @TestFactory Stream<DynamicTest> everyConvertedDefinitionPreservesItsProviderContract() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement element : reference()) {
            JsonObject entry = element.getAsJsonObject();
            JsonObject original = entry.getAsJsonObject("definition");
            String path = entry.get("path").getAsString();
            tests.add(DynamicTest.dynamicTest(original.get("id").getAsString(), () -> {
                JsonObject actual = resource(path);
                GroupDefinition parsed = GroupConfig.fromJsonChecked(actual.toString());
                assertEquals(GroupDocumentFormat.V1, parsed.documentFormat());
                assertEquals(parsed, GroupConfig.fromJsonChecked(GroupConfig.toJson(parsed)));
                JsonObject normalized = actual.deepCopy();
                normalized.remove("schema_version");
                assertEquals(original, normalized);
                assertEquals(0, parsed.priority());
            }));
        }
        assertEquals(528, tests.size());
        return tests.stream();
    }

    @Test void loadersKeepTheirOriginalDefinitionMembershipAndRegistrationOrder() throws Exception {
        for (var loader : Map.of("fabric", 528, "forge", 229).entrySet()) {
            JsonArray actual = JsonParser.parseString(Files.readString(ROOT.resolve(loader.getKey())
                .resolve("build/generated/builtin-groups/assets/collapsible_groups/builtin_catalog.json")))
                .getAsJsonObject().getAsJsonArray("groups");
            List<String> expectedIds = new ArrayList<>();
            for (JsonElement item : reference()) {
                JsonObject entry = item.getAsJsonObject();
                if (entry.getAsJsonArray("loaders").contains(new JsonPrimitive(loader.getKey()))) {
                    expectedIds.add(entry.getAsJsonObject("definition").get("id").getAsString());
                }
            }
            assertEquals(loader.getValue(), actual.size());
            assertEquals(expectedIds, actual.asList().stream().map(e -> e.getAsJsonObject().get("id").getAsString()).toList());
        }
    }

    @Test void bundledCatalogLoadsWithoutIntegrationModsAndMatchesEnglishNames() throws Exception {
        var bundled = GroupResourceLoader.readBundled(getClass().getClassLoader());
        var data = GroupResourceLoader.assemble(List.of(new GroupResourceLoader.Layer(bundled, false)));
        assertTrue(data.complete(), data.problems().toString());
        assertEquals(528, data.groups().size());
        JsonObject english = resource("assets/collapsible_groups/lang/en_us.json");
        for (GroupDefinition group : data.groups()) {
            assertEquals(group.displayName().fallback(), english.get(group.displayName().key()).getAsString(), group.id());
            assertEquals(GroupSource.BUILTIN, data.origin(group.id()).source());
        }
    }

    private static JsonArray reference() throws Exception {
        try (var reader = new InputStreamReader(BuiltinGroupDataContractTest.class.getResourceAsStream(
            "/fixtures/builtin-provider-reference.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonArray();
        }
    }

    private static JsonObject resource(String path) throws Exception {
        try (var reader = new InputStreamReader(BuiltinGroupDataContractTest.class.getResourceAsStream("/" + path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
