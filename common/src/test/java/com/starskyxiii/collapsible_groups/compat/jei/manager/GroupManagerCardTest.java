package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.EnabledPersistenceKind;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.GroupSource;
import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupManagerCardTest {
	private static final IIngredientType<Object> TEST_GENERIC_TYPE = () -> Object.class;

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
			List.of(),
			List.of(new Object()),
			List.of(new GenericIngredientRef("test:generic", TEST_GENERIC_TYPE, new Object())),
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

		GroupManagerCard card = GroupManagerCard.create(group, List.of(), List.of(), List.of(), List.of());

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
		List<ItemStack> items = new ArrayList<>();
		List<Object> fluids = new ArrayList<>(List.of(new Object()));
		List<GenericIngredientRef> generic = new ArrayList<>();

		GroupManagerCard card = GroupManagerCard.create(group, items, fluids, generic, List.of());
		items.add(null);
		fluids.add(new Object());

		assertEquals(0, card.itemCount());
		assertEquals(1, card.fluidCount());
		assertThrows(UnsupportedOperationException.class, () -> card.items().add(null));
		assertThrows(UnsupportedOperationException.class, () -> card.fluids().add(new Object()));
		assertThrows(UnsupportedOperationException.class, () -> card.genericEntries().add(null));
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
			List.of(),
			List.of(),
			List.of(new GenericIngredientRef("test:generic", TEST_GENERIC_TYPE, new Object())),
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
}
