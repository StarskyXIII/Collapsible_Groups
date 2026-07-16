package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupRegistryEnabledOverrideTest {
	@Test
	void appliesOverridesOnlyToBuiltinAndKubeJsGroups() {
		GroupDefinition builtin = group("__default_food", true);
		GroupDefinition kubeJs = group("__kjs_scripted", true);
		GroupDefinition user = group("custom_group", true);

		List<GroupDefinition> applied = GroupRegistry.applyEnabledOverridesToManagedSources(
			List.of(builtin, kubeJs, user),
			Map.of(
				"__default_food", false,
				"__kjs_scripted", false,
				"custom_group", false
			)
		);

		assertFalse(applied.get(0).enabled());
		assertFalse(applied.get(1).enabled());
		assertTrue(applied.get(2).enabled());
	}

	@Test
	void missingOrStaleOverridesDoNotChangeGroups() {
		GroupDefinition builtin = group("__default_food", true);
		GroupDefinition user = group("custom_group", false);

		List<GroupDefinition> applied = GroupRegistry.applyEnabledOverridesToManagedSources(
			List.of(builtin, user),
			Map.of("__default_missing", false)
		);

		assertEquals(List.of(builtin, user), applied);
	}

	private static GroupDefinition group(String id, boolean enabled) {
		return new GroupDefinition(id, id, enabled, Filters.itemId("minecraft:stone"));
	}
}
