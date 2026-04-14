package com.starskyxiii.collapsible_groups.core;

import com.starskyxiii.collapsible_groups.compat.jei.api.IngredientTypeRegistry;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

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
		return validateDetailed(filter).stream()
			.map(error -> error.toComponent().getString())
			.toList();
	}

	public static List<Component> validateComponents(GroupFilter filter) {
		return validateDetailed(filter).stream()
			.map(ValidationError::toComponent)
			.toList();
	}

	private static List<ValidationError> validateDetailed(GroupFilter filter) {
		List<ValidationError> errors = new ArrayList<>();
		validateNode(filter, errors);
		return List.copyOf(errors);
	}

	private static void validateNode(GroupFilter filter, List<ValidationError> errors) {
		switch (filter) {
			case GroupFilter.Any any -> {
				if (any.children().isEmpty()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_ANY_EMPTY);
				}
				any.children().forEach(child -> validateNode(child, errors));
			}
			case GroupFilter.All all -> {
				if (all.children().isEmpty()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_ALL_EMPTY);
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
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_INVALID_NAMESPACE, namespace.namespace());
				}
			}
			case GroupFilter.ExactStack exactStack -> {
				if (exactStack.encodedStack().isBlank()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_EXACT_STACK_BLANK);
				} else if (GroupItemSelector.decodeExactSelector(STACK_PREFIX + exactStack.encodedStack()).isEmpty()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_EXACT_STACK_INVALID);
				}
			}
			case GroupFilter.HasComponent hc -> {
				if (hc.componentTypeId().isBlank()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_HAS_COMPONENT_TYPE_BLANK);
				} else if (ResourceLocation.tryParse(hc.componentTypeId()) == null) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_HAS_COMPONENT_TYPE_INVALID, hc.componentTypeId());
				}
				if (hc.encodedValue().isBlank()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_HAS_COMPONENT_VALUE_BLANK);
				}
			}
			case GroupFilter.ComponentPath cp -> {
				if (cp.componentTypeId().isBlank()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_COMPONENT_PATH_TYPE_BLANK);
				} else if (ResourceLocation.tryParse(cp.componentTypeId()) == null) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_COMPONENT_PATH_TYPE_INVALID, cp.componentTypeId());
				}
				if (cp.path().isBlank()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_COMPONENT_PATH_BLANK);
				} else if (!PATH_PATTERN.matcher(cp.path()).matches()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_COMPONENT_PATH_GRAMMAR, cp.path());
				}
				if (cp.expectedValue().isBlank()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_COMPONENT_PATH_VALUE_BLANK);
				}
			}
		}
	}

	private static void validateType(String type, List<ValidationError> errors, String nodeName) {
		if (type == null || type.isBlank()) {
			addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_MISSING_TYPE, nodeName);
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
		addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_INVALID_TYPE, type);
	}

	private static void validateResourceLocation(String value, List<ValidationError> errors, String nodeName) {
		if (value == null || value.isBlank()) {
			addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_MISSING_VALUE, nodeName);
			return;
		}
		if (ResourceLocation.tryParse(value) == null) {
			addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_INVALID_RESOURCE_LOCATION, value);
		}
	}

	private static void validatePartialPath(String value, List<ValidationError> errors, String nodeName) {
		if (value == null || value.isBlank()) {
			addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_MISSING_VALUE, nodeName);
		}
	}

	private static void addError(List<ValidationError> errors, String key, Object... args) {
		errors.add(new ValidationError(key, args == null ? new Object[0] : args));
	}

	private record ValidationError(String key, Object[] args) {
		private Component toComponent() {
			return Component.translatable(key, args);
		}
	}
}
