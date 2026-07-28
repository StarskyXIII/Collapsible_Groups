package com.starskyxiii.collapsible_groups.compat.jei.manager;


import com.starskyxiii.collapsible_groups.client.manager.model.EnabledPersistenceKind;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupSource;
import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupManagerCardTest {
	@Test
	void buildsUserCardViewModelFromResolvedCounts() {
		GroupDefinition group = new GroupDefinition(
			"custom_group",
			"Custom Group",
			true,
			Filters.itemId("minecraft:stone")
		);

		GroupManagerCard card = GroupManagerCard.create(
			group,
			0,
			1,
			1,
			List.of()
		);

		assertEquals("custom_group", card.id());
		assertEquals("Custom Group", card.displayName());
		assertEquals(GroupSource.USER, card.source());
		assertTrue(card.editable());
		assertEquals(0, card.itemCount());
		assertEquals(1, card.fluidCount());
		assertEquals(1, card.genericCount());
		assertEquals(0, card.viewModel().preview().itemCount());
		assertEquals(1, card.viewModel().preview().fluidCount());
		assertEquals(1, card.viewModel().preview().genericCount());
		assertEquals(EnabledPersistenceKind.GROUP_JSON, card.actionEligibility().enabledPersistenceKind());
	}

	@Test
	void buildsReadonlyBuiltinCardEligibility() {
		GroupDefinition group = new GroupDefinition(
			"__default_food",
			"Food",
			false,
			Filters.itemId("minecraft:apple")
		);

		GroupManagerCard card = GroupManagerCard.create(group, 0, 0, 0, List.of());

		assertEquals(GroupSource.BUILTIN, card.source());
		assertFalse(card.editable());
		assertEquals(EnabledPersistenceKind.ENABLED_OVERRIDE_STORE, card.actionEligibility().enabledPersistenceKind());
		assertTrue(card.actionEligibility().switchRequiresEnabledOverrideStore());
	}

	@Test
	void copiesListsAndExposesImmutableSnapshots() {
		GroupDefinition group = new GroupDefinition(
			"custom_group",
			"Custom Group",
			true,
			Filters.itemId("minecraft:stone")
		);
		List<GroupPreviewEntry> entries = new java.util.ArrayList<>();
		entries.add(noopPreview());
		GroupManagerCard card = GroupManagerCard.create(group, 0, 1, 0, entries);
		entries.add(noopPreview());

		assertEquals(0, card.itemCount());
		assertEquals(1, card.fluidCount());
		assertEquals(1, card.previewEntries().size());
		assertThrows(UnsupportedOperationException.class, () -> card.previewEntries().add(noopPreview()));
		assertThrows(UnsupportedOperationException.class, () -> card.headerEntries().add(noopPreview()));
	}

	@Test
	void withGroupRebuildsViewModelForUpdatedNameAndEnabledState() {
		GroupDefinition group = new GroupDefinition(
			"custom_group",
			"Old Name",
			true,
			Filters.itemId("minecraft:stone")
		);
		GroupManagerCard card = GroupManagerCard.create(
			group,
			0,
			0,
			1,
			List.of()
		);

		GroupManagerCard updated = card.withGroup(group.withName("New Name").withEnabled(false));

		assertNotSame(card, updated);
		assertEquals("Old Name", card.viewModel().groupName());
		assertTrue(card.viewModel().enabled());
		assertEquals("New Name", updated.viewModel().groupName());
		assertFalse(updated.viewModel().enabled());
		assertEquals(1, updated.viewModel().preview().genericCount());
	}

	// header source resolution. The item-resolution branch
	// (valid icon ids) needs a bootstrapped item registry and is only exercisable
	// in-game; these cover the registry-free fallback branches.

	@Test
	void headerSourceFallsBackToPreviewEntriesWhenNoIconIds() {
		GroupDefinition group = new GroupDefinition(
			"custom_group", "Custom Group", true, Filters.itemId("minecraft:stone"));
		GroupPreviewEntry a = noopPreview();
		GroupPreviewEntry b = noopPreview();
		GroupManagerCard card = GroupManagerCard.create(
			group, 0, 0, 0, List.of(a, b));

		List<GroupPreviewEntry> header = card.headerSource();
		assertEquals(2, header.size());
		assertSame(a, header.get(0));
		assertSame(b, header.get(1));
	}

	// Note: the "icon ids present but unresolvable → fallback" branch reaches
	// ItemStack.EMPTY, which cannot statically initialize without a bootstrapped
	// game; that branch is left to in-game QA.

	@Test
	void headerSourceTruncatesFallbackToFirstTwoPreviewEntries() {
		GroupDefinition group = new GroupDefinition(
			"custom_group", "Custom Group", true, Filters.itemId("minecraft:stone"));
		GroupPreviewEntry a = noopPreview();
		GroupPreviewEntry b = noopPreview();
		GroupPreviewEntry c = noopPreview();
		GroupManagerCard card = GroupManagerCard.create(
			group, 0, 0, 0, List.of(a, b, c));

		List<GroupPreviewEntry> header = card.headerSource();
		assertEquals(2, header.size());
		assertSame(a, header.get(0));
		assertSame(b, header.get(1));
	}

	private static GroupPreviewEntry noopPreview() {
		return GroupPreviewEntry.ofRenderer((graphics, x, y) -> {});
	}
}
