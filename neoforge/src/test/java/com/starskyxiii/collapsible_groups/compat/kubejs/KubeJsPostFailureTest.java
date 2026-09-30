package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.group.ScriptedGroupStore;
import com.starskyxiii.collapsible_groups.platform.services.IPlatformHelper;
import com.starskyxiii.collapsible_groups.viewer.ViewerBootstrapContext;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubeJsPostFailureTest {
	private static final ViewerBootstrapContext<Object> EMPTY_BOOTSTRAP = new ViewerBootstrapContext<>() {
		private final ViewerIngredientUniverse<Object> universe = new ViewerIngredientUniverse<>(List.of());

		@Override
		public ViewerIngredientUniverse<Object> universe() {
			return universe;
		}

		@Override
		public List<ViewerIngredientType<Object>> ingredientTypes() {
			return List.of();
		}
	};

	@BeforeEach
	void reset() {
		CGEvents.GROUPS.clear(ScriptType.CLIENT);
		ScriptedGroupStore.invalidate();
	}

	@AfterEach
	void cleanUp() {
		CGEvents.GROUPS.clear(ScriptType.CLIENT);
		ScriptedGroupStore.invalidate();
	}

	@Test
	void bridgePublishesSuccessfulEventAndAbortsPartialEventOnListenerError() {
		ClassLoader original = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(IPlatformHelper.class.getClassLoader());
		try {
			CGEvents.GROUPS.listenJava(ScriptType.CLIENT, null, event -> {
				((CGGroupsKubeEvent) event).source("test:baseline", source ->
					source.item("test:baseline", "Baseline", "minecraft:stone"));
				return null;
			});
			long successCheckpoint = ScriptedGroupStore.publicationCheckpoint();
			KubeJSGroupBridge.applyGroups(EMPTY_BOOTSTRAP);
			assertEquals(List.of("Baseline"), scriptedNames());
			assertTrue(ScriptedGroupStore.markAppliedAfter(successCheckpoint));

			CGEvents.GROUPS.clear(ScriptType.CLIENT);
			CGEvents.GROUPS.listenJava(ScriptType.CLIENT, null, event -> {
				((CGGroupsKubeEvent) event).source("test:partial", source ->
					source.item("test:partial", "Partial", "minecraft:dirt"));
				return null;
			});
			CGEvents.GROUPS.listenJava(ScriptType.CLIENT, null, event -> {
				throw new IllegalStateException("intentional script failure");
			});

			long failureCheckpoint = ScriptedGroupStore.publicationCheckpoint();
			KubeJSGroupBridge.applyGroups(EMPTY_BOOTSTRAP);
			assertEquals(List.of("Baseline"), scriptedNames());
			assertFalse(ScriptedGroupStore.markAppliedAfter(failureCheckpoint));
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	private static List<String> scriptedNames() {
		return GroupRepository.getAllIncludingScripted().stream()
			.filter(group -> group.id().startsWith("__kjs_"))
			.map(group -> group.name())
			.toList();
	}
}
