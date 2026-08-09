package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerProjection;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

@SuppressWarnings("removal")
class JeiViewerAdapterBootstrapTest {
	@Test
	@SuppressWarnings("unchecked")
	void customTypeProjectsFromBootstrapBeforeRuntimeIsAvailable() {
		IIngredientType<String> type = new IIngredientType<>() {
			@Override public Class<? extends String> getIngredientClass() { return String.class; }
			@Override public String getUid() { return "test:bootstrap_chemical"; }
		};
		IIngredientHelper<String> helper = helper(type);
		ITypedIngredient<String> oxygen = typed(type, "oxygen");
		ITypedIngredient<String> hydrogen = typed(type, "hydrogen");
		List<ITypedIngredient<?>> all = List.of(oxygen, hydrogen);
		IIngredientManager manager = (IIngredientManager) Proxy.newProxyInstance(
			getClass().getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> {
				if (method.getName().equals("getRegisteredIngredientTypes")) return List.of(type);
				if (method.getName().equals("getIngredientHelper")) return helper;
				if (method.getName().equals("getAllTypedIngredients")) return all;
				throw new UnsupportedOperationException(method.toString());
			}
		);
		GroupDefinition group = new GroupDefinition(
			"__kjs_test_bootstrap",
			"Bootstrap chemicals",
			true,
			Filters.genericNamespace("test:bootstrap_chemical", "test")
		);

		JeiRuntimeHolder.set(null);
		JeiViewerAdapter adapter = JeiViewerAdapter.instance();
		try {
			GroupCandidateIndex ownership =
				adapter.buildOwnershipIndex(all, manager, List.of(group));
			ViewerIngredientType<ITypedIngredient<?>> bootstrapType =
				adapter.bootstrapContext().resolveType("test:bootstrap_chemical").orElseThrow();
			assertEquals("test:bootstrap_chemical", bootstrapType.canonicalId());
			assertEquals(2, bootstrapType.ingredients().size());
			assertFalse(JeiRuntimeHolder.isAvailable());

			ViewerProjection<ITypedIngredient<?>> projection = adapter.project(
				all, "", false, 0, List.of(group), id -> false, ownership
			);
			ViewerProjection.GroupHeader<ITypedIngredient<?>> header = assertInstanceOf(
				ViewerProjection.GroupHeader.class,
				projection.entries().getFirst()
			);
			assertEquals("__kjs_test_bootstrap", header.group().id());
			assertEquals(List.of(oxygen, hydrogen), header.children().stream()
				.map(ingredient -> ingredient.entry())
				.toList());
		} finally {
			JeiViewerAdapter.unregisterRuntime();
			JeiRuntimeHolder.set(null);
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void typedIconsResolveCanonicalAndJeiAliasFromBootstrapUniverseAndFallbackOnDrift() {
		IIngredientType<String> type = new IIngredientType<>() {
			@Override public Class<? extends String> getIngredientClass() { return String.class; }
			@Override public String getUid() { return "test:icon_uid"; }
		};
		JeiIngredientTypes.register("test:icon_canonical", type);
		IIngredientHelper<String> helper = helper(type);
		ITypedIngredient<String> oxygen = typed(type, "oxygen");
		ITypedIngredient<String> hydrogen = typed(type, "hydrogen");
		List<ITypedIngredient<?>> all = List.of(oxygen, hydrogen);
		IIngredientManager manager = (IIngredientManager) Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{IIngredientManager.class}, (proxy, method, args) -> {
				if (method.getName().equals("getRegisteredIngredientTypes")) return List.of(type);
				if (method.getName().equals("getIngredientHelper")) return helper;
				if (method.getName().equals("getAllTypedIngredients")) return all;
				throw new UnsupportedOperationException(method.toString());
			});
		GroupDefinition group = new GroupDefinition("icon_group", "Icons", true,
			Filters.genericNamespace("test:icon_canonical", "test"), List.of(
				new GroupIconDefinition("test:icon_canonical", "test:oxygen"),
				new GroupIconDefinition("test:icon_uid", "test:hydrogen")));

		JeiViewerAdapter adapter = JeiViewerAdapter.instance();
		try {
			adapter.buildOwnershipIndex(all, manager, List.of(group));
			List<ITypedIngredient<?>> resolved = adapter.resolveHeaderIconIngredients(group.iconIds());
			assertEquals(2, resolved.size());
			assertSame(oxygen, resolved.get(0));
			assertSame(hydrogen, resolved.get(1));

			GroupIconDefinition drifted = new GroupIconDefinition("test:icon_uid", "test:missing");
			assertEquals(List.of(hydrogen), adapter.assembleHeaderIcons(List.of(drifted), List.of(
				adapter.bootstrapContext().universe().ordered().get(1))));
			GroupDefinition driftedGroup = group.withIconIds(List.of(drifted));
			JeiHeaderIconResolver.clearWarnings();
			assertEquals(1, JeiHeaderIconResolver.warnUnresolvedAfterBootstrap(
				List.of(driftedGroup), adapter.bootstrapContext().universe()));
			assertEquals(0, JeiHeaderIconResolver.warnUnresolvedAfterBootstrap(
				List.of(driftedGroup), adapter.bootstrapContext().universe()));
			assertEquals(List.of(drifted), driftedGroup.iconIds());
		} finally {
			JeiViewerAdapter.unregisterRuntime();
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void collidingUidStringsRemainDistinctWhileEquivalentWrappersAreCanonicalized() {
		IIngredientType<String> type = new IIngredientType<>() {
			@Override public Class<? extends String> getIngredientClass() { return String.class; }
			@Override public String getUid() { return "test:colliding_uid"; }
		};
		IIngredientHelper<String> helper = new IIngredientHelper<>() {
			@Override public IIngredientType<String> getIngredientType() { return type; }
			@Override public String getDisplayName(String ingredient) { return ingredient; }
			@Override public Object getUid(String ingredient, UidContext context) {
				return new CollisionUid("value-overload:" + ingredient);
			}
			@Override public Object getUid(ITypedIngredient<String> ingredient, UidContext context) {
				return new CollisionUid(ingredient.getIngredient());
			}
			@Override public Identifier getIdentifier(String ingredient) {
				return Identifier.parse("test:" + ingredient);
			}
			@Override public String copyIngredient(String ingredient) { return ingredient; }
			@Override public String getErrorInfo(String ingredient) { return ingredient; }
		};
		ITypedIngredient<String> oxygen = typed(type, "oxygen");
		ITypedIngredient<String> duplicateOxygen = typed(type, "oxygen");
		ITypedIngredient<String> hydrogen = typed(type, "hydrogen");
		List<ITypedIngredient<?>> all = List.of(oxygen, duplicateOxygen, hydrogen);
		IIngredientManager manager = (IIngredientManager) Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{IIngredientManager.class}, (proxy, method, args) -> {
				if (method.getName().equals("getRegisteredIngredientTypes")) return List.of(type);
				if (method.getName().equals("getIngredientHelper")) return helper;
				if (method.getName().equals("getAllTypedIngredients")) return all;
				throw new UnsupportedOperationException(method.toString());
			});
		GroupDefinition group = new GroupDefinition("collision_group", "Collisions", true,
			Filters.genericNamespace("test:colliding_uid", "test"));

		JeiViewerAdapter adapter = JeiViewerAdapter.instance();
		try {
			GroupCandidateIndex ownership = adapter.buildOwnershipIndex(all, manager, List.of(group));
			assertEquals(2, adapter.bootstrapContext().universe().ordered().size());
			assertEquals(2, adapter.bootstrapContext().resolveType("test:colliding_uid")
				.orElseThrow().ingredients().size());

			ViewerProjection<ITypedIngredient<?>> projection = adapter.project(
				all, "", false, 0, List.of(group), id -> false, ownership);
			ViewerProjection.GroupHeader<ITypedIngredient<?>> header = assertInstanceOf(
				ViewerProjection.GroupHeader.class, projection.entries().getFirst());
			assertEquals(List.of(oxygen, hydrogen), header.children().stream()
				.map(ingredient -> ingredient.entry()).toList());
		} finally {
			JeiViewerAdapter.unregisterRuntime();
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void preparedOwnershipKeepsProjectionOnItsOwnBootstrapGeneration() {
		IIngredientType<String> type = new IIngredientType<>() {
			@Override public Class<? extends String> getIngredientClass() { return String.class; }
			@Override public String getUid() { return "test:generation"; }
		};
		IIngredientHelper<String> helper = helper(type);
		ITypedIngredient<String> oxygen = typed(type, "oxygen");
		ITypedIngredient<String> hydrogen = typed(type, "hydrogen");
		List<ITypedIngredient<?>> all = List.of(oxygen, hydrogen);
		IIngredientManager manager = (IIngredientManager) Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{IIngredientManager.class}, (proxy, method, args) -> {
				if (method.getName().equals("getRegisteredIngredientTypes")) return List.of(type);
				if (method.getName().equals("getIngredientHelper")) return helper;
				if (method.getName().equals("getAllTypedIngredients")) return all;
				throw new UnsupportedOperationException(method.toString());
			});
		GroupDefinition group = new GroupDefinition("generation_group", "Generation", true,
			Filters.genericNamespace("test:generation", "test"));

		JeiViewerAdapter adapter = JeiViewerAdapter.instance();
		try {
			JeiViewerAdapter.PreparedOwnershipBuild prepared = adapter.buildOwnershipIndexFromMatches(
				all, manager, List.of(group), Map.of(
					oxygen, List.of(group.id()), hydrogen, List.of(group.id())));
			assertEquals(prepared.projectionContext().universe().byIdentity().keySet(),
				prepared.candidates().candidates().keySet());

			// Publish a different global bootstrap snapshot before projecting the prepared generation.
			adapter.updateBootstrap(List.of(hydrogen), manager);
			ViewerProjection<ITypedIngredient<?>> projection = adapter.project(
				all, "", false, 0, List.of(group), id -> false,
				prepared.projectionContext(), prepared.candidates());
			ViewerProjection.GroupHeader<ITypedIngredient<?>> header = assertInstanceOf(
				ViewerProjection.GroupHeader.class, projection.entries().getFirst());
			assertEquals(List.of(oxygen, hydrogen), header.children().stream()
				.map(ingredient -> ingredient.entry()).toList());
		} finally {
			JeiViewerAdapter.unregisterRuntime();
		}
	}

	private static IIngredientHelper<String> helper(IIngredientType<String> type) {
		return new IIngredientHelper<>() {
			@Override
			public IIngredientType<String> getIngredientType() {
				return type;
			}

			@Override
			public String getDisplayName(String ingredient) {
				return ingredient;
			}

			@Override
			public String getUid(String ingredient, UidContext context) {
				return "test:" + ingredient;
			}

			@Override
			public Identifier getIdentifier(String ingredient) {
				return Identifier.parse("test:" + ingredient);
			}

			@Override
			public String copyIngredient(String ingredient) {
				return ingredient;
			}

			@Override
			public String getErrorInfo(String ingredient) {
				return ingredient;
			}
		};
	}

	private static ITypedIngredient<String> typed(IIngredientType<String> type, String value) {
		return new ITypedIngredient<>() {
			@Override
			public IIngredientType<String> getType() {
				return type;
			}

			@Override
			public String getIngredient() {
				return value;
			}
		};
	}

	private record CollisionUid(String value) {
		@Override public String toString() { return "same-string"; }
	}
}
