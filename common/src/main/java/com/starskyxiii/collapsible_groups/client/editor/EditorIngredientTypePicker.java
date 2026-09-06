package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.widget.EditorChrome;
import com.starskyxiii.collapsible_groups.client.widget.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

final class EditorIngredientTypePicker {
	private final Font font;
	private final EditorChrome.Rect bounds;
	private final EditBox search;
	private final EditorTypeSelection selection = new EditorTypeSelection();
	private final Consumer<String> confirm;
	private final Runnable cancel;
	private int focus;
	private int offset;
	private long lastClick;
	private String lastClicked;
	private boolean dragging;
	private double dragY;
	private int dragOffset;

	EditorIngredientTypePicker(Font font, EditorChrome.Rect bounds, Consumer<String> confirm, Runnable cancel) {
		this.font = font;
		this.bounds = bounds;
		this.confirm = confirm;
		this.cancel = cancel;
		search = new EditBox(font, bounds.x() + 10, bounds.y() + 23, bounds.width() - 20, 16, Component.empty());
		search.setBordered(false);
		search.setMaxLength(512);
		search.setHint(Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_SEARCH));
		search.setResponder(value -> { selection.search(value); offset = 0; lastClicked = null; });
		search.setFocused(true);
		refresh();
	}

	private boolean refresh() {
		if (!selection.update(EditorRuntimeServices.get().ingredientTypes())) return false;
		offset = 0;
		lastClicked = null;
		dragging = false;
		return true;
	}

	private EditorChrome.Rect list() { return new EditorChrome.Rect(bounds.x() + 6, bounds.y() + 44, bounds.width() - 22, Math.max(16, bounds.height() - 76)); }
	private EditorChrome.Rect ok() { return new EditorChrome.Rect(bounds.right() - 62, bounds.bottom() - 26, 56, 20); }
	private EditorChrome.Rect back() { return new EditorChrome.Rect(bounds.right() - 124, bounds.bottom() - 26, 56, 20); }
	private int maxOffset() { return Math.max(0, selection.rows().size() * 18 - list().height()); }
	private boolean canConfirm() { return selection.catalog().status() == EditorIngredientTypes.Status.READY && selection.selected() != null; }

	void render(GuiGraphics g, int mx, int my) {
		refresh();
		UiSkinRenderer.drawPanel(g, bounds.x(), bounds.y(), bounds.width(), bounds.height());
		g.drawString(font, Component.translatable(ModTranslationKeys.EDITOR_RULES_TYPE_TITLE), bounds.x() + 6, bounds.y() + 6, UiPalette.TEXT_PRIMARY, false);
		UiSkinRenderer.drawOutline(g, bounds.x() + 6, bounds.y() + 19, bounds.width() - 12, 22,
			focus == 0 ? UiPalette.OUTLINE_SELECTED : UiPalette.OUTLINE_DARK);
		search.render(g, mx, my, 0);
		var list = list();
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		String hovered = null;
		try {
			for (int i = Math.max(0, offset / 18); i < selection.rows().size() && i * 18 - offset < list.height(); i++) {
				String id = selection.rows().get(i).id();
				int y = list.y() + i * 18 - offset;
				boolean hover = list.contains(mx, my) && my >= y && my < y + 18;
				if (id.equals(selection.selected()) || hover) g.fill(list.x(), y, list.right(), y + 18,
					id.equals(selection.selected()) ? 0x554488AA : 0x33FFFFFF);
				g.drawString(font, font.plainSubstrByWidth(id, list.width() - 6), list.x() + 3, y + 5, UiPalette.TEXT_PRIMARY, false);
				if (hover) hovered = id;
			}
			if (selection.rows().isEmpty()) {
				String key = selection.catalog().status() != EditorIngredientTypes.Status.READY
					? selection.catalog().status() == EditorIngredientTypes.Status.PENDING
						? ModTranslationKeys.EDITOR_RULES_TYPE_PENDING : ModTranslationKeys.EDITOR_RULES_TYPE_UNAVAILABLE
					: selection.catalog().options().isEmpty() ? ModTranslationKeys.EDITOR_RULES_TYPE_EMPTY : ModTranslationKeys.EDITOR_RULES_PICKER_EMPTY;
				g.drawWordWrap(font, Component.translatable(key), list.x() + 3, list.y() + 5, list.width() - 6, UiPalette.TEXT_MUTED);
			}
		} finally { g.disableScissor(); }
		if (focus == 1) UiSkinRenderer.drawOutline(g, list.x(), list.y(), list.width(), list.height(), UiPalette.OUTLINE_SELECTED);
		ScrollbarHelper.renderPixels(g, list.right() + ScrollbarHelper.GAP, list.y(), list.height(), list.height(), selection.rows().size() * 18, offset);
		button(g, back(), ModTranslationKeys.BUTTON_CANCEL, true, focus == 2, mx, my);
		button(g, ok(), ModTranslationKeys.EDITOR_RULES_PICKER_CONFIRM, canConfirm(), focus == 3, mx, my);
		if (hovered != null) g.renderTooltip(font, font.split(Component.literal(hovered), Math.max(100, bounds.width())), mx, my);
	}

	private void button(GuiGraphics g, EditorChrome.Rect rect, String key, boolean enabled, boolean focused, int mx, int my) {
		UiSkinRenderer.drawButton(g, font, rect.x(), rect.y(), rect.width(), rect.height(), Component.translatable(key).getString(),
			!enabled ? UiSkinRenderer.ButtonState.DISABLED : focused || rect.contains(mx, my) ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL);
	}

	private void accept() { if (!refresh() && canConfirm()) confirm.accept(selection.selected()); }
	private void focus(int value) { focus = value; search.setFocused(value == 0); }

	boolean click(double mx, double my) {
		if (refresh()) return true;
		if (!bounds.contains(mx, my) || back().contains(mx, my)) { cancel.run(); return true; }
		if (ok().contains(mx, my)) { accept(); return true; }
		if (my >= bounds.y() + 19 && my < bounds.y() + 41) { focus(0); search.mouseClicked(mx, my, 0); return true; }
		var list = list();
		if (mx >= list.right() && mx < bounds.right() - 6 && my >= list.y() && my < list.bottom()) {
			offset = ScrollbarHelper.trackClickToOffset(my, list.y(), list.height(), selection.rows().size() * 18, list.height(), offset);
			dragging = true; dragY = my; dragOffset = offset;
			return true;
		}
		if (list.contains(mx, my)) {
			focus(1);
			selection.select((int) (my - list.y() + offset) / 18);
			long now = System.currentTimeMillis();
			String id = selection.selected();
			if (id != null && id.equals(lastClicked) && now - lastClick < 350) { accept(); return true; }
			lastClick = now; lastClicked = id;
		}
		return true;
	}

	boolean key(int key, int scan, int mods) {
		refresh();
		if (key == 256) { cancel.run(); return true; }
		if (key == 258) { focus(Math.floorMod(focus + ((mods & 1) == 0 ? 1 : -1), 4)); return true; }
		if (key == 265 || key == 264) {
			selection.move(key == 264 ? 1 : -1);
			int y = selection.selectedIndex() * 18;
			offset = Math.max(0, Math.min(maxOffset(), y < offset ? y : y + 18 > offset + list().height() ? y + 18 - list().height() : offset));
			return true;
		}
		if (key == 257 || key == 335 || key == 32 && focus > 0) {
			if (focus == 2) cancel.run(); else accept();
			return true;
		}
		return search.isFocused() && search.keyPressed(key, scan, mods);
	}

	boolean character(char c, int mods) { return search.isFocused() && search.charTyped(c, mods); }
	boolean textFocused() { return search.isFocused(); }
	void scroll(double delta) { refresh(); offset = Math.max(0, Math.min(maxOffset(), offset - (int) Math.signum(delta) * 18)); }
	void drag(double my) {
		if (refresh() || !dragging) return;
		offset = Math.max(0, Math.min(maxOffset(), dragOffset + (int) ((my - dragY) * selection.rows().size() * 18 / Math.max(1, list().height()))));
	}
	void release() { dragging = false; }
}
