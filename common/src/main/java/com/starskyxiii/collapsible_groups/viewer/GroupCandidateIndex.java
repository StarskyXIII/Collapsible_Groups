package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Enabled-independent, priority-ordered group candidates for each ingredient. */
public record GroupCandidateIndex(
	Map<ViewerIngredientIdentity, List<String>> candidates,
	Map<String, GroupDefinition> groupSnapshot,
	long candidateEdges,
	int indexedIngredients,
	int maxCandidates
) {
	public GroupCandidateIndex {
		candidates = Collections.unmodifiableMap(new LinkedHashMap<>(candidates));
		groupSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(groupSnapshot));
	}

	public double averageCandidates() {
		return indexedIngredients == 0 ? 0.0 : (double) candidateEdges / indexedIngredients;
	}
}
