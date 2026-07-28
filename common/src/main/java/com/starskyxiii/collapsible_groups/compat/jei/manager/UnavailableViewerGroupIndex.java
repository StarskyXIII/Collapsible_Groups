package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupPreviewSnapshot;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Loading-state index used only before the selected viewer adapter registers. */
final class UnavailableViewerGroupIndex implements ViewerGroupIndex {
	static final UnavailableViewerGroupIndex INSTANCE = new UnavailableViewerGroupIndex();
	private final CompletableFuture<Void> readiness = new CompletableFuture<>();

	private UnavailableViewerGroupIndex() {}

	@Override public Optional<GroupCandidateIndex> candidates() { return Optional.empty(); }
	@Override public boolean ready() { return false; }
	@Override public CompletableFuture<Void> whenReady() { return readiness; }
	@Override public Optional<ViewerGroupPreviewSnapshot> fullMatchSnapshot(GroupDefinition group) {
		return Optional.empty();
	}
	@Override public Optional<String> resolveOwner(ViewerIngredientIdentity identity,
		List<GroupDefinition> groups) { return Optional.empty(); }
	@Override public Map<ViewerIngredientIdentity, String> resolveOwnership(List<GroupDefinition> groups) {
		return Map.of();
	}
	@Override public void onGroupChange(GroupChangeEvent.Kind kind, List<GroupDefinition> groups) {}
}
