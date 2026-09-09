package com.starskyxiii.collapsible_groups.client.editor.model;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.group.filter.RuleDescriptor;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleDescriptorProjectionTest {
	@Test void uiContractsProjectDescriptorFieldsAndBounds() {
		for (GroupFilterRuleDraft.NodeKind kind : GroupFilterRuleDraft.NodeKind.values()) {
			RuleDescriptor descriptor = RuleDescriptor.forKind(kind.filterKind());
			RuleNodeUiContract contract = RuleNodeUiContract.forKind(kind);
			assertEquals(descriptor.compound(), contract.compound(), kind.name());
			assertEquals(descriptor.draftMinChildren(), contract.draftMinChildren(), kind.name());
			assertEquals(descriptor.validMinChildren(), contract.validMinChildren(), kind.name());
			assertEquals(descriptor.maxChildren(), contract.maxChildren(), kind.name());
			assertEquals(descriptor.fieldRoles().stream().map(role -> RuleFieldRole.valueOf(role.name())).toList(),
				contract.fieldRoles(), kind.name());
			assertEquals(contract.fieldRoles().stream()
				.filter(role -> descriptor.requiredRoles().contains(RuleDescriptor.FieldRole.valueOf(role.name())))
				.toList(), contract.requiredRoles(), kind.name());
		}
	}

	@Test void nbtRulesProjectFieldsLabelsAndReferenceSourcesWithoutAutomaticPickers() {
		RuleNodeUiContract nbt = RuleNodeUiContract.forKind(GroupFilterRuleDraft.NodeKind.NBT);
		RuleNodeUiContract path = RuleNodeUiContract.forKind(GroupFilterRuleDraft.NodeKind.NBT_PATH);
		assertEquals(List.of(RuleFieldRole.PRIMARY_VALUE), nbt.fieldRoles());
		assertEquals(nbt.fieldRoles(), nbt.requiredRoles());
		assertEquals(List.of(RuleFieldRole.PRIMARY_VALUE, RuleFieldRole.SECONDARY_VALUE), path.fieldRoles());
		assertEquals(path.fieldRoles(), path.requiredRoles());
		assertEquals(RuleDescriptor.ReferencePickerSource.ITEM_NBT,
			RuleNodePresentation.referencePickerSource(GroupFilterRuleDraft.NodeKind.NBT));
		assertEquals(RuleDescriptor.ReferencePickerSource.ITEM_NBT_PATH,
			RuleNodePresentation.referencePickerSource(GroupFilterRuleDraft.NodeKind.NBT_PATH));
		assertEquals(RuleNodePresentation.PickerKind.NONE,
			RuleNodePresentation.pickerKind(GroupFilterRuleDraft.NodeKind.NBT, "item"));
		assertEquals(RuleNodePresentation.PickerKind.NONE,
			RuleNodePresentation.pickerKind(GroupFilterRuleDraft.NodeKind.NBT_PATH, "item"));
		assertEquals(ModTranslationKeys.EDITOR_RULES_CHIP_NBT,
			RuleNodePresentation.chipLabelKey(GroupFilterRuleDraft.NodeKind.NBT, "item"));
		assertEquals(ModTranslationKeys.EDITOR_RULES_KIND_DESC_NBT_PATH,
			RuleNodePresentation.descriptionKey(GroupFilterRuleDraft.NodeKind.NBT_PATH));
		GroupFilterRuleDraft draft = GroupFilterRuleDraft.empty();
		GroupFilterRuleDraft.Node node = draft.createNode(GroupFilterRuleDraft.NodeKind.NBT_PATH);
		node.setPrimaryValue("display.Name");
		node.setSecondaryValue("'Relic'");
		assertEquals("display.Name = 'Relic'", RuleNodePresentation.valueText(node));
	}
}
