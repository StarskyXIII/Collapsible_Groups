package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.group.GroupRepositoryTestAccess;
import com.starskyxiii.collapsible_groups.group.ScriptedGroupStore;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.platform.TestPlatformHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubeJsGroupPublicationTest {
	@TempDir
	Path tempDir;

	@AfterEach
	void reset() {
		GroupRepositoryTestAccess.replace(List.of());
		System.clearProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY);
	}

	@Test
	void ownerBatchReplacesMultipleSourcesAndRemovesOnlyMissingOwnedSources() {
		KubeJsGroupPublication.Session first = KubeJsGroupPublication.begin("recipe-viewer");
		assertTrue(first.replace("client:item", List.of(group("client", "client:item")))
			.replace("remote:item", List.of(group("remote", "remote:item"))).publish());
		assertTrue(KubeJsGroupPublication.begin("another-adapter")
			.replace("client:item", List.of(group("other", "client:item"))).publish());

		assertTrue(KubeJsGroupPublication.begin("recipe-viewer")
			.replace("remote:item", List.of(group("remote-new", "remote:item"))).publish());

		assertEquals(List.of("remote-new", "other"), ids());
	}

	@Test
	void rejectedSourcePoisonsTheBatchAndLeavesPreviousSnapshotUntouched() {
		assertTrue(KubeJsGroupPublication.begin("owner")
			.replace("source", List.of(group("old", "source"))).publish());
		KubeJsGroupPublication.Session replacement = KubeJsGroupPublication.begin("owner");
		replacement.replace("source", List.of(group("new", "source")));

		assertThrows(IllegalArgumentException.class, () -> replacement.replace("broken", List.of(
			new KubeJsLoweredGroup("bad", "bad", KubeJsLoweringResult.unsupported("predicate", "broken")))));
		assertThrows(IllegalStateException.class, replacement::publish);

		assertEquals(List.of("old"), ids());
	}

	@Test
	void invalidSourceAndNullGroupsPoisonOtherwiseValidBatches() {
		assertTrue(KubeJsGroupPublication.begin("owner")
			.replace("source", List.of(group("old", "source"))).publish());
		KubeJsGroupPublication.Session invalidSource = KubeJsGroupPublication.begin("owner");
		invalidSource.replace("source", List.of(group("new", "source")));
		assertThrows(IllegalArgumentException.class, () -> invalidSource.replace(" ", List.of()));
		assertThrows(IllegalStateException.class, invalidSource::publish);

		KubeJsGroupPublication.Session nullGroups = KubeJsGroupPublication.begin("owner");
		nullGroups.replace("source", List.of(group("newer", "source")));
		assertThrows(NullPointerException.class, () -> nullGroups.replace("broken", null));
		assertThrows(IllegalStateException.class, nullGroups::publish);
		assertEquals(List.of("old"), ids());
	}

	@Test
	void duplicateGroupIdsRejectTheAtomicPublication() {
		assertTrue(KubeJsGroupPublication.begin("owner")
			.replace("source", List.of(group("old", "source"))).publish());
		KubeJsGroupPublication.Session duplicate = KubeJsGroupPublication.begin("owner");
		duplicate.replace("source", List.of(group("same", "source"), group("same", "source")));

		assertThrows(IllegalArgumentException.class, duplicate::publish);

		assertEquals(List.of("old"), ids());
	}

	@Test
	void userDefinitionWinsCollisionWithPublishedSource() {
		GroupDefinition user = new GroupDefinition("shared", "user", true, Filters.itemId("minecraft:dirt"));
		GroupRepositoryTestAccess.replace(List.of(user));

		assertTrue(KubeJsGroupPublication.begin("owner")
			.replace("source", List.of(group("shared", "source"), group("tail", "source"))).publish());

		assertSame(user, GroupRepository.findById("shared").orElseThrow());
		assertEquals(List.of("shared", "tail"), ids());
	}

	@Test
	void enabledOverrideSurvivesAScopedSourceReplacement() {
		System.setProperty(TestPlatformHelper.CONFIG_DIR_PROPERTY, tempDir.toString());
		assertTrue(KubeJsGroupPublication.begin("owner")
			.replace("source", List.of(group("scripted", "source"))).publish());
		assertTrue(GroupRepository.setEnabledQuietlyWithoutEvent("scripted", false));

		assertTrue(KubeJsGroupPublication.begin("owner")
			.replace("source", List.of(group("scripted", "source"))).publish());

		assertFalse(GroupRepository.findById("scripted").orElseThrow().enabled());
	}

	@Test
	void newerSessionRejectsOlderSessionForTheSameOwnerOnly() {
		KubeJsGroupPublication.Session old = KubeJsGroupPublication.begin("owner");
		KubeJsGroupPublication.Session otherOwner = KubeJsGroupPublication.begin("other");
		KubeJsGroupPublication.Session current = KubeJsGroupPublication.begin("owner");

		assertTrue(current.replace("source", List.of(group("current", "source"))).publish());
		assertFalse(old.replace("source", List.of(group("stale", "source"))).publish());
		assertTrue(otherOwner.replace("source", List.of(group("other", "source"))).publish());
		assertEquals(List.of("current", "other"), ids());
	}

	@Test
	void invalidationRejectsCapturedMaterializationAndClearsEverySource() {
		KubeJsGroupPublication.Session existing = KubeJsGroupPublication.begin("existing");
		assertTrue(existing.replace("source", List.of(group("existing", "source"))).publish());
		KubeJsGroupPublication.Session stale = KubeJsGroupPublication.begin("owner");
		KubeJsMaterializationCapture capture = stale.capture("materialized");
		KubeJsLoweredGroup materialized = new KubeJsLoweredGroup("materialized", "materialized",
			KubeJsLoweringResult.materialized(Filters.itemId("minecraft:stone"), "materialized", capture));
		stale.replace("materialized", List.of(materialized));

		ScriptedGroupStore.invalidate();

		assertFalse(stale.publish());
		assertTrue(GroupRepository.areScriptedGroupsEmpty());
	}

	@Test
	void materializationCaptureCannotCrossSources() {
		KubeJsGroupPublication.Session session = KubeJsGroupPublication.begin("owner");
		KubeJsMaterializationCapture capture = session.capture("first");
		KubeJsLoweredGroup materialized = new KubeJsLoweredGroup("materialized", "materialized",
			KubeJsLoweringResult.materialized(Filters.itemId("minecraft:stone"), "first", capture));

		assertThrows(IllegalArgumentException.class,
			() -> session.replace("second", List.of(materialized)));
		assertTrue(GroupRepository.areScriptedGroupsEmpty());
	}

	@Test
	void materializationRejectsAResidualPredicateInsteadOfTreatingItAsAnIdSet() {
		KubeJsGroupPublication.Session session = KubeJsGroupPublication.begin("owner");
		KubeJsMaterializationCapture capture = session.capture("source");

		assertThrows(IllegalArgumentException.class, () -> KubeJsLoweringResult.materialized(
			Filters.namespace("item", "minecraft"), "source", capture));
	}

	private static KubeJsLoweredGroup group(String id, String source) {
		return new KubeJsLoweredGroup(id, id,
			KubeJsLoweringResult.exact(Filters.itemId("minecraft:stone"), source));
	}

	private static List<String> ids() {
		return GroupRepository.getAllIncludingScripted().stream().map(GroupDefinition::id).toList();
	}
}
