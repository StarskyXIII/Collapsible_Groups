package com.starskyxiii.collapsible_groups.internal.query;

import com.starskyxiii.collapsible_groups.group.filter.FilterTypeScope;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;

import java.util.Objects;

public final class QueryPlan {
	private final FilterTypeScope candidateTypes;

	private QueryPlan(FilterTypeScope candidateTypes) {
		this.candidateTypes = candidateTypes;
	}

	public static QueryPlan compile(GroupFilter source) {
		return new QueryPlan(FilterTypeScope.candidates(Objects.requireNonNull(source, "source")));
	}

	public boolean mayMatch(String ingredientType) {
		return candidateTypes.contains(ingredientType);
	}

	public boolean mayMatchItems() {
		return mayMatch("item");
	}

	public boolean mayMatchFluids() {
		return mayMatch("fluid");
	}

	public boolean mayMatchGeneric() {
		return candidateTypes.containsGeneric();
	}

	public FilterTypeScope candidateTypes() {
		return candidateTypes;
	}
}
