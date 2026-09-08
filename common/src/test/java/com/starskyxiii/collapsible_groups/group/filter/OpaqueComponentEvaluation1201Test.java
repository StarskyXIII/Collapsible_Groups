package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpaqueComponentEvaluation1201Test {
	private static ItemStackIngredientView stone;

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		stone = new ItemStackIngredientView(new ItemStack(Items.STONE));
	}

	@Test
	void componentNodesAndTheirNegationsStayUnavailable() {
		assertUnavailable(new GroupFilter.HasComponent("minecraft:custom_data", "{}"));
		assertUnavailable(new GroupFilter.ComponentPath("minecraft:custom_data", "value", "1"));
	}

	private static void assertUnavailable(GroupFilter filter) {
		assertEquals(CompiledFilter.Evaluation.UNAVAILABLE, CompiledFilter.compile(filter).evaluate(stone));
		assertEquals(CompiledFilter.Evaluation.UNAVAILABLE,
			CompiledFilter.compile(new GroupFilter.Not(filter)).evaluate(stone));
	}
}
