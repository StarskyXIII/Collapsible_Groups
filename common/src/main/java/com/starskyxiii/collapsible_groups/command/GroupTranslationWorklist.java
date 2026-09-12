package com.starskyxiii.collapsible_groups.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupEvaluation;
import com.starskyxiii.collapsible_groups.group.GroupOrigin;
import com.starskyxiii.collapsible_groups.group.GroupResourceData;
import com.starskyxiii.collapsible_groups.group.GroupSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class GroupTranslationWorklist {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record Entry(String groupId, String key, String fallback, String fallbackLanguage,
        String source, String location, boolean enabled, int items, int fluids, int generic) {}
    public record Uncertain(String groupId, String status, List<String> reasons) {}
    public record Conflict(String key, List<Entry> definitions) {}
    public record Plan(Map<String, String> entries, List<Entry> included, List<String> empty,
        List<Uncertain> uncertain, List<Conflict> conflicts, List<String> sourceProblems, int existingKeys,
        long generation, boolean missingOnly) {
        public Plan {
            entries = Collections.unmodifiableMap(new TreeMap<>(entries));
            included = List.copyOf(included);
            empty = List.copyOf(empty);
            uncertain = List.copyOf(uncertain);
            conflicts = List.copyOf(conflicts);
            sourceProblems = List.copyOf(sourceProblems);
        }

        public boolean complete() { return uncertain.isEmpty() && conflicts.isEmpty() && sourceProblems.isEmpty(); }
    }

    private GroupTranslationWorklist() {}

    public static Plan plan(List<GroupDefinition> groups, GroupResourceData resources,
        Map<String, GroupEvaluation> evaluations, Map<String, String> target, boolean missingOnly) {
        if (resources.stale() || resources.rejected()) throw new IllegalStateException("Group resources are not current");
        List<Entry> included = new ArrayList<>();
        List<String> empty = new ArrayList<>();
        List<Uncertain> uncertain = new ArrayList<>();
        Map<String, List<Entry>> byKey = new TreeMap<>();
        long generation = -1;
        for (GroupDefinition group : groups) {
            GroupEvaluation evaluation = evaluations.getOrDefault(group.id(), GroupEvaluation.pending());
            if (evaluation.status() == GroupEvaluation.Status.PENDING) throw new IllegalStateException("Group evaluation is pending: " + group.id());
            if (generation == -1) generation = evaluation.generation();
            if (generation != evaluation.generation()) throw new IllegalStateException("Group evaluation generations differ");
            if (!evaluation.complete()) {
                uncertain.add(new Uncertain(group.id(), evaluation.status().name(),
                    evaluation.issues().stream().map(GroupEvaluation.Issue::reason).toList()));
            } else if (evaluation.empty()) {
                empty.add(group.id());
            } else {
                GroupOrigin origin = resources.origin(group.id());
                boolean builtin = origin != null && origin.source() == GroupSource.BUILTIN;
                Entry entry = new Entry(group.id(), group.displayName().key(), group.displayName().fallback(),
                    builtin ? "en_us" : "unspecified", origin == null ? GroupSource.KUBEJS.name() : origin.source().name(),
                    origin == null ? "KubeJS:" + group.id() : origin.sourceId() + ":" + origin.location(), group.enabled(),
                    evaluation.itemCount(), evaluation.fluidCount(), evaluation.genericCount());
                included.add(entry);
                byKey.computeIfAbsent(entry.key(), ignored -> new ArrayList<>()).add(entry);
            }
        }
        Map<String, String> entries = new TreeMap<>();
        List<Conflict> conflicts = new ArrayList<>();
        int existingKeys = 0;
        for (var keyed : byKey.entrySet()) {
            if (keyed.getValue().stream().map(Entry::fallback).distinct().count() > 1) {
                conflicts.add(new Conflict(keyed.getKey(), List.copyOf(keyed.getValue())));
                continue;
            }
            boolean exists = target.containsKey(keyed.getKey());
            if (exists) existingKeys++;
            if (!missingOnly || !exists) entries.put(keyed.getKey(), exists ? target.get(keyed.getKey()) : keyed.getValue().get(0).fallback());
        }
        List<String> problems = resources.problems().stream().filter(problem -> problem.error())
            .map(problem -> problem.origin().location() + ": " + problem.reason()).toList();
        return new Plan(entries, included, empty, uncertain, conflicts, problems, existingKeys, generation, missingOnly);
    }

    public static Path write(Plan plan, Path directory, String locale, String minecraft, String viewer,
        List<String> languageSources) throws IOException {
        TargetLocaleEntries.validateLocale(locale);
        Files.createDirectories(directory);
        Path output = Files.createTempDirectory(directory, locale + "-");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("target_locale", locale);
        report.put("minecraft", minecraft);
        report.put("viewer", viewer);
        report.put("complete", plan.complete());
        report.put("scope", plan.missingOnly() ? "missing_target_entries" : "all_nonempty_groups");
        report.put("evaluation_generation", plan.generation());
        report.put("included_groups", plan.included().size());
        report.put("excluded_empty_groups", plan.empty().size());
        report.put("uncertain_groups", plan.uncertain().size());
        report.put("exported_keys", plan.entries().size());
        report.put("existing_target_keys", plan.existingKeys());
        report.put("target_language_sources", languageSources);
        report.put("groups", plan.included());
        report.put("empty_group_ids", plan.empty());
        report.put("uncertain", plan.uncertain());
        report.put("conflicts", plan.conflicts());
        report.put("source_problems", plan.sourceProblems());
        report.put("note", "Existing target entries are preserved. Missing entries use the author's fallback; fallbackLanguage identifies maintained built-in English or unspecified source language. This is a translation worklist, not a translation-quality assessment.");
        Files.writeString(output.resolve(locale + ".json"), GSON.toJson(plan.entries()) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(output.resolve("report.json"), GSON.toJson(report) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return output;
    }
}
