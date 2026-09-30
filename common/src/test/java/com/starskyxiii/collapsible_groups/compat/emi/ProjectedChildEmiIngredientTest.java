package com.starskyxiii.collapsible_groups.compat.emi;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectedChildEmiIngredientTest {
	@Test void preservesZeroAmountCopyChanceTooltipAndStackList() {
		List<EmiStack> stacks = List.of();
		List<ClientTooltipComponent> tooltip = List.of();
		FakeIngredient delegate = new FakeIngredient(0, 0.25f, stacks, tooltip);
		ProjectedChildEmiIngredient child = new ProjectedChildEmiIngredient(delegate, "group");

		assertEquals(0, child.getAmount());
		assertEquals(0.25f, child.getChance());
		assertSame(stacks, child.getEmiStacks());
		assertSame(tooltip, child.getTooltip());
		ProjectedChildEmiIngredient copy = assertInstanceOf(ProjectedChildEmiIngredient.class, child.copy());
		assertEquals("group", copy.parentGroupId());
		assertNotSame(delegate, copy.delegate());
	}

	@Test void amountAndChanceUpdatesRemainWrappedAndDoNotMutateDelegate() {
		FakeIngredient delegate = new FakeIngredient(7, 0.5f, List.of(), List.of());
		ProjectedChildEmiIngredient child = new ProjectedChildEmiIngredient(delegate, "fluid-group");

		ProjectedChildEmiIngredient amount = assertInstanceOf(ProjectedChildEmiIngredient.class, child.setAmount(81));
		ProjectedChildEmiIngredient chance = assertInstanceOf(ProjectedChildEmiIngredient.class, child.setChance(0.75f));
		assertEquals(81, amount.getAmount());
		assertEquals(0.75f, chance.getChance());
		assertEquals(7, child.getAmount());
		assertEquals(0.5f, child.getChance());
		assertEquals("fluid-group", amount.parentGroupId());
		assertEquals("fluid-group", chance.parentGroupId());
	}

	private static final class FakeIngredient implements EmiIngredient {
		private final long amount;
		private final float chance;
		private final List<EmiStack> stacks;
		private final List<ClientTooltipComponent> tooltip;

		private FakeIngredient(long amount, float chance, List<EmiStack> stacks,
			List<ClientTooltipComponent> tooltip) {
			this.amount = amount;
			this.chance = chance;
			this.stacks = stacks;
			this.tooltip = tooltip;
		}

		@Override public List<EmiStack> getEmiStacks() { return stacks; }
		@Override public EmiIngredient copy() { return new FakeIngredient(amount, chance, stacks, tooltip); }
		@Override public long getAmount() { return amount; }
		@Override public EmiIngredient setAmount(long value) { return new FakeIngredient(value, chance, stacks, tooltip); }
		@Override public float getChance() { return chance; }
		@Override public EmiIngredient setChance(float value) { return new FakeIngredient(amount, value, stacks, tooltip); }
		@Override public List<ClientTooltipComponent> getTooltip() { return tooltip; }
		@Override public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {}
	}
}
