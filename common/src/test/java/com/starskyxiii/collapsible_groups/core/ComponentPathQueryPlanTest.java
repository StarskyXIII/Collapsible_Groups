package com.starskyxiii.collapsible_groups.core;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.ItemFilterQueryCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Tests for {@link ItemFilterQueryCompiler} behavior for ComponentPath nodes.
 *
 * These tests confirm query planning results without requiring Minecraft runtime.
 *
 * NOTE: The test that verifies {@code all(itemId(...), componentPath(...))} still
 * produces candidate reduction via the {@code itemId} branch CANNOT run without
 * Minecraft bootstrap because {@code compileId} calls {@code Identifier.tryParse}.
 * That test is documented here as an integration test requirement:
 *
 *   Expected: ItemFilterQueryCompiler.compile(
 *       new GroupFilter.All(List.of(
 *           new GroupFilter.Id("item", "irons_spellbooks:scroll"),
 *           new GroupFilter.ComponentPath("irons_spellbooks:spell_container", "data[0].id", "irons_spellbooks:blood_needles")
 *       ))
 *   ) returns CandidatePlan (narrows to scroll item bucket, then ComponentPath filters within it)
 */
class ComponentPathQueryPlanTest {

    @Test
    void standaloneComponentPathCompilestoFullScan() {
        GroupFilter filter = new GroupFilter.ComponentPath(
            "irons_spellbooks:spell_container",
            "data[0].id",
            "irons_spellbooks:blood_needles"
        );
        ItemFilterQueryCompiler.ItemQueryPlan plan = ItemFilterQueryCompiler.compile(filter);
        assertInstanceOf(
            ItemFilterQueryCompiler.FullScanPlan.class, plan,
            "Standalone ComponentPath must compile to FULL_SCAN"
        );
    }

    @Test
    void standaloneHasComponentStillCompilestoFullScan() {
        GroupFilter filter = new GroupFilter.HasComponent(
            "apotheosis:gem", "apotheosis:core/ballast"
        );
        ItemFilterQueryCompiler.ItemQueryPlan plan = ItemFilterQueryCompiler.compile(filter);
        assertInstanceOf(
            ItemFilterQueryCompiler.FullScanPlan.class, plan,
            "HasComponent must continue to compile to FULL_SCAN (backward compat)"
        );
    }

    @Test
    void allOfTwoFullScanChildrenIsFullScan() {
        // When both children are FULL_SCAN, the All node sees sawNonAllItems=true
        // and candidates.isEmpty() — returns FULL_SCAN.
        GroupFilter filter = new GroupFilter.All(List.of(
            new GroupFilter.ComponentPath("comp1", "field", "val1"),
            new GroupFilter.ComponentPath("comp2", "field", "val2")
        ));
        ItemFilterQueryCompiler.ItemQueryPlan plan = ItemFilterQueryCompiler.compile(filter);
        assertInstanceOf(
            ItemFilterQueryCompiler.FullScanPlan.class, plan,
            "All of two FULL_SCAN children must still be FULL_SCAN"
        );
    }

    @Test
    void anyOfTwoFullScanChildrenIsFullScan() {
        GroupFilter filter = new GroupFilter.Any(List.of(
            new GroupFilter.ComponentPath("comp1", "field", "val1"),
            new GroupFilter.ComponentPath("comp2", "field", "val2")
        ));
        ItemFilterQueryCompiler.ItemQueryPlan plan = ItemFilterQueryCompiler.compile(filter);
        assertInstanceOf(
            ItemFilterQueryCompiler.FullScanPlan.class, plan,
            "Any of FULL_SCAN children is FULL_SCAN"
        );
    }
}
