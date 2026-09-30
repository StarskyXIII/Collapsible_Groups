package com.starskyxiii.collapsible_groups.client.editor.model;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.group.filter.RuleDescriptor;

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
	 * {@link com.starskyxiii.collapsible_groups.group.filter.GroupFilterValidator}'s blank checks
	 * (see {@code RuleNodeUiContractRequiredRolesTest} for the cross-check), so every
	 * atomic kind's required set here must match the validator's blank-value errors —
	 * including {@code HAS_COMPONENT}'s {@code encodedValue} (SECONDARY_VALUE), which is
	 * required even though its picker/form UX otherwise treats it like a free-form field.
	 */
	public static RuleNodeUiContract forKind(GroupFilterRuleDraft.NodeKind kind) {
		Objects.requireNonNull(kind, "kind");
		RuleDescriptor descriptor = RuleDescriptor.forKind(kind.filterKind());
		List<RuleFieldRole> fieldRoles = descriptor.fieldRoles().stream()
			.map(RuleNodeUiContract::toUiRole)
			.toList();
		List<RuleFieldRole> requiredRoles = descriptor.fieldRoles().stream()
			.filter(descriptor.requiredRoles()::contains)
			.map(RuleNodeUiContract::toUiRole)
			.toList();
		return new RuleNodeUiContract(kind, descriptor.compound(), descriptor.draftMinChildren(),
			descriptor.validMinChildren(), descriptor.maxChildren(), descriptor.compound(), descriptor.compound(),
			fieldRoles, requiredRoles, fixedOperator(kind));
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

	private static RuleFieldRole toUiRole(RuleDescriptor.FieldRole role) {
		return switch (role) {
			case INGREDIENT_TYPE -> RuleFieldRole.INGREDIENT_TYPE;
			case PRIMARY_VALUE -> RuleFieldRole.PRIMARY_VALUE;
			case SECONDARY_VALUE -> RuleFieldRole.SECONDARY_VALUE;
			case TERTIARY_VALUE -> RuleFieldRole.TERTIARY_VALUE;
		};
	}

	private static RuleFixedOperator fixedOperator(GroupFilterRuleDraft.NodeKind kind) {
		return switch (kind) {
			case ITEM_PATH_STARTS_WITH -> RuleFixedOperator.ITEM_PATH_STARTS_WITH;
			case ITEM_PATH_CONTAINS -> RuleFixedOperator.ITEM_PATH_CONTAINS;
			case ITEM_PATH_ENDS_WITH -> RuleFixedOperator.ITEM_PATH_ENDS_WITH;
			default -> RuleFixedOperator.NONE;
		};
	}

	private static void requireNonNegative(int value, String name) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " must be non-negative");
		}
	}
}
