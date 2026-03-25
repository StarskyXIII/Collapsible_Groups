package com.starskyxiii.collapsible_groups.core;

import java.util.ArrayList;
import java.util.List;

public final class GroupFilterNormalizer {
	private GroupFilterNormalizer() {}

	public static GroupFilter normalize(GroupFilter filter) {
		return switch (filter) {
			case GroupFilter.Any any -> normalizeAny(any.children());
			case GroupFilter.All all -> normalizeAll(all.children());
			case GroupFilter.Not not -> normalizeNot(not.child());
			default -> filter;
		};
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
			return normalized.getFirst();
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
			return normalized.getFirst();
		}
		return new GroupFilter.All(normalized);
	}

	private static GroupFilter normalizeNot(GroupFilter child) {
		GroupFilter normalizedChild = normalize(child);
		if (normalizedChild instanceof GroupFilter.Not nested) {
			return normalize(nested.child());
		}
		return new GroupFilter.Not(normalizedChild);
	}
}
