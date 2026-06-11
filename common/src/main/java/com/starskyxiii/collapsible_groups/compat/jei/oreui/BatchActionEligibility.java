package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import java.util.Objects;

public record BatchActionEligibility(
	int selectedCount,
	int switchRequestCandidateCount,
	int enableCandidateCount,
	int disableCandidateCount,
	int deletableCount,
	int readOnlyDeleteSkippedCount,
	boolean canEnable,
	boolean canDisable,
	boolean canDelete
) {
	public BatchActionEligibility {
		requireNonNegative(selectedCount, "selectedCount");
		requireNonNegative(switchRequestCandidateCount, "switchRequestCandidateCount");
		requireNonNegative(enableCandidateCount, "enableCandidateCount");
		requireNonNegative(disableCandidateCount, "disableCandidateCount");
		requireNonNegative(deletableCount, "deletableCount");
		requireNonNegative(readOnlyDeleteSkippedCount, "readOnlyDeleteSkippedCount");
	}

	public static BatchActionEligibility fromCards(Iterable<GroupCardViewModel> selectedCards) {
		Objects.requireNonNull(selectedCards, "selectedCards");
		int selected = 0;
		int switchCandidates = 0;
		int enableCandidates = 0;
		int disableCandidates = 0;
		int deletable = 0;
		int skipped = 0;

		for (GroupCardViewModel card : selectedCards) {
			GroupCardViewModel resolved = Objects.requireNonNull(card, "card");
			GroupActionEligibility eligibility = resolved.actionEligibility();
			selected++;
			boolean canRequestBatchEnable = eligibility.canBatchRequestEnable();
			boolean canRequestBatchDisable = eligibility.canBatchRequestDisable();
			if (canRequestBatchEnable || canRequestBatchDisable) {
				switchCandidates++;
				if (!resolved.enabled() && canRequestBatchEnable) {
					enableCandidates++;
				}
				if (resolved.enabled() && canRequestBatchDisable) {
					disableCandidates++;
				}
			}
			if (eligibility.canBatchDelete()) {
				deletable++;
			} else {
				skipped++;
			}
		}

		return new BatchActionEligibility(
			selected,
			switchCandidates,
			enableCandidates,
			disableCandidates,
			deletable,
			skipped,
			enableCandidates > 0,
			disableCandidates > 0,
			deletable > 0
		);
	}

	private static void requireNonNegative(int value, String name) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " must be non-negative");
		}
	}
}
