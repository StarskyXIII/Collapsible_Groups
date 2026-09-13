package com.starskyxiii.collapsible_groups.group;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceFilterSection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GroupResourcePackIntegrationTest {
    @TempDir Path config;

    @Test void actualPackStackRetainsBothPathsAndSelectsHighestWholeIdBeforeLocalLayers() throws Exception {
        var low = pack("low", Map.of(path("same"), json("same", "minecraft:stone"),
            path("different"), json("from_low", "minecraft:oak_log")), null);
        var high = pack("high", Map.of(path("same"), json("same", "minecraft:dirt"),
            path("different"), json("from_high", "minecraft:spruce_log")), null);
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(low, high))) {
            assertEquals(2, manager.getResourceStack(ResourceLocation.parse(path("same"))).size());
            var data = GroupResourceLoader.load(manager, config);
            assertTrue(data.complete(), data.problems().toString());
            assertEquals("high", data.origin("same").sourceId());
            assertEquals(List.of("high", "low"), data.origins().get("same").stream().map(GroupOrigin::sourceId).toList());
            assertNotNull(data.origin("from_low"));
            assertNotNull(data.origin("from_high"));
            Path ordinary = config.resolve("collapsiblegroups/groups");
            Path overrides = config.resolve("collapsiblegroups/overrides/nested");
            Files.createDirectories(ordinary);
            Files.createDirectories(overrides);
            Files.writeString(ordinary.resolve("same.json"), json("same", "minecraft:diamond"));
            Files.writeString(overrides.resolve("same.json"), json("same", "minecraft:gold_ingot"));
            var local = GroupResourceLoader.load(manager, config);
            assertEquals(GroupSource.USER, local.origin("same").source());
            assertEquals(List.of(GroupSource.USER, GroupSource.RESOURCE_PACK, GroupSource.RESOURCE_PACK),
                local.origins().get("same").stream().map(GroupOrigin::source).toList());
        }
    }

    @Test void minecraftFilterRemovesLowerPathsButNotReplacementOrCatalogMembership() {
        var blocked = pack("blocked", Map.of(path("blocked"), json("blocked", "minecraft:stone")), null);
        var filter = ResourceFilterSection.TYPE.fromJson(JsonParser.parseString(
            "{\"block\":[{\"namespace\":\".*\",\"path\":\"collapsible_groups/groups/.*\"}]}").getAsJsonObject());
        var high = pack("filter", Map.of(path("kept"), json("kept", "minecraft:dirt")), filter);
        var original = GroupResourceLoader.load(null, config);
        assertFalse(original.builtinIds().isEmpty());
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(blocked, high))) {
            assertTrue(manager.getResourceStack(ResourceLocation.parse(path("blocked"))).isEmpty());
            var data = GroupResourceLoader.load(manager, config);
            assertTrue(data.complete(), data.problems().toString());
            assertEquals(List.of("kept"), data.groups().stream().map(GroupDefinition::id).toList());
            assertEquals(original.builtinIds(), data.builtinIds());
        }
    }

    @Test void samePackDuplicateAcrossNamespacesRejectsPublication() {
        var duplicate = pack("duplicate", Map.of(path("one"), json("same", "minecraft:stone"),
            "other:collapsible_groups/groups/two.json", json("same", "minecraft:dirt")), null);
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(duplicate))) {
            var data = GroupResourceLoader.load(manager, config);
            assertTrue(data.rejected());
            assertTrue(data.problems().stream().anyMatch(problem -> problem.reason().contains("Duplicate ID")));
            var service = new GroupService();
            assertFalse(service.replaceManaged(data, Map.of(), true));
            assertTrue(service.allPriorityOrder().isEmpty());
        }
    }

    @Test void retiredOverridePathIsIgnoredAndLeftUntouched() throws Exception {
        Files.createDirectories(config.resolve("collapsiblegroups"));
        Files.writeString(config.resolve("collapsiblegroups/overrides"), "unrelated file");
        var data = GroupResourceLoader.load(null, config);
        assertFalse(data.rejected());
        assertEquals("unrelated file", Files.readString(config.resolve("collapsiblegroups/overrides")));
    }

    private static String path(String name) { return "test:collapsible_groups/groups/" + name + ".json"; }

    private static String json(String id, String item) {
        return GroupResourceLoaderTest.doc(GroupSource.RESOURCE_PACK, id, id, item, true, 0).json();
    }

    private static PackResources pack(String id, Map<String, String> files, ResourceFilterSection filter) {
        Map<ResourceLocation, IoSupplier<InputStream>> resources = files.entrySet().stream().collect(Collectors.toMap(
            entry -> ResourceLocation.parse(entry.getKey()),
            entry -> () -> new ByteArrayInputStream(entry.getValue().getBytes(StandardCharsets.UTF_8))));
        Set<String> namespaces = resources.keySet().stream().map(ResourceLocation::getNamespace).collect(Collectors.toSet());
        return (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(), new Class<?>[]{PackResources.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "packId", "toString" -> id;
                case "getNamespaces" -> namespaces;
                case "getMetadataSection" -> args[0] == ResourceFilterSection.TYPE ? filter : null;
                case "getResource" -> resources.get(args[1]);
                case "getRootResource", "close" -> null;
                case "listResources" -> {
                    resources.forEach((location, supplier) -> {
                        if (location.getNamespace().equals(args[1]) && location.getPath().startsWith(args[2] + "/")) {
                            ((PackResources.ResourceOutput) args[3]).accept(location, supplier);
                        }
                    });
                    yield null;
                }
                default -> throw new AssertionError("Unexpected pack operation: " + method.getName());
            });
    }
}
