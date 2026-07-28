package com.starskyxiii.collapsible_groups.compat.emi;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import java.util.List;
import java.util.Objects;

/** Display-only expanded child. Serialization deliberately unwraps to {@link #delegate}. */
public final class ProjectedChildEmiIngredient implements EmiIngredient {
	private final EmiIngredient delegate;
	private final String parentGroupId;

	public ProjectedChildEmiIngredient(EmiIngredient delegate, String parentGroupId) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.parentGroupId = Objects.requireNonNull(parentGroupId, "parentGroupId");
	}

	public EmiIngredient delegate() { return delegate; }
	public String parentGroupId() { return parentGroupId; }

	@Override public List<EmiStack> getEmiStacks() { return delegate.getEmiStacks(); }
	@Override public EmiIngredient copy() { return new ProjectedChildEmiIngredient(delegate.copy(), parentGroupId); }
	@Override public long getAmount() { return delegate.getAmount(); }
	@Override public EmiIngredient setAmount(long amount) { return new ProjectedChildEmiIngredient(delegate.copy().setAmount(amount), parentGroupId); }
	@Override public float getChance() { return delegate.getChance(); }
	@Override public EmiIngredient setChance(float chance) { return new ProjectedChildEmiIngredient(delegate.copy().setChance(chance), parentGroupId); }
	@Override public List<ClientTooltipComponent> getTooltip() { return delegate.getTooltip(); }

	@Override
	public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
		delegate.render(graphics, x, y, delta, flags);
	}
}
