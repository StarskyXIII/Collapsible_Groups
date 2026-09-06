package com.starskyxiii.collapsible_groups.client.editor;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

final class EditorNamespaceCatalog {
	private Snapshot cached;

	void update(EditorIngredientIds source) {
		if (matches(source)) return;
		var values = new TreeSet<String>();
		if (source.status() == EditorIngredientIds.Status.READY) {
			for (String id : source.ids()) {
				int separator = id.indexOf(':');
				if (separator > 0) values.add(id.substring(0, separator));
			}
		}
		cached = new Snapshot(source.token(), source.type(), source.status(), source.partial(), List.copyOf(values));
	}

	Snapshot snapshot(EditorIngredientIds source) {
		if (matches(source)) return cached;
		return new Snapshot(source.token(), source.type(),
			source.status() == EditorIngredientIds.Status.READY ? EditorIngredientIds.Status.PENDING : source.status(),
			source.partial(), List.of());
	}

	private boolean matches(EditorIngredientIds source) {
		return cached != null && cached.sourceToken() == source.token() && Objects.equals(cached.type(), source.type());
	}

	void clear() { cached = null; }

	record Snapshot(Object sourceToken, String type, EditorIngredientIds.Status status, boolean partial, List<String> values) {
		Snapshot { values = List.copyOf(values); }
	}
}
