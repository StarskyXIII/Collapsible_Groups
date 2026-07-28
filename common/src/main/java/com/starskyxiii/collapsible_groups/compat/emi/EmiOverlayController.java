package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.compat.jei.manager.GroupManagerScreen;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import com.starskyxiii.collapsible_groups.viewer.ViewerOverlayHook;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Shared-skin Groups button composed into EMI's fixed bottom-left widget row. */
public final class EmiOverlayController implements ViewerOverlayHook {
	private static final EmiOverlayController INSTANCE = new EmiOverlayController();
	private static final int SIZE = 20;
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(
		"collapsible_groups", "textures/gui/groups_button.png");
	private boolean visible;
	private boolean enabled;
	private final SizedButtonWidget button = new SizedButtonWidget(0, 0, SIZE, SIZE, 184, 0,
		() -> enabled, ignored -> openManager(),
		List.of(Component.translatable(ModTranslationKeys.BUTTON_MANAGE_TOOLTIP)));
	private int x;
	private int y;

	private EmiOverlayController() {}
	public static EmiOverlayController instance() { return INSTANCE; }

	public void layout(Screen screen) {
		if (!ViewerLifecycleCoordinator.isEmiSelected()) {
			visible = false;
			enabled = false;
			button.visible = false;
			return;
		}
		x = EmiScreenManager.tree.getX() + EmiScreenManager.tree.getWidth() + 2;
		y = EmiScreenManager.tree.getY();
		visible = shouldShowButton(Services.CONFIG.showManagerButton(), !EmiScreenBase.getCurrent().isEmpty());
		enabled = !EmiScreenManager.isDisabled();
		button.setX(x);
		button.setY(y);
		button.visible = visible;
	}

	public void render(GuiGraphics graphics, int mouseX, int mouseY) {
		if (!visible) return;
		button.render(graphics, mouseX, mouseY, 0);
		graphics.pose().pushPose();
		graphics.pose().translate(x + 2, y + 2, 10);
		graphics.pose().scale(16f / 24f, 16f / 24f, 1f);
		graphics.blit(ICON, 0, 0, 0f, 0f, 24, 24, 24, 24);
		graphics.pose().popPose();
	}

	@Override public boolean shouldShowButton(boolean configuredVisible, boolean ingredientListVisible) {
		return configuredVisible && ingredientListVisible && ViewerLifecycleCoordinator.isEmiSelected();
	}
	@Override public Bounds placeButton(Bounds configButton, int gap) {
		return new Bounds(configButton.x() + (configButton.width() + gap) * 2,
			configButton.y(), configButton.width(), configButton.height());
	}
	@Override public int adjustSearchFieldWidth(Bounds searchField, Bounds button, int gap) { return searchField.width(); }

	@Override
	public boolean handleInput(Input input) {
		if (!ViewerLifecycleCoordinator.isEmiSelected() || !visible || !enabled) return false;
		return switch (input.type()) {
			case MOUSE_CLICK -> button.mouseClicked(input.mouseX(), input.mouseY(), input.button());
			case KEY_PRESS -> false;
		};
	}

	private static void openManager() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.setScreen(new GroupManagerScreen(minecraft.screen));
	}

}
