package com.starskyxiii.collapsible_groups.core;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.ItemFilterQueryCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ItemPathQueryPlanTest {

	@Test
	void standaloneItemPathStartsWithCompilesToFullScan() {
		GroupFilter filter = new GroupFilter.ItemPathStartsWith("gutter_");

		ItemFilterQueryCompiler.ItemQueryPlan plan = ItemFilterQueryCompiler.compile(filter);

		assertInstanceOf(ItemFilterQueryCompiler.FullScanPlan.class, plan);
	}

	@Test
	void standaloneItemPathEndsWithCompilesToFullScan() {
		GroupFilter filter = new GroupFilter.ItemPathEndsWith("_chair");

		ItemFilterQueryCompiler.ItemQueryPlan plan = ItemFilterQueryCompiler.compile(filter);

		assertInstanceOf(ItemFilterQueryCompiler.FullScanPlan.class, plan);
	}

	@Test
	void namespaceAndItemPathEndsWithCompilesToCandidatePlan() {
		GroupFilter filter = Filters.all(
			Filters.itemNamespace("mcwfurnitures"),
			Filters.itemPathEndsWith("_chair")
		);

		ItemFilterQueryCompiler.ItemQueryPlan plan = ItemFilterQueryCompiler.compile(filter);

		assertInstanceOf(ItemFilterQueryCompiler.CandidatePlan.class, plan);
	}
}
