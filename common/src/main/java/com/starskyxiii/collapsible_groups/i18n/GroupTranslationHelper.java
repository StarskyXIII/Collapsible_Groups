package com.starskyxiii.collapsible_groups.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.platform.Services;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Helper for generating stable translation keys from group IDs and managing
 * the config-directory overlay lang files.
 *
 * <p>All groups -- built-in, user-created, and KubeJS -- receive an
 * auto-generated key of the form {@code collapsible_groups.group.<groupId>}.
 *
 * <h2>Overlay lang files</h2>
 * <p>Players or modpack authors can place translation overrides in:
 * <pre>config/collapsiblegroups/lang/&lt;locale&gt;.json</pre>
 * These are loaded on startup and whenever groups are reloaded.
 * The resolution order for display text is:
 * <ol>
 *   <li>Overlay lang ({@code config/collapsiblegroups/lang/<locale>.json})</li>
 *   <li>Minecraft {@code Language} system ({@code assets/.../lang/*.json})</li>
 *   <li>Fallback plain-text name</li>
 * </ol>
 */
public final class GroupTranslationHelper {

	private static final String GROUP_KEY_PREFIX = "collapsible_groups.group.";

	/**
	 * Current overlay translations, keyed by translation key.
	 * Only the overlay for the current game locale is loaded.
	 * Empty map when no overlay file exists or before first load.
	 */
	private static volatile Map<String, String> overlay = Collections.emptyMap();

	/** The locale that {@link #overlay} was loaded for. */
	private static volatile String overlayLocale = "";

	private GroupTranslationHelper() {}

	/**
	 * Returns the translation key for the given group ID.
	 *
	 * @param groupId the internal group identifier
	 * @return a stable translation key, e.g. {@code "collapsible_groups.group.__default_potions"}
	 */
	public static String keyForGroupId(String groupId) {
		return GROUP_KEY_PREFIX + groupId;
	}

	// -----------------------------------------------------------------------
	// Overlay lang file support
	// -----------------------------------------------------------------------

	/**
	 * Returns the overlay lang directory: {@code config/collapsiblegroups/lang/}.
	 */
	public static Path getOverlayLangDir() {
		return Services.PLATFORM.getConfigDir().resolve("collapsiblegroups/lang");
	}

	/**
	 * Reloads the overlay translations for the current game locale.
	 * Call this during startup and whenever groups are reloaded.
	 */
	public static void reloadOverlay() {
		String locale = getCurrentLocale();
		overlay = loadOverlayFile(locale);
		overlayLocale = locale;
		if (!overlay.isEmpty()) {
			Constants.LOG.info("[CollapsibleGroups] Loaded {} overlay translations for locale '{}'",
				overlay.size(), locale);
		}
	}

	/**
	 * Looks up a key in the overlay. Returns the translated value if present.
	 */
	public static Optional<String> lookupOverlay(String key) {
		// If the game locale changed since last load, the overlay may be stale.
		// We don't hot-reload here (that happens at reload time), but we guard
		// against returning stale data for a different locale.
		String locale = getCurrentLocale();
		if (!locale.equals(overlayLocale)) {
			// Locale changed (e.g. player switched language in settings).
			// Reload lazily. This is a cheap operation (single file read).
			reloadOverlay();
		}
		return Optional.ofNullable(overlay.get(key));
	}

	/**
	 * Returns a snapshot of the current overlay map (unmodifiable).
	 */
	public static Map<String, String> getOverlaySnapshot() {
		return overlay;
	}

	/**
	 * Returns the current client locale, falling back to {@code en_us} during
	 * very early init when the Minecraft instance is not ready yet.
	 */
	public static String getCurrentLocale() {
		try {
			return Minecraft.getInstance().options.languageCode;
		} catch (Exception e) {
			return "en_us";
		}
	}

	// -----------------------------------------------------------------------
	// Internal
	// -----------------------------------------------------------------------

	private static Map<String, String> loadOverlayFile(String locale) {
		Path dir = getOverlayLangDir();
		Path file = dir.resolve(locale + ".json");
		if (!Files.exists(file)) {
			return Collections.emptyMap();
		}
		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			Map<String, String> map = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					map.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
			return Collections.unmodifiableMap(map);
		} catch (Exception e) {
			Constants.LOG.warn("[CollapsibleGroups] Failed to load overlay lang file '{}': {}",
				file, e.getMessage());
			return Collections.emptyMap();
		}
	}

}
