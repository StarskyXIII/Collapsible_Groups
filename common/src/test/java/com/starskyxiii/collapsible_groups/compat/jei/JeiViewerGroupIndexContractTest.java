package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiViewerGroupIndexContractTest {
	private enum Layer { CANDIDATES, RESOLVED, FULL_MATCH, PREVIEW }

	@TestFactory
	Stream<DynamicTest> everyLifecycleTableCellIsEnforced() {
		return Stream.of(GroupChangeEvent.Kind.FULL, GroupChangeEvent.Kind.ENABLED,
				GroupChangeEvent.Kind.STRUCTURE, GroupChangeEvent.Kind.KUBEJS_REPLACE)
			.flatMap(event -> Stream.of(Layer.values()).map(layer -> DynamicTest.dynamicTest(
				event + " / " + layer, () -> assertCell(event, layer))));
	}

	private static void assertCell(GroupChangeEvent.Kind event, Layer layer) {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		index.reset();
		GroupDefinition enabled = group(true);
		GroupCandidateIndex originalCandidate = candidate(enabled);
		index.publishCandidateIndex(originalCandidate, List.of(enabled));
		index.setFullMatchItemsByGroup(Map.of(enabled.id(), List.of()));
		index.setFullMatchFluidsByGroup(Map.of(enabled.id(), List.of()));
		index.setFullMatchGenericByGroup(Map.of(enabled.id(), List.of()));
		Map<?, ?> originalResolved = index.resolvedItemsCache();
		Map<?, ?> originalFullMatch = index.fullMatchItems();
		AtomicReference<String> rebuildThread = new AtomicReference<>();
		index.configureRebuild(() -> {
			rebuildThread.set(Thread.currentThread().getName());
			return candidate(enabled);
		}, Runnable::run, () -> {});

		List<GroupDefinition> currentGroups = event == GroupChangeEvent.Kind.ENABLED
			? List.of(group(false)) : List.of(enabled);
		index.onGroupChange(event, currentGroups);
		index.whenReady().join();

		boolean rebuild = event == GroupChangeEvent.Kind.FULL || event == GroupChangeEvent.Kind.KUBEJS_REPLACE;
		switch (layer) {
			case CANDIDATES -> {
				if (rebuild) {
					assertNotSame(originalCandidate, index.candidates().orElseThrow());
					assertTrue(rebuildThread.get().startsWith("CG-IndexRebuild"));
				} else {
					assertSame(originalCandidate, index.candidates().orElseThrow());
				}
			}
			case RESOLVED -> {
				if (event == GroupChangeEvent.Kind.STRUCTURE) {
					assertSame(originalResolved, index.resolvedItemsCache());
				} else {
					assertNotSame(originalResolved, index.resolvedItemsCache());
					assertTrue(index.ready());
				}
			}
			case FULL_MATCH -> {
				if (rebuild) assertNull(index.fullMatchItems());
				else assertSame(originalFullMatch, index.fullMatchItems());
			}
			case PREVIEW -> {
				if (rebuild) assertTrue(!index.previewCachesValid());
				else assertTrue(index.previewCachesValid());
			}
		}
	}

	private static GroupDefinition group(boolean enabled) {
		return new GroupDefinition("contract_group", "Contract Group", enabled,
			Filters.itemId("minecraft:stone"));
	}

	private static GroupCandidateIndex candidate(GroupDefinition group) {
		return new GroupCandidateIndex(Map.of(), Map.of(group.id(), group), 0, 0, 0);
	}
}
