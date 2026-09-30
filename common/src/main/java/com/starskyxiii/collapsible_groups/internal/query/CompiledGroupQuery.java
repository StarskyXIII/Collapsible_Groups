package com.starskyxiii.collapsible_groups.internal.query;

import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;

import java.util.Objects;

public final class CompiledGroupQuery {
	private final GroupFilter source;
	private final CompiledFilter evaluator;
	private final QueryPlan plan;

	private CompiledGroupQuery(GroupFilter source, CompiledFilter evaluator, QueryPlan plan) {
		this.source = source;
		this.evaluator = evaluator;
		this.plan = plan;
	}

	public static CompiledGroupQuery compile(GroupFilter source) {
		return compile(source, source);
	}

	public static CompiledGroupQuery compile(GroupFilter source, GroupFilter planSource) {
		Objects.requireNonNull(source, "source");
		return new CompiledGroupQuery(source, CompiledFilter.compile(source), QueryPlan.compile(planSource));
	}

	public boolean matches(IngredientView view) {
		return evaluator.matches(view);
	}

	public CompiledFilter.Evaluation evaluate(IngredientView view) {
		return evaluator.evaluate(view);
	}

	public GroupFilter source() {
		return source;
	}

	public CompiledFilter evaluator() {
		return evaluator;
	}

	public QueryPlan plan() {
		return plan;
	}
}
