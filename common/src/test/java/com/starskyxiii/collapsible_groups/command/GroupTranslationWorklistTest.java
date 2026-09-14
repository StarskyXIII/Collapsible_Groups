package com.starskyxiii.collapsible_groups.command;

import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.group.*;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroupTranslationWorklistTest {
    @TempDir Path output;

    @Test void includesDisabledSingleMatchAndAllIngredientTypesButExcludesOnlyCompleteEmptyGroups() {
        List<GroupDefinition> groups = List.of(group("builtin", "key.builtin", "Built-in", false),
            group("fluid", "key.fluid", "液體", true), group("generic", "key.generic", "Gas", true),
            group("empty", "key.empty", "Empty", true));
        var plan = GroupTranslationWorklist.plan(groups, resources(groups), Map.of(
            "builtin", complete(4, 1, 0, 0), "fluid", complete(4, 0, 1, 0),
            "generic", complete(4, 0, 0, 1), "empty", complete(4, 0, 0, 0)), Map.of(), false);
        assertEquals(Map.of("key.builtin", "Built-in", "key.fluid", "液體", "key.generic", "Gas"), plan.entries());
        assertEquals(List.of("empty"), plan.empty());
        assertTrue(plan.complete());
        assertEquals("en_us", plan.included().get(0).fallbackLanguage());
        assertEquals("unspecified", plan.included().get(1).fallbackLanguage());
        assertFalse(plan.included().get(0).enabled());
    }

    @Test void existingTargetEntriesArePreservedEvenWhenEqualToFallback() {
        List<GroupDefinition> groups = List.of(group("a", "a", "English", true),
            group("b", "b", "Unchanged proper noun", true), group("c", "c", "Missing", true));
        var evaluations = Map.of("a", complete(2, 1, 0, 0), "b", complete(2, 1, 0, 0), "c", complete(2, 1, 0, 0));
        Map<String, String> target = Map.of("a", "人工翻譯", "b", "Unchanged proper noun");
        var all = GroupTranslationWorklist.plan(groups, resources(groups), evaluations, target, false);
        var missing = GroupTranslationWorklist.plan(groups, resources(groups), evaluations, target, true);
        assertEquals(Map.of("a", "人工翻譯", "b", "Unchanged proper noun", "c", "Missing"), all.entries());
        assertEquals(Map.of("c", "Missing"), missing.entries());
        assertEquals(2, missing.existingKeys());
    }

    @Test void duplicateKeysWithDifferentFallbacksAreOmittedAndReportEverySource() {
        List<GroupDefinition> groups = List.of(group("one", "shared", "One", true),
            group("two", "shared", "Two", true), group("three", "safe", "Same", true),
            group("four", "safe", "Same", false));
        var evaluations = Map.of("one", complete(2, 1, 0, 0), "two", complete(2, 1, 0, 0),
            "three", complete(2, 1, 0, 0), "four", complete(2, 1, 0, 0));
        var plan = GroupTranslationWorklist.plan(groups, resources(groups), evaluations, Map.of("shared", "Existing"), false);
        assertFalse(plan.complete());
        assertEquals(Map.of("safe", "Same"), plan.entries());
        assertEquals(List.of("one", "two"), plan.conflicts().get(0).definitions().stream().map(GroupTranslationWorklist.Entry::groupId).toList());
    }

    @Test void unavailableAndErrorGroupsMakeScopeExplicitlyIncomplete() {
        List<GroupDefinition> groups = List.of(group("good", "good", "Good", true),
            group("error", "error", "Bad data", true), group("unknown", "unknown", "Missing type", false));
        var plan = GroupTranslationWorklist.plan(groups, resources(groups), Map.of("good", complete(2, 1, 0, 0),
            "error", issue(2, GroupEvaluation.Status.ERROR), "unknown", issue(2, GroupEvaluation.Status.UNAVAILABLE)), Map.of(), true);
        assertFalse(plan.complete());
        assertEquals(2, plan.uncertain().size());
        assertEquals(Map.of("good", "Good"), plan.entries());
        assertTrue(plan.empty().isEmpty());
    }

    @Test void pendingStaleAndMixedGenerationsAreRejectedBeforeWriting() throws Exception {
        var a = group("a", "a", "A", true);
        var b = group("b", "b", "B", true);
        var groups = List.of(a, b);
        assertThrows(IllegalStateException.class, () -> GroupTranslationWorklist.plan(groups, resources(groups),
            Map.of("a", complete(1, 1, 0, 0)), Map.of(), false));
        assertThrows(IllegalStateException.class, () -> GroupTranslationWorklist.plan(groups, resources(groups),
            Map.of("a", complete(1, 1, 0, 0), "b", complete(2, 1, 0, 0)), Map.of(), false));
        assertThrows(IllegalStateException.class, () -> GroupTranslationWorklist.plan(groups, resources(groups).retaining(resources(groups)),
            Map.of("a", complete(1, 1, 0, 0), "b", complete(1, 1, 0, 0)), Map.of(), false));
        try (var files = Files.list(output)) { assertEquals(0, files.count()); }
    }

    @Test void eachExportCreatesAnIndependentWorkDirectoryAndScopeReport() throws Exception {
        var groups = List.of(group("one", "key", "原始名稱", false));
        var plan = GroupTranslationWorklist.plan(groups, resources(groups), Map.of("one", complete(7, 1, 0, 0)), Map.of(), true);
        Path first = GroupTranslationWorklist.write(plan, output, "zh_tw", "1.21.1", "emi", List.of("manual"));
        Files.writeString(first.resolve("zh_tw.json"), "my translation in progress");
        Path second = GroupTranslationWorklist.write(plan, output, "zh_tw", "1.21.1", "jei", List.of());
        assertNotEquals(first, second);
        assertEquals("my translation in progress", Files.readString(first.resolve("zh_tw.json")));
        var report = JsonParser.parseString(Files.readString(second.resolve("report.json"))).getAsJsonObject();
        assertEquals("zh_tw", report.get("target_locale").getAsString());
        assertEquals(1, report.get("included_groups").getAsInt());
        assertEquals(0, report.get("uncertain_groups").getAsInt());
        assertTrue(report.get("complete").getAsBoolean());
    }

    private static GroupDefinition group(String id, String key, String fallback, boolean enabled) {
        return new GroupDefinition(id, new GroupDisplayName.Localized(key, fallback), enabled, Filters.itemId("minecraft:stone"));
    }

    private static GroupResourceData resources(List<GroupDefinition> groups) {
        Map<String, List<GroupOrigin>> origins = new LinkedHashMap<>();
        Map<String, List<GroupDefinition>> definitions = new LinkedHashMap<>();
        for (GroupDefinition group : groups) {
            GroupSource source = group.id().equals("builtin") ? GroupSource.BUILTIN : GroupSource.USER;
            origins.put(group.id(), List.of(new GroupOrigin(source, source.name(), group.id() + ".json", null)));
            definitions.put(group.id(), List.of(group));
        }
        return new GroupResourceData(groups, Set.of("builtin"), origins, definitions, Map.of(), List.of(), false, false);
    }

    private static GroupEvaluation complete(long generation, int items, int fluids, int generic) {
        return new GroupEvaluation(generation, GroupEvaluation.Status.COMPLETE, items, fluids, generic, List.of());
    }

    private static GroupEvaluation issue(long generation, GroupEvaluation.Status status) {
        return new GroupEvaluation(generation, status, 0, 0, 0, List.of(new GroupEvaluation.Issue(status, "test issue")));
    }
}
