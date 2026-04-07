package com.starskyxiii.collapsible_groups.platform.services;

/**
 * Platform-agnostic access to mod configuration values used by common code.
 * Each loader provides its own implementation (NeoForge via ModConfigSpec,
 * Fabric/Forge via their respective config systems).
 */
public interface IConfigProvider {

	/** Master switch: false means no built-in default groups are loaded. */
	boolean loadDefaultGroups();

	/** Whether to load built-in generic cross-mod groups (potions, enchanted books, music discs, etc.). */
	boolean loadGenericGroups();

	/** Whether to load built-in vanilla item family groups (wool, concrete, terracotta, etc.). */
	boolean loadVanillaGroups();

	/** Whether to show the group manager button in the JEI ingredient list overlay. */
	boolean showManagerButton();

	/** Whether debug timing/performance logs should be emitted. */
	boolean debugTimingEnabled();

	/**
	 * Whether to load built-in Chipped block-variant groups.
	 * Default false; override on loaders that support Chipped.
	 */
	default boolean shouldLoadChipped() { return false; }

	/**
	 * Whether to load built-in Rechiseled block-variant groups.
	 * Default false; override on loaders that support Rechiseled.
	 */
	default boolean shouldLoadRechiseled() { return false; }

	/**
	 * Whether to load built-in Refined Storage 2 block variant groups.
	 * Default false; overridden by loaders that support RS2.
	 */
	default boolean shouldLoadRS2() { return false; }

	/**
	 * Whether to load built-in Macaw's series block-tag groups.
	 * Default false; overridden by loaders that support the Macaw's series mods.
	 */
	default boolean shouldLoadMacawsSeries() { return false; }

	/** Whether startup index verification should compare the optimized builder against a reference implementation. */
	boolean debugStartupIndexVerificationEnabled();

	/** Whether the editor item index should run its correctness verification mode. */
	boolean debugEditorIndexVerificationEnabled();
}
