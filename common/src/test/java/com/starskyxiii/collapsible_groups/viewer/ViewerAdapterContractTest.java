package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.core.IngredientView;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
class ViewerAdapterContractTest {
	@Test
	void fakeAdapterRegistersReceivesRebuildKindsAndUnregisters() {
		ViewerIngredient<String> chemical = chemical("oxygen");
		ViewerIngredientUniverse<String> universe = new ViewerIngredientUniverse<>(List.of(chemical));
		FakeViewerAdapter adapter = new FakeViewerAdapter(
			universe,
			List.of(new ViewerIngredientType<>("test:chemical", List.of("chemical"), List.of(chemical))),
			new ViewerSearchSnapshot<>("", universe.ordered(), false, 0)
		);
		ViewerAdapterRegistry registry = new ViewerAdapterRegistry();

		ViewerRegistration registration = registry.register(adapter);
		assertEquals(List.of(adapter), registry.activeAdapters());
		assertThrows(IllegalStateException.class, () -> registry.register(adapter));
		GroupChangeEvent.publish(GroupChangeEvent.Kind.FULL);
		GroupChangeEvent.publish(GroupChangeEvent.Kind.KUBEJS_REPLACE);
		assertEquals(List.of(GroupChangeEvent.Kind.FULL, GroupChangeEvent.Kind.KUBEJS_REPLACE), adapter.changes());

		registration.close();
		GroupChangeEvent.publish(GroupChangeEvent.Kind.STRUCTURE);
		assertTrue(registry.activeAdapters().isEmpty());
		assertEquals(2, adapter.changes().size());
	}

	@Test
	void bootstrapProjectsCustomTypeGroupBeforeRuntimeUniverseIsAvailable() {
		ViewerIngredient<String> oxygen = chemical("oxygen");
		ViewerIngredient<String> hydrogen = chemical("hydrogen");
		ViewerIngredientUniverse<String> universe = new ViewerIngredientUniverse<>(List.of(oxygen, hydrogen));
		FakeViewerAdapter adapter = new FakeViewerAdapter(
			universe,
			List.of(new ViewerIngredientType<>("test:chemical", List.of("chemical"), List.of(oxygen, hydrogen))),
			new ViewerSearchSnapshot<>("", universe.ordered(), false, 0)
		);

		assertThrows(IllegalStateException.class, () -> adapter.universeProvider().universe());
		ViewerIngredientType<String> resolved = adapter.bootstrapContext().resolveType("chemical").orElseThrow();
		assertEquals("test:chemical", resolved.canonicalId());
		assertEquals(List.of(oxygen, hydrogen), resolved.ingredients());
		assertEquals(universe, adapter.bootstrapContext().universe());
		ViewerProjection<String> projection = GroupProjectionEngine.project(
			adapter.bootstrapContext().universe(),
			new ViewerSearchSnapshot<>("", resolved.ingredients(), false, 0),
			List.of(new com.starskyxiii.collapsible_groups.core.GroupDefinition(
				"__kjs_generic_test", "KubeJS chemicals", true,
				com.starskyxiii.collapsible_groups.core.Filters.genericNamespace("test:chemical", "test")
			)),
			id -> false
		);
		ViewerProjection.GroupHeader<String> header = org.junit.jupiter.api.Assertions.assertInstanceOf(
			ViewerProjection.GroupHeader.class,
			projection.entries().getFirst()
		);
		assertEquals(List.of(oxygen, hydrogen), header.children());
		assertFalse(adapter.bookmarkPolicy().canBookmark(new ViewerProjection.DisplayHeader<>(header)));

		adapter.setRuntimeAvailable(true);
		assertEquals(universe, adapter.universeProvider().universe());
	}

	private static ViewerIngredient<String> chemical(String name) {
		return new ViewerIngredient<>(
			new ViewerIngredientIdentity("test:chemical", "test:" + name),
			ViewerIngredient.Kind.GENERIC,
			name,
			new IngredientView() {
				@Override
				public String ingredientType() {
					return "test:chemical";
				}

				@Override
				public Identifier resourceLocation() {
					return Identifier.parse("test:" + name);
				}

				@Override
				public boolean hasTag(Identifier tagId) {
					return false;
				}

				@Override
				public boolean matchesExactStack(String encodedStack) {
					return false;
				}
			}
		);
	}
}
