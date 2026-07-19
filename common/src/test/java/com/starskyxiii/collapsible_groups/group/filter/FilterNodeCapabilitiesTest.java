package com.starskyxiii.collapsible_groups.group.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterNodeCapabilitiesTest {
	@Test
	void currentNodesAreFullyAvailableOnMain() {
		assertEquals(FilterNodeKind.values().length, FilterNodeCapabilities.all().size());
		for (FilterNodeKind kind : FilterNodeKind.values()) {
			FilterNodeCapabilities.Capability capability = FilterNodeCapabilities.capability(kind);
			if (kind == FilterNodeKind.UNKNOWN) {
				assertFalse(capability.available());
				assertEquals(FilterNodeCapabilities.ValidatorBehavior.PRESERVE_OPAQUE, capability.validatorBehavior());
				assertFalse(capability.exposedInEditorConditionMenu());
				assertFalse(capability.kubeJsLoweringSupported());
			} else {
				assertTrue(capability.available(), kind.name());
				assertEquals(FilterNodeCapabilities.ValidatorBehavior.VALIDATE, capability.validatorBehavior(), kind.name());
				assertTrue(capability.exposedInEditorConditionMenu(), kind.name());
				assertTrue(capability.kubeJsLoweringSupported(), kind.name());
			}
		}
	}
}
