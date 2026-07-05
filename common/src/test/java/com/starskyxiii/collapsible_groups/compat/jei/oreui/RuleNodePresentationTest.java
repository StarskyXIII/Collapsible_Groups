package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuleNodePresentationTest {
	@Test
	void chipLabelIsDerivedFromKindAndIngredientType() {
		assertEquals(ModTranslationKeys.EDITOR_RULES_CHIP_ITEM_TAG,
			RuleNodePresentation.chipLabelKey(GroupFilterRuleDraft.NodeKind.TAG, "item"));
		assertEquals(ModTranslationKeys.EDITOR_RULES_CHIP_FLUID_TAG,
			RuleNodePresentation.chipLabelKey(GroupFilterRuleDraft.NodeKind.TAG, " Fluid "));
		assertEquals(ModTranslationKeys.EDITOR_RULES_CHIP_TAG,
			RuleNodePresentation.chipLabelKey(GroupFilterRuleDraft.NodeKind.TAG, "entity"));
		assertEquals(ModTranslationKeys.EDITOR_RULES_CHIP_ITEM_ID,
			RuleNodePresentation.chipLabelKey(GroupFilterRuleDraft.NodeKind.ID, "item"));
		assertEquals(ModTranslationKeys.EDITOR_RULES_CHIP_FLUID_ID,
			RuleNodePresentation.chipLabelKey(GroupFilterRuleDraft.NodeKind.ID, "fluid"));
		assertEquals(ModTranslationKeys.EDITOR_RULES_CHIP_ID,
			RuleNodePresentation.chipLabelKey(GroupFilterRuleDraft.NodeKind.ID, null));
		assertEquals(ModTranslationKeys.EDITOR_RULES_CHIP_BLOCK_TAG,
			RuleNodePresentation.chipLabelKey(GroupFilterRuleDraft.NodeKind.BLOCK_TAG, "item"));
	}

	@Test
	void everyKindHasChipLabelAndDescription() {
		for (GroupFilterRuleDraft.NodeKind kind : GroupFilterRuleDraft.NodeKind.values()) {
			assertNotNull(RuleNodePresentation.chipLabelKey(kind, "item"), kind.name());
			assertNotNull(RuleNodePresentation.descriptionKey(kind), kind.name());
			assertNotNull(RuleNodePresentation.blockAccent(kind), kind.name());
		}
	}

	@Test
	void pickerKindOnlyCoversTagAndNamespaceNodes() {
		assertEquals(RuleNodePresentation.PickerKind.ITEM_TAG,
			RuleNodePresentation.pickerKind(GroupFilterRuleDraft.NodeKind.TAG, "item"));
		assertEquals(RuleNodePresentation.PickerKind.FLUID_TAG,
			RuleNodePresentation.pickerKind(GroupFilterRuleDraft.NodeKind.TAG, "fluid"));
		assertEquals(RuleNodePresentation.PickerKind.NONE,
			RuleNodePresentation.pickerKind(GroupFilterRuleDraft.NodeKind.TAG, "entity"));
		assertEquals(RuleNodePresentation.PickerKind.BLOCK_TAG,
			RuleNodePresentation.pickerKind(GroupFilterRuleDraft.NodeKind.BLOCK_TAG, "item"));
		assertEquals(RuleNodePresentation.PickerKind.NAMESPACE,
			RuleNodePresentation.pickerKind(GroupFilterRuleDraft.NodeKind.NAMESPACE, "item"));
		assertEquals(RuleNodePresentation.PickerKind.NONE,
			RuleNodePresentation.pickerKind(GroupFilterRuleDraft.NodeKind.ID, "item"));
		assertEquals(RuleNodePresentation.PickerKind.NONE,
			RuleNodePresentation.pickerKind(GroupFilterRuleDraft.NodeKind.ITEM_PATH_CONTAINS, "item"));
	}

	@Test
	void blockAccentMarksCompoundSemantics() {
		assertEquals(RuleNodePresentation.BlockAccent.POSITIVE,
			RuleNodePresentation.blockAccent(GroupFilterRuleDraft.NodeKind.ALL));
		assertEquals(RuleNodePresentation.BlockAccent.POSITIVE,
			RuleNodePresentation.blockAccent(GroupFilterRuleDraft.NodeKind.ANY));
		assertEquals(RuleNodePresentation.BlockAccent.NEGATIVE,
			RuleNodePresentation.blockAccent(GroupFilterRuleDraft.NodeKind.NOT));
		assertEquals(RuleNodePresentation.BlockAccent.NONE,
			RuleNodePresentation.blockAccent(GroupFilterRuleDraft.NodeKind.TAG));
	}

	@Test
	void valueTextJoinsMultiFieldKindsAndPrefixesUncommonTypes() {
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();

		GroupFilterRuleDraft.Node tag = draft.createNode(GroupFilterRuleDraft.NodeKind.TAG);
		tag.setPrimaryValue("c:ingots/iron");
		assertEquals("c:ingots/iron", RuleNodePresentation.valueText(tag));

		GroupFilterRuleDraft.Node typed = draft.createNode(GroupFilterRuleDraft.NodeKind.NAMESPACE);
		typed.setIngredientType("entity");
		typed.setPrimaryValue("ae2");
		assertEquals("entity ae2", RuleNodePresentation.valueText(typed));

		GroupFilterRuleDraft.Node hasComponent = draft.createNode(GroupFilterRuleDraft.NodeKind.HAS_COMPONENT);
		hasComponent.setPrimaryValue("minecraft:custom_name");
		assertEquals("minecraft:custom_name", RuleNodePresentation.valueText(hasComponent));
		hasComponent.setSecondaryValue("\"Boat\"");
		assertEquals("minecraft:custom_name = \"Boat\"", RuleNodePresentation.valueText(hasComponent));

		GroupFilterRuleDraft.Node componentPath = draft.createNode(GroupFilterRuleDraft.NodeKind.COMPONENT_PATH);
		componentPath.setPrimaryValue("minecraft:food");
		componentPath.setSecondaryValue("nutrition");
		componentPath.setTertiaryValue("4");
		assertEquals("minecraft:food / nutrition = 4", RuleNodePresentation.valueText(componentPath));

		GroupFilterRuleDraft.Node compound = draft.createNode(GroupFilterRuleDraft.NodeKind.ALL);
		assertEquals("", RuleNodePresentation.valueText(compound));
	}

	@Test
	void countLeafRulesReturnsZeroForEmptyTree() {
		assertEquals(0, RuleNodePresentation.countLeafRules(List.of()));
	}

	@Test
	void countLeafRulesReturnsZeroForRootCompoundOnly() {
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		draft.insertRelativeTo(null, GroupFilterRuleDraft.NodeKind.ALL);
		assertEquals(0, RuleNodePresentation.countLeafRules(draft.flatten()));
	}

	@Test
	void countLeafRulesCountsOnlyLeavesInNestedMix() {
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node root = draft.insertRelativeTo(null, GroupFilterRuleDraft.NodeKind.ALL);
		draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ID);
		GroupFilterRuleDraft.Node nested = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ANY);
		draft.insertRelativeTo(nested, GroupFilterRuleDraft.NodeKind.TAG);
		draft.insertRelativeTo(nested, GroupFilterRuleDraft.NodeKind.NAMESPACE);

		assertEquals(3, RuleNodePresentation.countLeafRules(draft.flatten()));
	}
}
