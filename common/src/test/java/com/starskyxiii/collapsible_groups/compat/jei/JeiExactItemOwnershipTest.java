package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JeiExactItemOwnershipTest {
	@Test
	void sharedRegistryItemVariantsKeepDifferentOwners() {
		FakeStack firstGem = new FakeStack("apotheosis:gem", "first@perfect");
		FakeStack secondGem = new FakeStack("apotheosis:gem", "second@perfect");
		ViewerIngredientIdentity firstIdentity = identity(firstGem.uid);
		ViewerIngredientIdentity secondIdentity = identity(secondGem.uid);
		Map<ViewerIngredientIdentity, String> owners = Map.of(
			firstIdentity, "first_group",
			secondIdentity, "second_group");
		Map<FakeStack, ViewerIngredientIdentity> canonical = new IdentityHashMap<>();
		canonical.put(firstGem, firstIdentity);
		canonical.put(secondGem, secondIdentity);

		Map<FakeStack, String> result = JeiViewerGroupIndex.resolveExactOwnership(
			List.of(firstGem, secondGem), owners, canonical, ignored -> Optional.empty());

		assertEquals("first_group", result.get(firstGem));
		assertEquals("second_group", result.get(secondGem));
	}

	@Test
	void copiedEquivalentStackFallsBackToItsExactJeiUid() {
		FakeStack canonicalStack = new FakeStack("apotheosis:gem", "bloody_pearl@flawless");
		FakeStack copiedStack = new FakeStack("apotheosis:gem", "bloody_pearl@flawless");
		ViewerIngredientIdentity identity = identity(canonicalStack.uid);
		Map<FakeStack, ViewerIngredientIdentity> canonical = new IdentityHashMap<>();
		canonical.put(canonicalStack, identity);

		Map<FakeStack, String> result = JeiViewerGroupIndex.resolveExactOwnership(
			List.of(copiedStack), Map.of(identity, "bloody_pearl"), canonical,
			stack -> Optional.of(identity(stack.uid)));

		assertEquals("bloody_pearl", result.get(copiedStack));
	}

	@Test
	void missingExactUidStaysUnownedInsteadOfFallingBackToRegistryItem() {
		FakeStack unknownGem = new FakeStack("apotheosis:gem", null);

		Map<FakeStack, String> result = JeiViewerGroupIndex.resolveExactOwnership(
			List.of(unknownGem), Map.of(), new IdentityHashMap<>(), ignored -> Optional.empty());

		assertFalse(result.containsKey(unknownGem));
	}

	private static ViewerIngredientIdentity identity(String uid) {
		return new ViewerIngredientIdentity("item", uid, uid);
	}

	private static final class FakeStack {
		private final String itemId;
		private final String uid;

		private FakeStack(String itemId, String uid) {
			this.itemId = itemId;
			this.uid = uid;
		}
	}
}
