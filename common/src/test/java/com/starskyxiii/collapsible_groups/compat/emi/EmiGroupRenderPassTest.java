package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBackgroundRenderer;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmiGroupRenderPassTest {
	@Test void onlyProjectedChildrenProduceChildPositionsAtIconOrigins() {
		EmiIngredient ordinary = new FakeIngredient();
		assertNull(EmiGroupRenderPass.positionFor(ordinary, 11, 21));

		var position = EmiGroupRenderPass.positionFor(
			new ProjectedChildEmiIngredient(ordinary, "mixed"), 11, 21);
		assertNotNull(position);
		assertEquals(GroupBackgroundRenderer.Kind.CHILD, position.kind());
		assertEquals("mixed", position.groupId());
		assertEquals(11, position.x());
		assertEquals(21, position.y());
	}

	private static final class FakeIngredient implements EmiIngredient {
		@Override public List<EmiStack> getEmiStacks() { return List.of(); }
		@Override public EmiIngredient copy() { return new FakeIngredient(); }
		@Override public long getAmount() { return 1; }
		@Override public EmiIngredient setAmount(long amount) { return this; }
		@Override public float getChance() { return 1; }
		@Override public EmiIngredient setChance(float chance) { return this; }
		@Override public List<ClientTooltipComponent> getTooltip() { return List.of(); }
		@Override public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {}
	}
}
