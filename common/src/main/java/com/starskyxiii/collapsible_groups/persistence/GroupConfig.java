package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupDisplayName;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupFilterValidator;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;
import com.starskyxiii.collapsible_groups.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class GroupConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private GroupConfig() {}

	private static Path getConfigDir() {
		return Services.PLATFORM.getConfigDir().resolve("collapsiblegroups/groups");
	}

	private static Path getStateFile() {
		return Services.PLATFORM.getConfigDir().resolve("collapsiblegroups/expand_state.json");
	}

	private static Path getUiStateFile() {
		return Services.PLATFORM.getConfigDir().resolve("collapsiblegroups/ui_state.json");
	}

	/** Loads the set of expanded group IDs from disk. Returns an empty set if missing. */
	public static Set<String> loadExpandState() {
		Path file = getStateFile();
		if (!Files.exists(file)) return new HashSet<>();
		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			Set<String> expanded = new HashSet<>();
			if (obj.has("expanded")) {
				obj.get("expanded").getAsJsonArray().forEach(e -> expanded.add(e.getAsString()));
			}
			return expanded;
		} catch (Exception e) {
			Constants.LOG.warn("Failed to load expand state, starting fresh: {}", e.getMessage());
			return new HashSet<>();
		}
	}

	/** Saves the set of expanded group IDs to disk. */
	public static void saveExpandState(Set<String> expandedIds) {
		Path file = getStateFile();
		try {
			Files.createDirectories(file.getParent());
			JsonObject obj = new JsonObject();
			JsonArray arr = new JsonArray();
			expandedIds.forEach(arr::add);
			obj.add("expanded", arr);
			writeAtomically(file, GSON.toJson(obj));
		} catch (IOException e) {
			Constants.LOG.error("Failed to save expand state", e);
		}
	}

	public static UiState loadUiState() {
		Path file = getUiStateFile();
		if (!Files.exists(file)) return new UiState(true, true, false);
		return readUiStateFile(file, "UI state");
	}

	private static UiState readUiStateFile(Path file, String label) {
		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			boolean showBuiltin = !obj.has("show_builtin") || obj.get("show_builtin").getAsBoolean();
			boolean showKubeJs = !obj.has("show_kubejs") || obj.get("show_kubejs").getAsBoolean();
			boolean hideUsed = obj.has("hide_used") && obj.get("hide_used").getAsBoolean();
			return new UiState(showBuiltin, showKubeJs, hideUsed);
		} catch (Exception e) {
			Constants.LOG.warn("Failed to load {}, using defaults: {}", label, e.getMessage());
			return new UiState(true, true, false);
		}
	}

	public static void saveUiState(boolean showBuiltin, boolean showKubeJs, boolean hideUsed) {
		Path file = getUiStateFile();
		try {
			Files.createDirectories(file.getParent());
			JsonObject obj = new JsonObject();
			obj.addProperty("show_builtin", showBuiltin);
			obj.addProperty("show_kubejs", showKubeJs);
			obj.addProperty("hide_used", hideUsed);
			writeAtomically(file, GSON.toJson(obj));
		} catch (IOException e) {
			Constants.LOG.error("Failed to save UI state", e);
		}
	}

	/** Loads all group definition JSON files from the config directory (both user-created and customised built-in groups). */
	public static List<GroupDefinition> load() {
		Path dir = getConfigDir();
		try {
			Files.createDirectories(dir);
			return loadFromDir(dir);
		} catch (IOException e) {
			Constants.LOG.error("Failed to load group config from {}", dir, e);
			return new ArrayList<>();
		}
	}

	private static List<GroupDefinition> loadFromDir(Path dir) throws IOException {
		List<GroupDefinition> result = new ArrayList<>();
		try (var stream = Files.list(dir)) {
			stream.filter(p -> p.toString().endsWith(".json"))
				.sorted()
				.forEach(path -> {
					try {
						String json = Files.readString(path, StandardCharsets.UTF_8);
						GroupDefinition def = fromJson(json);
						if (def != null) result.add(def);
					} catch (Exception e) {
						Constants.LOG.error("Failed to parse group file: {}", path, e);
					}
				});
		}
		return result;
	}

	/** Saves a group definition to disk. Creates or overwrites the file. */
	public static void save(GroupDefinition group) {
		Path dir = getConfigDir();
		try {
			Files.createDirectories(dir);
			writeAtomically(dir.resolve(group.id() + ".json"), toJson(group));
		} catch (IOException e) {
			Constants.LOG.error("Failed to save group: {}", group.id(), e);
		}
	}

	/** Deletes a group's config file from disk. */
	public static void delete(String id) {
		Path dir = getConfigDir();
		try {
			Constants.LOG.debug("Deleting group '{}' from {}", id, dir);
			AtomicInteger deletedCount = new AtomicInteger();
			Path canonicalPath = dir.resolve(id + ".json");
			if (Files.deleteIfExists(canonicalPath)) {
				deletedCount.incrementAndGet();
				Constants.LOG.debug("Deleted canonical group file for '{}': {}", id, canonicalPath);
			}
			if (!Files.exists(dir)) {
				return;
			}
			try (var stream = Files.list(dir)) {
				stream.filter(path -> path.toString().endsWith(".json"))
					.filter(path -> !path.getFileName().toString().equals(id + ".json"))
					.forEach(path -> deleteIfGroupIdMatches(path, id, deletedCount));
			}
			if (deletedCount.get() == 0) {
				Constants.LOG.warn("No group config files were deleted for id '{}' in {}", id, dir);
			} else {
				Constants.LOG.debug("Deleted {} group config file(s) for id '{}'", deletedCount.get(), id);
			}
		} catch (IOException e) {
			Constants.LOG.error("Failed to delete group: {}", id, e);
		}
	}

	public static GroupDefinition fromJson(String json) {
		String id = null;
		try {
			ParsedGroupJson parsed = parseGroupJson(json);
			id = parsed.id();
			return new GroupDefinition(parsed.id(), parsed.displayName(), parsed.enabled(), parsed.filter(), parsed.iconIds());
		} catch (IllegalArgumentException e) {
			Constants.LOG.error("Group '{}': {}", id, e.getMessage());
			return null;
		} catch (Exception e) {
			Constants.LOG.error("Invalid group JSON: {}", json, e);
			return null;
		}
	}

	private static ParsedGroupJson parseGroupJson(String json) {
		JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
		String id = obj.has("id") ? obj.get("id").getAsString() : null;
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("Missing required non-blank 'id' field.");
		}

		GroupDisplayName displayName = parseDisplayName(id, obj.get("name"));
		boolean enabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();

		if (!obj.has("filter")) {
			throw new IllegalArgumentException("Missing required 'filter' field.");
		}

		List<String> iconIds = new ArrayList<>();
		if (obj.has("icon")) {
			var iconElement = obj.get("icon");
			if (iconElement.isJsonArray()) {
				iconElement.getAsJsonArray().forEach(e -> iconIds.add(e.getAsString()));
			} else {
				iconIds.add(iconElement.getAsString());
			}
		}

		GroupFilter filter = parseFilter(obj.getAsJsonObject("filter"));
		return new ParsedGroupJson(id, displayName, enabled, filter, List.copyOf(iconIds));
	}

	/**
	 * Parses the "name" field from JSON, supporting plain-string shorthand,
	 * explicit text objects, and fully localized objects.
	 */
	private static GroupDisplayName parseDisplayName(String groupId, JsonElement nameElement) {
		if (nameElement == null || nameElement.isJsonNull()) {
			return new GroupDisplayName.Localized(GroupTranslationHelper.keyForGroupId(groupId), "");
		}
		if (nameElement.isJsonPrimitive()) {
			// Shorthand format: "name": "Spawn Eggs"
			return new GroupDisplayName.Localized(
				GroupTranslationHelper.keyForGroupId(groupId),
				nameElement.getAsString()
			);
		}
		if (nameElement.isJsonObject()) {
			JsonObject nameObj = nameElement.getAsJsonObject();
			if (nameObj.has("translate")) {
				String key = nameObj.get("translate").getAsString();
				String fallback = nameObj.has("fallback")
					? nameObj.get("fallback").getAsString()
					: groupId;
				return new GroupDisplayName.Localized(key, fallback);
			}
			if (nameObj.has("text")) {
				return new GroupDisplayName.Localized(
					GroupTranslationHelper.keyForGroupId(groupId),
					nameObj.get("text").getAsString()
				);
			}
		}
		return new GroupDisplayName.Localized(GroupTranslationHelper.keyForGroupId(groupId), "");
	}

	public static String toJson(GroupDefinition group) {
		JsonObject obj = new JsonObject();
		obj.addProperty("id", group.id());

		GroupDisplayName dn = group.displayName();
		if (dn instanceof GroupDisplayName.Localized loc) {
			JsonObject nameObj = new JsonObject();
			nameObj.addProperty("translate", loc.key());
			nameObj.addProperty("fallback", loc.fallback());
			obj.add("name", nameObj);
		}

		obj.addProperty("enabled", group.enabled());

		if (!group.iconIds().isEmpty()) {
			if (group.iconIds().size() == 1) {
				obj.addProperty("icon", group.iconIds().getFirst());
			} else {
				JsonArray iconArr = new JsonArray();
				group.iconIds().forEach(iconArr::add);
				obj.add("icon", iconArr);
			}
		}

		obj.add("filter", serializeFilter(group.filter()));
		return GSON.toJson(obj);
	}

	// package-private for testing (GroupConfigComponentPathTest)
	static GroupFilter parseFilter(JsonObject obj) {
		if (obj.has("any")) {
			List<GroupFilter> children = new ArrayList<>();
			obj.getAsJsonArray("any").forEach(element -> children.add(parseFilter(element.getAsJsonObject())));
			return new GroupFilter.Any(children);
		}
		if (obj.has("all")) {
			List<GroupFilter> children = new ArrayList<>();
			obj.getAsJsonArray("all").forEach(element -> children.add(parseFilter(element.getAsJsonObject())));
			return new GroupFilter.All(children);
		}
		if (obj.has("not")) {
			return new GroupFilter.Not(parseFilter(obj.getAsJsonObject("not")));
		}
		if (obj.has("component")) {
			if (!obj.has("type")) {
				throw new IllegalArgumentException("Component filter node requires explicit type='item': " + obj);
			}
			if (!"item".equals(obj.get("type").getAsString())) {
				throw new IllegalArgumentException("Component filter node only supports type='item': " + obj);
			}
			if (!obj.has("value")) {
				throw new IllegalArgumentException("Component filter node requires 'value': " + obj);
			}
			// Discriminator: component + path -> ComponentPath; component alone -> HasComponent.
			// If path is present but fails grammar validation, fail fast rather than silently falling back.
			if (obj.has("path")) {
				String path = obj.get("path").getAsString();
				if (!GroupFilterValidator.PATH_PATTERN.matcher(path).matches()) {
					throw new IllegalArgumentException(
						"ComponentPath node has invalid path grammar: '" + path
						+ "'. Allowed: field, parent.child, array[n], array[n].field. Node: " + obj);
				}
				return new GroupFilter.ComponentPath(
					obj.get("component").getAsString(),
					path,
					obj.get("value").getAsString()
				);
			}
			return new GroupFilter.HasComponent(
				obj.get("component").getAsString(),
				obj.get("value").getAsString()
			);
		}
		if (obj.has("stack")) {
			if (!obj.has("type")) {
				throw new IllegalArgumentException("ExactStack node requires explicit type='item': " + obj);
			}
			String type = obj.get("type").getAsString();
			if (!"item".equals(type)) {
				throw new IllegalArgumentException("ExactStack only supports type='item': " + obj);
			}
			return new GroupFilter.ExactStack(obj.get("stack").getAsString());
		}
		if (obj.has("block_tag")) {
			return new GroupFilter.BlockTag(obj.get("block_tag").getAsString());
		}
		if (obj.has("item_path_starts_with")) {
			return new GroupFilter.ItemPathStartsWith(obj.get("item_path_starts_with").getAsString());
		}
		if (obj.has("item_path_ends_with")) {
			return new GroupFilter.ItemPathEndsWith(obj.get("item_path_ends_with").getAsString());
		}
		if (!obj.has("type")) {
			throw new IllegalArgumentException("Filter node is missing type: " + obj);
		}
		String type = obj.get("type").getAsString();
		if (obj.has("id")) return new GroupFilter.Id(type, obj.get("id").getAsString());
		if (obj.has("tag")) return new GroupFilter.Tag(type, obj.get("tag").getAsString());
		if (obj.has("namespace")) return new GroupFilter.Namespace(type, obj.get("namespace").getAsString());
		throw new IllegalArgumentException("Unknown filter node: " + obj);
	}

	// package-private for testing (GroupConfigComponentPathTest)
	static JsonObject serializeFilter(GroupFilter filter) {
		JsonObject obj = new JsonObject();
		switch (filter) {
			case GroupFilter.Any any -> {
				JsonArray arr = new JsonArray();
				any.children().forEach(child -> arr.add(serializeFilter(child)));
				obj.add("any", arr);
			}
			case GroupFilter.All all -> {
				JsonArray arr = new JsonArray();
				all.children().forEach(child -> arr.add(serializeFilter(child)));
				obj.add("all", arr);
			}
			case GroupFilter.Not not -> obj.add("not", serializeFilter(not.child()));
			case GroupFilter.Id id -> {
				obj.addProperty("type", id.ingredientType());
				obj.addProperty("id", id.id());
			}
			case GroupFilter.Tag tag -> {
				obj.addProperty("type", tag.ingredientType());
				obj.addProperty("tag", tag.tag());
			}
			case GroupFilter.BlockTag blockTag -> obj.addProperty("block_tag", blockTag.tag());
			case GroupFilter.ItemPathStartsWith startsWith -> obj.addProperty("item_path_starts_with", startsWith.prefix());
			case GroupFilter.ItemPathEndsWith endsWith -> obj.addProperty("item_path_ends_with", endsWith.suffix());
			case GroupFilter.Namespace namespace -> {
				obj.addProperty("type", namespace.ingredientType());
				obj.addProperty("namespace", namespace.namespace());
			}
			case GroupFilter.ExactStack stack -> {
				obj.addProperty("type", "item");
				obj.addProperty("stack", stack.encodedStack());
			}
			case GroupFilter.HasComponent hc -> {
				obj.addProperty("type", "item");
				obj.addProperty("component", hc.componentTypeId());
				obj.addProperty("value", hc.encodedValue());
			}
			case GroupFilter.ComponentPath cp -> {
				obj.addProperty("type", "item");
				obj.addProperty("component", cp.componentTypeId());
				obj.addProperty("path", cp.path());
				obj.addProperty("value", cp.expectedValue());
			}
		}
		return obj;
	}

	private static void deleteIfGroupIdMatches(Path path, String id, AtomicInteger deletedCount) {
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			if (obj.has("id") && id.equals(obj.get("id").getAsString())) {
				if (Files.deleteIfExists(path)) {
					deletedCount.incrementAndGet();
					Constants.LOG.debug("Deleted matching group file for '{}': {}", id, path);
				}
			}
		} catch (Exception e) {
			Constants.LOG.warn("Failed to inspect group file '{}' during delete cleanup: {}", path, e.getMessage());
		}
	}

	private static void writeAtomically(Path targetFile, String json) throws IOException {
		Path parent = targetFile.getParent();
		Path tempFile = parent.resolve(targetFile.getFileName().toString() + ".tmp");
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
				// Best-effort cleanup after a failed write or move.
			}
		}
	}

	private record ParsedGroupJson(
		String id,
		GroupDisplayName displayName,
		boolean enabled,
		GroupFilter filter,
		List<String> iconIds
	) {}

	public record UiState(boolean showBuiltin, boolean showKubeJs, boolean hideUsed) {}
}
