package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupCatalog;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupEvaluation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record GroupIndexUpdate(List<GroupDefinition> groups, Set<String> reusedIds) {
    public GroupIndexUpdate {
        groups = List.copyOf(groups);
        reusedIds = Set.copyOf(reusedIds);
    }

    public static GroupIndexUpdate between(GroupCandidateIndex previous, List<GroupDefinition> groups) {
        Set<String> reused = new LinkedHashSet<>();
        if (previous != null) {
            for (GroupDefinition group : groups) {
                GroupDefinition old = previous.groupSnapshot().get(group.id());
                GroupEvaluation evaluation = previous.evaluations().get(group.id());
                if (old != null && evaluation != null && evaluation.status() != GroupEvaluation.Status.PENDING
                    && old.documentFormat() == group.documentFormat() && old.filter().equals(group.filter())) {
                    reused.add(group.id());
                }
            }
        }
        return new GroupIndexUpdate(GroupCatalog.orderByPriority(groups), reused);
    }

    public List<GroupDefinition> changedGroups() {
        return groups.stream().filter(group -> !reusedIds.contains(group.id())).toList();
    }

    public GroupCandidateIndex mergeCandidates(GroupCandidateIndex previous, GroupCandidateIndex changed) {
        Map<String, GroupDefinition> snapshot = new LinkedHashMap<>();
        Map<String, Integer> ranks = new LinkedHashMap<>();
        Map<String, GroupEvaluation> evaluations = new LinkedHashMap<>();
        for (GroupDefinition group : groups) {
            snapshot.put(group.id(), group);
            ranks.put(group.id(), ranks.size());
            GroupEvaluation evaluation = (reusedIds.contains(group.id()) ? previous : changed)
                .evaluations().get(group.id());
            evaluations.put(group.id(), new GroupEvaluation(changed.generation(), evaluation.status(),
                evaluation.itemCount(), evaluation.fluidCount(), evaluation.genericCount(), evaluation.issues()));
        }
        Map<ViewerIngredientIdentity, List<String>> candidates = new LinkedHashMap<>();
        if (previous != null) {
            previous.candidates().forEach((identity, ids) -> {
                List<String> retained = ids.stream().filter(reusedIds::contains).toList();
                if (!retained.isEmpty()) candidates.put(identity, new ArrayList<>(retained));
            });
        }
        changed.candidates().forEach((identity, ids) ->
            candidates.computeIfAbsent(identity, ignored -> new ArrayList<>()).addAll(ids));
        long edges = 0;
        int max = 0;
        for (var entry : candidates.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(ranks::get));
            entry.setValue(List.copyOf(entry.getValue()));
            edges += entry.getValue().size();
            max = Math.max(max, entry.getValue().size());
        }
        return new GroupCandidateIndex(candidates, snapshot, edges, changed.indexedIngredients(), max,
            evaluations, changed.generation());
    }

    public <T> Map<String, List<T>> mergeMatches(Map<String, List<T>> previous, Map<String, List<T>> changed) {
        Map<String, List<T>> result = new LinkedHashMap<>();
        for (GroupDefinition group : groups) {
            Map<String, List<T>> source = reusedIds.contains(group.id()) ? previous : changed;
            result.put(group.id(), source.getOrDefault(group.id(), List.of()));
        }
        return result;
    }
}
