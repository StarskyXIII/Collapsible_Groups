package com.starskyxiii.collapsible_groups.compat.emi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmiPolicyAndBootstrapTest {
	@Test void activationIsConsumedAndOtherActionsAreRejected() {
		assertEquals(EmiHeaderInteractionPolicy.Decision.TOGGLE_AND_CONSUME,
			EmiHeaderInteractionPolicy.decide(EmiHeaderInteractionPolicy.Action.ACTIVATE));
		assertEquals(EmiHeaderInteractionPolicy.Decision.REJECT,
			EmiHeaderInteractionPolicy.decide(EmiHeaderInteractionPolicy.Action.OTHER));
	}

	@Test void bootstrapIsClaimedOncePerDirtyLoadedIndexGeneration() {
		EmiBootstrapGate gate = new EmiBootstrapGate();
		gate.markDirty();
		assertFalse(gate.tryClaim(false, true));
		assertFalse(gate.tryClaim(true, false));
		assertTrue(gate.tryClaim(true, true));
		assertFalse(gate.tryClaim(true, true));
		gate.complete();
		assertTrue(gate.ready());
		gate.markDirty();
		assertFalse(gate.ready());
		assertTrue(gate.tryClaim(true, true));
	}

	@Test void ownershipAndEditorDisplayUseTheRequiredDistinctEmiSources() {
		List<String> stable = new ArrayList<>();
		List<String> visible = new ArrayList<>();
		EmiIndexSources.Sources<String> sources = EmiIndexSources.from(stable, visible);
		assertSame(stable, sources.ownership());
		assertSame(visible, sources.editorDisplay());
	}
}
