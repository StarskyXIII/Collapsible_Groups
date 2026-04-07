package com.starskyxiii.collapsible_groups.core;

import com.starskyxiii.collapsible_groups.compat.jei.api.IngredientTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class GroupFilterValidator {
	private static final String ITEM_TYPE = "item";
	private static final String FLUID_TYPE = "fluid";
	private static final String STACK_PREFIX = "stack:";

	/**
	 * Restricted path grammar:
	 * segment = [A-Za-z_][A-Za-z0-9_-]*(\[[0-9]+\])?
	 * path    = segment(\.segment)*
	 *
	 * Rejects wildcards, recursive descent, negative indices, empty segments,
	 * leading/trailing dots, and empty string.
	 */
	public static final Pattern PATH_PATTERN = Pattern.compile(
		"^[A-Za-z_][A-Za-z0-9_-]*(\\[[0-9]+\\])?(\\.[A-Za-z_][A-Za-z0-9_-]*(\\[[0-9]+\\])?)*$"
	);

	private GroupFilterValidator() {}

	public static List<String> validate(GroupFilter filter) {
		List<String> errors = new ArrayList<>();
		validateNode(filter, errors);
		return List.copyOf(errors);
	}

	private static void validateNode(GroupFilter filter, List<String> errors) {
		switch (filter) {
			case GroupFilter.Any any -> {
				if (any.children().isEmpty()) {
					errors.add("Filter node 'any' must contain at least one child");
				}
				any.children().forEach(child -> validateNode(child, errors));
			}
			case GroupFilter.All all -> {
				if (all.children().isEmpty()) {
					errors.add("Filter node 'all' must contain at least one child");
				}
				all.children().forEach(child -> validateNode(child, errors));
			}
			case GroupFilter.Not not -> validateNode(not.child(), errors);
			case GroupFilter.Id id -> {
				validateType(id.ingredientType(), errors, "id");
				validateResourceLocation(id.id(), errors, "id");
			}
			case GroupFilter.Tag tag -> {
				validateType(tag.ingredientType(), errors, "tag");
				validateResourceLocation(tag.tag(), errors, "tag");
			}
			case GroupFilter.BlockTag blockTag -> validateResourceLocation(blockTag.tag(), errors, "block_tag");
			case GroupFilter.ItemPathStartsWith startsWith -> validatePartialPath(startsWith.prefix(), errors, "item_path_starts_with");
			case GroupFilter.ItemPathEndsWith endsWith -> validatePartialPath(endsWith.suffix(), errors, "item_path_ends_with");
			case GroupFilter.Namespace namespace -> {
				validateType(namespace.ingredientType(), errors, "namespace");
				if (!ResourceLocation.isValidNamespace(namespace.namespace())) {
					errors.add("Invalid namespace '" + namespace.namespace() + "'");
				}
			}
			case GroupFilter.ExactStack exactStack -> {
				if (exactStack.encodedStack().isBlank()) {
					errors.add("Exact stack filter cannot be blank");
				} else if (GroupItemSelector.decodeExactSelector(STACK_PREFIX + exactStack.encodedStack()).isEmpty()) {
					errors.add("Invalid exact stack payload");
				}
			}
			case GroupFilter.HasComponent hc -> {
				if (hc.componentTypeId().isBlank()) {
					errors.add("HasComponent node has blank componentTypeId");
				} else if (ResourceLocation.tryParse(hc.componentTypeId()) == null) {
					errors.add("HasComponent node has invalid componentTypeId: " + hc.componentTypeId());
				}
				if (hc.encodedValue().isBlank()) {
					errors.add("HasComponent node has blank encodedValue");
				}
			}
			case GroupFilter.ComponentPath cp -> {
				if (cp.componentTypeId().isBlank()) {
					errors.add("ComponentPath node has blank componentTypeId");
				} else if (ResourceLocation.tryParse(cp.componentTypeId()) == null) {
					errors.add("ComponentPath node has invalid componentTypeId: " + cp.componentTypeId());
				}
				if (cp.path().isBlank()) {
					errors.add("ComponentPath node has blank path");
				} else if (!PATH_PATTERN.matcher(cp.path()).matches()) {
					errors.add("ComponentPath node has invalid path grammar: '" + cp.path()
						+ "'. Allowed: field, parent.child, array[n], array[n].field");
				}
				if (cp.expectedValue().isBlank()) {
					errors.add("ComponentPath node has blank expectedValue");
				}
			}
		}
	}

	private static void validateType(String type, List<String> errors, String nodeName) {
		if (type == null || type.isBlank()) {
			errors.add("Filter node '" + nodeName + "' is missing type");
			return;
		}
		if (ITEM_TYPE.equals(type) || FLUID_TYPE.equals(type)) {
			return;
		}
		if (IngredientTypeRegistry.getCanonicalId(type) != null) {
			return;
		}
		if (ResourceLocation.tryParse(type) != null) {
			return;
		}
		errors.add("Invalid ingredient type '" + type + "'");
	}

	private static void validateResourceLocation(String value, List<String> errors, String nodeName) {
		if (value == null || value.isBlank()) {
			errors.add("Filter node '" + nodeName + "' is missing its value");
			return;
		}
		if (ResourceLocation.tryParse(value) == null) {
			errors.add("Invalid resource location '" + value + "'");
		}
	}

	private static void validatePartialPath(String value, List<String> errors, String nodeName) {
		if (value == null || value.isBlank()) {
			errors.add("Filter node '" + nodeName + "' is missing its value");
		}
	}
}
