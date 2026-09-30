package com.starskyxiii.collapsible_groups.config;

import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.platform.services.IConfigProvider;

public abstract class AcceptedConfigProvider implements IConfigProvider, SettingsController.Storage {
    private final SettingsController settings = new SettingsController(this, new SettingsController.Effects() {
        public void builtins() { GroupRepository.applyBuiltinSetting(); }
        public void search() { GroupRepository.notifyStructureChanged(); }
    });

    @Override public java.util.Set<String> disabledBuiltinCategories() { return settings.snapshot().disabledBuiltinCategories(); }
    @Override public boolean showCategorySidebar() { return settings.snapshot().showCategorySidebar(); }
    @Override public SettingsController settings() { return settings; }
    @Override public boolean loadDefaultGroups() { return settings.snapshot().loadDefaultGroups(); }
    @Override public boolean showManagerButton() { return settings.snapshot().showManagerButton(); }
    @Override public boolean showGroupBackgrounds() { return settings.snapshot().showGroupBackgrounds(); }
    @Override public boolean searchUngroupSmallGroups() { return settings.snapshot().searchUngroupSmallGroups(); }
    @Override public int searchUngroupThreshold() { return settings.snapshot().searchUngroupThreshold(); }
    @Override public int collapsedGroupBackgroundColor() { return settings.snapshot().collapsedGroupBackgroundColor(); }
    @Override public int expandedGroupBackgroundColor() { return settings.snapshot().expandedGroupBackgroundColor(); }
    @Override public int groupNameColor() { return settings.snapshot().groupNameColor(); }
    @Override public int expandedGroupBorderColor() { return settings.snapshot().expandedGroupBorderColor(); }
    @Override public boolean debugTimingEnabled() { return settings.snapshot().debugTimingEnabled(); }
    @Override public boolean debugStartupIndexVerificationEnabled() { return settings.snapshot().debugStartupIndexVerificationEnabled(); }
    @Override public boolean debugEditorIndexVerificationEnabled() { return settings.snapshot().debugEditorIndexVerificationEnabled(); }
}
