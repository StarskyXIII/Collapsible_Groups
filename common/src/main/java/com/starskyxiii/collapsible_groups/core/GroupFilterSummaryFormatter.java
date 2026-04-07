package com.starskyxiii.collapsible_groups.core;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only formatter for compact structural filter summaries used by the editor UI.
 */
public final class GroupFilterSummaryFormatter {
	private static final String ITEM_TYPE = "item";
	private static final String FLUID_TYPE = "fluid";

	private GroupFilterSummaryFormatter() {}

	public static String format(@Nullable GroupFilter filter) {
		if (filter == null) {
			return "";
		}
		return formatNode(GroupFilterNormalizer.normalize(filter), true);
	}

	private static String formatNode(GroupFilter filter, boolean expandAtomicChildren) {
		return switch (filter) {
			case GroupFilter.Any any -> formatComposite("ANY", any.children(), expandAtomicChildren);
			case GroupFilter.All all -> formatComposite("ALL", all.children(), expandAtomicChildren);
			case GroupFilter.Not not -> "NOT(" + formatNestedChild(not.child()) + ")";
			case GroupFilter.Id id -> formatId(id.ingredientType(), id.id());
			case GroupFilter.Tag tag -> formatTag(tag.ingredientType(), tag.tag());
			case GroupFilter.BlockTag blockTag -> "block tag " + blockTag.tag();
			case GroupFilter.Namespace namespace -> formatNamespace(namespace.ingredientType(), namespace.namespace());
			case GroupFilter.ExactStack ignored -> "exact stack";
			case GroupFilter.HasComponent hc -> "has component " + hc.componentTypeId() + "=" + hc.encodedValue();
			case GroupFilter.ComponentPath cp -> "component path " + cp.componentTypeId() + "/" + cp.path() + "=" + cp.expectedValue();
		};
	}

	private static String formatComposite(String operator, List<GroupFilter> children, boolean expandAtomicChildren) {
		if (children.isEmpty()) {
			return operator + "()";
		}
		if (expandAtomicChildren && children.stream().allMatch(GroupFilterSummaryFormatter::isAtomic)) {
			return operator + "(" + formatAtomicChildren(children) + ")";
		}

		List<String> parts = new ArrayList<>(children.size());
		for (GroupFilter child : children) {
			parts.add(formatNestedChild(child));
		}
		return operator + "(" + String.join(", ", parts) + ")";
	}

	private static String formatAtomicChildren(List<GroupFilter> children) {
		if (children.size() <= 2) {
			return children.stream()
				.map(child -> formatNode(child, true))
				.reduce((left, right) -> left + ", " + right)
				.orElse("");
		}

		Map<String, Integer> counts = new LinkedHashMap<>();
		for (GroupFilter child : children) {
			counts.merge(categoryLabel(child), 1, Integer::sum);
		}

		List<String> parts = new ArrayList<>(counts.size());
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			parts.add(entry.getKey() + " x" + entry.getValue());
		}
		return String.join(", ", parts);
	}

	private static String formatNestedChild(GroupFilter child) {
		if (isAtomic(child)) {
			return formatNode(child, true);
		}
		return switch (child) {
			case GroupFilter.Any ignored -> "ANY(...)";
			case GroupFilter.All ignored -> "ALL(...)";
			case GroupFilter.Not ignored -> "NOT(...)";
			default -> formatNode(child, false);
		};
	}

	private static boolean isAtomic(GroupFilter filter) {
		return filter instanceof GroupFilter.Id
			|| filter instanceof GroupFilter.Tag
			|| filter instanceof GroupFilter.BlockTag
			|| filter instanceof GroupFilter.Namespace
			|| filter instanceof GroupFilter.ExactStack
			|| filter instanceof GroupFilter.HasComponent
			|| filter instanceof GroupFilter.ComponentPath;
	}

	private static String categoryLabel(GroupFilter filter) {
		return switch (filter) {
			case GroupFilter.Id id -> categoryPrefix(id.ingredientType()) + "id";
			case GroupFilter.Tag tag -> categoryPrefix(tag.ingredientType()) + "tag";
			case GroupFilter.BlockTag ignored -> "block tag";
			case GroupFilter.Namespace namespace -> categoryPrefix(namespace.ingredientType()) + "namespace";
			case GroupFilter.ExactStack ignored -> "exact stack";
			case GroupFilter.HasComponent ignored -> "has component";
			case GroupFilter.ComponentPath ignored -> "component path";
			default -> formatNode(filter, false);
		};
	}

	private static String formatId(String ingredientType, String id) {
		return categoryPrefix(ingredientType) + "id " + id;
	}

	private static String formatTag(String ingredientType, String tag) {
		return valuePrefix(ingredientType) + "tag " + tag;
	}

	private static String formatNamespace(String ingredientType, String namespace) {
		return valuePrefix(ingredientType) + "namespace " + namespace;
	}

	private static String categoryPrefix(String ingredientType) {
		return switch (ingredientType) {
			case ITEM_TYPE -> "item ";
			case FLUID_TYPE -> "fluid ";
			default -> ingredientType + " ";
		};
	}

	private static String valuePrefix(String ingredientType) {
		return switch (ingredientType) {
			case ITEM_TYPE -> "";
			case FLUID_TYPE -> "fluid ";
			default -> ingredientType + " ";
		};
	}
}
