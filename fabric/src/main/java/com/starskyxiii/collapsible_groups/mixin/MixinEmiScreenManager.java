package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.emi.EmiHeaderInteractionPolicy;
import com.starskyxiii.collapsible_groups.compat.emi.EmiOverlayController;
import com.starskyxiii.collapsible_groups.compat.emi.EmiProjectionController;
import com.starskyxiii.collapsible_groups.compat.emi.EmiViewerAdapter;
import com.starskyxiii.collapsible_groups.compat.emi.GroupHeaderEmiStack;
import com.starskyxiii.collapsible_groups.compat.emi.EmiInteractionTrace;
import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.persistence.GroupExpandState;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import com.starskyxiii.collapsible_groups.viewer.ViewerOverlayHook;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Function;

@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class MixinEmiScreenManager {
	@Shadow private static List<? extends EmiIngredient> searchedStacks;
	@Shadow public static EmiIngredient pressedStack;
	@Shadow public static EmiIngredient draggedStack;

	@Inject(method = "recalculate", at = @At("TAIL"), require = 0)
	private static void cg$observeSearch(CallbackInfo ci) {
		if (!ViewerLifecycleCoordinator.isEmiSelected()) return;
		var panel = EmiScreenManager.getSearchPanel();
		if (panel != null && panel.getType() == SidebarType.INDEX) {
			EmiViewerAdapter.instance().observeIndexSearch(searchedStacks);
		}
	}

	@Inject(method = "stackInteraction", at = @At("HEAD"), cancellable = true, require = 1)
	private static void cg$consumeHeader(EmiStackInteraction interaction, Function<EmiBind, Boolean> function,
		CallbackInfoReturnable<Boolean> cir) {
		if (!ViewerLifecycleCoordinator.isEmiSelected()
			|| !(interaction.getStack() instanceof GroupHeaderEmiStack header)) return;
		try {
			var action = function.apply(EmiBind.LEFT_CLICK)
				? EmiHeaderInteractionPolicy.Action.ACTIVATE : EmiHeaderInteractionPolicy.Action.OTHER;
			if (EmiHeaderInteractionPolicy.decide(action)
				== EmiHeaderInteractionPolicy.Decision.TOGGLE_AND_CONSUME) {
				GroupExpandState.toggleById(header.groupId());
				EmiProjectionController.clearCache();
				EmiScreenManager.forceRecalculate();
				cir.setReturnValue(true);
				return;
			}
			cir.setReturnValue(false);
		} catch (Throwable error) {
			Constants.LOG.error("Failed to handle synthetic EMI group header interaction", error);
			EmiInteractionTrace.exception("stackInteraction/header", error);
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "addWidgets", at = @At("TAIL"), require = 0)
	private static void cg$layoutButton(Screen screen, CallbackInfo ci) {
		if (ViewerLifecycleCoordinator.isEmiSelected()) EmiOverlayController.instance().layout(screen);
	}

	@Inject(method = "renderWidgets", at = @At("TAIL"), require = 0)
	private static void cg$renderButton(EmiDrawContext context, int mouseX, int mouseY, float delta,
		EmiScreenBase base, CallbackInfo ci) {
		if (ViewerLifecycleCoordinator.isEmiSelected()) EmiOverlayController.instance().render(context.raw(), mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
	private static void cg$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (!ViewerLifecycleCoordinator.isEmiSelected()) return;
		cg$traceInput("mouseClicked/head", mouseX, mouseY, button, false);
		if (EmiOverlayController.instance().handleInput(new ViewerOverlayHook.Input(
			ViewerOverlayHook.Input.Type.MOUSE_CLICK, mouseX, mouseY, button, 0))) cir.setReturnValue(true);
	}

	@Inject(method = "mouseClicked", at = @At("RETURN"), require = 1)
	private static void cg$mouseClickedComplete(double mouseX, double mouseY, int button,
		CallbackInfoReturnable<Boolean> cir) {
		if (ViewerLifecycleCoordinator.isEmiSelected()) {
			cg$traceInput("mouseClicked/return", mouseX, mouseY, button, false);
		}
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), require = 1)
	private static void cg$mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY,
		CallbackInfoReturnable<Boolean> cir) {
		if (ViewerLifecycleCoordinator.isEmiSelected()) {
			cg$traceInput("mouseDragged/head", mouseX, mouseY, button, false);
		}
	}

	@Inject(method = "mouseDragged", at = @At("RETURN"), require = 1)
	private static void cg$mouseDraggedComplete(double mouseX, double mouseY, int button,
		double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
		if (ViewerLifecycleCoordinator.isEmiSelected()) {
			cg$traceInput("mouseDragged/return", mouseX, mouseY, button, false);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 1)
	private static void cg$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (!ViewerLifecycleCoordinator.isEmiSelected()) return;
		if (EmiOverlayController.instance().handleInput(new ViewerOverlayHook.Input(
			ViewerOverlayHook.Input.Type.KEY_PRESS, 0, 0, 0, keyCode))) cir.setReturnValue(true);
	}

	private static void cg$traceInput(String phase, double mouseX, double mouseY, int button,
		boolean nativeFinallyReached) {
		if (!EmiInteractionTrace.enabled()) return;
		try {
			EmiInteractionTrace.input(phase, button,
				EmiScreenManager.getHoveredStack((int) mouseX, (int) mouseY, true),
				pressedStack, draggedStack, nativeFinallyReached);
		} catch (Throwable error) {
			EmiInteractionTrace.exception(phase, error);
		}
	}
}
