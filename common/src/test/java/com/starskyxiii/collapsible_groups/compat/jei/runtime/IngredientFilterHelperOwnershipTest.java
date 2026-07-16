package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientFilterHelperOwnershipTest {
	@Test
	void disabledEarlierGroupDoesNotClaimFirstMatchOwnership() {
		GroupDefinition disabledBuiltin = group("__default_music_discs", false);
		GroupDefinition enabledCopy = group("music_discs_copy", true);
		FakeIngredient disc = new FakeIngredient("disc");
		FakeEntry entry = new FakeEntry(disc);
		Map<String, List<FakeEntry>> fullMatch = entries(
			disabledBuiltin, List.of(entry),
			enabledCopy, List.of(entry)
		);
		Map<String, List<FakeEntry>> resolved = emptyEntries(disabledBuiltin, enabledCopy);
		IdentityHashMap<ITypedIngredient<?>, GroupDefinition> index = new IdentityHashMap<>();

		IngredientFilterHelper.assignFirstEnabledOwners(
			List.of(disabledBuiltin, enabledCopy),
			fullMatch,
			resolved,
			index,
			FakeEntry::typed
		);

		assertSame(enabledCopy, index.get(disc));
		assertTrue(resolved.get(disabledBuiltin.id()).isEmpty());
		assertEquals(List.of(entry), resolved.get(enabledCopy.id()));
		assertEquals(List.of(entry), fullMatch.get(disabledBuiltin.id()));
		assertEquals(List.of(entry), fullMatch.get(enabledCopy.id()));
	}

	@Test
	void enabledEarlierGroupKeepsFirstMatchOwnershipAheadOfCopy() {
		GroupDefinition enabledBuiltin = group("__default_music_discs", true);
		GroupDefinition enabledCopy = group("music_discs_copy", true);
		FakeIngredient disc = new FakeIngredient("disc");
		FakeEntry entry = new FakeEntry(disc);
		Map<String, List<FakeEntry>> fullMatch = entries(
			enabledBuiltin, List.of(entry),
			enabledCopy, List.of(entry)
		);
		Map<String, List<FakeEntry>> resolved = emptyEntries(enabledBuiltin, enabledCopy);
		IdentityHashMap<ITypedIngredient<?>, GroupDefinition> index = new IdentityHashMap<>();

		IngredientFilterHelper.assignFirstEnabledOwners(
			List.of(enabledBuiltin, enabledCopy),
			fullMatch,
			resolved,
			index,
			FakeEntry::typed
		);

		assertSame(enabledBuiltin, index.get(disc));
		assertEquals(List.of(entry), resolved.get(enabledBuiltin.id()));
		assertTrue(resolved.get(enabledCopy.id()).isEmpty());
		assertEquals(List.of(entry), fullMatch.get(enabledBuiltin.id()));
		assertEquals(List.of(entry), fullMatch.get(enabledCopy.id()));
	}

	private static GroupDefinition group(String id, boolean enabled) {
		return new GroupDefinition(id, id, enabled, Filters.itemId("minecraft:stone"));
	}

	private static Map<String, List<FakeEntry>> entries(
		GroupDefinition first,
		List<FakeEntry> firstEntries,
		GroupDefinition second,
		List<FakeEntry> secondEntries
	) {
		Map<String, List<FakeEntry>> map = new LinkedHashMap<>();
		map.put(first.id(), new ArrayList<>(firstEntries));
		map.put(second.id(), new ArrayList<>(secondEntries));
		return map;
	}

	private static Map<String, List<FakeEntry>> emptyEntries(GroupDefinition... groups) {
		Map<String, List<FakeEntry>> map = new LinkedHashMap<>();
		for (GroupDefinition group : groups) {
			map.put(group.id(), new ArrayList<>());
		}
		return map;
	}

	private record FakeEntry(ITypedIngredient<?> typed) {}

	private record FakeIngredient(Object value) implements ITypedIngredient<Object> {
		@Override
		public IIngredientType<Object> getType() {
			return null;
		}

		@Override
		public Object getIngredient() {
			return value;
		}
	}
}
