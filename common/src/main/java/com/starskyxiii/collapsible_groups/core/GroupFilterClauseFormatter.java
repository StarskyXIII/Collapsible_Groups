package com.starskyxiii.collapsible_groups.core;

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
		switch (filter) {
			case GroupFilter.Any any -> {
				clauses.add(new Clause(depth, "ANY", null));
				for (GroupFilter child : any.children()) {
					appendClauses(child, depth + 1, clauses);
				}
			}
			case GroupFilter.All all -> {
				clauses.add(new Clause(depth, "ALL", null));
				for (GroupFilter child : all.children()) {
					appendClauses(child, depth + 1, clauses);
				}
			}
			case GroupFilter.Not not -> {
				clauses.add(new Clause(depth, "NOT", null));
				appendClauses(not.child(), depth + 1, clauses);
			}
			case GroupFilter.Id id ->
				clauses.add(new Clause(depth, typedLabel(id.ingredientType(), "Id"), id.id()));
			case GroupFilter.Tag tag ->
				clauses.add(new Clause(depth, typedLabel(tag.ingredientType(), "Tag"), tag.tag()));
			case GroupFilter.BlockTag blockTag ->
				clauses.add(new Clause(depth, "Block Tag", blockTag.tag()));
			case GroupFilter.ItemPathStartsWith startsWith ->
				clauses.add(new Clause(depth, "Item Path Starts With", startsWith.prefix()));
			case GroupFilter.ItemPathEndsWith endsWith ->
				clauses.add(new Clause(depth, "Item Path Ends With", endsWith.suffix()));
			case GroupFilter.Namespace namespace ->
				clauses.add(new Clause(depth, typedLabel(namespace.ingredientType(), "Namespace"), namespace.namespace()));
			case GroupFilter.ExactStack stack ->
				clauses.add(new Clause(depth, "Exact Stack", stack.encodedStack()));
			case GroupFilter.HasComponent hc ->
				clauses.add(new Clause(depth, "Has Component", hc.componentTypeId() + " = " + hc.encodedValue()));
			case GroupFilter.ComponentPath cp ->
				clauses.add(new Clause(depth, "Component Path", cp.componentTypeId() + " / " + cp.path() + " = " + cp.expectedValue()));
		}
	}

	private static boolean hasSpecialClause(GroupFilter filter) {
		return switch (filter) {
			case GroupFilter.Id ignored -> false;
			case GroupFilter.ExactStack ignored -> false;
			case GroupFilter.HasComponent ignored -> true;
			case GroupFilter.ComponentPath ignored -> true;
			case GroupFilter.Any any -> any.children().stream().anyMatch(GroupFilterClauseFormatter::hasSpecialClause);
			case GroupFilter.Tag ignored -> true;
			case GroupFilter.BlockTag ignored -> true;
			case GroupFilter.ItemPathStartsWith ignored -> true;
			case GroupFilter.ItemPathEndsWith ignored -> true;
			case GroupFilter.Namespace ignored -> true;
			case GroupFilter.All ignored -> true;
			case GroupFilter.Not ignored -> true;
		};
	}

	private static String typedLabel(String ingredientType, String baseLabel) {
		return switch (ingredientType) {
			case ITEM_TYPE -> "Item " + baseLabel;
			case FLUID_TYPE -> "Fluid " + baseLabel;
			default -> ingredientType + " " + baseLabel;
		};
	}
}
