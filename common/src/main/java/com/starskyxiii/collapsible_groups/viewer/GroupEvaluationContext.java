package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupEvaluation;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.ingredient.TagQueryDiagnostics;
import com.starskyxiii.collapsible_groups.internal.version.data.MinecraftGroupRuleDiagnostics;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record GroupEvaluationContext(Set<String> supportedTypes,
    Map<String, TagQueryDiagnostics.Availability> tagAvailability,
    Function<GroupFilter, List<GroupEvaluation.Issue>> itemDataInspector) {
    public GroupEvaluationContext {
        supportedTypes = supportedTypes.stream().map(GroupEvaluationContext::canonical).collect(Collectors.toUnmodifiableSet());
        tagAvailability = Map.copyOf(tagAvailability);
    }

    public static GroupEvaluationContext simple(Set<String> types) {
        return new GroupEvaluationContext(types, Map.of(), rule -> List.of(new GroupEvaluation.Issue(
            GroupEvaluation.Status.UNAVAILABLE, "Item data inspection is unavailable")));
    }

    public static GroupEvaluationContext minecraft(Set<String> types, Map<String, TagQueryDiagnostics.Availability> tags) {
        return new GroupEvaluationContext(types, tags, new MinecraftGroupRuleDiagnostics());
    }

    public boolean supports(String type) { return supportedTypes.contains(canonical(type)); }

    public static String canonical(String type) {
        String canonical = IngredientTypeIds.getCanonicalId(type);
        return canonical == null ? type : canonical;
    }
}
