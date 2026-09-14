package com.starskyxiii.collapsible_groups.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TomlSettingsStorageTest {
    @TempDir Path directory;

    @Test void malformedSectionsAndValuesRemainUntouched() throws Exception {
        Path path = directory.resolve("settings.toml");
        var storage = new TomlSettingsStorage(path);
        for (String text : java.util.List.of("ui = \"broken\"", "debug = [1, 2]",
            "defaultGroups = 7", "[ui]\nsearchUngroupThreshold = 1.5",
            "[ui]\nexpandedGroupBorderColor = \"nope\"")) {
            Files.writeString(path, text);
            assertThrows(IllegalArgumentException.class, storage::read, text);
            assertThrows(IllegalArgumentException.class, () -> storage.write(SettingsSnapshot.DEFAULTS), text);
            assertEquals(text, Files.readString(path));
        }
    }

    @Test void completeWritePreservesUnrelatedValuesAndComments() throws Exception {
        Path path = directory.resolve("settings.toml");
        Files.writeString(path, "# user note\n[ui]\n# keep this\ncustom = 17\nsearchUngroupThreshold = 12\n");
        var storage = new TomlSettingsStorage(path);
        var draft = new SettingsDraft(storage.read());
        draft.loadDefaultGroups = false;
        draft.groupNameColor = 0x123456;
        storage.write(draft.snapshot());
        assertEquals(draft.snapshot(), storage.read());
        String saved = Files.readString(path);
        assertTrue(saved.contains("user note"));
        assertTrue(saved.contains("keep this"));
        assertTrue(saved.contains("custom = 17"));
        assertTrue(saved.contains("[debug]"));
    }

    @Test void missingFileCreatesTheExistingConfigurationShape() throws Exception {
        Path path = directory.resolve("nested/settings.toml");
        var storage = new TomlSettingsStorage(path);
        assertEquals(SettingsSnapshot.DEFAULTS, storage.read());
        String text = Files.readString(path);
        assertTrue(text.contains("[defaultGroups]"));
        assertTrue(text.contains("showManagerButton"));
        assertFalse(text.contains("version"));
    }
}

