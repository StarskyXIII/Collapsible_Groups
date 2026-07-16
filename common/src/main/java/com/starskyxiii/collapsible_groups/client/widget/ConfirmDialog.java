package com.starskyxiii.collapsible_groups.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class ConfirmDialog {
	public static final int WIDTH = 240;
	public static final int HEIGHT = 112;
	public static final int BUTTON_WIDTH = 96;
	public static final int BUTTON_HEIGHT = 20;
	public static final int BUTTON_GAP = 8;

	private ConfirmDialog() {}

	public static void render(GuiGraphics g, Font font, int screenWidth, int screenHeight,
	                          Component title, List<Component> bodyLines,
	                          Component primaryLabel, Component secondaryLabel,
	                          int mouseX, int mouseY) {
		Rect bounds = bounds(screenWidth, screenHeight);
		g.pose().pushPose();
		g.pose().translate(0, 0, 500);
		g.fill(0, 0, screenWidth, screenHeight, 0xAA000000);
		UiSkinRenderer.drawPanel(g, bounds.x(), bounds.y(), bounds.width(), bounds.height());
		UiSkinRenderer.drawOutline(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), UiPalette.OUTLINE_DARK);

		String titleText = title.getString();
		g.drawString(font, titleText, bounds.x() + (bounds.width() - font.width(titleText)) / 2,
			bounds.y() + 12, UiPalette.TEXT_PRIMARY, false);

		int bodyY = bounds.y() + (bodyLines.size() <= 1 ? 38 : 34);
		for (int i = 0; i < bodyLines.size(); i++) {
			drawCenteredLine(g, font, bounds, bodyLines.get(i).getString(), bodyY + i * 12, UiPalette.TEXT_MUTED);
		}

		Rect primary = primaryButton(screenWidth, screenHeight);
		Rect secondary = secondaryButton(screenWidth, screenHeight);
		UiSkinRenderer.drawButton(g, font, primary.x(), primary.y(), primary.width(), primary.height(),
			primaryLabel.getString(), buttonState(primary.contains(mouseX, mouseY)));
		UiSkinRenderer.drawButton(g, font, secondary.x(), secondary.y(), secondary.width(), secondary.height(),
			secondaryLabel.getString(), buttonState(secondary.contains(mouseX, mouseY)));
		g.pose().popPose();
	}

	public static Action hitTest(int screenWidth, int screenHeight, double mouseX, double mouseY) {
		if (primaryButton(screenWidth, screenHeight).contains(mouseX, mouseY)) return Action.PRIMARY;
		if (secondaryButton(screenWidth, screenHeight).contains(mouseX, mouseY)) return Action.SECONDARY;
		return Action.NONE;
	}

	public static Rect bounds(int screenWidth, int screenHeight) {
		return new Rect((screenWidth - WIDTH) / 2, (screenHeight - HEIGHT) / 2, WIDTH, HEIGHT);
	}

	public static Rect primaryButton(int screenWidth, int screenHeight) {
		Rect bounds = bounds(screenWidth, screenHeight);
		int groupWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
		int x = bounds.x() + (bounds.width() - groupWidth) / 2;
		int y = bounds.y() + bounds.height() - BUTTON_HEIGHT - 12;
		return new Rect(x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
	}

	public static Rect secondaryButton(int screenWidth, int screenHeight) {
		Rect primary = primaryButton(screenWidth, screenHeight);
		return new Rect(primary.right() + BUTTON_GAP, primary.y(), BUTTON_WIDTH, BUTTON_HEIGHT);
	}

	private static void drawCenteredLine(GuiGraphics g, Font font, Rect bounds, String text, int y, int color) {
		String clipped = font.plainSubstrByWidth(text, bounds.width() - 24);
		g.drawString(font, clipped, bounds.x() + (bounds.width() - font.width(clipped)) / 2, y, color, false);
	}

	private static UiSkinRenderer.ButtonState buttonState(boolean hovered) {
		return hovered ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL;
	}

	public enum Action {
		NONE,
		PRIMARY,
		SECONDARY
	}

	public record Rect(int x, int y, int width, int height) {
		public int right() {
			return x + width;
		}

		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < y + height;
		}
	}
}
