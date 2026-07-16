package com.starskyxiii.collapsible_groups.client.editor.model;

import com.starskyxiii.collapsible_groups.client.editor.model.RuleFieldRole;
import com.starskyxiii.collapsible_groups.client.editor.model.RuleFixedOperator;
import com.starskyxiii.collapsible_groups.client.editor.model.RuleNodeUiContract;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterValidator;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
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

	// ─────────────────────────────────────────────────────────────────────
	// requiredRoles ↔ GroupFilterValidator cross-check
	//
	// For every atomic kind, blanking a role the contract marks as required must
	// produce at least one GroupFilterValidator error on an otherwise-valid filter;
	// blanking INGREDIENT_TYPE (never required — the picker/form always defaults it)
	// must not.
	// ─────────────────────────────────────────────────────────────────────

	@Test
	void requiredRolesBlankingIdProducesValidatorError() {
		assertBlankingRoleFails(GroupFilterRuleDraft.NodeKind.ID, RuleFieldRole.PRIMARY_VALUE,
			new GroupFilter.Id("item", "minecraft:stone"), new GroupFilter.Id("item", ""));
	}

	@Test
	void requiredRolesBlankingTagProducesValidatorError() {
		assertBlankingRoleFails(GroupFilterRuleDraft.NodeKind.TAG, RuleFieldRole.PRIMARY_VALUE,
			new GroupFilter.Tag("item", "c:ingots"), new GroupFilter.Tag("item", ""));
	}

	@Test
	void requiredRolesBlankingBlockTagProducesValidatorError() {
		assertBlankingRoleFails(GroupFilterRuleDraft.NodeKind.BLOCK_TAG, RuleFieldRole.PRIMARY_VALUE,
			new GroupFilter.BlockTag("minecraft:stone"), new GroupFilter.BlockTag(""));
	}

	@Test
	void requiredRolesBlankingItemPathStartsWithProducesValidatorError() {
		assertBlankingRoleFails(GroupFilterRuleDraft.NodeKind.ITEM_PATH_STARTS_WITH, RuleFieldRole.PRIMARY_VALUE,
			new GroupFilter.ItemPathStartsWith("iron"), new GroupFilter.ItemPathStartsWith(""));
	}

	@Test
	void requiredRolesBlankingItemPathContainsProducesValidatorError() {
		assertBlankingRoleFails(GroupFilterRuleDraft.NodeKind.ITEM_PATH_CONTAINS, RuleFieldRole.PRIMARY_VALUE,
			new GroupFilter.ItemPathContains("iron"), new GroupFilter.ItemPathContains(""));
	}

	@Test
	void requiredRolesBlankingItemPathEndsWithProducesValidatorError() {
		assertBlankingRoleFails(GroupFilterRuleDraft.NodeKind.ITEM_PATH_ENDS_WITH, RuleFieldRole.PRIMARY_VALUE,
			new GroupFilter.ItemPathEndsWith("ingot"), new GroupFilter.ItemPathEndsWith(""));
	}

	/**
	 * NAMESPACE is a deliberate exception to the "requiredRoles mirrors validator 1:1"
	 * rule: {@code GroupFilterValidator}'s Namespace case only calls
	 * {@code ResourceLocation.isValidNamespace(...)}, and that check is vacuously true
	 * for an empty string (its char-by-char loop never runs), so the validator does not
	 * currently flag a blank namespace. requiredRoles still marks PRIMARY_VALUE as
	 * required here — an empty namespace field is a clear UX mistake even though the
	 * validator doesn't (yet) catch it — so this documents the gap rather than asserting
	 * a validator error that doesn't exist.
	 */
	@Test
	void requiredRolesNamespaceIsRequiredDespiteValidatorGapOnBlankString() {
		RuleNodeUiContract contract = RuleNodeUiContract.forKind(GroupFilterRuleDraft.NodeKind.NAMESPACE);
		assertTrue(contract.requiresField(RuleFieldRole.PRIMARY_VALUE));

		assertTrue(GroupFilterValidator.validateComponents(new GroupFilter.Namespace("item", "minecraft")).isEmpty());
		// Documents the validator gap: blank namespace passes GroupFilterValidator today.
		assertTrue(GroupFilterValidator.validateComponents(new GroupFilter.Namespace("item", "")).isEmpty());
	}

	@Test
	void requiredRolesNamespaceIngredientTypeIsNotRequired() {
		RuleNodeUiContract contract = RuleNodeUiContract.forKind(GroupFilterRuleDraft.NodeKind.NAMESPACE);
		assertFalse(contract.requiresField(RuleFieldRole.INGREDIENT_TYPE));
	}

	@Test
	void requiredRolesBlankingExactStackProducesValidatorError() {
		assertBlankingRoleFails(GroupFilterRuleDraft.NodeKind.EXACT_STACK, RuleFieldRole.PRIMARY_VALUE,
			new GroupFilter.ExactStack("{}"), new GroupFilter.ExactStack(""));
	}

	@Test
	void requiredRolesBlankingHasComponentTypeIdProducesValidatorError() {
		assertBlankingRoleFails(GroupFilterRuleDraft.NodeKind.HAS_COMPONENT, RuleFieldRole.PRIMARY_VALUE,
			new GroupFilter.HasComponent("minecraft:custom_name", "\"Boat\""),
			new GroupFilter.HasComponent("", "\"Boat\""));
	}

	/**
	 * Front-review condition 2's key trap: HAS_COMPONENT's encodedValue (SECONDARY_VALUE)
	 * is also required by the validator, even though it reads like a free-form value field.
	 */
	@Test
	void requiredRolesBlankingHasComponentEncodedValueProducesValidatorError() {
		assertBlankingRoleFails(GroupFilterRuleDraft.NodeKind.HAS_COMPONENT, RuleFieldRole.SECONDARY_VALUE,
			new GroupFilter.HasComponent("minecraft:custom_name", "\"Boat\""),
			new GroupFilter.HasComponent("minecraft:custom_name", ""));
	}

	@Test
	void requiredRolesBlankingComponentPathTypeIdProducesValidatorError() {
		assertBlankingRoleFails(GroupFilterRuleDraft.NodeKind.COMPONENT_PATH, RuleFieldRole.PRIMARY_VALUE,
			new GroupFilter.ComponentPath("minecraft:food", "nutrition", "4"),
			new GroupFilter.ComponentPath("", "nutrition", "4"));
	}

	@Test
	void requiredRolesBlankingComponentPathPathProducesValidatorError() {
		assertBlankingRoleFails(GroupFilterRuleDraft.NodeKind.COMPONENT_PATH, RuleFieldRole.SECONDARY_VALUE,
			new GroupFilter.ComponentPath("minecraft:food", "nutrition", "4"),
			new GroupFilter.ComponentPath("minecraft:food", "", "4"));
	}

	@Test
	void requiredRolesBlankingComponentPathExpectedValueProducesValidatorError() {
		assertBlankingRoleFails(GroupFilterRuleDraft.NodeKind.COMPONENT_PATH, RuleFieldRole.TERTIARY_VALUE,
			new GroupFilter.ComponentPath("minecraft:food", "nutrition", "4"),
			new GroupFilter.ComponentPath("minecraft:food", "nutrition", ""));
	}

	@Test
	void everyAtomicKindsRequiredRolesAreSubsetOfExposedFields() {
		for (RuleNodeUiContract contract : RuleNodeUiContract.all()) {
			for (RuleFieldRole role : contract.requiredRoles()) {
				assertTrue(contract.exposesField(role),
					contract.kind() + " requires " + role + " but does not expose it");
			}
		}
	}

	/**
	 * Confirms the contract's requiredRoles() for {@code kind} matches
	 * GroupFilterValidator's blank-value semantics: the valid baseline filter produces
	 * no errors, and blanking the field at {@code role} on that same baseline produces
	 * at least one error (and requiresField(role) is true).
	 */
	private static void assertBlankingRoleFails(
		GroupFilterRuleDraft.NodeKind kind,
		RuleFieldRole role,
		GroupFilter validBaseline,
		GroupFilter blanked
	) {
		RuleNodeUiContract contract = RuleNodeUiContract.forKind(kind);
		assertTrue(contract.requiresField(role), kind + " should require " + role);
		// validateComponents() (not validate(), which calls Component#getString and needs
		// a loaded Language instance unavailable in the common test sourceSet) is used here,
		// same as the existing GroupFilterValidatorTest.
		assertTrue(GroupFilterValidator.validateComponents(validBaseline).isEmpty(), "baseline should be valid");
		List<Component> errors = GroupFilterValidator.validateComponents(blanked);
		assertFalse(errors.isEmpty(), "blanking " + role + " on " + kind + " should produce a validator error");
	}
}
