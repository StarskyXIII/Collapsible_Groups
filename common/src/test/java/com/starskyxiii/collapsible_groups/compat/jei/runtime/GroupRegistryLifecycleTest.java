package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerGroupIndex;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.group.GroupRepositoryTestAccess;
import com.starskyxiii.collapsible_groups.platform.TestPlatformHelper;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
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
	private final List<GroupChangeEvent.Subscription> indexSubscriptions = new ArrayList<>();

	@BeforeEach
	void setUp() {
		System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, configDir.toString());
		resetRegistryState();
		for (GroupChangeEvent.Kind kind : GroupChangeEvent.Kind.values()) {
			indexSubscriptions.add(GroupChangeEvent.subscribe(kind, () -> JeiViewerGroupIndex.instance()
				.onGroupChange(kind, GroupRepository.getAllIncludingScripted())));
		}
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
	void tearDown() {
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
	void saveInvalidatesBothCacheLevelsAndInvokesOnlyFullCallback() {
		GroupDefinition group = group("saved_group", true);
		seedCaches(group.id());
		fullSubscription.close();
		fullSubscription = GroupChangeEvent.subscribe(GroupChangeEvent.Kind.FULL, () -> {
			assertFalse(cacheContains(JeiViewerGroupIndex.instance().resolvedItemsCache(), group.id()));
			assertFalse(hasPreview(group.id()));
			assertEquals(group, GroupRegistry.findById(group.id()).orElseThrow());
			callbackOrder.add("full");
		});

		GroupRegistry.save(group);

		assertEquals(List.of("full"), callbackOrder);
		assertFalse(cacheContains(JeiViewerGroupIndex.instance().resolvedItemsCache(), group.id()));
		assertFalse(cacheContains(JeiViewerGroupIndex.instance().resolvedFluidsCache(), group.id()));
		assertFalse(hasPreview(group.id()));
		assertEquals(group, GroupRegistry.findById(group.id()).orElseThrow());
	}

	@Test
	void saveQuietlyInvalidatesOnlyFirstMatchCachesAndInvokesNoCallback() {
		GroupDefinition group = group("quietly_saved_group", true);
		seedCaches(group.id());

		GroupRegistry.saveQuietly(group);

		assertTrue(callbackOrder.isEmpty());
		assertFalse(cacheContains(JeiViewerGroupIndex.instance().resolvedItemsCache(), group.id()));
		assertFalse(cacheContains(JeiViewerGroupIndex.instance().resolvedFluidsCache(), group.id()));
		assertTrue(hasPreview(group.id()));
	}

	@Test
	void deleteInvalidatesBothCacheLevelsAndInvokesOnlyFullCallback() {
		GroupDefinition group = group("deleted_group", true);
		replaceRegistrySnapshot(List.of(group));
		seedCaches(group.id());
		fullSubscription.close();
		fullSubscription = GroupChangeEvent.subscribe(GroupChangeEvent.Kind.FULL, () -> {
			assertFalse(cacheContains(JeiViewerGroupIndex.instance().resolvedItemsCache(), group.id()));
			assertFalse(hasPreview(group.id()));
			assertTrue(GroupRegistry.findById(group.id()).isEmpty());
			callbackOrder.add("full");
		});

		GroupRegistry.delete(group.id());

		assertEquals(List.of("full"), callbackOrder);
		assertTrue(GroupRegistry.findById(group.id()).isEmpty());
		assertFalse(cacheContains(JeiViewerGroupIndex.instance().resolvedItemsCache(), group.id()));
		assertFalse(cacheContains(JeiViewerGroupIndex.instance().resolvedFluidsCache(), group.id()));
		assertFalse(hasPreview(group.id()));
	}

	@Test
	void userEnabledChangeReresolvesFirstMatchCachesAndPublishesEnabledOnce() {
		GroupDefinition group = group("enabled_user_group", true);
		replaceRegistrySnapshot(List.of(group));
		seedCaches(group.id());

		assertTrue(GroupRegistry.setEnabledQuietly(group.id(), false));

		assertEquals(List.of("enabled"), callbackOrder);
		assertFalse(GroupRegistry.findById(group.id()).orElseThrow().enabled());
		assertTrue(cacheContains(JeiViewerGroupIndex.instance().resolvedItemsCache(), group.id()));
		assertTrue(cacheContains(JeiViewerGroupIndex.instance().resolvedFluidsCache(), group.id()));
		assertTrue(hasPreview(group.id()));
	}

	@Test
	void kubeJsEnabledChangeHasTheSameCacheAndEnabledEventEffects() {
		GroupDefinition group = group("__kjs_enabled_group", true);
		GroupRegistry.setKubeJsGroups(List.of(group));
		seedCaches(group.id());

		assertTrue(GroupRegistry.setEnabledQuietly(group.id(), false));

		assertEquals(List.of("enabled"), callbackOrder);
		assertFalse(GroupRegistry.findById(group.id()).orElseThrow().enabled());
		assertTrue(cacheContains(JeiViewerGroupIndex.instance().resolvedItemsCache(), group.id()));
		assertTrue(cacheContains(JeiViewerGroupIndex.instance().resolvedFluidsCache(), group.id()));
		assertTrue(hasPreview(group.id()));
	}

	@Test
	void enabledBatchCanCoalesceMultipleUpdatesIntoOneEvent() {
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
	void kubeJsReplacementClearsAllViewerCacheLayersBeforeAsyncRebuild() {
		String existingId = "existing_group";
		seedCaches(existingId);

		GroupRegistry.setKubeJsGroups(List.of(group("__kjs_replacement", true)));

		assertTrue(callbackOrder.isEmpty());
		assertFalse(cacheContains(JeiViewerGroupIndex.instance().resolvedItemsCache(), existingId));
		assertFalse(cacheContains(JeiViewerGroupIndex.instance().resolvedFluidsCache(), existingId));
		assertNull(JeiViewerGroupIndex.instance().displaySnapshot().candidates());
	}

	private static GroupDefinition group(String id, boolean enabled) {
		return new GroupDefinition(id, id, enabled, Filters.itemId("minecraft:stone"));
	}

	private static void seedCaches(String id) {
		GroupDefinition indexed = group(id, true);
		JeiViewerGroupIndex.instance().publishGeneration(new JeiViewerGroupIndex.Generation(
			new GroupCandidateIndex(Map.of(), Map.of(id, indexed), 0, 0, 0),
			Map.of(id, List.of()), Map.of(id, List.of(new Object())), Map.of(id, List.of()),
			Map.of(id, List.of(new Object())), Map.of(id, List.of()), Map.of(), Map.of()));
	}

	private static boolean hasPreview(String id) {
		var display = JeiViewerGroupIndex.instance().displaySnapshot();
		GroupDefinition definition = display.candidates() == null ? null : display.candidates().groupSnapshot().get(id);
		return definition != null && display.preview(definition).isPresent();
	}

	private static boolean cacheContains(Map<?, ?> cache, String id) {
		return cache != null && cache.containsKey(id);
	}

	private static void resetRegistryState() {
		JeiViewerGroupIndex.instance().reset();
		replaceRegistrySnapshot(List.of());
		GroupRegistry.clearJeiAllItems();
		GroupRegistry.clearJeiAllFluids();
		GroupRegistry.clearResolvedCaches();
	}

	private void closeSubscriptions() {
		if (fullSubscription != null) fullSubscription.close();
		if (structureSubscription != null) structureSubscription.close();
		if (enabledSubscription != null) enabledSubscription.close();
		indexSubscriptions.forEach(GroupChangeEvent.Subscription::close);
		indexSubscriptions.clear();
	}

	private static void replaceRegistrySnapshot(List<GroupDefinition> groups) {
		GroupRepositoryTestAccess.replace(groups);
	}
}
