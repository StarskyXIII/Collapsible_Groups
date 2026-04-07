package com.starskyxiii.collapsible_groups.core;

import java.util.List;
import java.util.Objects;

public sealed interface GroupFilter
	permits GroupFilter.Any,
	        GroupFilter.All,
	        GroupFilter.Not,
	        GroupFilter.Id,
	        GroupFilter.Tag,
	        GroupFilter.BlockTag,
	        GroupFilter.Namespace,
	        GroupFilter.ExactStack,
	        GroupFilter.HasComponent,
	        GroupFilter.ComponentPath {

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
}
