package com.starskyxiii.collapsible_groups.viewer;

/** Adapter hook for overlay placement, input composition, and search-field sizing. */
public interface ViewerOverlayHook {
	boolean shouldShowButton(boolean configuredVisible, boolean ingredientListVisible);

	Bounds placeButton(Bounds configButton, int gap);

	int adjustSearchFieldWidth(Bounds searchField, Bounds button, int gap);

	boolean handleInput(Input input);

	record Bounds(int x, int y, int width, int height) {}

	record Input(Type type, double mouseX, double mouseY, int button, int keyCode) {
		public enum Type {
			MOUSE_CLICK,
			MOUSE_RELEASE,
			KEY_PRESS
		}
	}
}
