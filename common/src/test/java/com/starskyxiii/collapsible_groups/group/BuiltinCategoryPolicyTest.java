package com.starskyxiii.collapsible_groups.group;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.platform.TestPlatformHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BuiltinCategoryPolicyTest {
    @TempDir Path config;
    @AfterEach void reset() {
        GroupRepository.replaceForTesting(List.of());
        System.clearProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY);
    }

    @Test void policySuppressesScriptShadowsAndRejectRetainsCompleteAppliedState() {
        var service = new GroupService();
        var source = new GroupService.SourceKey(GroupSource.KUBEJS, "script");
        var builtin = group("builtin");
        var independent = group("independent");
        service.replaceSource(source, List.of(builtin, independent));
        service.markApplied(source);
        assertTrue(service.replaceManaged(data(List.of(), Set.of("test:category")), Map.of(), true));
        assertEquals(List.of(independent), service.allPriorityOrder());
        assertTrue(service.findById("builtin").isEmpty());
        assertNull(service.visibleCategory("builtin"));
        assertFalse(service.readSnapshot().winningSources().containsKey("builtin"));
        assertEquals(List.of(builtin, independent), service.sourceGroups(source));
        assertTrue(service.isApplied(source));
        var rejected = new GroupResourceData(List.of(), Set.of(), Map.of(), Map.of(), Map.of(), List.of(), true, false);
        assertFalse(service.replaceManaged(rejected, Map.of(), false));
        assertTrue(service.builtinsEnabled());
        assertEquals(Set.of("test:category"), service.resources().builtinPolicy().disabledCategories());
        assertEquals(List.of(independent), service.allPriorityOrder());
        assertTrue(service.replaceManaged(data(List.of(builtin), Set.of()), Map.of("builtin", false), true));
        assertFalse(service.findById("builtin").orElseThrow().enabled());
        assertTrue(service.resources().builtinIds().contains("builtin"));
    }

    @Test void failedFullNotificationRetriesFullWithoutReloadingAndRejectedLoadKeepsAppliedState() {
        System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, config.toString());
        GroupRepository.replaceResourcesForTesting(data(List.of(group("builtin")), Set.of()), true);
        var disabled = Set.of("test:category");
        var received = new java.util.ArrayList<GroupChangeEvent.Kind>();
        var broken = GroupChangeEvent.subscribe(GroupChangeEvent.Kind.FULL, () -> { throw new IllegalStateException("listener"); });
        try (var full = GroupChangeEvent.subscribe(GroupChangeEvent.Kind.FULL, () -> received.add(GroupChangeEvent.Kind.FULL));
             var enabled = GroupChangeEvent.subscribe(GroupChangeEvent.Kind.ENABLED, () -> received.add(GroupChangeEvent.Kind.ENABLED))) {
            assertThrows(IllegalStateException.class, () -> GroupRepository.applyBuiltinSetting(false, disabled, () -> data(List.of(), disabled)));
            assertFalse(GroupRepository.readSnapshot().builtinsEnabled());
            assertTrue(GroupRepository.getAll().isEmpty());
            broken.close();
            received.clear();
            GroupRepository.applyBuiltinSetting(false, disabled, () -> { throw new AssertionError("unexpected reread"); });
            assertEquals(List.of(GroupChangeEvent.Kind.FULL), received);
            var before = GroupRepository.resourceData();
            var rejected = new GroupResourceData(List.of(), Set.of(), Map.of(), Map.of(), Map.of(), List.of(), true, false);
            assertThrows(IllegalStateException.class, () -> GroupRepository.applyBuiltinSetting(true, Set.of(), () -> rejected));
            assertSame(before, GroupRepository.resourceData());
            assertFalse(GroupRepository.readSnapshot().builtinsEnabled());
            assertEquals(List.of(GroupChangeEvent.Kind.FULL), received);
        } finally { broken.close(); }
    }

    private static GroupDefinition group(String id) { return new GroupDefinition(id, id, true, new GroupFilter.Id("item", "test:item")); }
    private static GroupResourceData data(List<GroupDefinition> groups, Set<String> disabled) {
        var origins = new java.util.HashMap<String, List<GroupOrigin>>();
        var definitions = new java.util.HashMap<String, List<GroupDefinition>>();
        for (var group : groups) {
            origins.put(group.id(), List.of(new GroupOrigin(GroupSource.BUILTIN, "test", "assets/collapsible_groups/groups/category/group.json", null)));
            definitions.put(group.id(), List.of(group));
        }
        return new GroupResourceData(groups, Set.of("builtin"), origins, definitions, Map.of(), List.of(), false, false,
            new BuiltinCategoryPolicy(Map.of("builtin", "test:category"), disabled));
    }
}
