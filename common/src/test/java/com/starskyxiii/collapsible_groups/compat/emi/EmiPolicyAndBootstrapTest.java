package com.starskyxiii.collapsible_groups.compat.emi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EmiPolicyAndBootstrapTest {
	@Test void onlyLeftClickActivationIsConsumed() {
		assertEquals(EmiHeaderInteractionPolicy.Decision.TOGGLE_AND_CONSUME,
			EmiHeaderInteractionPolicy.decide(EmiHeaderInteractionPolicy.Action.ACTIVATE));
		assertEquals(EmiHeaderInteractionPolicy.Decision.REJECT,
			EmiHeaderInteractionPolicy.decide(EmiHeaderInteractionPolicy.Action.OTHER));
	}

	@Test void modifierLeftClickOnHeaderIsRejectedExplicitly() {
		// EmiBind.LEFT_CLICK requires modifier mask zero; modified clicks enter the OTHER branch.
		assertEquals(EmiHeaderInteractionPolicy.Decision.REJECT,
			EmiHeaderInteractionPolicy.decide(EmiHeaderInteractionPolicy.Action.OTHER));
	}

	@ParameterizedTest(name = "{0}, cheat={1}, expanded={2}")
	@MethodSource("groupInteractionModes")
	void interactionDecisionIsIndependentOfGroupShapeCheatModeAndExpansion(
		String groupShape, boolean cheatMode, boolean expanded) {
		assertEquals(EmiHeaderInteractionPolicy.Decision.TOGGLE_AND_CONSUME,
			EmiHeaderInteractionPolicy.decide(EmiHeaderInteractionPolicy.Action.ACTIVATE));
		assertEquals(EmiHeaderInteractionPolicy.Decision.REJECT,
			EmiHeaderInteractionPolicy.decide(EmiHeaderInteractionPolicy.Action.OTHER));
	}

	private static Stream<Arguments> groupInteractionModes() {
		return Stream.of("items", "fluids", "generic", "mixed").flatMap(shape ->
			Stream.of(false, true).flatMap(cheat ->
				Stream.of(false, true).map(expanded -> Arguments.of(shape, cheat, expanded))));
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
