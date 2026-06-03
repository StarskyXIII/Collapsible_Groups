package com.starskyxiii.collapsible_groups.compat.jei.oreui;

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
	void exposesReadonlyActionMatrixWithoutClaimingSwitchPersistenceIsImplemented() {
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

	private static GroupCardViewModel card(String groupId, boolean enabled) {
		return GroupCardViewModel.of(groupId, groupId, enabled, 0, 0, 0, List.of());
	}
}
