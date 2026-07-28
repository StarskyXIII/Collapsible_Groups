package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.ScriptedGroupStore;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ViewerLifecycleCoordinatorTest {
	@AfterEach void resetStore() { ScriptedGroupStore.invalidate(); }

	static Stream<org.junit.jupiter.params.provider.Arguments> selections() {
		List<org.junit.jupiter.params.provider.Arguments> rows = new ArrayList<>();
		for (boolean jei : List.of(false, true)) for (boolean emi : List.of(false, true))
			for (boolean tmrv : List.of(false, true)) {
				boolean realJei = jei && !tmrv;
				boolean effectiveEmi = emi || tmrv;
				String expected = effectiveEmi ? "emi" : realJei ? "jei" : null;
				rows.add(org.junit.jupiter.params.provider.Arguments.of(jei, emi, tmrv, expected));
			}
		return rows.stream();
	}

	@ParameterizedTest @MethodSource("selections")
	void selectsExactlyOneViewer(boolean jei, boolean emi, boolean tmrv, String expected) {
		var selection = ViewerLifecycleCoordinator.select(
			new ViewerLifecycleCoordinator.Environment(jei, emi, tmrv), Set.of("jei", "emi"));
		assertEquals(java.util.Optional.ofNullable(expected), selection.viewerId());
	}

	@Test void prefersTheSupportedJeiAdapterWhenBothModsArePresent() {
		var selection = ViewerLifecycleCoordinator.select(
			new ViewerLifecycleCoordinator.Environment(true, true, false), Set.of("jei"));
		assertEquals(java.util.Optional.of("jei"), selection.viewerId());
	}

	@Test void emiWinsOverJeiOnceItsAdapterIsSupported() {
		var selection = ViewerLifecycleCoordinator.select(
			new ViewerLifecycleCoordinator.Environment(true, true, false), Set.of("jei", "emi"));
		assertEquals(java.util.Optional.of("emi"), selection.viewerId());
	}

	@Test void tmrvDisqualifiesJeiAndWarnsWhenNoEmiAdapterExists() {
		var selection = ViewerLifecycleCoordinator.select(
			new ViewerLifecycleCoordinator.Environment(true, true, true), Set.of("jei"));
		assertTrue(selection.viewerId().isEmpty());
		assertTrue(selection.warning().isPresent());
	}

	@Test void tmrvRoutesToEmiOnceItsAdapterIsSupported() {
		var selection = ViewerLifecycleCoordinator.select(
			new ViewerLifecycleCoordinator.Environment(true, false, true), Set.of("jei", "emi"));
		assertEquals(java.util.Optional.of("emi"), selection.viewerId());
	}

	@Test void realRegistrationPathKeepsJeiInertWhenEmiWins() {
		ViewerLifecycleCoordinator coordinator = new ViewerLifecycleCoordinator(
			new ViewerLifecycleCoordinator.Environment(true, true, false), Set.of("jei", "emi"), ignored -> {});
		FakeViewerAdapter emi = new FakeViewerAdapter("emi");
		FakeViewerAdapter jei = new FakeViewerAdapter("jei");
		coordinator.register(emi);
		coordinator.register(jei);
		GroupChangeEvent.publish(GroupChangeEvent.Kind.STRUCTURE);
		assertEquals(List.of(GroupChangeEvent.Kind.STRUCTURE), emi.changes());
		assertTrue(jei.changes().isEmpty());
	}

	@Test void inactiveJeiDoesNotSubscribeOrReceiveSharedChanges() {
		ViewerLifecycleCoordinator coordinator = new ViewerLifecycleCoordinator(
			new ViewerLifecycleCoordinator.Environment(false, true, false), Set.of("jei"), ignored -> {});
		FakeViewerAdapter jei = new FakeViewerAdapter("jei");
		coordinator.register(jei);
		GroupChangeEvent.publish(GroupChangeEvent.Kind.FULL);
		assertTrue(jei.changes().isEmpty());
		assertFalse(coordinator.activeUniverseReady("jei", jei.bootstrapContext()));
	}

	@Test void activeUniverseBootstrapPublishesScriptedGroupsOnce() {
		ViewerLifecycleCoordinator coordinator = new ViewerLifecycleCoordinator(
			new ViewerLifecycleCoordinator.Environment(true, false, false), Set.of("jei"), ignored -> {});
		FakeViewerAdapter jei = new FakeViewerAdapter("jei");
		coordinator.register(jei);
		GroupDefinition group = new GroupDefinition("scripted", "Scripted", true,
			new GroupFilter.Id("item", "minecraft:stone"));
		int[] calls = {0};
		coordinator.setScriptedGroupBootstrap(context -> {
			calls[0]++;
			ScriptedGroupStore.publish(List.of(group));
		});

		assertTrue(coordinator.activeUniverseReady("jei", jei.bootstrapContext()));
		assertFalse(coordinator.activeUniverseReady("jei", jei.bootstrapContext()));
		assertEquals(1, calls[0]);
		assertEquals(List.of(group), ScriptedGroupStore.groups());
	}

	@Test void remoteInvalidationUsesNeutralGroupChangeEvent() {
		ScriptedGroupStore.publish(List.of());
		ScriptedGroupStore.markApplied();
		int[] notifications = {0};
		try (GroupChangeEvent.Subscription ignored = GroupChangeEvent.subscribe(
			GroupChangeEvent.Kind.KUBEJS_REPLACE, () -> notifications[0]++)) {
			ScriptedGroupStore.invalidateAndNotify();
		}
		assertFalse(ScriptedGroupStore.isApplied());
		assertEquals(1, notifications[0]);
	}

	@Test void kubeJsReplacementCanRecollectAgainstTheAlreadyPublishedActiveUniverse() {
		ViewerLifecycleCoordinator coordinator = new ViewerLifecycleCoordinator(
			new ViewerLifecycleCoordinator.Environment(false, true, false), Set.of("emi"), ignored -> {});
		FakeViewerAdapter emi = new FakeViewerAdapter("emi");
		coordinator.register(emi);
		int[] calls = {0};
		coordinator.setScriptedGroupBootstrap(context -> calls[0]++);

		assertTrue(coordinator.activeUniverseReady("emi", emi.bootstrapContext()));
		ScriptedGroupStore.invalidate();
		assertTrue(coordinator.activeUniverseReady("emi", emi.bootstrapContext()));
		assertEquals(2, calls[0]);
	}

	@Test void scriptedBootstrapIsReentrySafeWhenPublishingReplacementEvents() {
		ViewerLifecycleCoordinator coordinator = new ViewerLifecycleCoordinator(
			new ViewerLifecycleCoordinator.Environment(false, true, false), Set.of("emi"), ignored -> {});
		FakeViewerAdapter emi = new FakeViewerAdapter("emi");
		coordinator.register(emi);
		int[] calls = {0};
		coordinator.setScriptedGroupBootstrap(context -> {
			calls[0]++;
			assertFalse(coordinator.activeUniverseReady("emi", context));
		});

		assertTrue(coordinator.activeUniverseReady("emi", emi.bootstrapContext()));
		assertEquals(1, calls[0]);
	}

	@Test void activeAdapterExposesTheSelectedIndexAndEditorRuntimeSeams() {
		ViewerLifecycleCoordinator coordinator = new ViewerLifecycleCoordinator(
			new ViewerLifecycleCoordinator.Environment(false, true, false), Set.of("emi"), ignored -> {});
		FakeViewerAdapter emi = new FakeViewerAdapter("emi");
		coordinator.register(emi);

		ViewerAdapter<?, ?> active = coordinator.activeAdapter().orElseThrow();
		assertSame(emi.groupIndex(), active.groupIndex());
		assertSame(emi.editorRuntimeAccess(), active.editorRuntimeAccess());
	}
}
