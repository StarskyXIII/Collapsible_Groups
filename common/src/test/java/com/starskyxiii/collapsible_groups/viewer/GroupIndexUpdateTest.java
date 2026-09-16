package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupEvaluation;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GroupIndexUpdateTest {
    @Test void projectableSubsetUsesCurrentDefinitionsAndPriorityWithoutMatchingChangedGroups() {
        Map<String, Integer> calls = new HashMap<>();
        var universe = new ViewerIngredientUniverse<>(List.of(ingredient("one", calls), ingredient("two", calls)));
        var first = group("first", "both");
        var second = group("second", "both");
        var edited = group("edited", "one");
        var previous = GroupProjectionEngine.buildCandidateIndex(universe, List.of(first, second, edited));
        calls.clear();
        var promoted = second.withPriority(10).withIconIds(List.of(com.starskyxiii.collapsible_groups.group.GroupIconDefinition.item("test:icon")));
        var subset = GroupIndexUpdate.between(previous, List.of(first, promoted, group("edited", "two"), group("new", "one")))
            .projectableCandidates(previous);
        assertEquals(Set.of("first", "second"), subset.groupSnapshot().keySet());
        assertEquals(List.of("second", "first"), subset.candidates().get(new ViewerIngredientIdentity("item", "one")));
        assertSame(promoted, subset.groupSnapshot().get("second"));
        assertTrue(calls.isEmpty());
        var projection = GroupProjectionEngine.project(universe, new ViewerSearchSnapshot<>("", universe.ordered(), false, 0),
            List.copyOf(subset.groupSnapshot().values()), id -> false, subset);
        assertEquals("second", ((ViewerProjection.GroupHeader<String>) projection.entries().get(0)).group().id());
    }

    @Test void addEditRemoveAndMetadataChangesMatchFullBuildWithoutEvaluatingRetainedRules() {
        Map<String, Integer> calls = new HashMap<>();
        var universe = new ViewerIngredientUniverse<>(List.of(ingredient("one", calls), ingredient("two", calls)));
        var stable = group("stable", "both");
        var edited = group("edited", "one");
        var removed = group("removed", "two");
        var previous = GroupProjectionEngine.buildCandidateIndex(universe, List.of(stable, edited, removed));
        calls.clear();
        var newest = List.of(stable.withEnabled(false).withPriority(10), group("edited", "two"), group("added", "one"));
        var update = GroupIndexUpdate.between(previous, newest);
        assertEquals(Set.of("stable"), update.reusedIds());
        var merged = update.mergeCandidates(previous, GroupProjectionEngine.buildCandidateIndex(universe, update.changedGroups()));
        assertEquals(Map.of("one", 2, "two", 2), calls);
        var full = GroupProjectionEngine.buildCandidateIndex(universe, newest);
        assertEquivalent(full, merged);
        assertEquals(GroupProjectionEngine.resolveOwnership(full, newest), GroupProjectionEngine.resolveOwnership(merged, newest));
        assertFalse(merged.groupSnapshot().containsKey("removed"));
        assertEquals(List.of("stable", "added"), merged.candidates().get(new ViewerIngredientIdentity("item", "one")));
        List<String> retained = List.of("old-preview");
        var previews = update.mergeMatches(Map.of("stable", retained, "removed", List.of("gone")),
            Map.of("edited", List.of("changed"), "added", List.of("new")));
        assertSame(retained, previews.get("stable"));
        assertEquals(Set.of("stable", "edited", "added"), previews.keySet());

        calls.clear();
        var reordered = List.of(newest.get(2).withPriority(20), newest.get(1), stable.withEnabled(true));
        var metadata = GroupIndexUpdate.between(merged, reordered);
        assertTrue(metadata.changedGroups().isEmpty());
        var result = metadata.mergeCandidates(merged, GroupProjectionEngine.buildCandidateIndex(universe, metadata.changedGroups()));
        assertTrue(calls.isEmpty());
        assertEquivalent(GroupProjectionEngine.buildCandidateIndex(universe, reordered), result);
    }

    @Test void retainsUnavailableAndErrorDiagnosticsWithoutRepeatingDataInspection() {
        AtomicInteger inspections = new AtomicInteger();
        var context = new GroupEvaluationContext(Set.of("item"), Map.of(), filter -> {
            inspections.incrementAndGet();
            return List.of(new GroupEvaluation.Issue(GroupEvaluation.Status.ERROR, "broken payload"));
        });
        var universe = new ViewerIngredientUniverse<String>(List.of(), null, context);
        var groups = List.of(new GroupDefinition("error", "error", true, new GroupFilter.HasComponent("test:key", "value")),
            new GroupDefinition("unavailable", "unavailable", true, new GroupFilter.Id("missing:type", "test:x")));
        var previous = GroupProjectionEngine.buildCandidateIndex(universe, groups);
        assertEquals(1, inspections.get());
        var update = GroupIndexUpdate.between(previous, groups);
        var result = update.mergeCandidates(previous, GroupProjectionEngine.buildCandidateIndex(universe, update.changedGroups()));
        assertEquals(1, inspections.get());
        assertEquals(GroupEvaluation.Status.ERROR, result.evaluations().get("error").status());
        assertEquals(GroupEvaluation.Status.UNAVAILABLE, result.evaluations().get("unavailable").status());
        assertEquals(previous.evaluations().get("error").issues(), result.evaluations().get("error").issues());
        assertNotEquals(previous.generation(), result.generation());
        result.evaluations().values().forEach(e -> assertEquals(result.generation(), e.generation()));
    }

    @Test void missingEvaluationsAndNewSourcesRequireEvaluation() {
        var group = group("one", "one");
        var missing = new GroupCandidateIndex(Map.of(), Map.of(group.id(), group), 0, 0, 0);
        assertEquals(List.of(group), GroupIndexUpdate.between(missing, List.of(group)).changedGroups());
        assertEquals(List.of(group), GroupIndexUpdate.between(null, List.of(group)).changedGroups());
        var pending = new GroupCandidateIndex(Map.of(), Map.of(group.id(), group), 0, 0, 0,
            Map.of(group.id(), GroupEvaluation.pending()), 1);
        assertEquals(List.of(group), GroupIndexUpdate.between(pending, List.of(group)).changedGroups());
    }

    private static void assertEquivalent(GroupCandidateIndex expected, GroupCandidateIndex actual) {
        assertEquals(expected.candidates(), actual.candidates());
        assertEquals(expected.groupSnapshot(), actual.groupSnapshot());
        assertEquals(expected.candidateEdges(), actual.candidateEdges());
        assertEquals(expected.indexedIngredients(), actual.indexedIngredients());
        assertEquals(expected.maxCandidates(), actual.maxCandidates());
        expected.evaluations().forEach((id, e) -> assertEquals(
            new GroupEvaluation(actual.generation(), e.status(), e.itemCount(), e.fluidCount(), e.genericCount(), e.issues()),
            actual.evaluations().get(id)));
    }

    private static GroupDefinition group(String id, String tag) {
        return new GroupDefinition(id, id, true, new GroupFilter.Tag("item", "test:" + tag));
    }

    private static ViewerIngredient<String> ingredient(String id, Map<String, Integer> calls) {
        return new ViewerIngredient<>(new ViewerIngredientIdentity("item", id), ViewerIngredient.Kind.ITEM, id,
            new IngredientView() {
                public String ingredientType() { return "item"; }
                public ResourceLocation resourceLocation() { return new ResourceLocation("test:" + id); }
                public boolean hasTag(ResourceLocation tag) {
                    calls.merge(tag.getPath(), 1, Integer::sum);
                    return tag.getPath().equals("both") || tag.getPath().equals(id);
                }
                public boolean matchesExactStack(String stack) { return false; }
            });
    }
}
