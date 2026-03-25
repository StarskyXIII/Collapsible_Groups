package com.starskyxiii.collapsible_groups.config;

import com.starskyxiii.collapsible_groups.platform.services.IConfigProvider;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Mod configuration registered through NeoForge's ModConfigSpec system.
 * Config file: config/collapsiblegroups/collapsiblegroups.toml
 *
 * Structure:
 *   [defaultGroups]
 *       enabled, loadGeneric, loadVanilla
 *       [defaultGroups.ModIntegration]
 *           loadModIntegration, loadAE2, loadRS2, loadEnderIO,
 *           loadChipped, loadRechiseled, loadChisel, loadApotheosis
 *   [ui]
 *       showManagerButton
 *   [debug]
 *       enableTimingLogs, verifyStartupIndex, verifyEditorPreviewIndex
 */
public final class NeoForgeConfig implements IConfigProvider {

	// ?? IConfigProvider ???????????????????????????????????????????????????????

	@Override public boolean loadDefaultGroups() { return LOAD_DEFAULT_GROUPS.get(); }
	@Override public boolean loadGenericGroups()  { return LOAD_GENERIC_GROUPS.get();  }
	@Override public boolean loadVanillaGroups()  { return LOAD_VANILLA_GROUPS.get();  }
	@Override public boolean showManagerButton()  { return SHOW_MANAGER_BUTTON.get();  }
	@Override public boolean debugTimingEnabled() { return DEBUG_TIMING_LOGS.get(); }
	@Override public boolean debugStartupIndexVerificationEnabled() { return DEBUG_STARTUP_INDEX_VERIFY.get(); }
	@Override public boolean debugEditorIndexVerificationEnabled() { return DEBUG_EDITOR_INDEX_VERIFY.get(); }


	// ?? defaultGroups ?????????????????????????????????????????????????????????

	/** Master switch ??set to false for a completely clean slate with no built-in groups. */
	public static final ModConfigSpec.BooleanValue LOAD_DEFAULT_GROUPS;

	/** Whether to load built-in generic cross-mod groups (potions, enchanted books, spawn eggs, ??. */
	public static final ModConfigSpec.BooleanValue LOAD_GENERIC_GROUPS;

	/** Whether to load built-in vanilla groupings (wool, concrete, terracotta, ??. */
	public static final ModConfigSpec.BooleanValue LOAD_VANILLA_GROUPS;

	// ?? defaultGroups.ModIntegration ??????????????????????????????????????????

	/** Master switch for all mod-integration groups. */
	public static final ModConfigSpec.BooleanValue LOAD_MOD_INTEGRATION_GROUPS;

	/**
	 * Whether to load built-in AE2 groups.
	 * Ignored if AE2 is not installed ??the setting cannot take effect without the mod.
	 */
	public static final ModConfigSpec.BooleanValue LOAD_AE2;

	/**
	 * Whether to load built-in RS2 groups.
	 * Ignored if RS2 is not installed ??the setting cannot take effect without the mod.
	 */
	public static final ModConfigSpec.BooleanValue LOAD_RS2;

	/**
	 * Whether to load built-in EnderIO groups.
	 * Ignored if EnderIO is not installed ??the setting cannot take effect without the mod.
	 */
	public static final ModConfigSpec.BooleanValue LOAD_ENDER_IO;

	/**
	 * Whether to load built-in Chipped block-variant groups.
	 * Ignored if Chipped is not installed ??the setting cannot take effect without the mod.
	 */
	public static final ModConfigSpec.BooleanValue LOAD_CHIPPED;

	/**
	 * Whether to load built-in Rechiseled block-variant groups.
	 * Ignored if Rechiseled is not installed ??the setting cannot take effect without the mod.
	 */
	public static final ModConfigSpec.BooleanValue LOAD_RECHISELED;

	/**
	 * Whether to load built-in Chisel block-variant groups.
	 * Ignored if Chisel is not installed ??the setting cannot take effect without the mod.
	 */
	public static final ModConfigSpec.BooleanValue LOAD_CHISEL;

	/**
	 * Whether to load built-in Apotheosis gem groups.
	 * Ignored if Apotheosis is not installed ??the setting cannot take effect without the mod.
	 */
	public static final ModConfigSpec.BooleanValue LOAD_APOTHEOSIS;

	/**
	 * Whether to load built-in Iron's Spellbooks scroll groups.
	 * Ignored if Iron's Spellbooks is not installed ??the setting cannot take effect without the mod.
	 */
	public static final ModConfigSpec.BooleanValue LOAD_IRONS_SPELLBOOKS;

	// ?? ui ????????????????????????????????????????????????????????????????????

	/** Whether to show the group manager button in the JEI overlay. */
	public static final ModConfigSpec.BooleanValue SHOW_MANAGER_BUTTON;

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
		LOAD_GENERIC_GROUPS = builder
			.comment("Whether to load built-in generic cross-mod groups (potions, enchanted books, spawn eggs, etc.)")
			.translation("collapsible_groups.configuration.defaultGroups.loadGeneric")
			.define("loadGeneric", true);
		LOAD_VANILLA_GROUPS = builder
			.comment("Whether to load built-in vanilla item groupings (wool, concrete, terracotta, etc.)")
			.translation("collapsible_groups.configuration.defaultGroups.loadVanilla")
			.define("loadVanilla", true);

		// [defaultGroups.ModIntegration]
		builder.translation("collapsible_groups.configuration.defaultGroups.ModIntegration").push("ModIntegration");
		LOAD_MOD_INTEGRATION_GROUPS = builder
			.comment(
				"Whether to load built-in mod-integration groups.",
				"Groups for mods that are not currently installed are always skipped."
			)
			.translation("collapsible_groups.configuration.defaultGroups.ModIntegration.loadModIntegration")
			.define("loadModIntegration", true);
		LOAD_AE2 = builder
			.comment(
				"Whether to load built-in AE2 groups.",
				"Has no effect if AE2 is not installed."
			)
			.translation("collapsible_groups.configuration.defaultGroups.ModIntegration.loadAE2")
			.define("loadAE2", true);
		LOAD_RS2 = builder
			.comment(
				"Whether to load built-in RS2 groups.",
				"Has no effect if RS2 is not installed."
			)
			.translation("collapsible_groups.configuration.defaultGroups.ModIntegration.loadRS2")
			.define("loadRS2", true);
		LOAD_ENDER_IO = builder
			.comment(
				"Whether to load built-in EnderIO groups.",
				"Has no effect if EnderIO is not installed."
			)
			.translation("collapsible_groups.configuration.defaultGroups.ModIntegration.loadEnderIO")
			.define("loadEnderIO", true);
		LOAD_CHIPPED = builder
			.comment(
				"Whether to load built-in Chipped block-variant groups (one group per block type).",
				"Has no effect if Chipped is not installed."
			)
			.translation("collapsible_groups.configuration.defaultGroups.ModIntegration.loadChipped")
			.define("loadChipped", true);
		LOAD_RECHISELED = builder
			.comment(
				"Whether to load built-in Rechiseled block-variant groups (one group per block type).",
				"Has no effect if Rechiseled is not installed."
			)
			.translation("collapsible_groups.configuration.defaultGroups.ModIntegration.loadRechiseled")
			.define("loadRechiseled", true);
		LOAD_CHISEL = builder
			.comment(
				"Whether to load built-in Chisel block-variant groups (one group per carving tag).",
				"Has no effect if Chisel is not installed."
			)
			.translation("collapsible_groups.configuration.defaultGroups.ModIntegration.loadChisel")
			.define("loadChisel", true);
		LOAD_APOTHEOSIS = builder
			.comment(
				"Whether to load built-in Apotheosis gem groups (one group per gem type).",
				"Has no effect if Apotheosis is not installed."
			)
			.translation("collapsible_groups.configuration.defaultGroups.ModIntegration.loadApotheosis")
			.define("loadApotheosis", true);
		LOAD_IRONS_SPELLBOOKS = builder
			.comment(
				"Whether to load built-in Iron's Spellbooks scroll groups (one group per spell).",
				"Has no effect if Iron's Spellbooks is not installed."
			)
			.translation("collapsible_groups.configuration.defaultGroups.ModIntegration.loadIronsSpellbooks")
			.define("loadIronsSpellbooks", true);
		builder.pop(); // ModIntegration
		builder.pop(); // defaultGroups

		// [ui]
		builder.translation("collapsible_groups.configuration.ui").push("ui");
		SHOW_MANAGER_BUTTON = builder
			.comment("Whether to show the group manager button in the JEI ingredient list overlay.")
			.translation("collapsible_groups.configuration.ui.showManagerButton")
			.define("showManagerButton", true);
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

	// ?? Convenience accessors with mod-presence guard ?????????????????????????

	/**
	 * Returns true only when the AE2 config flag is enabled AND AE2 is installed.
	 * Always false when AE2 is absent, regardless of the stored config value.
	 */
	public static boolean shouldLoadAE2() {
		return LOAD_DEFAULT_GROUPS.get()
			&& LOAD_MOD_INTEGRATION_GROUPS.get()
			&& LOAD_AE2.get()
			&& ModList.get().isLoaded("ae2");
	}

	/**
	 * Returns true only when the EnderIO config flag is enabled AND EnderIO is installed.
	 * Always false when EnderIO is absent, regardless of the stored config value.
	 */
	public static boolean shouldLoadEnderIO() {
		return LOAD_DEFAULT_GROUPS.get()
			&& LOAD_MOD_INTEGRATION_GROUPS.get()
			&& LOAD_ENDER_IO.get()
			&& ModList.get().isLoaded("enderio");
	}

	@Override public boolean shouldLoadRS2() {
		return LOAD_DEFAULT_GROUPS.get()
			&& LOAD_MOD_INTEGRATION_GROUPS.get()
			&& LOAD_RS2.get()
			&& ModList.get().isLoaded("refinedstorage");
	}

	/**
	 * Returns true only when the Chipped config flag is enabled AND Chipped is installed.
	 * Always false when Chipped is absent, regardless of the stored config value.
	 */
	@Override public boolean shouldLoadChipped() {
		return LOAD_DEFAULT_GROUPS.get()
			&& LOAD_MOD_INTEGRATION_GROUPS.get()
			&& LOAD_CHIPPED.get()
			&& ModList.get().isLoaded("chipped");
	}

	/**
	 * Returns true only when the Chisel config flag is enabled AND Chisel is installed.
	 * Always false when Chisel is absent, regardless of the stored config value.
	 */
	public static boolean shouldLoadChisel() {
		return LOAD_DEFAULT_GROUPS.get()
			&& LOAD_MOD_INTEGRATION_GROUPS.get()
			&& LOAD_CHISEL.get()
			&& ModList.get().isLoaded("chisel");
	}

	/**
	 * Returns true only when the Rechiseled config flag is enabled AND Rechiseled is installed.
	 * Always false when Rechiseled is absent, regardless of the stored config value.
	 */
	@Override public boolean shouldLoadRechiseled() {
		return LOAD_DEFAULT_GROUPS.get()
			&& LOAD_MOD_INTEGRATION_GROUPS.get()
			&& LOAD_RECHISELED.get()
			&& ModList.get().isLoaded("rechiseled");
	}

	/**
	 * Returns true only when the Apotheosis config flag is enabled AND Apotheosis is installed.
	 * Always false when Apotheosis is absent, regardless of the stored config value.
	 */
	public static boolean shouldLoadApotheosis() {
		return LOAD_DEFAULT_GROUPS.get()
			&& LOAD_MOD_INTEGRATION_GROUPS.get()
			&& LOAD_APOTHEOSIS.get()
			&& ModList.get().isLoaded("apotheosis");
	}

	/**
	 * Returns true only when the Iron's Spellbooks config flag is enabled AND Iron's Spellbooks is installed.
	 * Always false when Iron's Spellbooks is absent, regardless of the stored config value.
	 */
	public static boolean shouldLoadIronsSpellbooks() {
		return LOAD_DEFAULT_GROUPS.get()
			&& LOAD_MOD_INTEGRATION_GROUPS.get()
			&& LOAD_IRONS_SPELLBOOKS.get()
			&& ModList.get().isLoaded("irons_spellbooks");
	}

	public NeoForgeConfig() {}
}
