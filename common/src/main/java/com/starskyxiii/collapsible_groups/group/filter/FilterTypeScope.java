package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record FilterTypeScope(Set<String> types, boolean unrestricted) {
	public FilterTypeScope {
		types = Set.copyOf(types);
	}

	public static FilterTypeScope declared(GroupFilter filter) {
		return collect(filter, false);
	}

	public static FilterTypeScope candidates(GroupFilter filter) {
		return collect(filter, true);
	}

	public boolean isEmpty() {
		return !unrestricted && types.isEmpty();
	}

	public boolean contains(String type) {
		if (unrestricted || types.contains(type)) return true;
		String canonical = canonical(type);
		return types.stream().anyMatch(value -> canonical(value).equals(canonical));
	}

	public boolean containsGeneric() {
		return unrestricted || types.stream().map(FilterTypeScope::canonical)
			.anyMatch(type -> !"item".equals(type) && !"fluid".equals(type));
	}

	private static FilterTypeScope collect(GroupFilter filter, boolean candidates) {
		return switch (filter) {
			case GroupFilter.Any any -> union(any.children(), candidates);
			case GroupFilter.All all -> all.children().isEmpty() && candidates
				? new FilterTypeScope(Set.of(), true) : union(all.children(), candidates);
			case GroupFilter.Not not -> declared(not.child());
			case GroupFilter.Id id -> single(id.ingredientType());
			case GroupFilter.Tag tag -> single(tag.ingredientType());
			case GroupFilter.Namespace namespace -> single(namespace.ingredientType());
			case GroupFilter.Unsupported ignored -> new FilterTypeScope(Set.of(), false);
			default -> single("item");
		};
	}

	private static FilterTypeScope union(List<GroupFilter> children, boolean candidates) {
		Set<String> types = new LinkedHashSet<>();
		boolean unrestricted = false;
		for (GroupFilter child : children) {
			FilterTypeScope scope = collect(child, candidates);
			types.addAll(scope.types());
			unrestricted |= scope.unrestricted();
		}
		return new FilterTypeScope(types, unrestricted);
	}

	private static FilterTypeScope single(String type) {
		return new FilterTypeScope(Set.of(type), false);
	}

	private static String canonical(String type) {
		String canonical = IngredientTypeIds.getCanonicalId(type);
		return canonical == null ? type : canonical;
	}
}
