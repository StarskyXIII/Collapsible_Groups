package com.starskyxiii.collapsible_groups.client.manager.model;

import com.starskyxiii.collapsible_groups.client.manager.model.BatchActionEligibility;
import com.starskyxiii.collapsible_groups.client.manager.model.BatchSelectionState;
import com.starskyxiii.collapsible_groups.client.manager.model.EnabledPersistenceKind;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupAction;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupActionEligibility;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupCardViewModel;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupSource;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupActionAndBatchContractTest {
	@Test
	void classifiesGroupSourcesFromCurrentPrefixes() {
		assertEquals(GroupSource.BUILTIN, GroupSource.fromGroupId("__default_food"));
		assertEquals(GroupSource.KUBEJS, GroupSource.fromGroupId("__kjs_custom_pack"));
		assertEquals(GroupSource.USER, GroupSource.fromGroupId("custom_group"));
	}

	@Test
	void exposesUserActionMatrix() {
		GroupActionEligibility eligibility = GroupActionEligibility.forSource(GroupSource.USER);

		assertTrue(eligibility.canRequest(GroupAction.SWITCH_ENABLED));
		assertEquals(EnabledPersistenceKind.GROUP_JSON, eligibility.enabledPersistenceKind());
		assertFalse(eligibility.switchRequiresEnabledOverrideStore());
		assertTrue(eligibility.canRequest(GroupAction.EDIT));
		assertTrue(eligibility.canRequest(GroupAction.DELETE));
		assertTrue(eligibility.canRequest(GroupAction.SHIFT_DELETE));
		assertFalse(eligibility.canRequest(GroupAction.COPY_AS_CUSTOM));
		assertTrue(eligibility.canRequest(GroupAction.BATCH_SELECT));
		assertTrue(eligibility.canRequest(GroupAction.BATCH_ENABLE));
		assertTrue(eligibility.canRequest(GroupAction.BATCH_DISABLE));
		assertTrue(eligibility.canRequest(GroupAction.BATCH_DELETE));
	}

	@Test
	void exposesReadonlyActionMatrixWithEnabledOverridePersistence() {
		for (GroupSource source : List.of(GroupSource.BUILTIN, GroupSource.KUBEJS)) {
			GroupActionEligibility eligibility = GroupActionEligibility.forSource(source);

			assertTrue(eligibility.canRequest(GroupAction.SWITCH_ENABLED));
			assertEquals(EnabledPersistenceKind.ENABLED_OVERRIDE_STORE, eligibility.enabledPersistenceKind());
			assertTrue(eligibility.switchRequiresEnabledOverrideStore());
			assertFalse(eligibility.canRequest(GroupAction.EDIT));
			assertFalse(eligibility.canRequest(GroupAction.DELETE));
			assertFalse(eligibility.canRequest(GroupAction.SHIFT_DELETE));
			assertTrue(eligibility.canRequest(GroupAction.COPY_AS_CUSTOM));
			assertTrue(eligibility.canRequest(GroupAction.BATCH_SELECT));
			assertTrue(eligibility.canRequest(GroupAction.BATCH_ENABLE));
			assertTrue(eligibility.canRequest(GroupAction.BATCH_DISABLE));
			assertFalse(eligibility.canRequest(GroupAction.BATCH_DELETE));
		}
	}

	@Test
	void batchSelectionPreservesOrderAndDeduplicatesIds() {
		BatchSelectionState selection = new BatchSelectionState(List.of("alpha", "beta", "alpha"))
			.select("gamma")
			.toggle("beta")
			.toggle("delta");

		assertEquals(List.of("alpha", "gamma", "delta"), selection.selectedGroupIds());
		assertEquals(3, selection.selectedCount());
		assertTrue(selection.isSelected("gamma"));
		assertFalse(selection.isSelected("beta"));
		assertEquals(List.of(), selection.clear().selectedGroupIds());
	}

	@Test
	void batchSelectionPrunesToCurrentVisibleIdsWithoutReordering() {
		BatchSelectionState selection = new BatchSelectionState(List.of("alpha", "beta", "gamma", "delta"))
			.pruneTo(List.of("delta", "alpha", "gamma"));

		assertEquals(List.of("alpha", "gamma", "delta"), selection.selectedGroupIds());
		assertEquals(List.of(), selection.pruneTo(List.of()).selectedGroupIds());
	}

	@Test
	void batchSelectionSelectsAndClearsAllFilteredResults() {
		BatchSelectionState mixed = new BatchSelectionState(List.of("outside", "alpha"));
		BatchSelectionState all = mixed.selectAll(List.of("alpha", "beta", "gamma"));

		assertEquals(List.of("outside", "alpha", "beta", "gamma"), all.selectedGroupIds());
		assertTrue(all.containsAll(List.of("alpha", "beta", "gamma")));
		assertFalse(all.containsAll(List.of()));
		assertEquals(List.of("outside"), all.deselectAll(List.of("alpha", "beta", "gamma")).selectedGroupIds());
	}

	@Test
	void batchActionCountsEnabledDisabledAndReadonlyDeletes() {
		List<GroupCardViewModel> cards = List.of(
			card("custom_a", true),
			card("__default_food", false),
			card("__kjs_scripted", true)
		);

		BatchActionEligibility eligibility = BatchActionEligibility.fromCards(cards);

		assertEquals(3, eligibility.selectedCount());
		assertEquals(3, eligibility.switchRequestCandidateCount());
		assertEquals(1, eligibility.enableCandidateCount());
		assertEquals(2, eligibility.disableCandidateCount());
		assertEquals(1, eligibility.deletableCount());
		assertEquals(2, eligibility.readOnlyDeleteSkippedCount());
		assertTrue(eligibility.canEnable());
		assertTrue(eligibility.canDisable());
		assertTrue(eligibility.canDelete());
	}

	@Test
	void batchActionDisablesDeleteForReadonlyOnlySelection() {
		BatchActionEligibility eligibility = BatchActionEligibility.fromCards(List.of(
			card("__default_food", false),
			card("__kjs_scripted", true)
		));

		assertEquals(2, eligibility.selectedCount());
		assertEquals(2, eligibility.switchRequestCandidateCount());
		assertEquals(1, eligibility.enableCandidateCount());
		assertEquals(1, eligibility.disableCandidateCount());
		assertEquals(0, eligibility.deletableCount());
		assertEquals(2, eligibility.readOnlyDeleteSkippedCount());
		assertTrue(eligibility.canEnable());
		assertTrue(eligibility.canDisable());
		assertFalse(eligibility.canDelete());
	}

	@Test
	void batchActionOnlyEnablesApplicableMixedStates() {
		BatchActionEligibility allEnabled = BatchActionEligibility.fromCards(List.of(
			card("custom_a", true),
			card("__default_food", true)
		));
		assertFalse(allEnabled.canEnable());
		assertTrue(allEnabled.canDisable());
		assertEquals(0, allEnabled.enableCandidateCount());
		assertEquals(2, allEnabled.disableCandidateCount());

		BatchActionEligibility allDisabled = BatchActionEligibility.fromCards(List.of(
			card("custom_a", false),
			card("__default_food", false)
		));
		assertTrue(allDisabled.canEnable());
		assertFalse(allDisabled.canDisable());
		assertEquals(2, allDisabled.enableCandidateCount());
		assertEquals(0, allDisabled.disableCandidateCount());
	}

	private static GroupCardViewModel card(String groupId, boolean enabled) {
		return GroupCardViewModel.of(groupId, groupId, enabled, 0, 0, 0, List.of());
	}
}
