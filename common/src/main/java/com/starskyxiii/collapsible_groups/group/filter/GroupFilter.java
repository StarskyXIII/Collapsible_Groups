package com.starskyxiii.collapsible_groups.group.filter;

import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;

import java.util.List;
import java.util.Objects;

public sealed interface GroupFilter
	permits GroupFilter.Any,
	        GroupFilter.All,
	        GroupFilter.Not,
	        GroupFilter.Id,
	        GroupFilter.Tag,
	        GroupFilter.BlockTag,
	        GroupFilter.ItemPathStartsWith,
	        GroupFilter.ItemPathContains,
	        GroupFilter.ItemPathEndsWith,
	        GroupFilter.Namespace,
	        GroupFilter.ExactStack,
	        GroupFilter.Nbt,
	        GroupFilter.NbtPath,
	        GroupFilter.HasComponent,
	        GroupFilter.ComponentPath,
	        GroupFilter.Unsupported {

	record Any(List<GroupFilter> children) implements GroupFilter {
		public Any {
			Objects.requireNonNull(children, "children");
			children = List.copyOf(children);
		}
	}

	record All(List<GroupFilter> children) implements GroupFilter {
		public All {
			Objects.requireNonNull(children, "children");
			children = List.copyOf(children);
		}
	}

	record Not(GroupFilter child) implements GroupFilter {
		public Not {
			Objects.requireNonNull(child, "child");
		}
	}

	record Id(String ingredientType, String id) implements GroupFilter {
		public Id {
			Objects.requireNonNull(ingredientType, "ingredientType");
			Objects.requireNonNull(id, "id");
		}
	}

	record Tag(String ingredientType, String tag) implements GroupFilter {
		public Tag {
			Objects.requireNonNull(ingredientType, "ingredientType");
			Objects.requireNonNull(tag, "tag");
		}
	}

	record BlockTag(String tag) implements GroupFilter {
		public BlockTag {
			Objects.requireNonNull(tag, "tag");
		}
	}

	record ItemPathStartsWith(String prefix) implements GroupFilter {
		public ItemPathStartsWith {
			Objects.requireNonNull(prefix, "prefix");
		}
	}

	record ItemPathContains(String needle) implements GroupFilter {
		public ItemPathContains {
			Objects.requireNonNull(needle, "needle");
		}
	}

	record ItemPathEndsWith(String suffix) implements GroupFilter {
		public ItemPathEndsWith {
			Objects.requireNonNull(suffix, "suffix");
		}
	}

	record Namespace(String ingredientType, String namespace) implements GroupFilter {
		public Namespace {
			Objects.requireNonNull(ingredientType, "ingredientType");
			Objects.requireNonNull(namespace, "namespace");
		}
	}

	record ExactStack(String encodedStack, ItemDataPayload payload) implements GroupFilter {
		public ExactStack(String encodedStack) { this(encodedStack, null); }
		public ExactStack(ItemDataPayload payload) { this(payload.encodedValue(), payload); }
		public ExactStack {
			if (payload != null) encodedStack = payload.encodedValue();
			Objects.requireNonNull(encodedStack, "encodedStack");
		}
	}

	record Nbt(String expectedSnbt, ItemDataPayload payload) implements GroupFilter {
		public Nbt(String expectedSnbt) { this(expectedSnbt, null); }
		public Nbt(ItemDataPayload payload) { this(payload.encodedValue(), payload); }
		public Nbt {
			if (payload != null) expectedSnbt = payload.encodedValue();
			Objects.requireNonNull(expectedSnbt, "expectedSnbt");
		}
	}

	record NbtPath(String path, String expectedSnbt, ItemDataPayload payload) implements GroupFilter {
		public NbtPath(String path, String expectedSnbt) { this(path, expectedSnbt, null); }
		public NbtPath(String path, ItemDataPayload payload) { this(path, payload.encodedValue(), payload); }
		public NbtPath {
			if (payload != null) expectedSnbt = payload.encodedValue();
			Objects.requireNonNull(path, "path");
			Objects.requireNonNull(expectedSnbt, "expectedSnbt");
		}
	}

	record HasComponent(String componentTypeId, String encodedValue, ItemDataPayload payload) implements GroupFilter {
		public HasComponent(String componentTypeId, String encodedValue) { this(componentTypeId, encodedValue, null); }
		public HasComponent(String componentTypeId, ItemDataPayload payload) { this(componentTypeId, payload.encodedValue(), payload); }
		public HasComponent {
			if (payload != null) encodedValue = payload.encodedValue();
			Objects.requireNonNull(componentTypeId, "componentTypeId");
			Objects.requireNonNull(encodedValue, "encodedValue");
		}
	}

	record ComponentPath(String componentTypeId, String path, String expectedValue, ItemDataPayload payload) implements GroupFilter {
		public ComponentPath(String componentTypeId, String path, String expectedValue) { this(componentTypeId, path, expectedValue, null); }
		public ComponentPath(String componentTypeId, String path, ItemDataPayload payload) { this(componentTypeId, path, payload.encodedValue(), payload); }
		public ComponentPath {
			if (payload != null) expectedValue = payload.encodedValue();
			Objects.requireNonNull(componentTypeId, "componentTypeId");
			Objects.requireNonNull(path, "path");
			Objects.requireNonNull(expectedValue, "expectedValue");
		}
	}

	/**
	 * Opaque persistence placeholder for a node this runtime cannot evaluate.
	 *
	 * <p>The complete atomic JSON subtree is copied on construction and on access so callers cannot
	 * accidentally mutate it. Persistence writes this subtree back directly instead of rebuilding it
	 * through a version-specific DTO. Evaluation yields {@code UNAVAILABLE}; in particular, it is
	 * never treated as {@code false}, which would make a surrounding {@code not} match everything.
	 */
	final class Unsupported implements GroupFilter {
		private final JsonObject rawJson;
		private final String recognizedKind;

		public Unsupported(JsonObject rawJson, String recognizedKind) {
			this.rawJson = Objects.requireNonNull(rawJson, "rawJson").deepCopy();
			this.recognizedKind = Objects.requireNonNull(recognizedKind, "recognizedKind");
		}

		public JsonObject rawJson() {
			return rawJson.deepCopy();
		}

		public String recognizedKind() {
			return recognizedKind;
		}

		@Override
		public boolean equals(Object object) {
			return this == object || object instanceof Unsupported other
				&& rawJson.equals(other.rawJson)
				&& recognizedKind.equals(other.recognizedKind);
		}

		@Override
		public int hashCode() {
			return Objects.hash(rawJson, recognizedKind);
		}

		@Override
		public String toString() {
			return "Unsupported[recognizedKind=" + recognizedKind + ", rawJson=" + rawJson + ']';
		}
	}
}
