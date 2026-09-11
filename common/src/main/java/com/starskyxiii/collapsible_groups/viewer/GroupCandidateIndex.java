package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupEvaluation;
import com.starskyxiii.collapsible_groups.internal.query.IngredientCatalog;

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
	int maxCandidates,
	Map<String, GroupEvaluation> evaluations,
	long generation
) {
	private static final java.util.concurrent.atomic.AtomicLong GENERATIONS = new java.util.concurrent.atomic.AtomicLong();

	public GroupCandidateIndex(Map<ViewerIngredientIdentity, List<String>> candidates,
		Map<String, GroupDefinition> groupSnapshot, long candidateEdges, int indexedIngredients, int maxCandidates) {
		this(candidates, groupSnapshot, candidateEdges, indexedIngredients, maxCandidates, Map.of(), GENERATIONS.incrementAndGet());
	}

	public GroupCandidateIndex {
		candidates = Collections.unmodifiableMap(new LinkedHashMap<>(candidates));
		groupSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(groupSnapshot));
		evaluations = Map.copyOf(evaluations);
	}

	public static <E> GroupCandidateIndex completed(Map<ViewerIngredientIdentity, List<String>> candidates,
		Map<String, GroupDefinition> groups, long edges, int indexedIngredients, int maxCandidates,
		IngredientCatalog<ViewerIngredient<E>, ViewerIngredientIdentity> universe, Map<String, String> failures) {
		long generation = GENERATIONS.incrementAndGet();
		return new GroupCandidateIndex(candidates, groups, edges, indexedIngredients, maxCandidates,
			GroupEvaluations.summarize(generation, universe, List.copyOf(groups.values()), candidates, failures), generation);
	}

	public double averageCandidates() {
		return indexedIngredients == 0 ? 0.0 : (double) candidateEdges / indexedIngredients;
	}
}
