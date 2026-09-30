package com.starskyxiii.collapsible_groups.client.editor;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class EditorIdCatalog {
	private final int maxReads;
	private final LongSupplier clock;
	private WeakReference<Object> generation = new WeakReference<>(null);
	private boolean hasGeneration;
	private String type;
	private EditorIngredientIds snapshot = EditorIngredientIds.UNAVAILABLE;
	private Job job;
	private boolean completed;

	public EditorIdCatalog(int maxReads) { this(maxReads, System::nanoTime); }
	EditorIdCatalog(int maxReads, LongSupplier clock) {
		if (maxReads < 1 || maxReads > 256) throw new IllegalArgumentException("maxReads");
		this.maxReads = maxReads;
		this.clock = clock;
	}

	public EditorIngredientIds snapshot(Object current, String requested) {
		return sameGeneration(current) && Objects.equals(type, requested) ? snapshot : EditorIngredientIds.UNAVAILABLE;
	}

	public void update(Object current, String requested, EditorIngredientIds.Status status,
		Supplier<Iterator<Supplier<String>>> sources, BooleanSupplier valid) {
		if (!sameGeneration(current) || !Objects.equals(type, requested)) {
			clear();
			generation = new WeakReference<>(current);
			hasGeneration = current != null;
			type = requested;
		}
		if (status != EditorIngredientIds.Status.READY) {
			cancel();
			completed = false;
			if (snapshot.status() != status || !snapshot.type().equals(requested))
				snapshot = new EditorIngredientIds(new Object(), requested, status, false, List.of());
			return;
		}
		if (!valid.getAsBoolean()) { clear(); return; }
		if (completed) return;
		if (job == null) {
			snapshot = new EditorIngredientIds(new Object(), requested, EditorIngredientIds.Status.PENDING, false, List.of());
			Job next = new Job();
			job = next;
			try { next.sources = Objects.requireNonNull(sources.get()); }
			catch (RuntimeException | LinkageError failure) { next.failures++; next.exhausted = true; }
			if (job != next) return;
		}
		Job active = job;
		long started = clock.getAsLong();
		int reads = 0;
		while (job == active && reads < maxReads && clock.getAsLong() - started < 2_000_000) {
			if (!valid.getAsBoolean()) { clear(); return; }
			Supplier<String> source;
			try {
				if (active.exhausted || !active.sources.hasNext()) {
					if (job != active) return;
					var next = new EditorIngredientIds(new Object(), requested,
						active.successes > 0 || active.failures == 0 ? EditorIngredientIds.Status.READY : EditorIngredientIds.Status.UNAVAILABLE,
						active.missing > 0 || active.failures > 0, List.copyOf(active.ids));
					if (!valid.getAsBoolean()) { clear(); return; }
					if (job != active) return;
					cancel();
					snapshot = next;
					completed = true;
					if (active.failures > 0) com.starskyxiii.collapsible_groups.Constants.LOG.debug(
						"ID catalog for {} retained {} IDs with {} source errors", requested, next.ids().size(), active.failures);
					return;
				}
				source = active.sources.next();
			} catch (RuntimeException | LinkageError failure) {
				active.failures++;
				active.exhausted = true;
				continue;
			}
			if (job != active) return;
			reads++;
			try {
				String id = source.get();
				if (job != active) return;
				active.successes++;
				if (id == null) active.missing++; else active.ids.add(id);
			} catch (RuntimeException | LinkageError failure) { active.failures++; }
		}
		if (job == active && !valid.getAsBoolean()) clear();
	}

	public void cancel() { job = null; }
	public void clear() {
		cancel();
		generation.clear();
		hasGeneration = false;
		type = null;
		completed = false;
		snapshot = EditorIngredientIds.UNAVAILABLE;
	}
	private boolean sameGeneration(Object current) { return current == null ? !hasGeneration : generation.get() == current; }

	private static final class Job {
		Iterator<Supplier<String>> sources;
		final TreeSet<String> ids = new TreeSet<>();
		int successes;
		int missing;
		int failures;
		boolean exhausted;
	}
}
