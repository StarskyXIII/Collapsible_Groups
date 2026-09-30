package com.starskyxiii.collapsible_groups.config;

import com.starskyxiii.collapsible_groups.persistence.AtomicFileWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SettingsControllerTest {
    @TempDir Path directory;

    @Test void initializationAcceptsCompleteExistingSettingsWithoutDisplayEffects() {
        var fixture = new Fixture();
        var draft = new SettingsDraft(SettingsSnapshot.DEFAULTS);
        draft.loadDefaultGroups = false;
        draft.searchUngroupThreshold = "12";
        fixture.disk = draft.snapshot();
        fixture.controller.initialize();
        assertEquals(fixture.disk, fixture.controller.snapshot());
        assertTrue(fixture.controller.writable());
        assertTrue(fixture.events.isEmpty());
    }

    @Test void failedWriteDoesNotPublishOrLoseTheDraft() {
        var fixture = new Fixture();
        fixture.controller.initialize();
        var draft = new SettingsDraft(fixture.disk);
        draft.loadDefaultGroups = false;
        fixture.failWrite = true;
        assertEquals(SettingsController.Result.SAVE_FAILED, fixture.controller.save(draft.snapshot()));
        assertEquals(SettingsSnapshot.DEFAULTS, fixture.disk);
        assertEquals(SettingsSnapshot.DEFAULTS, fixture.controller.snapshot());
        assertFalse(draft.loadDefaultGroups);
        assertTrue(fixture.events.isEmpty());
        fixture.failWrite = false;
        assertEquals(SettingsController.Result.SUCCESS, fixture.controller.save(draft.snapshot()));
        assertFalse(fixture.controller.snapshot().loadDefaultGroups());
    }

    @Test void savedSettingsStayAcceptedWhenNativeSyncFailsAndNoopSaveRetriesWithoutWriting() {
        var fixture = new Fixture();
        fixture.controller.initialize();
        var draft = new SettingsDraft(fixture.disk);
        draft.groupNameColor = 0x123456;
        fixture.failSync = true;
        assertEquals(SettingsController.Result.APPLY_PENDING, fixture.controller.save(draft.snapshot()));
        assertEquals(draft.snapshot(), fixture.disk);
        assertEquals(draft.snapshot(), fixture.controller.snapshot());
        assertTrue(fixture.events.isEmpty());
        fixture.failSync = false;
        assertEquals(SettingsController.Result.SUCCESS, fixture.controller.save(draft.snapshot()));
        assertEquals(1, fixture.writes);
        assertEquals(2, fixture.syncs);
    }

    @Test void failedBuiltinNotificationRetriesEvenAfterStateChangedAndSupersedesSearchRefresh() {
        var fixture = new Fixture();
        fixture.controller.initialize();
        var draft = new SettingsDraft(fixture.disk);
        draft.loadDefaultGroups = false;
        draft.searchUngroupThreshold = "9";
        fixture.failEffect = true;
        assertEquals(SettingsController.Result.APPLY_PENDING, fixture.controller.save(draft.snapshot()));
        assertFalse(fixture.controller.snapshot().loadDefaultGroups());
        assertEquals(List.of("builtins"), fixture.events);
        fixture.failEffect = false;
        assertEquals(SettingsController.Result.SUCCESS, fixture.controller.save(draft.snapshot()));
        assertEquals(List.of("builtins", "builtins"), fixture.events);
        assertEquals(1, fixture.writes);
    }

    @Test void newerSnapshotDoesNotEraseAnEarlierFailedDisplayEffect() {
        var fixture = new Fixture();
        fixture.controller.initialize();
        var draft = new SettingsDraft(fixture.disk);
        draft.searchUngroupThreshold = "12";
        fixture.failEffect = true;
        fixture.controller.save(draft.snapshot());
        draft.groupNameColor = 0xAABBCC;
        fixture.disk = draft.snapshot();
        fixture.failEffect = false;
        assertEquals(SettingsController.Result.SUCCESS, fixture.controller.reload());
        assertEquals(List.of("search", "search"), fixture.events);
        assertEquals(draft.snapshot(), fixture.controller.snapshot());
        assertEquals(1, fixture.writes);
    }

    @Test void repeatedReloadReadsTheCurrentFileAndDoesNotRepeatSuccessfulEffects() {
        var fixture = new Fixture();
        fixture.controller.initialize();
        var draft = new SettingsDraft(fixture.disk);
        draft.searchUngroupSmallGroups = false;
        fixture.disk = draft.snapshot();
        fixture.controller.reload();
        fixture.controller.reload();
        assertEquals(List.of("search"), fixture.events);
        assertEquals(draft.snapshot(), fixture.controller.snapshot());
        assertEquals(0, fixture.writes);
    }

    @Test void malformedReloadPreservesAcceptedSettingsAndRequiresRepairBeforeSaving() throws Exception {
        Path path = directory.resolve("settings.json");
        var storage = new JsonSettingsStorage(path);
        var controller = new SettingsController(storage, noEffects());
        controller.initialize();
        SettingsSnapshot before = controller.snapshot();
        String broken = "{\"ui\":\"broken\"}";
        Files.writeString(path, broken);
        assertEquals(SettingsController.Result.READ_FAILED, controller.reload());
        assertEquals(before, controller.snapshot());
        assertEquals(SettingsController.Result.READ_FAILED, controller.save(before));
        assertEquals(broken, Files.readString(path));
        Files.writeString(path, "{\"ui\":{\"searchUngroupThreshold\":23}}");
        assertEquals(SettingsController.Result.SUCCESS, controller.reload());
        assertTrue(controller.writable());
        assertEquals(23, controller.snapshot().searchUngroupThreshold());
    }

    @Test void jsonSaveRetainsUnrelatedFieldsAndAtomicFailureLeavesTheDestinationUntouched() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "{\"unrelated\":{\"text\":\"keep\"},\"ui\":{\"custom\":7}}");
        var storage = new JsonSettingsStorage(path);
        storage.write(SettingsSnapshot.DEFAULTS);
        var document = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals("keep", document.getAsJsonObject("unrelated").get("text").getAsString());
        assertEquals(7, document.getAsJsonObject("ui").get("custom").getAsInt());
        Path targetDirectory = directory.resolve("occupied");
        Files.createDirectories(targetDirectory);
        Files.writeString(targetDirectory.resolve("keep.txt"), "keep");
        assertThrows(IOException.class, () -> AtomicFileWriter.write(targetDirectory, "replace"));
        assertEquals("keep", Files.readString(targetDirectory.resolve("keep.txt")));
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }

    @Test void invalidDraftSurvivesOtherEditsAndDoesNotMutateAcceptedSettings() {
        var draft = new SettingsDraft(SettingsSnapshot.DEFAULTS);
        draft.searchUngroupThreshold = "2147483648";
        draft.showManagerButton = false;
        draft.groupNameColor = 0xB0C0D0;
        assertFalse(draft.valid());
        assertEquals("2147483648", draft.searchUngroupThreshold);
        assertTrue(SettingsSnapshot.DEFAULTS.showManagerButton());
        draft.searchUngroupThreshold = "0";
        assertTrue(draft.valid());
        assertEquals(0, draft.snapshot().searchUngroupThreshold());
        assertEquals(0xB0C0D0, draft.snapshot().groupNameColor());
        assertThrows(IllegalArgumentException.class, () -> SettingsSnapshot.read(Map.of("ui.searchUngroupThreshold", 1.5)::get));
        assertThrows(IllegalArgumentException.class, () -> SettingsSnapshot.read(Map.of("ui.groupNameColor", "invalid")::get));
    }

    @Test void unchangedSaveDoesNotWriteOrNotify() {
        var fixture = new Fixture();
        fixture.controller.initialize();
        assertEquals(SettingsController.Result.SUCCESS, fixture.controller.save(fixture.disk));
        assertEquals(0, fixture.writes);
        assertEquals(0, fixture.syncs);
        assertTrue(fixture.events.isEmpty());
    }

    @Test void categoryDraftAndSnapshotDoNotShareMutableState() {
        var first = new SettingsDraft(SettingsSnapshot.DEFAULTS);
        first.disabledBuiltinCategories.add("collapsible_groups:macaw");
        var snapshot = first.snapshot();
        first.disabledBuiltinCategories.clear();
        var second = new SettingsDraft(snapshot);
        second.disabledBuiltinCategories.add("absent:future");
        assertEquals(java.util.Set.of("collapsible_groups:macaw"), snapshot.disabledBuiltinCategories());
        assertTrue(SettingsSnapshot.DEFAULTS.disabledBuiltinCategories().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.disabledBuiltinCategories().clear());
    }

    @Test void categoryJsonRoundTripKeepsUnknownIdsAndCanonicalOrdering() throws Exception {
        Path path = directory.resolve("categories-settings.json");
        Files.writeString(path, "{\"defaultGroups\":{\"disabledCategories\":[\"z:last\",\"a:first\",\"z:last\"]},\"ui\":{\"showCategorySidebar\":false}}");
        var storage = new JsonSettingsStorage(path);
        var snapshot = storage.read();
        assertEquals(java.util.Set.of("z:last", "a:first"), snapshot.disabledBuiltinCategories());
        assertFalse(snapshot.showCategorySidebar());
        storage.write(snapshot);
        assertEquals(snapshot, storage.read());
        var array = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject()
            .getAsJsonObject("defaultGroups").getAsJsonArray("disabledCategories");
        assertEquals("a:first", array.get(0).getAsString());
        assertEquals(2, array.size());
        for (String invalid : List.of("7", "true", "null", "\"a:first\"", "[7]", "[\"bad:UPPER\"]")) {
            String malformed = "{\"defaultGroups\":{\"disabledCategories\":" + invalid + "}}";
            Files.writeString(path, malformed);
            assertThrows(RuntimeException.class, storage::read);
            assertThrows(RuntimeException.class, () -> storage.write(snapshot));
            assertEquals(malformed, Files.readString(path));
        }
    }

    @Test void categoryAndMasterChangesCoalesceWhileSidebarChangesDoNotRebuild() {
        var fixture = new Fixture();
        fixture.controller.initialize();
        var draft = new SettingsDraft(fixture.disk);
        draft.showCategorySidebar = false;
        fixture.controller.save(draft.snapshot());
        assertTrue(fixture.events.isEmpty());
        draft.disabledBuiltinCategories.add("future:category");
        draft.loadDefaultGroups = false;
        draft.searchUngroupThreshold = "18";
        fixture.failWrite = true;
        assertEquals(SettingsController.Result.SAVE_FAILED, fixture.controller.save(draft.snapshot()));
        assertTrue(fixture.controller.snapshot().disabledBuiltinCategories().isEmpty());
        fixture.failWrite = false;
        fixture.failEffect = true;
        assertEquals(SettingsController.Result.APPLY_PENDING, fixture.controller.save(draft.snapshot()));
        assertEquals(draft.snapshot(), fixture.controller.snapshot());
        assertEquals(List.of("builtins"), fixture.events);
        fixture.failEffect = false;
        assertEquals(SettingsController.Result.SUCCESS, fixture.controller.save(draft.snapshot()));
        assertEquals(List.of("builtins", "builtins"), fixture.events);
    }

    private static SettingsController.Effects noEffects() {
        return new SettingsController.Effects() {
            public void builtins() {}
            public void search() {}
        };
    }

    private static final class Fixture implements SettingsController.Storage, SettingsController.Effects {
        SettingsSnapshot disk = SettingsSnapshot.DEFAULTS;
        final SettingsController controller = new SettingsController(this, this);
        final List<String> events = new ArrayList<>();
        int writes, syncs;
        boolean failWrite, failSync, failEffect;
        public SettingsSnapshot read() { return disk; }
        public void write(SettingsSnapshot settings) throws IOException {
            if (failWrite) throw new IOException("disk unavailable");
            writes++;
            disk = settings;
        }
        public void synchronize() throws IOException {
            syncs++;
            if (failSync) throw new IOException("native sync unavailable");
        }
        public void builtins() { events.add("builtins"); effect(); }
        public void search() { events.add("search"); effect(); }
        private void effect() { if (failEffect) throw new IllegalStateException("subscriber failed"); }
    }
}
