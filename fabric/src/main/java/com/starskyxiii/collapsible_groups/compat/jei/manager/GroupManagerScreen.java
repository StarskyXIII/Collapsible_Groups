package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.compat.jei.GroupUiState;
import com.starskyxiii.collapsible_groups.compat.jei.editor.GroupEditorScreen;
import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.platform.Services;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Item-only group manager for the Fabric loader. */
public class GroupManagerScreen extends Screen {
	private static final int CARD_WIDTH    = 162;
	private static final int CARD_HEIGHT   = 108;
	private static final int CARD_PADDING  = 6;
	private static final int PREVIEW_COLS  = 8;
	private static final int PREVIEW_ROWS  = 3;
	private static final int ITEM_SIZE     = 18;
	private static final int HEADER_HEIGHT = 32;
	private static final int FOOTER_HEIGHT = 28;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int BTN_Y_OFF     = CARD_HEIGHT - 22;
	private static final int BTN_H         = 18;

	private record ItemCard(GroupDefinition group, List<ItemStack> items, List<GroupPreviewEntry> previewEntries) {
		String id()          { return group.id(); }
		String displayName() {
			String resolved = group.name();
			String name = resolved.isEmpty() ? group.id() : resolved;
			if (group.id().startsWith("__kjs_"))
				return Component.translatable(ModTranslationKeys.MANAGER_PREFIX_KUBEJS, name).getString();
			if (GroupRegistry.isBuiltin(group.id()))
				return Component.translatable(ModTranslationKeys.MANAGER_PREFIX_BUILTIN, name).getString();
			return name;
		}
		boolean isEditable() { return !group.id().startsWith("__kjs_") && !GroupRegistry.isBuiltin(group.id()); }
	}

	private final Screen previousScreen;
	private final boolean kubeJsLoaded;
	private final Map<String, Integer> previewScrollOffsets = new HashMap<>();
	private List<ItemCard> allCards      = new ArrayList<>();
	private List<ItemCard> filteredCards = new ArrayList<>();
	private int cols = 1;
	private int scrollPixelOffset = 0;

	// Filter state
	private boolean showBuiltin       = GroupUiState.showBuiltin();
	private boolean showKubeJs        = GroupUiState.showKubeJs();
	private boolean builtinFilterHeld = false;
	private boolean kubejsFilterHeld  = false;

	private static final int BACK_BTN_X    = 6,  BACK_BTN_Y = 6, BACK_BTN_W = 50, BACK_BTN_H = 20;
	private static final int BUILTIN_BTN_X = 62, BUILTIN_BTN_W = 72;
	private static final int KUBEJS_BTN_X  = 140, KUBEJS_BTN_W = 65;
	private static final int NEW_BTN_W = 110, NEW_BTN_H = 20;
	private boolean backButtonHeld     = false;
	private boolean newGroupButtonHeld = false;

	private int hoveredCardIndex  = -1;
	private int hoveredButtonType = -1;
	private boolean isDraggingScrollbar   = false;
	private double  sbDragStartMouseY;
	private int     sbDragStartPixelOffset;

	public GroupManagerScreen(Screen previousScreen) {
		super(Component.translatable(ModTranslationKeys.SCREEN_TITLE));
		this.previousScreen = previousScreen;
		this.kubeJsLoaded   = Services.PLATFORM.isModLoaded("kubejs");
	}

	@Override
	protected void init() {
		rebuildCards();
		calcLayout();
		clearWidgets();
	}

	private void rebuildCards() {
		allCards = new ArrayList<>();
		for (GroupDefinition group : GroupRegistry.getAllIncludingKubeJs()) {
			List<ItemStack> items = GroupRegistry.getFullMatchItems(group);
			allCards.add(new ItemCard(group, items, GroupPreviewEntry.fromItems(items)));
		}
		previewScrollOffsets.keySet().retainAll(
			allCards.stream().map(ItemCard::id).collect(Collectors.toSet()));
		rebuildFilteredCards();
	}

	private void rebuildFilteredCards() {
		filteredCards = allCards.stream().filter(card -> {
			if (GroupRegistry.isBuiltin(card.id()) && !showBuiltin) return false;
			if (card.id().startsWith("__kjs_") && !showKubeJs) return false;
			return true;
		}).toList();
		scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
	}

	private void calcLayout() {
		int usableWidth = this.width - CARD_PADDING * 2 - SCROLLBAR_WIDTH - CARD_PADDING;
		cols = Math.max(1, usableWidth / (CARD_WIDTH + CARD_PADDING));
		scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
	}

	private void updateCardEnabled(String id, boolean enabled) {
		for (int i = 0; i < allCards.size(); i++) {
			ItemCard ic = allCards.get(i);
			if (ic.id().equals(id)) {
				GroupDefinition updated = ic.group().withEnabled(enabled);
				allCards.set(i, new ItemCard(updated, ic.items(), ic.previewEntries()));
				rebuildFilteredCards();
				return;
			}
		}
	}

	private void removeCard(String id) {
		allCards.removeIf(c -> c.id().equals(id));
		previewScrollOffsets.remove(id);
		rebuildFilteredCards();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		extractBackground(g, mouseX, mouseY, partialTicks);
		hoveredCardIndex  = -1;
		hoveredButtonType = -1;

		int vpTop    = HEADER_HEIGHT;
		int vpBottom = this.height - FOOTER_HEIGHT;

		g.fill(0, 0, this.width, vpTop,    0xCC0E0E1A);
		g.fill(0, vpTop, this.width, vpTop + 1, 0x33667799);
		g.fill(0, vpBottom, this.width, this.height, 0xAA0E0E1A);
		g.fill(0, vpBottom, this.width, vpBottom + 1, 0x33667799);

		g.enableScissor(0, vpTop, this.width, vpBottom);
		for (int i = 0; i < filteredCards.size(); i++) renderCard(g, i, mouseX, mouseY);
		g.disableScissor();

		renderScrollbar(g);

		boolean backHover = isMouseOver(mouseX, mouseY, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H);
		renderCardButton(g, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H,
			Component.translatable(ModTranslationKeys.MANAGER_BTN_BACK).getString(), backHover || backButtonHeld);

		boolean builtinHover = isMouseOver(mouseX, mouseY, BUILTIN_BTN_X, BACK_BTN_Y, BUILTIN_BTN_W, BACK_BTN_H);
		renderFilterButton(g, BUILTIN_BTN_X, BACK_BTN_Y, BUILTIN_BTN_W, BACK_BTN_H,
			Component.translatable(ModTranslationKeys.MANAGER_BTN_FILTER_BUILTIN).getString(),
			showBuiltin, builtinHover || builtinFilterHeld, 0xAA665533);

		if (kubeJsLoaded) {
			boolean kubejsHover = isMouseOver(mouseX, mouseY, KUBEJS_BTN_X, BACK_BTN_Y, KUBEJS_BTN_W, BACK_BTN_H);
			renderFilterButton(g, KUBEJS_BTN_X, BACK_BTN_Y, KUBEJS_BTN_W, BACK_BTN_H,
				Component.translatable(ModTranslationKeys.MANAGER_BTN_FILTER_KUBEJS).getString(),
				showKubeJs, kubejsHover || kubejsFilterHeld, 0xAA664488);
		}

		int newBtnX = this.width - NEW_BTN_W - 6;
		boolean newHover = isMouseOver(mouseX, mouseY, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H);
		renderCardButton(g, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H,
			Component.translatable(ModTranslationKeys.MANAGER_BTN_NEW_GROUP).getString(), newHover || newGroupButtonHeld);

		g.centeredText(font, this.title, this.width / 2, 8, 0xFFFFFFFF);
		Component countText = filteredCards.size() == allCards.size()
			? Component.translatable(ModTranslationKeys.MANAGER_COUNT_ALL, allCards.size())
			: Component.translatable(ModTranslationKeys.MANAGER_COUNT_FILTERED, filteredCards.size(), allCards.size());
		g.centeredText(font, countText, this.width / 2, 20, 0x8899AABB);
		g.text(font, Component.translatable(ModTranslationKeys.MANAGER_FOOTER_HINT),
			6, vpBottom + (FOOTER_HEIGHT - font.lineHeight) / 2 + 1, 0x8899AABB, false);

		for (var child : this.children()) {
			if (child instanceof net.minecraft.client.gui.components.Renderable r) {
				r.extractRenderState(g, mouseX, mouseY, partialTicks);
			}
		}
	}

	private void renderCard(GuiGraphicsExtractor g, int index, int mouseX, int mouseY) {
		ItemCard card = filteredCards.get(index);
		int[] pos = cardPos(index);
		int x = pos[0], y = pos[1];
		if (y + CARD_HEIGHT < HEADER_HEIGHT || y > this.height - FOOTER_HEIGHT) return;

		int borderColor = card.isEditable()
			? (card.group().enabled() ? 0x55339966 : 0x55993333)
			: (GroupRegistry.isBuiltin(card.id()) ? 0x55665533 : 0x55664488);

		g.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, 0x55101020);
		drawOutline(g, x, y, CARD_WIDTH, CARD_HEIGHT, borderColor);

		int previewX  = x + 3;
		int previewY  = y + 25;
		int rowOffset = previewScrollOffsets.getOrDefault(card.id(), 0);
		renderPreviewEntries(g, card.previewEntries(), previewX, previewY, rowOffset);

		int previewTotalRows = totalRowsForCard(card);
		if (previewTotalRows > PREVIEW_ROWS) {
			int sbX    = previewX + PREVIEW_COLS * ITEM_SIZE + 2;
			int sbH    = PREVIEW_ROWS * ITEM_SIZE;
			int maxRow = previewTotalRows - PREVIEW_ROWS;
			int thumbH = Math.max(6, sbH * PREVIEW_ROWS / previewTotalRows);
			int thumbY = previewY + (maxRow > 0 ? (sbH - thumbH) * rowOffset / maxRow : 0);
			g.fill(sbX, previewY, sbX + 3, previewY + sbH, 0x18667799);
			g.fill(sbX, thumbY,   sbX + 3, thumbY + thumbH, 0x6699AABB);
		}

		if (isMouseOver(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT - 22))
			g.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT - 22, 0x18667799);

		boolean cardHovered = isMouseOver(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT);
		renderScrollingText(g, card.displayName(), x + 4, y + 4, CARD_WIDTH - 8, 0xFFFFFFFF, cardHovered);
		Component countLabel = Component.translatable(ModTranslationKeys.COUNT_ITEMS, card.items().size());
		g.text(font, countLabel, x + 4, y + 14, 0x7799AABB, false);

		boolean inVp    = isInsideCardViewport(mouseX, mouseY);
		int buttonY     = y + BTN_Y_OFF;
		if (card.isEditable()) {
			boolean toggleHover = inVp && isMouseOver(mouseX, mouseY, x + 2,   buttonY, 74, BTN_H);
			boolean editHover   = inVp && isMouseOver(mouseX, mouseY, x + 80,  buttonY, 38, BTN_H);
			boolean delHover    = inVp && isMouseOver(mouseX, mouseY, x + 122, buttonY, 36, BTN_H);
			renderCardButton(g, x + 2,   buttonY, 74, BTN_H,
				Component.translatable(card.group().enabled() ? ModTranslationKeys.MANAGER_BTN_ENABLED : ModTranslationKeys.MANAGER_BTN_DISABLED).getString(), toggleHover);
			renderCardButton(g, x + 80,  buttonY, 38, BTN_H,
				Component.translatable(ModTranslationKeys.MANAGER_BTN_EDIT).getString(), editHover);
			renderCardButton(g, x + 122, buttonY, 36, BTN_H,
				Component.translatable(ModTranslationKeys.MANAGER_BTN_DELETE).getString(), delHover);
			if (toggleHover) { hoveredCardIndex = index; hoveredButtonType = 0; }
			if (editHover)   { hoveredCardIndex = index; hoveredButtonType = 1; }
			if (delHover)    { hoveredCardIndex = index; hoveredButtonType = 2; }
			} else {
				boolean isBuiltin = GroupRegistry.isBuiltin(card.id());
				String label  = Component.translatable(isBuiltin ? ModTranslationKeys.MANAGER_BADGE_BUILTIN : ModTranslationKeys.MANAGER_BADGE_KUBEJS).getString();
				int border = isBuiltin ? 0x44665533 : 0x44664488;
				int text   = isBuiltin ? 0x88BBAA66 : 0x8899AABB;
				g.fill(x + 2, buttonY, x + CARD_WIDTH - 2, buttonY + BTN_H, 0x880E0E1A);
				int w = CARD_WIDTH - 4;
				drawOutline(g, x + 2, buttonY, w, BTN_H, border);
				g.text(font, label, x + 2 + (w - font.width(label)) / 2, buttonY + (BTN_H - 8) / 2, text, false);
			}
		}

	private void renderPreviewEntries(GuiGraphicsExtractor g, List<GroupPreviewEntry> entries, int previewX, int previewY, int rowOffset) {
		int remaining  = entries.size() - (rowOffset + PREVIEW_ROWS) * PREVIEW_COLS;
		int maxVisible = remaining > 0 ? PREVIEW_ROWS * PREVIEW_COLS - 1 : PREVIEW_ROWS * PREVIEW_COLS;
		int idx = rowOffset * PREVIEW_COLS;
		int rendered = 0;
		for (int row = 0; row < PREVIEW_ROWS && idx < entries.size() && rendered < maxVisible; row++) {
			for (int col = 0; col < PREVIEW_COLS && idx < entries.size() && rendered < maxVisible; col++) {
				entries.get(idx).render(g, previewX + col * ITEM_SIZE, previewY + row * ITEM_SIZE);
				idx++;
				rendered++;
			}
		}
		if (remaining > 0) {
			int lastX = previewX + (PREVIEW_COLS - 1) * ITEM_SIZE;
			int lastY = previewY + (PREVIEW_ROWS - 1) * ITEM_SIZE;
			String more = "+" + (remaining + 1);
			g.pose().pushMatrix(); g.nextStratum();
			g.fill(lastX, lastY, lastX + ITEM_SIZE, lastY + ITEM_SIZE, 0x88000000);
			g.text(font, more, lastX + (ITEM_SIZE - font.width(more)) / 2, lastY + (ITEM_SIZE - 8) / 2, 0xFFFFFFFF, false);
			g.pose().popMatrix();
		}
	}

	private void renderCardButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean hovered) {
		int bg = hovered ? 0xCC1A1A2E : 0x880E0E1A, border = hovered ? 0x8899AABB : 0x44667799, text = hovered ? 0xFFFFFFFF : 0xCC99AABB;
		g.fill(x, y, x + w, y + h, bg);
		drawOutline(g, x, y, w, h, border);
		g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, text, false);
	}

	private void renderFilterButton(GuiGraphicsExtractor g, int x, int y, int w, int h,
	                                String label, boolean active, boolean hovered, int accentBorder) {
		int bg     = hovered ? 0xCC1A1A2E : 0x880E0E1A;
		int border = hovered ? 0x8899AABB : (active ? accentBorder : 0x44334444);
		int text   = hovered ? 0xFFFFFFFF : (active ? 0xCC99AABB : 0x55667788);
		g.fill(x, y, x + w, y + h, bg);
		drawOutline(g, x, y, w, h, border);
		g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, text, false);
	}

	private void drawOutline(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
		int right = x + width;
		int bottom = y + height;
		g.fill(x, y, right, y + 1, color);
		g.fill(x, bottom - 1, right, bottom, color);
		g.fill(x, y + 1, x + 1, bottom - 1, color);
		g.fill(right - 1, y + 1, right, bottom - 1, color);
	}

	private void renderScrollingText(GuiGraphicsExtractor g, String text, int x, int y,
	                                 int maxWidth, int color, boolean hovered) {
		int textWidth = font.width(text);
		if (textWidth <= maxWidth) {
			g.text(font, text, x, y, color, true);
			return;
		}
		if (!hovered) {
			String truncated = font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
			g.text(font, truncated, x, y, color, true);
			return;
		}
		g.enableScissor(x, y - 1, x + maxWidth, y + font.lineHeight + 1);
		int   gap        = 20;
		int   totalCycle = textWidth + gap;
		float scrollOffset = (System.currentTimeMillis() % (totalCycle * 30L)) / 30.0f;
		int   drawX1     = (int)(x - scrollOffset);
		int   drawX2     = drawX1 + totalCycle;
		g.text(font, text, drawX1, y, color, true);
		g.text(font, text, drawX2, y, color, true);
		g.disableScissor();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		if (button == 0 && isMouseOver(mouseX, mouseY, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H)) { backButtonHeld = true; return true; }
		if (button == 0 && isMouseOver(mouseX, mouseY, BUILTIN_BTN_X, BACK_BTN_Y, BUILTIN_BTN_W, BACK_BTN_H)) { builtinFilterHeld = true; return true; }
		if (kubeJsLoaded && button == 0 && isMouseOver(mouseX, mouseY, KUBEJS_BTN_X, BACK_BTN_Y, KUBEJS_BTN_W, BACK_BTN_H)) { kubejsFilterHeld = true; return true; }
		int newBtnX = this.width - NEW_BTN_W - 6;
		if (button == 0 && isMouseOver(mouseX, mouseY, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H)) { newGroupButtonHeld = true; return true; }
		if (super.mouseClicked(event, doubleClick)) return true;
		if (button != 0) return false;

		int sbX = this.width - CARD_PADDING - SCROLLBAR_WIDTH;
		int sbY = HEADER_HEIGHT + CARD_PADDING;
		int sbH = contentHeight();
		if (mouseX >= sbX && mouseX < sbX + SCROLLBAR_WIDTH && mouseY >= sbY && mouseY < sbY + sbH) {
			isDraggingScrollbar = true; sbDragStartMouseY = mouseY;
			int maxPx = maxScrollPixels();
			if (maxPx > 0) {
				int thumbH = Math.max(14, sbH * sbH / (maxPx + sbH));
				int travel = sbH - thumbH;
				int thumbY = sbY + (travel > 0 ? travel * scrollPixelOffset / maxPx : 0);
				if (mouseY < thumbY || mouseY >= thumbY + thumbH)
					scrollPixelOffset = clamp((int)((mouseY - sbY - thumbH / 2.0) * maxPx / Math.max(1, travel)), 0, maxPx);
			}
			sbDragStartPixelOffset = scrollPixelOffset;
			return true;
		}

		if (!isInsideCardViewport(mouseX, mouseY)) return false;
		for (int i = 0; i < filteredCards.size(); i++) {
			ItemCard card = filteredCards.get(i);
			if (!card.isEditable()) continue;
			int[] pos = cardPos(i);
			int x = pos[0], y = pos[1];
			if (y + CARD_HEIGHT < HEADER_HEIGHT || y > this.height - FOOTER_HEIGHT) continue;
			int buttonY = y + BTN_Y_OFF;
			if (isMouseOver(mouseX, mouseY, x + 2, buttonY, 74, BTN_H)) {
				boolean newEnabled = !card.group().enabled();
				GroupRegistry.saveQuietly(card.group().withEnabled(newEnabled));
				updateCardEnabled(card.id(), newEnabled);
				GroupRegistry.notifyJeiStructureOnly();
				return true;
			}
			if (isMouseOver(mouseX, mouseY, x + 80, buttonY, 38, BTN_H)) { Minecraft.getInstance().setScreen(new GroupEditorScreen(this, card.group())); return true; }
			if (isMouseOver(mouseX, mouseY, x + 122, buttonY, 36, BTN_H)) {
				GroupRegistry.deleteQuietly(card.id());
				removeCard(card.id());
				GroupRegistry.notifyJei();
				scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		if (button == 0 && isDraggingScrollbar) {
			int maxPx = maxScrollPixels();
			if (maxPx > 0) {
				int sbH    = contentHeight();
				int thumbH = Math.max(14, sbH * sbH / (maxPx + sbH));
				int travel = sbH - thumbH;
				if (travel > 0) scrollPixelOffset = clamp((int)Math.round(sbDragStartPixelOffset + (mouseY - sbDragStartMouseY) * maxPx / travel), 0, maxPx);
			}
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		if (button == 0 && backButtonHeld) {
			backButtonHeld = false;
			if (isMouseOver(mouseX, mouseY, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H)) Minecraft.getInstance().setScreen(previousScreen);
			return true;
		}
		if (button == 0 && builtinFilterHeld) {
			builtinFilterHeld = false;
			if (isMouseOver(mouseX, mouseY, BUILTIN_BTN_X, BACK_BTN_Y, BUILTIN_BTN_W, BACK_BTN_H)) {
				showBuiltin = !showBuiltin;
				GroupUiState.setShowBuiltin(showBuiltin);
				rebuildFilteredCards();
			}
			return true;
		}
		if (button == 0 && kubejsFilterHeld) {
			kubejsFilterHeld = false;
			if (kubeJsLoaded && isMouseOver(mouseX, mouseY, KUBEJS_BTN_X, BACK_BTN_Y, KUBEJS_BTN_W, BACK_BTN_H)) {
				showKubeJs = !showKubeJs;
				GroupUiState.setShowKubeJs(showKubeJs);
				rebuildFilteredCards();
			}
			return true;
		}
		int newBtnX = this.width - NEW_BTN_W - 6;
		if (button == 0 && newGroupButtonHeld) {
			newGroupButtonHeld = false;
			if (isMouseOver(mouseX, mouseY, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H)) Minecraft.getInstance().setScreen(new GroupEditorScreen(this, null));
			return true;
		}
		if (button == 0) isDraggingScrollbar = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		for (int i = 0; i < filteredCards.size(); i++) {
			int[] pos    = cardPos(i);
			int previewX = pos[0] + 3, previewY = pos[1] + 25;
			if (mouseX >= previewX && mouseX < previewX + PREVIEW_COLS * ITEM_SIZE && mouseY >= previewY && mouseY < previewY + PREVIEW_ROWS * ITEM_SIZE) {
				int maxRow  = Math.max(0, totalRowsForCard(filteredCards.get(i)) - PREVIEW_ROWS);
				int current = previewScrollOffsets.getOrDefault(filteredCards.get(i).id(), 0);
				int next    = clamp(current - (int)Math.signum(deltaY), 0, maxRow);
				if (next != current) previewScrollOffsets.put(filteredCards.get(i).id(), next);
				return true;
			}
		}
		if (isInsideCardViewport(mouseX, mouseY)) {
			scrollPixelOffset = clamp(scrollPixelOffset + (int)(deltaY * -20), 0, maxScrollPixels());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
	}

	@Override public void onClose() { Minecraft.getInstance().setScreen(previousScreen); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return true; }

	public void onGroupSaved() { rebuildCards(); scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels()); }

	private int contentHeight()    { return this.height - HEADER_HEIGHT - FOOTER_HEIGHT - CARD_PADDING; }
	private int totalCardRows()    { return filteredCards.isEmpty() ? 0 : (filteredCards.size() + cols - 1) / cols; }
	private int maxScrollPixels()  { return Math.max(0, totalCardRows() * (CARD_HEIGHT + CARD_PADDING) - contentHeight()); }
	private int totalRowsForCard(ItemCard card) { int c = card.previewEntries().size(); return c == 0 ? 0 : Math.max(1, (c + PREVIEW_COLS - 1) / PREVIEW_COLS); }

	private int[] cardPos(int index) {
		int usedWidth = cols * CARD_WIDTH + Math.max(0, cols - 1) * CARD_PADDING;
		int left = (this.width - SCROLLBAR_WIDTH - CARD_PADDING - usedWidth) / 2;
		return new int[]{ left + (index % cols) * (CARD_WIDTH + CARD_PADDING), HEADER_HEIGHT + CARD_PADDING + (index / cols) * (CARD_HEIGHT + CARD_PADDING) - scrollPixelOffset };
	}

	private boolean isInsideCardViewport(double mouseX, double mouseY) {
		return mouseX >= CARD_PADDING && mouseX < this.width - CARD_PADDING && mouseY >= HEADER_HEIGHT && mouseY < this.height - FOOTER_HEIGHT;
	}

	private static boolean isMouseOver(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
	private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

	private void renderScrollbar(GuiGraphicsExtractor g) {
		int x = this.width - CARD_PADDING - SCROLLBAR_WIDTH, y = HEADER_HEIGHT + CARD_PADDING, height = contentHeight();
		g.fill(x, y, x + SCROLLBAR_WIDTH, y + height, 0x18667799);
		int maxPx = maxScrollPixels();
		if (maxPx <= 0) { g.fill(x, y, x + SCROLLBAR_WIDTH, y + height, 0x22334455); return; }
		int thumbHeight = Math.max(14, height * height / (maxPx + height));
		int thumbY = y + (height - thumbHeight) * scrollPixelOffset / maxPx;
		g.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0x6699AABB);
	}
}
