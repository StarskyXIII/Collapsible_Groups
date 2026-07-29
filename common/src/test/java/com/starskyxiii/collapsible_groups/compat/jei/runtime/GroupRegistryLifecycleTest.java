package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerGroupIndex;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.platform.TestPlatformHelper;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupRegistryLifecycleTest {
	@TempDir
	Path configDir;

	private final List<String> callbackOrder = new ArrayList<>();
	private GroupChangeEvent.Subscription fullSubscription;
	private GroupChangeEvent.Subscription structureSubscription;
	private GroupChangeEvent.Subscription enabledSubscription;

	@BeforeEach
	void setUp() throws Exception {
		System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, configDir.toString());
		resetRegistryState();
		fullSubscription = GroupChangeEvent.subscribe(
			GroupChangeEvent.Kind.FULL,
			() -> callbackOrder.add("full")
		);
		structureSubscription = GroupChangeEvent.subscribe(
			GroupChangeEvent.Kind.STRUCTURE,
			() -> callbackOrder.add("structure")
		);
		enabledSubscription = GroupChangeEvent.subscribe(
			GroupChangeEvent.Kind.ENABLED,
			() -> callbackOrder.add("enabled")
		);
	}

	@AfterEach
	void tearDown() throws Exception {
		closeSubscriptions();
		resetRegistryState();
		System.clearProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY);
	}

	@Test
	void callbackSlotsAreIndependentAndRunSynchronouslyInRequestedOrder() {
		GroupRegistry.notifyJeiStructureOnly();
		GroupRegistry.notifyJei();
		GroupRegistry.notifyJeiStructureOnly();

		assertEquals(List.of("structure", "full", "structure"), callbackOrder);
	}

	@Test
	void saveInvalidatesBothCacheLevelsAndInvokesOnlyFullCallback() throws Exception {
		GroupDefinition group = group("saved_group", true);
		seedCaches(group.id());
		fullSubscription.close();
		fullSubscription = GroupChangeEvent.subscribe(GroupChangeEvent.Kind.FULL, () -> {
			assertFalse(cacheContainsUnchecked("resolvedItemsByGroup", group.id()));
			assertFalse(cacheContainsUnchecked("fullMatchItemsByGroup", group.id()));
			assertEquals(group, GroupRegistry.findById(group.id()).orElseThrow());
			callbackOrder.add("full");
		});

		GroupRegistry.save(group);

		assertEquals(List.of("full"), callbackOrder);
		assertFalse(cacheContains("resolvedItemsByGroup", group.id()));
		assertFalse(cacheContains("resolvedFluidsByGroup", group.id()));
		assertFalse(cacheContains("fullMatchItemsByGroup", group.id()));
		assertFalse(cacheContains("fullMatchFluidsByGroup", group.id()));
		assertFalse(cacheContains("fullMatchGenericByGroup", group.id()));
		assertEquals(group, GroupRegistry.findById(group.id()).orElseThrow());
	}

	@Test
	void saveQuietlyInvalidatesOnlyFirstMatchCachesAndInvokesNoCallback() throws Exception {
		GroupDefinition group = group("quietly_saved_group", true);
		seedCaches(group.id());

		GroupRegistry.saveQuietly(group);

		assertTrue(callbackOrder.isEmpty());
		assertFalse(cacheContains("resolvedItemsByGroup", group.id()));
		assertFalse(cacheContains("resolvedFluidsByGroup", group.id()));
		assertTrue(cacheContains("fullMatchItemsByGroup", group.id()));
		assertTrue(cacheContains("fullMatchFluidsByGroup", group.id()));
		assertTrue(cacheContains("fullMatchGenericByGroup", group.id()));
	}

	@Test
	void deleteInvalidatesBothCacheLevelsAndInvokesOnlyFullCallback() throws Exception {
		GroupDefinition group = group("deleted_group", true);
		replaceRegistrySnapshot(List.of(group));
		seedCaches(group.id());
		fullSubscription.close();
		fullSubscription = GroupChangeEvent.subscribe(GroupChangeEvent.Kind.FULL, () -> {
			assertFalse(cacheContainsUnchecked("resolvedItemsByGroup", group.id()));
			assertFalse(cacheContainsUnchecked("fullMatchItemsByGroup", group.id()));
			assertTrue(GroupRegistry.findById(group.id()).isEmpty());
			callbackOrder.add("full");
		});

		GroupRegistry.delete(group.id());

		assertEquals(List.of("full"), callbackOrder);
		assertTrue(GroupRegistry.findById(group.id()).isEmpty());
		assertFalse(cacheContains("resolvedItemsByGroup", group.id()));
		assertFalse(cacheContains("resolvedFluidsByGroup", group.id()));
		assertFalse(cacheContains("fullMatchItemsByGroup", group.id()));
		assertFalse(cacheContains("fullMatchFluidsByGroup", group.id()));
		assertFalse(cacheContains("fullMatchGenericByGroup", group.id()));
	}

	@Test
	void userEnabledChangeReresolvesFirstMatchCachesAndPublishesEnabledOnce() throws Exception {
		GroupDefinition group = group("enabled_user_group", true);
		replaceRegistrySnapshot(List.of(group));
		seedCaches(group.id());

		assertTrue(GroupRegistry.setEnabledQuietly(group.id(), false));

		assertEquals(List.of("enabled"), callbackOrder);
		assertFalse(GroupRegistry.findById(group.id()).orElseThrow().enabled());
		assertTrue(cacheContains("resolvedItemsByGroup", group.id()));
		assertTrue(cacheContains("resolvedFluidsByGroup", group.id()));
		assertTrue(cacheContains("fullMatchItemsByGroup", group.id()));
		assertTrue(cacheContains("fullMatchFluidsByGroup", group.id()));
		assertTrue(cacheContains("fullMatchGenericByGroup", group.id()));
	}

	@Test
	void kubeJsEnabledChangeHasTheSameCacheAndEnabledEventEffects() throws Exception {
		GroupDefinition group = group("__kjs_enabled_group", true);
		GroupRegistry.setKubeJsGroups(List.of(group));
		seedCaches(group.id());

		assertTrue(GroupRegistry.setEnabledQuietly(group.id(), false));

		assertEquals(List.of("enabled"), callbackOrder);
		assertFalse(GroupRegistry.findById(group.id()).orElseThrow().enabled());
		assertTrue(cacheContains("resolvedItemsByGroup", group.id()));
		assertTrue(cacheContains("resolvedFluidsByGroup", group.id()));
		assertTrue(cacheContains("fullMatchItemsByGroup", group.id()));
		assertTrue(cacheContains("fullMatchFluidsByGroup", group.id()));
		assertTrue(cacheContains("fullMatchGenericByGroup", group.id()));
	}

	@Test
	void enabledBatchCanCoalesceMultipleUpdatesIntoOneEvent() throws Exception {
		GroupDefinition first = group("batch_first", true);
		GroupDefinition second = group("batch_second", true);
		replaceRegistrySnapshot(List.of(first, second));

		assertTrue(GroupRegistry.setEnabledQuietlyWithoutEvent(first.id(), false));
		assertTrue(GroupRegistry.setEnabledQuietlyWithoutEvent(second.id(), false));
		assertTrue(callbackOrder.isEmpty());

		GroupRegistry.notifyEnabledChanged();

		assertEquals(List.of("enabled"), callbackOrder);
		assertFalse(GroupRegistry.findById(first.id()).orElseThrow().enabled());
		assertFalse(GroupRegistry.findById(second.id()).orElseThrow().enabled());
	}

	@Test
	void kubeJsReplacementClearsAllViewerCacheLayersBeforeAsyncRebuild() throws Exception {
		String existingId = "existing_group";
		seedCaches(existingId);

		GroupRegistry.setKubeJsGroups(List.of(group("__kjs_replacement", true)));

		assertTrue(callbackOrder.isEmpty());
		assertFalse(cacheContains("resolvedItemsByGroup", existingId));
		assertFalse(cacheContains("resolvedFluidsByGroup", existingId));
		assertNull(cache("fullMatchItemsByGroup"));
		assertNull(cache("fullMatchFluidsByGroup"));
		assertNull(cache("fullMatchGenericByGroup"));
	}

	@Test
	void savedPreviewPopulationCreatesEntriesForEveryFullMatchCache() throws Exception {
		GroupDefinition group = new GroupDefinition(
			"preview_group",
			"Preview Group",
			false,
			Filters.id("mekanism:chemical", "mekanism:hydrogen")
		);
		GroupRegistry.clearManagerPreviewCaches();

		GroupRegistry.populateFullMatchCacheFromSaved(group);

		assertTrue(callbackOrder.isEmpty());
		assertTrue(cacheContains("fullMatchItemsByGroup", group.id()));
		assertTrue(cacheContains("fullMatchFluidsByGroup", group.id()));
		assertTrue(cacheContains("fullMatchGenericByGroup", group.id()));
		assertTrue(GroupRegistry.getFullMatchItemsLookup(group).cacheHit());
		assertTrue(GroupRegistry.getFullMatchFluidsLookup(group).cacheHit());
		assertTrue(GroupRegistry.getFullMatchGenericIngredientsLookup(group).cacheHit());
	}

	private static GroupDefinition group(String id, boolean enabled) {
		return new GroupDefinition(id, id, enabled, Filters.itemId("minecraft:stone"));
	}

	private static void seedCaches(String id) {
		GroupDefinition indexed = group(id, true);
		JeiViewerGroupIndex.instance().publishCandidateIndex(
			new GroupCandidateIndex(Map.of(), Map.of(id, indexed), 0, 0, 0), List.of(indexed));
		GroupRegistry.setResolvedItemsByGroup(Map.of(id, List.of()));
		GroupRegistry.setResolvedFluidsByGroup(Map.of(id, List.of(new Object())));
		GroupRegistry.setFullMatchCachesByGroup(
			Map.of(id, List.of()),
			Map.of(id, List.of(new Object())),
			Map.of(id, List.<GenericIngredientRef>of())
		);
	}

	private static boolean cacheContains(String fieldName, String id) throws ReflectiveOperationException {
		Map<?, ?> cache = cache(fieldName);
		return cache != null && cache.containsKey(id);
	}

	private static boolean cacheContainsUnchecked(String fieldName, String id) {
		try {
			return cacheContains(fieldName, id);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static Map<?, ?> cache(String fieldName) throws ReflectiveOperationException {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		return switch (fieldName) {
			case "resolvedItemsByGroup" -> index.resolvedItemsCache();
			case "resolvedFluidsByGroup" -> index.resolvedFluidsCache();
			case "fullMatchItemsByGroup" -> index.fullMatchItems();
			case "fullMatchFluidsByGroup" -> index.fullMatchFluids();
			case "fullMatchGenericByGroup" -> index.fullMatchGeneric();
			default -> throw new NoSuchFieldException(fieldName);
		};
	}

	private static void resetRegistryState() throws Exception {
		JeiViewerGroupIndex.instance().reset();
		replaceRegistrySnapshot(List.of());
		KubeJsGroupStore.clearAll();
		GroupRegistry.clearJeiAllItems();
		GroupRegistry.clearJeiAllFluids();
		GroupRegistry.clearResolvedCaches();
	}

	private void closeSubscriptions() {
		if (fullSubscription != null) fullSubscription.close();
		if (structureSubscription != null) structureSubscription.close();
		if (enabledSubscription != null) enabledSubscription.close();
	}

	private static void replaceRegistrySnapshot(List<GroupDefinition> groups) throws Exception {
		setStaticField("groups", List.copyOf(groups));
		setStaticField("orderedGroups", GroupRegistry.orderByPriority(groups));

		Map<String, GroupDefinition> byId = new LinkedHashMap<>();
		for (GroupDefinition group : groups) {
			byId.put(group.id(), group);
		}
		setStaticField("groupsById", Map.copyOf(byId));
	}

	private static void setStaticField(String fieldName, Object value) throws Exception {
		Field field = GroupRegistry.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
