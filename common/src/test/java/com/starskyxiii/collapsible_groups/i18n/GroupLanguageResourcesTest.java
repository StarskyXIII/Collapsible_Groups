package com.starskyxiii.collapsible_groups.i18n;

import com.starskyxiii.collapsible_groups.command.TargetLocaleEntries;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class GroupLanguageResourcesTest {
    @TempDir Path directory;

    @Test void packIdentityOrdersSeparateFilesAndRequestedLocaleExcludesEnglish() throws Exception {
        var low = pack("same-name", Map.of(
            "collapsible_groups:lang/zh_tw.json", "{\"within\":\"low-ui\"}",
            "collapsible_groups:group_lang/zh_tw.json", "{\"across\":\"low-group\",\"within\":\"low-group\"}",
            "collapsible_groups:group_lang/en_us.json", "{\"english-only\":\"English\"}"), null);
        var high = pack("same-name", Map.of(
            "collapsible_groups:lang/zh_tw.json", "{\"across\":\"high-ui\",\"within\":\"high-ui\"}",
            "collapsible_groups:group_lang/zh_tw.json", "{\"within\":\"high-group\"}"), null);
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(low, high))) {
            var actual = TargetLocaleEntries.read(manager, directory, "zh_tw").entries();
            assertEquals(Map.of("across", "high-ui", "within", "high-group"), actual);
            var runtime = new LinkedHashMap<String, String>();
            for (String locale : List.of("en_us", "zh_tw")) {
                for (var resource : GroupLanguageResources.runtimeStack(manager,
                    new ResourceLocation("collapsible_groups:lang/" + locale + ".json"))) {
                    try (var reader = resource.openAsReader()) { runtime.putAll(LanguageJson.parse(reader, locale)); }
                }
            }
            assertEquals("English", runtime.remove("english-only"));
            assertEquals(actual, runtime);
        }
    }

    @Test void invalidGroupFileIsIsolatedBeforePartialPublicationAndWorklistFails() throws Exception {
        var bad = pack("bad", Map.of("collapsible_groups:group_lang/zh_tw.json", "{\"partial\":\"bad\",\"invalid\":3}"), null);
        var high = pack("high", Map.of("collapsible_groups:lang/zh_tw.json", "{\"valid\":\"survives\"}"), null);
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(bad, high))) {
            var stack = GroupLanguageResources.runtimeStack(manager, new ResourceLocation("collapsible_groups:lang/zh_tw.json"));
            assertThrows(IOException.class, () -> { try (var input = stack.get(0).open()) { input.readAllBytes(); } });
            try (var reader = stack.get(1).openAsReader()) {
                assertEquals(Map.of("valid", "survives"), LanguageJson.parse(reader, "high"));
            }
            assertThrows(IOException.class, () -> TargetLocaleEntries.read(manager, directory, "zh_tw"));
        }
    }

    @Test void resourceFilterAppliesToGroupLanguageStack() throws Exception {
        var low = pack("low", Map.of("collapsible_groups:group_lang/zh_tw.json", "{\"filtered\":\"gone\"}"), null);
        var filter = ResourceFilterSection.TYPE.fromJson(JsonParser.parseString(
            "{\"block\":[{\"namespace\":\"collapsible_groups\",\"path\":\"group_lang/.*\"}]}").getAsJsonObject());
        var high = pack("filter", Map.of("collapsible_groups:lang/zh_tw.json", "{\"kept\":\"kept\"}"), filter);
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(low, high))) {
            assertEquals(Map.of("kept", "kept"), TargetLocaleEntries.read(manager, directory, "zh_tw").entries());
        }
    }

    private static PackResources pack(String id, Map<String, String> files, ResourceFilterSection filter) {
        Map<ResourceLocation, IoSupplier<InputStream>> resources = files.entrySet().stream().collect(Collectors.toMap(
            entry -> new ResourceLocation(entry.getKey()),
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
