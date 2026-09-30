package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;

import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

public record EditorIngredientTypes(Object token, Status status, List<Option> options) {

	public enum Status { READY, PENDING, UNAVAILABLE }
	public static final EditorIngredientTypes PENDING = new EditorIngredientTypes(new Object(), Status.PENDING, List.of());
	public static final EditorIngredientTypes UNAVAILABLE = new EditorIngredientTypes(new Object(), Status.UNAVAILABLE, List.of());

	public EditorIngredientTypes {
		options = List.copyOf(options);
	}

	public record Option(String id, List<String> aliases) {
		public Option { aliases = List.copyOf(aliases); }
		public boolean matches(String query) {
			String needle = query.toLowerCase(Locale.ROOT);
			return id.toLowerCase(Locale.ROOT).contains(needle)
				|| aliases.stream().anyMatch(alias -> alias.toLowerCase(Locale.ROOT).contains(needle));
		}
		public boolean identifies(String value) { return id.equals(value) || aliases.contains(value); }
	}

	public static <E> EditorIngredientTypes from(List<ViewerIngredientType<E>> types) {
		var aliases = new TreeMap<String, TreeSet<String>>();
		for (var type : types) {
			String id = type.canonicalId();
			if (id.equals("item") || id.equals("fluid") || type.ingredients().stream().noneMatch(
				entry -> entry.kind() == ViewerIngredient.Kind.GENERIC && entry.identity().typeId().equals(id))) continue;
			aliases.computeIfAbsent(id, ignored -> new TreeSet<>()).addAll(type.aliases());
		}
		return new EditorIngredientTypes(new Object(), Status.READY, aliases.entrySet().stream()
			.map(entry -> new Option(entry.getKey(), List.copyOf(entry.getValue()))).toList());
	}

	public static final class Cache {
		private Object generation;
		private EditorIngredientTypes snapshot = PENDING;

		public synchronized EditorIngredientTypes get(Object current, Supplier<EditorIngredientTypes> build) {
			if (current == null) { clear(); return PENDING; }
			if (generation != current) {
				EditorIngredientTypes next = build.get();
				if (next.status() != Status.READY) { clear(); return next; }
				generation = current;
				snapshot = next;
			}
			return snapshot;
		}

		public synchronized void clear() { generation = null; snapshot = PENDING; }
	}
}
