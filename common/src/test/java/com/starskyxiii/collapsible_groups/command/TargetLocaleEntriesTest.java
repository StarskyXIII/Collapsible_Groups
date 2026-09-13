package com.starskyxiii.collapsible_groups.command;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TargetLocaleEntriesTest {
    @TempDir Path directory;

    @Test void readsRequestedLocaleInPackOrderThenExplicitLocalOverlay() throws Exception {
        var manager = manager(Map.of("lang/zh_tw.json", List.of(resource("low", "{\"key\":\"低優先\",\"other\":\"資源翻譯\"}"),
            resource("high", "{\"key\":\"高優先\"}")), "lang/en_us.json", List.of(resource("english", "{\"missing\":\"English\"}"))));
        Files.writeString(directory.resolve("zh_tw.json"), "{\"key\":\"本機翻譯\"}");
        Files.writeString(directory.resolve("ja_jp.json"), "{\"missing\":\"別の言語\"}");
        var result = TargetLocaleEntries.read(manager, directory, "zh_tw");
        assertEquals(Map.of("key", "本機翻譯", "other", "資源翻譯"), result.entries());
        assertEquals(3, result.sources().size());
        assertTrue(result.unchanged());
        Files.writeString(directory.resolve("zh_tw.json"), "{\"key\":\"已修改\"}");
        assertFalse(result.unchanged());
    }

    @Test void missingLocaleHasNoEnglishFallbackButMalformedAndUnreadableFilesFail() throws Exception {
        var manager = manager(Map.of("lang/en_us.json", List.of(resource("english", "{\"key\":\"English\"}"))));
        var missing = TargetLocaleEntries.read(manager, directory, "zh_tw");
        assertTrue(missing.entries().isEmpty());
        assertTrue(missing.unchanged());
        Files.writeString(directory.resolve("zh_tw.json"), "{");
        assertFalse(missing.unchanged());
        assertThrows(IOException.class, () -> TargetLocaleEntries.read(manager, directory, "zh_tw"));
        Files.delete(directory.resolve("zh_tw.json"));
        Files.createDirectory(directory.resolve("zh_tw.json"));
        assertThrows(IOException.class, () -> TargetLocaleEntries.read(manager, directory, "zh_tw"));
        var badPack = manager(Map.of("lang/zh_tw.json", List.of(resource("bad", "{\"bad\":{}}"))));
        assertThrows(IOException.class, () -> TargetLocaleEntries.read(badPack, directory, "zh_tw"));
    }

    @Test void rejectsLocalePathTraversalAndPreservesLiteralStringTypes() throws Exception {
        for (String locale : List.of("../zh_tw", "zh_tw/../../manual", "C:/escape", "zh_tw.json", "", "ZH_TW")) {
            assertThrows(IllegalArgumentException.class, () -> TargetLocaleEntries.read(manager(Map.of()), directory, locale));
        }
        for (String locale : List.of("zh_tw", "en_us", "es_419", "fil_ph")) TargetLocaleEntries.validateLocale(locale);
        var manager = manager(Map.of("lang/zh_tw.json", List.of(resource("raw", "{\"a\":\"1\",\"b\":\"true\",\"c\":\"\"}"))));
        assertEquals(Map.of("a", "1", "b", "true", "c", ""), TargetLocaleEntries.read(manager, directory, "zh_tw").entries());
    }

    private static Resource resource(String id, String json) {
        PackResources pack = (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(), new Class<?>[]{PackResources.class},
            (proxy, method, args) -> method.getName().equals("packId") ? id : null);
        return new Resource(pack, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static ResourceManager manager(Map<String, List<Resource>> resources) {
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getNamespaces" -> Set.of("collapsible_groups");
                case "listPacks" -> resources.values().stream().flatMap(List::stream).map(Resource::source);
                case "getResourceStack" -> resources.getOrDefault(((ResourceLocation) args[0]).getPath(), List.of());
                default -> throw new AssertionError("Unexpected resource lookup: " + method.getName());
            });
    }
}
