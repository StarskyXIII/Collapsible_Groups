package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class GroupsButtonController implements IIconButtonController {

	private static final ResourceLocation TEXTURE =
		ResourceLocation.fromNamespaceAndPath("collapsible_groups", "textures/gui/groups_button.png");

	@Override
	public void initState(IButtonState state) {
		state.setIcon(GROUPS_ICON);
	}

	@Override
	public void getTooltips(ITooltipBuilder tooltip) {
		tooltip.add(Component.translatable(ModTranslationKeys.BUTTON_MANAGE_TOOLTIP));
	}

	@Override
	public boolean onPress(IJeiUserInput input) {
		if (!input.isSimulate()) {
			Minecraft mc = Minecraft.getInstance();
			mc.setScreen(new GroupManagerScreen(mc.screen));
		}
		return true;
	}

	private static final mezz.jei.api.gui.drawable.IDrawable GROUPS_ICON = new mezz.jei.api.gui.drawable.IDrawable() {
		private static final float SCALE = 16f / 24f;
		@Override public int getWidth()  { return 16; }
		@Override public int getHeight() { return 16; }
		@Override public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
			RenderSystem.setShaderColor(0.67f, 0.67f, 0.67f, 1.0f);
			guiGraphics.pose().pushPose();
			guiGraphics.pose().translate(xOffset, yOffset, 0);
			guiGraphics.pose().scale(SCALE, SCALE, 1f);
			guiGraphics.blit(TEXTURE, 0, 0, 0f, 0f, 24, 24, 24, 24);
			guiGraphics.pose().popPose();
			RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		}
	};
}
