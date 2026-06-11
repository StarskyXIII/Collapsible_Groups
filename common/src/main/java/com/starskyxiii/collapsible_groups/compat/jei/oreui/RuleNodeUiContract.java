package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record RuleNodeUiContract(
	GroupFilterRuleDraft.NodeKind kind,
	boolean compound,
	int draftMinChildren,
	int validMinChildren,
	int maxChildren,
	boolean canAddFilter,
	boolean canAddGroup,
	List<RuleFieldRole> fieldRoles,
	RuleFixedOperator fixedOperator
) {
	public RuleNodeUiContract {
		kind = Objects.requireNonNull(kind, "kind");
		requireNonNegative(draftMinChildren, "draftMinChildren");
		requireNonNegative(validMinChildren, "validMinChildren");
		requireNonNegative(maxChildren, "maxChildren");
		fieldRoles = List.copyOf(Objects.requireNonNull(fieldRoles, "fieldRoles"));
		fixedOperator = Objects.requireNonNull(fixedOperator, "fixedOperator");
	}

	public static RuleNodeUiContract forKind(GroupFilterRuleDraft.NodeKind kind) {
		return switch (Objects.requireNonNull(kind, "kind")) {
			case ANY, ALL -> compound(kind, kind.minChildren(), kind.minChildren(), kind.maxChildren());
			case NOT -> compound(kind, kind.minChildren(), 1, kind.maxChildren());
			case ID, TAG, NAMESPACE -> atomic(kind, RuleFixedOperator.NONE, RuleFieldRole.INGREDIENT_TYPE, RuleFieldRole.PRIMARY_VALUE);
			case BLOCK_TAG -> atomic(kind, RuleFixedOperator.NONE, RuleFieldRole.PRIMARY_VALUE);
			case ITEM_PATH_STARTS_WITH -> atomic(kind, RuleFixedOperator.ITEM_PATH_STARTS_WITH, RuleFieldRole.PRIMARY_VALUE);
			case ITEM_PATH_CONTAINS -> atomic(kind, RuleFixedOperator.ITEM_PATH_CONTAINS, RuleFieldRole.PRIMARY_VALUE);
			case ITEM_PATH_ENDS_WITH -> atomic(kind, RuleFixedOperator.ITEM_PATH_ENDS_WITH, RuleFieldRole.PRIMARY_VALUE);
			case EXACT_STACK -> atomic(kind, RuleFixedOperator.NONE, RuleFieldRole.PRIMARY_VALUE);
			case HAS_COMPONENT -> atomic(kind, RuleFixedOperator.NONE, RuleFieldRole.PRIMARY_VALUE, RuleFieldRole.SECONDARY_VALUE);
			case COMPONENT_PATH -> atomic(
				kind,
				RuleFixedOperator.NONE,
				RuleFieldRole.PRIMARY_VALUE,
				RuleFieldRole.SECONDARY_VALUE,
				RuleFieldRole.TERTIARY_VALUE
			);
		};
	}

	public static List<RuleNodeUiContract> all() {
		return Arrays.stream(GroupFilterRuleDraft.NodeKind.values())
			.map(RuleNodeUiContract::forKind)
			.toList();
	}

	public boolean canAddChild(int childCount) {
		requireNonNegative(childCount, "childCount");
		return compound && childCount < maxChildren;
	}

	public boolean validChildCount(int childCount) {
		requireNonNegative(childCount, "childCount");
		return childCount >= validMinChildren && childCount <= maxChildren;
	}

	public boolean exposesField(RuleFieldRole role) {
		return fieldRoles.contains(Objects.requireNonNull(role, "role"));
	}

	private static RuleNodeUiContract compound(
		GroupFilterRuleDraft.NodeKind kind,
		int draftMinChildren,
		int validMinChildren,
		int maxChildren
	) {
		return new RuleNodeUiContract(
			kind,
			true,
			draftMinChildren,
			validMinChildren,
			maxChildren,
			true,
			true,
			List.of(),
			RuleFixedOperator.NONE
		);
	}

	private static RuleNodeUiContract atomic(
		GroupFilterRuleDraft.NodeKind kind,
		RuleFixedOperator fixedOperator,
		RuleFieldRole... fieldRoles
	) {
		return new RuleNodeUiContract(
			kind,
			false,
			0,
			0,
			0,
			false,
			false,
			List.of(fieldRoles),
			fixedOperator
		);
	}

	private static void requireNonNegative(int value, String name) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " must be non-negative");
		}
	}
}
