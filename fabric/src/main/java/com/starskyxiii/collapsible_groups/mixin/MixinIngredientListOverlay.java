package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiIngredientListOverlayController;
import com.starskyxiii.collapsible_groups.platform.Services;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.input.GuiTextFieldFilter;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.overlay.IngredientListOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.stream.Stream;

@Mixin(value = IngredientListOverlay.class, remap = false)
public abstract class MixinIngredientListOverlay {
	@Shadow private IconButton configButton;
	@Shadow private GuiTextFieldFilter searchField;
	@Shadow public abstract boolean isListDisplayed();
	@Shadow public abstract Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double mouseX, double mouseY);
	@Unique private JeiIngredientListOverlayController cg$controller;

	@Inject(
		method = "<init>(Lmezz/jei/gui/overlay/ingredients/IIngredientGridSource;Lmezz/jei/gui/filter/IFilterTextSource;" +
			"Lmezz/jei/api/runtime/IScreenHelper;Lmezz/jei/gui/overlay/ingredients/IIngredientListOverlayContents;" +
			"Lmezz/jei/gui/overlay/bookmarks/history/LookupHistoryOverlay;" +
			"Lmezz/jei/common/config/IIngredientGridConfig;Lmezz/jei/common/config/IClientConfig;" +
			"Lmezz/jei/common/config/IClientToggleState;Lmezz/jei/common/input/IInternalKeyMappings;)V",
		at = @At("TAIL"),
		require = 1
	)
	private void cg$onInit(CallbackInfo ci) {
		this.cg$controller = new JeiIngredientListOverlayController(
			this.configButton, this.searchField,
			() -> ((MixinGuiTextFieldFilterAccessor) (Object) this.searchField).cg$getArea(),
			this::isListDisplayed, Services.CONFIG::showManagerButton);
	}

	@Inject(method = "drawScreen", at = @At("HEAD"), require = 0)
	private void cg$beforeDraw(Minecraft minecraft, GuiGraphicsExtractor graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci) {
		this.cg$controller.beforeDraw(graphics);
	}

	@Inject(method = "drawScreen", at = @At("TAIL"), require = 0)
	private void cg$afterDraw(Minecraft minecraft, GuiGraphicsExtractor graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci) {
		this.cg$controller.afterDraw(graphics, mouseX, mouseY, partialTicks);
	}

	@Inject(method = "drawTooltips", at = @At("TAIL"), require = 0)
	private void cg$drawTooltips(Minecraft minecraft, GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		this.cg$controller.drawTooltips(graphics, mouseX, mouseY);
	}

	@Inject(
		method = "createInputHandler()Lmezz/jei/gui/input/IUserInputHandler;",
		at = @At("RETURN"),
		cancellable = true,
		require = 1
	)
	private void cg$wrapInputHandler(CallbackInfoReturnable<IUserInputHandler> cir) {
		if (this.cg$controller == null) return;
		IUserInputHandler original = cir.getReturnValue();
		IUserInputHandler headerFallback = new IUserInputHandler() {
			@Override
			public Optional<IUserInputHandler> handleUserInput(Screen screen, IGuiProperties guiProperties,
				UserInput input, IInternalKeyMappings keyBindings) {
				boolean leftMouseButton = input.ifMouseEvent((event, doubleClick) -> event.button() == 0);
				if (!leftMouseButton) return Optional.empty();
				return MixinIngredientListOverlay.this.getIngredientUnderMouse(input.getMouseX(), input.getMouseY())
					.filter(clicked -> clicked.getTypedIngredient().getIngredient() instanceof GroupIcon)
					.findFirst()
					.map(clicked -> {
						if (!input.isSimulate()) {
							GroupIcon icon = (GroupIcon) clicked.getTypedIngredient().getIngredient();
							GroupRegistry.toggleById(icon.groupId());
							GroupRegistry.notifyJeiStructureOnly();
						}
						return this;
					});
			}
		};
		cir.setReturnValue(this.cg$controller.wrapInputHandler(
			new CombinedInputHandler("IngredientListOverlay_withGroupHeaderFallback", headerFallback, original)));
	}
}
