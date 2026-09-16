package com.starskyxiii.collapsible_groups.persistence;

import com.starskyxiii.collapsible_groups.platform.TestPlatformHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GroupConfigUiStateTest {
    @TempDir Path directory;
    private String previous;

    @BeforeEach void setup() {
        previous = System.getProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY);
        System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, directory.toString());
    }

    @AfterEach void restore() {
        if (previous == null) System.clearProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY);
        else System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, previous);
    }

    @Test void olderPreferencesOpenTheSidebarWithoutLosingTheirFilters() throws Exception {
        Path file = directory.resolve("collapsiblegroups/ui_state.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"manager_category_filter\":\"example:test\",\"manager_show_empty\":true}");
        var state = GroupConfig.loadUiState();
        assertTrue(state.managerSidebarOpen());
        assertTrue(state.managerShowEmpty());
        assertEquals("example:test", state.managerCategoryFilter());
    }

    @Test void collapsedPreferenceSurvivesSavingAndLoading() {
        GroupConfig.saveUiState(true, false, true, "user", "name_asc", true, "custom:test", false);
        var state = GroupConfig.loadUiState();
        assertFalse(state.managerSidebarOpen());
        assertEquals("custom:test", state.managerCategoryFilter());
        assertEquals("user", state.managerSourceFilter());
        assertTrue(state.managerShowEmpty());
    }
}
