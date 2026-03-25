package com.starskyxiii.collapsible_groups.i18n;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Deploys bundled group translation files from the mod jar to the config
 * overlay directory.
 *
 * <p>Only the current locale is deployed. Existing valid user edits are
 * preserved, and malformed overlay files are never overwritten.
 */
public final class GroupLangDeployer {

	private static final Gson GSON = new GsonBuilder()
		.setPrettyPrinting()
		.disableHtmlEscaping()
		.create();

	private static final String RESOURCE_PREFIX =
		"/assets/collapsible_groups/group_lang/";

	private GroupLangDeployer() {}

	/**
	 * Deploys the bundled group translation file for the current locale to the
	 * config overlay directory.
	 */
	public static void deployCurrentLocale() {
		String locale = GroupTranslationHelper.getCurrentLocale();
		Map<String, String> bundled = loadBundledResource(locale);
		if (bundled.isEmpty()) {
			return;
		}

		Path targetDir = GroupTranslationHelper.getOverlayLangDir();
		try {
			Files.createDirectories(targetDir);
		} catch (IOException e) {
			Constants.LOG.warn(
				"[CollapsibleGroups] Could not create group lang overlay dir '{}': {}",
				targetDir,
				e.getMessage()
			);
			return;
		}

		Path targetFile = targetDir.resolve(locale + ".json");
		Optional<Map<String, String>> existingOpt = loadExistingOverlay(targetFile);
		if (existingOpt.isEmpty()) {
			Constants.LOG.warn(
				"[CollapsibleGroups] Skipping bundled group lang deploy for locale '{}' because the existing overlay file is invalid: {}",
				locale,
				targetFile
			);
			return;
		}

		Map<String, String> existing = existingOpt.get();
		Map<String, String> merged = new LinkedHashMap<>(existing);
		int added = 0;
		for (Map.Entry<String, String> entry : bundled.entrySet()) {
			if (!merged.containsKey(entry.getKey())) {
				merged.put(entry.getKey(), entry.getValue());
				added++;
			}
		}

		if (added == 0 && Files.exists(targetFile)) {
			return;
		}

		try {
			writeAtomically(targetFile, GSON.toJson(merged));
			Constants.LOG.info(
				"[CollapsibleGroups] Deployed bundled group lang for locale '{}': {} new keys (total {})",
				locale,
				added,
				merged.size()
			);
		} catch (IOException e) {
			Constants.LOG.warn(
				"[CollapsibleGroups] Failed to write group lang overlay '{}': {}",
				targetFile,
				e.getMessage()
			);
		}
	}

	private static Map<String, String> loadBundledResource(String locale) {
		String resourcePath = RESOURCE_PREFIX + locale + ".json";
		try (InputStream is = GroupLangDeployer.class.getResourceAsStream(resourcePath)) {
			if (is == null) {
				return Map.of();
			}
			try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(is, StandardCharsets.UTF_8))) {
				return parseJsonMap(reader);
			}
		} catch (Exception e) {
			Constants.LOG.warn(
				"[CollapsibleGroups] Failed to read bundled group lang '{}' from '{}': {}",
				locale,
				resourcePath,
				e.getMessage()
			);
			return Map.of();
		}
	}

	private static Optional<Map<String, String>> loadExistingOverlay(Path file) {
		if (!Files.exists(file)) {
			return Optional.of(new LinkedHashMap<>());
		}
		try {
			try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				return Optional.of(parseJsonMap(reader));
			}
		} catch (Exception e) {
			Constants.LOG.warn(
				"[CollapsibleGroups] Existing group lang overlay '{}' is invalid and will not be overwritten: {}",
				file,
				e.getMessage()
			);
			return Optional.empty();
		}
	}

	private static Map<String, String> parseJsonMap(BufferedReader reader) {
		JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
		Map<String, String> map = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
			if (entry.getValue().isJsonPrimitive()) {
				map.put(entry.getKey(), entry.getValue().getAsString());
			}
		}
		return map;
	}

	private static void writeAtomically(Path targetFile, String json) throws IOException {
		Path parent = targetFile.getParent();
		String tempName = targetFile.getFileName().toString() + ".tmp";
		Path tempFile = parent.resolve(tempName);
		try {
			Files.writeString(
				tempFile,
				json,
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE
			);
			try {
				Files.move(
					tempFile,
					targetFile,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE
				);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			try {
				Files.deleteIfExists(tempFile);
			} catch (IOException ignored) {
				// Best effort cleanup after a failed move/write.
			}
		}
	}
}
