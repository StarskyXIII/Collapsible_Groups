package com.starskyxiii.collapsible_groups.config;

public final class SettingsDraft {
    public boolean loadDefaultGroups;
    public boolean showManagerButton;
    public boolean showGroupBackgrounds;
    public boolean searchUngroupSmallGroups;
    public String searchUngroupThreshold;
    public int collapsedGroupBackgroundColor;
    public int expandedGroupBackgroundColor;
    public int groupNameColor;
    public int expandedGroupBorderColor;
    public boolean debugTimingEnabled;
    public boolean debugStartupIndexVerificationEnabled;
    public boolean debugEditorIndexVerificationEnabled;

    public SettingsDraft(SettingsSnapshot value) {
        loadDefaultGroups = value.loadDefaultGroups();
        showManagerButton = value.showManagerButton();
        showGroupBackgrounds = value.showGroupBackgrounds();
        searchUngroupSmallGroups = value.searchUngroupSmallGroups();
        searchUngroupThreshold = Integer.toString(value.searchUngroupThreshold());
        collapsedGroupBackgroundColor = value.collapsedGroupBackgroundColor();
        expandedGroupBackgroundColor = value.expandedGroupBackgroundColor();
        groupNameColor = value.groupNameColor();
        expandedGroupBorderColor = value.expandedGroupBorderColor();
        debugTimingEnabled = value.debugTimingEnabled();
        debugStartupIndexVerificationEnabled = value.debugStartupIndexVerificationEnabled();
        debugEditorIndexVerificationEnabled = value.debugEditorIndexVerificationEnabled();
    }

    public SettingsSnapshot snapshot() {
        return new SettingsSnapshot(loadDefaultGroups, showManagerButton, showGroupBackgrounds,
            searchUngroupSmallGroups, Integer.parseInt(searchUngroupThreshold.trim()),
            collapsedGroupBackgroundColor, expandedGroupBackgroundColor, groupNameColor, expandedGroupBorderColor,
            debugTimingEnabled, debugStartupIndexVerificationEnabled, debugEditorIndexVerificationEnabled);
    }

    public boolean valid() {
        try { snapshot(); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }
}
