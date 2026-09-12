package com.starskyxiii.collapsible_groups.viewer;

import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.client.manager.GroupManagerVisibility;
import com.starskyxiii.collapsible_groups.client.manager.model.BatchSelectionState;
import com.starskyxiii.collapsible_groups.group.*;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.ingredient.TagQueryDiagnostics;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GroupEvaluationsTest {
    @AfterEach void resetRepository() { GroupRepositoryTestAccess.replace(List.of()); }

    @Test void fullCountsIncludeSingleMatchesDisabledAndCompletelyShadowedGroupsAcrossKinds() {
        var item = ingredient("item", ViewerIngredient.Kind.ITEM, "test:one", new AtomicInteger());
        var fluid = ingredient("fluid", ViewerIngredient.Kind.FLUID, "test:water", new AtomicInteger());
        var gas = ingredient("test:gas", ViewerIngredient.Kind.GENERIC, "test:gas", new AtomicInteger());
        var all = group("all", Filters.any(Filters.itemId("test:one"), new GroupFilter.Id("fluid", "test:water"),
            new GroupFilter.Id("test:gas", "test:gas")));
        var disabled = group("disabled", Filters.itemId("test:one")).withEnabled(false);
        var shadowed = group("shadowed", Filters.itemId("test:one")).withPriority(-1);
        var empty = group("empty", Filters.itemId("test:missing"));
        var index = GroupProjectionEngine.buildCandidateIndex(new ViewerIngredientUniverse<>(List.of(item, fluid, gas)), List.of(all, disabled, shadowed, empty));
        assertEquals(3, index.evaluations().get("all").count());
        assertEquals(1, index.evaluations().get("all").genericCount());
        assertTrue(index.evaluations().get("disabled").hasContent());
        assertTrue(index.evaluations().get("shadowed").hasContent());
        assertTrue(index.evaluations().get("empty").empty());
        assertEquals(Set.of("all"), Set.copyOf(GroupProjectionEngine.resolveOwnership(index, List.of(all, disabled, shadowed, empty)).values()));
    }

    @Test void preflightInspectsUnsupportedRulesUnderEveryLogicalOperatorEvenWithoutCandidates() {
        var unknown = new GroupFilter.Unsupported(new JsonObject(), "future_rule");
        var item = Filters.itemId("test:missing");
        var groups = List.of(group("and", Filters.all(item, unknown)), group("or", Filters.any(item, unknown)),
            group("not", Filters.not(unknown)));
        var index = GroupProjectionEngine.buildCandidateIndex(new ViewerIngredientUniverse<String>(List.of()), groups);
        for (var evaluation : index.evaluations().values()) {
            assertEquals(GroupEvaluation.Status.UNAVAILABLE, evaluation.status());
            assertFalse(evaluation.empty());
            assertTrue(evaluation.issues().get(0).reason().contains("future_rule"));
        }
    }

    @Test void matchingOrBranchCannotHideUnavailableOrInvalidDataRules() {
        var unavailable = new GroupFilter.Unsupported(new JsonObject(), "future_rule");
        var malformed = new GroupFilter.Unsupported(new JsonObject(), "nbt_path");
        var match = Filters.itemId("test:one");
        var groups = List.of(group("unavailable", Filters.any(match, unavailable)), group("error", Filters.any(match, malformed)));
        var index = GroupProjectionEngine.buildCandidateIndex(new ViewerIngredientUniverse<>(List.of(
            ingredient("item", ViewerIngredient.Kind.ITEM, "test:one", new AtomicInteger()))), groups);
        assertEquals(GroupEvaluation.Status.UNAVAILABLE, index.evaluations().get("unavailable").status());
        assertEquals(GroupEvaluation.Status.ERROR, index.evaluations().get("error").status());
        assertEquals(1, index.evaluations().get("error").count());
        assertFalse(index.evaluations().get("error").empty());
    }

    @Test void missingIngredientTypePartialTagSupportAndCodecErrorsRemainVisible() {
        var context = new GroupEvaluationContext(Set.of("item", "fluid", "test:gas"),
            Map.of("test:gas", TagQueryDiagnostics.Availability.PARTIAL),
            filter -> List.of(new GroupEvaluation.Issue(GroupEvaluation.Status.ERROR, "Invalid NBT payload")));
        var groups = List.of(group("missing", new GroupFilter.Id("test:absent", "test:one")),
            group("tags", new GroupFilter.Tag("test:gas", "test:tag")),
            group("data", new GroupFilter.Nbt("{}")));
        var index = GroupProjectionEngine.buildCandidateIndex(new ViewerIngredientUniverse<String>(List.of(), null, context), groups);
        assertEquals(GroupEvaluation.Status.UNAVAILABLE, index.evaluations().get("missing").status());
        assertEquals(GroupEvaluation.Status.UNAVAILABLE, index.evaluations().get("tags").status());
        assertEquals(GroupEvaluation.Status.ERROR, index.evaluations().get("data").status());
        assertTrue(index.evaluations().values().stream().noneMatch(GroupEvaluation::empty));
    }

    @Test void cachedSummariesFilteringAndEnabledResolutionNeverRepeatCandidateMatching() {
        AtomicInteger lookups = new AtomicInteger();
        var one = group("one", Filters.itemId("test:one"));
        var empty = group("empty", Filters.itemId("test:absent"));
        var groups = List.of(one, empty);
        var index = GroupProjectionEngine.buildCandidateIndex(new ViewerIngredientUniverse<>(List.of(
            ingredient("item", ViewerIngredient.Kind.ITEM, "test:one", lookups))), groups);
        int afterBuild = lookups.get();
        for (int count = 0; count < 20; count++) {
            var hidden = GroupManagerVisibility.filter(groups, false, group -> index.evaluations().get(group.id()));
            assertEquals(List.of(one), hidden.visible());
            assertEquals(1, hidden.hiddenEmpty());
            assertEquals(groups, GroupManagerVisibility.filter(groups, true, group -> index.evaluations().get(group.id())).visible());
            assertTrue(GroupProjectionEngine.resolveOwnership(index, List.of(one.withEnabled(false), empty)).isEmpty());
            assertEquals(List.of("one"), new BatchSelectionState(List.of("empty", "one"))
                .pruneTo(hidden.visible().stream().map(GroupDefinition::id).toList()).selectedGroupIds());
        }
        assertEquals(afterBuild, lookups.get());
        assertTrue(afterBuild > 0);
    }

    @Test void showEmptyNeverHidesPendingErrorsOrUnavailableEntries() {
        var values = List.of(GroupEvaluation.pending(), new GroupEvaluation(1, GroupEvaluation.Status.ERROR, 0, 0, 0, List.of()),
            new GroupEvaluation(1, GroupEvaluation.Status.UNAVAILABLE, 0, 0, 0, List.of()),
            new GroupEvaluation(1, GroupEvaluation.Status.COMPLETE, 0, 0, 0, List.of()));
        var result = GroupManagerVisibility.filter(values, false, evaluation -> evaluation);
        assertEquals(values.subList(0, 3), result.visible());
        assertEquals(1, result.hiddenEmpty());
    }

    @Test void oldOrMismatchedIndexReturnsPendingWithoutCallingFullMatchResolver() {
        var original = group("one", Filters.itemId("test:one"));
        var candidates = GroupProjectionEngine.buildCandidateIndex(new ViewerIngredientUniverse<String>(List.of()), List.of(original));
        ViewerGroupIndex index = readOnly(candidates, true);
        assertTrue(index.evaluation(original).empty());
        assertEquals(GroupEvaluation.Status.PENDING, index.evaluation(original.withFilter(Filters.itemId("test:two"))).status());
        assertEquals(GroupEvaluation.Status.PENDING, readOnly(candidates, false).evaluation(original).status());
        assertEquals(GroupEvaluation.Status.PENDING, readOnly(new GroupCandidateIndex(Map.of(), Map.of("one", original), 0, 0, 0), true)
            .evaluation(original).status());
    }

    private static ViewerGroupIndex readOnly(GroupCandidateIndex candidates, boolean ready) {
        return new ViewerGroupIndex() {
            public Optional<GroupCandidateIndex> candidates() { return Optional.of(candidates); }
            public boolean ready() { return ready; }
            public CompletableFuture<Void> whenReady() { return CompletableFuture.completedFuture(null); }
            public Optional<ViewerGroupPreviewSnapshot> fullMatchSnapshot(GroupDefinition group) { throw new AssertionError("Unexpected full scan"); }
            public Optional<String> resolveOwner(ViewerIngredientIdentity identity, List<GroupDefinition> groups) { return Optional.empty(); }
            public Map<ViewerIngredientIdentity, String> resolveOwnership(List<GroupDefinition> groups) { return Map.of(); }
            public void onGroupChange(GroupChangeEvent.Kind kind, List<GroupDefinition> groups) {}
        };
    }

    private static GroupDefinition group(String id, GroupFilter filter) { return new GroupDefinition(id, id, true, filter); }

    static ViewerIngredient<String> ingredient(String type, ViewerIngredient.Kind kind, String id, AtomicInteger calls) {
        ResourceLocation resource = new ResourceLocation(id);
        return new ViewerIngredient<>(new ViewerIngredientIdentity(type, id), kind, id, new IngredientView() {
            public String ingredientType() { return type; }
            public ResourceLocation resourceLocation() { calls.incrementAndGet(); return resource; }
            public boolean hasTag(ResourceLocation tag) { return false; }
            public boolean matchesExactStack(String stack) { return false; }
        });
    }
}
