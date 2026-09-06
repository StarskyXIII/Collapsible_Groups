package com.starskyxiii.collapsible_groups.client.editor;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class EditorTagCatalog {
	public record Source(Object identity, Supplier<Stream<String>> tags) {}
	private final LongSupplier clock;
	private java.lang.ref.WeakReference<Object> generation = new java.lang.ref.WeakReference<>(null);
	private boolean hasGeneration;
	private String type;
	private EditorIngredientTags snapshot = EditorIngredientTags.UNAVAILABLE;
	private Job job;
	private boolean completed;

	public EditorTagCatalog() { this(System::nanoTime); }
	EditorTagCatalog(LongSupplier clock) { this.clock = clock; }

	public EditorIngredientTags snapshot(Object current, String requested) {
		return sameGeneration(current) && java.util.Objects.equals(type, requested)
			? snapshot : EditorIngredientTags.UNAVAILABLE;
	}

	public void update(Object current, String requested, EditorIngredientTags.Status status,
		EditorIngredientTags.Coverage coverage, Supplier<Iterator<Source>> sources, BooleanSupplier valid) {
		if (!sameGeneration(current) || !java.util.Objects.equals(type, requested)) {
			clear();
			generation = new java.lang.ref.WeakReference<>(current);
			hasGeneration = current != null;
			type = requested;
		}
		if (status != EditorIngredientTags.Status.READY) {
			cancel();
			completed = false;
			if (snapshot.status() != status || !snapshot.type().equals(requested))
				snapshot = new EditorIngredientTags(new Object(), requested, status, coverage, false, List.of());
			return;
		}
		if (job == null && !completed) {
			snapshot = new EditorIngredientTags(new Object(), requested, EditorIngredientTags.Status.PENDING, coverage, false, List.of());
			try { job = new Job(sources.get()); }
			catch (RuntimeException | LinkageError failure) {
				snapshot = new EditorIngredientTags(new Object(), requested, EditorIngredientTags.Status.UNAVAILABLE, coverage, true, List.of());
				completed = true;
				return;
			}
		}
		Job active = job;
		if (active == null) return;
		if (!valid.getAsBoolean()) { clear(); return; }
		long started = clock.getAsLong();
		int calls = 0;
		int steps = 0;
		while (job == active && calls < 16 && steps < 256 && clock.getAsLong() - started < 2_000_000) {
			steps++;
			boolean advancing = false;
			try {
				if (active.iterator != null) {
					if (active.iterator.hasNext()) {
						String tag = active.iterator.next();
						if (tag == null) active.failures++; else active.tags.add(tag);
					} else {
						active.successes++;
						active.closeStream();
					}
				} else {
					advancing = true;
					if (!active.exhausted && active.sources.hasNext()) {
						Source source = active.sources.next();
						advancing = false;
						if (source.identity() != null && !active.seen.add(source.identity())) continue;
						calls++;
						active.stream = source.tags().get();
						if (job != active) { active.closeStream(); return; }
						if (!valid.getAsBoolean()) { clear(); return; }
						if (active.stream == null) { active.failures++; continue; }
						active.iterator = active.stream.iterator();
					} else {
						if (job != active) { active.closeStream(); return; }
						if (!valid.getAsBoolean()) { clear(); return; }
						var next = new EditorIngredientTags(new Object(), requested,
							active.successes > 0 || !active.tags.isEmpty() ? EditorIngredientTags.Status.READY : EditorIngredientTags.Status.UNAVAILABLE,
							coverage, active.failures > 0, List.copyOf(active.tags));
						if (job != active) { active.closeStream(); return; }
						if (!valid.getAsBoolean()) { clear(); return; }
						cancel();
						snapshot = next;
						completed = true;
						if (active.failures > 0) com.starskyxiii.collapsible_groups.Constants.LOG.debug(
							"Tag catalog for {} retained {} tags with {} source errors", requested, next.tags().size(), active.failures);
						return;
					}
				}
			} catch (RuntimeException | LinkageError failure) {
				active.failures++;
				if (advancing) active.exhausted = true;
				active.closeStream();
			}
		}
		if (job != active) active.closeStream();
		else if (!valid.getAsBoolean()) clear();
	}

	public void cancel() {
		Job previous = job;
		job = null;
		if (previous != null) previous.closeStream();
	}

	public void clear() {
		cancel();
		generation.clear();
		hasGeneration = false;
		type = null;
		completed = false;
		snapshot = EditorIngredientTags.UNAVAILABLE;
	}

	private boolean sameGeneration(Object current) {
		return current == null ? !hasGeneration : generation.get() == current;
	}

	private static final class Job {
		final Iterator<Source> sources;
		final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		final TreeSet<String> tags = new TreeSet<>();
		Stream<String> stream;
		Iterator<String> iterator;
		int successes;
		int failures;
		boolean exhausted;
		Job(Iterator<Source> sources) { this.sources = sources; }
		void closeStream() {
			var previous = stream;
			stream = null;
			iterator = null;
			if (previous != null) {
				try { previous.close(); } catch (RuntimeException | LinkageError failure) { failures++; }
			}
		}
	}
}
