package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.client.manager.model.GroupSortMode;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Deterministic, locale-provider-independent ordering for manager views. */
final class GroupManagerSort {
	private GroupManagerSort() {}

	static <T> List<T> apply(List<T> source, GroupSortMode mode,
	                         Function<T, String> displayName, Function<T, String> id) {
		if (mode == null || mode == GroupSortMode.PRIORITY) return List.copyOf(source);
		Comparator<T> comparator = (left, right) -> {
			String leftName = key(displayName.apply(left));
			String rightName = key(displayName.apply(right));
			int nameOrder = mode == GroupSortMode.NAME_DESC
				? rightName.compareTo(leftName)
				: leftName.compareTo(rightName);
			return nameOrder != 0 ? nameOrder : clean(id.apply(left)).compareTo(clean(id.apply(right)));
		};
		return source.stream().sorted(comparator).toList();
	}

	static String key(String value) {
		return Normalizer.normalize(clean(value), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
	}

	private static String clean(String value) {
		return value == null ? "" : value;
	}
}
