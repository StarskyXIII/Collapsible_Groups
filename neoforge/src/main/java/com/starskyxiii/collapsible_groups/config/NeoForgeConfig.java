package com.starskyxiii.collapsible_groups.config;

import com.starskyxiii.collapsible_groups.platform.services.IConfigProvider;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class NeoForgeConfig implements IConfigProvider {
	private static final int COLLAPSED_GROUP_BACKGROUND_COLOR_DEFAULT = 0x24FFFFFF;
	private static final int EXPANDED_GROUP_BACKGROUND_COLOR_DEFAULT  = 0x24FFFFFF;
	private static final int GROUP_NAME_COLOR_DEFAULT                 = 0x00FFAA00;
	private static final int EXPANDED_GROUP_BORDER_COLOR_DEFAULT      = 0x66FFFFFF;


	// IConfigProvider

	@Override public boolean loadDefaultGroups() { return LOAD_DEFAULT_GROUPS.get(); }
	@Override public boolean showManagerButton()       { return SHOW_MANAGER_BUTTON.get(); }
	@Override public boolean showGroupBackgrounds()    { return SHOW_GROUP_BACKGROUNDS.get(); }
	@Override public boolean searchUngroupSmallGroups() { return SEARCH_UNGROUP_SMALL_GROUPS.get(); }
	@Override public int searchUngroupThreshold()       { return SEARCH_UNGROUP_THRESHOLD.get(); }
	@Override public int collapsedGroupBackgroundColor() {
		return ColorConfigParser.parseArgb(COLLAPSED_GROUP_BACKGROUND_COLOR.get(), COLLAPSED_GROUP_BACKGROUND_COLOR_DEFAULT);
	}
	@Override public int expandedGroupBackgroundColor() {
		return ColorConfigParser.parseArgb(EXPANDED_GROUP_BACKGROUND_COLOR.get(), EXPANDED_GROUP_BACKGROUND_COLOR_DEFAULT);
	}
	@Override public int groupNameColor() {
		return ColorConfigParser.parseRgb(GROUP_NAME_COLOR.get(), GROUP_NAME_COLOR_DEFAULT);
	}
	@Override public int expandedGroupBorderColor() {
		return ColorConfigParser.parseArgb(EXPANDED_GROUP_BORDER_COLOR.get(), EXPANDED_GROUP_BORDER_COLOR_DEFAULT);
	}
	@Override public boolean debugTimingEnabled()      { return DEBUG_TIMING_LOGS.get(); }
	@Override public boolean debugStartupIndexVerificationEnabled() { return DEBUG_STARTUP_INDEX_VERIFY.get(); }
	@Override public boolean debugEditorIndexVerificationEnabled() { return DEBUG_EDITOR_INDEX_VERIFY.get(); }


	/** auto, jei, or emi. Sampled at startup; changes require a restart. */

	// defaultGroups

	/** Master switch: set to false for a completely clean slate with no built-in groups. */
	public static final ModConfigSpec.BooleanValue LOAD_DEFAULT_GROUPS;

	// ui

	/** Whether to show the group manager button in the JEI overlay. */
	public static final ModConfigSpec.BooleanValue SHOW_MANAGER_BUTTON;

	/** Whether grouped slots draw a semi-transparent background tint. */
	public static final ModConfigSpec.BooleanValue SHOW_GROUP_BACKGROUNDS;

	/** Whether small matching groups are shown as regular entries while filtering JEI search results. */
	public static final ModConfigSpec.BooleanValue SEARCH_UNGROUP_SMALL_GROUPS;

	/** Filtered group child count must be lower than this value before search leaves it ungrouped. */
	public static final ModConfigSpec.IntValue SEARCH_UNGROUP_THRESHOLD;

	/** ARGB background color for collapsed group headers. */
	public static final ModConfigSpec.ConfigValue<String> COLLAPSED_GROUP_BACKGROUND_COLOR;

	/** ARGB background color for expanded group headers and children. */
	public static final ModConfigSpec.ConfigValue<String> EXPANDED_GROUP_BACKGROUND_COLOR;

	/** RGB color for group display names. */
	public static final ModConfigSpec.ConfigValue<String> GROUP_NAME_COLOR;

	/** ARGB color for the connected border around expanded groups. */
	public static final ModConfigSpec.ConfigValue<String> EXPANDED_GROUP_BORDER_COLOR;

	/** Whether to emit debug timing/performance logs. */
	public static final ModConfigSpec.BooleanValue DEBUG_TIMING_LOGS;

	/** Whether to verify the startup index against a reference implementation. */
	public static final ModConfigSpec.BooleanValue DEBUG_STARTUP_INDEX_VERIFY;

	/** Whether to enable editor preview index verification mode. */
	public static final ModConfigSpec.BooleanValue DEBUG_EDITOR_INDEX_VERIFY;

	public static final ModConfigSpec SPEC;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

		// [defaultGroups]
		builder.translation("collapsible_groups.configuration.defaultGroups").push("defaultGroups");
		LOAD_DEFAULT_GROUPS = builder
			.comment(
				"Master switch for all built-in default groups.",
				"Set to false to start with a completely clean slate (no default groups)."
			)
			.translation("collapsible_groups.configuration.defaultGroups.enabled")
			.define("enabled", true);
		builder.pop();

		// [ui]
		builder.translation("collapsible_groups.configuration.ui").push("ui");
		SHOW_MANAGER_BUTTON = builder
			.comment("Whether to show the group manager button in the JEI ingredient list overlay.")
			.translation("collapsible_groups.configuration.ui.showManagerButton")
			.define("showManagerButton", true);
		SHOW_GROUP_BACKGROUNDS = builder
			.comment(
				"Whether grouped JEI slots draw a semi-transparent background tint.",
				"Set to false to keep group backgrounds fully transparent while preserving the +/- indicator and borders."
			)
			.translation("collapsible_groups.configuration.ui.showGroupBackgrounds")
			.define("showGroupBackgrounds", true);
		SEARCH_UNGROUP_SMALL_GROUPS = builder
			.comment(
				"Whether small matching groups should be shown as regular entries while filtering JEI search results.",
				"This avoids showing a group header when only a few children match the search."
			)
			.translation("collapsible_groups.configuration.ui.searchUngroupSmallGroups")
			.define("searchUngroupSmallGroups", true);
		SEARCH_UNGROUP_THRESHOLD = builder
			.comment(
				"Filtered group child count must be lower than this value before search leaves it ungrouped.",
				"For example, 5 leaves groups with 2-4 matching entries ungrouped; 0 disables this behavior."
			)
			.translation("collapsible_groups.configuration.ui.searchUngroupThreshold")
			.defineInRange("searchUngroupThreshold", 5, 0, Integer.MAX_VALUE);
		COLLAPSED_GROUP_BACKGROUND_COLOR = builder
			.comment(
				"ARGB background color for collapsed group headers.",
				"Accepted formats: #AARRGGBB, 0xAARRGGBB, AARRGGBB, or RGB variants that keep the default alpha."
			)
			.translation("collapsible_groups.configuration.ui.collapsedGroupBackgroundColor")
			.define("collapsedGroupBackgroundColor", "#24FFFFFF");
		EXPANDED_GROUP_BACKGROUND_COLOR = builder
			.comment(
				"ARGB background color for expanded group headers and children.",
				"Accepted formats: #AARRGGBB, 0xAARRGGBB, AARRGGBB, or RGB variants that keep the default alpha."
			)
			.translation("collapsible_groups.configuration.ui.expandedGroupBackgroundColor")
			.define("expandedGroupBackgroundColor", "#24FFFFFF");
		GROUP_NAME_COLOR = builder
			.comment(
				"RGB color for group display names. Alpha is ignored if an ARGB value is provided.",
				"Accepted formats: #RRGGBB, 0xRRGGBB, RRGGBB, or ARGB variants with ignored alpha."
			)
			.translation("collapsible_groups.configuration.ui.groupNameColor")
			.define("groupNameColor", "#FFAA00");
		EXPANDED_GROUP_BORDER_COLOR = builder
			.comment(
				"ARGB color for the connected border around expanded groups.",
				"Accepted formats: #AARRGGBB, 0xAARRGGBB, AARRGGBB, or RGB variants that keep the default alpha."
			)
			.translation("collapsible_groups.configuration.ui.expandedGroupBorderColor")
			.define("expandedGroupBorderColor", "#66FFFFFF");
		builder.pop(); // ui

		// [debug]
		builder.translation("collapsible_groups.configuration.debug").push("debug");
		DEBUG_TIMING_LOGS = builder
			.comment(
				"Show Collapsible Groups timing logs in the game log.",
				"Useful when diagnosing slow JEI startup, group rebuilds, or editor/manager refreshes."
			)
			.translation("collapsible_groups.configuration.debug.enableTimingLogs")
			.define("enableTimingLogs", false);
		DEBUG_STARTUP_INDEX_VERIFY = builder
			.comment(
				"Verify the startup item-group index against a reference implementation.",
				"Builds the reference result and compares it with the optimized startup index. This is slower, but useful for correctness testing."
			)
			.translation("collapsible_groups.configuration.debug.verifyStartupIndex")
			.define("verifyStartupIndex", false);
		DEBUG_EDITOR_INDEX_VERIFY = builder
			.comment(
				"Verify the editor preview index against a reference implementation.",
				"Useful when testing editor-side preview correctness."
			)
			.translation("collapsible_groups.configuration.debug.verifyEditorPreviewIndex")
			.define("verifyEditorPreviewIndex", false);
		builder.pop(); // debug

		SPEC = builder.build();
	}

	public NeoForgeConfig() {}
}
