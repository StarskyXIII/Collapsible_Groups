package com.starskyxiii.collapsible_groups.platform;

import com.starskyxiii.collapsible_groups.group.GroupOrigin;
import com.starskyxiii.collapsible_groups.group.GroupResourceData;
import com.starskyxiii.collapsible_groups.group.GroupResourceLoader;
import com.starskyxiii.collapsible_groups.group.GroupSource;
import com.starskyxiii.collapsible_groups.mixin.MixinGroupResourcePackAccessor;
import net.fabricmc.fabric.api.resource.ModResourcePack;
import net.fabricmc.fabric.impl.resource.loader.FabricModResourcePack;
import net.minecraft.SharedConstants;
import com.starskyxiii.collapsible_groups.command.TargetLocaleEntries;
import com.starskyxiii.collapsible_groups.i18n.GroupLanguageResources;
import com.starskyxiii.collapsible_groups.i18n.LanguageJson;
import java.util.LinkedHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FabricGroupResourcePacksTest {
    @TempDir Path config;
    private static final String RESOURCE = "collapsible_groups:groups/same.json";

    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void actualFabricAggregateExposesEachModInNativePriorityOrder() {
        var own = pack("collapsible_groups", Map.of(RESOURCE, json("__default_potions")));
        var other = pack("other_mod", Map.of(RESOURCE, json("external")));
        var selected = pack("file/high", Map.of(RESOURCE, json("selected")));
        var aggregate = new FabricModResourcePack(PackType.CLIENT_RESOURCES, List.of(own, other));
        assertInstanceOf(MixinGroupResourcePackAccessor.class, aggregate);
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(aggregate, selected))) {
            assertEquals(List.of(own, other, selected), new FabricPlatformHelper().groupResourcePacks(manager));
        }
    }

    @Test void ownAggregateChildKeepsBuiltinSourceAndExternalSamePathDefinitionsSurvive() {
        var own = pack("collapsible_groups", Map.of(RESOURCE, json("__default_potions")));
        var low = pack("mod_low", Map.of(RESOURCE, json("external_low")));
        var high = pack("mod_high", Map.of(RESOURCE, json("external_high")));
        var aggregate = new FabricModResourcePack(PackType.CLIENT_RESOURCES, List.of(own, low, high));
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(aggregate))) {
            var data = load(manager);
            assertTrue(data.complete(), data.problems().toString());
            assertTrue(data.builtinIds().contains("__default_potions"));
            assertEquals(GroupSource.BUILTIN, data.origin("__default_potions").source());
            assertEquals(1, data.origins().get("__default_potions").size());
            assertEquals("mod_low", data.origin("external_low").sourceId());
            assertEquals("mod_high", data.origin("external_high").sourceId());
        }
    }

    @Test void anotherModAndSelectedPackCanStillOverrideBuiltinId() {
        var own = pack("collapsible_groups", Map.of(RESOURCE, json("__default_potions")));
        var other = pack("other_mod", Map.of(RESOURCE, json("__default_potions")));
        var selected = pack("file/high", Map.of(RESOURCE, json("__default_potions")));
        var aggregate = new FabricModResourcePack(PackType.CLIENT_RESOURCES, List.of(own, other));
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(aggregate, selected))) {
            var data = load(manager);
            assertTrue(data.complete(), data.problems().toString());
            assertEquals(GroupSource.RESOURCE_PACK, data.origin("__default_potions").source());
            assertEquals("file/high", data.origin("__default_potions").sourceId());
            assertEquals(List.of("file/high", "other_mod", "collapsible_groups"),
                data.origins().get("__default_potions").stream().map(GroupOrigin::sourceId).toList());
        }
    }

    private static String json(String id) {
        return "{\"schema_version\":1,\"id\":\"" + id + "\",\"filter\":{\"type\":\"item\",\"id\":\"minecraft:stone\"}}";
    }

    private GroupResourceData load(MultiPackResourceManager manager) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(FabricPlatformHelper.class.getClassLoader());
            return GroupResourceLoader.load(manager, config);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static ModResourcePack pack(String id, Map<String, String> files) {
        Map<ResourceLocation, IoSupplier<InputStream>> resources = files.entrySet().stream().collect(Collectors.toMap(
            entry -> new ResourceLocation(entry.getKey()),
            entry -> () -> new ByteArrayInputStream(entry.getValue().getBytes(StandardCharsets.UTF_8))));
        Set<String> namespaces = resources.keySet().stream().map(ResourceLocation::getNamespace).collect(Collectors.toSet());
        return (ModResourcePack) Proxy.newProxyInstance(ModResourcePack.class.getClassLoader(), new Class<?>[]{ModResourcePack.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "packId", "toString" -> id;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "getNamespaces" -> namespaces;
                case "getMetadataSection", "getFabricModMetadata", "getRootResource", "close" -> null;
                case "getResource" -> resources.get(args[1]);
                case "isBuiltin" -> true;
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
    private static final String LANGUAGE = "collapsible_groups:lang/zh_tw.json";
    private static final String GROUP_LANGUAGE = "collapsible_groups:group_lang/zh_tw.json";

    @Test void aggregateLanguageResourcesKeepOwnGroupsAndRespectChildAndPlayerPriority() throws Exception {
        var own = pack("collapsible_groups", Map.of(GROUP_LANGUAGE, "{\"own\":\"kept\",\"player\":\"bundled\"}"));
        var low = pack("same-name", Map.of(GROUP_LANGUAGE, "{\"across\":\"low-group\"}"));
        var high = pack("same-name", Map.of(LANGUAGE, "{\"across\":\"high-ui\",\"within\":\"high-ui\"}", GROUP_LANGUAGE, "{\"within\":\"high-group\"}"));
        var player = pack("file/player", Map.of(LANGUAGE, "{\"player\":\"custom\"}"));
        var aggregate = new FabricModResourcePack(PackType.CLIENT_RESOURCES, List.of(own, low, high));
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(aggregate, player))) {
            assertLanguage(manager, Map.of("own", "kept", "player", "custom", "across", "high-ui", "within", "high-group"));
        }
    }

    private void assertLanguage(MultiPackResourceManager manager, Map<String, String> expected) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(FabricPlatformHelper.class.getClassLoader());
            assertEquals(expected, TargetLocaleEntries.read(manager, config, "zh_tw").entries());
            var runtime = new LinkedHashMap<String, String>();
            for (var resource : GroupLanguageResources.runtimeStack(manager, new ResourceLocation(LANGUAGE))) {
                try (var reader = resource.openAsReader()) { runtime.putAll(LanguageJson.parse(reader, resource.sourcePackId())); }
            }
            assertEquals(expected, runtime);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

}
