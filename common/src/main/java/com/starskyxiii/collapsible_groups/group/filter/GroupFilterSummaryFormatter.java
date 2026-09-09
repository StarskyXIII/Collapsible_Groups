package com.starskyxiii.collapsible_groups.group.filter;

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
		if (filter instanceof GroupFilter.Any) return formatComposite("ANY", ((GroupFilter.Any) filter).children(), expandAtomicChildren);
		if (filter instanceof GroupFilter.All) return formatComposite("ALL", ((GroupFilter.All) filter).children(), expandAtomicChildren);
		if (filter instanceof GroupFilter.Not) return "NOT(" + formatNestedChild(((GroupFilter.Not) filter).child()) + ")";
		if (filter instanceof GroupFilter.Id) { GroupFilter.Id value = (GroupFilter.Id) filter; return formatId(value.ingredientType(), value.id()); }
		if (filter instanceof GroupFilter.Tag) { GroupFilter.Tag value = (GroupFilter.Tag) filter; return formatTag(value.ingredientType(), value.tag()); }
		if (filter instanceof GroupFilter.BlockTag) return "block tag " + ((GroupFilter.BlockTag) filter).tag();
		if (filter instanceof GroupFilter.ItemPathStartsWith) return "item path starts with " + ((GroupFilter.ItemPathStartsWith) filter).prefix();
		if (filter instanceof GroupFilter.ItemPathContains) return "item path contains " + ((GroupFilter.ItemPathContains) filter).needle();
		if (filter instanceof GroupFilter.ItemPathEndsWith) return "item path ends with " + ((GroupFilter.ItemPathEndsWith) filter).suffix();
		if (filter instanceof GroupFilter.Namespace) { GroupFilter.Namespace value = (GroupFilter.Namespace) filter; return formatNamespace(value.ingredientType(), value.namespace()); }
		if (filter instanceof GroupFilter.ExactStack) return "exact stack";
		if (filter instanceof GroupFilter.Nbt) return "NBT = " + ((GroupFilter.Nbt) filter).expectedSnbt();
		if (filter instanceof GroupFilter.NbtPath) { GroupFilter.NbtPath value = (GroupFilter.NbtPath) filter; return "NBT path " + value.path() + " = " + value.expectedSnbt(); }
		if (filter instanceof GroupFilter.HasComponent) { GroupFilter.HasComponent value = (GroupFilter.HasComponent) filter; return "has component " + value.componentTypeId() + "=" + value.encodedValue(); }
		if (filter instanceof GroupFilter.ComponentPath) { GroupFilter.ComponentPath value = (GroupFilter.ComponentPath) filter; return "component path " + value.componentTypeId() + "/" + value.path() + "=" + value.expectedValue(); }
		return "unavailable " + ((GroupFilter.Unsupported) filter).recognizedKind();
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
		if (child instanceof GroupFilter.Any) return "ANY(...)";
		if (child instanceof GroupFilter.All) return "ALL(...)";
		if (child instanceof GroupFilter.Not) return "NOT(...)";
		return formatNode(child, false);
	}

	private static boolean isAtomic(GroupFilter filter) {
		return filter instanceof GroupFilter.Id
			|| filter instanceof GroupFilter.Tag
			|| filter instanceof GroupFilter.BlockTag
			|| filter instanceof GroupFilter.ItemPathStartsWith
			|| filter instanceof GroupFilter.ItemPathContains
			|| filter instanceof GroupFilter.ItemPathEndsWith
			|| filter instanceof GroupFilter.Namespace
			|| filter instanceof GroupFilter.ExactStack
			|| filter instanceof GroupFilter.Nbt
			|| filter instanceof GroupFilter.NbtPath
			|| filter instanceof GroupFilter.HasComponent
			|| filter instanceof GroupFilter.ComponentPath;
	}

	private static String categoryLabel(GroupFilter filter) {
		if (filter instanceof GroupFilter.Id) return categoryPrefix(((GroupFilter.Id) filter).ingredientType()) + "id";
		if (filter instanceof GroupFilter.Tag) return categoryPrefix(((GroupFilter.Tag) filter).ingredientType()) + "tag";
		if (filter instanceof GroupFilter.BlockTag) return "block tag";
		if (filter instanceof GroupFilter.ItemPathStartsWith) return "item path starts with";
		if (filter instanceof GroupFilter.ItemPathContains) return "item path contains";
		if (filter instanceof GroupFilter.ItemPathEndsWith) return "item path ends with";
		if (filter instanceof GroupFilter.Namespace) return categoryPrefix(((GroupFilter.Namespace) filter).ingredientType()) + "namespace";
		if (filter instanceof GroupFilter.ExactStack) return "exact stack";
		if (filter instanceof GroupFilter.Nbt) return "NBT";
		if (filter instanceof GroupFilter.NbtPath) return "NBT path";
		if (filter instanceof GroupFilter.HasComponent) return "has component";
		if (filter instanceof GroupFilter.ComponentPath) return "component path";
		return formatNode(filter, false);
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
