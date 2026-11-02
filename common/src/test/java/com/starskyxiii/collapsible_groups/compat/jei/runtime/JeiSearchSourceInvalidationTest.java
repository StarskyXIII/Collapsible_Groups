package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerAdapter;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerGroupIndex;
import com.starskyxiii.collapsible_groups.group.GroupRepositoryTestAccess;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.overlay.elements.IElement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JeiSearchSourceInvalidationTest {
	@Test
	void searchesReuseBootstrapAndBatchedAddRemoveUpdateVisibilityAndSortChangesReplaceIt() throws Exception {
		IIngredientType<String> type = new IIngredientType<>() {
			public Class<? extends String> getIngredientClass() { return String.class; }
			public String getUid() { return "test:source_invalidation"; }
		};
		var uidCalls = new AtomicInteger();
		var helper = (IIngredientHelper<?>) Proxy.newProxyInstance(IIngredientHelper.class.getClassLoader(),
			new Class<?>[]{IIngredientHelper.class}, (proxy, method, args) -> {
				Object value = args != null && args.length > 0 ? args[0] : null;
				if (value instanceof ITypedIngredient<?> typed) value = typed.getIngredient();
				return switch (method.getName()) {
					case "getUid", "getUniqueId" -> { uidCalls.incrementAndGet(); yield value; }
					case "getIngredientType" -> type;
					default -> throw new AssertionError(method.getName());
				};
			});
		var manager = (IIngredientManager) Proxy.newProxyInstance(IIngredientManager.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class}, (proxy, method, args) -> switch (method.getName()) {
				case "getRegisteredIngredientTypes" -> List.of(type);
				case "getIngredientHelper" -> helper;
				default -> throw new AssertionError(method.getName());
			});
		var first = typed(type, "first");
		var second = typed(type, "second");
		var third = typed(type, "third");
		var input = new AtomicReference<List<ITypedIngredient<?>>>(List.of(first, second));
		var query = new AtomicReference<>("first");
		var cache = new AtomicReference<List<IElement<?>>>();
		var context = new AtomicReference<JeiViewerAdapter.ProjectionContext>();
		var sources = new AtomicInteger();
		var invalidations = new AtomicInteger();
		var hooks = new JeiIngredientFilterController.PlatformHooks() {
			public boolean hasFluidType() { return false; }
			public IElement<?> createFluidChild(ITypedIngredient<?> value, String group) { throw new AssertionError(); }
			public JeiIngredientFilterController.GenericProbe genericProbe() { return JeiIngredientFilterController.exactGenericProbe(); }
			public JeiIngredientFilterController.FluidCachePolicy fluidCachePolicy() {
				return JeiIngredientFilterController.FluidCachePolicy.INDEPENDENT;
			}
			public boolean beforeIndex(JeiViewerAdapter.IngredientSourceSnapshot source, IIngredientManager ingredients) {
				context.set(JeiViewerAdapter.instance().updateBootstrap(source, ingredients));
				return false;
			}
		};
		var controller = new JeiIngredientFilterController(query::get, text -> {
			if (text.isEmpty()) sources.incrementAndGet();
			return input.get().stream().filter(value -> value.getIngredient().toString().contains(text));
		}, () -> {}, manager, cache::get, cache::set, hooks);
		var invalidate = JeiIngredientFilterController.class.getDeclaredMethod("invalidateSource");
		invalidate.setAccessible(true);
		var index = JeiViewerGroupIndex.instance();
		index.reset();
		GroupRepositoryTestAccess.replace(List.of());
		index.configureSourceInvalidation(() -> {
			invalidations.incrementAndGet();
			try { invalidate.invoke(controller); } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
		});
		try {
			assertEquals(List.of(first), entries(controller.getElements()));
			var initial = context.get();
			int resolved = uidCalls.get();
			assertTrue(resolved > 0);
			for (String search : List.of("second", "absent", "first")) {
				query.set(search);
				cache.set(null);
				controller.getElements();
				assertSame(initial, context.get());
			}
			assertEquals(resolved, uidCalls.get());
			assertEquals(1, sources.get());
			assertEquals(0, invalidations.get());

			query.set("");
			controller.sourceChanged(); // entry hook runs before JEI changes its source
			controller.sourceChanged(); // a nested visibility/add callback belongs to the same batch
			controller.sourceChanged();
			assertEquals(0, invalidations.get());
			assertEquals(1, sources.get(), "Entry notifications must not read the source before JEI finishes");
			var added = JeiIngredientFilterHook.getElementsAfterDirtyStateUpdate(
				() -> input.set(List.of(first, second, third)), controller::getElements);
			assertEquals(input.get(), entries(added));
			assertEquals(1, invalidations.get());
			assertNotSame(initial, context.get());
			var afterAdd = context.get();

			for (List<ITypedIngredient<?>> change : List.of(
				List.<ITypedIngredient<?>>of(first, second), // remove
				List.<ITypedIngredient<?>>of(first), // hide second
				List.<ITypedIngredient<?>>of(first, second), // show second
				List.<ITypedIngredient<?>>of(second, first), // sort
				List.<ITypedIngredient<?>>of(second, typed(type, "updated")))) { // update wrapper/identity
				var previous = context.get();
				input.set(change);
				controller.sourceChanged();
				assertEquals(change, entries(controller.getElements()));
				assertNotSame(previous, context.get());
			}
			assertEquals(6, invalidations.get());
			assertEquals(3, afterAdd.universe().ordered().size());
			assertEquals(List.of(first, second), initial.universe().ordered().stream().map(value -> value.entry()).toList());
		} finally {
			index.reset();
			JeiViewerAdapter.unregisterRuntime();
			GroupRepositoryTestAccess.replace(List.of());
		}
	}

	private static List<ITypedIngredient<?>> entries(List<IElement<?>> elements) {
		return elements.stream().<ITypedIngredient<?>>map(IElement::getTypedIngredient).toList();
	}

	private static ITypedIngredient<String> typed(IIngredientType<String> type, String value) {
		return new ITypedIngredient<>() {
			public IIngredientType<String> getType() { return type; }
			public String getIngredient() { return value; }
			public ITypedIngredient<String> normalize(IIngredientHelper<String> ignored) { return this; }
		};
	}
}
