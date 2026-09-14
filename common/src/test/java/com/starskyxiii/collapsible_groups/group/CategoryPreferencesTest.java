package com.starskyxiii.collapsible_groups.group;

import com.starskyxiii.collapsible_groups.persistence.GroupCategoryStore;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CategoryPreferencesTest {
    @TempDir Path directory;
    private static final String LOCAL = "local:2c636fbd-79cc-4af9-aa61-6c6df0a6a69b";
    private static final String SOURCE = "collapsible_groups:sample";

    @Test void explicitUncategorizedSurvivesRoundTripAndDiffersFromFollowingSource() {
        var preferences = CategoryPreferences.empty().assign(List.of("one"), null);
        var restored = CategoryPreferences.parse(preferences.toJson());
        assertTrue(restored.groupCategories().containsKey("one"));
        assertNull(restored.groupCategories().get("one"));
        assertFalse(restored.groupCategories().containsKey("two"));
        assertEquals(SOURCE, restored.effectiveCategory("two", origin(), resources(true)));
        assertNull(restored.effectiveCategory("one", origin(), resources(true)));
        assertEquals(SOURCE, restored.followSource(List.of("one")).effectiveCategory("one", origin(), resources(true)));
    }

    @Test void deletingLocalCategoryRestoresSourceAndDoesNotDeleteGroups() {
        var preferences = CategoryPreferences.empty().create(LOCAL, "Building")
            .assign(List.of("builtin", "user"), LOCAL);
        var deleted = preferences.delete(LOCAL);
        assertTrue(deleted.customCategories().isEmpty());
        assertTrue(deleted.groupCategories().isEmpty());
        assertEquals(SOURCE, deleted.effectiveCategory("builtin", origin(), resources(true)));
        assertNull(deleted.effectiveCategory("user", null, resources(true)));
    }

    @Test void unavailableSourceAssignmentIsRetainedAndRestoredWithoutHidingLocalGroup() {
        var preferences = CategoryPreferences.empty().assign(List.of("user"), SOURCE);
        assertNull(preferences.effectiveCategory("user", null, resources(false)));
        assertEquals(SOURCE, preferences.groupCategories().get("user"));
        assertEquals(SOURCE, preferences.effectiveCategory("user", null, resources(true)));
        assertNull(preferences.effectiveCategory("user", null, GroupResourceData.empty()));
    }

    @Test void malformedLocalReferencesAreRejectedWhileUnknownSourcesAreRetained() {
        var preferences = CategoryPreferences.empty().assign(List.of("group"), SOURCE);
        assertEquals(preferences, CategoryPreferences.parse(preferences.toJson()));
        assertThrows(IllegalArgumentException.class, () -> CategoryPreferences.empty().assign(List.of("group"), LOCAL));
        assertThrows(IllegalArgumentException.class, () -> CategoryPreferences.empty().create("local:not-uuid", "Bad"));
        assertThrows(IllegalArgumentException.class, () -> CategoryPreferences.empty().rename(SOURCE, " "));
    }

    @Test void nameResetAndNewAssignmentsReplaceOnlyTheirOwnPreference() {
        var preferences = CategoryPreferences.empty().rename(SOURCE, "Renamed")
            .create(LOCAL, "Custom").assign(List.of("group", "other"), LOCAL);
        var reset = preferences.resetName(SOURCE).assign(List.of("group"), null);
        assertTrue(reset.sourceNames().isEmpty());
        assertEquals("Custom", reset.customCategories().get(LOCAL));
        assertEquals(LOCAL, reset.groupCategories().get("other"));
        assertNull(reset.groupCategories().get("group"));
    }

    @Test void failedWriteKeepsAcceptedPreferencesAndExistingDiskData() throws Exception {
        Path file = directory.resolve("categories.json");
        var store = new GroupCategoryStore(file);
        assertTrue(store.update(current -> current.create(LOCAL, "Before")));
        String before = Files.readString(file);
        Files.createDirectory(directory.resolve("categories.json.tmp"));
        assertFalse(store.update(current -> current.rename(LOCAL, "After")));
        assertEquals("Before", store.snapshot().customCategories().get(LOCAL));
        assertEquals(before, Files.readString(file));
    }

    @Test void malformedReloadStaysReadOnlyUntilValidFileIsReloaded() throws Exception {
        Path file = directory.resolve("categories.json");
        var store = new GroupCategoryStore(file);
        assertTrue(store.update(current -> current.create(LOCAL, "Before")));
        String before = Files.readString(file);
        Files.writeString(file, "{");
        store.reload();
        assertFalse(store.writable());
        assertEquals("Before", store.snapshot().customCategories().get(LOCAL));
        assertFalse(store.update(current -> current.rename(LOCAL, "After")));
        assertEquals("{", Files.readString(file));
        Files.writeString(file, before);
        store.reload();
        assertTrue(store.writable());
        assertTrue(store.update(current -> current.rename(LOCAL, "After")));
    }

    @Test void localChangesPublishNoGroupChangeEventsAndNoOpDoesNotCreateFile() {
        Path file = directory.resolve("nested/categories.json");
        var store = new GroupCategoryStore(file);
        var events = new AtomicInteger();
        var subscriptions = java.util.Arrays.stream(GroupChangeEvent.Kind.values())
            .map(kind -> GroupChangeEvent.subscribe(kind, events::incrementAndGet)).toList();
        try {
            assertTrue(store.update(current -> current));
            assertFalse(Files.exists(file));
            assertTrue(store.update(current -> current.create(LOCAL, "Custom").assign(List.of("group"), LOCAL)));
            assertEquals(0, events.get());
        } finally { subscriptions.forEach(GroupChangeEvent.Subscription::close); }
    }

    @Test void externalEditsLockOlderDraftsUntilExplicitReload() throws Exception {
        Path file = directory.resolve("categories.json");
        var store = new GroupCategoryStore(file);
        assertTrue(store.update(current -> current.create(LOCAL, "Before")));
        String external = store.snapshot().rename(LOCAL, "External").toJson();
        Files.writeString(file, external);
        assertFalse(store.update(current -> current.rename(LOCAL, "Old draft")));
        assertEquals(external, Files.readString(file));
        assertEquals("Before", store.snapshot().customCategories().get(LOCAL));
        assertFalse(store.writable());
        store.reload();
        assertEquals("External", store.snapshot().customCategories().get(LOCAL));
        Files.writeString(file, "{");
        assertFalse(store.update(current -> current.rename(LOCAL, "Old draft")));
        assertFalse(store.writable());
        assertEquals("{", Files.readString(file));
    }

    private static GroupOrigin origin() {
        return new GroupOrigin(GroupSource.BUILTIN, "collapsible_groups", "assets/collapsible_groups/groups/sample/nested/item.json", null);
    }

    private static GroupResourceData resources(boolean available) {
        var id = ResourceLocation.parse(SOURCE);
        return new GroupResourceData(List.of(), Set.of(), Map.of(), Map.of(),
            Map.of(id, new GroupCategory(id, new GroupDisplayName.Localized("category.sample", "Sample"), available)),
            List.of(), false, false);
    }
}
