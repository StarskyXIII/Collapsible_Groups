package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ViewerGroupIndex {
	default ViewerGroupDisplaySnapshot displaySnapshot() {
		var readiness = whenReady();
		return new ViewerGroupDisplaySnapshot(null, id -> Optional.empty(), readiness,
			!readiness.isDone(), readiness.isCompletedExceptionally());
	}

	default com.starskyxiii.collapsible_groups.group.GroupEvaluation evaluation(GroupDefinition group) {
		if (!ready()) return com.starskyxiii.collapsible_groups.group.GroupEvaluation.pending();
		GroupCandidateIndex index = candidates().orElse(null);
		GroupDefinition indexed = index == null ? null : index.groupSnapshot().get(group.id());
		if (indexed == null || !indexed.filter().equals(group.filter()) || indexed.documentFormat() != group.documentFormat()) {
			return com.starskyxiii.collapsible_groups.group.GroupEvaluation.pending();
		}
		return index.evaluations().getOrDefault(group.id(), com.starskyxiii.collapsible_groups.group.GroupEvaluation.pending());
	}

	/** Returns the current enabled-independent candidate generation, if one is ready. */
	Optional<GroupCandidateIndex> candidates();

	/** True only when a candidate generation and its enabled-dependent resolved caches are ready. */
	boolean ready();

	/** Completes when the current asynchronous rebuild, if any, has published its generation. */
	CompletableFuture<Void> whenReady();

	/** Resolves one current enabled owner by walking only this identity's candidate list. */
	Optional<String> resolveOwner(ViewerIngredientIdentity identity, List<GroupDefinition> groups);

	/** Resolves all current enabled owners from the ready candidate generation. */
	Map<ViewerIngredientIdentity, String> resolveOwnership(List<GroupDefinition> groups);

	/** Applies the cache action prescribed by the lifecycle table. */
	void onGroupChange(GroupChangeEvent.Kind kind, List<GroupDefinition> groups);
}
