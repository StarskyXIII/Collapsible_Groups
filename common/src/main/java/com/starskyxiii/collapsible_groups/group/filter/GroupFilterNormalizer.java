package com.starskyxiii.collapsible_groups.group.filter;

import java.util.ArrayList;
import java.util.List;

public final class GroupFilterNormalizer {
	private GroupFilterNormalizer() {}

	public static GroupFilter normalize(GroupFilter filter) {
		if (filter instanceof GroupFilter.Any) return normalizeAny(((GroupFilter.Any) filter).children());
		if (filter instanceof GroupFilter.All) return normalizeAll(((GroupFilter.All) filter).children());
		if (filter instanceof GroupFilter.Not) return normalizeNot(((GroupFilter.Not) filter).child());
		return filter;
	}

	private static GroupFilter normalizeAny(List<GroupFilter> children) {
		List<GroupFilter> normalized = new ArrayList<>();
		for (GroupFilter child : children) {
			GroupFilter node = normalize(child);
			if (node instanceof GroupFilter.Any nested) {
				normalized.addAll(nested.children());
			} else {
				normalized.add(node);
			}
		}
		if (normalized.size() == 1) {
			return normalized.get(0);
		}
		return new GroupFilter.Any(normalized);
	}

	private static GroupFilter normalizeAll(List<GroupFilter> children) {
		List<GroupFilter> normalized = new ArrayList<>();
		for (GroupFilter child : children) {
			GroupFilter node = normalize(child);
			if (node instanceof GroupFilter.All nested) {
				normalized.addAll(nested.children());
			} else {
				normalized.add(node);
			}
		}
		if (normalized.size() == 1) {
			return normalized.get(0);
		}
		return new GroupFilter.All(normalized);
	}

	private static GroupFilter normalizeNot(GroupFilter child) {
		GroupFilter normalizedChild = normalize(child);
		return new GroupFilter.Not(normalizedChild);
	}
}
