package com.starskyxiii.collapsible_groups.group;

import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.persistence.GroupConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ResourceFilterSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroupCategoryLoadingTest {
    @TempDir Path config;
    private static final String META = "groups/sample/metadata.json";
    private static final String GROUP = "groups/sample/group.json";
    private static final ResourceLocation CATEGORY = ResourceLocation.parse("collapsible_groups:sample");
    private final Map<String, Integer> opened = new HashMap<>();

    @AfterEach void resetRepository() { GroupRepository.replaceForTesting(List.of()); }

    @Test void unmetCategoryNeverOpensGroupAndStillReservesItsId() {
        var data = load(Map.of(META, metadata(",\"requirement\":{\"any\":[{\"mod\":\"missing\"}]}"), GROUP, "{"), List.of());
        assertTrue(data.complete(), data.problems().toString());
        assertTrue(data.groups().isEmpty());
        assertFalse(data.categories().get(CATEGORY).available());
        assertFalse(opened.containsKey(GROUP));
        assertEquals(Set.of("reserved"), data.builtinIds());
        GroupRepository.replaceResourcesForTesting(data, true);
        assertNotEquals("reserved", GroupRepository.generateUniqueId("reserved"));
        assertNotEquals("reserved", GroupRepository.generateUniqueIdIncludingScripted("reserved"));
        assertFalse(GroupRepository.saveQuietlyChecked(GroupConfig.fromJsonChecked(group("reserved"))));
    }

    @Test void higherMetadataReplacesWholeDocumentWithoutReadingShadowedInvalidMetadata() {
        var replacement = pack("high", Map.of(META, metadata("")), null);
        var data = load(Map.of(META, "{", GROUP, group("reserved")), List.of(replacement));
        assertTrue(data.complete(), data.problems().toString());
        assertTrue(data.categories().get(CATEGORY).available());
        assertEquals("reserved", data.groups().getFirst().id());
        assertEquals(1, opened.get(META));
        assertEquals(GroupSource.BUILTIN, data.origin("reserved").source());
    }

    @Test void explicitMetadataFilterLeavesGroupsUnclassifiedAndUnrestricted() {
        var filter = ResourceFilterSection.TYPE.fromJson(JsonParser.parseString(
            "{\"block\":[{\"namespace\":\"collapsible_groups\",\"path\":\"groups/sample/metadata.json\"}]}").getAsJsonObject());
        var data = load(Map.of(META, "{", GROUP, group("reserved")), List.of(pack("filter", Map.of(), filter)));
        assertTrue(data.complete(), data.problems().toString());
        assertTrue(data.categories().isEmpty());
        assertEquals(1, data.groups().size());
        assertFalse(opened.containsKey(META));
        assertEquals(Set.of("reserved"), data.builtinIds());
    }

    @Test void missingBuiltinMetadataRejectsUnlessFilteredOrReplaced() {
        var missing = load(Map.of(GROUP, group("reserved")), List.of());
        assertTrue(missing.rejected());
        var replaced = load(Map.of(GROUP, group("reserved")), List.of(pack("replacement", Map.of(META, metadata("")), null)));
        assertTrue(replaced.complete(), replaced.problems().toString());
    }

    @Test void packWithoutMetadataLoadsNestedGroupsAndIgnoresNestedMetadata() {
        var data = load(Map.of(META, metadata(""), GROUP, group("reserved")), List.of(pack("extra",
            Map.of("groups/extra/deeper/item.json", group("extra"), "groups/extra/deeper/metadata.json", "{"), null)));
        assertTrue(data.complete(), data.problems().toString());
        assertNotNull(data.origin("extra"));
        assertFalse(data.categories().containsKey(ResourceLocation.parse("collapsible_groups:extra")));
        assertFalse(opened.containsKey("groups/extra/deeper/metadata.json"));
        assertEquals(1, data.problems().size());
        assertFalse(data.problems().getFirst().error());
    }

    @Test void unavailableHigherCategoryDoesNotOverrideLowerIdOrHideLocalGroups() throws Exception {
        var high = pack("high", Map.of("groups/absent/metadata.json",
            metadata(",\"requirement\":{\"all\":[{\"mod\":\"installed\"},{\"mod\":\"missing\"}]}"),
            "groups/absent/group.json", group("reserved")), null);
        var data = load(Map.of(META, metadata(""), GROUP, group("reserved")), List.of(high));
        assertTrue(data.complete(), data.problems().toString());
        assertEquals(GroupSource.BUILTIN, data.origin("reserved").source());
        assertFalse(opened.containsKey("groups/absent/group.json"));
        Files.createDirectories(config.resolve("collapsiblegroups/groups"));
        Files.writeString(config.resolve("collapsiblegroups/groups/local.json"), group("local"));
        var local = load(Map.of(META, metadata(",\"requirement\":{\"any\":[{\"mod\":\"missing\"}]}"),
            GROUP, "{"), List.of());
        assertTrue(local.complete(), local.problems().toString());
        assertEquals(List.of("local"), local.groups().stream().map(GroupDefinition::id).toList());
    }

    @Test void invalidFinalMetadataRetainsDefinitionsAndCategoriesTogether() {
        var previous = load(Map.of(META, metadata(""), GROUP, group("reserved")), List.of());
        var service = new GroupService();
        assertTrue(service.replaceManaged(previous, Map.of(), true));
        var invalid = load(Map.of(META, metadata(""), GROUP, group("reserved")),
            List.of(pack("bad", Map.of(META, "{"), null)));
        assertFalse(service.replaceManaged(invalid, Map.of(), true));
        assertTrue(service.resources().stale());
        assertEquals(previous.groups(), service.resources().groups());
        assertEquals(previous.categories(), service.resources().categories());
    }

    @Test void requirementsValidateEveryEntryAndSupportAnyAndAll() {
        assertTrue(GroupCategory.parse(CATEGORY, metadata(",\"requirement\":{\"any\":[{\"mod\":\"missing\"},{\"mod\":\"installed\"}]}"),
            "installed"::equals).available());
        assertFalse(GroupCategory.parse(CATEGORY, metadata(",\"requirement\":{\"all\":[{\"mod\":\"missing\"},{\"mod\":\"installed\"}]}"),
            "installed"::equals).available());
        for (String requirement : List.of("{}", "{\"any\":[]}", "{\"any\":[{\"mod\":\"installed\"},{\"all\":[]}]}",
            "{\"any\":[{\"mod\":\"installed\"}],\"all\":[{\"mod\":\"installed\"}]}", "null", "{\"any\":[{\"mod\":1}]}")) {
            assertThrows(RuntimeException.class, () -> GroupCategory.parse(CATEGORY, metadata(",\"requirement\":" + requirement), mod -> true));
        }
    }

    @Test void loadersAreValidatedWithoutRestrictingRuntimeAvailability() {
        assertTrue(GroupCategory.parse(CATEGORY, metadata(""), mod -> false).available());
        for (String loaders : List.of("null", "false", "[]", "[\"unknown\"]", "[\"fabric\",\"fabric\"]")) {
            String malformed = metadata("").replace("[\"fabric\"]", loaders);
            assertThrows(RuntimeException.class, () -> GroupCategory.parse(CATEGORY, malformed, mod -> true));
        }
    }

    @Test void catalogValidationDoesNotDependOnOpeningSuppressedGroups() {
        String entry = "{\"path\":\"assets/collapsible_groups/" + GROUP + "\",\"id\":\"reserved\"}";
        var files = Map.of(META, metadata(",\"requirement\":{\"any\":[{\"mod\":\"missing\"}]}"), GROUP, "{");
        for (String entries : List.of(entry + "," + entry.replace("group.json", "other.json"),
            entry.replace("\"reserved\"", "\"\""), entry.replace("\"reserved\"", "15"),
            entry.replace("group.json", "metadata.json"))) {
            assertTrue(load(files, List.of(), "{\"version\":1,\"groups\":[" + entries + "]}").rejected());
        }
        assertFalse(opened.containsKey(GROUP));
    }

    private GroupResourceData load(Map<String, String> files, List<PackResources> packs) {
        return load(files, packs, "{\"version\":1,\"groups\":[{\"path\":\"assets/collapsible_groups/" + GROUP + "\",\"id\":\"reserved\"}]}");
    }

    private GroupResourceData load(Map<String, String> files, List<PackResources> packs, String catalog) {
        var loader = new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String path) {
                if (path.equals(GroupResourceLoader.CATALOG_RESOURCE)) return bytes(catalog);
                String relative = path.substring("assets/collapsible_groups/".length());
                opened.merge(relative, 1, Integer::sum);
                return files.containsKey(relative) ? bytes(files.get(relative)) : null;
            }
        };
        return GroupResourceLoader.load(loader, packs, config, "installed"::equals);
    }

    private PackResources pack(String id, Map<String, String> files, ResourceFilterSection filter) {
        return (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(), new Class<?>[]{PackResources.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "packId", "toString" -> id;
                case "getMetadataSection" -> args[0] == ResourceFilterSection.TYPE ? filter : null;
                case "listResources" -> {
                    files.forEach((path, text) -> {
                        IoSupplier<InputStream> supplier = () -> {
                            opened.merge(path, 1, Integer::sum);
                            return bytes(text);
                        };
                        ((PackResources.ResourceOutput) args[3]).accept(ResourceLocation.parse("collapsible_groups:" + path), supplier);
                    });
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
    }

    private static InputStream bytes(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
    private static String metadata(String requirement) {
        return "{\"loaders\":[\"fabric\"],\"category\":{\"translate\":\"category.sample\",\"fallback\":\"Sample\"}" + requirement + "}";
    }
    private static String group(String id) {
        return "{\"id\":\"" + id + "\",\"name\":\"Sample\",\"filter\":{\"type\":\"item\",\"id\":\"minecraft:stone\"}}";
    }
}
