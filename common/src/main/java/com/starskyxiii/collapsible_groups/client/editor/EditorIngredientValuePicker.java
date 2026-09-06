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

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

final class EditorIngredientValuePicker {
	private final Font font;
	private final EditorChrome.Rect bounds;
	private final String type;
	private final EditorValuePickerKind kind;
	private final EditBox search;
	private final EditorValueSelection selection = new EditorValueSelection();
	private final EditorNamespaceCatalog namespaces = new EditorNamespaceCatalog();
	private final EditorValuePickerLayout footer;
	private final Consumer<String> confirm;
	private final Consumer<String> manual;
	private final Runnable cancel;
	private WeakReference<EditorRuntimeAccess> runtime = new WeakReference<>(null);
	private int focus;
	private int offset;
	private long lastClick;
	private String lastClicked;
	private boolean dragging;
	private double dragY;
	private int dragOffset;

	EditorIngredientValuePicker(Font font, EditorChrome.Rect bounds, String type, EditorValuePickerKind kind,
		Consumer<String> confirm, Consumer<String> manual, Runnable cancel) {
		this.font = font;
		this.bounds = bounds;
		this.type = type;
		this.kind = kind;
		this.confirm = confirm;
		this.manual = manual;
		this.cancel = cancel;
		footer = EditorValuePickerLayout.create(bounds, font.width(Component.translatable(ModTranslationKeys.BUTTON_CANCEL)),
			font.width(Component.translatable(kind.manualKey)),
			font.width(Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_CONFIRM)));
		var rect = searchRect();
		search = new EditBox(font, rect.x() + 4, rect.y() + (rect.height() - font.lineHeight) / 2,
			rect.width() - 8, font.lineHeight, Component.empty());
		search.setBordered(false);
		search.setMaxLength(512);
		search.setHint(Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_SEARCH));
		search.setResponder(value -> { selection.search(value); offset = 0; lastClicked = null; });
		search.setFocused(true);
		refresh();
	}

	void tick() {
		var next = EditorRuntimeServices.find().orElse(null);
		var previous = runtime.get();
		if (next != previous) {
			if (previous != null) kind.cancel(previous);
			namespaces.clear();
			runtime = new WeakReference<>(next);
		}
		if (next != null) kind.update(next, type);
		if (kind == EditorValuePickerKind.NAMESPACE)
			namespaces.update(next == null ? EditorIngredientIds.UNAVAILABLE : next.ingredientIds(type));
		refresh();
	}

	void close() {
		var previous = runtime.get();
		if (previous != null) kind.cancel(previous);
		runtime.clear();
		namespaces.clear();
	}

	private boolean refresh() {
		var next = kind.snapshot(EditorRuntimeServices.find().orElse(null), type, namespaces);
		if (!selection.update(next)) return false;
		offset = 0;
		lastClicked = null;
		dragging = false;
		return true;
	}

	private EditorChrome.Rect searchRect() { return new EditorChrome.Rect(bounds.x() + 6, bounds.y() + 33, bounds.width() - 12, 14); }
	private EditorChrome.Rect list() {
		int top = searchRect().bottom() + font.lineHeight * 2 + 8;
		return new EditorChrome.Rect(bounds.x() + 6, top, bounds.width() - 22, Math.max(0, footer.top() - top - 6));
	}
	private int maxOffset() { return Math.max(0, selection.rows().size() * 18 - list().height()); }
	private boolean canConfirm() { return selection.catalog().ready() && selection.selected() != null; }

	void render(GuiGraphics g, int mx, int my) {
		refresh();
		UiSkinRenderer.drawPanel(g, bounds.x(), bounds.y(), bounds.width(), bounds.height());
		g.drawString(font, Component.translatable(kind.titleKey), bounds.x() + 6, bounds.y() + 6, UiPalette.TEXT_PRIMARY, false);
		g.drawString(font, font.plainSubstrByWidth(type, bounds.width() - 12), bounds.x() + 6, bounds.y() + 19, UiPalette.TEXT_MUTED, false);
		var rect = searchRect();
		UiSkinRenderer.drawOutline(g, rect.x(), rect.y(), rect.width(), rect.height(), focus == 0 ? UiPalette.OUTLINE_SELECTED : UiPalette.OUTLINE_DARK);
		search.render(g, mx, my, 0);
		var message = Component.translatable(selection.catalog().statusKey());
		var lines = font.split(message, bounds.width() - 12);
		for (int i = 0; i < Math.min(2, lines.size()); i++)
			g.drawString(font, lines.get(i), bounds.x() + 6, rect.bottom() + 4 + i * font.lineHeight, UiPalette.TEXT_MUTED, false);
		var list = list();
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		String hovered = null;
		try {
			for (int i = Math.max(0, offset / 18); i < selection.rows().size() && i * 18 - offset < list.height(); i++) {
				String id = selection.rows().get(i);
				int y = list.y() + i * 18 - offset;
				boolean hover = list.contains(mx, my) && my >= y && my < y + 18;
				if (id.equals(selection.selected()) || hover) g.fill(list.x(), y, list.right(), y + 18,
					id.equals(selection.selected()) ? 0x554488AA : 0x33FFFFFF);
				g.drawString(font, font.plainSubstrByWidth(id, list.width() - 6), list.x() + 3, y + 5, UiPalette.TEXT_PRIMARY, false);
				if (hover) hovered = id;
			}
			if (selection.rows().isEmpty() && selection.catalog().ready())
				g.drawWordWrap(font, Component.translatable(selection.catalog().values().isEmpty()
					? kind.emptyKey : ModTranslationKeys.EDITOR_RULES_PICKER_EMPTY),
					list.x() + 3, list.y() + 5, list.width() - 6, UiPalette.TEXT_MUTED);
		} finally { g.disableScissor(); }
		if (focus == 1) UiSkinRenderer.drawOutline(g, list.x(), list.y(), list.width(), list.height(), UiPalette.OUTLINE_SELECTED);
		ScrollbarHelper.renderPixels(g, list.right() + ScrollbarHelper.GAP, list.y(), list.height(), list.height(), selection.rows().size() * 18, offset);
		button(g, footer.cancel(), ModTranslationKeys.BUTTON_CANCEL, true, focus == 2, mx, my);
		button(g, footer.manual(), kind.manualKey, true, focus == 3, mx, my);
		button(g, footer.confirm(), ModTranslationKeys.EDITOR_RULES_PICKER_CONFIRM, canConfirm(), focus == 4, mx, my);
		if (hovered != null) g.renderTooltip(font, font.split(Component.literal(hovered), Math.max(100, bounds.width())), mx, my);
		else if (mx >= bounds.x() + 6 && mx < bounds.right() - 6) {
			if (my >= bounds.y() + 19 && my < bounds.y() + 19 + font.lineHeight)
				g.renderTooltip(font, font.split(Component.literal(type), bounds.width()), mx, my);
			else if (my >= rect.bottom() + 4 && my < list.y()) g.renderTooltip(font, font.split(message, bounds.width()), mx, my);
		}
	}

	private void button(GuiGraphics g, EditorChrome.Rect rect, String key, boolean enabled, boolean focused, int mx, int my) {
		String label = Component.translatable(key).getString();
		UiSkinRenderer.drawButton(g, font, rect.x(), rect.y(), rect.width(), rect.height(), font.plainSubstrByWidth(label, rect.width() - 8),
			!enabled ? UiSkinRenderer.ButtonState.DISABLED : focused || rect.contains(mx, my) ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL);
		if (rect.contains(mx, my) && font.width(label) > rect.width() - 8) g.renderTooltip(font, Component.literal(label), mx, my);
	}

	private void accept() { if (!refresh() && canConfirm()) confirm.accept(selection.selected()); }
	private void focus(int value) { focus = value; search.setFocused(value == 0); }

	boolean click(double mx, double my) {
		boolean changed = refresh();
		if (!bounds.contains(mx, my) || footer.cancel().contains(mx, my)) { cancel.run(); return true; }
		if (footer.manual().contains(mx, my)) { manual.accept(search.getValue()); return true; }
		if (changed) return true;
		if (footer.confirm().contains(mx, my)) { accept(); return true; }
		if (searchRect().contains(mx, my)) {
			focus(0);
			search.mouseClicked(Math.max(search.getX(), Math.min(search.getX() + search.getWidth() - 1, mx)),
				Math.max(search.getY(), Math.min(search.getY() + search.getHeight() - 1, my)), 0);
			return true;
		}
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
		if (key == 258) { focus(Math.floorMod(focus + ((mods & 1) == 0 ? 1 : -1), 5)); return true; }
		if (key == 265 || key == 264) {
			selection.move(key == 264 ? 1 : -1);
			int y = selection.selectedIndex() * 18;
			offset = Math.max(0, Math.min(maxOffset(), y < offset ? y : y + 18 > offset + list().height() ? y + 18 - list().height() : offset));
			return true;
		}
		if (key == 257 || key == 335 || key == 32 && focus > 0) {
			if (focus == 2) cancel.run();
			else if (focus == 3) manual.accept(search.getValue());
			else accept();
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
