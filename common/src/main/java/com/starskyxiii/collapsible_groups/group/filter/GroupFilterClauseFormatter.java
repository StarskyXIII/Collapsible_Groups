package com.starskyxiii.collapsible_groups.group.filter;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only formatter that traverses a {@link GroupFilter} tree and produces a flat list of
 * depth-annotated {@link Clause} entries for indented display in the editor.
 */
public final class GroupFilterClauseFormatter {
	private static final String ITEM_TYPE = "item";
	private static final String FLUID_TYPE = "fluid";

	private GroupFilterClauseFormatter() {}

	public record Clause(int depth, String label, @Nullable String value) {}

	public static List<Clause> format(@Nullable GroupFilter filter) {
		if (filter == null) {
			return List.of();
		}

		List<Clause> clauses = new ArrayList<>();
		appendClauses(GroupFilterNormalizer.normalize(filter), 0, clauses);
		return List.copyOf(clauses);
	}

	public static boolean shouldDisplay(@Nullable GroupFilter filter) {
		if (filter == null) {
			return false;
		}

		return hasSpecialClause(GroupFilterNormalizer.normalize(filter));
	}

	private static void appendClauses(GroupFilter filter, int depth, List<Clause> clauses) {
		if (filter instanceof GroupFilter.Any) {
			GroupFilter.Any any = (GroupFilter.Any) filter;
				clauses.add(new Clause(depth, "ANY", null));
				for (GroupFilter child : any.children()) {
					appendClauses(child, depth + 1, clauses);
				}
		} else if (filter instanceof GroupFilter.All) {
			GroupFilter.All all = (GroupFilter.All) filter;
				clauses.add(new Clause(depth, "ALL", null));
				for (GroupFilter child : all.children()) {
					appendClauses(child, depth + 1, clauses);
				}
		} else if (filter instanceof GroupFilter.Not) {
			GroupFilter.Not not = (GroupFilter.Not) filter;
				clauses.add(new Clause(depth, "NOT", null));
				appendClauses(not.child(), depth + 1, clauses);
		} else if (filter instanceof GroupFilter.Id) {
			GroupFilter.Id value = (GroupFilter.Id) filter;
			clauses.add(new Clause(depth, typedLabel(value.ingredientType(), "Id"), value.id()));
		} else if (filter instanceof GroupFilter.Tag) {
			GroupFilter.Tag value = (GroupFilter.Tag) filter;
			clauses.add(new Clause(depth, typedLabel(value.ingredientType(), "Tag"), value.tag()));
		} else if (filter instanceof GroupFilter.BlockTag) {
			clauses.add(new Clause(depth, "Block Tag", ((GroupFilter.BlockTag) filter).tag()));
		} else if (filter instanceof GroupFilter.ItemPathStartsWith) {
			clauses.add(new Clause(depth, "Item Path Starts With", ((GroupFilter.ItemPathStartsWith) filter).prefix()));
		} else if (filter instanceof GroupFilter.ItemPathContains) {
			clauses.add(new Clause(depth, "Item Path Contains", ((GroupFilter.ItemPathContains) filter).needle()));
		} else if (filter instanceof GroupFilter.ItemPathEndsWith) {
			clauses.add(new Clause(depth, "Item Path Ends With", ((GroupFilter.ItemPathEndsWith) filter).suffix()));
		} else if (filter instanceof GroupFilter.Namespace) {
			GroupFilter.Namespace value = (GroupFilter.Namespace) filter;
			clauses.add(new Clause(depth, typedLabel(value.ingredientType(), "Namespace"), value.namespace()));
		} else if (filter instanceof GroupFilter.ExactStack) {
			clauses.add(new Clause(depth, "Exact Stack", ((GroupFilter.ExactStack) filter).encodedStack()));
		} else if (filter instanceof GroupFilter.HasComponent) {
			GroupFilter.HasComponent value = (GroupFilter.HasComponent) filter;
			clauses.add(new Clause(depth, "Has Component", value.componentTypeId() + " = " + value.encodedValue()));
		} else if (filter instanceof GroupFilter.ComponentPath) {
			GroupFilter.ComponentPath value = (GroupFilter.ComponentPath) filter;
			clauses.add(new Clause(depth, "Component Path", value.componentTypeId() + " / " + value.path() + " = " + value.expectedValue()));
		} else {
			clauses.add(new Clause(depth, "Unavailable Rule", ((GroupFilter.Unsupported) filter).recognizedKind()));
		}
	}

	private static boolean hasSpecialClause(GroupFilter filter) {
		if (filter instanceof GroupFilter.Id || filter instanceof GroupFilter.ExactStack) return false;
		if (filter instanceof GroupFilter.Any) return ((GroupFilter.Any) filter).children().stream().anyMatch(GroupFilterClauseFormatter::hasSpecialClause);
		return true;
	}

	private static String typedLabel(String ingredientType, String baseLabel) {
		return switch (ingredientType) {
			case ITEM_TYPE -> "Item " + baseLabel;
			case FLUID_TYPE -> "Fluid " + baseLabel;
			default -> ingredientType + " " + baseLabel;
		};
	}
}
