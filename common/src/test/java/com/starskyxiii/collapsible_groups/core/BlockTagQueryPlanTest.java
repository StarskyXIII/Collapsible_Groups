package com.starskyxiii.collapsible_groups.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-common coverage for {@link GroupFilter.BlockTag}.
 *
 * <p>Query-plan assertions that execute {@code ItemFilterQueryCompiler.compile(...)}
 * cannot run here because {@code compileBlockTag(...)} calls
 * {@code ResourceLocation.tryParse(...)}, which requires Minecraft classes on the
 * test runtime classpath.
 *
 * <p>Integration requirement:
 * <ul>
 *     <li>{@code new GroupFilter.BlockTag("minecraft:logs")} should compile to
 *     {@code CandidatePlan}</li>
 *     <li>{@code new GroupFilter.BlockTag("not a resource location")} should compile to
 *     {@code EmptyPlan}</li>
 * </ul>
 */
class BlockTagQueryPlanTest {

	@Test
	void blockTagStoresTagValue() {
		GroupFilter.BlockTag filter = new GroupFilter.BlockTag("minecraft:logs");
		assertEquals("minecraft:logs", filter.tag());
	}
}
