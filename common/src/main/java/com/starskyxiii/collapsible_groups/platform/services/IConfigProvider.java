package com.starskyxiii.collapsible_groups.platform.services;

/**
 * Platform-agnostic access to mod configuration values used by common code.
 * Each loader provides its own implementation (NeoForge via ModConfigSpec,
 * Fabric/Forge via their respective config systems).
 */
public interface IConfigProvider {
    default com.starskyxiii.collapsible_groups.config.SettingsController settings() {
        throw new UnsupportedOperationException("Settings are not editable");
    }
	/** Master switch: false means no built-in default groups are loaded. */
	boolean loadDefaultGroups();



	/** Whether to show the group manager button in the JEI ingredient list overlay. */
	boolean showManagerButton();

	/** Whether group slots should draw their semi-transparent background tint. */
	boolean showGroupBackgrounds();

	/** Whether small matching groups should be shown as regular entries while filtering JEI search results. */
	boolean searchUngroupSmallGroups();

	/** Filtered child count must be lower than this value before search leaves a group ungrouped. */
	int searchUngroupThreshold();

	/** ARGB background color for collapsed group headers. */
	int collapsedGroupBackgroundColor();

	/** ARGB background color for expanded group headers and children. */
	int expandedGroupBackgroundColor();

	/** RGB color for group display names. Alpha is ignored. */
	int groupNameColor();

	/** ARGB color for the connected border around expanded groups. */
	int expandedGroupBorderColor();

	/** Whether debug timing/performance logs should be emitted. */
	boolean debugTimingEnabled();

	/** Whether startup index verification should compare the optimized builder against a reference implementation. */
	boolean debugStartupIndexVerificationEnabled();

	/** Whether the editor item index should run its correctness verification mode. */
	boolean debugEditorIndexVerificationEnabled();
}
