package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleNodeUiContractTest {
	@Test
	void coversEveryRuleNodeKind() {
		Set<GroupFilterRuleDraft.NodeKind> covered = RuleNodeUiContract.all().stream()
			.map(RuleNodeUiContract::kind)
			.collect(Collectors.toSet());

		assertEquals(Set.of(GroupFilterRuleDraft.NodeKind.values()), covered);
	}

	@Test
	void notAllowsIncompleteDraftButRequiresExactlyOneValidChild() {
		RuleNodeUiContract not = RuleNodeUiContract.forKind(GroupFilterRuleDraft.NodeKind.NOT);

		assertTrue(not.compound());
		assertEquals(0, not.draftMinChildren());
		assertEquals(1, not.validMinChildren());
		assertEquals(1, not.maxChildren());
		assertTrue(not.canAddChild(0));
		assertFalse(not.canAddChild(1));
		assertFalse(not.validChildCount(0));
		assertTrue(not.validChildCount(1));
		assertFalse(not.validChildCount(2));
	}

	@Test
	void compoundNodesExposeChildInsertionContracts() {
		for (GroupFilterRuleDraft.NodeKind kind : Set.of(GroupFilterRuleDraft.NodeKind.ANY, GroupFilterRuleDraft.NodeKind.ALL)) {
			RuleNodeUiContract contract = RuleNodeUiContract.forKind(kind);

			assertTrue(contract.compound());
			assertTrue(contract.canAddFilter());
			assertTrue(contract.canAddGroup());
			assertTrue(contract.canAddChild(0));
			assertFalse(contract.validChildCount(0));
			assertTrue(contract.validChildCount(1));
			assertEquals(RuleFixedOperator.NONE, contract.fixedOperator());
			assertEquals(Set.of(), Set.copyOf(contract.fieldRoles()));
		}
	}

	@Test
	void atomicFieldRolesMatchCurrentFilterDraftFields() {
		RuleNodeUiContract id = RuleNodeUiContract.forKind(GroupFilterRuleDraft.NodeKind.ID);
		assertTrue(id.exposesField(RuleFieldRole.INGREDIENT_TYPE));
		assertTrue(id.exposesField(RuleFieldRole.PRIMARY_VALUE));

		RuleNodeUiContract blockTag = RuleNodeUiContract.forKind(GroupFilterRuleDraft.NodeKind.BLOCK_TAG);
		assertFalse(blockTag.exposesField(RuleFieldRole.INGREDIENT_TYPE));
		assertTrue(blockTag.exposesField(RuleFieldRole.PRIMARY_VALUE));

		RuleNodeUiContract componentPath = RuleNodeUiContract.forKind(GroupFilterRuleDraft.NodeKind.COMPONENT_PATH);
		assertTrue(componentPath.exposesField(RuleFieldRole.PRIMARY_VALUE));
		assertTrue(componentPath.exposesField(RuleFieldRole.SECONDARY_VALUE));
		assertTrue(componentPath.exposesField(RuleFieldRole.TERTIARY_VALUE));

		RuleNodeUiContract hasComponent = RuleNodeUiContract.forKind(GroupFilterRuleDraft.NodeKind.HAS_COMPONENT);
		assertTrue(hasComponent.exposesField(RuleFieldRole.PRIMARY_VALUE));
		assertTrue(hasComponent.exposesField(RuleFieldRole.SECONDARY_VALUE));
		assertFalse(hasComponent.exposesField(RuleFieldRole.TERTIARY_VALUE));
	}

	@Test
	void itemPathContractsExposeFixedOperators() {
		assertPathOperator(
			GroupFilterRuleDraft.NodeKind.ITEM_PATH_STARTS_WITH,
			RuleFixedOperator.ITEM_PATH_STARTS_WITH
		);
		assertPathOperator(
			GroupFilterRuleDraft.NodeKind.ITEM_PATH_CONTAINS,
			RuleFixedOperator.ITEM_PATH_CONTAINS
		);
		assertPathOperator(
			GroupFilterRuleDraft.NodeKind.ITEM_PATH_ENDS_WITH,
			RuleFixedOperator.ITEM_PATH_ENDS_WITH
		);
	}

	private static void assertPathOperator(GroupFilterRuleDraft.NodeKind kind, RuleFixedOperator operator) {
		RuleNodeUiContract contract = RuleNodeUiContract.forKind(kind);

		assertEquals(operator, contract.fixedOperator());
		assertTrue(contract.fixedOperator().present());
		assertEquals(Set.of(RuleFieldRole.PRIMARY_VALUE), Set.copyOf(contract.fieldRoles()));
	}
}
