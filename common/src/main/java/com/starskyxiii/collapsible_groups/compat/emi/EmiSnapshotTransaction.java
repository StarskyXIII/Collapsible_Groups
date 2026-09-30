package com.starskyxiii.collapsible_groups.compat.emi;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class EmiSnapshotTransaction<T> {
	record Stamp(long epoch, Object source, Object session) {
		boolean same(Stamp other) {
			return other != null && epoch == other.epoch && source == other.source && session == other.session;
		}
	}
	private final Object stateLock;
	private final Object reloadLock;
	private final Supplier<Stamp> current;
	private final Stamp stamp;
	private final T captured;

	private EmiSnapshotTransaction(Object stateLock, Object reloadLock, Supplier<Stamp> current, Stamp stamp, T captured) {
		this.stateLock = stateLock;
		this.reloadLock = reloadLock;
		this.current = current;
		this.stamp = stamp;
		this.captured = captured;
	}

	static <T> EmiSnapshotTransaction<T> capture(Object stateLock, Object reloadLock,
		Supplier<Stamp> current, Supplier<T> capture) {
		synchronized (stateLock) {
			synchronized (reloadLock) {
				Stamp stamp = current.get();
				if (stamp == null) return null;
				T captured = capture.get();
				return captured == null ? null : new EmiSnapshotTransaction<>(stateLock, reloadLock, current, stamp, captured);
			}
		}
	}

	<R> boolean prepareAndPublish(Function<T, R> prepare, Consumer<R> publish) {
		R candidate = prepare.apply(captured);
		synchronized (stateLock) {
			synchronized (reloadLock) {
				if (!stamp.same(current.get())) return false;
				publish.accept(candidate);
				return true;
			}
		}
	}

	long epoch() { return stamp.epoch(); }
}
