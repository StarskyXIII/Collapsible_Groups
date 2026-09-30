package com.starskyxiii.collapsible_groups.config;

import com.starskyxiii.collapsible_groups.platform.Services;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Forge config provider that reads and writes
 * {@code config/collapsiblegroups/collapsiblegroups.toml} via {@link ForgeConfigSpec}.
 */
public final class ForgeConfig extends AcceptedConfigProvider {
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> DISABLED_BUILTIN_CATEGORIES;
    public static final ForgeConfigSpec.BooleanValue SHOW_CATEGORY_SIDEBAR;
	public static final ForgeConfigSpec.BooleanValue LOAD_DEFAULT_GROUPS;

	// ui

	/** Whether to show the group manager button in the JEI overlay. */
	public static final ForgeConfigSpec.BooleanValue SHOW_MANAGER_BUTTON;

	/** Whether grouped slots draw a semi-transparent background tint. */
	public static final ForgeConfigSpec.BooleanValue SHOW_GROUP_BACKGROUNDS;

	/** Whether small matching groups are shown as regular entries while filtering JEI search results. */
	public static final ForgeConfigSpec.BooleanValue SEARCH_UNGROUP_SMALL_GROUPS;

	/** Filtered group child count must be lower than this value before search leaves it ungrouped. */
	public static final ForgeConfigSpec.IntValue SEARCH_UNGROUP_THRESHOLD;

	/** ARGB background color for collapsed group headers. */
	public static final ForgeConfigSpec.ConfigValue<String> COLLAPSED_GROUP_BACKGROUND_COLOR;

	/** ARGB background color for expanded group headers and children. */
	public static final ForgeConfigSpec.ConfigValue<String> EXPANDED_GROUP_BACKGROUND_COLOR;

	/** RGB color for group display names. */
	public static final ForgeConfigSpec.ConfigValue<String> GROUP_NAME_COLOR;

	/** ARGB color for the connected border around expanded groups. */
	public static final ForgeConfigSpec.ConfigValue<String> EXPANDED_GROUP_BORDER_COLOR;

	// debug

	/** Whether to emit debug timing/performance logs. */
	public static final ForgeConfigSpec.BooleanValue DEBUG_TIMING_LOGS;

	/** Whether to verify the startup index against a reference implementation. */
	public static final ForgeConfigSpec.BooleanValue DEBUG_STARTUP_INDEX_VERIFY;

	/** Whether to enable editor preview index verification mode. */
	public static final ForgeConfigSpec.BooleanValue DEBUG_EDITOR_INDEX_VERIFY;

	public static final ForgeConfigSpec SPEC;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

		// [defaultGroups]
		builder.push("defaultGroups");
		LOAD_DEFAULT_GROUPS = builder
			.comment(
				"Master switch for all built-in default groups.",
				"Set to false to start with a completely clean slate (no default groups)."
			)
			.define("enabled", true);
        DISABLED_BUILTIN_CATEGORIES = builder.defineListAllowEmpty(java.util.List.of("disabledCategories"),
            java.util.List.of(), SettingsSnapshot::validCategoryId);
		builder.pop();

		// [ui]
		builder.push("ui");
        SHOW_CATEGORY_SIDEBAR = builder.define("showCategorySidebar", true);
		SHOW_MANAGER_BUTTON = builder
			.comment("Whether to show the group manager button in the JEI ingredient list overlay.")
			.define("showManagerButton", true);
		SHOW_GROUP_BACKGROUNDS = builder
			.comment(
				"Whether grouped JEI slots draw a semi-transparent background tint.",
				"Set to false to keep group backgrounds fully transparent while preserving the +/- indicator and borders."
			)
			.define("showGroupBackgrounds", true);
		SEARCH_UNGROUP_SMALL_GROUPS = builder
			.comment(
				"Whether small matching groups should be shown as regular entries while filtering JEI search results.",
				"This avoids showing a group header when only a few children match the search."
			)
			.define("searchUngroupSmallGroups", true);
		SEARCH_UNGROUP_THRESHOLD = builder
			.comment(
				"Filtered group child count must be lower than this value before search leaves it ungrouped.",
				"For example, 5 leaves groups with 2-4 matching entries ungrouped; 0 disables this behavior."
			)
			.defineInRange("searchUngroupThreshold", 5, 0, Integer.MAX_VALUE);
		COLLAPSED_GROUP_BACKGROUND_COLOR = builder
			.comment(
				"ARGB background color for collapsed group headers.",
				"Accepted formats: #AARRGGBB, 0xAARRGGBB, AARRGGBB, or RGB variants that keep the default alpha."
			)
			.define("collapsedGroupBackgroundColor", "#24FFFFFF");
		EXPANDED_GROUP_BACKGROUND_COLOR = builder
			.comment(
				"ARGB background color for expanded group headers and children.",
				"Accepted formats: #AARRGGBB, 0xAARRGGBB, AARRGGBB, or RGB variants that keep the default alpha."
			)
			.define("expandedGroupBackgroundColor", "#24FFFFFF");
		GROUP_NAME_COLOR = builder
			.comment(
				"RGB color for group display names. Alpha is ignored if an ARGB value is provided.",
				"Accepted formats: #RRGGBB, 0xRRGGBB, RRGGBB, or ARGB variants with ignored alpha."
			)
			.define("groupNameColor", "#FFAA00");
		EXPANDED_GROUP_BORDER_COLOR = builder
			.comment(
				"ARGB color for the connected border around expanded groups.",
				"Accepted formats: #AARRGGBB, 0xAARRGGBB, AARRGGBB, or RGB variants that keep the default alpha."
			)
			.define("expandedGroupBorderColor", "#66FFFFFF");
		builder.pop(); // ui

		// [debug]
		builder.push("debug");
		DEBUG_TIMING_LOGS = builder
			.comment(
				"Show Collapsible Groups timing logs in the game log.",
				"Useful when diagnosing slow JEI startup, group rebuilds, or editor/manager refreshes."
			)
			.define("enableTimingLogs", false);
		DEBUG_STARTUP_INDEX_VERIFY = builder
			.comment(
				"Verify the startup item-group index against a reference implementation.",
				"Builds the reference result and compares it with the optimized startup index. This is slower, but useful for correctness testing."
			)
			.define("verifyStartupIndex", false);
		DEBUG_EDITOR_INDEX_VERIFY = builder
			.comment(
				"Verify the editor preview index against a reference implementation.",
				"Useful when testing editor-side preview correctness."
			)
			.define("verifyEditorPreviewIndex", false);
		builder.pop(); // debug

		SPEC = builder.build();
	}

    private static volatile ModConfig nativeConfig;

    public static void bind(ModConfig config) { nativeConfig = config; }

    private final TomlSettingsStorage storage = new TomlSettingsStorage(
        Services.PLATFORM.getConfigDir().resolve("collapsiblegroups/collapsiblegroups.toml"));

    @Override public SettingsSnapshot read() throws Exception { return storage.read(); }
    @Override public void write(SettingsSnapshot settings) throws Exception { storage.write(settings); }

    @Override public void synchronize() {
        ModConfig config = nativeConfig;
        if (config == null) throw new IllegalStateException("Native config is not loaded");
        if (config.getConfigData() instanceof com.electronwill.nightconfig.core.file.FileConfig file) file.load();
        else throw new IllegalStateException("Native config has no file");
        SPEC.afterReload();
    }

	public ForgeConfig() {}

}
