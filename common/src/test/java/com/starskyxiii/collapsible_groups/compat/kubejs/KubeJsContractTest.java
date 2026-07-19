package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KubeJsContractTest {
	@Test
	void idFormulasRemainStable() {
		assertEquals("__kjs_example_tools_hammers", KubeJsGroupIds.item("example:tools/hammers"));
		assertEquals("__kjs_fluid_example_hot_water", KubeJsGroupIds.fluid("example:hot/water"));
		assertEquals("__kjs_example_chemical_example_acid", KubeJsGroupIds.generic("example:chemical", "example:acid"));
		assertEquals("__kjs_remote_example_tools_hammers", KubeJsGroupIds.remoteItem("example:tools/hammers"));
		assertEquals("__kjs_remote_fluid_example_hot_water", KubeJsGroupIds.remoteFluid("example:hot/water"));
	}

	@Test
	void compositionKeepsExistingEmptySingleAndManyRules() {
		GroupFilter first = Filters.itemId("minecraft:stone");
		GroupFilter second = Filters.itemId("minecraft:dirt");
		assertNull(KubeJsFilterComposition.any(List.of()));
		assertSame(first, KubeJsFilterComposition.any(List.of(first)));
		assertEquals(Filters.any(first, second), KubeJsFilterComposition.any(List.of(first, second)));
	}
}
