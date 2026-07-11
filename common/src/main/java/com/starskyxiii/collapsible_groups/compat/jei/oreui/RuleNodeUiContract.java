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
	List<RuleFieldRole> requiredRoles,
	RuleFixedOperator fixedOperator
) {
	public RuleNodeUiContract {
		kind = Objects.requireNonNull(kind, "kind");
		requireNonNegative(draftMinChildren, "draftMinChildren");
		requireNonNegative(validMinChildren, "validMinChildren");
		requireNonNegative(maxChildren, "maxChildren");
		fieldRoles = List.copyOf(Objects.requireNonNull(fieldRoles, "fieldRoles"));
		requiredRoles = List.copyOf(Objects.requireNonNull(requiredRoles, "requiredRoles"));
		fixedOperator = Objects.requireNonNull(fixedOperator, "fixedOperator");
	}

	/**
	 * Fields whose blank value must block confirmation. Semantics are aligned with
	 * {@link com.starskyxiii.collapsible_groups.core.GroupFilterValidator}'s blank checks
	 * (see {@code RuleNodeUiContractRequiredRolesTest} for the cross-check), so every
	 * atomic kind's required set here must match the validator's blank-value errors —
	 * including {@code HAS_COMPONENT}'s {@code encodedValue} (SECONDARY_VALUE), which is
	 * required even though its picker/form UX otherwise treats it like a free-form field.
	 */
	public static RuleNodeUiContract forKind(GroupFilterRuleDraft.NodeKind kind) {
		return switch (Objects.requireNonNull(kind, "kind")) {
			case ANY, ALL -> compound(kind, kind.minChildren(), kind.minChildren(), kind.maxChildren());
			case NOT -> compound(kind, kind.minChildren(), 1, kind.maxChildren());
			case ID, TAG, NAMESPACE -> atomic(kind, RuleFixedOperator.NONE,
				List.of(RuleFieldRole.INGREDIENT_TYPE, RuleFieldRole.PRIMARY_VALUE),
				List.of(RuleFieldRole.PRIMARY_VALUE));
			case BLOCK_TAG -> atomic(kind, RuleFixedOperator.NONE,
				List.of(RuleFieldRole.PRIMARY_VALUE),
				List.of(RuleFieldRole.PRIMARY_VALUE));
			case ITEM_PATH_STARTS_WITH -> atomic(kind, RuleFixedOperator.ITEM_PATH_STARTS_WITH,
				List.of(RuleFieldRole.PRIMARY_VALUE),
				List.of(RuleFieldRole.PRIMARY_VALUE));
			case ITEM_PATH_CONTAINS -> atomic(kind, RuleFixedOperator.ITEM_PATH_CONTAINS,
				List.of(RuleFieldRole.PRIMARY_VALUE),
				List.of(RuleFieldRole.PRIMARY_VALUE));
			case ITEM_PATH_ENDS_WITH -> atomic(kind, RuleFixedOperator.ITEM_PATH_ENDS_WITH,
				List.of(RuleFieldRole.PRIMARY_VALUE),
				List.of(RuleFieldRole.PRIMARY_VALUE));
			case EXACT_STACK -> atomic(kind, RuleFixedOperator.NONE,
				List.of(RuleFieldRole.PRIMARY_VALUE),
				List.of(RuleFieldRole.PRIMARY_VALUE));
			case HAS_COMPONENT -> atomic(kind, RuleFixedOperator.NONE,
				List.of(RuleFieldRole.PRIMARY_VALUE, RuleFieldRole.SECONDARY_VALUE),
				List.of(RuleFieldRole.PRIMARY_VALUE, RuleFieldRole.SECONDARY_VALUE));
			case COMPONENT_PATH -> atomic(
				kind,
				RuleFixedOperator.NONE,
				List.of(RuleFieldRole.PRIMARY_VALUE, RuleFieldRole.SECONDARY_VALUE, RuleFieldRole.TERTIARY_VALUE),
				List.of(RuleFieldRole.PRIMARY_VALUE, RuleFieldRole.SECONDARY_VALUE, RuleFieldRole.TERTIARY_VALUE)
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

	/** Whether {@code role} must be non-blank before this node's editor may confirm. */
	public boolean requiresField(RuleFieldRole role) {
		return requiredRoles.contains(Objects.requireNonNull(role, "role"));
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
			List.of(),
			RuleFixedOperator.NONE
		);
	}

	private static RuleNodeUiContract atomic(
		GroupFilterRuleDraft.NodeKind kind,
		RuleFixedOperator fixedOperator,
		List<RuleFieldRole> fieldRoles,
		List<RuleFieldRole> requiredRoles
	) {
		return new RuleNodeUiContract(
			kind,
			false,
			0,
			0,
			0,
			false,
			false,
			fieldRoles,
			requiredRoles,
			fixedOperator
		);
	}

	private static void requireNonNegative(int value, String name) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " must be non-negative");
		}
	}
}
