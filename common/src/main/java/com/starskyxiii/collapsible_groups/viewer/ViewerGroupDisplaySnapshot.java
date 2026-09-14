package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupEvaluation;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public record ViewerGroupDisplaySnapshot(GroupCandidateIndex candidates,
    Function<GroupDefinition, Optional<ViewerGroupPreviewSnapshot>> previews,
    CompletableFuture<Void> readiness, boolean pending, boolean failed) {

    public boolean matches(GroupDefinition group) {
        GroupDefinition indexed = candidates == null ? null : candidates.groupSnapshot().get(group.id());
        return indexed != null && indexed.filter().equals(group.filter())
            && indexed.documentFormat() == group.documentFormat();
    }

    public GroupEvaluation evaluation(GroupDefinition group) {
        if (matches(group)) return candidates.evaluations().getOrDefault(group.id(), GroupEvaluation.pending());
        return failed ? new GroupEvaluation(-1, GroupEvaluation.Status.ERROR, 0, 0, 0, List.of())
            : GroupEvaluation.pending();
    }

    public Optional<ViewerGroupPreviewSnapshot> preview(GroupDefinition group) {
        return matches(group) ? previews.apply(group) : Optional.empty();
    }
}
