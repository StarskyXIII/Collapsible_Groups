package com.starskyxiii.collapsible_groups.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.platform.services.IConfigProvider;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fabric config provider ??reads/writes {@code config/collapsiblegroups/collapsiblegroups.json}.
 * Falls back to defaults when the file does not exist or cannot be parsed.
 */
public final class FabricConfig implements IConfigProvider {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
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
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("collapsiblegroups")
		.resolve("collapsiblegroups.json");

	private static FabricConfigData data = new FabricConfigData();

	/** Load config from disk; writes defaults if the file does not exist. */
	public static void load() {
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
				FabricConfigData loaded = GSON.fromJson(reader, FabricConfigData.class);
				data = loaded != null ? loaded : new FabricConfigData();
			} catch (Exception e) {
				Constants.LOG.warn("[CollapsibleGroups] Failed to read config, using defaults: {}", e.getMessage());
				data = new FabricConfigData();
			}
		} else {
			data = new FabricConfigData();
			save(data);
		}
	}

	/** Write {@code newData} to disk and update the in-memory snapshot. */
	public static void save(FabricConfigData newData) {
		data = newData;
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(newData, writer);
			}
		} catch (IOException e) {
			Constants.LOG.warn("[CollapsibleGroups] Failed to write config: {}", e.getMessage());
		}
	}

	public static FabricConfigData getData() { return data; }

	// IConfigProvider

	@Override public boolean loadDefaultGroups()                    { return data.defaultGroups.enabled; }
	@Override public boolean loadGenericGroups()                    { return data.defaultGroups.loadGeneric; }
	@Override public boolean loadVanillaGroups()                    { return data.defaultGroups.loadVanilla; }
	@Override public boolean shouldLoadChipped()   {
		return data.defaultGroups.enabled
			&& data.defaultGroups.modIntegration.loadModIntegration
			&& data.defaultGroups.modIntegration.loadChipped
			&& FabricLoader.getInstance().isModLoaded("chipped");
	}
	@Override public boolean shouldLoadRechiseled() {
		return data.defaultGroups.enabled
			&& data.defaultGroups.modIntegration.loadModIntegration
			&& data.defaultGroups.modIntegration.loadRechiseled
			&& FabricLoader.getInstance().isModLoaded("rechiseled");
	}
	@Override public boolean shouldLoadRS2() {
		return data.defaultGroups.enabled
			&& data.defaultGroups.modIntegration.loadModIntegration
			&& data.defaultGroups.modIntegration.loadRS2
			&& FabricLoader.getInstance().isModLoaded("refinedstorage");
	}
	@Override public boolean shouldLoadMacawsSeries() {
		return data.defaultGroups.enabled
			&& data.defaultGroups.modIntegration.loadModIntegration
			&& data.defaultGroups.modIntegration.loadMacawsSeries
			&& isAnyMacawsSeriesLoaded();
	}
	@Override public boolean showManagerButton()                    { return data.ui.showManagerButton; }
	@Override public boolean useOreUiManager()                     { return data.ui.useOreUiManager; }
	@Override public boolean showGroupBackgrounds()                 { return data.ui.showGroupBackgrounds; }
	@Override public int collapsedGroupBackgroundColor() {
		return ColorConfigParser.parseArgb(data.ui.collapsedGroupBackgroundColor, UiData.COLLAPSED_GROUP_BACKGROUND_COLOR_DEFAULT);
	}
	@Override public int expandedGroupBackgroundColor() {
		return ColorConfigParser.parseArgb(data.ui.expandedGroupBackgroundColor, UiData.EXPANDED_GROUP_BACKGROUND_COLOR_DEFAULT);
	}
	@Override public int groupNameColor() {
		return ColorConfigParser.parseRgb(data.ui.groupNameColor, UiData.GROUP_NAME_COLOR_DEFAULT);
	}
	@Override public int expandedGroupBorderColor() {
		return ColorConfigParser.parseArgb(data.ui.expandedGroupBorderColor, UiData.EXPANDED_GROUP_BORDER_COLOR_DEFAULT);
	}
	@Override public boolean debugTimingEnabled()                   { return data.debug.enableTimingLogs; }
	@Override public boolean debugStartupIndexVerificationEnabled() { return data.debug.verifyStartupIndex; }
	@Override public boolean debugEditorIndexVerificationEnabled()  { return data.debug.verifyEditorPreviewIndex; }

	// Config data POJO

	public static final class FabricConfigData {
		public DefaultGroupsData defaultGroups = new DefaultGroupsData();
		public UiData            ui            = new UiData();
		public DebugData         debug         = new DebugData();
	}

	public static final class DefaultGroupsData {
		public boolean           enabled        = true;
		public boolean           loadGeneric    = true;
		public boolean           loadVanilla    = true;
		public ModIntegrationData modIntegration = new ModIntegrationData();
	}

	public static final class ModIntegrationData {
		public boolean loadModIntegration = true;
		public boolean loadChipped        = true;
		public boolean loadRechiseled     = true;
		public boolean loadRS2            = true;
		public boolean loadMacawsSeries   = true;
	}

	public static final class UiData {
		public static final int COLLAPSED_GROUP_BACKGROUND_COLOR_DEFAULT = 0x24FFFFFF;
		public static final int EXPANDED_GROUP_BACKGROUND_COLOR_DEFAULT  = 0x24FFFFFF;
		public static final int GROUP_NAME_COLOR_DEFAULT                 = 0x00FFAA00;
		public static final int EXPANDED_GROUP_BORDER_COLOR_DEFAULT      = 0x66FFFFFF;

		public boolean showManagerButton    = true;
		public boolean useOreUiManager      = false;
		public boolean showGroupBackgrounds = true;
		public String collapsedGroupBackgroundColor = "#24FFFFFF";
		public String expandedGroupBackgroundColor  = "#24FFFFFF";
		public String groupNameColor               = "#FFAA00";
		public String expandedGroupBorderColor     = "#66FFFFFF";
	}

	public static final class DebugData {
		public boolean enableTimingLogs          = false;
		public boolean verifyStartupIndex        = false;
		public boolean verifyEditorPreviewIndex  = false;
	}

	private static boolean isAnyMacawsSeriesLoaded() {
		FabricLoader loader = FabricLoader.getInstance();
		for (String modId : MACAWS_SERIES_MODS) {
			if (loader.isModLoaded(modId)) {
				return true;
			}
		}
		return false;
	}
}
