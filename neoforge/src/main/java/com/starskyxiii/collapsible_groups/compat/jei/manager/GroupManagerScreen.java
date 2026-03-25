package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.compat.jei.GroupUiState;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.editor.GroupEditorScreen;
import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.platform.Services;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Scrollable card-grid screen listing all collapsible groups.
 * All group types (item, fluid, generic) are represented uniformly as {@link AnyCard.ItemCard}.
 * User-editable groups show Toggle / Edit / Delete buttons; KubeJS and built-in groups show a read-only badge.
 */
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
	private static final int CACHE_FALLBACK_SAMPLE_LIMIT = 8;

	// -----------------------------------------------------------------------
	// Card type abstraction
	// -----------------------------------------------------------------------

	private interface AnyCard {
		String id();
		String displayName();
		int entryCount();
		boolean isEditable();
		List<GroupPreviewEntry> previewEntries();
		int itemCount();
		int fluidCount();
		int genericCount();

		record ItemCard(
			GroupDefinition group,
			List<ItemStack> items,
			List<Object> fluids,
			List<GenericIngredientRef> genericEntries,
			List<GroupPreviewEntry> previewEntries
		) implements AnyCard {
			public String id()          { return group.id(); }
			public String displayName() {
				String resolved = group.name();
				String name = resolved.isEmpty() ? group.id() : resolved;
				if (group.id().startsWith("__kjs_"))
					return Component.translatable(ModTranslationKeys.MANAGER_PREFIX_KUBEJS, name).getString();
				if (GroupRegistry.isBuiltin(group.id()))
					return Component.translatable(ModTranslationKeys.MANAGER_PREFIX_BUILTIN, name).getString();
				return name;
			}
			public int entryCount()     { return previewEntries.size(); }
			public boolean isEditable() {
				return !group.id().startsWith("__kjs_") && !GroupRegistry.isBuiltin(group.id());
			}
			public int itemCount()      { return items.size(); }
			public int fluidCount()     { return fluids.size(); }
			public int genericCount()   { return genericEntries.size(); }
		}
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

	// -----------------------------------------------------------------------
	// Fields
	// -----------------------------------------------------------------------

	private final Screen previousScreen;
	private final boolean kubeJsLoaded;
	private final Map<String, Integer> previewScrollOffsets = new HashMap<>();
	private List<AnyCard> allCards      = new ArrayList<>();
	private List<AnyCard> filteredCards = new ArrayList<>();
	private int cols = 1;
	private int scrollPixelOffset = 0;

	// Filter state
	private boolean showBuiltin       = GroupUiState.showBuiltin();
	private boolean showKubeJs        = GroupUiState.showKubeJs();
	private boolean builtinFilterHeld = false;
	private boolean kubejsFilterHeld  = false;

	// Header buttons: manually tracked to fire on mouse-release
	private static final int BACK_BTN_X    = 6,  BACK_BTN_Y = 6, BACK_BTN_W = 50, BACK_BTN_H = 20;
	private static final int BUILTIN_BTN_X = 62, BUILTIN_BTN_W = 72;
	private static final int KUBEJS_BTN_X  = 140, KUBEJS_BTN_W = 65;
	private static final int NEW_BTN_W = 110, NEW_BTN_H = 20;
	private boolean backButtonHeld     = false;
	private boolean newGroupButtonHeld = false;

	// Hover state (updated each render frame)
	private int hoveredCardIndex  = -1;
	private int hoveredButtonType = -1; // 0=toggle, 1=edit, 2=delete

	// Main scrollbar drag state
	private boolean isDraggingScrollbar = false;
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

	/** Flips the enabled state of one card in-place without re-resolving ingredients. */
	private void updateCardEnabled(String id, boolean enabled) {
		for (int i = 0; i < allCards.size(); i++) {
			if (allCards.get(i).id().equals(id) && allCards.get(i) instanceof AnyCard.ItemCard ic) {
				GroupDefinition updated = ic.group().withEnabled(enabled);
				allCards.set(i, new AnyCard.ItemCard(
					updated, ic.items(), ic.fluids(), ic.genericEntries(), ic.previewEntries()));
				rebuildFilteredCards();
				return;
			}
		}
	}

	/** Removes one card from the list without rebuilding the rest. */
	private void removeCard(String id) {
		allCards.removeIf(c -> c.id().equals(id));
		previewScrollOffsets.remove(id);
		rebuildFilteredCards();
	}

	private void rebuildCards() {
		long traceStart = PerformanceTrace.begin();
		allCards = new ArrayList<>();
		int totalItems = 0;
		int totalFluids = 0;
		int totalGeneric = 0;
		CacheTraceStats cacheStats = new CacheTraceStats();

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
			allCards.add(new AnyCard.ItemCard(group, items, fluids, generic,
				GroupPreviewEntry.combine(items, fluids, generic)));
		}

		previewScrollOffsets.keySet().retainAll(
			allCards.stream().map(AnyCard::id).collect(Collectors.toSet()));
		rebuildFilteredCards();
		PerformanceTrace.log("GroupManagerScreen.rebuildCards.cache", cacheStats.summary());
		PerformanceTrace.logIfSlow("GroupManagerScreen.rebuildCards", traceStart, 20,
			"groups=" + allCards.size()
				+ " totalItems=" + totalItems
				+ " totalFluids=" + totalFluids
				+ " totalGeneric=" + totalGeneric);
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

	private void openEditor(GroupDefinition group) {
		Minecraft.getInstance().setScreen(new GroupEditorScreen(this, group));
	}

	// -----------------------------------------------------------------------
	// Rendering
	// -----------------------------------------------------------------------

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTicks);

		hoveredCardIndex  = -1;
		hoveredButtonType = -1;

		int vpTop    = HEADER_HEIGHT;
		int vpBottom = this.height - FOOTER_HEIGHT;

		// ---- Header bar ----
		guiGraphics.fill(0, 0, this.width, vpTop, 0xCC0E0E1A);
		guiGraphics.fill(0, vpTop, this.width, vpTop + 1, 0x33667799);

		// ---- Footer bar ----
		guiGraphics.fill(0, vpBottom, this.width, this.height, 0xAA0E0E1A);
		guiGraphics.fill(0, vpBottom, this.width, vpBottom + 1, 0x33667799);

		guiGraphics.enableScissor(0, vpTop, this.width, vpBottom);

		for (int i = 0; i < filteredCards.size(); i++) {
			renderCard(guiGraphics, i, mouseX, mouseY);
		}

		guiGraphics.disableScissor();
		renderScrollbar(guiGraphics);

		// Back button
		boolean backHover = isMouseOver(mouseX, mouseY, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H);
		renderCardButton(guiGraphics, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H,
			Component.translatable(ModTranslationKeys.MANAGER_BTN_BACK).getString(), backHover || backButtonHeld);

		// Built-in filter button
		boolean builtinHover = isMouseOver(mouseX, mouseY, BUILTIN_BTN_X, BACK_BTN_Y, BUILTIN_BTN_W, BACK_BTN_H);
		renderFilterButton(guiGraphics, BUILTIN_BTN_X, BACK_BTN_Y, BUILTIN_BTN_W, BACK_BTN_H,
			Component.translatable(ModTranslationKeys.MANAGER_BTN_FILTER_BUILTIN).getString(),
			showBuiltin, builtinHover || builtinFilterHeld, 0xAA665533);

		// KubeJS filter button ??only shown when KubeJS is installed
		if (kubeJsLoaded) {
			boolean kubejsHover = isMouseOver(mouseX, mouseY, KUBEJS_BTN_X, BACK_BTN_Y, KUBEJS_BTN_W, BACK_BTN_H);
			renderFilterButton(guiGraphics, KUBEJS_BTN_X, BACK_BTN_Y, KUBEJS_BTN_W, BACK_BTN_H,
				Component.translatable(ModTranslationKeys.MANAGER_BTN_FILTER_KUBEJS).getString(),
				showKubeJs, kubejsHover || kubejsFilterHeld, 0xAA664488);
		}

		// New Group button
		int newBtnX = this.width - NEW_BTN_W - 6;
		boolean newHover = isMouseOver(mouseX, mouseY, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H);
		renderCardButton(guiGraphics, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H,
			Component.translatable(ModTranslationKeys.MANAGER_BTN_NEW_GROUP).getString(), newHover || newGroupButtonHeld);

		// Title and group count centered at top
		guiGraphics.drawCenteredString(font, this.title, this.width / 2, 8, 0xFFFFFF);
		Component countText = filteredCards.size() == allCards.size()
			? Component.translatable(ModTranslationKeys.MANAGER_COUNT_ALL, allCards.size())
			: Component.translatable(ModTranslationKeys.MANAGER_COUNT_FILTERED, filteredCards.size(), allCards.size());
		guiGraphics.drawCenteredString(font, countText, this.width / 2, 20, 0x8899AABB);

		// Footer info text
		guiGraphics.drawString(font, Component.translatable(ModTranslationKeys.MANAGER_FOOTER_HINT),
			6, vpBottom + (FOOTER_HEIGHT - font.lineHeight) / 2 + 1, 0x8899AABB, false);

		for (var renderable : this.renderables) {
			renderable.render(guiGraphics, mouseX, mouseY, partialTicks);
		}
	}

	private void renderCard(GuiGraphics guiGraphics, int index, int mouseX, int mouseY) {
		AnyCard card = filteredCards.get(index);
		int[] pos = cardPos(index);
		int x = pos[0], y = pos[1];

		if (y + CARD_HEIGHT < HEADER_HEIGHT || y > this.height - FOOTER_HEIGHT) return;

		// Background and border colours ??unified dark theme matching editor
		int bgColor = 0x55101020;
		int borderColor;
		if (card instanceof AnyCard.ItemCard ic && ic.isEditable()) {
			borderColor = ic.group().enabled() ? 0x55339966 : 0x55993333;
		} else if (card instanceof AnyCard.ItemCard ic && GroupRegistry.isBuiltin(ic.id())) {
			borderColor = 0x55665533; // amber ??built-in provider group
		} else {
			borderColor = 0x55664488; // purple ??KubeJS ephemeral group
		}

		guiGraphics.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, bgColor);
		drawOutline(guiGraphics, x, y, CARD_WIDTH, CARD_HEIGHT, borderColor);

		// Preview grid (items/fluids/generic) ??rendered before text
		int previewX = x + 3;
		int previewY = y + 25;
		int rowOffset = previewScrollOffsets.getOrDefault(card.id(), 0);
		renderCardPreview(guiGraphics, card, previewX, previewY, rowOffset);

		// Preview vertical scrollbar
		int previewTotalRows = totalRowsForCard(card);
		if (previewTotalRows > PREVIEW_ROWS) {
			int sbX    = previewX + PREVIEW_COLS * ITEM_SIZE + 2;
			int sbY    = previewY;
			int sbH    = PREVIEW_ROWS * ITEM_SIZE;
			int maxRow = previewTotalRows - PREVIEW_ROWS;
			int thumbH = Math.max(6, sbH * PREVIEW_ROWS / previewTotalRows);
			int thumbY = sbY + (maxRow > 0 ? (sbH - thumbH) * rowOffset / maxRow : 0);
			guiGraphics.fill(sbX, sbY, sbX + 3, sbY + sbH, 0x18667799);
			guiGraphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0x6699AABB);
		}

		// Card body hover highlight
		if (isMouseOver(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT - 22)) {
			guiGraphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT - 22, 0x18667799);
		}

		// Title (scrolling marquee when hovered) and count
		boolean cardHovered = isMouseOver(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT);
		renderScrollingText(guiGraphics, card.displayName(), x + 4, y + 4, CARD_WIDTH - 8, 0xFFFFFF, cardHovered);
		guiGraphics.drawString(font, countLabel(card), x + 4, y + 14, 0x7799AABB, false);

		// Buttons (editable) or read-only badge
		boolean inVp    = isInsideCardViewport(mouseX, mouseY);
		int buttonY     = y + BTN_Y_OFF;

		if (card.isEditable() && card instanceof AnyCard.ItemCard ic) {
			boolean toggleHover = inVp && isMouseOver(mouseX, mouseY, x + 2,   buttonY, 74, BTN_H);
			boolean editHover   = inVp && isMouseOver(mouseX, mouseY, x + 80,  buttonY, 38, BTN_H);
			boolean delHover    = inVp && isMouseOver(mouseX, mouseY, x + 122, buttonY, 36, BTN_H);

			renderCardButton(guiGraphics, x + 2,   buttonY, 74, BTN_H,
				Component.translatable(ic.group().enabled() ? ModTranslationKeys.MANAGER_BTN_ENABLED : ModTranslationKeys.MANAGER_BTN_DISABLED).getString(), toggleHover);
			renderCardButton(guiGraphics, x + 80,  buttonY, 38, BTN_H,
				Component.translatable(ModTranslationKeys.MANAGER_BTN_EDIT).getString(), editHover);
			renderCardButton(guiGraphics, x + 122, buttonY, 36, BTN_H,
				Component.translatable(ModTranslationKeys.MANAGER_BTN_DELETE).getString(), delHover);

			if (toggleHover) { hoveredCardIndex = index; hoveredButtonType = 0; }
			if (editHover)   { hoveredCardIndex = index; hoveredButtonType = 1; }
			if (delHover)    { hoveredCardIndex = index; hoveredButtonType = 2; }
		} else {
			renderReadOnlyBadge(guiGraphics, x + 2, buttonY, CARD_WIDTH - 4, BTN_H, card);
		}
	}

	private Component countLabel(AnyCard card) {
		net.minecraft.network.chat.MutableComponent result = null;
		if (card.itemCount() > 0) {
			result = Component.translatable(ModTranslationKeys.COUNT_ITEMS, card.itemCount());
		}
		if (card.fluidCount() > 0) {
			net.minecraft.network.chat.MutableComponent part = Component.translatable(ModTranslationKeys.COUNT_FLUIDS, card.fluidCount());
			result = result == null ? part : result.append(", ").append(part);
		}
		if (card.genericCount() > 0) {
			net.minecraft.network.chat.MutableComponent part = Component.translatable(ModTranslationKeys.COUNT_ENTRIES, card.genericCount());
			result = result == null ? part : result.append(", ").append(part);
		}
		return result != null ? result : Component.empty();
	}

	// -----------------------------------------------------------------------
	// Preview rendering
	// -----------------------------------------------------------------------

	private void renderCardPreview(GuiGraphics guiGraphics, AnyCard card, int previewX, int previewY, int rowOffset) {
		renderPreviewEntries(guiGraphics, card.previewEntries(), previewX, previewY, rowOffset);
	}

	private void renderPreviewEntries(GuiGraphics guiGraphics, List<GroupPreviewEntry> entries,
	                                  int previewX, int previewY, int rowOffset) {
		int remaining  = entries.size() - (rowOffset + PREVIEW_ROWS) * PREVIEW_COLS;
		int maxVisible = remaining > 0 ? PREVIEW_ROWS * PREVIEW_COLS - 1 : PREVIEW_ROWS * PREVIEW_COLS;
		int idx = rowOffset * PREVIEW_COLS;
		int rendered = 0;
		for (int row = 0; row < PREVIEW_ROWS && idx < entries.size() && rendered < maxVisible; row++) {
			for (int col = 0; col < PREVIEW_COLS && idx < entries.size() && rendered < maxVisible; col++) {
				entries.get(idx).render(guiGraphics, previewX + col * ITEM_SIZE, previewY + row * ITEM_SIZE);
				idx++;
				rendered++;
			}
		}
		renderOverflowBadge(guiGraphics, remaining, previewX, previewY);
	}

	/** Renders a "+N" badge in the last preview slot when there are more entries than visible. */
	private void renderOverflowBadge(GuiGraphics guiGraphics, int remaining, int previewX, int previewY) {
		if (remaining <= 0) return;
		int lastX  = previewX + (PREVIEW_COLS - 1) * ITEM_SIZE;
		int lastY  = previewY + (PREVIEW_ROWS - 1) * ITEM_SIZE;
		String more = "+" + (remaining + 1);
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(0, 0, 200);
		guiGraphics.fill(lastX, lastY, lastX + ITEM_SIZE, lastY + ITEM_SIZE, 0x88000000);
		guiGraphics.drawString(font, more,
			lastX + (ITEM_SIZE - font.width(more)) / 2,
			lastY + (ITEM_SIZE - 8) / 2,
			0xFFFFFF, false);
		guiGraphics.pose().popPose();
	}

	// -----------------------------------------------------------------------
	// Button / badge / text rendering
	// -----------------------------------------------------------------------

	private void renderCardButton(GuiGraphics guiGraphics, int x, int y, int w, int h, String label, boolean hovered) {
		int bg     = hovered ? 0xCC1A1A2E : 0x880E0E1A;
		int border = hovered ? 0x8899AABB : 0x44667799;
		int text   = hovered ? 0xFFFFFFFF : 0xCC99AABB;
		guiGraphics.fill(x, y, x + w, y + h, bg);
		drawOutline(guiGraphics, x, y, w, h, border);
		guiGraphics.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, text, false);
	}

	/**
	 * Filter toggle button. When {@code active} the category is visible; when inactive it is hidden.
	 * {@code accentBorder} is the coloured border used when the filter is active.
	 */
	private void renderFilterButton(GuiGraphics g, int x, int y, int w, int h,
	                                String label, boolean active, boolean hovered, int accentBorder) {
		int bg     = hovered ? 0xCC1A1A2E : 0x880E0E1A;
		int border = hovered ? 0x8899AABB : (active ? accentBorder : 0x44334444);
		int text   = hovered ? 0xFFFFFFFF : (active ? 0xCC99AABB : 0x55667788);
		g.fill(x, y, x + w, y + h, bg);
		drawOutline(g, x, y, w, h, border);
		g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, text, false);
	}

	private void renderReadOnlyBadge(GuiGraphics guiGraphics, int x, int y, int w, int h, AnyCard card) {
		boolean isBuiltin = card instanceof AnyCard.ItemCard ic && GroupRegistry.isBuiltin(ic.id());
		String label  = Component.translatable(isBuiltin ? ModTranslationKeys.MANAGER_BADGE_BUILTIN : ModTranslationKeys.MANAGER_BADGE_KUBEJS).getString();
		int    border = isBuiltin ? 0x44665533 : 0x44664488;
		int    text   = isBuiltin ? 0x88BBAA66 : 0x8899AABB;
		guiGraphics.fill(x, y, x + w, y + h, 0x880E0E1A);
		drawOutline(guiGraphics, x, y, w, h, border);
		guiGraphics.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, text, false);
	}

	private void drawOutline(GuiGraphics g, int x, int y, int width, int height, int color) {
		int right = x + width;
		int bottom = y + height;
		g.fill(x, y, right, y + 1, color);
		g.fill(x, bottom - 1, right, bottom, color);
		g.fill(x, y + 1, x + 1, bottom - 1, color);
		g.fill(right - 1, y + 1, right, bottom - 1, color);
	}

	/**
	 * Renders text clipped to {@code maxWidth}. When hovered and text is wider than the clip region,
	 * shows a looping marquee; when not hovered, truncates with "??.
	 */
	private void renderScrollingText(GuiGraphics g, String text, int x, int y,
	                                 int maxWidth, int color, boolean hovered) {
		int textWidth = font.width(text);
		if (textWidth <= maxWidth) {
			g.drawString(font, text, x, y, color, true);
			return;
		}
		if (!hovered) {
			String truncated = font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
			g.drawString(font, truncated, x, y, color, true);
			return;
		}
		// Marquee scroll (outer viewport scissor already active; inner scissor is intersected)
		g.enableScissor(x, y - 1, x + maxWidth, y + font.lineHeight + 1);
		int   gap         = 20;
		int   totalCycle  = textWidth + gap;
		float scrollOffset = (System.currentTimeMillis() % (totalCycle * 30L)) / 30.0f;
		int   drawX1      = (int)(x - scrollOffset);
		int   drawX2      = drawX1 + totalCycle;
		g.drawString(font, text, drawX1, y, color, true);
		g.drawString(font, text, drawX2, y, color, true);
		g.disableScissor();
	}

	// -----------------------------------------------------------------------
	// Input handling
	// -----------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Header buttons: record held state on press; action fires on release
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

		// Scrollbar click
		int sbX = this.width - CARD_PADDING - SCROLLBAR_WIDTH;
		int sbY = HEADER_HEIGHT + CARD_PADDING;
		int sbH = contentHeight();
		if (mouseX >= sbX && mouseX < sbX + SCROLLBAR_WIDTH && mouseY >= sbY && mouseY < sbY + sbH) {
			isDraggingScrollbar = true;
			sbDragStartMouseY = mouseY;
			int maxPx = maxScrollPixels();
			if (maxPx > 0) {
				int totalPx = maxPx + sbH;
				int thumbH  = Math.max(14, sbH * sbH / totalPx);
				int travel  = sbH - thumbH;
				int thumbY  = sbY + (travel > 0 ? travel * scrollPixelOffset / maxPx : 0);
				if (mouseY < thumbY || mouseY >= thumbY + thumbH) {
					scrollPixelOffset = clamp(
						(int) ((mouseY - sbY - thumbH / 2.0) * maxPx / Math.max(1, travel)), 0, maxPx);
				}
			}
			sbDragStartPixelOffset = scrollPixelOffset;
			return true;
		}

		if (!isInsideCardViewport(mouseX, mouseY)) return false;

		for (int i = 0; i < filteredCards.size(); i++) {
			AnyCard card = filteredCards.get(i);
			if (!(card instanceof AnyCard.ItemCard ic) || !ic.isEditable()) continue;

			int[] pos = cardPos(i);
			int x = pos[0], y = pos[1];
			if (y + CARD_HEIGHT < HEADER_HEIGHT || y > this.height - FOOTER_HEIGHT) continue;

			int buttonY = y + BTN_Y_OFF;

			if (isMouseOver(mouseX, mouseY, x + 2, buttonY, 74, BTN_H)) {
				boolean newEnabled = !ic.group().enabled();
				GroupRegistry.saveQuietly(ic.group().withEnabled(newEnabled));
				updateCardEnabled(ic.group().id(), newEnabled);
				GroupRegistry.notifyJeiStructureOnly();
				return true;
			}
			if (isMouseOver(mouseX, mouseY, x + 80, buttonY, 38, BTN_H)) {
				openEditor(ic.group());
				return true;
			}
			if (isMouseOver(mouseX, mouseY, x + 122, buttonY, 36, BTN_H)) {
				String deletedId = ic.group().id();
				GroupRegistry.deleteQuietly(deletedId);
				removeCard(deletedId);
				GroupRegistry.notifyJei();
				scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && isDraggingScrollbar) {
			int maxPx = maxScrollPixels();
			if (maxPx > 0) {
				int sbH    = contentHeight();
				int totalPx = maxPx + sbH;
				int thumbH  = Math.max(14, sbH * sbH / totalPx);
				int travel  = sbH - thumbH;
				if (travel > 0) {
					double delta = mouseY - sbDragStartMouseY;
					scrollPixelOffset = clamp(
						(int) Math.round(sbDragStartPixelOffset + delta * maxPx / travel), 0, maxPx);
				}
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		// Header buttons: fire action only if the cursor is still over the button on release
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
			scrollPixelOffset = clamp(scrollPixelOffset + (int) (deltaY * -20), 0, maxScrollPixels());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
	}

	private boolean scrollHoveredPreview(double mouseX, double mouseY, double deltaY) {
		for (int i = 0; i < filteredCards.size(); i++) {
			AnyCard card = filteredCards.get(i);
			int[] pos    = cardPos(i);
			int previewX = pos[0] + 3;
			int previewY = pos[1] + 25;
			if (mouseX >= previewX && mouseX < previewX + PREVIEW_COLS * ITEM_SIZE
					&& mouseY >= previewY && mouseY < previewY + PREVIEW_ROWS * ITEM_SIZE) {
				int maxRow  = Math.max(0, totalRowsForCard(card) - PREVIEW_ROWS);
				int current = previewScrollOffsets.getOrDefault(card.id(), 0);
				int next    = clamp(current - (int) Math.signum(deltaY), 0, maxRow);
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

	// -----------------------------------------------------------------------
	// Layout helpers
	// -----------------------------------------------------------------------

	private int contentHeight() {
		return this.height - HEADER_HEIGHT - FOOTER_HEIGHT - CARD_PADDING;
	}

	private int totalCardRows() {
		return filteredCards.isEmpty() ? 0 : (filteredCards.size() + cols - 1) / cols;
	}

	private int maxScrollPixels() {
		int totalH = totalCardRows() * (CARD_HEIGHT + CARD_PADDING);
		return Math.max(0, totalH - contentHeight());
	}

	private int[] cardPos(int cardIndex) {
		int usedWidth = cols * CARD_WIDTH + Math.max(0, cols - 1) * CARD_PADDING;
		int left = (this.width - SCROLLBAR_WIDTH - CARD_PADDING - usedWidth) / 2;
		int col  = cardIndex % cols;
		int row  = cardIndex / cols;
		int x    = left + col * (CARD_WIDTH + CARD_PADDING);
		int y    = HEADER_HEIGHT + CARD_PADDING + row * (CARD_HEIGHT + CARD_PADDING) - scrollPixelOffset;
		return new int[]{x, y};
	}

	private int totalRowsForCard(AnyCard card) {
		int count = card.previewEntries().size();
		if (count == 0) return 0;
		return Math.max(1, (count + PREVIEW_COLS - 1) / PREVIEW_COLS);
	}

	@SuppressWarnings("unchecked")
	private boolean isInsideCardViewport(double mouseX, double mouseY) {
		return mouseX >= CARD_PADDING
			&& mouseX < this.width - CARD_PADDING
			&& mouseY >= HEADER_HEIGHT
			&& mouseY < this.height - FOOTER_HEIGHT;
	}

	private static boolean isMouseOver(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private void renderScrollbar(GuiGraphics guiGraphics) {
		int x      = this.width - CARD_PADDING - SCROLLBAR_WIDTH;
		int y      = HEADER_HEIGHT + CARD_PADDING;
		int height = contentHeight();
		guiGraphics.fill(x, y, x + SCROLLBAR_WIDTH, y + height, 0x18667799);

		int maxPx = maxScrollPixels();
		if (maxPx <= 0) {
			guiGraphics.fill(x, y, x + SCROLLBAR_WIDTH, y + height, 0x22334455);
			return;
		}

		int totalPx   = maxPx + height;
		int thumbHeight = Math.max(14, height * height / totalPx);
		int travel    = height - thumbHeight;
		int thumbY    = y + travel * scrollPixelOffset / maxPx;
		guiGraphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0x6699AABB);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	/** Called by {@link GroupEditorScreen} after a save to rebuild the card list while preserving scroll position. */
	public void onGroupSaved() {
		rebuildCards();
		scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
	}
}
