package com.starskyxiii.collapsible_groups.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupDisplayName;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pure logic for the {@code /cg group_key dump <locale>} command.
 *
 * <p>This class lives in common and has <b>no</b> dependency on any
 * loader-specific Brigadier source type.  Each loader registers its own
 * command node and delegates here.
 *
 * <p>The dump always uses {@code displayName().key() -> displayName().fallback()},
 * never the runtime-resolved {@code name()}.  This guarantees stable output
 * regardless of the current game language.
 */
public final class GroupKeyDumpLogic {

	private static final Gson GSON = new GsonBuilder()
		.setPrettyPrinting()
		.disableHtmlEscaping()
		.create();

	private GroupKeyDumpLogic() {}

	/**
	 * Dumps all group translation keys to a JSON file.
	 *
	 * @param locale   the locale code used for the output filename (e.g. {@code "en_us"})
	 * @param clean    if {@code true}, remove orphaned keys that no longer correspond to any loaded group
	 * @param feedback callback for sending chat feedback to the player
	 * @return {@code 1} on success, {@code 0} on failure
	 */
	public static int dump(String locale, boolean clean, Consumer<Component> feedback) {
		List<GroupDefinition> allGroups = GroupRegistry.getAllIncludingKubeJs();

		if (allGroups.isEmpty()) {
			feedback.accept(Component.translatable("collapsible_groups.command.dump_empty"));
			return 0;
		}

		// Build ordered map: key -> fallback
		Map<String, String> entries = new LinkedHashMap<>();
		for (GroupDefinition group : allGroups) {
			GroupDisplayName dn = group.displayName();
			entries.put(dn.key(), dn.fallback());
		}

		// The set of all valid keys (used for clean mode)
		Set<String> validKeys = entries.keySet();

		// Write to config/collapsiblegroups/lang/<locale>.json
		Path outputDir = GroupTranslationHelper.getOverlayLangDir();
		Path outputFile = outputDir.resolve(locale + ".json");

		try {
			Files.createDirectories(outputDir);

			int removedCount = 0;

			// If the file already exists, merge: keep existing entries, add new ones
			if (Files.exists(outputFile)) {
				try {
					String existingJson = Files.readString(outputFile, StandardCharsets.UTF_8);
					Map<?, ?> existing = GSON.fromJson(existingJson, LinkedHashMap.class);
					if (existing != null) {
						Map<String, String> merged = new LinkedHashMap<>();
						// Existing entries first (preserve user edits), optionally filtering orphans
						for (Map.Entry<?, ?> e : existing.entrySet()) {
							String key = String.valueOf(e.getKey());
							if (clean && !validKeys.contains(key)) {
								removedCount++;
								continue;
							}
							merged.put(key, String.valueOf(e.getValue()));
						}
						// Add new keys that don't exist yet
						for (Map.Entry<String, String> e : entries.entrySet()) {
							merged.putIfAbsent(e.getKey(), e.getValue());
						}
						entries = merged;
					}
				} catch (Exception e) {
					Constants.LOG.warn("[CollapsibleGroups] Could not parse existing overlay file '{}', overwriting: {}",
						outputFile, e.getMessage());
				}
			}

			String json = GSON.toJson(entries);
			Files.writeString(outputFile, json, StandardCharsets.UTF_8);

			feedback.accept(Component.translatable(
				"collapsible_groups.command.dump_success",
				entries.size(),
				outputFile.toString()
			));
			if (clean && removedCount > 0) {
				feedback.accept(Component.translatable(
					"collapsible_groups.command.dump_cleaned",
					removedCount
				));
			}
			Constants.LOG.info("[CollapsibleGroups] Dumped {} group keys to {}{}", entries.size(), outputFile,
				clean && removedCount > 0 ? " (removed " + removedCount + " orphaned keys)" : "");
			return 1;
		} catch (IOException e) {
			Constants.LOG.error("[CollapsibleGroups] Failed to write dump file", e);
			feedback.accept(Component.translatable(
				"collapsible_groups.command.dump_error",
				e.getMessage()
			));
			return 0;
		}
	}
}
