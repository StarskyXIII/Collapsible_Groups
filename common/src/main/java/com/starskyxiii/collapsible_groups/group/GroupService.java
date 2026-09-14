package com.starskyxiii.collapsible_groups.group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

final class GroupService {
	record SourceKey(GroupSource category, String producerId) {
		SourceKey {
			if (category == null) throw new NullPointerException("category");
			if (producerId == null || producerId.isBlank()) {
				throw new IllegalArgumentException("producerId must not be blank");
			}
		}
	}

	private record Entry(GroupDefinition group, SourceKey source) {}
	private record Snapshot(
		Map<SourceKey, List<GroupDefinition>> sources,
		Set<SourceKey> appliedSources,
		GroupCatalog.Snapshot managed,
		GroupCatalog.Snapshot all,
		GroupResourceData resources,
		boolean builtinsEnabled,
		Map<String, GroupSource> winningSources
	) {
		private static Snapshot empty() {
			GroupCatalog.Snapshot empty = GroupCatalog.Snapshot.from(List.of());
			return new Snapshot(Map.of(), Set.of(), empty, empty, GroupResourceData.empty(), true, Map.of());
		}
	}

	private volatile Snapshot snapshot = Snapshot.empty();

	record ReadSnapshot(List<GroupDefinition> groups, GroupResourceData resources, boolean builtinsEnabled, Map<String, GroupSource> winningSources) {}

	ReadSnapshot readSnapshot() {
		Snapshot current = snapshot;
		return new ReadSnapshot(current.all().priorityOrder(), current.resources(), current.builtinsEnabled(), current.winningSources());
	}

	GroupResourceData resources() { return snapshot.resources(); }
	boolean builtinsEnabled() { return snapshot.builtinsEnabled(); }

	synchronized boolean replaceManaged(GroupResourceData incoming, Map<String, Boolean> enabledOverrides, boolean enabled) {
		if (incoming.rejected()) {
			snapshot = new Snapshot(snapshot.sources(), snapshot.appliedSources(), snapshot.managed(), snapshot.all(),
				incoming.retaining(snapshot.resources()), enabled, snapshot.winningSources());
			return false;
		}
		List<GroupDefinition> effective = incoming.groups().stream().map(group -> {
			GroupOrigin origin = incoming.origin(group.id());
			Boolean preference = incoming.builtinIds().contains(group.id()) || origin.source().usesEnabledOverride()
				? enabledOverrides.get(group.id()) : null;
			return preference == null || preference == group.enabled() ? group : group.withEnabled(preference);
		}).toList();
		LinkedHashMap<SourceKey, List<GroupDefinition>> sources = mutableSources(snapshot.sources());
		sources.keySet().removeIf(key -> key.category() != GroupSource.KUBEJS);
		sources.put(new SourceKey(GroupSource.USER, "resources"), validate(effective));
		publish(sources, snapshot.appliedSources(), incoming, enabled);
		return true;
	}

	synchronized void setBuiltinsEnabled(boolean enabled) {
		snapshot = new Snapshot(snapshot.sources(), snapshot.appliedSources(), snapshot.managed(), snapshot.all(),
			snapshot.resources(), enabled, snapshot.winningSources());
	}

	List<GroupDefinition> managedRegistrationOrder() {
		return snapshot.managed().registrationOrder();
	}

	List<GroupDefinition> managedPriorityOrder() {
		return snapshot.managed().priorityOrder();
	}

	List<GroupDefinition> allRegistrationOrder() {
		return snapshot.all().registrationOrder();
	}

	List<GroupDefinition> allPriorityOrder() {
		return snapshot.all().priorityOrder();
	}

	Optional<GroupDefinition> findById(String id) {
		if (id == null || id.isBlank()) return Optional.empty();
		return Optional.ofNullable(snapshot.all().byId().get(id));
	}

	List<GroupDefinition> sourceGroups(SourceKey key) {
		return snapshot.sources().getOrDefault(key, List.of());
	}

	List<GroupDefinition> categoryGroups(GroupSource category) {
		List<GroupDefinition> result = new ArrayList<>();
		snapshot.sources().forEach((key, groups) -> {
			if (key.category() == category) result.addAll(groups);
		});
		return List.copyOf(result);
	}

	Set<SourceKey> categorySources(GroupSource category) {
		return snapshot.sources().keySet().stream()
			.filter(key -> key.category() == category)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	synchronized void replaceSource(SourceKey key, List<GroupDefinition> incoming) {
		List<GroupDefinition> validated = validate(incoming);
		LinkedHashMap<SourceKey, List<GroupDefinition>> sources = mutableSources(snapshot.sources());
		sources.put(key, validated);
		publish(sources, snapshot.appliedSources());
	}

	synchronized void replaceSources(Map<SourceKey, List<GroupDefinition>> replacements,
		Set<SourceKey> removals) {
		if (replacements == null) throw new NullPointerException("replacements");
		if (removals == null) throw new NullPointerException("removals");
		LinkedHashMap<SourceKey, List<GroupDefinition>> validated = new LinkedHashMap<>();
		for (Map.Entry<SourceKey, List<GroupDefinition>> entry : replacements.entrySet()) {
			if (entry.getKey() == null) throw new NullPointerException("source key");
			validated.put(entry.getKey(), validate(entry.getValue()));
		}
		LinkedHashMap<SourceKey, List<GroupDefinition>> sources = mutableSources(snapshot.sources());
		removals.forEach(sources::remove);
		validated.forEach(sources::put);
		Set<SourceKey> applied = new LinkedHashSet<>(snapshot.appliedSources());
		applied.removeAll(removals);
		publish(sources, applied);
	}

	synchronized void removeSource(SourceKey key) {
		if (!snapshot.sources().containsKey(key) && !snapshot.appliedSources().contains(key)) return;
		LinkedHashMap<SourceKey, List<GroupDefinition>> sources = mutableSources(snapshot.sources());
		sources.remove(key);
		Set<SourceKey> applied = new LinkedHashSet<>(snapshot.appliedSources());
		applied.remove(key);
		publish(sources, applied);
	}

	synchronized void removeCategory(GroupSource category) {
		LinkedHashMap<SourceKey, List<GroupDefinition>> sources = mutableSources(snapshot.sources());
		boolean changed = sources.keySet().removeIf(key -> key.category() == category);
		Set<SourceKey> applied = new LinkedHashSet<>(snapshot.appliedSources());
		changed |= applied.removeIf(key -> key.category() == category);
		if (changed) publish(sources, applied);
	}

	synchronized boolean update(SourceKey key, String id, UnaryOperator<GroupDefinition> updater) {
		if (id == null || id.isBlank()) return false;
		if (updater == null) throw new NullPointerException("updater");
		List<GroupDefinition> current = snapshot.sources().get(key);
		if (current == null) return false;
		List<GroupDefinition> updated = new ArrayList<>(current.size());
		boolean found = false;
		for (GroupDefinition group : current) {
			if (id.equals(group.id())) {
				GroupDefinition replacement = updater.apply(group);
				if (replacement == null || !id.equals(replacement.id())) {
					throw new IllegalArgumentException("source update must preserve a non-null group id");
				}
				updated.add(replacement);
				found = true;
			} else {
				updated.add(group);
			}
		}
		if (!found) return false;
		replaceSource(key, updated);
		return true;
	}

	synchronized boolean updateVisible(String id, UnaryOperator<GroupDefinition> updater) {
		SourceKey source = visibleSource(id);
		return source != null && update(source, id, updater);
	}

	synchronized void saveOrReplace(SourceKey key, GroupDefinition group) {
		List<GroupDefinition> current = snapshot.sources().getOrDefault(key, List.of());
		List<GroupDefinition> updated = new ArrayList<>(current.size() + 1);
		boolean replaced = false;
		for (GroupDefinition existing : current) {
			if (existing.id().equals(group.id())) {
				updated.add(group);
				replaced = true;
			} else {
				updated.add(existing);
			}
		}
		if (!replaced) updated.add(group);
		replaceSource(key, updated);
	}

	void validateGroup(GroupDefinition group) {
		validate(List.of(group));
	}

	synchronized boolean removeGroup(SourceKey key, String id) {
		List<GroupDefinition> current = snapshot.sources().get(key);
		if (current == null || current.stream().noneMatch(group -> id.equals(group.id()))) return false;
		replaceSource(key, current.stream().filter(group -> !id.equals(group.id())).toList());
		return true;
	}

	synchronized void reset(Map<SourceKey, List<GroupDefinition>> sources) {
		LinkedHashMap<SourceKey, List<GroupDefinition>> validated = new LinkedHashMap<>();
		for (Map.Entry<SourceKey, List<GroupDefinition>> entry : sources.entrySet()) {
			validated.put(entry.getKey(), validate(entry.getValue()));
		}
		publish(validated, Set.of());
	}

	synchronized void markApplied(SourceKey key) {
		if (snapshot.appliedSources().contains(key)) return;
		Set<SourceKey> applied = new LinkedHashSet<>(snapshot.appliedSources());
		applied.add(key);
		snapshot = new Snapshot(snapshot.sources(), immutableSet(applied), snapshot.managed(), snapshot.all(),
			snapshot.resources(), snapshot.builtinsEnabled(), snapshot.winningSources());
	}

	boolean isApplied(SourceKey key) {
		return snapshot.appliedSources().contains(key);
	}

	GroupSource visibleCategory(String id) {
		SourceKey source = visibleSource(id);
		return source == null ? null : source.category();
	}

	private SourceKey visibleSource(String id) {
		return visibleSource(snapshot, id);
	}

	private static SourceKey visibleSource(Snapshot current, String id) {
		SourceKey winner = null;
		for (Map.Entry<SourceKey, List<GroupDefinition>> source : current.sources().entrySet()) {
			boolean contains = source.getValue().stream().anyMatch(group -> id.equals(group.id()));
			if (!contains) continue;
			if (winner == null || authority(source.getKey().category()) > authority(winner.category())) {
				winner = source.getKey();
			}
		}
		return winner;
	}

	private void publish(LinkedHashMap<SourceKey, List<GroupDefinition>> sources,
		Set<SourceKey> appliedSources) {
		publish(sources, appliedSources, snapshot.resources(), snapshot.builtinsEnabled());
	}

	private void publish(LinkedHashMap<SourceKey, List<GroupDefinition>> sources, Set<SourceKey> appliedSources,
		GroupResourceData resources, boolean builtinsEnabled) {
		Map<SourceKey, List<GroupDefinition>> frozenSources = immutableMap(sources);
		List<GroupDefinition> managed = merge(frozenSources, EnumSet.complementOf(EnumSet.of(GroupSource.KUBEJS)));
		List<GroupDefinition> all = merge(frozenSources, EnumSet.allOf(GroupSource.class));
		Map<String, GroupSource> winners = new LinkedHashMap<>();
		frozenSources.forEach((source, groups) -> groups.forEach(group -> winners.merge(group.id(), source.category(),
			(left, right) -> authority(right) > authority(left) ? right : left)));
		resources.origins().forEach((id, origins) -> {
			if (winners.containsKey(id) && !origins.isEmpty()) winners.put(id, origins.get(0).source());
		});
		snapshot = new Snapshot(frozenSources, immutableSet(appliedSources),
			GroupCatalog.Snapshot.from(managed), GroupCatalog.Snapshot.from(all), resources, builtinsEnabled, Map.copyOf(winners));
	}

	private static List<GroupDefinition> validate(List<GroupDefinition> incoming) {
		if (incoming == null) throw new NullPointerException("incoming");
		List<GroupDefinition> copy = new ArrayList<>(incoming.size());
		Set<String> ids = new LinkedHashSet<>();
		for (GroupDefinition group : incoming) {
			if (group == null) throw new IllegalArgumentException("group source contains null");
			if (group.id().isBlank()) throw new IllegalArgumentException("group id must not be blank");
			if (!ids.add(group.id())) throw new IllegalArgumentException("duplicate group id: " + group.id());
			copy.add(group);
		}
		return List.copyOf(copy);
	}

	private static List<GroupDefinition> merge(Map<SourceKey, List<GroupDefinition>> sources,
		Set<GroupSource> included) {
		List<Entry> entries = new ArrayList<>();
		Map<String, Integer> positions = new HashMap<>();
		for (GroupSource category : List.of(GroupSource.BUILTIN, GroupSource.RESOURCE_PACK, GroupSource.USER, GroupSource.KUBEJS)) {
			if (!included.contains(category)) continue;
			for (Map.Entry<SourceKey, List<GroupDefinition>> source : sources.entrySet()) {
				if (source.getKey().category() != category) continue;
				for (GroupDefinition group : source.getValue()) {
					Integer position = positions.get(group.id());
					if (position == null) {
						positions.put(group.id(), entries.size());
						entries.add(new Entry(group, source.getKey()));
					} else if (authority(source.getKey().category())
						> authority(entries.get(position).source().category())) {
						entries.set(position, new Entry(group, source.getKey()));
					}
				}
			}
		}
		return entries.stream().map(Entry::group).toList();
	}

	private static int authority(GroupSource source) {
		return switch (source) {
			case USER -> 4;
			case RESOURCE_PACK -> 3;
			case BUILTIN -> 2;
			case KUBEJS -> 1;
		};
	}

	private static LinkedHashMap<SourceKey, List<GroupDefinition>> mutableSources(
		Map<SourceKey, List<GroupDefinition>> source) {
		return new LinkedHashMap<>(source);
	}

	private static <K, V> Map<K, V> immutableMap(LinkedHashMap<K, V> source) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}

	private static <T> Set<T> immutableSet(Set<T> source) {
		return Collections.unmodifiableSet(new LinkedHashSet<>(source));
	}
}
