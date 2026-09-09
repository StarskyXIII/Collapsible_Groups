package com.starskyxiii.collapsible_groups.client.editor;

import org.junit.jupiter.api.Test;

import com.starskyxiii.collapsible_groups.viewer.ViewerAdapter;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EditorRuntimeServicesTest {
	@Test void installedRuntimeBacksEveryFocusedView() {
		List<String> calls = new ArrayList<>();
		EditorRuntimeAccess runtime = proxy((proxy, method, args) -> {
			calls.add(method.getName());
			return switch (method.getName()) {
				case "allGroups", "allItems" -> List.of();
				case "previewTooltip" -> null;
				default -> throw new AssertionError(method.getName());
			};
		});
		ViewerLifecycleCoordinator coordinator = coordinator();
		try (var ignored = coordinator.register(adapter(runtime))) {
			EditorGroupAccess groups = EditorRuntimeServices.groups(coordinator);
			EditorIngredientAccess ingredients = EditorRuntimeServices.ingredients(coordinator);
			EditorPresentationAccess presentation = EditorRuntimeServices.presentation(coordinator);
			assertSame(runtime, groups);
			assertSame(runtime, ingredients);
			assertSame(runtime, presentation);
			assertTrue(groups.allGroups().isEmpty());
			assertTrue(ingredients.allItems().isEmpty());
			assertNull(presentation.previewTooltip("name", 0, 0, 0, 0, false, List.of()));
			assertEquals(List.of("allGroups", "allItems", "previewTooltip"), calls);
		}
	}

	@Test void unavailableRuntimeMakesEveryFocusedViewUnavailable() {
		ViewerLifecycleCoordinator coordinator = coordinator();
		assertTrue(EditorRuntimeServices.find(coordinator).isEmpty());
		assertTrue(EditorRuntimeServices.findGroups(coordinator).isEmpty());
		assertTrue(EditorRuntimeServices.findIngredients(coordinator).isEmpty());
		assertTrue(EditorRuntimeServices.findPresentation(coordinator).isEmpty());
		for (var access : List.<Runnable>of(() -> EditorRuntimeServices.groups(coordinator),
			() -> EditorRuntimeServices.ingredients(coordinator), () -> EditorRuntimeServices.presentation(coordinator))) {
			var error = assertThrows(IllegalStateException.class, access::run);
			assertEquals("No active recipe-viewer editor runtime", error.getMessage());
		}
	}

	@Test void aggregatePreservesIngredientAndPresentationDefaults() {
		EditorRuntimeAccess runtime = proxy((proxy, method, args) -> {
			if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
			throw new AssertionError(method.getName());
		});
		EditorIngredientAccess ingredients = runtime;
		EditorPresentationAccess presentation = runtime;
		assertSame(EditorIngredientTypes.UNAVAILABLE, ingredients.ingredientTypes());
		assertSame(EditorIngredientTags.UNAVAILABLE, ingredients.ingredientTags("test:type"));
		assertSame(EditorIngredientIds.UNAVAILABLE, ingredients.ingredientIds("test:type"));
		assertSame(runtime, presentation.previewGeneration());
		assertDoesNotThrow(presentation::closeEditor);
	}

	@Test void aggregateRetainsDirectMethodDeclarationsForExistingBinaries() throws Exception {
		assertEquals(EditorRuntimeAccess.class, EditorRuntimeAccess.class.getDeclaredMethod("allGroups").getDeclaringClass());
		assertEquals(EditorRuntimeAccess.class, EditorRuntimeAccess.class.getDeclaredMethod("ingredientTypes").getDeclaringClass());
		assertEquals(EditorRuntimeAccess.class, EditorRuntimeAccess.class.getDeclaredMethod("previewGeneration").getDeclaringClass());
		assertEquals(EditorRuntimeAccess.class, EditorRuntimeAccess.class.getDeclaredMethod("renderFluid",
			net.minecraft.client.gui.GuiGraphics.class, EditorFluidIngredientView.class, int.class, int.class).getDeclaringClass());
	}

	private static EditorRuntimeAccess proxy(InvocationHandler handler) {
		return (EditorRuntimeAccess) Proxy.newProxyInstance(EditorRuntimeAccess.class.getClassLoader(),
			new Class<?>[]{EditorRuntimeAccess.class}, handler);
	}

	private static ViewerLifecycleCoordinator coordinator() {
		return new ViewerLifecycleCoordinator(new ViewerLifecycleCoordinator.Environment(false, true, false),
			Set.of("emi"), ignored -> {});
	}

	@SuppressWarnings("unchecked")
	private static ViewerAdapter<Object, Object> adapter(EditorRuntimeAccess runtime) {
		return (ViewerAdapter<Object, Object>) Proxy.newProxyInstance(ViewerAdapter.class.getClassLoader(),
			new Class<?>[]{ViewerAdapter.class}, (proxy, method, args) -> switch (method.getName()) {
				case "id" -> "emi";
				case "editorRuntimeAccess" -> runtime;
				case "onGroupChange" -> null;
				default -> throw new AssertionError(method.getName());
			});
	}
}
