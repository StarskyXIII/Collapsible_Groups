package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiIngredientFilterControllerTest {
	@Test
	void configuredBuildRetainsTheSourceAndEvaluatesOnlyChangedGroupsUntilInvalidation() throws Exception {
		IIngredientType<String> type = new IIngredientType<>() {
			public Class<? extends String> getIngredientClass() { return String.class; }
			public String getUid() { return "test:incremental"; }
		};
		var itemProbes = new java.util.concurrent.atomic.AtomicInteger();
        var ingredient = typed(type, "test:one", itemProbes);
		var helper = (IIngredientHelper<?>) java.lang.reflect.Proxy.newProxyInstance(
			IIngredientHelper.class.getClassLoader(), new Class<?>[]{IIngredientHelper.class}, (proxy, method, args) -> {
				return switch (method.getName()) {
					case "getResourceLocation" -> new net.minecraft.resources.ResourceLocation((String) args[0]);
					case "getUid", "getUniqueId", "getDisplayName", "getErrorInfo", "copyIngredient" -> args[0];
					case "getIngredientType" -> type;
					case "getTagStream" -> java.util.stream.Stream.empty();
					default -> throw new AssertionError(method.getName());
				};
			});
		var manager = (mezz.jei.api.runtime.IIngredientManager) java.lang.reflect.Proxy.newProxyInstance(
			mezz.jei.api.runtime.IIngredientManager.class.getClassLoader(),
			new Class<?>[]{mezz.jei.api.runtime.IIngredientManager.class}, (proxy, method, args) -> switch (method.getName()) {
				case "getRegisteredIngredientTypes" -> List.of(type);
				case "getIngredientHelper" -> helper;
				default -> throw new AssertionError(method.getName());
			});
		var evaluated = new java.util.ArrayList<String>();
		var hooks = new JeiIngredientFilterController.PlatformHooks() {
			public boolean hasFluidType() { return false; }
			public mezz.jei.gui.overlay.elements.IElement<?> createFluidChild(ITypedIngredient<?> value, String id) { return null; }
			public JeiIngredientFilterController.FluidCachePolicy fluidCachePolicy() {
				return JeiIngredientFilterController.FluidCachePolicy.INDEPENDENT;
			}
			public JeiIngredientFilterController.GenericProbe genericProbe() {
				return (value, source, owners, groups, matches, candidates, failures) -> {
					groups.forEach(group -> evaluated.add(group.id()));
					JeiIngredientFilterController.exactGenericProbe().index(value, source, owners, groups, matches, candidates, failures);
				};
			}
		};
		var enumerations = new java.util.concurrent.atomic.AtomicInteger();
		var controller = new JeiIngredientFilterController(() -> "", text -> {
			enumerations.incrementAndGet();
			return java.util.stream.Stream.of(ingredient);
		}, () -> {}, manager, List::of, ignored -> {}, hooks);
		var build = JeiIngredientFilterController.class.getDeclaredMethod("buildConfiguredIndex");
		build.setAccessible(true);
		var invalidate = JeiIngredientFilterController.class.getDeclaredMethod("invalidateSource");
		invalidate.setAccessible(true);
		var stable = new com.starskyxiii.collapsible_groups.group.GroupDefinition("stable", "stable", true,
			new com.starskyxiii.collapsible_groups.group.filter.GroupFilter.Id(type.getUid(), "test:one"));
		var edited = new com.starskyxiii.collapsible_groups.group.GroupDefinition("edited", "edited", true,
			new com.starskyxiii.collapsible_groups.group.filter.GroupFilter.Id(type.getUid(), "test:two"));
		try {
			com.starskyxiii.collapsible_groups.group.GroupRepositoryTestAccess.replace(List.of(stable, edited));
			var first = (com.starskyxiii.collapsible_groups.compat.jei.JeiViewerGroupIndex.Generation) build.invoke(controller);
			assertEquals(1, enumerations.get());
			assertEquals(2, evaluated.size());
			evaluated.clear();
			edited = edited.withFilter(stable.filter());
			com.starskyxiii.collapsible_groups.group.GroupRepositoryTestAccess.replace(List.of(stable, edited));
			var next = (com.starskyxiii.collapsible_groups.compat.jei.JeiViewerGroupIndex.Generation) build.invoke(controller);
			assertEquals(List.of("edited"), evaluated);
			assertSame(first.projectionContext(), next.projectionContext());
			assertSame(first.fullMatchGeneric().get("stable"), next.fullMatchGeneric().get("stable"));
			assertEquals(1, next.fullMatchGeneric().get("edited").size());
			assertEquals(1, enumerations.get());
			evaluated.clear();
			com.starskyxiii.collapsible_groups.group.GroupRepositoryTestAccess.replace(List.of(stable.withEnabled(false).withPriority(10)));
			itemProbes.set(0);
			var removed = (com.starskyxiii.collapsible_groups.compat.jei.JeiViewerGroupIndex.Generation) build.invoke(controller);
			assertTrue(evaluated.isEmpty());
            assertEquals(0, itemProbes.get());
			assertEquals(java.util.Set.of("stable"), removed.fullMatchGeneric().keySet());
			invalidate.invoke(controller);
			var reloaded = (com.starskyxiii.collapsible_groups.compat.jei.JeiViewerGroupIndex.Generation) build.invoke(controller);
			assertEquals(List.of("stable"), evaluated);
			org.junit.jupiter.api.Assertions.assertNotSame(next.projectionContext(), reloaded.projectionContext());
			assertEquals(2, enumerations.get());
		} finally {
			com.starskyxiii.collapsible_groups.group.GroupRepositoryTestAccess.replace(List.of());
			com.starskyxiii.collapsible_groups.compat.jei.JeiViewerGroupIndex.instance().reset();
			com.starskyxiii.collapsible_groups.compat.jei.JeiViewerAdapter.unregisterRuntime();
		}
	}

	@Test
	void rawFallbackPreservesIngredientOrderAndContainsNoGroupState() {
		IIngredientType<String> type = new IIngredientType<>() {
			@Override public Class<? extends String> getIngredientClass() { return String.class; }
			@Override public String getUid() { return "test:raw_fallback"; }
		};
		ITypedIngredient<String> first = typed(type, "first");
		ITypedIngredient<String> second = typed(type, "second");

		JeiIngredientFilterController.RawStructure raw =
			JeiIngredientFilterController.buildRawStructure(List.of(first, second));

		assertEquals(2, raw.elements().size());
		assertSame(first, raw.elements().get(0).getTypedIngredient());
		assertSame(second, raw.elements().get(1).getTypedIngredient());
		assertEquals(2, raw.groupIds().size());
		assertTrue(raw.groupIds().stream().allMatch(id -> id == null));
		assertTrue(raw.childrenByGroupId().isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> raw.elements().clear());
		assertThrows(UnsupportedOperationException.class, () -> raw.groupIds().clear());
	}

	private static ITypedIngredient<String> typed(IIngredientType<String> type, String value) {
		return typed(type, value, new java.util.concurrent.atomic.AtomicInteger());
	}

    private static ITypedIngredient<String> typed(IIngredientType<String> type, String value, java.util.concurrent.atomic.AtomicInteger probes) {
		return new ITypedIngredient<String>() {
			@Override public IIngredientType<String> getType() { return type; }
			@Override public String getIngredient() { return value; }
            @Override public java.util.Optional<net.minecraft.world.item.ItemStack> getItemStack() { probes.incrementAndGet(); return java.util.Optional.empty(); }
			public ITypedIngredient<String> normalize(IIngredientHelper<String> helper) { return this; }
		};
	}
}
