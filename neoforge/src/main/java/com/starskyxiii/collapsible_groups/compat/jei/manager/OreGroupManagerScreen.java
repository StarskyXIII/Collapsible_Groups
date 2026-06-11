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
import com.starskyxiii.collapsible_groups.compat.jei.ui.OreUiPalette;
import com.starskyxiii.collapsible_groups.compat.jei.ui.OreUiRenderer;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OreGroupManagerScreen extends Screen implements GroupManagerParent {
	private static final int CARD_WIDTH = 196;
	private static final int CARD_HEIGHT = 116;
	private static final int CARD_PADDING = 6;
	private static final int CARD_TITLE_Y = 6;
	private static final int CARD_PREVIEW_Y = 32;
	private static final int CARD_FOOTER_Y = 88;
	private static final int CARD_CONTROL_Z = 250;
	private static final int ACTION_BUTTON_WIDTH = 24;
	private static final int ACTION_BUTTON_HEIGHT = 20;
	private static final int ACTION_BUTTON_GAP = 4;
	private static final int SWITCH_WIDTH = 24;
	private static final int SWITCH_HEIGHT = 24;
	private static final int HEADER_PREVIEW_SIZE = 22;
	private static final int PREVIEW_COLS = 10;
	private static final int PREVIEW_ROWS = 3;
	private static final int PREVIEW_CELL_PITCH = 17;
	private static final int PREVIEW_ICON_INSET = 1;
	private static final int PREVIEW_GRID_WIDTH = PREVIEW_COLS * PREVIEW_CELL_PITCH + 1;
	private static final int PREVIEW_GRID_HEIGHT = PREVIEW_ROWS * PREVIEW_CELL_PITCH + 1;
	private static final int HEADER_HEIGHT = 56;
	private static final int FOOTER_HEIGHT = 28;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int CACHE_FALLBACK_SAMPLE_LIMIT = 8;

	private static final int BACK_BTN_X = 6;
	private static final int BACK_BTN_Y = 5;
	private static final int BACK_BTN_W = 50;
	private static final int BACK_BTN_H = 20;
	private static final int HEADER_TITLE_X = 62;
	private static final int NEW_BTN_W = 110;
	private static final int NEW_BTN_H = 20;
	private static final int SEGMENT_X = 6;
	private static final int SEGMENT_Y = 31;
	private static final int SEGMENT_HEIGHT = 18;
	private static final int SEGMENT_MIN_WIDTH = 40;
	private static final int SEGMENT_TEXT_PADDING = 16;
	private static final int SEGMENT_OVERLAP = 1;
	private static final int MINI_SCROLLBAR_GAP = 4;
	private static final int MINI_SCROLLBAR_WIDTH = 5;

	private final Screen previousScreen;
	private final boolean kubeJsLoaded;
	private final Map<String, Integer> previewScrollOffsets = new HashMap<>();
	private List<GroupManagerCard> allCards = new ArrayList<>();
	private List<GroupManagerCard> filteredCards = new ArrayList<>();
	private int cols = 1;
	private int scrollPixelOffset = 0;

	private GroupUiState.ManagerSourceFilter sourceFilter = GroupUiState.managerSourceFilter();
	private boolean backButtonHeld = false;
	private int heldSegmentIndex = -1;
	private boolean newGroupButtonHeld = false;
	private boolean isDraggingScrollbar = false;
	private String heldSwitchGroupId = null;
	private String suppressedSwitchHoverGroupId = null;
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
		if (!kubeJsLoaded && sourceFilter == GroupUiState.ManagerSourceFilter.KUBEJS) {
			sourceFilter = GroupUiState.ManagerSourceFilter.ALL;
		}
		rebuildCards();
		calcLayout();
		clearWidgets();
	}

	private void rebuildCards() {
		suppressedSwitchHoverGroupId = null;
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
		filteredCards = allCards.stream().filter(this::matchesSourceFilter).toList();
		scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
	}

	private boolean matchesSourceFilter(GroupManagerCard card) {
		return switch (sourceFilter) {
			case ALL -> true;
			case USER -> card.source() == GroupSource.USER;
			case BUILTIN -> card.source() == GroupSource.BUILTIN;
			case KUBEJS -> card.source() == GroupSource.KUBEJS;
		};
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
		if (id.equals(suppressedSwitchHoverGroupId)) suppressedSwitchHoverGroupId = null;
		rebuildFilteredCards();
	}

	private void openEditor(GroupDefinition group) {
		Minecraft.getInstance().setScreen(new GroupEditorScreen(this, group));
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
		g.fill(0, 0, this.width, this.height, OreUiPalette.SCREEN_SCRIM);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
		renderBackground(g, mouseX, mouseY, partialTicks);
		pendingTooltip = null;

		int vpTop = HEADER_HEIGHT;
		int vpBottom = this.height - FOOTER_HEIGHT;
		OreUiRenderer.drawScreenBars(g, this.width, this.height, HEADER_HEIGHT, FOOTER_HEIGHT);

		g.enableScissor(0, vpTop, this.width, vpBottom);
		for (int i = 0; i < filteredCards.size(); i++) {
			renderCard(g, i, mouseX, mouseY);
		}
		g.disableScissor();

		renderScrollbar(g);
		renderHeaderButtons(g, mouseX, mouseY);

		g.drawString(font, this.title, HEADER_TITLE_X, 7, OreUiPalette.TEXT_PRIMARY, false);
		Component countText = filteredCards.size() == allCards.size()
			? Component.translatable(ModTranslationKeys.MANAGER_COUNT_ALL, allCards.size())
			: Component.translatable(ModTranslationKeys.MANAGER_COUNT_FILTERED, filteredCards.size(), allCards.size());
		g.drawString(font, countText, HEADER_TITLE_X, 18, OreUiPalette.TEXT_MUTED, false);
		g.drawString(font, Component.translatable(ModTranslationKeys.MANAGER_FOOTER_HINT),
			6, vpBottom + OreUiRenderer.centeredTextY(font, 0, FOOTER_HEIGHT), OreUiPalette.TEXT_HINT, false);

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
			Component.translatable(ModTranslationKeys.MANAGER_BTN_BACK).getString(), true, backHover, backButtonHeld && backHover);

		renderSegmentedFilter(g, mouseX, mouseY);

		int newBtnX = this.width - NEW_BTN_W - 6;
		boolean newHover = isMouseOver(mouseX, mouseY, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H);
		renderButton(g, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H,
			Component.translatable(ModTranslationKeys.MANAGER_BTN_NEW_GROUP).getString(), true, newHover, newGroupButtonHeld && newHover);
	}

	private void renderSegmentedFilter(GuiGraphics g, int mouseX, int mouseY) {
		GroupUiState.ManagerSourceFilter[] filters = segmentFilters();
		int hoveredIndex = hoveredSegmentIndex(filters, mouseX, mouseY);
		int selectedIndex = -1;
		for (int i = 0; i < filters.length; i++) {
			if (sourceFilter == filters[i]) {
				selectedIndex = i;
				continue;
			}
			if (i == hoveredIndex) continue;
			renderSegment(g, filters, i, false, false);
		}
		if (selectedIndex >= 0 && selectedIndex != hoveredIndex) {
			renderSegment(g, filters, selectedIndex, true, false);
		}
		if (hoveredIndex >= 0) {
			renderSegment(g, filters, hoveredIndex, hoveredIndex == selectedIndex, true);
		}
	}

	private int hoveredSegmentIndex(GroupUiState.ManagerSourceFilter[] filters, double mouseX, double mouseY) {
		for (int i = 0; i < filters.length; i++) {
			if (isMouseOver(mouseX, mouseY, segmentX(filters, i), SEGMENT_Y, segmentWidth(filters), SEGMENT_HEIGHT)) {
				return i;
			}
		}
		return -1;
	}

	private void renderSegment(GuiGraphics g, GroupUiState.ManagerSourceFilter[] filters, int index,
	                           boolean selected, boolean hovered) {
		int x = segmentX(filters, index);
		int w = segmentWidth(filters);
		boolean pressed = hovered && heldSegmentIndex == index;
		OreUiRenderer.ButtonState state = selected
			? pressed ? OreUiRenderer.ButtonState.SELECTED_PRESSED
			: hovered ? OreUiRenderer.ButtonState.SELECTED_HOVERED : OreUiRenderer.ButtonState.SELECTED
			: pressed ? OreUiRenderer.ButtonState.PRESSED
			: hovered ? OreUiRenderer.ButtonState.HOVERED : OreUiRenderer.ButtonState.NORMAL;
		OreUiRenderer.drawSegment(g, font, x, SEGMENT_Y, w, SEGMENT_HEIGHT, segmentLabel(filters[index]), state);
	}

	private GroupUiState.ManagerSourceFilter[] segmentFilters() {
		return kubeJsLoaded
			? new GroupUiState.ManagerSourceFilter[] {
				GroupUiState.ManagerSourceFilter.ALL,
				GroupUiState.ManagerSourceFilter.USER,
				GroupUiState.ManagerSourceFilter.BUILTIN,
				GroupUiState.ManagerSourceFilter.KUBEJS }
			: new GroupUiState.ManagerSourceFilter[] {
				GroupUiState.ManagerSourceFilter.ALL,
				GroupUiState.ManagerSourceFilter.USER,
				GroupUiState.ManagerSourceFilter.BUILTIN };
	}

	private String segmentLabel(GroupUiState.ManagerSourceFilter filter) {
		return switch (filter) {
			case ALL -> Component.translatable(ModTranslationKeys.MANAGER_FILTER_ALL).getString();
			case USER -> Component.translatable(ModTranslationKeys.MANAGER_FILTER_USER).getString();
			case BUILTIN -> Component.translatable(ModTranslationKeys.MANAGER_BTN_FILTER_BUILTIN).getString();
			case KUBEJS -> Component.translatable(ModTranslationKeys.MANAGER_BTN_FILTER_KUBEJS).getString();
		};
	}

	private int segmentWidth(GroupUiState.ManagerSourceFilter[] filters) {
		int width = SEGMENT_MIN_WIDTH;
		for (GroupUiState.ManagerSourceFilter filter : filters) {
			width = Math.max(width, font.width(segmentLabel(filter)) + SEGMENT_TEXT_PADDING);
		}
		return width;
	}

	private int segmentX(GroupUiState.ManagerSourceFilter[] filters, int index) {
		return SEGMENT_X + index * (segmentWidth(filters) - SEGMENT_OVERLAP);
	}

	private void renderCard(GuiGraphics g, int index, int mouseX, int mouseY) {
		GroupManagerCard card = filteredCards.get(index);
		int[] pos = cardPos(index);
		int x = pos[0];
		int y = pos[1];
		if (y + CARD_HEIGHT < HEADER_HEIGHT || y > this.height - FOOTER_HEIGHT) return;

		boolean cardHover = isMouseOver(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT);
		int outlineColor = cardHover ? OreUiPalette.OUTLINE_HOVER : OreUiPalette.OUTLINE_DARK;
		OreUiRenderer.drawCard(g, x, y, CARD_WIDTH, CARD_HEIGHT, cardHover, outlineColor);

		renderHeaderPreview(g, card, x + 6, y + CARD_TITLE_Y, switchControlX(x) - 4, cardHover);
		renderCardPreview(g, card, x + 6, y + CARD_PREVIEW_Y, previewScrollOffsets.getOrDefault(card.id(), 0));
		renderSourceTab(g, card, x, y);
		if (!card.group().enabled()) {
			renderDisabledOverlay(g, x, y);
		}
		renderCardControls(g, card, x, y, mouseX, mouseY);
	}

	private void renderDisabledOverlay(GuiGraphics g, int x, int y) {
		g.pose().pushPose();
		g.pose().translate(0, 0, 200);
		g.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, OreUiPalette.DISABLED_OVERLAY);
		g.pose().popPose();
	}

	private void renderHeaderPreview(GuiGraphics g, GroupManagerCard card, int x, int y, int textRight, boolean hovered) {
		int headerColor = GroupThemeResolver.collapsedHeaderBackgroundColor(card.id());
		g.fill(x, y, x + HEADER_PREVIEW_SIZE, y + HEADER_PREVIEW_SIZE, headerColor);
		drawOutline(g, x, y, HEADER_PREVIEW_SIZE, HEADER_PREVIEW_SIZE, OreUiPalette.OUTLINE_DARK);
		renderStackedPreviewIcons(g, card.previewEntries(), x, y);

		int textX = x + HEADER_PREVIEW_SIZE + 6;
		int maxTextWidth = Math.max(0, textRight - textX);
		renderScrollingText(g, localizedDisplayName(card).getString(), textX, y + 1,
			maxTextWidth, OreUiPalette.TEXT_PRIMARY, hovered);
		g.drawString(font, countLabel(card), textX, y + 12, OreUiPalette.TEXT_MUTED, false);
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
		OreUiRenderer.drawSlotGrid(g, previewX, previewY, PREVIEW_COLS, PREVIEW_ROWS, PREVIEW_CELL_PITCH);
		renderPreviewEntries(g, card.previewEntries(), previewX, previewY, rowOffset);
		int previewTotalRows = totalRowsForCard(card);
		if (previewTotalRows <= PREVIEW_ROWS) return;

		int sbX = previewX + PREVIEW_GRID_WIDTH + MINI_SCROLLBAR_GAP;
		int sbH = PREVIEW_GRID_HEIGHT;
		OreUiRenderer.drawMiniScrollbar(g, sbX, previewY, sbH, PREVIEW_ROWS, previewTotalRows, rowOffset);
	}

	private void renderPreviewEntries(GuiGraphics g, List<GroupPreviewEntry> entries, int previewX, int previewY, int rowOffset) {
		PreviewGridLayout layout = PreviewGridLayout.fixedColumns(entries.size(), PREVIEW_COLS, PREVIEW_ROWS, rowOffset);
		layout.forEachCell((entryIndex, column, row) ->
			entries.get(entryIndex).render(g,
				previewX + column * PREVIEW_CELL_PITCH + PREVIEW_ICON_INSET,
				previewY + row * PREVIEW_CELL_PITCH + PREVIEW_ICON_INSET));
		if (!layout.hasOverflow()) return;

		int cellInner = PREVIEW_CELL_PITCH - 1;
		int lastX = previewX + layout.overflowColumn() * PREVIEW_CELL_PITCH + PREVIEW_ICON_INSET;
		int lastY = previewY + layout.overflowRow() * PREVIEW_CELL_PITCH + PREVIEW_ICON_INSET;
		String more = "+" + layout.overflowCount();
		g.pose().pushPose();
		g.pose().translate(0, 0, 200);
		g.fill(lastX, lastY, lastX + cellInner, lastY + cellInner, OreUiPalette.DISABLED_OVERLAY);
		g.drawString(font, more, lastX + (cellInner - font.width(more)) / 2,
			lastY + (cellInner - 8) / 2, OreUiPalette.TEXT_PRIMARY, false);
		g.pose().popPose();
	}

	private void renderCardControls(GuiGraphics g, GroupManagerCard card, int cardX, int cardY, int mouseX, int mouseY) {
		int switchX = switchControlX(cardX);
		int switchY = switchControlY(cardY);
		int editX = editButtonX(cardX);
		int deleteX = deleteButtonX(cardX);
		int actionY = cardY + CARD_FOOTER_Y;
		boolean editable = card.editable();

		boolean rawSwitchHover = isMouseOver(mouseX, mouseY, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT);
		boolean switchHover = effectiveSwitchHover(card.id(), rawSwitchHover);
		boolean editHover = isMouseOver(mouseX, mouseY, editX, actionY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
		boolean deleteHover = isMouseOver(mouseX, mouseY, deleteX, actionY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
		boolean switchPressed = editable && switchHover && card.id().equals(heldSwitchGroupId);

		g.pose().pushPose();
		g.pose().translate(0, 0, CARD_CONTROL_Z);
		OreUiRenderer.drawSwitch(g, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT,
			card.group().enabled(), editable, switchHover, switchPressed);
		renderIconButton(g, editX, actionY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT,
			OreUiRenderer.ICON_EDIT, editable, editHover, false);
		renderIconButton(g, deleteX, actionY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT,
			OreUiRenderer.ICON_DELETE, editable, deleteHover, false);
		g.pose().popPose();

		if (switchHover && !editable) pendingTooltip = Component.translatable(ModTranslationKeys.MANAGER_TOOLTIP_SWITCH_READONLY);
		if (editHover) pendingTooltip = editable
			? Component.translatable(ModTranslationKeys.MANAGER_BTN_EDIT)
			: Component.translatable(ModTranslationKeys.MANAGER_TOOLTIP_COPY_DEFERRED);
		if (deleteHover) pendingTooltip = editable
			? Component.translatable(ModTranslationKeys.MANAGER_BTN_DELETE)
			: Component.translatable(ModTranslationKeys.MANAGER_TOOLTIP_DELETE_READONLY);
	}

	private void renderSourceTab(GuiGraphics g, GroupManagerCard card, int cardX, int cardY) {
		String label = switch (card.source()) {
			case BUILTIN -> Component.translatable(ModTranslationKeys.MANAGER_BADGE_BUILTIN).getString();
			case KUBEJS -> Component.translatable(ModTranslationKeys.MANAGER_BADGE_KUBEJS).getString();
			case USER -> null;
		};
		if (label == null) return;
		int tabWidth = font.width(label) + 10;
		int tabHeight = 14;
		int left = cardX + 1;
		int bottom = cardY + CARD_HEIGHT - 1;
		int top = bottom - tabHeight;
		g.fill(left, top, left + tabWidth, bottom, OreUiPalette.SURFACE_DARK);
		g.fill(left, top, left + tabWidth, top + 1, OreUiPalette.OUTLINE_DARK);
		g.fill(left + tabWidth - 1, top, left + tabWidth, bottom, OreUiPalette.OUTLINE_DARK);
		g.drawString(font, label, left + 5,
			OreUiRenderer.centeredTextY(font, top, tabHeight), OreUiPalette.TEXT_MUTED, false);
	}

	private Component localizedDisplayName(GroupManagerCard card) {
		String resolved = card.group().name();
		String name = resolved.isEmpty() ? card.displayName() : resolved;
		return Component.literal(name);
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

	private void renderButton(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, boolean hovered, boolean pressed) {
		OreUiRenderer.ButtonState state = !active
			? OreUiRenderer.ButtonState.DISABLED
			: pressed ? OreUiRenderer.ButtonState.PRESSED
			: hovered ? OreUiRenderer.ButtonState.HOVERED : OreUiRenderer.ButtonState.NORMAL;
		OreUiRenderer.drawButton(g, font, x, y, w, h, label, state);
	}

	private void renderIconButton(GuiGraphics g, int x, int y, int w, int h,
	                              ResourceLocation icon, boolean active, boolean hovered, boolean pressed) {
		OreUiRenderer.ButtonState state = !active
			? OreUiRenderer.ButtonState.DISABLED
			: pressed ? OreUiRenderer.ButtonState.PRESSED
			: hovered ? OreUiRenderer.ButtonState.HOVERED : OreUiRenderer.ButtonState.NORMAL;
		OreUiRenderer.drawToolbarIconButton(g, x, y, w, h, icon, state);
	}

	private void drawOutline(GuiGraphics g, int x, int y, int width, int height, int color) {
		OreUiRenderer.drawOutline(g, x, y, width, height, color);
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
		if (button == 0) {
			heldSwitchGroupId = null;
		}
		if (button == 0 && isMouseOver(mouseX, mouseY, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H)) {
			backButtonHeld = true;
			return true;
		}
		if (button == 0) {
			int segmentIndex = hoveredSegmentIndex(segmentFilters(), mouseX, mouseY);
			if (segmentIndex >= 0) {
				heldSegmentIndex = segmentIndex;
				return true;
			}
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

			boolean switchClick = isMouseOver(mouseX, mouseY, switchControlX(x), switchControlY(y), SWITCH_WIDTH, SWITCH_HEIGHT);
			boolean editClick = isMouseOver(mouseX, mouseY, editButtonX(x), y + CARD_FOOTER_Y, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
			boolean deleteClick = isMouseOver(mouseX, mouseY, deleteButtonX(x), y + CARD_FOOTER_Y, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
			if (!switchClick && !editClick && !deleteClick) continue;
			if (!card.editable()) return true;

			if (switchClick) {
				heldSwitchGroupId = card.id();
				boolean newEnabled = !card.group().enabled();
				GroupRegistry.saveQuietly(card.group().withEnabled(newEnabled));
				updateCardEnabled(card.id(), newEnabled);
				suppressedSwitchHoverGroupId = card.id();
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
		if (button == 0 && heldSwitchGroupId != null && !isHeldSwitchHovered(mouseX, mouseY)) {
			heldSwitchGroupId = null;
		}
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
		if (button == 0) {
			heldSwitchGroupId = null;
		}
		if (button == 0 && backButtonHeld) {
			backButtonHeld = false;
			if (isMouseOver(mouseX, mouseY, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H)) {
				Minecraft.getInstance().setScreen(previousScreen);
			}
			return true;
		}
		if (button == 0 && heldSegmentIndex >= 0) {
			int index = heldSegmentIndex;
			heldSegmentIndex = -1;
			GroupUiState.ManagerSourceFilter[] filters = segmentFilters();
			if (index < filters.length
				&& isMouseOver(mouseX, mouseY, segmentX(filters, index), SEGMENT_Y, segmentWidth(filters), SEGMENT_HEIGHT)
				&& sourceFilter != filters[index]) {
				sourceFilter = filters[index];
				GroupUiState.setManagerSourceFilter(sourceFilter);
				suppressedSwitchHoverGroupId = null;
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
		suppressedSwitchHoverGroupId = null;
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
			int previewY = pos[1] + CARD_PREVIEW_Y;
			int maxRow = Math.max(0, totalRowsForCard(card) - PREVIEW_ROWS);
			if (maxRow <= 0) continue;
			int previewHeight = PREVIEW_GRID_HEIGHT;
			int scrollbarX = previewX + PREVIEW_GRID_WIDTH + MINI_SCROLLBAR_GAP;
			boolean previewHover = isMouseOver(mouseX, mouseY, previewX, previewY,
				PREVIEW_GRID_WIDTH, previewHeight);
			boolean scrollbarHover = isMouseOver(mouseX, mouseY, scrollbarX, previewY, MINI_SCROLLBAR_WIDTH, previewHeight);
			if (!previewHover && !scrollbarHover) continue;

			int current = previewScrollOffsets.getOrDefault(card.id(), 0);
			int next = clamp(current - (int)Math.signum(deltaY), 0, maxRow);
			if (next != current) {
				previewScrollOffsets.put(card.id(), next);
			}
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		heldSwitchGroupId = null;
		suppressedSwitchHoverGroupId = null;
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

	private int switchControlX(int cardX) {
		return cardX + CARD_WIDTH - SWITCH_WIDTH - 6;
	}

	private int switchControlY(int cardY) {
		return cardY + CARD_TITLE_Y - 1;
	}

	private int editButtonX(int cardX) {
		return deleteButtonX(cardX) - ACTION_BUTTON_WIDTH - ACTION_BUTTON_GAP;
	}

	private int deleteButtonX(int cardX) {
		return cardX + CARD_WIDTH - ACTION_BUTTON_WIDTH - 6;
	}

	private boolean effectiveSwitchHover(String groupId, boolean rawHover) {
		if (!rawHover) {
			if (groupId.equals(suppressedSwitchHoverGroupId)) suppressedSwitchHoverGroupId = null;
			return false;
		}
		return !groupId.equals(suppressedSwitchHoverGroupId);
	}

	private boolean isInsideCardViewport(double mouseX, double mouseY) {
		return mouseX >= CARD_PADDING
			&& mouseX < this.width - CARD_PADDING
			&& mouseY >= HEADER_HEIGHT
			&& mouseY < this.height - FOOTER_HEIGHT;
	}

	private boolean isHeldSwitchHovered(double mouseX, double mouseY) {
		if (heldSwitchGroupId == null) return false;
		for (int i = 0; i < filteredCards.size(); i++) {
			GroupManagerCard card = filteredCards.get(i);
			if (!card.id().equals(heldSwitchGroupId)) continue;
			int[] pos = cardPos(i);
			return isMouseOver(mouseX, mouseY, switchControlX(pos[0]), switchControlY(pos[1]), SWITCH_WIDTH, SWITCH_HEIGHT);
		}
		return false;
	}

	private void renderScrollbar(GuiGraphics g) {
		int x = this.width - CARD_PADDING - SCROLLBAR_WIDTH;
		int y = HEADER_HEIGHT + CARD_PADDING;
		int height = contentHeight();
		int maxPx = maxScrollPixels();
		OreUiRenderer.drawScrollbarPixels(g, x, y, height, height, maxPx + height, scrollPixelOffset);
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
