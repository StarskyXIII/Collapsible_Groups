package com.starskyxiii.collapsible_groups.group.filter;

import com.google.gson.JsonObject;

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

	record ExactStack(String encodedStack) implements GroupFilter {
		public ExactStack {
			Objects.requireNonNull(encodedStack, "encodedStack");
		}
	}

	record HasComponent(String componentTypeId, String encodedValue) implements GroupFilter {
		public HasComponent {
			Objects.requireNonNull(componentTypeId, "componentTypeId");
			Objects.requireNonNull(encodedValue, "encodedValue");
		}
	}

	record ComponentPath(String componentTypeId, String path, String expectedValue) implements GroupFilter {
		public ComponentPath {
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
