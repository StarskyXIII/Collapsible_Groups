package com.starskyxiii.collapsible_groups.config;

import com.starskyxiii.collapsible_groups.platform.services.IConfigProvider;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Forge config provider that reads and writes
 * {@code config/collapsiblegroups/collapsiblegroups.toml} via {@link ForgeConfigSpec}.
 */
public final class ForgeConfig implements IConfigProvider {
	private static final int COLLAPSED_GROUP_BACKGROUND_COLOR_DEFAULT = 0x24FFFFFF;
	private static final int EXPANDED_GROUP_BACKGROUND_COLOR_DEFAULT  = 0x24FFFFFF;
	private static final int GROUP_NAME_COLOR_DEFAULT                 = 0x00FFAA00;
	private static final int EXPANDED_GROUP_BORDER_COLOR_DEFAULT      = 0x66FFFFFF;

	private static final String[] MACAWS_SERIES_MODS = {
		"mcwwindows",
		"mcwbridges",
		"mcwdoors",
		"mcwfences",
		"mcwfurnitures",
		"mcwlights",
		"mcwpaths",
		"mcwstairs",
		"mcwtrpdoors",
	};

	// IConfigProvider

	@Override public boolean loadDefaultGroups()                   { return LOAD_DEFAULT_GROUPS.get(); }
	@Override public boolean loadGenericGroups()                   { return LOAD_GENERIC_GROUPS.get(); }
	@Override public boolean loadVanillaGroups()                   { return LOAD_VANILLA_GROUPS.get(); }
	@Override public boolean shouldLoadRechiseled() {
		return LOAD_DEFAULT_GROUPS.get()
			&& LOAD_MOD_INTEGRATION_GROUPS.get()
			&& LOAD_RECHISELED.get()
			&& net.minecraftforge.fml.ModList.get().isLoaded("rechiseled");
	}
	@Override public boolean shouldLoadMacawsSeries() {
		return LOAD_DEFAULT_GROUPS.get()
			&& LOAD_MOD_INTEGRATION_GROUPS.get()
			&& LOAD_MACAWS_SERIES.get()
			&& isAnyMacawsSeriesLoaded();
	}
	@Override public boolean showManagerButton()                   { return SHOW_MANAGER_BUTTON.get(); }
	@Override public boolean showGroupBackgrounds()                { return SHOW_GROUP_BACKGROUNDS.get(); }
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
	@Override public boolean debugTimingEnabled()                  { return DEBUG_TIMING_LOGS.get(); }
	@Override public boolean debugStartupIndexVerificationEnabled() { return DEBUG_STARTUP_INDEX_VERIFY.get(); }
	@Override public boolean debugEditorIndexVerificationEnabled() { return DEBUG_EDITOR_INDEX_VERIFY.get(); }

	// defaultGroups

	/** Master switch: set to false for a completely clean slate with no built-in groups. */
	public static final ForgeConfigSpec.BooleanValue LOAD_DEFAULT_GROUPS;

	/** Whether to load built-in generic cross-mod groups (potions, enchanted books, spawn eggs, etc.). */
	public static final ForgeConfigSpec.BooleanValue LOAD_GENERIC_GROUPS;

	/** Whether to load built-in vanilla groupings (wool, concrete, terracotta, etc.). */
	public static final ForgeConfigSpec.BooleanValue LOAD_VANILLA_GROUPS;

	// defaultGroups.ModIntegration

	/** Master switch for all mod-integration groups. */
	public static final ForgeConfigSpec.BooleanValue LOAD_MOD_INTEGRATION_GROUPS;

	/**
	 * Whether to load built-in Rechiseled block-variant groups.
	 * Ignored if Rechiseled is not installed; the setting cannot take effect without the mod.
	 */
	public static final ForgeConfigSpec.BooleanValue LOAD_RECHISELED;

	/**
	 * Whether to load built-in Macaw's series groups.
	 * Ignored if none of the supported Macaw's mods are installed.
	 */
	public static final ForgeConfigSpec.BooleanValue LOAD_MACAWS_SERIES;

	// ui

	/** Whether to show the group manager button in the JEI overlay. */
	public static final ForgeConfigSpec.BooleanValue SHOW_MANAGER_BUTTON;

	/** Whether grouped slots draw a semi-transparent background tint. */
	public static final ForgeConfigSpec.BooleanValue SHOW_GROUP_BACKGROUNDS;

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
		LOAD_GENERIC_GROUPS = builder
			.comment("Whether to load built-in generic cross-mod groups (potions, enchanted books, spawn eggs, etc.)")
			.define("loadGeneric", true);
		LOAD_VANILLA_GROUPS = builder
			.comment("Whether to load built-in vanilla item groupings (wool, concrete, terracotta, etc.)")
			.define("loadVanilla", true);

		// [defaultGroups.ModIntegration]
		builder.push("ModIntegration");
		LOAD_MOD_INTEGRATION_GROUPS = builder
			.comment(
				"Whether to load built-in mod-integration groups.",
				"Groups for mods that are not currently installed are always skipped."
			)
			.define("loadModIntegration", true);
		LOAD_RECHISELED = builder
			.comment(
				"Whether to load built-in Rechiseled block-variant groups (one group per block type).",
				"Has no effect if Rechiseled is not installed."
			)
			.define("loadRechiseled", true);
		LOAD_MACAWS_SERIES = builder
			.comment(
				"Whether to load built-in Macaw's series block-tag groups.",
				"Has no effect if none of the supported Macaw's mods are installed."
			)
			.define("loadMacawsSeries", true);
		// Note: Chipped and RS2 integration groups are not yet implemented on Forge.
		// IConfigProvider defaults shouldLoadChipped() and shouldLoadRS2() to false.
		builder.pop(); // ModIntegration
		builder.pop(); // defaultGroups

		// [ui]
		builder.push("ui");
		SHOW_MANAGER_BUTTON = builder
			.comment("Whether to show the group manager button in the JEI ingredient list overlay.")
			.define("showManagerButton", true);
		SHOW_GROUP_BACKGROUNDS = builder
			.comment(
				"Whether grouped JEI slots draw a semi-transparent background tint.",
				"Set to false to keep group backgrounds fully transparent while preserving the +/- indicator and borders."
			)
			.define("showGroupBackgrounds", true);
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

	public ForgeConfig() {}

	private static boolean isAnyMacawsSeriesLoaded() {
		net.minecraftforge.fml.ModList modList = net.minecraftforge.fml.ModList.get();
		for (String modId : MACAWS_SERIES_MODS) {
			if (modList.isLoaded(modId)) {
				return true;
			}
		}
		return false;
	}
}
