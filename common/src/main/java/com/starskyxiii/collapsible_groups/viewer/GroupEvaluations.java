package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupDocumentFormat;
import com.starskyxiii.collapsible_groups.group.GroupEvaluation;
import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.ingredient.TagQueryDiagnostics;
import com.starskyxiii.collapsible_groups.internal.query.IngredientCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GroupEvaluations {
    private GroupEvaluations() {}

    public static CompiledFilter.Evaluation evaluate(GroupDefinition group, IngredientView ingredient, Map<String, String> failures) {
        try {
            CompiledFilter.Evaluation result = group.query().evaluate(ingredient);
            if (result == CompiledFilter.Evaluation.UNAVAILABLE) failures.putIfAbsent(group.id(),
                "Could not fully evaluate " + ingredient.ingredientType() + " candidates");
            return result;
        } catch (RuntimeException failure) {
            failures.putIfAbsent(group.id(), failure.toString());
            return CompiledFilter.Evaluation.UNAVAILABLE;
        }
    }

    public static <E> Map<String, GroupEvaluation> summarize(long generation,
        IngredientCatalog<ViewerIngredient<E>, ViewerIngredientIdentity> universe, List<GroupDefinition> groups,
        Map<ViewerIngredientIdentity, List<String>> matches, Map<String, String> failures) {
        GroupEvaluationContext context = universe instanceof ViewerIngredientUniverse<?> viewer
            ? viewer.evaluationContext() : GroupEvaluationContext.simple(Set.of("item", "fluid"));
        Map<String, int[]> counts = new LinkedHashMap<>();
        groups.forEach(group -> counts.put(group.id(), new int[3]));
        for (ViewerIngredient<E> ingredient : universe.ordered()) {
            int kind = switch (ingredient.kind()) { case ITEM -> 0; case FLUID -> 1; case GENERIC -> 2; };
            for (String id : matches.getOrDefault(ingredient.identity(), List.of())) {
                int[] count = counts.get(id);
                if (count != null) count[kind]++;
            }
        }
        Map<String, GroupEvaluation> result = new LinkedHashMap<>();
        for (GroupDefinition group : groups) {
            List<GroupEvaluation.Issue> issues = new ArrayList<>();
            if (group.documentFormat() == GroupDocumentFormat.UNSUPPORTED) {
                issues.add(new GroupEvaluation.Issue(GroupEvaluation.Status.UNAVAILABLE, "Unsupported group document format"));
            }
            inspect(group.filter(), context, issues);
            if (failures.containsKey(group.id())) issues.add(new GroupEvaluation.Issue(GroupEvaluation.Status.UNAVAILABLE, failures.get(group.id())));
            List<GroupEvaluation.Issue> distinct = List.copyOf(new LinkedHashSet<>(issues));
            GroupEvaluation.Status status = distinct.stream().anyMatch(issue -> issue.status() == GroupEvaluation.Status.ERROR)
                ? GroupEvaluation.Status.ERROR : distinct.isEmpty() ? GroupEvaluation.Status.COMPLETE : GroupEvaluation.Status.UNAVAILABLE;
            int[] count = counts.get(group.id());
            result.put(group.id(), new GroupEvaluation(generation, status, count[0], count[1], count[2], distinct));
        }
        return Map.copyOf(result);
    }

    private static void inspect(GroupFilter filter, GroupEvaluationContext context, List<GroupEvaluation.Issue> issues) {
        switch (filter) {
            case GroupFilter.Any any -> any.children().forEach(child -> inspect(child, context, issues));
            case GroupFilter.All all -> all.children().forEach(child -> inspect(child, context, issues));
            case GroupFilter.Not not -> inspect(not.child(), context, issues);
            case GroupFilter.Unsupported unsupported -> {
                boolean malformed = Set.of("item_data", "has_component", "component_path", "exact_stack").contains(unsupported.recognizedKind());
                issues.add(new GroupEvaluation.Issue(malformed ? GroupEvaluation.Status.ERROR : GroupEvaluation.Status.UNAVAILABLE,
                    (malformed ? "Invalid rule: " : "Unsupported rule: ") + unsupported.recognizedKind()));
            }
            case GroupFilter.Id id -> requireType(id.ingredientType(), context, issues);
            case GroupFilter.Namespace namespace -> requireType(namespace.ingredientType(), context, issues);
            case GroupFilter.Tag tag -> {
                requireType(tag.ingredientType(), context, issues);
                var availability = context.tagAvailability().getOrDefault(GroupEvaluationContext.canonical(tag.ingredientType()), TagQueryDiagnostics.Availability.SUPPORTED);
                if (availability != TagQueryDiagnostics.Availability.SUPPORTED) issues.add(new GroupEvaluation.Issue(
                    GroupEvaluation.Status.UNAVAILABLE, "Tag queries for " + tag.ingredientType() + ": " + availability.name().toLowerCase(java.util.Locale.ROOT)));
            }
            case GroupFilter.ExactStack ignored -> inspectData(filter, context, issues);
            case GroupFilter.HasComponent ignored -> inspectData(filter, context, issues);
            case GroupFilter.ComponentPath ignored -> inspectData(filter, context, issues);
            default -> requireType("item", context, issues);
        }
    }

    private static void inspectData(GroupFilter filter, GroupEvaluationContext context, List<GroupEvaluation.Issue> issues) {
        requireType("item", context, issues);
        issues.addAll(context.itemDataInspector().apply(filter));
    }

    private static void requireType(String type, GroupEvaluationContext context, List<GroupEvaluation.Issue> issues) {
        if (!context.supports(type)) issues.add(new GroupEvaluation.Issue(GroupEvaluation.Status.UNAVAILABLE,
            "Ingredient type is unavailable: " + type));
    }
}
