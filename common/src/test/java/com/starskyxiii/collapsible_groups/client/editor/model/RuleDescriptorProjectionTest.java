package com.starskyxiii.collapsible_groups.client.editor.model;

import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.group.filter.RuleDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleDescriptorProjectionTest {
	@Test
	void uiContractProjectsDescriptorFieldsRequirementsAndChildBounds() {
		for (GroupFilterRuleDraft.NodeKind kind : GroupFilterRuleDraft.NodeKind.values()) {
			RuleDescriptor descriptor = RuleDescriptor.forKind(kind.filterKind());
			RuleNodeUiContract ui = RuleNodeUiContract.forKind(kind);

			assertEquals(descriptor.compound(), ui.compound(), kind.name());
			assertEquals(descriptor.draftMinChildren(), ui.draftMinChildren(), kind.name());
			assertEquals(descriptor.validMinChildren(), ui.validMinChildren(), kind.name());
			assertEquals(descriptor.maxChildren(), ui.maxChildren(), kind.name());
			assertEquals(descriptor.fieldRoles().stream().map(RuleDescriptorProjectionTest::uiRole).toList(),
				ui.fieldRoles(), kind.name());
			assertEquals(descriptor.fieldRoles().stream().filter(descriptor.requiredRoles()::contains)
				.map(RuleDescriptorProjectionTest::uiRole).toList(), ui.requiredRoles(), kind.name());
		}
	}

	@Test
	void presentationProjectsDescriptorReferenceSourcesWithoutInventingNbtPickers() {
		assertEquals(RuleDescriptor.ReferencePickerSource.ITEM_COMPONENTS,
			RuleNodePresentation.referencePickerSource(GroupFilterRuleDraft.NodeKind.HAS_COMPONENT));
		assertEquals(RuleDescriptor.ReferencePickerSource.ITEM_COMPONENT_PATHS,
			RuleNodePresentation.referencePickerSource(GroupFilterRuleDraft.NodeKind.COMPONENT_PATH));
		assertEquals(RuleNodePresentation.PickerKind.NONE,
			RuleNodePresentation.pickerKind(GroupFilterRuleDraft.NodeKind.HAS_COMPONENT, "item"));
		assertEquals(RuleNodePresentation.PickerKind.NONE,
			RuleNodePresentation.pickerKind(GroupFilterRuleDraft.NodeKind.COMPONENT_PATH, "item"));
		assertEquals(List.of("ANY", "ALL", "NOT", "ID", "TAG", "BLOCK_TAG",
			"ITEM_PATH_STARTS_WITH", "ITEM_PATH_CONTAINS", "ITEM_PATH_ENDS_WITH", "NAMESPACE",
			"EXACT_STACK", "HAS_COMPONENT", "COMPONENT_PATH"),
			java.util.Arrays.stream(GroupFilterRuleDraft.NodeKind.values()).map(Enum::name).toList());
	}

	private static RuleFieldRole uiRole(RuleDescriptor.FieldRole role) {
		return switch (role) {
			case INGREDIENT_TYPE -> RuleFieldRole.INGREDIENT_TYPE;
			case PRIMARY_VALUE -> RuleFieldRole.PRIMARY_VALUE;
			case SECONDARY_VALUE -> RuleFieldRole.SECONDARY_VALUE;
			case TERTIARY_VALUE -> RuleFieldRole.TERTIARY_VALUE;
		};
	}
}
