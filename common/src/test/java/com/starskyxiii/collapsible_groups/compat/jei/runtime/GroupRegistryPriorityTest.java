package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupRegistryPriorityTest {
	@AfterEach
	void resetRegistryState() throws Exception {
		replaceRegistrySnapshot(List.of());
		KubeJsGroupStore.clearAll();
	}

	@Test
	void orderByPriorityIsDescendingAndStable() {
		GroupDefinition firstTie = group("first_tie", 0);
		GroupDefinition high = group("high", 10);
		GroupDefinition secondTie = group("second_tie", 0);
		GroupDefinition middle = group("middle", 3);

		List<String> orderedIds = GroupRegistry.orderByPriority(List.of(firstTie, high, secondTie, middle))
			.stream()
			.map(GroupDefinition::id)
			.toList();

		assertEquals(List.of("high", "middle", "first_tie", "second_tie"), orderedIds);
	}

	@Test
	void getAllIncludingKubeJsOrdersByPriorityAndPreservesStableTies() throws Exception {
		GroupDefinition persistedFirstTie = group("persisted_first_tie", 0);
		GroupDefinition persistedHigh = group("persisted_high", 5);
		GroupDefinition persistedSecondTie = group("persisted_second_tie", 0);
		GroupDefinition kubeJsHighest = group("__kjs_highest", 9);
		GroupDefinition kubeJsTie = group("__kjs_tie", 0);
		replaceRegistrySnapshot(List.of(persistedFirstTie, persistedHigh, persistedSecondTie));
		KubeJsGroupStore.setGroups(List.of(kubeJsHighest, kubeJsTie));

		List<String> orderedIds = GroupRegistry.getAllIncludingKubeJs()
			.stream()
			.map(GroupDefinition::id)
			.toList();

		assertEquals(List.of(
			"__kjs_highest",
			"persisted_high",
			"persisted_first_tie",
			"persisted_second_tie",
			"__kjs_tie"
		), orderedIds);
	}

	@Test
	void priorityOrderControlsFirstEnabledOwnership() {
		GroupDefinition low = group("low_priority", 0);
		GroupDefinition high = group("high_priority", 10);
		FakeIngredient stone = new FakeIngredient("stone");
		FakeEntry entry = new FakeEntry(stone);
		Map<String, List<FakeEntry>> fullMatch = entries(
			low, List.of(entry),
			high, List.of(entry)
		);
		Map<String, List<FakeEntry>> resolved = emptyEntries(low, high);
		IdentityHashMap<ITypedIngredient<?>, GroupDefinition> index = new IdentityHashMap<>();

		IngredientFilterHelper.assignFirstEnabledOwners(
			GroupRegistry.orderByPriority(List.of(low, high)),
			fullMatch,
			resolved,
			index,
			FakeEntry::typed
		);

		assertSame(high, index.get(stone));
		assertTrue(resolved.get(low.id()).isEmpty());
		assertEquals(List.of(entry), resolved.get(high.id()));
	}

	@Test
	void equalPriorityOwnershipKeepsRegistrationOrder() {
		GroupDefinition first = group("first", 0);
		GroupDefinition second = group("second", 0);
		FakeIngredient stone = new FakeIngredient("stone");
		FakeEntry entry = new FakeEntry(stone);
		Map<String, List<FakeEntry>> fullMatch = entries(
			first, List.of(entry),
			second, List.of(entry)
		);
		Map<String, List<FakeEntry>> resolved = emptyEntries(first, second);
		IdentityHashMap<ITypedIngredient<?>, GroupDefinition> index = new IdentityHashMap<>();

		IngredientFilterHelper.assignFirstEnabledOwners(
			GroupRegistry.orderByPriority(List.of(first, second)),
			fullMatch,
			resolved,
			index,
			FakeEntry::typed
		);

		assertSame(first, index.get(stone));
		assertEquals(List.of(entry), resolved.get(first.id()));
		assertTrue(resolved.get(second.id()).isEmpty());
	}

	private static GroupDefinition group(String id, int priority) {
		return new GroupDefinition(id, id, true, Filters.itemId("minecraft:stone")).withPriority(priority);
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

	private static void replaceRegistrySnapshot(List<GroupDefinition> groups) throws Exception {
		Field groupsField = GroupRegistry.class.getDeclaredField("groups");
		groupsField.setAccessible(true);
		groupsField.set(null, List.copyOf(groups));

		Field orderedGroupsField = GroupRegistry.class.getDeclaredField("orderedGroups");
		orderedGroupsField.setAccessible(true);
		orderedGroupsField.set(null, GroupRegistry.orderByPriority(groups));

		Map<String, GroupDefinition> byId = new LinkedHashMap<>();
		for (GroupDefinition group : groups) {
			byId.put(group.id(), group);
		}
		Field groupsByIdField = GroupRegistry.class.getDeclaredField("groupsById");
		groupsByIdField.setAccessible(true);
		groupsByIdField.set(null, Map.copyOf(byId));
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
