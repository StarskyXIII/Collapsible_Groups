package com.starskyxiii.collapsible_groups.group.filter;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuleDescriptorTest {
	@Test void unavailableRulesStayOpaqueAndCannotLowerToKubeJs() {
		assertEquals(FilterNodeKind.values().length, RuleDescriptor.all().size());
		for (FilterNodeKind kind : Set.of(FilterNodeKind.HAS_COMPONENT, FilterNodeKind.COMPONENT_PATH,
			FilterNodeKind.UNKNOWN)) {
			RuleDescriptor descriptor = RuleDescriptor.forKind(kind);
			FilterNodeCapabilities.Capability capability = FilterNodeCapabilities.capability(kind);
			assertFalse(descriptor.available());
			assertFalse(descriptor.kubeJsLoweringSupported());
			assertFalse(capability.available());
			assertFalse(capability.exposedInEditorConditionMenu());
			assertFalse(FilterNodeCapabilities.supportsKubeJsLowering(kind));
			assertEquals(FilterNodeCapabilities.ValidatorBehavior.PRESERVE_OPAQUE, capability.validatorBehavior());
		}
	}

	@Test void nbtRulesAreAvailableThroughTheCapabilityFacade() {
		for (FilterNodeKind kind : Set.of(FilterNodeKind.NBT, FilterNodeKind.NBT_PATH)) {
			RuleDescriptor descriptor = RuleDescriptor.forKind(kind);
			assertTrue(descriptor.available());
			assertTrue(descriptor.kubeJsLoweringSupported());
			assertTrue(FilterNodeCapabilities.isAvailable(kind));
			assertTrue(FilterNodeCapabilities.isEditorConditionExposed(kind));
			assertTrue(FilterNodeCapabilities.supportsKubeJsLowering(kind));
		}
	}
}
