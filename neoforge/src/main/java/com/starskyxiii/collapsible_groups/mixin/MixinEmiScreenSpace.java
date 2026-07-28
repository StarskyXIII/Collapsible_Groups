package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.emi.EmiProjectionController;
import com.starskyxiii.collapsible_groups.compat.emi.EmiGroupRenderPass;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBackgroundRenderer;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiDrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "dev.emi.emi.screen.EmiScreenManager$ScreenSpace", remap = false)
public abstract class MixinEmiScreenSpace {
	@Shadow @Final public boolean search;
	@Shadow public abstract SidebarType getType();
	@Shadow public abstract List<? extends EmiIngredient> getStacks();
	@Shadow public abstract int getWidth(int y);
	@Shadow public abstract int getX(int x, int y);
	@Shadow public abstract int getY(int x, int y);
	@Shadow @Final public int th;
	@Unique private List<GroupBackgroundRenderer.BackgroundPosition> cg$groupPositions = List.of();

	@Inject(method = "getStacks", at = @At("RETURN"), cancellable = true, require = 1)
	private void cg$projectIndex(CallbackInfoReturnable<List<? extends EmiIngredient>> cir) {
		if (!ViewerLifecycleCoordinator.isEmiSelected() || getType() != SidebarType.INDEX) return;
		cir.setReturnValue(EmiProjectionController.project(cir.getReturnValue(), search));
	}

	@Inject(method = "render", at = @At(value = "INVOKE",
		target = "Ldev/emi/emi/screen/EmiScreenManager$ScreenSpace;getRawOffsetFromMouse(II)I"), require = 1)
	private void cg$drawGroupTints(EmiDrawContext context, int mouseX, int mouseY, float delta,
		int startIndex, CallbackInfo ci) {
		if (!ViewerLifecycleCoordinator.isEmiSelected() || getType() != SidebarType.INDEX) {
			cg$groupPositions = List.of();
			return;
		}
		List<GroupBackgroundRenderer.BackgroundPosition> positions = new java.util.ArrayList<>();
		List<? extends EmiIngredient> stacks = getStacks();
		int i = startIndex;
		outer: for (int yo = 0; yo < th; yo++) {
			for (int xo = 0; xo < getWidth(yo); xo++) {
				if (i >= stacks.size()) break outer;
				var position = EmiGroupRenderPass.positionFor(stacks.get(i++), getX(xo, yo) + 1, getY(xo, yo) + 1);
				if (position != null) positions.add(position);
			}
		}
		cg$groupPositions = List.copyOf(positions);
		EmiGroupRenderPass.drawTints(context.raw(), cg$groupPositions);
	}

	@Inject(method = "render", at = @At(value = "INVOKE",
		target = "Ldev/emi/emi/screen/StackBatcher;draw()V", shift = At.Shift.AFTER), require = 1)
	private void cg$drawGroupBorders(EmiDrawContext context, int mouseX, int mouseY, float delta,
		int startIndex, CallbackInfo ci) {
		EmiGroupRenderPass.drawBorders(context.raw(), cg$groupPositions);
		cg$groupPositions = List.of();
	}
}
