package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.compat.jei.GroupUiState;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.editor.GroupEditorScreen;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.BatchActionEligibility;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.BatchSelectionState;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.GroupAction;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.GroupCardViewModel;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
	private static final int HEADER_HEIGHT = 78;
	private static final int FOOTER_HEIGHT = 28;
	private static final int TOP_BUTTON_GAP = 6;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int CACHE_FALLBACK_SAMPLE_LIMIT = 8;

	private static final int BACK_BTN_X = 6;
	private static final int BACK_BTN_Y = 5;
	private static final int BACK_BTN_W = 50;
	private static final int BACK_BTN_H = 20;
	private static final int HEADER_TITLE_X = 62;
	private static final int BATCH_TOGGLE_BTN_W = 92;
	private static final int NEW_BTN_W = 110;
	private static final int NEW_BTN_H = 20;
	private static final int BATCH_ACTION_BTN_W = 72;
	private static final int BATCH_ACTION_BTN_MIN_W = 44;
	private static final int BATCH_ACTION_BTN_H = 20;
	private static final int BATCH_ACTION_GAP = 6;
	private static final int SEGMENT_X = 6;
	private static final int SEGMENT_Y = 31;
	private static final int SEGMENT_HEIGHT = 18;
	private static final int SEGMENT_MIN_WIDTH = 40;
	private static final int SEGMENT_TEXT_PADDING = 16;
	private static final int SEGMENT_OVERLAP = 1;
	private static final int SEARCH_FIELD_MIN_WIDTH = 96;
	private static final int SEARCH_FIELD_MAX_WIDTH = 180;
	private static final int SEARCH_FIELD_GAP = 8;
	private static final int SEARCH_FIELD_Y = 53;
	private static final int SEARCH_FIELD_HEIGHT = 18;
	private static final int SEARCH_FIELD_TEXT_PAD = 5;
	private static final int BATCH_SELECTED_STATUS_W = 112;
	private static final int MINI_SCROLLBAR_GAP = 4;
	private static final int MINI_SCROLLBAR_WIDTH = 5;
	private static final int DELETE_DIALOG_WIDTH = 240;
	private static final int DELETE_DIALOG_HEIGHT = 112;
	private static final int DELETE_DIALOG_BUTTON_WIDTH = 96;
	private static final int DELETE_DIALOG_BUTTON_HEIGHT = 20;
	private static final int DELETE_DIALOG_BUTTON_GAP = 8;
	private static final int BATCH_SELECTED_OVERLAY = 0x3340B96A;

	private final Screen previousScreen;
	private final boolean kubeJsLoaded;
	private final Map<String, Integer> previewScrollOffsets = new HashMap<>();
	private List<GroupManagerCard> allCards = new ArrayList<>();
	private List<GroupManagerCard> filteredCards = new ArrayList<>();
	private int cols = 1;
	private int scrollPixelOffset = 0;

	private GroupUiState.ManagerSourceFilter sourceFilter = GroupUiState.managerSourceFilter();
	private EditBox searchField;
	private String searchQuery = "";
	private boolean backButtonHeld = false;
	private int heldSegmentIndex = -1;
	private boolean batchMode = false;
	private boolean batchToggleButtonHeld = false;
	private BatchSelectionState batchSelection = BatchSelectionState.empty();
	private BatchToolbarAction heldBatchToolbarAction = null;
	private boolean newGroupButtonHeld = false;
	private boolean isDraggingScrollbar = false;
	private String heldSwitchGroupId = null;
	private String suppressedSwitchHoverGroupId = null;
	private double sbDragStartMouseY;
	private int sbDragStartPixelOffset;
	private Component pendingTooltip;
	private PendingDelete pendingDelete = null;
	private PendingBatchDelete pendingBatchDelete = null;

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
		clearWidgets();
		searchField = null;
		rebuildCards();
		calcLayout();
		createSearchField();
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
		filteredCards = allCards.stream().filter(this::matchesCurrentFilters).toList();
		scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
		pruneBatchSelectionToFilteredCards();
	}

	private boolean matchesCurrentFilters(GroupManagerCard card) {
		return GroupManagerSearchMatcher.matches(sourceFilter, searchQuery, searchFields(card));
	}

	private GroupManagerSearchMatcher.SearchFields searchFields(GroupManagerCard card) {
		return new GroupManagerSearchMatcher.SearchFields(
			localizedDisplayName(card).getString(),
			card.displayName(),
			card.id(),
			card.source(),
			sourceSearchLabel(card.source())
		);
	}

	private void calcLayout() {
		int usableWidth = this.width - CARD_PADDING * 2 - SCROLLBAR_WIDTH - CARD_PADDING;
		cols = Math.max(1, usableWidth / (CARD_WIDTH + CARD_PADDING));
		scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
	}

	private void createSearchField() {
		SearchFieldLayout layout = searchFieldLayout();
		if (!layout.visible()) return;
		int textX = layout.x() + SEARCH_FIELD_TEXT_PAD;
		int textY = searchFieldTextY(layout);
		int textWidth = Math.max(1, layout.width() - SEARCH_FIELD_TEXT_PAD * 2);
		searchField = new EditBox(font, textX, textY, textWidth, font.lineHeight,
			Component.translatable(ModTranslationKeys.MANAGER_SEARCH_LABEL));
		searchField.setMaxLength(128);
		searchField.setBordered(false);
		searchField.setTextColor(OreUiPalette.TEXT_PRIMARY);
		searchField.setTextColorUneditable(OreUiPalette.TEXT_DISABLED);
		searchField.setHint(Component.translatable(ModTranslationKeys.MANAGER_SEARCH_HINT));
		searchField.setValue(searchQuery);
		searchField.setResponder(this::applySearchQuery);
		addRenderableWidget(searchField);
	}

	private int searchFieldTextY(SearchFieldLayout layout) {
		return OreUiRenderer.textFieldTextY(font, layout.y(), layout.height()) + 1;
	}

	private void applySearchQuery(String value) {
		String next = value == null ? "" : value;
		if (searchQuery.equals(next)) return;
		searchQuery = next;
		clearTransientInputState();
		rebuildFilteredCards();
	}

	private int headerHeight() {
		return HEADER_HEIGHT;
	}

	private SearchFieldLayout searchFieldLayout() {
		int right = batchSelectedStatusX() - SEARCH_FIELD_GAP;
		int width = Math.min(SEARCH_FIELD_MAX_WIDTH, right - SEGMENT_X);
		if (width >= SEARCH_FIELD_MIN_WIDTH) {
			return new SearchFieldLayout(SEGMENT_X, SEARCH_FIELD_Y, width, SEARCH_FIELD_HEIGHT, true, false);
		}
		return SearchFieldLayout.hidden();
	}

	private void renderSearchFieldChrome(GuiGraphics g, int mouseX, int mouseY) {
		SearchFieldLayout layout = searchFieldLayout();
		if (!layout.visible()) return;
		boolean hovered = isMouseOver(mouseX, mouseY, layout.x(), layout.y(), layout.width(), layout.height());
		boolean focused = searchField != null && searchField.isFocused();
		int outline = focused ? OreUiPalette.OUTLINE_SELECTED : hovered ? OreUiPalette.OUTLINE_HOVER : OreUiPalette.OUTLINE_DARK;
		g.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + layout.height(), OreUiPalette.SURFACE_DARK);
		g.fill(layout.x() + 1, layout.y() + 1,
			layout.x() + layout.width() - 1, layout.y() + layout.height() - 1, OreUiPalette.SURFACE);
		OreUiRenderer.drawOutline(g, layout.x(), layout.y(), layout.width(), layout.height(), outline);
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
		removeCards(List.of(id));
	}

	private void removeCards(List<String> ids) {
		if (ids.isEmpty()) return;
		Set<String> idSet = new HashSet<>(ids);
		allCards.removeIf(card -> idSet.contains(card.id()));
		previewScrollOffsets.keySet().removeIf(idSet::contains);
		if (suppressedSwitchHoverGroupId != null && idSet.contains(suppressedSwitchHoverGroupId)) {
			suppressedSwitchHoverGroupId = null;
		}
		rebuildFilteredCards();
	}

	private void pruneBatchSelectionToFilteredCards() {
		if (!batchMode) {
			batchSelection = batchSelection.clear();
			return;
		}
		batchSelection = batchSelection.pruneTo(filteredCards.stream().map(GroupManagerCard::id).toList());
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

		int headerHeight = headerHeight();
		int vpTop = headerHeight;
		int vpBottom = this.height - FOOTER_HEIGHT;
		OreUiRenderer.drawScreenBars(g, this.width, this.height, headerHeight, FOOTER_HEIGHT);

		g.enableScissor(0, vpTop, this.width, vpBottom);
		for (int i = 0; i < filteredCards.size(); i++) {
			renderCard(g, i, mouseX, mouseY);
		}
		if (filteredCards.isEmpty()) {
			renderEmptyState(g, vpTop, vpBottom);
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
		if (hasPendingDialog()) {
			pendingTooltip = null;
			renderPendingDialog(g, mouseX, mouseY);
		} else if (pendingTooltip != null) {
			g.renderTooltip(font, pendingTooltip, mouseX, mouseY);
		}
	}

	private void renderHeaderButtons(GuiGraphics g, int mouseX, int mouseY) {
		boolean backHover = isMouseOver(mouseX, mouseY, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H);
		renderButton(g, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H,
			Component.translatable(ModTranslationKeys.MANAGER_BTN_BACK).getString(), true, backHover, backButtonHeld && backHover);

		renderSegmentedFilter(g, mouseX, mouseY);
		renderSearchFieldChrome(g, mouseX, mouseY);
		if (batchMode) {
			renderBatchToolbar(g, mouseX, mouseY);
			renderBatchSelectedStatus(g);
		}

		int batchX = batchToggleButtonX();
		int batchW = batchToggleButtonWidth();
		boolean batchHover = isMouseOver(mouseX, mouseY, batchX, BACK_BTN_Y, batchW, NEW_BTN_H);
		String batchLabel = Component.translatable(batchMode
			? ModTranslationKeys.MANAGER_BATCH_DONE
			: ModTranslationKeys.MANAGER_BATCH_SELECT).getString();
		renderButton(g, batchX, BACK_BTN_Y, batchW, NEW_BTN_H, batchLabel,
			true, batchHover, batchToggleButtonHeld && batchHover);

		int newBtnX = newButtonX();
		boolean newHover = isMouseOver(mouseX, mouseY, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H);
		renderButton(g, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H,
			Component.translatable(ModTranslationKeys.MANAGER_BTN_NEW_GROUP).getString(), true, newHover, newGroupButtonHeld && newHover);
	}

	private void renderBatchToolbar(GuiGraphics g, int mouseX, int mouseY) {
		BatchActionEligibility eligibility = currentBatchEligibility();
		renderBatchToolbarButton(g, BatchToolbarAction.ENABLE, eligibility, mouseX, mouseY);
		renderBatchToolbarButton(g, BatchToolbarAction.DISABLE, eligibility, mouseX, mouseY);
		renderBatchToolbarButton(g, BatchToolbarAction.DELETE, eligibility, mouseX, mouseY);
	}

	private void renderBatchToolbarButton(GuiGraphics g, BatchToolbarAction action,
	                                     BatchActionEligibility eligibility, int mouseX, int mouseY) {
		int x = batchActionButtonX(action);
		int y = batchActionButtonY();
		int w = batchActionButtonWidth();
		boolean active = batchActionActive(action, eligibility);
		boolean hovered = isMouseOver(mouseX, mouseY, x, y, w, BATCH_ACTION_BTN_H);
		renderButton(g, x, y, w, BATCH_ACTION_BTN_H, batchActionLabel(action),
			active, hovered, heldBatchToolbarAction == action && hovered);
	}

	private void renderBatchSelectedStatus(GuiGraphics g) {
		int x = batchSelectedStatusX();
		int y = SEARCH_FIELD_Y;
		Component selected = Component.translatable(ModTranslationKeys.MANAGER_BATCH_SELECTED_COUNT, batchSelection.selectedCount());
		String selectedText = font.plainSubstrByWidth(selected.getString(), BATCH_SELECTED_STATUS_W);
		g.drawString(font, selectedText, x + BATCH_SELECTED_STATUS_W - font.width(selectedText),
			OreUiRenderer.textFieldTextY(font, y, SEARCH_FIELD_HEIGHT), OreUiPalette.TEXT_HINT, false);
	}

	private void renderEmptyState(GuiGraphics g, int viewportTop, int viewportBottom) {
		Component empty = Component.translatable(ModTranslationKeys.MANAGER_EMPTY_SEARCH);
		String text = font.plainSubstrByWidth(empty.getString(), Math.max(0, this.width - 24));
		int x = Math.max(6, (this.width - font.width(text)) / 2);
		int y = viewportTop + Math.max(0, (viewportBottom - viewportTop - font.lineHeight) / 2);
		g.drawString(font, text, x, y, OreUiPalette.TEXT_HINT, false);
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
		if (batchMode && hoveredBatchAction(mouseX, mouseY) != null) {
			return -1;
		}
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

	private String sourceSearchLabel(GroupSource source) {
		return switch (source) {
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

	private int segmentRight(GroupUiState.ManagerSourceFilter[] filters) {
		if (filters.length == 0) return SEGMENT_X;
		int lastIndex = filters.length - 1;
		return segmentX(filters, lastIndex) + segmentWidth(filters);
	}

	private void renderCard(GuiGraphics g, int index, int mouseX, int mouseY) {
		GroupManagerCard card = filteredCards.get(index);
		int[] pos = cardPos(index);
		int x = pos[0];
		int y = pos[1];
		if (y + CARD_HEIGHT < headerHeight() || y > this.height - FOOTER_HEIGHT) return;

		boolean cardHover = isMouseOver(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT);
		boolean batchSelected = batchMode && batchSelection.isSelected(card.id());
		int outlineColor = batchSelected ? OreUiPalette.OUTLINE_SELECTED
			: cardHover ? OreUiPalette.OUTLINE_HOVER : OreUiPalette.OUTLINE_DARK;
		OreUiRenderer.drawCard(g, x, y, CARD_WIDTH, CARD_HEIGHT, cardHover, outlineColor);

		renderHeaderPreview(g, card, x + 6, y + CARD_TITLE_Y, switchControlX(x) - 4, cardHover);
		renderCardPreview(g, card, x + 6, y + CARD_PREVIEW_Y, previewScrollOffsets.getOrDefault(card.id(), 0));
		renderSourceTab(g, card, x, y);
		if (!card.group().enabled()) {
			renderDisabledOverlay(g, x, y);
		}
		renderCardControls(g, card, x, y, mouseX, mouseY);
		if (batchSelected) {
			renderBatchSelectedOverlay(g, x, y);
		}
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
		boolean canSwitch = card.actionEligibility().canRequest(GroupAction.SWITCH_ENABLED);
		boolean canEdit = card.actionEligibility().canRequest(GroupAction.EDIT);
		boolean canCopy = card.actionEligibility().canRequest(GroupAction.COPY_AS_CUSTOM);
		boolean canDelete = card.actionEligibility().canRequest(GroupAction.DELETE);
		boolean canShiftDelete = card.actionEligibility().canRequest(GroupAction.SHIFT_DELETE);
		boolean canUseMiddleAction = canEdit || canCopy;

		boolean controlsInteractive = !batchMode;
		boolean rawSwitchHover = controlsInteractive && isMouseOver(mouseX, mouseY, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT);
		boolean switchHover = effectiveSwitchHover(card.id(), rawSwitchHover);
		boolean editHover = controlsInteractive && isMouseOver(mouseX, mouseY, editX, actionY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
		boolean deleteHover = controlsInteractive && isMouseOver(mouseX, mouseY, deleteX, actionY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
		boolean switchPressed = canSwitch && switchHover && card.id().equals(heldSwitchGroupId);
		boolean shiftDeleteArmed = controlsInteractive && deleteHover && canShiftDelete && Screen.hasShiftDown();

		g.pose().pushPose();
		g.pose().translate(0, 0, CARD_CONTROL_Z);
		OreUiRenderer.drawSwitch(g, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT,
			card.group().enabled(), canSwitch, switchHover, switchPressed);
		renderIconButton(g, editX, actionY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT,
			OreUiRenderer.ICON_EDIT, canUseMiddleAction, editHover, false);
		renderIconButton(g, deleteX, actionY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT,
			OreUiRenderer.ICON_DELETE, canDelete || shiftDeleteArmed, deleteHover, shiftDeleteArmed);
		g.pose().popPose();

		if (controlsInteractive) {
			if (switchHover && !canSwitch) pendingTooltip = Component.translatable(ModTranslationKeys.MANAGER_TOOLTIP_SWITCH_READONLY);
			if (editHover) pendingTooltip = canEdit
				? Component.translatable(ModTranslationKeys.MANAGER_BTN_EDIT)
				: canCopy
					? Component.translatable(ModTranslationKeys.MANAGER_TOOLTIP_COPY_AS_CUSTOM)
					: Component.translatable(ModTranslationKeys.MANAGER_BTN_COPY);
			if (deleteHover) pendingTooltip = deleteTooltip(card);
		}
	}

	private Component deleteTooltip(GroupManagerCard card) {
		if (card.actionEligibility().canRequest(GroupAction.SHIFT_DELETE) && Screen.hasShiftDown()) {
			return Component.translatable(ModTranslationKeys.MANAGER_TOOLTIP_SHIFT_DELETE);
		}
		if (card.actionEligibility().canRequest(GroupAction.DELETE)) {
			return Component.translatable(ModTranslationKeys.MANAGER_TOOLTIP_DELETE_CONFIRM);
		}
		return Component.translatable(ModTranslationKeys.MANAGER_TOOLTIP_DELETE_READONLY);
	}

	private void renderBatchSelectedOverlay(GuiGraphics g, int x, int y) {
		g.pose().pushPose();
		g.pose().translate(0, 0, CARD_CONTROL_Z + 40);
		g.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, BATCH_SELECTED_OVERLAY);
		drawOutline(g, x, y, CARD_WIDTH, CARD_HEIGHT, OreUiPalette.OUTLINE_SELECTED);
		g.pose().popPose();
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

	private BatchActionEligibility currentBatchEligibility() {
		List<GroupCardViewModel> cards = selectedVisibleCards().stream()
			.map(GroupManagerCard::viewModel)
			.toList();
		return BatchActionEligibility.fromCards(cards);
	}

	private List<GroupManagerCard> selectedVisibleCards() {
		if (batchSelection.selectedCount() == 0) return List.of();
		Map<String, GroupManagerCard> byId = new HashMap<>();
		for (GroupManagerCard card : filteredCards) {
			byId.put(card.id(), card);
		}
		List<GroupManagerCard> selected = new ArrayList<>();
		for (String id : batchSelection.selectedGroupIds()) {
			GroupManagerCard card = byId.get(id);
			if (card != null) {
				selected.add(card);
			}
		}
		return selected;
	}

	private boolean batchActionActive(BatchToolbarAction action, BatchActionEligibility eligibility) {
		return switch (action) {
			case ENABLE -> eligibility.canEnable();
			case DISABLE -> eligibility.canDisable();
			case DELETE -> eligibility.canDelete();
		};
	}

	private String batchActionLabel(BatchToolbarAction action) {
		String key = switch (action) {
			case ENABLE -> ModTranslationKeys.MANAGER_BATCH_ENABLE;
			case DISABLE -> ModTranslationKeys.MANAGER_BATCH_DISABLE;
			case DELETE -> ModTranslationKeys.MANAGER_BATCH_DELETE;
		};
		return Component.translatable(key).getString();
	}

	private int newButtonX() {
		return this.width - NEW_BTN_W - 6;
	}

	private int batchToggleButtonX() {
		return newButtonX() - batchToggleButtonWidth() - TOP_BUTTON_GAP;
	}

	private int batchToggleButtonWidth() {
		return BATCH_TOGGLE_BTN_W;
	}

	private int batchActionButtonY() {
		return SEGMENT_Y;
	}

	private int batchActionButtonX(BatchToolbarAction action) {
		int buttonWidth = batchActionButtonWidth();
		int deleteX = this.width - CARD_PADDING - buttonWidth;
		return switch (action) {
			case DELETE -> deleteX;
			case DISABLE -> deleteX - buttonWidth - BATCH_ACTION_GAP;
			case ENABLE -> deleteX - (buttonWidth + BATCH_ACTION_GAP) * 2;
		};
	}

	private int batchActionButtonWidth() {
		int right = this.width - CARD_PADDING;
		int available = right - segmentRight(segmentFilters()) - SEARCH_FIELD_GAP - BATCH_ACTION_GAP * 2;
		return clamp(available / 3, BATCH_ACTION_BTN_MIN_W, BATCH_ACTION_BTN_W);
	}

	private int batchSelectedStatusX() {
		return this.width - CARD_PADDING - BATCH_SELECTED_STATUS_W;
	}

	private BatchToolbarAction hoveredBatchAction(double mouseX, double mouseY) {
		for (BatchToolbarAction action : BatchToolbarAction.values()) {
			if (isMouseOver(mouseX, mouseY, batchActionButtonX(action), batchActionButtonY(),
				batchActionButtonWidth(), BATCH_ACTION_BTN_H)) {
				return action;
			}
		}
		return null;
	}

	private void renderPendingDialog(GuiGraphics g, int mouseX, int mouseY) {
		int x = deleteDialogX();
		int y = deleteDialogY();
		g.pose().pushPose();
		g.pose().translate(0, 0, 500);
		g.fill(0, 0, this.width, this.height, 0xAA000000);
		OreUiRenderer.drawPanel(g, x, y, DELETE_DIALOG_WIDTH, DELETE_DIALOG_HEIGHT);
		drawOutline(g, x, y, DELETE_DIALOG_WIDTH, DELETE_DIALOG_HEIGHT, OreUiPalette.OUTLINE_DARK);

		String title = pendingBatchDelete != null
			? Component.translatable(ModTranslationKeys.MANAGER_BATCH_DELETE_DIALOG_TITLE,
				pendingBatchDelete.deletableCount()).getString()
			: Component.translatable(ModTranslationKeys.MANAGER_DELETE_DIALOG_TITLE).getString();
		g.drawString(font, title, x + (DELETE_DIALOG_WIDTH - font.width(title)) / 2, y + 12,
			OreUiPalette.TEXT_PRIMARY, false);

		if (pendingBatchDelete != null) {
			drawCenteredDialogLine(g, Component.translatable(ModTranslationKeys.MANAGER_BATCH_DELETE_DIALOG_BODY,
				pendingBatchDelete.deletableCount()).getString(), y + 34, OreUiPalette.TEXT_MUTED);
			if (pendingBatchDelete.skippedCount() > 0) {
				drawCenteredDialogLine(g, Component.translatable(ModTranslationKeys.MANAGER_BATCH_DELETE_DIALOG_SKIPPED,
					pendingBatchDelete.skippedCount()).getString(), y + 46, OreUiPalette.TEXT_MUTED);
			}
		} else if (pendingDelete != null) {
			drawCenteredDialogLine(g, Component.translatable(ModTranslationKeys.MANAGER_DELETE_DIALOG_BODY,
				pendingDelete.displayName()).getString(), y + 38, OreUiPalette.TEXT_MUTED);
		}

		renderButton(g, deleteCancelButtonX(), deleteDialogButtonY(), DELETE_DIALOG_BUTTON_WIDTH,
			DELETE_DIALOG_BUTTON_HEIGHT, Component.translatable(ModTranslationKeys.BUTTON_CANCEL).getString(),
			true, isMouseOver(mouseX, mouseY, deleteCancelButtonX(), deleteDialogButtonY(),
				DELETE_DIALOG_BUTTON_WIDTH, DELETE_DIALOG_BUTTON_HEIGHT), false);
		renderButton(g, deleteConfirmButtonX(), deleteDialogButtonY(), DELETE_DIALOG_BUTTON_WIDTH,
			DELETE_DIALOG_BUTTON_HEIGHT, deleteConfirmLabel(),
			true, isMouseOver(mouseX, mouseY, deleteConfirmButtonX(), deleteDialogButtonY(),
				DELETE_DIALOG_BUTTON_WIDTH, DELETE_DIALOG_BUTTON_HEIGHT), false);
		g.pose().popPose();
	}

	private void drawCenteredDialogLine(GuiGraphics g, String text, int y, int color) {
		String clipped = font.plainSubstrByWidth(text, DELETE_DIALOG_WIDTH - 24);
		g.drawString(font, clipped, deleteDialogX() + (DELETE_DIALOG_WIDTH - font.width(clipped)) / 2, y, color, false);
	}

	private String deleteConfirmLabel() {
		if (pendingBatchDelete != null) {
			return Component.translatable(ModTranslationKeys.MANAGER_BATCH_DELETE_DIALOG_CONFIRM,
				pendingBatchDelete.deletableCount()).getString();
		}
		return Component.translatable(ModTranslationKeys.MANAGER_BTN_DELETE).getString();
	}

	private int deleteDialogX() {
		return (this.width - DELETE_DIALOG_WIDTH) / 2;
	}

	private int deleteDialogY() {
		return (this.height - DELETE_DIALOG_HEIGHT) / 2;
	}

	private int deleteDialogButtonY() {
		return deleteDialogY() + DELETE_DIALOG_HEIGHT - DELETE_DIALOG_BUTTON_HEIGHT - 12;
	}

	private int deleteDialogButtonGroupWidth() {
		return DELETE_DIALOG_BUTTON_WIDTH * 2 + DELETE_DIALOG_BUTTON_GAP;
	}

	private int deleteDialogButtonGroupX() {
		return deleteDialogX() + (DELETE_DIALOG_WIDTH - deleteDialogButtonGroupWidth()) / 2;
	}

	private int deleteCancelButtonX() {
		return deleteConfirmButtonX() + DELETE_DIALOG_BUTTON_WIDTH + DELETE_DIALOG_BUTTON_GAP;
	}

	private int deleteConfirmButtonX() {
		return deleteDialogButtonGroupX();
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
		if (hasPendingDialog()) {
			return handlePendingDialogClick(mouseX, mouseY, button);
		}
		if (button == 0 && handleSearchFieldClick(mouseX, mouseY, button)) {
			return true;
		}
		if (button == 0) {
			blurSearchField();
		}
		if (button == 0) {
			heldSwitchGroupId = null;
			heldBatchToolbarAction = null;
		}
		if (button == 0 && isMouseOver(mouseX, mouseY, BACK_BTN_X, BACK_BTN_Y, BACK_BTN_W, BACK_BTN_H)) {
			backButtonHeld = true;
			return true;
		}
		if (button == 0 && batchMode && handleBatchToolbarClick(mouseX, mouseY)) return true;
		if (button == 0) {
			int segmentIndex = hoveredSegmentIndex(segmentFilters(), mouseX, mouseY);
			if (segmentIndex >= 0) {
				heldSegmentIndex = segmentIndex;
				return true;
			}
		}
		int batchX = batchToggleButtonX();
		if (button == 0 && isMouseOver(mouseX, mouseY, batchX, BACK_BTN_Y, batchToggleButtonWidth(), NEW_BTN_H)) {
			batchToggleButtonHeld = true;
			return true;
		}
		int newBtnX = newButtonX();
		if (button == 0 && isMouseOver(mouseX, mouseY, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H)) {
			newGroupButtonHeld = true;
			return true;
		}
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		if (button != 0) return false;

		if (handleScrollbarClick(mouseX, mouseY)) return true;
		if (!isInsideCardViewport(mouseX, mouseY)) return false;
		if (batchMode) return handleBatchCardClick(mouseX, mouseY);

		for (int i = 0; i < filteredCards.size(); i++) {
			GroupManagerCard card = filteredCards.get(i);
			int[] pos = cardPos(i);
			int x = pos[0];
			int y = pos[1];
			if (y + CARD_HEIGHT < headerHeight() || y > this.height - FOOTER_HEIGHT) continue;

			boolean switchClick = isMouseOver(mouseX, mouseY, switchControlX(x), switchControlY(y), SWITCH_WIDTH, SWITCH_HEIGHT);
			boolean editClick = isMouseOver(mouseX, mouseY, editButtonX(x), y + CARD_FOOTER_Y, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
			boolean deleteClick = isMouseOver(mouseX, mouseY, deleteButtonX(x), y + CARD_FOOTER_Y, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
			if (!switchClick && !editClick && !deleteClick) continue;

			if (switchClick) {
				if (!card.actionEligibility().canRequest(GroupAction.SWITCH_ENABLED)) return true;
				heldSwitchGroupId = card.id();
				boolean newEnabled = !card.group().enabled();
				if (!GroupRegistry.setEnabledQuietly(card.id(), newEnabled)) return true;
				updateCardEnabled(card.id(), newEnabled);
				suppressedSwitchHoverGroupId = card.id();
				GroupRegistry.notifyJei();
				return true;
			}
			if (editClick) {
				if (card.actionEligibility().canRequest(GroupAction.EDIT)) {
					openEditor(card.group());
				} else if (card.actionEligibility().canRequest(GroupAction.COPY_AS_CUSTOM)) {
					executeCopyAsCustom(card.id());
				}
				return true;
			}
			if (deleteClick) {
				if (Screen.hasShiftDown()) {
					if (!card.actionEligibility().canRequest(GroupAction.SHIFT_DELETE)) return true;
					executeSingleDelete(card.id(), GroupAction.SHIFT_DELETE);
				} else {
					if (!card.actionEligibility().canRequest(GroupAction.DELETE)) return true;
					openDeleteDialog(card);
				}
				return true;
			}
		}
		return false;
	}

	private boolean handleSearchFieldClick(double mouseX, double mouseY, int button) {
		if (button != 0 || searchField == null) return false;
		SearchFieldLayout layout = searchFieldLayout();
		if (!layout.visible() || !isMouseOver(mouseX, mouseY, layout.x(), layout.y(), layout.width(), layout.height())) {
			return false;
		}
		clearTransientInputState();
		setFocused(searchField);
		searchField.setFocused(true);
		if (searchField.isMouseOver(mouseX, mouseY)) {
			searchField.mouseClicked(mouseX, mouseY, button);
		}
		return true;
	}

	private void blurSearchField() {
		if (searchField != null) {
			searchField.setFocused(false);
		}
	}

	private boolean handlePendingDialogClick(double mouseX, double mouseY, int button) {
		if (button != 0) return true;
		if (isMouseOver(mouseX, mouseY, deleteCancelButtonX(), deleteDialogButtonY(),
			DELETE_DIALOG_BUTTON_WIDTH, DELETE_DIALOG_BUTTON_HEIGHT)) {
			cancelPendingDialog();
			return true;
		}
		if (isMouseOver(mouseX, mouseY, deleteConfirmButtonX(), deleteDialogButtonY(),
			DELETE_DIALOG_BUTTON_WIDTH, DELETE_DIALOG_BUTTON_HEIGHT)) {
			if (pendingBatchDelete != null) {
				executeBatchDelete();
			} else {
				PendingDelete pending = pendingDelete;
				if (pending != null) executeSingleDelete(pending.groupId(), GroupAction.DELETE);
			}
			return true;
		}
		return true;
	}

	private boolean handleBatchToolbarClick(double mouseX, double mouseY) {
		BatchToolbarAction action = hoveredBatchAction(mouseX, mouseY);
		if (action == null) return false;
		BatchActionEligibility eligibility = currentBatchEligibility();
		if (batchActionActive(action, eligibility)) {
			heldBatchToolbarAction = action;
		}
		return true;
	}

	private boolean handleBatchCardClick(double mouseX, double mouseY) {
		for (int i = 0; i < filteredCards.size(); i++) {
			GroupManagerCard card = filteredCards.get(i);
			int[] pos = cardPos(i);
			int x = pos[0];
			int y = pos[1];
			if (y + CARD_HEIGHT < headerHeight() || y > this.height - FOOTER_HEIGHT) continue;
			if (!isMouseOver(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT)) continue;
			if (card.actionEligibility().canRequest(GroupAction.BATCH_SELECT)) {
				batchSelection = batchSelection.toggle(card.id());
			}
			return true;
		}
		return false;
	}

	private void openDeleteDialog(GroupManagerCard card) {
		clearTransientInputState();
		pendingDelete = new PendingDelete(card.id(), localizedDisplayName(card).getString());
	}

	private void cancelDeleteDialog() {
		pendingDelete = null;
		clearTransientInputState();
	}

	private void cancelPendingDialog() {
		pendingDelete = null;
		pendingBatchDelete = null;
		clearTransientInputState();
	}

	private boolean hasPendingDialog() {
		return pendingDelete != null || pendingBatchDelete != null;
	}

	private boolean executeSingleDelete(String id, GroupAction requiredAction) {
		GroupManagerCard card = findCurrentCard(id);
		if (card == null || !card.actionEligibility().canRequest(requiredAction)) {
			cancelDeleteDialog();
			return false;
		}
		GroupRegistry.deleteQuietly(id);
		removeCard(id);
		GroupRegistry.notifyJei();
		scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
		cancelDeleteDialog();
		return true;
	}

	private void setBatchMode(boolean enabled) {
		if (batchMode == enabled) return;
		batchMode = enabled;
		batchSelection = BatchSelectionState.empty();
		suppressedSwitchHoverGroupId = null;
		clearTransientInputState();
	}

	private void executeBatchSetEnabled(boolean enabled) {
		List<GroupManagerCard> cards = selectedVisibleCards();
		boolean changed = false;
		GroupAction action = enabled ? GroupAction.BATCH_ENABLE : GroupAction.BATCH_DISABLE;
		for (GroupManagerCard card : cards) {
			if (card.group().enabled() == enabled || !card.actionEligibility().canRequest(action)) {
				continue;
			}
			if (GroupRegistry.setEnabledQuietly(card.id(), enabled)) {
				updateCardEnabled(card.id(), enabled);
				changed = true;
			}
		}
		if (changed) {
			GroupRegistry.notifyJei();
		}
		heldBatchToolbarAction = null;
	}

	private void openBatchDeleteDialog() {
		BatchActionEligibility eligibility = currentBatchEligibility();
		if (!eligibility.canDelete()) return;
		clearTransientInputState();
		pendingBatchDelete = new PendingBatchDelete(
			eligibility.deletableCount(),
			eligibility.readOnlyDeleteSkippedCount()
		);
	}

	private boolean executeBatchDelete() {
		List<String> deletableIds = selectedVisibleCards().stream()
			.filter(card -> card.actionEligibility().canRequest(GroupAction.BATCH_DELETE))
			.map(GroupManagerCard::id)
			.toList();
		if (deletableIds.isEmpty()) {
			cancelPendingDialog();
			pruneBatchSelectionToFilteredCards();
			return false;
		}
		for (String id : deletableIds) {
			GroupRegistry.deleteQuietly(id);
		}
		removeCards(deletableIds);
		batchSelection = batchSelection.clear();
		GroupRegistry.notifyJei();
		scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
		cancelPendingDialog();
		return true;
	}

	private boolean executeCopyAsCustom(String id) {
		GroupManagerCard card = findCurrentCard(id);
		if (card == null || !card.actionEligibility().canRequest(GroupAction.COPY_AS_CUSTOM)) {
			return false;
		}
		String copiedDisplayName = Component.translatable(
			ModTranslationKeys.MANAGER_COPY_NAME_FORMAT,
			localizedDisplayName(card).getString()
		).getString();
		Optional<GroupDefinition> copied = GroupRegistry.createCustomCopyDraft(id, copiedDisplayName);
		if (copied.isEmpty()) {
			return false;
		}
		clearTransientInputState();
		Minecraft.getInstance().setScreen(new GroupEditorScreen(this, copied.get(), true));
		return true;
	}

	private GroupManagerCard findCurrentCard(String id) {
		if (id == null || id.isBlank()) return null;
		for (GroupManagerCard card : allCards) {
			if (id.equals(card.id())) return card;
		}
		return null;
	}

	private void clearTransientInputState() {
		backButtonHeld = false;
		heldSegmentIndex = -1;
		batchToggleButtonHeld = false;
		heldBatchToolbarAction = null;
		newGroupButtonHeld = false;
		isDraggingScrollbar = false;
		heldSwitchGroupId = null;
		suppressedSwitchHoverGroupId = null;
	}

	private boolean handleScrollbarClick(double mouseX, double mouseY) {
		int sbX = this.width - CARD_PADDING - SCROLLBAR_WIDTH;
		int sbY = headerHeight() + CARD_PADDING;
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
		if (hasPendingDialog()) {
			clearTransientInputState();
			return true;
		}
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
		if (hasPendingDialog()) {
			clearTransientInputState();
			return true;
		}
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
		if (button == 0 && batchToggleButtonHeld) {
			batchToggleButtonHeld = false;
			if (isMouseOver(mouseX, mouseY, batchToggleButtonX(), BACK_BTN_Y, batchToggleButtonWidth(), NEW_BTN_H)) {
				setBatchMode(!batchMode);
			}
			return true;
		}
		int newBtnX = newButtonX();
		if (button == 0 && newGroupButtonHeld) {
			newGroupButtonHeld = false;
			if (isMouseOver(mouseX, mouseY, newBtnX, BACK_BTN_Y, NEW_BTN_W, NEW_BTN_H)) {
				setBatchMode(false);
				openEditor(null);
			}
			return true;
		}
		if (button == 0 && heldBatchToolbarAction != null) {
			BatchToolbarAction action = heldBatchToolbarAction;
			heldBatchToolbarAction = null;
			if (batchMode
				&& isMouseOver(mouseX, mouseY, batchActionButtonX(action), batchActionButtonY(),
					batchActionButtonWidth(), BATCH_ACTION_BTN_H)
				&& batchActionActive(action, currentBatchEligibility())) {
				switch (action) {
					case ENABLE -> executeBatchSetEnabled(true);
					case DISABLE -> executeBatchSetEnabled(false);
					case DELETE -> openBatchDeleteDialog();
				}
			}
			return true;
		}
		if (button == 0) isDraggingScrollbar = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		if (hasPendingDialog()) return true;
		suppressedSwitchHoverGroupId = null;
		if (scrollHoveredPreview(mouseX, mouseY, deltaY)) return true;
		if (isInsideCardViewport(mouseX, mouseY)) {
			scrollPixelOffset = clamp(scrollPixelOffset + (int)(deltaY * -20), 0, maxScrollPixels());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (hasPendingDialog() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
			cancelPendingDialog();
			return true;
		}
		if (hasPendingDialog()) return true;
		if (searchField != null && searchField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				if (!searchQuery.isEmpty()) {
					searchField.setValue("");
				} else {
					blurSearchField();
				}
				return true;
			}
			if (searchField.keyPressed(keyCode, scanCode, modifiers)) return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (hasPendingDialog()) return true;
		if (searchField != null && searchField.isFocused()
			&& searchField.charTyped(codePoint, modifiers)) {
			return true;
		}
		return super.charTyped(codePoint, modifiers);
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
		if (hasPendingDialog()) {
			cancelPendingDialog();
			return;
		}
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
		return this.height - headerHeight() - FOOTER_HEIGHT - CARD_PADDING;
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
			headerHeight() + CARD_PADDING + (index / cols) * (CARD_HEIGHT + CARD_PADDING) - scrollPixelOffset
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
			&& mouseY >= headerHeight()
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
		int y = headerHeight() + CARD_PADDING;
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

	private record SearchFieldLayout(int x, int y, int width, int height, boolean visible, boolean stacked) {
		static SearchFieldLayout hidden() {
			return new SearchFieldLayout(0, 0, 0, 0, false, false);
		}
	}

	private enum BatchToolbarAction {
		ENABLE,
		DISABLE,
		DELETE
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

	private record PendingDelete(String groupId, String displayName) {}

	private record PendingBatchDelete(int deletableCount, int skippedCount) {}
}
