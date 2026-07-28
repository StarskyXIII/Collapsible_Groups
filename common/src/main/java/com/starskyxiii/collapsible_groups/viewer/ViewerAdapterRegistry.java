package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Lifecycle registry for viewer adapters. The current policy permits one active adapter. */
public final class ViewerAdapterRegistry {
	private final Map<String, ViewerAdapter<?, ?>> adapters = new LinkedHashMap<>();

	public synchronized ViewerRegistration register(ViewerAdapter<?, ?> adapter) {
		Objects.requireNonNull(adapter, "adapter");
		if (adapter.id().isBlank()) throw new IllegalArgumentException("adapter id must not be blank");
		if (!adapters.isEmpty()) {
			throw new IllegalStateException("Only one viewer adapter may be active");
		}
		adapters.put(adapter.id(), adapter);
		List<GroupChangeEvent.Subscription> subscriptions = new ArrayList<>();
		for (GroupChangeEvent.Kind kind : GroupChangeEvent.Kind.values()) {
			subscriptions.add(GroupChangeEvent.subscribe(kind, () -> adapter.onGroupChange(kind)));
		}
		return new AdapterRegistration(adapter.id(), subscriptions);
	}

	public synchronized List<ViewerAdapter<?, ?>> activeAdapters() {
		return List.copyOf(adapters.values());
	}

	public synchronized java.util.Optional<ViewerAdapter<?, ?>> activeAdapter() {
		return adapters.values().stream().findFirst();
	}

	private final class AdapterRegistration implements ViewerRegistration {
		private final String adapterId;
		private final List<GroupChangeEvent.Subscription> subscriptions;
		private boolean closed;

		private AdapterRegistration(String adapterId, List<GroupChangeEvent.Subscription> subscriptions) {
			this.adapterId = adapterId;
			this.subscriptions = List.copyOf(subscriptions);
		}

		@Override
		public void close() {
			synchronized (ViewerAdapterRegistry.this) {
				if (closed) return;
				closed = true;
				subscriptions.forEach(GroupChangeEvent.Subscription::close);
				adapters.remove(adapterId);
			}
		}
	}
}
