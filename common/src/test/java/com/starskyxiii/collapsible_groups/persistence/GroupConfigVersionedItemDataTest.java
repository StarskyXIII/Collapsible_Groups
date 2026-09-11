package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.*;
import com.starskyxiii.collapsible_groups.client.editor.GroupEditorDefinitionFactory;
import com.starskyxiii.collapsible_groups.group.*;
import com.starskyxiii.collapsible_groups.group.filter.*;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;
import com.starskyxiii.collapsible_groups.platform.TestPlatformHelper;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GroupConfigVersionedItemDataTest {
    @org.junit.jupiter.api.AfterEach void resetRepository() { GroupRepositoryTestAccess.replace(List.of()); }
    private static final IngredientView ITEM = new IngredientView() {
        public String ingredientType() { return "item"; }
        public ResourceLocation resourceLocation() { return ResourceLocation.parse("minecraft:stone"); }
        public boolean hasTag(ResourceLocation tag) { return false; }
        public boolean matchesExactStack(String value) { return false; }
    };

    @ParameterizedTest
    @ValueSource(strings = {"\"1\"", "1", "true", "false", "{}", "[]", "\"\"", "null", "\"null\"", "{\"n\":null,\"s\":\"1\",\"a\":[1,true]}", "\"quote \\\" and \\\\ slash\""})
    void typedComponentValuesSurviveNestedRulesDraftCopyAndRoundTrip(String literal) {
        JsonElement data = ItemDataPayload.parseLiteral(literal);
        GroupFilter leaf = Filters.itemComponentPathValue("minecraft:custom_data", "value", data);
        GroupDefinition group = new GroupDefinition("typed", "Typed", true,
            Filters.all(Filters.itemId("minecraft:stone"), Filters.not(Filters.any(leaf, Filters.itemId("minecraft:dirt")))));
        assertEquals(GroupDocumentFormat.V1, group.documentFormat());
        GroupFilter draft = GroupFilterRuleDraft.decode(group.filter(), GroupDocumentFormat.V1).copy().toFilter().orElseThrow();
        GroupDefinition loaded = GroupConfig.fromJson(GroupConfig.toJson(group.withFilter(draft)));
        assertNotNull(loaded);
        assertEquals(group, loaded);
        JsonObject serialized = JsonParser.parseString(GroupConfig.toJson(loaded)).getAsJsonObject();
        assertEquals(1, serialized.get("schema_version").getAsInt());
        JsonObject payload = serialized.getAsJsonObject("filter").getAsJsonArray("all").get(1).getAsJsonObject()
            .getAsJsonObject("not").getAsJsonArray("any").get(0).getAsJsonObject().getAsJsonObject("value");
        assertEquals(data, payload.get("data"));
        assertEquals(2, payload.size());
    }

    @Test void legacyStringsRemainLegacyThroughEditCopyAndReload() {
        String source = "{\"id\":\"__default_legacy\",\"name\":\"Legacy\",\"filter\":{\"type\":\"item\",\"component\":\"minecraft:custom_data\",\"path\":\"n\",\"value\":\"1\"}}";
        GroupDefinition group = GroupConfig.fromJson(source);
        assertNotNull(group);
        GroupDefinition edited = GroupEditorDefinitionFactory.create(group.id(), "Edited", false, group.filter(), group);
        GroupRepositoryTestAccess.replace(List.of(group));
        GroupDefinition copied = GroupCatalog.createCustomCopy(group, "Copy", List.of()).orElseThrow();
        for (GroupDefinition value : List.of(group, edited, copied)) {
            assertEquals(GroupDocumentFormat.LEGACY, value.documentFormat());
            JsonObject json = JsonParser.parseString(GroupConfig.toJson(value)).getAsJsonObject();
            assertFalse(json.has("schema_version"));
            assertEquals(new JsonPrimitive("1"), json.getAsJsonObject("filter").get("value"));
            GroupDefinition reloaded = GroupConfig.fromJson(json.toString());
            assertEquals(value, reloaded);
            assertNull(((GroupFilter.ComponentPath) reloaded.filter()).payload());
        }
    }

    @Test void blankOrdinaryGroupUsesV1AndCopiesRetainSourceFormat() {
        GroupDefinition blank = GroupEditorDefinitionFactory.create("__default_new", "New", true, Filters.itemId("minecraft:stone"), null);
        assertEquals(GroupDocumentFormat.V1, blank.documentFormat());
        GroupRepositoryTestAccess.replace(List.of(blank));
        assertEquals(GroupDocumentFormat.V1, GroupCatalog.createCustomCopy(blank, "Copied", List.of()).orElseThrow().documentFormat());
        assertEquals(1, JsonParser.parseString(GroupConfig.toJson(blank)).getAsJsonObject().get("schema_version").getAsInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.0", "1e0", "1.0000000000000000000000000000000000000000"})
    void numericSchemaVersionOneIsRecognized(String version) {
        assertEquals(GroupDocumentFormat.V1, GroupConfig.fromJson("{\"id\":\"one\",\"schema_version\":" + version
            + ",\"filter\":{\"type\":\"item\",\"id\":\"minecraft:stone\"}}").documentFormat());
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo.json", "other.json", "missing-id"})
    void knownSaveCannotOverwriteOrShadowAnUnknownDocument(String fileName, @TempDir Path directory) throws Exception {
        String previous = System.getProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY);
        System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, directory.toString());
        try {
            Path groups = directory.resolve("collapsiblegroups/groups");
            Files.createDirectories(groups);
            Path protectedFile = groups.resolve(fileName.equals("missing-id") ? "foo.json" : fileName);
            String raw = fileName.equals("missing-id") ? "{\"schema_version\":2,\"future\":null}"
                : "{\"schema_version\":2,\"id\":\"" + (fileName.equals("foo.json") ? "future_group" : "foo") + "\",\"future\":null}";
            Files.writeString(protectedFile, raw);
            assertFalse(GroupConfig.saveChecked(new GroupDefinition("foo", "Foo", true, Filters.itemId("minecraft:stone"))));
            assertEquals(raw, Files.readString(protectedFile));
            if (fileName.equals("other.json")) assertFalse(Files.exists(groups.resolve("foo.json")));
        } finally {
            if (previous == null) System.clearProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY);
            else System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "0", "-1", "\"1\"", "1.5", "null", "true", "{}", "[]"})
    void unsupportedDocumentStaysOpaqueAcrossAllWithMethodsAndQueries(String version) {
        JsonObject raw = JsonParser.parseString("{\"schema_version\":" + version + ",\"id\":\"__default_future\",\"future\":{\"null\":null},\"filter\":{\"type\":\"item\",\"id\":\"minecraft:stone\"}}").getAsJsonObject();
        GroupDefinition group = GroupConfig.fromJson(raw.toString());
        assertNotNull(group);
        GroupDefinition edited = group.withEnabled(true).withName("New").withFilter(Filters.itemId("minecraft:stone"))
            .withIconIds(List.of("minecraft:dirt")).withTheme(GroupTheme.EMPTY).withPriority(8).withExtra(new JsonObject());
        assertEquals(GroupDocumentFormat.UNSUPPORTED, edited.documentFormat());
        assertEquals(raw, JsonParser.parseString(GroupConfig.toJson(edited)));
        assertTrue(edited.hasUnavailableFilter());
        assertFalse(edited.isStructurallyEditable());
        assertEquals(CompiledFilter.Evaluation.UNAVAILABLE, edited.query().evaluate(ITEM));
        assertFalse(edited.query().matches(ITEM));
        assertTrue(GroupCatalog.createCustomCopy(edited, "Copy", List.of()).isEmpty());
        edited.rawDocument().addProperty("schema_version", 1);
        assertEquals(raw, edited.rawDocument());
        GroupDefinition reloaded = GroupConfig.fromJson(GroupConfig.toJson(edited));
        assertEquals(GroupDocumentFormat.UNSUPPORTED, reloaded.documentFormat());
        assertEquals(raw, reloaded.rawDocument());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"1\"", "{}", "{\"data_format\":\"future:type\",\"data\":1}", "{\"data_format\":\"minecraft:data_component\"}", "{\"data_format\":null,\"data\":1}", "{\"data_format\":1,\"data\":1}"})
    void malformedOrUnknownPayloadsPreserveTheirWholeAtomicNode(String payload) {
        JsonObject node = JsonParser.parseString("{\"type\":\"item\",\"component\":\"minecraft:damage\",\"value\":" + payload + ",\"future_field\":null}").getAsJsonObject();
        GroupFilter parsed = GroupConfig.parseFilter(node, GroupDocumentFormat.V1);
        assertInstanceOf(GroupFilter.Unsupported.class, parsed);
        assertEquals(node, GroupConfig.serializeFilter(parsed, GroupDocumentFormat.V1));
        assertEquals(CompiledFilter.Evaluation.UNAVAILABLE, CompiledFilter.compile(Filters.not(parsed)).evaluate(ITEM));
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "true", "null", "\"\"", "\"BAD ID\"", "{}", "[]"})
    void malformedComponentIdentifiersRemainRaw(String component) {
        JsonObject node = JsonParser.parseString("{\"type\":\"item\",\"component\":" + component
            + ",\"value\":{\"data_format\":\"minecraft:data_component\",\"data\":1}}").getAsJsonObject();
        GroupFilter parsed = GroupConfig.parseFilter(node, GroupDocumentFormat.V1);
        assertInstanceOf(GroupFilter.Unsupported.class, parsed);
        assertEquals(node, GroupConfig.serializeFilter(parsed, GroupDocumentFormat.V1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "true", "null", "{}", "[]", "\"bad[*]\""})
    void malformedComponentPathsRemainRaw(String path) {
        JsonObject node = JsonParser.parseString("{\"type\":\"item\",\"component\":\"minecraft:custom_data\",\"path\":" + path
            + ",\"value\":{\"data_format\":\"minecraft:data_component\",\"data\":1}}").getAsJsonObject();
        GroupFilter parsed = GroupConfig.parseFilter(node, GroupDocumentFormat.V1);
        assertInstanceOf(GroupFilter.Unsupported.class, parsed);
        assertEquals(node, GroupConfig.serializeFilter(parsed, GroupDocumentFormat.V1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[\"item\"]", "[[\"item\"]]", "1", "true", "null", "{}"})
    void malformedVersionedItemTypeRemainsOpaque(String type) {
        JsonObject raw = JsonParser.parseString("{\"type\":" + type
            + ",\"stack\":{\"data_format\":\"minecraft:item_components\",\"data\":{\"id\":\"minecraft:stone\"}}}").getAsJsonObject();
        GroupFilter parsed = GroupConfig.parseFilter(raw, GroupDocumentFormat.V1);
        assertInstanceOf(GroupFilter.Unsupported.class, parsed);
        assertEquals(raw, GroupConfig.serializeFilter(parsed, GroupDocumentFormat.V1));
    }

    @Test void mixedOrExtendedDataNodesStayOpaqueInsteadOfDroppingConditions() {
        for (String source : List.of(
            "{\"type\":\"item\",\"component\":\"minecraft:damage\",\"value\":{\"data_format\":\"minecraft:data_component\",\"data\":1},\"stack\":{\"data_format\":\"minecraft:item_components\",\"data\":{\"id\":\"minecraft:stone\"}}}",
            "{\"type\":\"item\",\"component\":\"minecraft:damage\",\"value\":{\"data_format\":\"minecraft:data_component\",\"data\":1},\"future_condition\":true}",
            "{\"type\":\"item\",\"component\":\"minecraft:damage\",\"value\":{\"data_format\":\"minecraft:data_component\",\"data\":1,\"future\":null}}",
            "{\"any\":[{\"type\":\"item\",\"id\":\"minecraft:stone\"}],\"stack\":{\"data_format\":\"minecraft:item_components\",\"data\":{\"id\":\"minecraft:stone\"}}}")) {
            JsonObject node = JsonParser.parseString(source).getAsJsonObject();
            GroupFilter parsed = GroupConfig.parseFilter(node, GroupDocumentFormat.V1);
            assertInstanceOf(GroupFilter.Unsupported.class, parsed);
            assertEquals(node, GroupConfig.serializeFilter(parsed, GroupDocumentFormat.V1));
            assertEquals(CompiledFilter.Evaluation.UNAVAILABLE, CompiledFilter.compile(Filters.not(parsed)).evaluate(ITEM));
        }
    }

    @Test void typedExactPayloadSurvivesContentsAndRuleDrafts() {
        ItemDataPayload payload = new ItemDataPayload(ItemDataPayload.ITEM_COMPONENTS,
            JsonParser.parseString("{\"id\":\"minecraft:stone\",\"count\":1,\"components\":{\"!minecraft:max_stack_size\":{}}}"));
        GroupFilter exact = new GroupFilter.ExactStack(payload);
        GroupFilter contents = GroupFilterEditorDraft.decode(exact).draft().toFilter().orElseThrow();
        assertEquals(exact, contents);
        assertEquals(exact, GroupFilterRuleDraft.decode(contents, GroupDocumentFormat.V1).copy().toFilter().orElseThrow());
        GroupDefinition group = new GroupDefinition("exact", "Exact", true, exact);
        assertEquals(group, GroupConfig.fromJson(GroupConfig.toJson(group)));
    }

    @Test void mixedLegacyAndTypedComponentDefinitionsCannotBeSilentlyReencoded(@TempDir Path directory) throws Exception {
        GroupDefinition mixed = new GroupDefinition("mixed", "Mixed", true,
            Filters.any(Filters.itemComponent("minecraft:damage", "1"), Filters.itemComponentValue("minecraft:damage", new JsonPrimitive(1))));
        assertEquals(GroupDocumentFormat.V1, mixed.documentFormat());
        assertThrows(IllegalArgumentException.class, () -> GroupConfig.toJson(mixed));
        String previous = System.getProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY);
        System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, directory.toString());
        try {
            assertFalse(GroupConfig.saveChecked(mixed));
            assertFalse(Files.exists(directory.resolve("collapsiblegroups/groups/mixed.json")));
            GroupDefinition unsupported = GroupConfig.fromJson("{\"id\":\"future\",\"schema_version\":null}");
            assertFalse(GroupConfig.saveChecked(unsupported.withEnabled(true)));
            assertFalse(Files.exists(directory.resolve("collapsiblegroups/groups/future.json")));
        } finally {
            if (previous == null) System.clearProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY);
            else System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, previous);
        }
    }

    @Test void typedCanonicalConstructorsCannotDisagreeWithTheirSerializedData() {
        ItemDataPayload number = new ItemDataPayload(ItemDataPayload.DATA_COMPONENT, new JsonPrimitive(1));
        assertEquals("1", new GroupFilter.HasComponent("minecraft:damage", "2", number).encodedValue());
        assertEquals("1", new GroupFilter.ComponentPath("minecraft:custom_data", "n", "2", number).expectedValue());
        ItemDataPayload stone = new ItemDataPayload(ItemDataPayload.ITEM_COMPONENTS, JsonParser.parseString("{\"id\":\"minecraft:stone\"}"));
        assertEquals(stone.data().toString(), new GroupFilter.ExactStack("wrong", stone).encodedStack());
        GroupFilter wrongFormat = new GroupFilter.ExactStack(new ItemDataPayload(ItemDataPayload.NBT, stone.data()));
        assertEquals(CompiledFilter.Evaluation.UNAVAILABLE, CompiledFilter.compile(Filters.not(wrongFormat)).evaluate(ITEM));
    }
}
