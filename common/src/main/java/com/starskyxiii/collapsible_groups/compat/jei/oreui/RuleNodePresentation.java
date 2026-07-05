package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Presentation-layer mapping for the rules builder. Display categories such as
 * "item tag" vs "fluid tag" are derived from {@code (NodeKind, ingredientType)};
 * {@link RuleNodeUiContract} remains the sole authority for field/child rules.
 */
public final class RuleNodePresentation {
	public enum PickerKind {
		NONE,
		ITEM_TAG,
		FLUID_TAG,
		BLOCK_TAG,
		NAMESPACE
	}

	public enum BlockAccent {
		POSITIVE,
		NEGATIVE,
		NONE
	}

	private static final String TYPE_ITEM = "item";
	private static final String TYPE_FLUID = "fluid";

	private RuleNodePresentation() {}

	public static String chipLabelKey(GroupFilterRuleDraft.NodeKind kind, String ingredientType) {
		Objects.requireNonNull(kind, "kind");
		String type = normalizeType(ingredientType);
		return switch (kind) {
			case ALL -> ModTranslationKeys.EDITOR_RULES_CHIP_ALL;
			case ANY -> ModTranslationKeys.EDITOR_RULES_CHIP_ANY;
			case NOT -> ModTranslationKeys.EDITOR_RULES_CHIP_NOT;
			case ID -> switch (type) {
				case TYPE_ITEM -> ModTranslationKeys.EDITOR_RULES_CHIP_ITEM_ID;
				case TYPE_FLUID -> ModTranslationKeys.EDITOR_RULES_CHIP_FLUID_ID;
				default -> ModTranslationKeys.EDITOR_RULES_CHIP_ID;
			};
			case TAG -> switch (type) {
				case TYPE_ITEM -> ModTranslationKeys.EDITOR_RULES_CHIP_ITEM_TAG;
				case TYPE_FLUID -> ModTranslationKeys.EDITOR_RULES_CHIP_FLUID_TAG;
				default -> ModTranslationKeys.EDITOR_RULES_CHIP_TAG;
			};
			case BLOCK_TAG -> ModTranslationKeys.EDITOR_RULES_CHIP_BLOCK_TAG;
			case NAMESPACE -> ModTranslationKeys.EDITOR_RULES_CHIP_NAMESPACE;
			case ITEM_PATH_STARTS_WITH -> ModTranslationKeys.EDITOR_RULES_CHIP_PATH_STARTS;
			case ITEM_PATH_CONTAINS -> ModTranslationKeys.EDITOR_RULES_CHIP_PATH_CONTAINS;
			case ITEM_PATH_ENDS_WITH -> ModTranslationKeys.EDITOR_RULES_CHIP_PATH_ENDS;
			case EXACT_STACK -> ModTranslationKeys.EDITOR_RULES_CHIP_EXACT_STACK;
			case HAS_COMPONENT -> ModTranslationKeys.EDITOR_RULES_CHIP_HAS_COMPONENT;
			case COMPONENT_PATH -> ModTranslationKeys.EDITOR_RULES_CHIP_COMPONENT_PATH;
		};
	}

	public static PickerKind pickerKind(GroupFilterRuleDraft.NodeKind kind, String ingredientType) {
		Objects.requireNonNull(kind, "kind");
		String type = normalizeType(ingredientType);
		return switch (kind) {
			case TAG -> switch (type) {
				case TYPE_ITEM -> PickerKind.ITEM_TAG;
				case TYPE_FLUID -> PickerKind.FLUID_TAG;
				default -> PickerKind.NONE;
			};
			case BLOCK_TAG -> PickerKind.BLOCK_TAG;
			case NAMESPACE -> PickerKind.NAMESPACE;
			default -> PickerKind.NONE;
		};
	}

	public static BlockAccent blockAccent(GroupFilterRuleDraft.NodeKind kind) {
		return switch (Objects.requireNonNull(kind, "kind")) {
			case ALL, ANY -> BlockAccent.POSITIVE;
			case NOT -> BlockAccent.NEGATIVE;
			default -> BlockAccent.NONE;
		};
	}

	public static String descriptionKey(GroupFilterRuleDraft.NodeKind kind) {
		return switch (Objects.requireNonNull(kind, "kind")) {
			case ALL -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_ALL;
			case ANY -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_ANY;
			case NOT -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_NOT;
			case ID -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_ID;
			case TAG -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_TAG;
			case BLOCK_TAG -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_BLOCK_TAG;
			case ITEM_PATH_STARTS_WITH -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_PATH_STARTS;
			case ITEM_PATH_CONTAINS -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_PATH_CONTAINS;
			case ITEM_PATH_ENDS_WITH -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_PATH_ENDS;
			case NAMESPACE -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_NAMESPACE;
			case EXACT_STACK -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_EXACT_STACK;
			case HAS_COMPONENT -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_HAS_COMPONENT;
			case COMPONENT_PATH -> ModTranslationKeys.EDITOR_RULES_KIND_DESC_COMPONENT_PATH;
		};
	}

	/** Plain display text for a leaf row's value area. Compound kinds return an empty string. */
	public static String valueText(GroupFilterRuleDraft.Node node) {
		Objects.requireNonNull(node, "node");
		return switch (node.kind()) {
			case ALL, ANY, NOT -> "";
			case ID, TAG, NAMESPACE -> typedValue(node);
			case BLOCK_TAG, ITEM_PATH_STARTS_WITH, ITEM_PATH_CONTAINS, ITEM_PATH_ENDS_WITH, EXACT_STACK ->
				node.primaryValue();
			case HAS_COMPONENT -> node.secondaryValue().isBlank()
				? node.primaryValue()
				: node.primaryValue() + " = " + node.secondaryValue();
			case COMPONENT_PATH -> node.primaryValue() + " / " + node.secondaryValue() + " = " + node.tertiaryValue();
		};
	}

	private static String typedValue(GroupFilterRuleDraft.Node node) {
		String type = normalizeType(node.ingredientType());
		if (type.equals(TYPE_ITEM) || type.equals(TYPE_FLUID) || type.isEmpty()) {
			return node.primaryValue();
		}
		return type + " " + node.primaryValue();
	}

	private static String normalizeType(String ingredientType) {
		return ingredientType == null ? "" : ingredientType.trim().toLowerCase(Locale.ROOT);
	}

	/** Counts leaf (non-compound) condition nodes in a flattened tree, for the toolbar info label. */
	public static int countLeafRules(List<GroupFilterRuleDraft.FlatNode> flatNodes) {
		Objects.requireNonNull(flatNodes, "flatNodes");
		int count = 0;
		for (GroupFilterRuleDraft.FlatNode flat : flatNodes) {
			if (!flat.node().kind().compound()) {
				count++;
			}
		}
		return count;
	}
}
