package com.starskyxiii.collapsible_groups.group;

import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.persistence.GroupConfig;
import com.starskyxiii.collapsible_groups.persistence.GroupFileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GroupResourceLoaderTest {
    @TempDir Path config;

    @Test void wholeDefinitionPriorityUsesConfigThenPackThenBuiltinThenScript() {
        var data = GroupResourceLoader.assemble(List.of(
            layer(doc(GroupSource.BUILTIN, "builtin", "same", "minecraft:stone", true, 0)),
            layer(doc(GroupSource.RESOURCE_PACK, "low", "same", "minecraft:dirt", true, 50)),
            layer(doc(GroupSource.RESOURCE_PACK, "high", "same", "minecraft:diamond", true, -1)),
            new GroupResourceLoader.Layer(List.of(doc(GroupSource.USER, "user", "same", "minecraft:apple", false, 1)), true)));
        assertTrue(data.complete());
        assertEquals(List.of("same"), data.groups().stream().map(GroupDefinition::id).toList());
        assertEquals("minecraft:apple", ((com.starskyxiii.collapsible_groups.group.filter.GroupFilter.Id) data.groups().get(0).filter()).id());
        assertFalse(data.groups().get(0).enabled());
        assertEquals(1, data.groups().get(0).priority());
        assertTrue(data.builtinIds().contains("same"));
        assertEquals(List.of("user", "high", "low", "builtin"),
            data.origins().get("same").stream().map(GroupOrigin::sourceId).toList());
        GroupService service = new GroupService();
        service.replaceSource(new GroupService.SourceKey(GroupSource.KUBEJS, "script"),
            List.of(GroupConfig.fromJsonChecked(doc(GroupSource.KUBEJS, "script", "same", "minecraft:emerald", true, 100).json())));
        service.replaceManaged(data, Map.of(), false);
        assertFalse(service.findById("same").orElseThrow().enabled());
        assertEquals(data.groups().get(0), service.findById("same").orElseThrow());
    }

    @Test void duplicateNewLayerRejectsAtomicallyAndNeverPublishesLowerDefinition() {
        GroupService service = new GroupService();
        var previous = GroupResourceLoader.assemble(List.of(layer(doc(GroupSource.BUILTIN, "builtin", "same", "minecraft:stone", true, 0))));
        assertTrue(service.replaceManaged(previous, Map.of(), true));
        var rejected = GroupResourceLoader.assemble(List.of(
            layer(doc(GroupSource.BUILTIN, "changed", "same", "minecraft:diamond", true, 0)),
            layer(doc(GroupSource.RESOURCE_PACK, "pack/a", "same", "minecraft:gold_ingot", true, 0),
                doc(GroupSource.RESOURCE_PACK, "pack/b", "same", "minecraft:emerald", true, 0))));
        assertTrue(rejected.rejected());
        assertFalse(service.replaceManaged(rejected, Map.of(), true));
        assertTrue(service.resources().stale());
        assertEquals(previous.groups(), service.allPriorityOrder());
        assertTrue(service.resources().problems().get(0).reason().contains("pack/a"));
    }

    @Test void invalidHighLayerRetainsPreviousGenerationAndFreshFailureStaysEmpty() {
        var malformed = new GroupResourceLoader.Document(new GroupOrigin(GroupSource.RESOURCE_PACK, "bad", "bad.json", null), "{");
        var rejected = GroupResourceLoader.assemble(List.of(layer(doc(GroupSource.BUILTIN, "builtin", "same", "minecraft:stone", true, 0)), layer(malformed)));
        GroupService service = new GroupService();
        assertFalse(service.replaceManaged(rejected, Map.of(), true));
        assertTrue(service.allPriorityOrder().isEmpty());
        assertTrue(service.resources().stale());
        assertFalse(service.resources().complete());
    }

    @Test void normalConfigKeepsSortedLastValidPolicyAndIgnoresReservedOldFiles() throws Exception {
        Path directory = config.resolve("collapsiblegroups/groups");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("a.json"), doc(GroupSource.USER, "a", "ordinary", "minecraft:stone", true, 0).json());
        Files.writeString(directory.resolve("b.json"), doc(GroupSource.USER, "b", "ordinary", "minecraft:dirt", false, 1).json());
        Files.writeString(directory.resolve("c.json"), "{\"id\":\"ordinary\",\"filter\":null}");
        Files.writeString(directory.resolve("old.json"), doc(GroupSource.USER, "old", "__default_ignored", "minecraft:diamond", true, 0).json());
        var data = GroupResourceLoader.assemble(List.of(GroupResourceLoader.readDirectory(directory)));
        assertFalse(data.rejected());
        assertEquals(1, data.groups().size());
        assertFalse(data.groups().get(0).enabled());
        assertEquals(directory.resolve("b.json").toAbsolutePath(), data.origin("ordinary").file());
        assertTrue(data.problems().stream().anyMatch(GroupLoadProblem::error));
    }

    @Test void invalidKnownNormalIdBlocksLowerButUnknownVersionRemainsVisible() {
        var data = GroupResourceLoader.assemble(List.of(layer(doc(GroupSource.RESOURCE_PACK, "pack", "same", "minecraft:stone", true, 0)),
            new GroupResourceLoader.Layer(List.of(new GroupResourceLoader.Document(
                new GroupOrigin(GroupSource.USER, "bad", "bad.json", config.resolve("collapsiblegroups/groups/bad.json")),
                "{\"id\":\"same\",\"filter\":null}")), true)));
        assertFalse(data.rejected());
        assertEquals(GroupDocumentFormat.UNSUPPORTED, data.groups().get(0).documentFormat());
        assertEquals(GroupSource.USER, data.origin("same").source());
    }

    @Test void deletingCustomFilePreservesLowerDefinitionAndPreferences() throws Exception {
        var lower = GroupResourceLoader.assemble(List.of(layer(doc(GroupSource.BUILTIN, "builtin", "same", "minecraft:stone", true, 0))));
        GroupDefinition definition = GroupConfig.fromJsonChecked(doc(GroupSource.USER, "local", "same", "minecraft:diamond", false, 0).json());
        GroupOrigin origin = GroupFileStore.create(definition, config).orElseThrow();
        Path unrelated = origin.file().resolveSibling("unrelated.json");
        Files.writeString(unrelated, "manual content");
        var data = lower.withDefinition(definition, origin);
        GroupService service = new GroupService();
        service.replaceManaged(data, Map.of("same", false), false);
        assertTrue(GroupFileStore.delete("same", origin, config));
        assertTrue(service.replaceManaged(data.withoutOwnedDefinition("same"), Map.of("same", false), true));
        assertEquals("builtin", service.resources().origin("same").sourceId());
        assertFalse(service.findById("same").orElseThrow().enabled());
        assertFalse(Files.exists(origin.file()));
        assertEquals("manual content", Files.readString(unrelated));
    }

    @Test void fileOperationsRejectUnownedPathsUnknownVersionsAndForeignIds() throws Exception {
        GroupDefinition group = GroupConfig.fromJsonChecked(doc(GroupSource.USER, "user", "same", "minecraft:stone", true, 0).json());
        Path outside = config.resolve("outside.json");
        Files.writeString(outside, GroupConfig.toJson(group));
        var escaped = new GroupOrigin(GroupSource.USER, "user", "outside", outside);
        assertFalse(GroupFileStore.save(group, escaped, config));
        assertFalse(GroupFileStore.delete(group.id(), escaped, config));
        GroupOrigin owned = GroupFileStore.create(group, config).orElseThrow();
        String unknown = "{\"id\":\"same\",\"schema_version\":2}";
        Files.writeString(owned.file(), unknown);
        assertFalse(GroupFileStore.save(group, owned, config));
        assertEquals(unknown, Files.readString(owned.file()));
        Files.writeString(owned.file(), "{\"id\":\"foreign\"}");
        assertFalse(GroupFileStore.delete(group.id(), owned, config));
        assertTrue(Files.exists(owned.file()));
        assertTrue(Files.exists(outside));
    }

    static GroupResourceLoader.Layer layer(GroupResourceLoader.Document... documents) {
        return new GroupResourceLoader.Layer(List.of(documents), false);
    }

    static GroupResourceLoader.Document doc(GroupSource source, String location, String id, String item, boolean enabled, int priority) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", location);
        json.addProperty("enabled", enabled);
        json.addProperty("priority", priority);
        JsonObject filter = new JsonObject();
        filter.addProperty("type", "item");
        filter.addProperty("id", item);
        json.add("filter", filter);
        return new GroupResourceLoader.Document(new GroupOrigin(source, location, location, null), json.toString());
    }
}
