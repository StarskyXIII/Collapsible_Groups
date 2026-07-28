package com.starskyxiii.collapsible_groups.compat.jei.manager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Coalesces manager refreshes to one render-executor action per readiness publication. */
final class PublishedGenerationRefresh {
	private CompletableFuture<Void> observed;

	synchronized void schedule(boolean publicationPending, CompletableFuture<Void> readiness,
		Executor renderExecutor, Runnable rebuild) {
		if (!publicationPending || readiness == observed) return;
		observed = readiness;
		readiness.whenComplete((ignored, failure) -> renderExecutor.execute(() -> complete(readiness, failure, rebuild)));
	}

	private void complete(CompletableFuture<Void> readiness, Throwable failure, Runnable rebuild) {
		synchronized (this) {
			if (observed != readiness) return;
		}
		if (failure == null) rebuild.run();
	}
}
