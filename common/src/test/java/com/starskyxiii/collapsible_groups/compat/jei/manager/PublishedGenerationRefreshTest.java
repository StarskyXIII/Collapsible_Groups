package com.starskyxiii.collapsible_groups.compat.jei.manager;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublishedGenerationRefreshTest {
	@Test void publicationSchedulesExactlyOneRenderThreadRebuild() {
		PublishedGenerationRefresh refresh = new PublishedGenerationRefresh();
		ControlledExecutor renderThread = new ControlledExecutor();
		CompletableFuture<Void> readiness = new CompletableFuture<>();
		AtomicInteger rebuilds = new AtomicInteger();

		refresh.schedule(true, readiness, renderThread, rebuilds::incrementAndGet);
		refresh.schedule(true, readiness, renderThread, rebuilds::incrementAndGet);
		readiness.complete(null);

		assertEquals(1, renderThread.size());
		assertEquals(0, rebuilds.get());
		renderThread.runNext();
		assertEquals(1, rebuilds.get());
	}

	private static final class ControlledExecutor implements Executor {
		private final Queue<Runnable> tasks = new ArrayDeque<>();
		@Override public void execute(Runnable command) { tasks.add(command); }
		int size() { return tasks.size(); }
		void runNext() { tasks.remove().run(); }
	}
}
