package com.starskyxiii.collapsible_groups.group.filter;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleDescriptorTest {
	@Test
	void tableCoversEveryRuntimeKindAndCurrentEditorKindExactlyOnce() {
		assertEquals(Set.of(FilterNodeKind.values()), RuleDescriptor.all().keySet());
		assertEquals(
			Arrays.stream(GroupFilterRuleDraft.NodeKind.values())
				.map(GroupFilterRuleDraft.NodeKind::filterKind).collect(Collectors.toSet()),
			RuleDescriptor.all().keySet().stream()
				.filter(kind -> kind != FilterNodeKind.UNKNOWN).collect(Collectors.toSet()));
	}

	@Test
	void componentRulesAreAvailableItemOnlyRulesWithTheirNativeFields() {
		RuleDescriptor hasComponent = RuleDescriptor.forKind(FilterNodeKind.HAS_COMPONENT);
		assertTrue(hasComponent.available());
		assertTrue(hasComponent.kubeJsLoweringSupported());
		assertEquals(RuleDescriptor.TypePolicy.ITEM_ONLY, hasComponent.typePolicy());
		assertEquals(
			Set.of(RuleDescriptor.FieldRole.PRIMARY_VALUE, RuleDescriptor.FieldRole.SECONDARY_VALUE),
			hasComponent.requiredRoles());
		assertEquals(RuleDescriptor.ReferencePickerSource.ITEM_COMPONENTS,
			hasComponent.referencePickerSource());

		RuleDescriptor componentPath = RuleDescriptor.forKind(FilterNodeKind.COMPONENT_PATH);
		assertEquals(RuleDescriptor.TypePolicy.ITEM_ONLY, componentPath.typePolicy());
		assertEquals(Set.of(
			RuleDescriptor.FieldRole.PRIMARY_VALUE,
			RuleDescriptor.FieldRole.SECONDARY_VALUE,
			RuleDescriptor.FieldRole.TERTIARY_VALUE), componentPath.requiredRoles());
		assertEquals(RuleDescriptor.ReferencePickerSource.ITEM_COMPONENT_PATHS,
			componentPath.referencePickerSource());
	}

	@Test
	void unknownKindIsOpaqueAndHasNoEditorOrLoweringContract() {
		RuleDescriptor unknown = RuleDescriptor.forKind(FilterNodeKind.UNKNOWN);
		assertFalse(unknown.available());
		assertFalse(unknown.kubeJsLoweringSupported());
		assertEquals(RuleDescriptor.TypePolicy.NONE, unknown.typePolicy());
		assertFalse(unknown.compound());
		assertTrue(unknown.fieldRoles().isEmpty());
		assertEquals(RuleDescriptor.ReferencePickerSource.NONE, unknown.referencePickerSource());
	}

	@Test
	void draftNodeChildBoundsComeFromTheDescriptor() {
		for (GroupFilterRuleDraft.NodeKind kind : GroupFilterRuleDraft.NodeKind.values()) {
			RuleDescriptor descriptor = RuleDescriptor.forKind(kind.filterKind());
			assertEquals(descriptor.compound(), kind.compound(), kind.name());
			assertEquals(descriptor.draftMinChildren(), kind.minChildren(), kind.name());
			assertEquals(descriptor.maxChildren(), kind.maxChildren(), kind.name());
		}
	}

	@Test
	void typeScopeProjectsLeafPoliciesWhileKeepingBooleanTraversal() {
		GroupFilter filter = new GroupFilter.Any(java.util.List.of(
			new GroupFilter.HasComponent("minecraft:custom_name", "\"Boat\""),
			new GroupFilter.Id("emi:mekanism_chemical", "mekanism:oxygen")));

		assertEquals(Set.of("item", "emi:mekanism_chemical"), FilterTypeScope.declared(filter).types());
		assertEquals(Set.of("item"), FilterTypeScope.declared(new GroupFilter.ComponentPath(
			"minecraft:food", "nutrition", "4")).types());
		assertTrue(FilterTypeScope.declared(new GroupFilter.Unsupported(
			new com.google.gson.JsonObject(), "nbt")).isEmpty());
	}
}
