package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class JeiIngredientSourceStateTest {
	@BeforeAll static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@AfterEach void clear() { JeiIngredientSourceState.deactivate(); }

	@Test void lateEnumerationCannotResurrectClearedOrReplacedItems() throws Exception {
		for (boolean replace : List.of(false, true)) {
			var entered = new CountDownLatch(1);
			var release = new CountDownLatch(1);
			var old = List.of(new ItemStack(Items.STONE));
			var manager = manager(() -> { entered.countDown(); await(release); return old; });
			JeiIngredientSourceState.activate(runtime(manager));
			var executor = Executors.newSingleThreadExecutor();
			try {
				Future<?> work = executor.submit(JeiIngredientSourceState::populateIfEmpty);
				await(entered);
				JeiIngredientSourceState.clearItems();
				var fresh = List.of(new ItemStack(Items.DIRT));
				if (replace) {
					JeiIngredientSourceState.deactivate();
					var replacement = manager(() -> fresh);
					JeiIngredientSourceState.activate(runtime(replacement));
					JeiIngredientSourceState.populateIfEmpty();
				}
				release.countDown();
				work.get(10, TimeUnit.SECONDS);
				assertEquals(replace ? fresh : List.of(), JeiIngredientSourceState.items());
			} finally {
				release.countDown();
				executor.shutdownNow();
			}
		}
	}

	@Test void lateIndexBuildCannotPublishAfterClearOrSameListReplacement() throws Exception {
		for (boolean replace : List.of(false, true)) {
			var items = List.of(new ItemStack(Items.STONE));
			var manager = manager(() -> items);
			JeiIngredientSourceState.activate(runtime(manager));
			JeiIngredientSourceState.populateIfEmpty();
			var entered = new CountDownLatch(1);
			var release = new CountDownLatch(1);
			var executor = Executors.newSingleThreadExecutor();
			try {
				Future<EditorItemIndex> old = executor.submit(() -> JeiIngredientSourceState.editorIndex(snapshot -> {
					entered.countDown();
					await(release);
					return EditorItemIndex.build(snapshot);
				}));
				await(entered);
				JeiIngredientSourceState.clearItems();
				EditorItemIndex current = null;
				if (replace) {
					JeiIngredientSourceState.populateIfEmpty();
					current = JeiIngredientSourceState.editorIndex(EditorItemIndex::build);
				}
				release.countDown();
				assertSame(current, old.get(10, TimeUnit.SECONDS));
				assertSame(current, JeiIngredientSourceState.editorIndex(EditorItemIndex::build));
			} finally {
				release.countDown();
				executor.shutdownNow();
			}
		}
	}

	@Test void sameGenerationBuildersUseFirstPublishedIndex() throws Exception {
		var items = List.of(new ItemStack(Items.STONE));
		JeiIngredientSourceState.activate(runtime(manager(() -> items)));
		JeiIngredientSourceState.populateIfEmpty();
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var executor = Executors.newSingleThreadExecutor();
		try {
			Future<EditorItemIndex> late = executor.submit(() -> JeiIngredientSourceState.editorIndex(snapshot -> {
				entered.countDown();
				await(release);
				return EditorItemIndex.build(snapshot);
			}));
			await(entered);
			var current = JeiIngredientSourceState.editorIndex(EditorItemIndex::build);
			release.countDown();
			assertSame(current, late.get(10, TimeUnit.SECONDS));
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	@Test void queuedWarmNeverEnumeratesAfterUnregisterOrReregister() throws Exception {
		var calls = new AtomicInteger();
		var manager = manager(() -> { calls.incrementAndGet(); return List.of(new ItemStack(Items.STONE)); });
		JeiIngredientSourceState.activate(runtime(manager));
		var release = new CountDownLatch(1);
		var executor = Executors.newSingleThreadExecutor();
		try {
			executor.submit(() -> await(release));
			Future<?> warm = executor.submit(JeiIngredientSourceState::warmEditorIndex);
			JeiIngredientSourceState.deactivate();
			JeiIngredientSourceState.activate(runtime(manager));
			release.countDown();
			warm.get(10, TimeUnit.SECONDS);
			assertEquals(0, calls.get());
			assertTrue(JeiIngredientSourceState.itemsEmpty());
			JeiIngredientSourceState.deactivate();
			JeiIngredientSourceState.warmEditorIndex();
			JeiIngredientSourceState.populateIfEmpty();
			assertEquals(0, calls.get());
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	@Test void controllerTokensRejectPriorRuntimeAndRepeatedInstalls() {
		var items = List.of(new ItemStack(Items.STONE));
		var manager = manager(() -> items);
		var runtime = runtime(manager);
		JeiIngredientSourceState.activate(runtime);
		var old = JeiIngredientSourceState.capture(manager);
		JeiIngredientSourceState.deactivate();
		JeiIngredientSourceState.activate(runtime);
		assertFalse(JeiIngredientSourceState.install(old, items, null));
		var current = JeiIngredientSourceState.capture(manager);
		assertTrue(JeiIngredientSourceState.install(current, items, null));
		var index = JeiIngredientSourceState.editorIndex(EditorItemIndex::build);
		assertFalse(JeiIngredientSourceState.install(current, items, null));
		assertFalse(JeiIngredientSourceState.install(JeiIngredientSourceState.capture(manager), items, List.of()));
		assertSame(index, JeiIngredientSourceState.editorIndex(EditorItemIndex::build));
		assertNull(JeiIngredientSourceState.capture(manager(List::of)));
	}

	private static void await(CountDownLatch latch) {
		try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
		catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
	}

	private static IIngredientManager manager(Supplier<List<ItemStack>> items) {
		return (IIngredientManager) Proxy.newProxyInstance(IIngredientManager.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class}, (proxy, method, args) -> {
				if (method.getName().equals("getAllIngredients")) return items.get();
				throw new UnsupportedOperationException(method.toString());
			});
	}

	private static IJeiRuntime runtime(IIngredientManager manager) {
		return (IJeiRuntime) Proxy.newProxyInstance(IJeiRuntime.class.getClassLoader(),
			new Class<?>[]{IJeiRuntime.class}, (proxy, method, args) -> {
				if (method.getName().equals("getIngredientManager")) return manager;
				throw new UnsupportedOperationException(method.toString());
			});
	}
}
