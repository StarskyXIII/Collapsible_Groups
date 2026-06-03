package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.compat.jei.GroupUiState;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.editor.GroupEditorScreen;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.GroupSource;
import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.compat.jei.preview.PreviewGridLayout;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupThemeResolver;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OreGroupManagerScreen extends Screen implements GroupManagerParent {
	private static final int CARD_WIDTH = 196;
	private static final int CARD_HEIGHT = 112;
	private static final int CARD_PADDING = 6;
	private static final int ACTION_RAIL_WIDTH = 42;
	private static final int ACTION_RAIL_GAP = 4;
	private static final int ACTION_BUTTON_HEIGHT = 24;
	private static final int HEADER_PREVIEW_SIZE = 22;
	private static final int PREVIEW_COLS = 6;
	private static final int PREVIEW_ROWS = 3;
	private static final int ITEM_SIZE = 18;
	private static final int HEADER_HEIGHT = 32;
	private static final int FOOTER_HEIGHT = 28;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int CACHE_FALLBACK_SAMPLE_LIMIT = 8;

	private static final int BACK_BTN_X = 6;
	private static final int BACK_BTN_Y = 6;
	private static final int BACK_BTN_W = 50;
	private static final int BACK_BTN_H = 20;
	private static final int BUILTIN_BTN_X = 62;
	private static final int BUILTIN_BTN_W = 72;
	private static final int KUBEJS_BTN_X = 140;
	private static final int KUBEJS_BTN_W = 65;
	private static final int NEW_BTN_W = 110;
	private static final int NEW_BTN_H = 20;

	private final Screen previousScreen;
	private final boolean kubeJsLoaded;
	private final Map<String, Integer> previewScrollOffsets = new HashMap<>();
	private List<GroupManagerCard> allCards = new ArrayList<>();
	private List<GroupManagerCard> filteredCards = new ArrayList<>();
	private int cols = 1;
	private int scrollPixelOffset = 0;

	private boolean showBuiltin = GroupUiState.showBuiltin();
	private boolean showKubeJs = GroupUiState.showKubeJs();
	private boolean backButtonHeld = false;
	private boolean builtinFilterHeld = false;
	private boolean kubejsFilterHeld = false;
	private boolean newGroupButtonHeld = false;
	private boolean isDraggingScrollbar = false;
	private double sbDragStartMouseY;
	private int sbDragStartPixelOffset;
	private Component pendingTooltip;

	public OreGroupManagerScreen(Screen previousScreen) {
		super(Component.translatable(ModTranslationKeys.SCREEN_TITLE));
		this.previousScreen = previousScreen;
		this.kubeJsLoaded = Services.PLATFORM.isModLoaded("kubejs");
	}

	@Override
	protected void init() {
		rebuildCards();
		calcLayout();
		clearWidgets();
	}

	private void rebuildCards() {
		long traceStart = PerformanceTrace.begin();
		CacheTraceStats cacheStats = new CacheTraceStats();
		int totalItems = 0;
		int totalFluids = 0;
		int totalGeneric = 0;
		List<GroupManagerCard> cards = new ArrayList<>();

		for (GroupDefinition group : GroupRegistry.getAllIncludingKubeJs()) {
			GroupRegistry.FullMatchLookup<ItemStack> itemLookup = GroupRegistry.getFullMatchItemsLookup(group);
			GroupRegistry.FullMatchLookup<Object> fluidLookup = GroupRegistry.getFullMatchFluidsLookup(group);
			GroupRegistry.FullMatchLookup<GenericIngredientRef> genericLookup =
				GroupRegistry.getFullMatchGenericIngredientsLookup(group);
			cacheStats.record("item", group.id(), itemLookup);
			cacheStats.record("fluid", group.id(), fluidLookup);
			cacheStats.record("generic", group.id(), genericLookup);

			List<ItemStack> items = itemLookup.values();
			List<Object> fluids = fluidLookup.values();
			List<GenericIngredientRef> generic = genericLookup.values();
			totalItems += items.size();
			totalFluids += fluids.size();
			totalGeneric += generic.size();
			cards.add(GroupManagerCard.create(
				group,
				items,
				fluids,
				generic,
				GroupPreviewEntry.combine(items, fluids, generic)
			));
		}

		allCards = cards;
		previewScrollOffsets.keySet().retainAll(
			allCards.stream().map(GroupManagerCard::id).collect(Collectors.toSet()));
		rebuildFilteredCards();
		PerformanceTrace.log("OreGroupManagerScreen.rebuildCards.cache", cacheStats.summary());
		PerformanceTrace.logIfSlow("OreGroupManagerScreen.rebuildCards", traceStart, 20,
			"groups=" + allCards.size()
				+ " totalItems=" + totalItems
				+ " totalFluids=" + totalFluids
				+ " totalGeneric=" + totalGeneric);
	}

	private void rebuildFilteredCards() {
		filteredCards = allCards.stream().filter(card -> {
			if (card.source() == GroupSource.BUILTIN && !showBuiltin) return false;
			if (card.source() == GroupSource.KUBEJS && !showKubeJs) return false;
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
			GroupManagerCard card = allCards.get(i);
			if (card.id().equals(id)) {
				allCards.set(i, card.withGroup(card.group().withEnabled(enabled)));
				rebuildFilteredCards();
				return;
			}
		}
	}

	private void removeCard(String id) {
		allCards.removeIf(card -> card.id().equals(id));
		previewScrollOffsets.remove(id);
		rebuildFilteredCards();
	}

	private void openEditor(GroupDefinition group) {
		Minecraft.getInstance().setScreen(new GroupEditorScreen(this, group));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
		renderBackground(g, mouseX, mouseY, partialTicks);
		pendingTooltip = null;

		int vpTop = HEADER_HEIGHT;
		int vpBottom = this.height - FOOTER_HEIGHT;
		g.fill(0, 0, this.width, vpTop, 0xCC0E0E1A);
		g.fill(0, vpTop, this.width, vpTop + 1, 0x33667799);
		g.fill(0, vpBottom, this.width, this.height, 0xAA0E0E1A);
		g.fill(0, vpBottom, this.width, vpBottom + 1, 0x33667799);

		g.enableScissor(0, vpTop, this.width, vpBottom);
		for (int i = 0; i < filteredCards.size(); i++) {
			renderCard(g, i, mouseX, mouseY);
		}
		g.disableScissor();

		renderScrollbar(g);
		renderHeaderButtons(g, mouseX, mouseY);

		g.drawCenteredString(font, this.title, this.width / 2, 8, 0xFFFFFF);
		Component countText = filteredCards.size() == allCards.size()
			? Component.translatable(ModTranslationKeys.MANAGER_COUNT_ALL, allCards.size())
			: Component.translatable(ModTranslationKeys.MANAGER_COUNT_FILTERED, filteredCards.size(), allCards.size());
		g.drawCenteredString(font, countText, this.width / 2, 20, 0x8899AABB);
		g.drawString(font, Component.translatable(ModTranslationKeys.MANAGER_FOOTER_HINT),
			6, vpBottom + (FOOTER_HEIGHT - font.lineHeight) / 2 + 1, 0x8899AABB, false);

		for (var child : this.children()) {
			if (child instanceof Renderable renderable) {
				renderable.render(g, mouseX, mouseY, partialTicks);
			}
		}
		if (pendingTooltip != null) {
			g.renderTooltip(font, pendingTooltip, mouseX, mouseY);
		}
	}

	private void renderHeaderButtons(GuiGraphics g, int mouseX, int mouseY) {
		boolean backHover = isMouseOver(mouseX, mouseY, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H);
		renderButton(g, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H,
			Component.translatable(ModTranslationKeys.MANAGER_BTN_BACK).getString(), true, backHover || backButtonHeld);

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
		renderButton(g, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H,
			Component.translatable(ModTranslationKeys.MANAGER_BTN_NEW_GROUP).getString(), true, newHover || newGroupButtonHeld);
	}

	private void renderCard(GuiGraphics g, int index, int mouseX, int mouseY) {
		GroupManagerCard card = filteredCards.get(index);
		int[] pos = cardPos(index);
		int x = pos[0];
		int y = pos[1];
		if (y + CARD_HEIGHT < HEADER_HEIGHT || y > this.height - FOOTER_HEIGHT) return;

		int actionX = actionRailX(x);
		int bodyRight = actionX - ACTION_RAIL_GAP;
		int borderColor = borderColor(card);
		g.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, 0x55101020);
		drawOutline(g, x, y, CARD_WIDTH, CARD_HEIGHT, borderColor);

		boolean bodyHover = isMouseOver(mouseX, mouseY, x, y, bodyRight - x, CARD_HEIGHT);
		if (bodyHover) {
			g.fill(x + 1, y + 1, bodyRight, y + CARD_HEIGHT - 1, 0x14667799);
		}

		renderHeaderPreview(g, card, x + 6, y + 6, bodyRight, bodyHover);
		renderCardPreview(g, card, x + 6, y + 38, previewScrollOffsets.getOrDefault(card.id(), 0));
		renderActionRail(g, card, x, y, mouseX, mouseY);
	}

	private void renderHeaderPreview(GuiGraphics g, GroupManagerCard card, int x, int y, int bodyRight, boolean hovered) {
		int iconX = x;
		int iconY = y;
		int headerColor = GroupThemeResolver.collapsedHeaderBackgroundColor(card.id());
		int borderColor = borderColor(card);
		g.fill(iconX, iconY, iconX + HEADER_PREVIEW_SIZE, iconY + HEADER_PREVIEW_SIZE, headerColor);
		drawOutline(g, iconX, iconY, HEADER_PREVIEW_SIZE, HEADER_PREVIEW_SIZE, borderColor);
		renderStackedPreviewIcons(g, card.previewEntries(), iconX, iconY);

		int textX = iconX + HEADER_PREVIEW_SIZE + 6;
		int maxTextWidth = Math.max(0, bodyRight - textX - 2);
		renderScrollingText(g, localizedDisplayName(card).getString(), textX, y + 2,
			maxTextWidth, GroupThemeResolver.groupNameColor(card.id()), hovered);
		g.drawString(font, countLabel(card), textX, y + 14, 0x7799AABB, false);
	}

	private void renderStackedPreviewIcons(GuiGraphics g, List<GroupPreviewEntry> entries, int x, int y) {
		if (entries.isEmpty()) return;
		g.pose().pushPose();
		g.pose().translate(0, 0, 120);
		if (entries.size() > 1) {
			entries.get(1).render(g, x + 4, y + 2);
			g.pose().translate(0, 0, 8);
			entries.get(0).render(g, x + 2, y + 4);
		} else {
			entries.get(0).render(g, x + 3, y + 3);
		}
		g.pose().popPose();
	}

	private void renderCardPreview(GuiGraphics g, GroupManagerCard card, int previewX, int previewY, int rowOffset) {
		renderPreviewEntries(g, card.previewEntries(), previewX, previewY, rowOffset);
		int previewTotalRows = totalRowsForCard(card);
		if (previewTotalRows <= PREVIEW_ROWS) return;

		int sbX = previewX + PREVIEW_COLS * ITEM_SIZE + 2;
		int sbH = PREVIEW_ROWS * ITEM_SIZE;
		int maxRow = previewTotalRows - PREVIEW_ROWS;
		int thumbH = Math.max(6, sbH * PREVIEW_ROWS / previewTotalRows);
		int thumbY = previewY + (maxRow > 0 ? (sbH - thumbH) * rowOffset / maxRow : 0);
		g.fill(sbX, previewY, sbX + 3, previewY + sbH, 0x18667799);
		g.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0x6699AABB);
	}

	private void renderPreviewEntries(GuiGraphics g, List<GroupPreviewEntry> entries, int previewX, int previewY, int rowOffset) {
		PreviewGridLayout layout = PreviewGridLayout.fixedColumns(entries.size(), PREVIEW_COLS, PREVIEW_ROWS, rowOffset);
		layout.forEachCell((entryIndex, column, row) ->
			entries.get(entryIndex).render(g, previewX + column * ITEM_SIZE, previewY + row * ITEM_SIZE));
		if (!layout.hasOverflow()) return;

		int lastX = previewX + layout.overflowColumn() * ITEM_SIZE;
		int lastY = previewY + layout.overflowRow() * ITEM_SIZE;
		String more = "+" + layout.overflowCount();
		g.pose().pushPose();
		g.pose().translate(0, 0, 200);
		g.fill(lastX, lastY, lastX + ITEM_SIZE, lastY + ITEM_SIZE, 0x88000000);
		g.drawString(font, more, lastX + (ITEM_SIZE - font.width(more)) / 2,
			lastY + (ITEM_SIZE - 8) / 2, 0xFFFFFF, false);
		g.pose().popPose();
	}

	private void renderActionRail(GuiGraphics g, GroupManagerCard card, int cardX, int cardY, int mouseX, int mouseY) {
		int x = actionRailX(cardX);
		int w = ACTION_RAIL_WIDTH;
		int switchY = cardY + 8;
		int editY = cardY + 43;
		int deleteY = cardY + 78;
		boolean editable = card.editable();

		boolean switchHover = isMouseOver(mouseX, mouseY, x, switchY, w, ACTION_BUTTON_HEIGHT);
		boolean editHover = isMouseOver(mouseX, mouseY, x, editY, w, ACTION_BUTTON_HEIGHT);
		boolean deleteHover = isMouseOver(mouseX, mouseY, x, deleteY, w, ACTION_BUTTON_HEIGHT);
		String switchLabel = Component.translatable(card.group().enabled()
			? ModTranslationKeys.CONFIG_VAL_ON
			: ModTranslationKeys.CONFIG_VAL_OFF).getString();
		String middleLabel = Component.translatable(editable
			? ModTranslationKeys.MANAGER_BTN_EDIT
			: ModTranslationKeys.MANAGER_BTN_COPY).getString();

		renderButton(g, x, switchY, w, ACTION_BUTTON_HEIGHT, switchLabel, editable, switchHover);
		renderButton(g, x, editY, w, ACTION_BUTTON_HEIGHT, middleLabel, editable, editHover);
		renderButton(g, x, deleteY, w, ACTION_BUTTON_HEIGHT,
			Component.translatable(ModTranslationKeys.MANAGER_BTN_DELETE).getString(), editable, deleteHover);

		if (!editable) {
			if (switchHover) pendingTooltip = Component.translatable(ModTranslationKeys.MANAGER_TOOLTIP_SWITCH_OVERRIDE_REQUIRED);
			if (editHover) pendingTooltip = Component.translatable(ModTranslationKeys.MANAGER_TOOLTIP_COPY_DEFERRED);
			if (deleteHover) pendingTooltip = Component.translatable(ModTranslationKeys.MANAGER_TOOLTIP_DELETE_READONLY);
		}
	}

	private Component localizedDisplayName(GroupManagerCard card) {
		String resolved = card.group().name();
		String name = resolved.isEmpty() ? card.displayName() : resolved;
		return switch (card.source()) {
			case BUILTIN -> Component.translatable(ModTranslationKeys.MANAGER_PREFIX_BUILTIN, name);
			case KUBEJS -> Component.translatable(ModTranslationKeys.MANAGER_PREFIX_KUBEJS, name);
			case USER -> Component.literal(name);
		};
	}

	private Component countLabel(GroupManagerCard card) {
		net.minecraft.network.chat.MutableComponent result = null;
		if (card.itemCount() > 0) {
			result = Component.translatable(ModTranslationKeys.COUNT_ITEMS, card.itemCount());
		}
		if (card.fluidCount() > 0) {
			net.minecraft.network.chat.MutableComponent part =
				Component.translatable(ModTranslationKeys.COUNT_FLUIDS, card.fluidCount());
			result = result == null ? part : result.append(", ").append(part);
		}
		if (card.genericCount() > 0) {
			net.minecraft.network.chat.MutableComponent part =
				Component.translatable(ModTranslationKeys.COUNT_ENTRIES, card.genericCount());
			result = result == null ? part : result.append(", ").append(part);
		}
		return result != null ? result : Component.empty();
	}

	private void renderButton(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, boolean hovered) {
		int bg = !active ? 0x550E0E1A : (hovered ? 0xCC1A1A2E : 0x880E0E1A);
		int border = !active ? 0x33445566 : (hovered ? 0x8899AABB : 0x44667799);
		int text = !active ? 0x66778899 : (hovered ? 0xFFFFFFFF : 0xCC99AABB);
		g.fill(x, y, x + w, y + h, bg);
		drawOutline(g, x, y, w, h, border);
		String clipped = font.plainSubstrByWidth(label, Math.max(0, w - 6));
		g.drawString(font, clipped, x + (w - font.width(clipped)) / 2, y + (h - 8) / 2, text, false);
	}

	private void renderFilterButton(GuiGraphics g, int x, int y, int w, int h,
									String label, boolean active, boolean hovered, int accentBorder) {
		int bg = hovered ? 0xCC1A1A2E : 0x880E0E1A;
		int border = hovered ? 0x8899AABB : (active ? accentBorder : 0x44334444);
		int text = hovered ? 0xFFFFFFFF : (active ? 0xCC99AABB : 0x55667788);
		g.fill(x, y, x + w, y + h, bg);
		drawOutline(g, x, y, w, h, border);
		g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, text, false);
	}

	private void drawOutline(GuiGraphics g, int x, int y, int width, int height, int color) {
		int right = x + width;
		int bottom = y + height;
		g.fill(x, y, right, y + 1, color);
		g.fill(x, bottom - 1, right, bottom, color);
		g.fill(x, y + 1, x + 1, bottom - 1, color);
		g.fill(right - 1, y + 1, right, bottom - 1, color);
	}

	private void renderScrollingText(GuiGraphics g, String text, int x, int y, int maxWidth, int color, boolean hovered) {
		int safeWidth = Math.max(0, maxWidth);
		int textWidth = font.width(text);
		if (textWidth <= safeWidth) {
			g.drawString(font, text, x, y, color, true);
			return;
		}
		if (!hovered || safeWidth <= font.width("...")) {
			String truncated = font.plainSubstrByWidth(text, Math.max(0, safeWidth - font.width("..."))) + "...";
			g.drawString(font, truncated, x, y, color, true);
			return;
		}
		g.enableScissor(x, y - 1, x + safeWidth, y + font.lineHeight + 1);
		int gap = 20;
		int totalCycle = textWidth + gap;
		float scrollOffset = (System.currentTimeMillis() % (totalCycle * 30L)) / 30.0f;
		int drawX1 = (int)(x - scrollOffset);
		int drawX2 = drawX1 + totalCycle;
		g.drawString(font, text, drawX1, y, color, true);
		g.drawString(font, text, drawX2, y, color, true);
		g.disableScissor();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isMouseOver(mouseX, mouseY, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H)) {
			backButtonHeld = true;
			return true;
		}
		if (button == 0 && isMouseOver(mouseX, mouseY, BUILTIN_BTN_X, BACK_BTN_Y, BUILTIN_BTN_W, BACK_BTN_H)) {
			builtinFilterHeld = true;
			return true;
		}
		if (kubeJsLoaded && button == 0 && isMouseOver(mouseX, mouseY, KUBEJS_BTN_X, BACK_BTN_Y, KUBEJS_BTN_W, BACK_BTN_H)) {
			kubejsFilterHeld = true;
			return true;
		}
		int newBtnX = this.width - NEW_BTN_W - 6;
		if (button == 0 && isMouseOver(mouseX, mouseY, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H)) {
			newGroupButtonHeld = true;
			return true;
		}
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		if (button != 0) return false;

		if (handleScrollbarClick(mouseX, mouseY)) return true;
		if (!isInsideCardViewport(mouseX, mouseY)) return false;

		for (int i = 0; i < filteredCards.size(); i++) {
			GroupManagerCard card = filteredCards.get(i);
			int[] pos = cardPos(i);
			int x = pos[0];
			int y = pos[1];
			if (y + CARD_HEIGHT < HEADER_HEIGHT || y > this.height - FOOTER_HEIGHT) continue;
			int actionX = actionRailX(x);
			if (mouseX < actionX || mouseX >= actionX + ACTION_RAIL_WIDTH) continue;

			boolean switchClick = isMouseOver(mouseX, mouseY, actionX, y + 8, ACTION_RAIL_WIDTH, ACTION_BUTTON_HEIGHT);
			boolean editClick = isMouseOver(mouseX, mouseY, actionX, y + 43, ACTION_RAIL_WIDTH, ACTION_BUTTON_HEIGHT);
			boolean deleteClick = isMouseOver(mouseX, mouseY, actionX, y + 78, ACTION_RAIL_WIDTH, ACTION_BUTTON_HEIGHT);
			if (!switchClick && !editClick && !deleteClick) continue;
			if (!card.editable()) return true;

			if (switchClick) {
				boolean newEnabled = !card.group().enabled();
				GroupRegistry.saveQuietly(card.group().withEnabled(newEnabled));
				updateCardEnabled(card.id(), newEnabled);
				GroupRegistry.notifyJeiStructureOnly();
				return true;
			}
			if (editClick) {
				openEditor(card.group());
				return true;
			}
			if (deleteClick) {
				String deletedId = card.id();
				GroupRegistry.deleteQuietly(deletedId);
				removeCard(deletedId);
				GroupRegistry.notifyJei();
				scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
				return true;
			}
		}
		return false;
	}

	private boolean handleScrollbarClick(double mouseX, double mouseY) {
		int sbX = this.width - CARD_PADDING - SCROLLBAR_WIDTH;
		int sbY = HEADER_HEIGHT + CARD_PADDING;
		int sbH = contentHeight();
		if (mouseX < sbX || mouseX >= sbX + SCROLLBAR_WIDTH || mouseY < sbY || mouseY >= sbY + sbH) return false;
		isDraggingScrollbar = true;
		sbDragStartMouseY = mouseY;
		int maxPx = maxScrollPixels();
		if (maxPx > 0) {
			int thumbH = Math.max(14, sbH * sbH / (maxPx + sbH));
			int travel = sbH - thumbH;
			int thumbY = sbY + (travel > 0 ? travel * scrollPixelOffset / maxPx : 0);
			if (mouseY < thumbY || mouseY >= thumbY + thumbH) {
				scrollPixelOffset = clamp((int)((mouseY - sbY - thumbH / 2.0) * maxPx / Math.max(1, travel)), 0, maxPx);
			}
		}
		sbDragStartPixelOffset = scrollPixelOffset;
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && isDraggingScrollbar) {
			int maxPx = maxScrollPixels();
			if (maxPx > 0) {
				int sbH = contentHeight();
				int thumbH = Math.max(14, sbH * sbH / (maxPx + sbH));
				int travel = sbH - thumbH;
				if (travel > 0) {
					scrollPixelOffset = clamp((int)Math.round(sbDragStartPixelOffset + (mouseY - sbDragStartMouseY) * maxPx / travel), 0, maxPx);
				}
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && backButtonHeld) {
			backButtonHeld = false;
			if (isMouseOver(mouseX, mouseY, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H)) {
				Minecraft.getInstance().setScreen(previousScreen);
			}
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
			if (isMouseOver(mouseX, mouseY, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H)) {
				openEditor(null);
			}
			return true;
		}
		if (button == 0) isDraggingScrollbar = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		if (scrollHoveredPreview(mouseX, mouseY, deltaY)) return true;
		if (isInsideCardViewport(mouseX, mouseY)) {
			scrollPixelOffset = clamp(scrollPixelOffset + (int)(deltaY * -20), 0, maxScrollPixels());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
	}

	private boolean scrollHoveredPreview(double mouseX, double mouseY, double deltaY) {
		for (int i = 0; i < filteredCards.size(); i++) {
			GroupManagerCard card = filteredCards.get(i);
			int[] pos = cardPos(i);
			int previewX = pos[0] + 6;
			int previewY = pos[1] + 38;
			if (mouseX >= previewX && mouseX < previewX + PREVIEW_COLS * ITEM_SIZE
					&& mouseY >= previewY && mouseY < previewY + PREVIEW_ROWS * ITEM_SIZE) {
				int maxRow = Math.max(0, totalRowsForCard(card) - PREVIEW_ROWS);
				int current = previewScrollOffsets.getOrDefault(card.id(), 0);
				int next = clamp(current - (int)Math.signum(deltaY), 0, maxRow);
				if (next != current) previewScrollOffsets.put(card.id(), next);
				return true;
			}
		}
		return false;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(previousScreen);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onGroupSaved() {
		rebuildCards();
		scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
	}

	@Override
	public Screen asScreen() {
		return this;
	}

	private int contentHeight() {
		return this.height - HEADER_HEIGHT - FOOTER_HEIGHT - CARD_PADDING;
	}

	private int totalCardRows() {
		return filteredCards.isEmpty() ? 0 : (filteredCards.size() + cols - 1) / cols;
	}

	private int maxScrollPixels() {
		return Math.max(0, totalCardRows() * (CARD_HEIGHT + CARD_PADDING) - contentHeight());
	}

	private int totalRowsForCard(GroupManagerCard card) {
		return PreviewGridLayout.totalRows(card.previewEntries().size(), PREVIEW_COLS);
	}

	private int[] cardPos(int index) {
		int usedWidth = cols * CARD_WIDTH + Math.max(0, cols - 1) * CARD_PADDING;
		int left = (this.width - SCROLLBAR_WIDTH - CARD_PADDING - usedWidth) / 2;
		return new int[] {
			left + (index % cols) * (CARD_WIDTH + CARD_PADDING),
			HEADER_HEIGHT + CARD_PADDING + (index / cols) * (CARD_HEIGHT + CARD_PADDING) - scrollPixelOffset
		};
	}

	private int actionRailX(int cardX) {
		return cardX + CARD_WIDTH - ACTION_RAIL_WIDTH - 4;
	}

	private int borderColor(GroupManagerCard card) {
		return switch (card.source()) {
			case USER -> card.group().enabled() ? 0x55339966 : 0x55993333;
			case BUILTIN -> 0x55665533;
			case KUBEJS -> 0x55664488;
		};
	}

	private boolean isInsideCardViewport(double mouseX, double mouseY) {
		return mouseX >= CARD_PADDING
			&& mouseX < this.width - CARD_PADDING
			&& mouseY >= HEADER_HEIGHT
			&& mouseY < this.height - FOOTER_HEIGHT;
	}

	private void renderScrollbar(GuiGraphics g) {
		int x = this.width - CARD_PADDING - SCROLLBAR_WIDTH;
		int y = HEADER_HEIGHT + CARD_PADDING;
		int height = contentHeight();
		g.fill(x, y, x + SCROLLBAR_WIDTH, y + height, 0x18667799);
		int maxPx = maxScrollPixels();
		if (maxPx <= 0) {
			g.fill(x, y, x + SCROLLBAR_WIDTH, y + height, 0x22334455);
			return;
		}
		int thumbHeight = Math.max(14, height * height / (maxPx + height));
		int thumbY = y + (height - thumbHeight) * scrollPixelOffset / maxPx;
		g.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0x6699AABB);
	}

	private static boolean isMouseOver(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static final class CacheTraceStats {
		private int itemHits;
		private int itemMisses;
		private int fluidHits;
		private int fluidMisses;
		private int genericHits;
		private int genericMisses;
		private final Map<String, Integer> fallbackReasons = new HashMap<>();
		private final List<String> fallbackSamples = new ArrayList<>();

		void record(String kind, String groupId, GroupRegistry.FullMatchLookup<?> lookup) {
			if (lookup.cacheHit()) {
				switch (kind) {
					case "item" -> itemHits++;
					case "fluid" -> fluidHits++;
					case "generic" -> genericHits++;
					default -> { }
				}
				return;
			}
			switch (kind) {
				case "item" -> itemMisses++;
				case "fluid" -> fluidMisses++;
				case "generic" -> genericMisses++;
				default -> { }
			}
			String reason = lookup.fallbackReason() == null ? "unknown" : lookup.fallbackReason();
			fallbackReasons.merge(kind + ":" + reason, 1, Integer::sum);
			if (fallbackSamples.size() < CACHE_FALLBACK_SAMPLE_LIMIT) {
				fallbackSamples.add(kind + ":" + groupId + "(" + reason + ")");
			}
		}

		String summary() {
			return "itemHit=" + itemHits
				+ " itemMiss=" + itemMisses
				+ " fluidHit=" + fluidHits
				+ " fluidMiss=" + fluidMisses
				+ " genericHit=" + genericHits
				+ " genericMiss=" + genericMisses
				+ " fallbackReasons=" + fallbackReasons
				+ " fallbackSamples=" + fallbackSamples;
		}
	}
}
