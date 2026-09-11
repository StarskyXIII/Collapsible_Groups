package com.starskyxiii.collapsible_groups.group;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record GroupResourceData(
    List<GroupDefinition> groups,
    Set<String> builtinIds,
    Map<String, List<GroupOrigin>> origins,
    Map<String, List<GroupDefinition>> definitions,
    List<GroupLoadProblem> problems,
    boolean rejected,
    boolean stale
) {
    public GroupResourceData {
        groups = List.copyOf(groups);
        builtinIds = Set.copyOf(builtinIds);
        Map<String, List<GroupOrigin>> frozen = new LinkedHashMap<>();
        origins.forEach((id, values) -> frozen.put(id, List.copyOf(values)));
        origins = Collections.unmodifiableMap(frozen);
        Map<String, List<GroupDefinition>> frozenDefinitions = new LinkedHashMap<>();
        definitions.forEach((id, values) -> frozenDefinitions.put(id, List.copyOf(values)));
        definitions = Collections.unmodifiableMap(frozenDefinitions);
        problems = List.copyOf(problems);
    }

    public static GroupResourceData empty() {
        return new GroupResourceData(List.of(), Set.of(), Map.of(), Map.of(), List.of(), false, false);
    }

    public GroupOrigin origin(String id) {
        List<GroupOrigin> candidates = origins.get(id);
        return candidates == null || candidates.isEmpty() ? null : candidates.getFirst();
    }

    public boolean complete() {
        return !rejected && !stale && problems.stream().noneMatch(GroupLoadProblem::error);
    }

    public GroupResourceData retaining(GroupResourceData previous) {
        return new GroupResourceData(previous.groups(), previous.builtinIds(), previous.origins(), previous.definitions(),
            problems, true, true);
    }

    public GroupResourceData withDefinition(GroupDefinition group, GroupOrigin origin) {
        Map<String, List<GroupOrigin>> nextOrigins = new LinkedHashMap<>(origins);
        Map<String, List<GroupDefinition>> nextDefinitions = new LinkedHashMap<>(definitions);
        var history = new java.util.ArrayList<>(origins.getOrDefault(group.id(), List.of()));
        var versions = new java.util.ArrayList<>(definitions.getOrDefault(group.id(), List.of()));
        if (!history.isEmpty() && history.getFirst().equals(origin)) {
            versions.set(0, group);
        } else {
            history.addFirst(origin);
            versions.addFirst(group);
        }
        nextOrigins.put(group.id(), history);
        nextDefinitions.put(group.id(), versions);
        Map<String, GroupDefinition> current = new LinkedHashMap<>();
        groups.forEach(value -> current.put(value.id(), value));
        current.put(group.id(), group);
        return new GroupResourceData(List.copyOf(current.values()), builtinIds, nextOrigins, nextDefinitions,
            problems, rejected, stale);
    }

    public GroupResourceData withoutOwnedDefinition(String id) {
        GroupOrigin origin = origin(id);
        if (origin == null || !origin.locallyOwned()) return this;
        Map<String, List<GroupOrigin>> nextOrigins = new LinkedHashMap<>(origins);
        Map<String, List<GroupDefinition>> nextDefinitions = new LinkedHashMap<>(definitions);
        var history = new java.util.ArrayList<>(origins.get(id));
        var versions = new java.util.ArrayList<>(definitions.get(id));
        history.removeFirst();
        versions.removeFirst();
        nextOrigins.put(id, history);
        nextDefinitions.put(id, versions);
        List<GroupDefinition> nextGroups = groups.stream().filter(group -> !group.id().equals(id) || !versions.isEmpty())
            .map(group -> group.id().equals(id) ? versions.getFirst() : group).toList();
        return new GroupResourceData(nextGroups, builtinIds, nextOrigins, nextDefinitions,
            problems.stream().filter(problem -> !origin.equals(problem.origin())).toList(), rejected, stale);
    }
}
