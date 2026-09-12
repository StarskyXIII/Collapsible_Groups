package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.group.GroupDocumentFormat;
import com.starskyxiii.collapsible_groups.group.GroupFormatPolicy;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.config.ColorConfigParser;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupDisplayName;
import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterValidator;
import com.starskyxiii.collapsible_groups.group.filter.FilterNodeCapabilities;
import com.starskyxiii.collapsible_groups.group.filter.FilterNodeKind;
import com.starskyxiii.collapsible_groups.group.GroupTheme;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;
import com.starskyxiii.collapsible_groups.internal.version.data.Minecraft1201NbtAccess;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataAccesses;
import com.starskyxiii.collapsible_groups.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class GroupConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	private static final int UNAVAILABLE_WARNING_LIMIT = 10;

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

	private static Path getEnabledOverridesFile() {
		return Services.PLATFORM.getConfigDir().resolve("collapsiblegroups/enabled_overrides.json");
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
		if (!Files.exists(file)) return new UiState(true, true, false,
			UiState.SOURCE_FILTER_DEFAULT, UiState.SORT_MODE_DEFAULT);
		return readUiStateFile(file, "UI state");
	}

	private static UiState readUiStateFile(Path file, String label) {
		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			boolean showBuiltin = !obj.has("show_builtin") || obj.get("show_builtin").getAsBoolean();
			boolean showKubeJs = !obj.has("show_kubejs") || obj.get("show_kubejs").getAsBoolean();
			boolean hideUsed = obj.has("hide_used") && obj.get("hide_used").getAsBoolean();
			String managerSourceFilter = UiState.SOURCE_FILTER_DEFAULT;
			String managerSortMode = UiState.SORT_MODE_DEFAULT;
			if (obj.has("manager_source_filter")
				&& obj.get("manager_source_filter").isJsonPrimitive()
				&& obj.get("manager_source_filter").getAsJsonPrimitive().isString()) {
				managerSourceFilter = obj.get("manager_source_filter").getAsString();
			}
			if (obj.has("manager_sort_mode")
				&& obj.get("manager_sort_mode").isJsonPrimitive()
				&& obj.get("manager_sort_mode").getAsJsonPrimitive().isString()) {
				managerSortMode = obj.get("manager_sort_mode").getAsString();
			}
			boolean showEmpty = obj.has("manager_show_empty") && obj.get("manager_show_empty").getAsBoolean();
			return new UiState(showBuiltin, showKubeJs, hideUsed, managerSourceFilter, managerSortMode, showEmpty);
		} catch (Exception e) {
			Constants.LOG.warn("Failed to load {}, using defaults: {}", label, e.getMessage());
			return new UiState(true, true, false, UiState.SOURCE_FILTER_DEFAULT, UiState.SORT_MODE_DEFAULT);
		}
	}

	public static void saveUiState(boolean showBuiltin, boolean showKubeJs, boolean hideUsed,
	                               String managerSourceFilter, String managerSortMode) {
		saveUiState(showBuiltin, showKubeJs, hideUsed, managerSourceFilter, managerSortMode, false);
	}

	public static void saveUiState(boolean showBuiltin, boolean showKubeJs, boolean hideUsed,
		String managerSourceFilter, String managerSortMode, boolean managerShowEmpty) {
		Path file = getUiStateFile();
		try {
			Files.createDirectories(file.getParent());
			JsonObject obj = new JsonObject();
			obj.addProperty("show_builtin", showBuiltin);
			obj.addProperty("show_kubejs", showKubeJs);
			obj.addProperty("hide_used", hideUsed);
			obj.addProperty("manager_source_filter", managerSourceFilter);
			obj.addProperty("manager_sort_mode", managerSortMode);
			obj.addProperty("manager_show_empty", managerShowEmpty);
			writeAtomically(file, GSON.toJson(obj));
		} catch (IOException e) {
			Constants.LOG.error("Failed to save UI state", e);
		}
	}

	public static Map<String, Boolean> loadEnabledOverrides() {
		Path file = getEnabledOverridesFile();
		if (!Files.exists(file)) return Map.of();
		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			return parseEnabledOverrides(json);
		} catch (Exception e) {
			Constants.LOG.warn("Failed to load enabled overrides, using none: {}", e.getMessage());
			return Map.of();
		}
	}

	public static void saveEnabledOverrides(Map<String, Boolean> overrides) {
		saveEnabledOverridesChecked(overrides);
	}

	static boolean saveEnabledOverridesChecked(Map<String, Boolean> overrides) {
		Path file = getEnabledOverridesFile();
		try {
			Files.createDirectories(file.getParent());
			writeAtomically(file, serializeEnabledOverrides(overrides));
			return true;
		} catch (IOException e) {
			Constants.LOG.error("Failed to save enabled overrides", e);
			return false;
		}
	}

	static Map<String, Boolean> parseEnabledOverrides(String json) {
		try {
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject()) return Map.of();
			JsonElement overridesElement = root.getAsJsonObject().get("overrides");
			if (overridesElement == null || !overridesElement.isJsonObject()) return Map.of();

			Map<String, Boolean> overrides = new LinkedHashMap<>();
			for (var entry : overridesElement.getAsJsonObject().entrySet()) {
				String id = entry.getKey();
				JsonElement value = entry.getValue();
				if (id == null || id.isBlank() || value == null || !value.isJsonPrimitive()) continue;
				var primitive = value.getAsJsonPrimitive();
				if (!primitive.isBoolean()) continue;
				overrides.put(id, primitive.getAsBoolean());
			}
			return Map.copyOf(overrides);
		} catch (Exception e) {
			return Map.of();
		}
	}

	static String serializeEnabledOverrides(Map<String, Boolean> overrides) {
		JsonObject root = new JsonObject();
		JsonObject values = new JsonObject();
		if (overrides != null) {
			overrides.entrySet().stream()
				.filter(entry -> entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null)
				.sorted(Map.Entry.comparingByKey())
				.forEach(entry -> values.addProperty(entry.getKey(), entry.getValue()));
		}
		root.add("overrides", values);
		return GSON.toJson(root);
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
		AtomicInteger unavailableWarnings = new AtomicInteger();
		try (var stream = Files.list(dir)) {
			stream.filter(p -> p.toString().endsWith(".json"))
				.sorted()
				.forEach(path -> {
					try {
						String json = Files.readString(path, StandardCharsets.UTF_8);
						GroupDefinition def = fromJson(json);
						if (def != null) {
							result.add(def);
							if (def.hasUnavailableFilter()) {
								int warningIndex = unavailableWarnings.getAndIncrement();
								if (warningIndex < UNAVAILABLE_WARNING_LIMIT) {
									Constants.LOG.warn(
										"Group '{}' contains unavailable filter node(s) {} and will remain inert until supported; raw JSON is preserved.",
										def.id(), FilterNodeCapabilities.unavailableKinds(def.filter())
									);
								} else if (warningIndex == UNAVAILABLE_WARNING_LIMIT) {
									Constants.LOG.warn("Additional groups with unavailable filter nodes were loaded; further warnings are suppressed.");
								}
							}
						}
					} catch (Exception e) {
						Constants.LOG.error("Failed to parse group file: {}", path, e);
					}
				});
		}
		return result;
	}

	/** Saves a group definition to disk. Creates or overwrites the file. */
	public static void save(GroupDefinition group) {
		saveChecked(group);
	}

	static boolean saveChecked(GroupDefinition group) {
		if (!GroupFormatPolicy.writable(group)) {
			Constants.LOG.warn("Cannot save group '{}' in its document format", group.id());
			return false;
		}
		Path dir = getConfigDir();
		Path targetFile = dir.resolve(group.id() + ".json");
		try {
			String json = toJson(group);
			if (Files.exists(dir)) {
				try (var files = Files.list(dir)) {
					for (Path path : files.filter(file -> file.equals(targetFile) || file.toString().endsWith(".json")).toList()) {
						JsonObject existing;
						try { existing = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject(); }
						catch (RuntimeException ignored) { continue; }
						if (documentFormat(existing) == GroupDocumentFormat.UNSUPPORTED
							&& (path.equals(targetFile) || (existing.has("id") && existing.get("id").isJsonPrimitive()
								&& group.id().equals(existing.get("id").getAsString())))) {
							Constants.LOG.warn("Cannot overwrite unsupported group document '{}'", path);
							return false;
						}
					}
				}
			}
			Files.createDirectories(dir);
			writeAtomically(targetFile, json);
			return true;
		} catch (IOException | IllegalArgumentException e) {
			Constants.LOG.error("Failed to save group: {}", group.id(), e);
			return false;
		}
	}

	/** Deletes a group's config file from disk. */
	public static void delete(String id) {
		deleteChecked(id);
	}

	static boolean deleteChecked(String id) {
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
				return true;
			}
			try (var stream = Files.list(dir)) {
				for (Path path : stream.filter(path -> path.toString().endsWith(".json"))
					.filter(path -> !path.getFileName().toString().equals(id + ".json"))
					.toList()) {
					if (!deleteIfGroupIdMatches(path, id, deletedCount)) return false;
				}
			}
			if (deletedCount.get() == 0) {
				Constants.LOG.warn("No group config files were deleted for id '{}' in {}", id, dir);
			} else {
				Constants.LOG.debug("Deleted {} group config file(s) for id '{}'", deletedCount.get(), id);
			}
			return true;
		} catch (IOException e) {
			Constants.LOG.error("Failed to delete group: {}", id, e);
			return false;
		}
	}

	public static GroupDefinition fromJson(String json) {
		String id = null;
		try {
			return fromJsonChecked(json);
		} catch (IllegalArgumentException e) {
			Constants.LOG.error("Group '{}': {}", id, e.getMessage());
			return null;
		} catch (Exception e) {
			Constants.LOG.error("Invalid group JSON: {}", json, e);
			return null;
		}
	}

	public static GroupDefinition fromJsonChecked(String json) {
		ParsedGroupJson parsed = parseGroupJson(json);
		return new GroupDefinition(parsed.id(), parsed.displayName(), parsed.enabled(), parsed.filter(),
			parsed.iconIds(), parsed.theme(), parsed.priority(), parsed.extra(), parsed.documentFormat(), parsed.rawDocument());
	}

	private static ParsedGroupJson parseGroupJson(String json) {
		JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
		GroupDocumentFormat documentFormat = documentFormat(obj);
		String id = obj.has("id") ? obj.get("id").getAsString() : null;
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("Missing required non-blank 'id' field.");
		}

		if (documentFormat == GroupDocumentFormat.UNSUPPORTED) {
			return new ParsedGroupJson(id, new GroupDisplayName.Localized(GroupTranslationHelper.keyForGroupId(id), id),
				false, new GroupFilter.Unsupported(obj, "schema_version"), List.of(), GroupTheme.EMPTY, 0,
				new JsonObject(), documentFormat, obj);
		}
		GroupDisplayName displayName = parseDisplayName(id, obj.get("name"));
		boolean enabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();

		if (!obj.has("filter")) {
			throw new IllegalArgumentException("Missing required 'filter' field.");
		}

		List<GroupIconDefinition> iconIds = new ArrayList<>();
		if (obj.has("icon")) {
			var iconElement = obj.get("icon");
			if (iconElement.isJsonArray()) {
				iconElement.getAsJsonArray().forEach(e -> iconIds.add(parseIcon(e)));
			} else {
				iconIds.add(parseIcon(iconElement));
			}
		}

		GroupFilter filter = parseFilter(obj.getAsJsonObject("filter"), documentFormat);
		GroupTheme theme = parseTheme(id, obj.get("theme"));
		int priority = parsePriority(id, obj.get("priority"));
		JsonObject extra = parseExtra(id, obj.get("extra"));
		return new ParsedGroupJson(id, displayName, enabled, filter, List.copyOf(iconIds), theme, priority, extra, documentFormat, null);
	}

	private static GroupIconDefinition parseIcon(JsonElement element) {
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			return GroupIconDefinition.item(element.getAsString());
		}
		if (element != null && element.isJsonObject()) {
			JsonObject icon = element.getAsJsonObject();
			if (!icon.has("type") || !icon.has("id")) {
				throw new IllegalArgumentException("Typed icon requires non-blank 'type' and 'id': " + icon);
			}
			return new GroupIconDefinition(icon.get("type").getAsString(), icon.get("id").getAsString());
		}
		throw new IllegalArgumentException("Icon must be a string or {type,id} object: " + element);
	}

	private static int parsePriority(String groupId, JsonElement priorityElement) {
		if (priorityElement == null || priorityElement.isJsonNull()) {
			return 0;
		}
		if (!priorityElement.isJsonPrimitive() || !priorityElement.getAsJsonPrimitive().isNumber()) {
			Constants.LOG.warn("Group '{}': Ignoring non-number 'priority' field.", groupId);
			return 0;
		}
		try {
			return priorityElement.getAsInt();
		} catch (Exception e) {
			Constants.LOG.warn("Group '{}': Ignoring unreadable 'priority' field.", groupId);
			return 0;
		}
	}

	private static JsonObject parseExtra(String groupId, JsonElement extraElement) {
		if (extraElement == null || extraElement.isJsonNull()) {
			return new JsonObject();
		}
		if (!extraElement.isJsonObject()) {
			Constants.LOG.warn("Group '{}': Ignoring non-object 'extra' field.", groupId);
			return new JsonObject();
		}
		return extraElement.getAsJsonObject().deepCopy();
	}

	private static GroupTheme parseTheme(String groupId, JsonElement themeElement) {
		if (themeElement == null || themeElement.isJsonNull()) {
			return GroupTheme.EMPTY;
		}
		if (!themeElement.isJsonObject()) {
			Constants.LOG.warn("Group '{}': Ignoring non-object 'theme' field.", groupId);
			return GroupTheme.EMPTY;
		}

		JsonObject themeObj = themeElement.getAsJsonObject();
		GroupTheme theme = new GroupTheme(
			parseThemeColor(groupId, themeObj, "name_color"),
			parseThemeColor(groupId, themeObj, "collapsed_header_background"),
			parseThemeColor(groupId, themeObj, "expanded_header_background"),
			parseThemeColor(groupId, themeObj, "expanded_group_background"),
			parseThemeColor(groupId, themeObj, "expanded_group_border")
		);
		return theme.isEmpty() ? GroupTheme.EMPTY : theme;
	}

	private static String parseThemeColor(String groupId, JsonObject themeObj, String field) {
		if (!themeObj.has(field) || themeObj.get(field).isJsonNull()) {
			return null;
		}

		JsonElement valueElement = themeObj.get(field);
		if (!valueElement.isJsonPrimitive()) {
			Constants.LOG.warn("Group '{}': Ignoring non-primitive theme color '{}'.", groupId, field);
			return null;
		}
		if (!valueElement.getAsJsonPrimitive().isString()) {
			Constants.LOG.warn("Group '{}': Ignoring non-string theme color '{}'.", groupId, field);
			return null;
		}

		String value;
		try {
			value = valueElement.getAsString().trim();
		} catch (Exception e) {
			Constants.LOG.warn("Group '{}': Ignoring unreadable theme color '{}'.", groupId, field);
			return null;
		}

		if (!ColorConfigParser.isValidArgb(value)) {
			Constants.LOG.warn("Group '{}': Ignoring invalid theme color '{}': {}", groupId, field, value);
			return null;
		}
		return value;
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
		if (group.documentFormat() == GroupDocumentFormat.UNSUPPORTED) return GSON.toJson(group.rawDocument());
		if (!GroupFormatPolicy.writable(group)) throw new IllegalArgumentException("This data requires a blank new group.");
		JsonObject obj = new JsonObject();
		if (group.documentFormat() == GroupDocumentFormat.V1) obj.addProperty("schema_version", 1);
		obj.addProperty("id", group.id());

		GroupDisplayName dn = group.displayName();
		if (dn instanceof GroupDisplayName.Localized loc) {
			JsonObject nameObj = new JsonObject();
			nameObj.addProperty("translate", loc.key());
			nameObj.addProperty("fallback", loc.fallback());
			obj.add("name", nameObj);
		}

		obj.addProperty("enabled", group.enabled());

		if (group.priority() != 0) {
			obj.addProperty("priority", group.priority());
		}

		if (!group.iconIds().isEmpty()) {
			if (group.iconIds().size() == 1) {
				obj.add("icon", serializeIcon(group.iconIds().get(0)));
			} else {
				JsonArray iconArr = new JsonArray();
				group.iconIds().forEach(icon -> iconArr.add(serializeIcon(icon)));
				obj.add("icon", iconArr);
			}
		}

		if (!group.theme().isEmpty()) {
			obj.add("theme", serializeTheme(group.theme()));
		}

		if (group.hasExtra()) {
			obj.add("extra", group.extra());
		}

		obj.add("filter", serializeFilter(group.filter(), group.documentFormat()));
		return GSON.toJson(obj);
	}

	private static JsonElement serializeIcon(GroupIconDefinition icon) {
		if (icon.isItem()) return GSON.toJsonTree(icon.valueId());
		JsonObject object = new JsonObject();
		object.addProperty("type", icon.ingredientType());
		object.addProperty("id", icon.valueId());
		return object;
	}

	private static JsonObject serializeTheme(GroupTheme theme) {
		JsonObject obj = new JsonObject();
		addThemeColor(obj, "name_color", theme.nameColor());
		addThemeColor(obj, "collapsed_header_background", theme.collapsedHeaderBackground());
		addThemeColor(obj, "expanded_header_background", theme.expandedHeaderBackground());
		addThemeColor(obj, "expanded_group_background", theme.expandedGroupBackground());
		addThemeColor(obj, "expanded_group_border", theme.expandedGroupBorder());
		return obj;
	}

	private static void addThemeColor(JsonObject obj, String key, String value) {
		if (value != null) {
			obj.addProperty(key, value);
		}
	}

	// package-private for testing (GroupConfigComponentPathTest)
	static GroupFilter parseFilter(JsonObject obj) { return parseFilter(obj, GroupDocumentFormat.LEGACY); }

	static GroupFilter parseFilter(JsonObject obj, GroupDocumentFormat format) {
		if (obj.has("nbt") && (!hasExactKeys(obj, "type", "nbt") || obj.has("nbt_path"))) {
			return new GroupFilter.Unsupported(obj, "nbt");
		}
		if (obj.has("nbt_path") && !hasExactKeys(obj, "type", "nbt_path", "value")) {
			return new GroupFilter.Unsupported(obj, "nbt_path");
		}
		FilterNodeKind kind = nodeKind(obj);
		if (kind == FilterNodeKind.UNKNOWN || !FilterNodeCapabilities.isAvailable(kind)) {
			return new GroupFilter.Unsupported(obj, recognizedKind(obj, kind));
		}
		if (obj.has("any")) {
			List<GroupFilter> children = new ArrayList<>();
			obj.getAsJsonArray("any").forEach(element -> children.add(parseFilter(element.getAsJsonObject(), format)));
			return new GroupFilter.Any(children);
		}
		if (obj.has("all")) {
			List<GroupFilter> children = new ArrayList<>();
			obj.getAsJsonArray("all").forEach(element -> children.add(parseFilter(element.getAsJsonObject(), format)));
			return new GroupFilter.All(children);
		}
		if (obj.has("not")) {
			return new GroupFilter.Not(parseFilter(obj.getAsJsonObject("not"), format));
		}
		if (obj.has("nbt") || obj.has("nbt_path") || obj.has("stack")) {
			String field = obj.has("nbt") ? "nbt" : obj.has("stack") ? "stack" : "value";
			Optional<ItemDataPayload> payload = ItemDataPayload.parse(obj.get(field));
			if (format != GroupDocumentFormat.V1 || !isItemNode(obj) || payload.isEmpty()
				|| !ItemDataPayload.NBT.equals(payload.get().dataFormat())
				|| !payload.get().data().isJsonPrimitive() || !payload.get().data().getAsJsonPrimitive().isString())
				return new GroupFilter.Unsupported(obj, recognizedKind(obj, kind));
			ItemDataPayload data = payload.get();
			if (obj.has("stack")) {
				if (!hasExactKeys(obj, "type", "stack") || Minecraft1201NbtAccess.canonicalRoot(data.encodedValue()).isEmpty())
					return new GroupFilter.Unsupported(obj, "exact_stack");
				return new GroupFilter.ExactStack(data);
			}
			if (obj.has("nbt")) return Minecraft1201NbtAccess.canonicalRoot(data.encodedValue()).isPresent()
				? new GroupFilter.Nbt(data) : new GroupFilter.Unsupported(obj, "nbt");
			JsonElement path = obj.get("nbt_path");
			if (path == null || !path.isJsonPrimitive() || !path.getAsJsonPrimitive().isString()
				|| !Minecraft1201NbtAccess.validPath(path.getAsString())
				|| Minecraft1201NbtAccess.canonicalValue(data.encodedValue()).isEmpty())
				return new GroupFilter.Unsupported(obj, "nbt_path");
			return new GroupFilter.NbtPath(path.getAsString(), data);
		}
		if (obj.has("component")) return new GroupFilter.Unsupported(obj, recognizedKind(obj, kind));
		if (obj.has("block_tag")) {
			return new GroupFilter.BlockTag(obj.get("block_tag").getAsString());
		}
		if (obj.has("item_path_starts_with")) {
			return new GroupFilter.ItemPathStartsWith(obj.get("item_path_starts_with").getAsString());
		}
		if (obj.has("item_path_contains")) {
			return new GroupFilter.ItemPathContains(obj.get("item_path_contains").getAsString());
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

	static GroupDocumentFormat documentFormat(JsonObject object) {
		if (!object.has("schema_version")) return GroupDocumentFormat.LEGACY;
		JsonElement version = object.get("schema_version");
		if (version.isJsonPrimitive() && version.getAsJsonPrimitive().isNumber()) {
			try { if (version.getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) == 0) return GroupDocumentFormat.V1; }
			catch (NumberFormatException ignored) {}
		}
		return GroupDocumentFormat.UNSUPPORTED;
	}

	private static boolean isItemNode(JsonObject obj) {
		return obj.has("type")
			&& obj.get("type").isJsonPrimitive()
			&& obj.get("type").getAsJsonPrimitive().isString()
			&& "item".equals(obj.get("type").getAsString());
	}

	private static boolean hasExactKeys(JsonObject obj, String... keys) {
		if (obj.size() != keys.length) return false;
		for (String key : keys) if (!obj.has(key)) return false;
		return true;
	}

	// package-private for testing (GroupConfigComponentPathTest)
	static JsonObject serializeFilter(GroupFilter filter) { return serializeFilter(filter, GroupFormatPolicy.inferredFormat(filter)); }

	static JsonObject serializeFilter(GroupFilter filter, GroupDocumentFormat format) {
		if (!GroupFormatPolicy.representable(format, filter)) throw new IllegalArgumentException("This data requires a blank new group.");
		if (filter instanceof GroupFilter.Unsupported unsupported) {
			return unsupported.rawJson();
		}
		JsonObject obj = new JsonObject();
		if (filter instanceof GroupFilter.Any) {
			GroupFilter.Any any = (GroupFilter.Any) filter;
				JsonArray arr = new JsonArray();
				any.children().forEach(child -> arr.add(serializeFilter(child, format)));
				obj.add("any", arr);
		} else if (filter instanceof GroupFilter.All) {
			GroupFilter.All all = (GroupFilter.All) filter;
				JsonArray arr = new JsonArray();
				all.children().forEach(child -> arr.add(serializeFilter(child, format)));
				obj.add("all", arr);
		} else if (filter instanceof GroupFilter.Not) {
			obj.add("not", serializeFilter(((GroupFilter.Not) filter).child(), format));
		} else if (filter instanceof GroupFilter.Id) {
			GroupFilter.Id id = (GroupFilter.Id) filter;
				obj.addProperty("type", id.ingredientType());
				obj.addProperty("id", id.id());
		} else if (filter instanceof GroupFilter.Tag) {
			GroupFilter.Tag tag = (GroupFilter.Tag) filter;
				obj.addProperty("type", tag.ingredientType());
				obj.addProperty("tag", tag.tag());
		} else if (filter instanceof GroupFilter.BlockTag) {
			obj.addProperty("block_tag", ((GroupFilter.BlockTag) filter).tag());
		} else if (filter instanceof GroupFilter.ItemPathStartsWith) {
			obj.addProperty("item_path_starts_with", ((GroupFilter.ItemPathStartsWith) filter).prefix());
		} else if (filter instanceof GroupFilter.ItemPathContains) {
			obj.addProperty("item_path_contains", ((GroupFilter.ItemPathContains) filter).needle());
		} else if (filter instanceof GroupFilter.ItemPathEndsWith) {
			obj.addProperty("item_path_ends_with", ((GroupFilter.ItemPathEndsWith) filter).suffix());
		} else if (filter instanceof GroupFilter.Namespace) {
			GroupFilter.Namespace namespace = (GroupFilter.Namespace) filter;
				obj.addProperty("type", namespace.ingredientType());
				obj.addProperty("namespace", namespace.namespace());
		} else if (filter instanceof GroupFilter.ExactStack) {
			GroupFilter.ExactStack stack = (GroupFilter.ExactStack) filter;
			obj.addProperty("type", "item");
			obj.add("stack", payloadOrNbt(stack.payload(), stack.encodedStack()).toJson());
		} else if (filter instanceof GroupFilter.Nbt) {
			obj.addProperty("type", "item");
			obj.add("nbt", payloadOrNbt(((GroupFilter.Nbt) filter).payload(), ((GroupFilter.Nbt) filter).expectedSnbt()).toJson());
		} else if (filter instanceof GroupFilter.NbtPath) {
			GroupFilter.NbtPath nbtPath = (GroupFilter.NbtPath) filter;
			obj.addProperty("type", "item");
			obj.addProperty("nbt_path", nbtPath.path());
			obj.add("value", payloadOrNbt(nbtPath.payload(), nbtPath.expectedSnbt()).toJson());
		} else if (filter instanceof GroupFilter.HasComponent) {
			GroupFilter.HasComponent hc = (GroupFilter.HasComponent) filter;
				obj.addProperty("type", "item");
				obj.addProperty("component", hc.componentTypeId());
				if (format == GroupDocumentFormat.V1) obj.add("value", hc.payload().toJson()); else obj.addProperty("value", hc.encodedValue());
		} else if (filter instanceof GroupFilter.ComponentPath) {
			GroupFilter.ComponentPath cp = (GroupFilter.ComponentPath) filter;
				obj.addProperty("type", "item");
				obj.addProperty("component", cp.componentTypeId());
				obj.addProperty("path", cp.path());
				if (format == GroupDocumentFormat.V1) obj.add("value", cp.payload().toJson()); else obj.addProperty("value", cp.expectedValue());
		} else {
			throw new AssertionError("Unsupported nodes return before serialization");
		}
		return obj;
	}

	private static ItemDataPayload payloadOrNbt(ItemDataPayload payload, String snbt) { return payload == null ? ItemDataPayload.nbt(snbt) : payload; }

	private static FilterNodeKind nodeKind(JsonObject obj) {
		if (obj.has("any")) return FilterNodeKind.ANY;
		if (obj.has("all")) return FilterNodeKind.ALL;
		if (obj.has("not")) return FilterNodeKind.NOT;
		if (obj.has("nbt")) return FilterNodeKind.NBT;
		if (obj.has("nbt_path")) return FilterNodeKind.NBT_PATH;
		if (obj.has("component")) return obj.has("path") ? FilterNodeKind.COMPONENT_PATH : FilterNodeKind.HAS_COMPONENT;
		if (obj.has("stack")) return FilterNodeKind.EXACT_STACK;
		if (obj.has("block_tag")) return FilterNodeKind.BLOCK_TAG;
		if (obj.has("item_path_starts_with")) return FilterNodeKind.ITEM_PATH_STARTS_WITH;
		if (obj.has("item_path_contains")) return FilterNodeKind.ITEM_PATH_CONTAINS;
		if (obj.has("item_path_ends_with")) return FilterNodeKind.ITEM_PATH_ENDS_WITH;
		if (obj.has("id")) return FilterNodeKind.ID;
		if (obj.has("tag")) return FilterNodeKind.TAG;
		if (obj.has("namespace")) return FilterNodeKind.NAMESPACE;
		return FilterNodeKind.UNKNOWN;
	}

	private static String recognizedKind(JsonObject obj, FilterNodeKind kind) {
		if (kind != FilterNodeKind.UNKNOWN) {
			return kind.name().toLowerCase(java.util.Locale.ROOT);
		}
		for (String key : obj.keySet()) {
			if (!"type".equals(key)) return key;
		}
		return "unknown";
	}

	private static boolean deleteIfGroupIdMatches(Path path, String id, AtomicInteger deletedCount) {
		boolean matches;
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			matches = obj.has("id") && id.equals(obj.get("id").getAsString());
		} catch (Exception e) {
			Constants.LOG.warn("Failed to inspect group file '{}' during delete cleanup: {}", path, e.getMessage());
			return true;
		}
		if (!matches) return true;
		try {
			if (Files.deleteIfExists(path)) {
				deletedCount.incrementAndGet();
				Constants.LOG.debug("Deleted matching group file for '{}': {}", id, path);
			}
			return true;
		} catch (IOException e) {
			Constants.LOG.error("Failed to delete matching group file for '{}': {}", id, path, e);
			return false;
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
		List<GroupIconDefinition> iconIds,
		GroupTheme theme,
		int priority,
		JsonObject extra, GroupDocumentFormat documentFormat, JsonObject rawDocument
	) {}

	public record UiState(boolean showBuiltin, boolean showKubeJs, boolean hideUsed,
	                      String managerSourceFilter, String managerSortMode, boolean managerShowEmpty) {
		public UiState(boolean showBuiltin, boolean showKubeJs, boolean hideUsed, String source, String sort) {
			this(showBuiltin, showKubeJs, hideUsed, source, sort, false);
		}
		public static final String SOURCE_FILTER_DEFAULT = "all";
		public static final String SORT_MODE_DEFAULT = "priority";
	}
}
