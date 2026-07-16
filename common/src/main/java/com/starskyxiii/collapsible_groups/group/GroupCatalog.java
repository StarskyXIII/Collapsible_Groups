package com.starskyxiii.collapsible_groups.group;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Copy-on-write catalog of group definitions and domain-level group operations. */
public final class GroupCatalog {
	private volatile List<GroupDefinition> registrationOrder = List.of();
	private volatile List<GroupDefinition> priorityOrder = List.of();
	private volatile Map<String, GroupDefinition> byId = Map.of();

	public List<GroupDefinition> registrationOrder() {
		return registrationOrder;
	}

	public List<GroupDefinition> priorityOrder() {
		return priorityOrder;
	}

	public Map<String, GroupDefinition> byId() {
		return byId;
	}

	public Optional<GroupDefinition> findById(String id) {
		if (id == null || id.isBlank()) return Optional.empty();
		return Optional.ofNullable(byId.get(id));
	}

	public synchronized void publish(List<GroupDefinition> definitions) {
		registrationOrder = definitions == null ? List.of() : List.copyOf(definitions);
		priorityOrder = orderByPriority(registrationOrder);
		byId = buildById(registrationOrder);
	}

	public synchronized void replace(UnaryOperator<List<GroupDefinition>> updater) {
		publish(updater.apply(registrationOrder));
	}

	public void saveOrReplace(GroupDefinition group) {
		replace(snapshot -> {
			List<GroupDefinition> copy = new ArrayList<>(snapshot.size() + 1);
			boolean replaced = false;
			for (GroupDefinition existing : snapshot) {
				if (existing.id().equals(group.id())) {
					copy.add(group);
					replaced = true;
				} else {
					copy.add(existing);
				}
			}
			if (!replaced) copy.add(group);
			return List.copyOf(copy);
		});
	}

	public void delete(String id) {
		replace(snapshot -> snapshot.stream().filter(group -> !group.id().equals(id)).toList());
	}

	public boolean setEnabled(String id, boolean enabled) {
		GroupDefinition existing = byId.get(id);
		if (existing == null) return false;
		if (existing.enabled() == enabled) return true;
		replace(snapshot -> snapshot.stream()
			.map(group -> id.equals(group.id()) ? group.withEnabled(enabled) : group)
			.toList());
		return true;
	}

	public String generateUniqueId(String base) {
		return generateUniqueId(base, registrationOrder.stream().map(GroupDefinition::id).toList());
	}

	public static String generateUniqueId(String base, List<String> existingIds) {
		String id = sanitizeGeneratedIdBase(base);
		if (id.isEmpty()) id = fallbackGeneratedIdBase(base);
		List<String> existing = existingIds == null ? List.of() : List.copyOf(existingIds);
		if (!existing.contains(id)) return id;
		for (int i = 2; i < 1000; i++) {
			String candidate = id + "_" + i;
			if (!existing.contains(candidate)) return candidate;
		}
		return id + "_" + System.currentTimeMillis();
	}

	public static Optional<GroupDefinition> createCustomCopy(
		GroupDefinition source,
		String copiedDisplayName,
		List<String> existingGroupIds
	) {
		if (source == null || GroupSource.fromGroupId(source.id()) == GroupSource.USER) {
			return Optional.empty();
		}
		String fallbackName = normalizedCopyName(copiedDisplayName, source);
		String id = generateUniqueCustomCopyId(copyBaseId(source.id(), fallbackName), existingGroupIds);
		return Optional.of(new GroupDefinition(
			id,
			fallbackName,
			source.enabled(),
			source.filter(),
			source.iconIds(),
			source.theme(),
			source.priority(),
			source.extra()
		));
	}

	public static List<GroupDefinition> orderByPriority(List<GroupDefinition> source) {
		if (source == null || source.isEmpty()) return List.of();
		return source.stream()
			.sorted(Comparator.comparingInt(GroupDefinition::priority).reversed())
			.toList();
	}

	public static List<GroupDefinition> applyEnabledOverrides(
		List<GroupDefinition> source,
		Map<String, Boolean> overrides
	) {
		if (source == null || source.isEmpty() || overrides == null || overrides.isEmpty()) {
			return source == null ? List.of() : List.copyOf(source);
		}
		List<GroupDefinition> updated = new ArrayList<>(source.size());
		for (GroupDefinition group : source) {
			Boolean override = GroupSource.fromGroupId(group.id()).usesEnabledOverride()
				? overrides.get(group.id())
				: null;
			updated.add(override != null && group.enabled() != override ? group.withEnabled(override) : group);
		}
		return List.copyOf(updated);
	}

	public static String sanitizeGeneratedIdBase(String base) {
		if (base == null || base.isBlank()) return "";
		return Normalizer.normalize(base, Normalizer.Form.NFKC)
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9_]", "_")
			.replaceAll("_+", "_")
			.replaceAll("^_+|_+$", "");
	}

	private static String normalizedCopyName(String copiedDisplayName, GroupDefinition source) {
		if (copiedDisplayName != null && !copiedDisplayName.isBlank()) return copiedDisplayName.trim();
		String fallback = source.displayName().fallback();
		return fallback == null || fallback.isBlank() ? source.id() : fallback;
	}

	private static String copyBaseId(String sourceId, String copiedDisplayName) {
		String stripped = stripReservedSourcePrefix(sourceId);
		return stripped.isBlank() ? copiedDisplayName + "_copy" : stripped + "_copy";
	}

	private static String stripReservedSourcePrefix(String id) {
		if (id == null) return "";
		if (id.startsWith("__default_")) return id.substring("__default_".length());
		if (id.startsWith("__kjs_")) return id.substring("__kjs_".length());
		return id;
	}

	private static String generateUniqueCustomCopyId(String base, List<String> existingGroupIds) {
		String id = sanitizeGeneratedIdBase(base);
		if (id.isEmpty()) id = fallbackGeneratedIdBase(base);
		if (id.startsWith("__default_") || id.startsWith("__kjs_")) {
			id = sanitizeGeneratedIdBase(stripReservedSourcePrefix(id));
			if (id.isEmpty()) id = "group";
		}
		return generateUniqueId(id, existingGroupIds);
	}

	private static String fallbackGeneratedIdBase(String base) {
		if (base == null || base.isBlank()) return "group";
		String normalized = Normalizer.normalize(base, Normalizer.Form.NFKC);
		return "group_" + Integer.toUnsignedString(normalized.hashCode(), 36);
	}

	private static Map<String, GroupDefinition> buildById(List<GroupDefinition> source) {
		if (source.isEmpty()) return Map.of();
		Map<String, GroupDefinition> result = new LinkedHashMap<>(source.size());
		for (GroupDefinition group : source) result.put(group.id(), group);
		return Map.copyOf(result);
	}
}
