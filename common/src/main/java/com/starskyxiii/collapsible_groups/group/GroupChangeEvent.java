package com.starskyxiii.collapsible_groups.group;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Multi-subscriber publisher for group lifecycle changes. */
public final class GroupChangeEvent {
	private static final Map<Kind, CopyOnWriteArrayList<Runnable>> SUBSCRIBERS = new EnumMap<>(Kind.class);

	static {
		for (Kind kind : Kind.values()) SUBSCRIBERS.put(kind, new CopyOnWriteArrayList<>());
	}

	private GroupChangeEvent() {}

	public enum Kind {
		FULL,
		STRUCTURE,
		ENABLED,
		KUBEJS_REPLACE
	}

	public static Subscription subscribe(Kind kind, Runnable listener) {
		if (kind == null) throw new NullPointerException("kind");
		if (listener == null) throw new NullPointerException("listener");
		SUBSCRIBERS.get(kind).add(listener);
		return new Subscription(kind, listener);
	}

	public static void publish(Kind kind) {
		for (Runnable listener : SUBSCRIBERS.get(kind)) listener.run();
	}

	public static final class Subscription implements AutoCloseable {
		private final Kind kind;
		private final Runnable listener;
		private final AtomicBoolean closed = new AtomicBoolean();

		private Subscription(Kind kind, Runnable listener) {
			this.kind = kind;
			this.listener = listener;
		}

		@Override
		public void close() {
			if (closed.compareAndSet(false, true)) SUBSCRIBERS.get(kind).remove(listener);
		}
	}
}
