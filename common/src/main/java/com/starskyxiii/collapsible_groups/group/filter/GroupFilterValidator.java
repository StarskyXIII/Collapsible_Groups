package com.starskyxiii.collapsible_groups.group.filter;

import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.internal.version.data.Minecraft1201NbtAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class GroupFilterValidator {
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
		FilterNodeCapabilities.Capability capability = FilterNodeCapabilities.capability(FilterNodeCapabilities.kindOf(filter));
		if (!capability.available() || capability.validatorBehavior() == FilterNodeCapabilities.ValidatorBehavior.PRESERVE_OPAQUE) {
			return;
		}
		if (filter instanceof GroupFilter.Any) {
			GroupFilter.Any any = (GroupFilter.Any) filter;
				if (any.children().isEmpty()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_ANY_EMPTY);
				}
				any.children().forEach(child -> validateNode(child, errors));
		} else if (filter instanceof GroupFilter.All) {
			GroupFilter.All all = (GroupFilter.All) filter;
				if (all.children().isEmpty()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_ALL_EMPTY);
				}
				all.children().forEach(child -> validateNode(child, errors));
		} else if (filter instanceof GroupFilter.Not) {
			validateNode(((GroupFilter.Not) filter).child(), errors);
		} else if (filter instanceof GroupFilter.Id) {
			GroupFilter.Id id = (GroupFilter.Id) filter;
				validateType(id.ingredientType(), errors, "id");
				validateResourceLocation(id.id(), errors, "id");
		} else if (filter instanceof GroupFilter.Tag) {
			GroupFilter.Tag tag = (GroupFilter.Tag) filter;
				validateType(tag.ingredientType(), errors, "tag");
				validateResourceLocation(tag.tag(), errors, "tag");
		} else if (filter instanceof GroupFilter.BlockTag) {
			validateResourceLocation(((GroupFilter.BlockTag) filter).tag(), errors, "block_tag");
		} else if (filter instanceof GroupFilter.ItemPathStartsWith) {
			validatePartialPath(((GroupFilter.ItemPathStartsWith) filter).prefix(), errors, "item_path_starts_with");
		} else if (filter instanceof GroupFilter.ItemPathContains) {
			validatePartialPath(((GroupFilter.ItemPathContains) filter).needle(), errors, "item_path_contains");
		} else if (filter instanceof GroupFilter.ItemPathEndsWith) {
			validatePartialPath(((GroupFilter.ItemPathEndsWith) filter).suffix(), errors, "item_path_ends_with");
		} else if (filter instanceof GroupFilter.Namespace) {
			GroupFilter.Namespace namespace = (GroupFilter.Namespace) filter;
				validateType(namespace.ingredientType(), errors, "namespace");
				if (namespace.namespace().isBlank()
					|| ResourceLocation.tryParse(namespace.namespace() + ":valid") == null) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_INVALID_NAMESPACE, namespace.namespace());
				}
		} else if (filter instanceof GroupFilter.ExactStack) {
			GroupFilter.ExactStack exactStack = (GroupFilter.ExactStack) filter;
				if (exactStack.encodedStack().isBlank()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_EXACT_STACK_BLANK);
				} else if (!isExactStackPayloadJsonObject(exactStack.encodedStack())) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_EXACT_STACK_INVALID);
				}
		} else if (filter instanceof GroupFilter.Nbt) {
			String value = ((GroupFilter.Nbt) filter).expectedSnbt();
			if (value.isBlank()) {
				addError(errors, "collapsible_groups.editor.rules.error.nbt_value_blank");
			} else if (Minecraft1201NbtAccess.canonicalRoot(value).isEmpty()) {
				addError(errors, "collapsible_groups.editor.rules.error.nbt_value_invalid");
			}
		} else if (filter instanceof GroupFilter.NbtPath) {
			GroupFilter.NbtPath value = (GroupFilter.NbtPath) filter;
			if (value.path().isBlank()) {
				addError(errors, "collapsible_groups.editor.rules.error.nbt_path_blank");
			} else if (!Minecraft1201NbtAccess.validPath(value.path())) {
				addError(errors, "collapsible_groups.editor.rules.error.nbt_path_grammar", value.path());
			}
			if (value.expectedSnbt().isBlank()) {
				addError(errors, "collapsible_groups.editor.rules.error.nbt_path_value_blank");
			} else if (Minecraft1201NbtAccess.canonicalValue(value.expectedSnbt()).isEmpty()) {
				addError(errors, "collapsible_groups.editor.rules.error.nbt_path_value_invalid");
			}
		} else if (filter instanceof GroupFilter.HasComponent) {
			GroupFilter.HasComponent hc = (GroupFilter.HasComponent) filter;
				if (hc.componentTypeId().isBlank()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_HAS_COMPONENT_TYPE_BLANK);
				} else if (ResourceLocation.tryParse(hc.componentTypeId()) == null) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_HAS_COMPONENT_TYPE_INVALID, hc.componentTypeId());
				}
				if (hc.encodedValue().isBlank()) {
					addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_HAS_COMPONENT_VALUE_BLANK);
				}
		} else if (filter instanceof GroupFilter.ComponentPath) {
			GroupFilter.ComponentPath cp = (GroupFilter.ComponentPath) filter;
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

	private static void validateType(String type, List<ValidationError> errors, String nodeName) {
		if (type == null || type.isBlank()) {
			addError(errors, ModTranslationKeys.EDITOR_RULES_ERROR_MISSING_TYPE, nodeName);
		}
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

	private static boolean isExactStackPayloadJsonObject(String encodedStack) {
		try {
			return JsonParser.parseString(encodedStack).isJsonObject();
		} catch (RuntimeException e) {
			return false;
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
