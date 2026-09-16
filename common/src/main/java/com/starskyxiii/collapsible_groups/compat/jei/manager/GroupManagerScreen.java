package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.client.manager.GroupManagerParent;
import com.starskyxiii.collapsible_groups.client.manager.CategoryChoices;
import com.starskyxiii.collapsible_groups.client.manager.CategoryPopup;
import com.starskyxiii.collapsible_groups.client.manager.CategoryManagerScreen;
import com.starskyxiii.collapsible_groups.client.manager.ManagerHeaderLayout;
import com.starskyxiii.collapsible_groups.client.manager.ManagerHeaderLayout.Rect;
import com.starskyxiii.collapsible_groups.client.manager.ManagerContentLayout;
import com.starskyxiii.collapsible_groups.client.manager.CategorySidebar;
import com.starskyxiii.collapsible_groups.persistence.GroupCategoryStore;
import com.starskyxiii.collapsible_groups.client.manager.GroupManagerSearchMatcher;
import com.starskyxiii.collapsible_groups.client.manager.GroupManagerVisibility;
import com.starskyxiii.collapsible_groups.group.GroupEvaluation;
import com.starskyxiii.collapsible_groups.group.filter.Filters;

import com.starskyxiii.collapsible_groups.client.manager.model.GroupUiState;
import com.starskyxiii.collapsible_groups.client.editor.GroupEditorScreen;
import com.starskyxiii.collapsible_groups.client.manager.model.BatchActionEligibility;
import com.starskyxiii.collapsible_groups.client.manager.model.BatchSelectionState;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupAction;
import com.starskyxiii.collapsible_groups.client.manager.model.PressedCardAction;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupCardViewModel;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupSource;
import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.client.preview.PreviewGridLayout;
import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupThemeResolver;
import com.starskyxiii.collapsible_groups.client.widget.ConfirmDialog;
import com.starskyxiii.collapsible_groups.client.widget.CommandPress;
import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupSortMode;
import com.starskyxiii.collapsible_groups.client.manager.model.SavedGroupContext;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class GroupManagerScreen extends Screen implements GroupManagerParent {
    private final CommandPress<ConfirmDialog.Action> dialogPress = new CommandPress<>();
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
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int SEGMENT_HEIGHT = 18;
	private static final int SEGMENT_TEXT_PADDING = 16;
	private static final int SEARCH_FIELD_TEXT_PAD = 5;
	private static final int SORT_BUTTON_SIZE = 20;
	private static final int SORT_BUTTON_GAP = 4;
	private static final int SORT_MENU_PADDING = 4;
	private static final int SORT_MENU_OPTION_GAP = 4;
	private static final int SORT_MENU_ROW_HEIGHT = 18;
	private static final long SAVED_CARD_HIGHLIGHT_MS = 1200L;
	private static final int MINI_SCROLLBAR_GAP = 4;
	private static final int MINI_SCROLLBAR_WIDTH = 5;
	private static final int BATCH_SELECTED_OVERLAY = 0x3340B96A;

	private final Screen previousScreen;
	private final boolean kubeJsLoaded;
	private final Map<String, Integer> previewScrollOffsets = new HashMap<>();
	private List<GroupManagerCard> allCards = new ArrayList<>();
	private List<GroupManagerCard> filteredCards = new ArrayList<>();
	private int cols = 1;
	private int scrollPixelOffset = 0;

	private GroupUiState.ManagerSourceFilter sourceFilter = GroupUiState.managerSourceFilter();
	private GroupSortMode sortMode = GroupUiState.managerSortMode();
	private EditBox searchField;
	private String searchQuery = "";
    private String categoryFilter = GroupUiState.managerCategoryFilter();
    private final GroupCategoryStore categories = GroupCategoryStore.current();
    private CategoryPopup categoryPopup;
    private boolean movingCategories;
    private List<String> categoryMoveIds = List.of();
    private String draftCategory;
    private ManagerHeaderLayout headerLayout;
    private ManagerContentLayout contentLayout;
    private final CategorySidebar categorySidebar = new CategorySidebar();
    private boolean sidebarOpen = GroupUiState.managerSidebarOpen();
    private boolean drawerOpen;
    private boolean sidebarFocused;
    private boolean categoryButtonHeld;
    private boolean categoryToggleFocused;
    private PressedCardAction heldCardAction;
    private boolean settingsButtonHeld;
    private boolean batchMenuOpen;
    private int batchMenuFocus = -1;
    private int prunedSelectionCount;
    private long selectionNoticeUntil;
	private boolean backButtonHeld = false;
	private int heldSegmentIndex = -1;
	private boolean batchMode = false;
	private boolean batchToggleButtonHeld = false;
	private BatchSelectionState batchSelection = BatchSelectionState.empty();
	private BatchToolbarAction heldBatchToolbarAction = null;
	private boolean newGroupButtonHeld = false;
	private boolean sortButtonHeld = false;
	private boolean sortMenuOpen = false;
	private boolean showEmptyGroups = GroupUiState.managerShowEmpty();
	private boolean heldShowEmpty;
	private int hiddenEmptyCount;
	private Component operationMessage;
	private String lastSavedGroupId;
	private GroupSortMode heldSortMode = null;
	private boolean isDraggingScrollbar = false;
	private String heldSwitchGroupId = null;
	private boolean activeManager;
	private boolean builtinsEnabled = true;
	private SavedGroupContext pendingSavedContext;
	private final List<com.starskyxiii.collapsible_groups.group.GroupChangeEvent.Subscription> subscriptions = new ArrayList<>();
	private String suppressedSwitchHoverGroupId = null;
	private double sbDragStartMouseY;
	private int sbDragStartPixelOffset;
	private Component pendingTooltip;
	private PendingDelete pendingDelete = null;
	private PendingBatchDelete pendingBatchDelete = null;
	private String highlightedSavedGroupId = null;
	private long highlightedSavedUntil = 0L;
	private boolean generationPending;
	private final PublishedGenerationRefresh publicationRefresh = new PublishedGenerationRefresh();

	public GroupManagerScreen(Screen previousScreen) {
		super(Component.translatable(ModTranslationKeys.SCREEN_TITLE));
		this.previousScreen = previousScreen;
		this.kubeJsLoaded = Services.PLATFORM.isModLoaded("kubejs");
	}

	@Override
	protected void init() {
        ScrollAnchor anchor = scrollAnchor();
        clearTransientInputState();
        categories.reload();
        validateCategoryFilter();
		activeManager = true;
		if (subscriptions.isEmpty()) {
			for (var kind : com.starskyxiii.collapsible_groups.group.GroupChangeEvent.Kind.values()) {
				subscriptions.add(com.starskyxiii.collapsible_groups.group.GroupChangeEvent.subscribe(kind,
					() -> Minecraft.getInstance().tell(this::refreshIfActive)));
			}
		}
		if (!kubeJsLoaded && sourceFilter == GroupUiState.ManagerSourceFilter.KUBEJS) {
			sourceFilter = GroupUiState.ManagerSourceFilter.ALL;
		}
		clearWidgets();
		searchField = null;
		rebuildCards();
		calcLayout();
		createSearchField();
        restoreAnchor(anchor);
		applySavedContext();
		if (!searchFieldLayout().visible()) closeSortMenu();
		if (highlightedSavedGroupId != null) ensureCardVisible(highlightedSavedGroupId);
	}

	private void rebuildCards() {
        categoryPopup = null;
        validateCategoryFilter();
		suppressedSwitchHoverGroupId = null;
		long traceStart = PerformanceTrace.begin();
		ViewerGroupIndex index = ViewerLifecycleCoordinator.global().activeAdapter()
			.map(adapter -> adapter.groupIndex()).orElse(UnavailableViewerGroupIndex.INSTANCE);
		var repository = GroupRepository.readSnapshot();
		var display = index.displaySnapshot();
		builtinsEnabled = repository.builtinsEnabled();
		GroupManagerCardAssembler.Result result = GroupManagerCardAssembler.build(repository, display);
		this.generationPending = result.generationPending();
		publicationRefresh.schedule(display.pending(), display.readiness(),
			command -> Minecraft.getInstance().tell(command), this::refreshIfActive);
		allCards = new ArrayList<>(result.cards());
		previewScrollOffsets.keySet().retainAll(
			allCards.stream().map(GroupManagerCard::id).collect(Collectors.toSet()));
		rebuildFilteredCards();
		PerformanceTrace.logIfSlow("GroupManagerScreen.rebuildCards", traceStart, 20,
			"groups=" + allCards.size()
				+ " totalItems=" + result.totalItems()
				+ " totalFluids=" + result.totalFluids()
				+ " totalGeneric=" + result.totalGeneric());
	}

	private void refreshIfActive() {
        if (activeManager && Minecraft.getInstance().screen == this) {
            ScrollAnchor anchor = scrollAnchor();
            clearTransientInputState();
            rebuildCards();
            relayout(anchor);
        }
	}

	@Override
	public void removed() {
        categoryPopup = null;
		clearTransientInputState();
		activeManager = false;
		subscriptions.forEach(com.starskyxiii.collapsible_groups.group.GroupChangeEvent.Subscription::close);
		subscriptions.clear();
		publicationRefresh.clear();
		super.removed();
	}

	private void rebuildFilteredCards() {
        heldCardAction = null;
		List<GroupManagerCard> matched = allCards.stream().filter(this::matchesCurrentFilters).toList();
		GroupManagerVisibility.Result<GroupManagerCard> visibility = GroupManagerVisibility.filter(matched, showEmptyGroups,
			GroupManagerCard::evaluation);
		hiddenEmptyCount = visibility.hiddenEmpty();
		filteredCards = GroupManagerSort.apply(visibility.visible(), sortMode,
			card -> localizedDisplayName(card).getString(), GroupManagerCard::id);
		scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
        pruneBatchSelectionToFilteredCards();
        refreshCategorySidebar();
    }

	private boolean matchesCurrentFilters(GroupManagerCard card) {
		return (CategoryChoices.ALL.equals(categoryFilter) || categoryFilter.equals(categoryFilterId(card)))
            && GroupManagerSearchMatcher.matches(sourceFilter, searchQuery, searchFields(card));
	}

    private String categoryOf(GroupManagerCard card) {
        var resources = GroupRepository.resourceData();
        var origin = card.source() == GroupSource.USER || card.source() == GroupSource.KUBEJS ? null : resources.origin(card.id());
        return categories.snapshot().effectiveCategory(card.id(), origin, resources);
    }

    private String categoryFilterId(GroupManagerCard card) {
        String id = categoryOf(card);
        return id == null ? CategoryChoices.UNCATEGORIZED : id;
    }

    private void validateCategoryFilter() {
        if (CategoryChoices.ALL.equals(categoryFilter) || CategoryChoices.UNCATEGORIZED.equals(categoryFilter)) return;
        if (CategoryChoices.available(categories.snapshot(), GroupRepository.resourceData()).stream()
            .noneMatch(entry -> entry.id().equals(categoryFilter))) {
            categoryFilter = CategoryChoices.ALL;
            GroupUiState.setManagerCategoryFilter(categoryFilter);
        }
    }

    private void openCategoryPopup() {
        Rect anchor = headerLayout.secondary();
        clearTransientInputState();
        blurSearchField();
        movingCategories = true;
        categoryMoveIds = selectedVisibleCards().stream().map(GroupManagerCard::id).toList();
        List<CategoryChoices.Entry> entries = new ArrayList<>();
        entries.add(new CategoryChoices.Entry(CategoryChoices.UNCATEGORIZED, CategoryChoices.label("uncategorized")));
        entries.add(new CategoryChoices.Entry(CategoryChoices.FOLLOW_SOURCE, CategoryChoices.label("follow_source")));
        entries.addAll(CategoryChoices.available(categories.snapshot(), GroupRepository.resourceData()));
        categoryPopup = new CategoryPopup(entries, null, anchor.x(), anchor.bottom(), this.width, this.height);
    }

    private void refreshCategorySidebar() {
        List<CategoryChoices.Entry> entries = new ArrayList<>();
        entries.add(new CategoryChoices.Entry(CategoryChoices.ALL, CategoryChoices.label("all")));
        entries.add(new CategoryChoices.Entry(CategoryChoices.UNCATEGORIZED, CategoryChoices.label("uncategorized")));
        entries.addAll(CategoryChoices.available(categories.snapshot(), GroupRepository.resourceData()));
        categorySidebar.entries(entries, categoryFilter);
    }

    private boolean sidebarVisible() {
        return contentLayout != null && (contentLayout.dockable() ? sidebarOpen : drawerOpen);
    }

    private void toggleCategorySidebar() {
        ScrollAnchor anchor = scrollAnchor();
        boolean open = drawerOpen;
        clearTransientInputState();
        blurSearchField();
        if (contentLayout.dockable()) {
            sidebarOpen = !sidebarOpen;
            GroupUiState.setManagerSidebarOpen(sidebarOpen);
        } else drawerOpen = !open;
        relayout(anchor);
        sidebarFocused = sidebarVisible();
        categoryToggleFocused = !sidebarFocused;
        if (sidebarFocused) categorySidebar.focusSelection();
    }

    private void browseCategory(CategoryChoices.Entry entry) {
        movingCategories = false;
        if (drawerOpen) closeCategoryDrawer();
        categorySidebar.cancelPress();
        chooseCategory(entry);
    }

    private boolean releaseCardAction(double mouseX, double mouseY) {
        PressedCardAction held = heldCardAction;
        heldCardAction = null;
        if (held == null) return false;
        if (batchMode || !isInsideCardViewport(mouseX, mouseY)) return true;
        for (int i = 0; i < filteredCards.size(); i++) {
            GroupManagerCard card = filteredCards.get(i);
            if (!held.groupId().equals(card.id())) continue;
            int[] pos = cardPos(i);
            GroupAction target = null;
            if (isMouseOver(mouseX, mouseY, editButtonX(pos[0]), pos[1] + CARD_FOOTER_Y, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT))
                target = card.actionEligibility().canRequest(GroupAction.EDIT) ? GroupAction.EDIT : GroupAction.COPY_AS_CUSTOM;
            else if (isMouseOver(mouseX, mouseY, deleteButtonX(pos[0]), pos[1] + CARD_FOOTER_Y, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT))
                target = GroupAction.DELETE;
            GroupAction action = held.release(card.id(), target, true, card.actionEligibility(), Screen.hasShiftDown());
            if (action == GroupAction.EDIT) openEditor(card.group());
            else if (action == GroupAction.COPY_AS_CUSTOM) {
                if (!executeCopyAsCustom(card.id())) operationMessage = Component.translatable("collapsible_groups.manager.operation_failed");
            } else if (action == GroupAction.DELETE) openDeleteDialog(card);
            else if (action == GroupAction.SHIFT_DELETE) executeSingleDelete(card.id(), action);
            return true;
        }
        return true;
    }

    private void closeCategoryDrawer() {
        clearTransientInputState();
        categoryToggleFocused = true;
    }

    private void chooseCategory(CategoryChoices.Entry selected) {
        if (CategoryChoices.MANAGE.equals(selected.id())) {
            categoryPopup = null;
            Minecraft.getInstance().setScreen(new CategoryManagerScreen(this));
            return;
        }
        if (movingCategories) {
            var ids = categoryMoveIds;
            if (ids.isEmpty() || ids.stream().anyMatch(id -> GroupRepository.findById(id).isEmpty())) {
                categoryPopup = null;
                operationMessage = CategoryChoices.label("save_failed");
                return;
            }
            String target = CategoryChoices.UNCATEGORIZED.equals(selected.id()) ? null : selected.id();
            boolean saved = categories.update(preferences -> CategoryChoices.FOLLOW_SOURCE.equals(target)
                ? preferences.followSource(ids) : preferences.assign(ids, target));
            operationMessage = CategoryChoices.label(saved ? "moved" : "save_failed");
            if (!saved) { categoryPopup = null; return; }
            batchSelection = batchSelection.clear();
        } else {
            categoryFilter = selected.id();
            GroupUiState.setManagerCategoryFilter(categoryFilter);
        }
        categoryPopup = null;
        rebuildFilteredCards();
    }

	private GroupManagerSearchMatcher.SearchFields searchFields(GroupManagerCard card) {
		return new GroupManagerSearchMatcher.SearchFields(
			localizedDisplayName(card).getString(),
			card.displayName(),
			card.id(),
			card.source(),
			sourceSearchLabel(card.source()),
            CategoryChoices.name(categoryOf(card), categories.snapshot(), GroupRepository.resourceData()).getString(),
            categoryOf(card)
		);
	}

    private void calcLayout() {
        List<Integer> widths = new ArrayList<>();
        for (var filter : segmentFilters()) widths.add(Math.max(32, font.width(segmentLabel(filter)) + SEGMENT_TEXT_PADDING));
        int primary = Math.max(font.width(Component.translatable(ModTranslationKeys.MANAGER_BATCH_SELECT)),
            font.width(Component.translatable(ModTranslationKeys.MANAGER_BATCH_DONE))) + 16;
        int secondary = Math.max(font.width(Component.translatable(ModTranslationKeys.MANAGER_BTN_NEW_GROUP)),
            font.width(Component.translatable("collapsible_groups.manager.batch_actions").append(" ▼"))) + 16;
        int back = Math.max(50, font.width(Component.translatable(ModTranslationKeys.MANAGER_BTN_BACK)) + 12);
        headerLayout = ManagerHeaderLayout.create(width, widths, back, Math.max(60, primary), Math.max(80, secondary));
        contentLayout = ManagerContentLayout.create(width, height, headerHeight(), sidebarOpen,
            font.width(Component.translatable("collapsible_groups.manager.settings")));
        categorySidebar.layout(contentLayout.sidebar());
        cols = contentLayout.columns();
        scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
    }

    private void relayout(ScrollAnchor anchor) {
        boolean focused = searchField != null && searchField.isFocused();
        calcLayout();
        clearWidgets();
        createSearchField();
        if (focused) { setFocused(searchField); searchField.setFocused(true); }
        restoreAnchor(anchor);
    }

    private ScrollAnchor scrollAnchor() {
        if (filteredCards.isEmpty()) return new ScrollAnchor(null, 0, scrollPixelOffset);
        int index = Math.min(filteredCards.size() - 1, Math.max(0, scrollPixelOffset / (CARD_HEIGHT + CARD_PADDING) * cols));
        return new ScrollAnchor(filteredCards.get(index).id(), scrollPixelOffset % (CARD_HEIGHT + CARD_PADDING), scrollPixelOffset);
    }

    private void restoreAnchor(ScrollAnchor anchor) {
        int offset = anchor.fallback();
        for (int i = 0; i < filteredCards.size(); i++) if (filteredCards.get(i).id().equals(anchor.id())) {
            offset = i / cols * (CARD_HEIGHT + CARD_PADDING) + anchor.withinRow();
            break;
        }
        scrollPixelOffset = clamp(offset, 0, maxScrollPixels());
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
		searchField.setTextColor(UiPalette.TEXT_PRIMARY);
		searchField.setTextColorUneditable(UiPalette.TEXT_DISABLED);
		searchField.setHint(Component.translatable(ModTranslationKeys.MANAGER_SEARCH_HINT));
		searchField.setValue(searchQuery);
		searchField.setResponder(this::applySearchQuery);
		addRenderableWidget(searchField);
	}

	private int searchFieldTextY(SearchFieldLayout layout) {
		return UiSkinRenderer.textFieldTextY(font, layout.y(), layout.height()) + 1;
	}

	private void applySearchQuery(String value) {
		String next = value == null ? "" : value;
		if (searchQuery.equals(next)) return;
		searchQuery = next;
		clearTransientInputState();
		rebuildFilteredCards();
	}

	private int headerHeight() {
		return (headerLayout == null ? HEADER_HEIGHT : headerLayout.height()) + (GroupRepository.resourceData().problems().isEmpty() ? 0 : 16);
	}

	private SearchFieldLayout searchFieldLayout() {
        var rect = headerLayout.search();
        return new SearchFieldLayout(rect.x(), rect.y(), rect.width(), rect.height(), rect.width() > 0, false);
	}

	private int sortButtonX() {
		SearchFieldLayout layout = searchFieldLayout();
		return layout.x() + layout.width() + SORT_BUTTON_GAP;
	}

	private void renderSearchFieldChrome(GuiGraphics g, int mouseX, int mouseY) {
		SearchFieldLayout layout = searchFieldLayout();
		if (!layout.visible()) return;
		boolean hovered = isMouseOver(mouseX, mouseY, layout.x(), layout.y(), layout.width(), layout.height());
		boolean focused = searchField != null && searchField.isFocused();
		int outline = focused ? UiPalette.OUTLINE_SELECTED : hovered ? UiPalette.OUTLINE_HOVER : UiPalette.OUTLINE_DARK;
		g.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + layout.height(), UiPalette.SURFACE_DARK);
		g.fill(layout.x() + 1, layout.y() + 1,
			layout.x() + layout.width() - 1, layout.y() + layout.height() - 1, UiPalette.SURFACE);
		UiSkinRenderer.drawOutline(g, layout.x(), layout.y(), layout.width(), layout.height(), outline);
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
        int previous = batchSelection.selectedCount();
        batchSelection = batchSelection.pruneTo(filteredCards.stream().map(GroupManagerCard::id).toList());
        if (batchSelection.selectedCount() < previous) {
            prunedSelectionCount = previous - batchSelection.selectedCount();
            selectionNoticeUntil = System.currentTimeMillis() + 3500;
        }
	}

	private void openEditor(GroupDefinition group) {
        draftCategory = CategoryChoices.ALL.equals(categoryFilter) || CategoryChoices.UNCATEGORIZED.equals(categoryFilter) ? null : categoryFilter;
		Minecraft.getInstance().setScreen(new GroupEditorScreen(this, group));
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
		g.fill(0, 0, this.width, this.height, UiPalette.SCREEN_SCRIM);
	}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        renderBackground(g, mouseX, mouseY, partialTicks);
        pendingTooltip = null;
        boolean popup = hasPendingDialog() || categoryPopup != null || batchMenuOpen || sortMenuOpen;
        int surfaceMouseX = popup || drawerOpen ? Integer.MIN_VALUE : mouseX;
        Rect content = contentLayout.content();
        UiSkinRenderer.drawScreenBars(g, width, height, headerHeight(), FOOTER_HEIGHT);
        g.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        if (generationPending && filteredCards.isEmpty()) {
            renderCenteredState(g, content.y(), content.bottom(), Component.translatable(ModTranslationKeys.EDITOR_LOADING));
        } else {
            for (int i = 0; i < filteredCards.size(); i++) renderCard(g, i, surfaceMouseX, mouseY);
            if (filteredCards.isEmpty()) renderEmptyState(g, content.y(), content.bottom());
        }
        g.disableScissor();
        renderScrollbar(g);
        renderHeaderButtons(g, surfaceMouseX, mouseY);
        g.drawString(font, ellipsize(title.getString(), headerLayout.titleWidth()), headerLayout.titleX(), 7, UiPalette.TEXT_PRIMARY, false);
        Component count = batchMode
            ? Component.translatable("collapsible_groups.manager.selected_count", batchSelection.selectedCount())
            : filteredCards.size() == allCards.size()
                ? Component.translatable(ModTranslationKeys.MANAGER_COUNT_ALL, allCards.size())
                : Component.translatable(ModTranslationKeys.MANAGER_COUNT_FILTERED, filteredCards.size(), allCards.size());
        Component fullCount = count;
        if (!sidebarVisible()) {
            Component category = CategoryChoices.name(categoryFilter, categories.snapshot(), GroupRepository.resourceData());
            fullCount = Component.translatable("collapsible_groups.manager.category_count", category, count);
            int categoryWidth = headerLayout.titleWidth() - font.width(Component.translatable("collapsible_groups.manager.category_count", "", count));
            if (categoryWidth > 0) count = Component.translatable("collapsible_groups.manager.category_count", ellipsize(category.getString(), categoryWidth), count);
        }
        String shownCount = ellipsize(count.getString(), headerLayout.titleWidth());
        g.drawString(font, shownCount, headerLayout.titleX(), 18, UiPalette.TEXT_MUTED, false);
        if (!shownCount.equals(fullCount.getString()) && isMouseOver(surfaceMouseX, mouseY, headerLayout.titleX(), 18, headerLayout.titleWidth(), font.lineHeight)) pendingTooltip = fullCount;
        renderFooter(g, surfaceMouseX, mouseY);
        renderSourceProblems(g, surfaceMouseX, mouseY);
        for (var child : children()) if (child instanceof Renderable renderable) renderable.render(g, surfaceMouseX, mouseY, partialTicks);
        if (sidebarVisible()) {
            if (drawerOpen) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.fill(content.x(), content.y(), content.right(), content.bottom(), UiPalette.DISABLED_OVERLAY);
            }
            Component tooltip = categorySidebar.render(g, font, popup ? Integer.MIN_VALUE : mouseX, mouseY, sidebarFocused && !popup && minecraft.getLastInputType().isKeyboard());
            if (tooltip != null) pendingTooltip = tooltip;
            renderCategoryToggle(g, popup ? Integer.MIN_VALUE : mouseX, mouseY);
            if (drawerOpen) g.pose().popPose();
        } else {
            Rect rail = contentLayout.rail();
            g.fill(rail.x(), rail.y(), rail.right(), rail.bottom(), UiPalette.SURFACE);
            g.fill(rail.right() - 1, rail.y(), rail.right(), rail.bottom(), UiPalette.OUTLINE_DARK);
            renderCategoryToggle(g, surfaceMouseX, mouseY);
        }
        if (hasPendingDialog()) {
            pendingTooltip = null;
            renderPendingDialog(g, mouseX, mouseY);
        } else if (categoryPopup != null) {
            pendingTooltip = null;
            categoryPopup.render(g, font, mouseX, mouseY);
        } else if (batchMenuOpen) {
            pendingTooltip = null;
            renderBatchMenu(g, mouseX, mouseY);
        } else if (sortMenuOpen) {
            pendingTooltip = null;
            renderSortMenu(g, mouseX, mouseY);
        } else if (pendingTooltip != null) {
            g.renderTooltip(font, font.split(pendingTooltip, Math.max(80, Math.min(360, width - 24))), mouseX, mouseY);
        }
    }

    private void renderHeaderButtons(GuiGraphics g, int mouseX, int mouseY) {
        renderRectButton(g, headerLayout.back(), Component.translatable(ModTranslationKeys.MANAGER_BTN_BACK).getString(), mouseX, mouseY, backButtonHeld, false);
        renderSegmentedFilter(g, mouseX, mouseY);
        renderSearchFieldChrome(g, mouseX, mouseY);
        renderSortButton(g, mouseX, mouseY);
        renderRectButton(g, headerLayout.primary(), Component.translatable(batchMode ? ModTranslationKeys.MANAGER_BATCH_DONE : ModTranslationKeys.MANAGER_BATCH_SELECT).getString(),
            mouseX, mouseY, batchToggleButtonHeld, false);
        String secondary = batchMode ? Component.translatable("collapsible_groups.manager.batch_actions").getString() + " ▼"
            : Component.translatable(ModTranslationKeys.MANAGER_BTN_NEW_GROUP).getString();
        renderRectButton(g, headerLayout.secondary(), secondary, mouseX, mouseY, newGroupButtonHeld, batchMenuOpen);
    }

    private void renderRectButton(GuiGraphics g, Rect rect, String label, int mouseX, int mouseY, boolean held, boolean selected) {
        boolean hover = rect.contains(mouseX, mouseY);
        UiSkinRenderer.ButtonState state = UiSkinRenderer.buttonState(true, selected, hover, held);
        UiSkinRenderer.drawButton(g, font, rect.x(), rect.y(), rect.width(), rect.height(), ellipsize(label, rect.width() - 10), state);
        if (hover && font.width(label) > rect.width() - 10) pendingTooltip = Component.literal(label);
    }

    private void renderCategoryToggle(GuiGraphics g, int mouseX, int mouseY) {
        Rect rect = contentLayout.categoryToggle(sidebarVisible());
        if (rect.height() < 20) return;
        boolean hover = rect.contains(mouseX, mouseY);
        boolean focused = categoryToggleFocused && mouseX != Integer.MIN_VALUE && minecraft.getLastInputType().isKeyboard();
        UiSkinRenderer.ButtonState state = UiSkinRenderer.buttonState(true, false, hover || focused, categoryButtonHeld && hover);
        UiSkinRenderer.drawToolbarChevronButton(g, rect.x(), rect.y(), !sidebarVisible(), state);
        if (focused) UiSkinRenderer.drawOutline(g, rect.x(), rect.y(), rect.width(), rect.height(), UiPalette.OUTLINE_SELECTED);
        if (hover) pendingTooltip = Component.translatable(sidebarVisible()
            ? "collapsible_groups.manager.hide_categories" : "collapsible_groups.manager.show_categories",
            CategoryChoices.name(categoryFilter, categories.snapshot(), GroupRepository.resourceData()));
    }

    private void renderFooter(GuiGraphics g, int mouseX, int mouseY) {
        Rect hint = contentLayout.footerHint();
        Component message = footerText();
        String shown = ellipsize(message.getString(), hint.width());
        g.drawString(font, shown, hint.x(), UiSkinRenderer.centeredTextY(font, hint.y(), hint.height()), UiPalette.TEXT_HINT, false);
        if (hint.contains(mouseX, mouseY) && !shown.equals(message.getString())) pendingTooltip = message;
        Rect settings = contentLayout.settings();
        boolean hovered = settings.contains(mouseX, mouseY);
        String label = Component.translatable("collapsible_groups.manager.settings").getString();
        UiSkinRenderer.ButtonState state = UiSkinRenderer.buttonState(true, false, hovered, settingsButtonHeld);
        g.drawString(font, ellipsize(label, settings.width() - 24), settings.x(),
            UiSkinRenderer.centeredTextY(font, settings.y(), settings.height()) + UiSkinRenderer.toolbarButtonOffset(state), hovered ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_MUTED, false);
        renderIconButton(g, settings.right() - 18, settings.y(), 18, 20, UiSkinRenderer.ICON_EDIT,
            true, hovered, settingsButtonHeld && hovered);
    }

    private String ellipsize(String value, int width) {
        if (width <= 0) return "";
        if (width < font.width("…")) return font.plainSubstrByWidth(value, width);
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
    }

    private BatchMenuLayout batchMenuLayout() {
        int width = 96;
        for (BatchToolbarAction action : BatchToolbarAction.values()) width = Math.max(width, font.width(batchActionLabel(action)) + 20);
        width = Math.min(width, this.width - 12);
        int height = BatchToolbarAction.values().length * 20 + 8;
        Rect anchor = headerLayout.secondary();
        return new BatchMenuLayout(new Rect(clamp(anchor.right() - width, 6, this.width - width - 6),
            clamp(anchor.bottom() + 2, 6, Math.max(6, this.height - height - 6)), width, height));
    }

    private void renderBatchMenu(GuiGraphics g, int mouseX, int mouseY) {
        BatchMenuLayout layout = batchMenuLayout();
        Rect bounds = layout.bounds();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), UiPalette.SURFACE);
        UiSkinRenderer.drawOutline(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), UiPalette.OUTLINE);
        BatchActionEligibility eligibility = currentBatchEligibility();
        for (BatchToolbarAction action : BatchToolbarAction.values()) {
            Rect row = layout.row(action.ordinal());
            boolean active = batchActionActive(action, eligibility);
            boolean hovered = row.contains(mouseX, mouseY);
            UiSkinRenderer.ButtonState state = UiSkinRenderer.buttonState(active, false, hovered, heldBatchToolbarAction == action);
            UiSkinRenderer.drawButton(g, font, row.x(), row.y(), row.width(), row.height(), batchActionLabel(action), state);
            if (batchMenuFocus == action.ordinal()) {
                UiSkinRenderer.drawOutline(g, row.x(), row.y(), row.width(), row.height(), UiPalette.OUTLINE_HOVER);
            }
        }
        g.pose().popPose();
    }

    private void executeBatchAction(BatchToolbarAction action) {
        if (!batchActionActive(action, currentBatchEligibility())) return;
        clearTransientInputState();
        switch (action) {
            case SELECT_ALL_RESULTS -> toggleSelectAllResults();
            case ENABLE -> executeBatchSetEnabled(true);
            case DISABLE -> executeBatchSetEnabled(false);
            case DELETE -> openBatchDeleteDialog();
            case MOVE -> openCategoryPopup();
        }
    }

	private void renderSortButton(GuiGraphics g, int mouseX, int mouseY) {
		SearchFieldLayout layout = searchFieldLayout();
		if (!layout.visible()) return;
		int x = sortButtonX();
		boolean hovered = isMouseOver(mouseX, mouseY, x, searchFieldLayout().y(), SORT_BUTTON_SIZE, SORT_BUTTON_SIZE);
		UiSkinRenderer.ButtonState state = sortMenuOpen
			? sortButtonHeld && hovered ? UiSkinRenderer.ButtonState.SELECTED_PRESSED
			: hovered ? UiSkinRenderer.ButtonState.SELECTED_HOVERED : UiSkinRenderer.ButtonState.SELECTED
			: sortButtonHeld && hovered ? UiSkinRenderer.ButtonState.PRESSED
			: hovered ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL;
		UiSkinRenderer.drawToolbarIconButton(g, x, searchFieldLayout().y(), SORT_BUTTON_SIZE, SORT_BUTTON_SIZE,
			UiSkinRenderer.ICON_SORT, state);
		if (hovered && !sortMenuOpen) {
			pendingTooltip = Component.translatable(ModTranslationKeys.MANAGER_SORT_TOOLTIP,
				Component.translatable(sortLabelKey(sortMode)));
		}
	}

	private void renderSortMenu(GuiGraphics g, int mouseX, int mouseY) {
		SortMenuLayout layout = sortMenuLayout();
		int x = layout.contentX();
		int w = layout.contentWidth();
		g.pose().pushPose();
		g.pose().translate(0, 0, 350);
		g.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + layout.height(), UiPalette.SURFACE);
		UiSkinRenderer.drawOutline(g, layout.x(), layout.y(), layout.width(), layout.height(), UiPalette.OUTLINE);
		for (int i = 0; i < GroupSortMode.values().length; i++) {
			GroupSortMode mode = GroupSortMode.values()[i];
			int rowY = layout.rowY(i);
			boolean hovered = isMouseOver(mouseX, mouseY, x, rowY, w, SORT_MENU_ROW_HEIGHT);
			boolean selected = sortMode == mode;
			boolean pressed = heldSortMode == mode && hovered;
			UiSkinRenderer.ButtonState state = selected
				? pressed ? UiSkinRenderer.ButtonState.SELECTED_PRESSED
				: hovered ? UiSkinRenderer.ButtonState.SELECTED_HOVERED : UiSkinRenderer.ButtonState.SELECTED
				: pressed ? UiSkinRenderer.ButtonState.PRESSED
				: hovered ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL;
			UiSkinRenderer.drawSegment(g, font, x, rowY, w, SORT_MENU_ROW_HEIGHT,
				Component.translatable(sortLabelKey(mode)).getString(), state);
		}
		int optionY = layout.optionY();
		boolean optionHover = isMouseOver(mouseX, mouseY, x, optionY, w, SORT_MENU_ROW_HEIGHT);
		if (optionHover) g.fill(x, optionY, x + w, optionY + SORT_MENU_ROW_HEIGHT, UiPalette.SURFACE_HOVER_OVERLAY);
		UiSkinRenderer.drawCheckbox(g, x + 5, optionY + 2, showEmptyGroups, optionHover);
		String label = Component.translatable(ModTranslationKeys.MANAGER_SHOW_EMPTY).getString();
		g.drawString(font, font.plainSubstrByWidth(label, Math.max(0, w - 29)), x + 24,
			UiSkinRenderer.centeredTextY(font, optionY, SORT_MENU_ROW_HEIGHT), UiPalette.TEXT_PRIMARY, false);
		g.pose().popPose();
	}

	private boolean hoveredShowEmpty(double x, double y) {
		if (!sortMenuOpen) return false;
		SortMenuLayout layout = sortMenuLayout();
		return isMouseOver(x, y, layout.contentX(), layout.optionY(), layout.contentWidth(), SORT_MENU_ROW_HEIGHT);
	}

	private boolean isOverSortMenu(double x, double y) {
		return sortMenuOpen && searchFieldLayout().visible() && !hasPendingDialog() && sortMenuLayout().contains(x, y);
	}

	private void closeSortMenu() {
		sortMenuOpen = false;
		sortButtonHeld = false;
		heldSortMode = null;
		heldShowEmpty = false;
	}

	private void setShowEmptyGroups(boolean value) {
		showEmptyGroups = value;
		GroupUiState.setManagerShowEmpty(value);
		rebuildFilteredCards();
		if (value && lastSavedGroupId != null) ensureCardVisible(lastSavedGroupId);
	}

    private Component footerText() {
		if (operationMessage != null) return operationMessage;
        if (System.currentTimeMillis() < selectionNoticeUntil) return Component.translatable("collapsible_groups.manager.selection_pruned", prunedSelectionCount);
		GroupManagerCard saved = findCurrentCard(lastSavedGroupId);
		if (!showEmptyGroups && saved != null && saved.evaluation().empty()) {
			return Component.translatable(ModTranslationKeys.MANAGER_SAVED_EMPTY, localizedDisplayName(saved));
		}
		return hiddenEmptyCount > 0 ? Component.translatable(ModTranslationKeys.MANAGER_HIDDEN_EMPTY, hiddenEmptyCount)
			: Component.translatable(ModTranslationKeys.MANAGER_FOOTER_HINT);
	}

	private boolean savedGroupIsHiddenEmpty() {
		GroupManagerCard saved = findCurrentCard(lastSavedGroupId);
		return !showEmptyGroups && saved != null && saved.evaluation().empty();
	}

	private void renderSourceProblems(GuiGraphics g, int mouseX, int mouseY) {
		var data = GroupRepository.resourceData();
		if (data.problems().isEmpty()) return;
		int y = headerLayout.height() + 1;
		String key = data.stale() ? ModTranslationKeys.MANAGER_SOURCES_STALE : ModTranslationKeys.MANAGER_SOURCE_PROBLEMS;
		g.drawString(font, Component.translatable(key, data.problems().size()), 6, y, 0xFFFFB74D, false);
		if (isMouseOver(mouseX, mouseY, 0, y - 2, this.width, 16)) {
			var detail = Component.translatable(key, data.problems().size());
			data.problems().stream().limit(8).forEach(problem -> detail.append("\n" + problem.origin().location() + "\n" + problem.reason()));
			pendingTooltip = detail;
		}
	}

	private static String sortLabelKey(GroupSortMode mode) {
		return switch (mode) {
			case PRIORITY -> ModTranslationKeys.MANAGER_SORT_PRIORITY;
			case NAME_ASC -> ModTranslationKeys.MANAGER_SORT_NAME_ASC;
			case NAME_DESC -> ModTranslationKeys.MANAGER_SORT_NAME_DESC;
		};
	}

	private SortMenuLayout sortMenuLayout() {
		int contentWidth = font.width(Component.translatable(ModTranslationKeys.MANAGER_SHOW_EMPTY)) + 29;
		for (GroupSortMode mode : GroupSortMode.values()) {
			contentWidth = Math.max(contentWidth, font.width(Component.translatable(sortLabelKey(mode))) + 16);
		}
		int w = Math.min(contentWidth + SORT_MENU_PADDING * 2, Math.max(1, this.width - 12));
		int h = (GroupSortMode.values().length + 1) * SORT_MENU_ROW_HEIGHT + SORT_MENU_OPTION_GAP + SORT_MENU_PADDING * 2;
		int x = clamp(sortButtonX() + SORT_BUTTON_SIZE - w, 6, this.width - w - 6);
		int marginY = Math.min(6, Math.max(0, (this.height - h) / 2));
		int y = clamp(searchFieldLayout().y() + SORT_BUTTON_SIZE, marginY, Math.max(marginY, this.height - h - marginY));
		return new SortMenuLayout(x, y, w, h);
	}

    private void renderEmptyState(GuiGraphics g, int viewportTop, int viewportBottom) {
        boolean hasCategory = allCards.stream().anyMatch(card -> CategoryChoices.ALL.equals(categoryFilter) || categoryFilter.equals(categoryFilterId(card)));
        String key = hiddenEmptyCount > 0 ? "collapsible_groups.manager.empty_hidden"
            : !hasCategory && !CategoryChoices.ALL.equals(categoryFilter) ? "collapsible_groups.manager.empty_category" : ModTranslationKeys.MANAGER_EMPTY_SEARCH;
        renderCenteredState(g, viewportTop, viewportBottom, Component.translatable(key));
    }

    private void renderCenteredState(GuiGraphics g, int viewportTop, int viewportBottom, Component message) {
        Rect content = contentLayout.content();
        var lines = font.split(message, Math.max(1, content.width() - 24));
        int y = viewportTop + Math.max(0, (viewportBottom - viewportTop - lines.size() * (font.lineHeight + 2)) / 2);
        for (var line : lines) {
            g.drawString(font, line, content.x() + (content.width() - font.width(line)) / 2, y, UiPalette.TEXT_HINT, false);
            y += font.lineHeight + 2;
        }
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
        return headerLayout.sourceAt(mouseX, mouseY);
    }

	private void renderSegment(GuiGraphics g, GroupUiState.ManagerSourceFilter[] filters, int index,
	                           boolean selected, boolean hovered) {
		int x = segmentX(filters, index);
		int w = headerLayout.sources().get(index).width();
		boolean pressed = hovered && heldSegmentIndex == index;
		UiSkinRenderer.ButtonState state = selected
			? pressed ? UiSkinRenderer.ButtonState.SELECTED_PRESSED
			: hovered ? UiSkinRenderer.ButtonState.SELECTED_HOVERED : UiSkinRenderer.ButtonState.SELECTED
			: pressed ? UiSkinRenderer.ButtonState.PRESSED
			: hovered ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL;
		String label = segmentLabel(filters[index]);
        String shown = ellipsize(label, w - 4);
        UiSkinRenderer.drawSegment(g, font, x, headerLayout.sources().get(index).y(), w, SEGMENT_HEIGHT, shown, state);
        if (hovered && !shown.equals(label)) pendingTooltip = Component.literal(label);
	}

	private GroupUiState.ManagerSourceFilter[] segmentFilters() {
		return kubeJsLoaded
			? new GroupUiState.ManagerSourceFilter[] {
				GroupUiState.ManagerSourceFilter.ALL,
				GroupUiState.ManagerSourceFilter.USER,
				GroupUiState.ManagerSourceFilter.BUILTIN,
				GroupUiState.ManagerSourceFilter.RESOURCE_PACK,
				GroupUiState.ManagerSourceFilter.KUBEJS }
			: new GroupUiState.ManagerSourceFilter[] {
				GroupUiState.ManagerSourceFilter.ALL,
				GroupUiState.ManagerSourceFilter.USER,
				GroupUiState.ManagerSourceFilter.BUILTIN,
				GroupUiState.ManagerSourceFilter.RESOURCE_PACK };
	}

	private String segmentLabel(GroupUiState.ManagerSourceFilter filter) {
		return switch (filter) {
			case ALL -> Component.translatable(ModTranslationKeys.MANAGER_FILTER_ALL).getString();
			case USER -> Component.translatable(ModTranslationKeys.MANAGER_FILTER_USER).getString();
			case BUILTIN -> Component.translatable(ModTranslationKeys.MANAGER_BTN_FILTER_BUILTIN).getString();
			case KUBEJS -> Component.translatable(ModTranslationKeys.MANAGER_BTN_FILTER_KUBEJS).getString();
			case RESOURCE_PACK -> Component.translatable(ModTranslationKeys.MANAGER_SOURCE_RESOURCE_PACK).getString();
		};
	}

	private String sourceSearchLabel(GroupSource source) {
		return switch (source) {
			case USER -> Component.translatable(ModTranslationKeys.MANAGER_FILTER_USER).getString();
			case BUILTIN -> Component.translatable(ModTranslationKeys.MANAGER_BTN_FILTER_BUILTIN).getString();
			case KUBEJS -> Component.translatable(ModTranslationKeys.MANAGER_BTN_FILTER_KUBEJS).getString();
			case RESOURCE_PACK -> Component.translatable(ModTranslationKeys.MANAGER_SOURCE_RESOURCE_PACK).getString();
		};
	}



	private int segmentX(GroupUiState.ManagerSourceFilter[] filters, int index) {
        return headerLayout.sources().get(index).x();
	}

	private void renderCard(GuiGraphics g, int index, int mouseX, int mouseY) {
		GroupManagerCard card = filteredCards.get(index);
		int[] pos = cardPos(index);
		int x = pos[0];
		int y = pos[1];
		if (y + CARD_HEIGHT < headerHeight() || y > this.height - FOOTER_HEIGHT) return;

		boolean cardHover = isInsideCardViewport(mouseX, mouseY) && isMouseOver(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT);
		boolean batchSelected = batchMode && batchSelection.isSelected(card.id());
		boolean savedHighlight = card.id().equals(highlightedSavedGroupId)
			&& System.currentTimeMillis() < highlightedSavedUntil;
		if (!savedHighlight && card.id().equals(highlightedSavedGroupId)) highlightedSavedGroupId = null;
		int outlineColor = batchSelected || savedHighlight ? UiPalette.OUTLINE_SELECTED
			: cardHover ? UiPalette.OUTLINE_HOVER : UiPalette.OUTLINE_DARK;
		UiSkinRenderer.drawCard(g, x, y, CARD_WIDTH, CARD_HEIGHT, cardHover, outlineColor);

		renderHeaderPreview(g, card, x + 6, y + CARD_TITLE_Y, switchControlX(x) - 4, cardHover);
		renderCardPreview(g, card, x + 6, y + CARD_PREVIEW_Y, previewScrollOffsets.getOrDefault(card.id(), 0));
		renderSourceTab(g, card, x, y, cardHover ? mouseX : Integer.MIN_VALUE, mouseY);
		if (cardHover) recordEvaluationTooltip(card, x, y, mouseX, mouseY);
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
		g.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, UiPalette.DISABLED_OVERLAY);
		g.pose().popPose();
	}

	private void renderHeaderPreview(GuiGraphics g, GroupManagerCard card, int x, int y, int textRight, boolean hovered) {
		int headerColor = GroupThemeResolver.collapsedHeaderBackgroundColor(card.id());
		g.fill(x, y, x + HEADER_PREVIEW_SIZE, y + HEADER_PREVIEW_SIZE, headerColor);
		drawOutline(g, x, y, HEADER_PREVIEW_SIZE, HEADER_PREVIEW_SIZE, UiPalette.OUTLINE_DARK);
		renderStackedPreviewIcons(g, card.headerSource(), x, y);

		int textX = x + HEADER_PREVIEW_SIZE + 6;
		int maxTextWidth = Math.max(0, textRight - textX);
		renderScrollingText(g, localizedDisplayName(card).getString(), textX, y + 1,
			maxTextWidth, UiPalette.TEXT_PRIMARY, hovered);
		Component count = card.evaluation().status() == GroupEvaluation.Status.COMPLETE ? countLabel(card)
			: Component.translatable(evaluationLabel(card.evaluation().status()));
		g.drawString(font, font.plainSubstrByWidth(count.getString(), maxTextWidth), textX, y + 12, UiPalette.TEXT_MUTED, false);
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
		UiSkinRenderer.drawSlotGrid(g, previewX, previewY, PREVIEW_COLS, PREVIEW_ROWS, PREVIEW_CELL_PITCH);
		renderPreviewEntries(g, card.previewEntries(), previewX, previewY, rowOffset);
		int previewTotalRows = totalRowsForCard(card);
		if (previewTotalRows <= PREVIEW_ROWS) return;

		int sbX = previewX + PREVIEW_GRID_WIDTH + MINI_SCROLLBAR_GAP;
		int sbH = PREVIEW_GRID_HEIGHT;
		UiSkinRenderer.drawMiniScrollbar(g, sbX, previewY, sbH, PREVIEW_ROWS, previewTotalRows, rowOffset);
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
		g.fill(lastX, lastY, lastX + cellInner, lastY + cellInner, UiPalette.DISABLED_OVERLAY);
		g.drawString(font, more, lastX + (cellInner - font.width(more)) / 2,
			lastY + (cellInner - 8) / 2, UiPalette.TEXT_PRIMARY, false);
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

		boolean controlsInteractive = !batchMode && isInsideCardViewport(mouseX, mouseY);
		boolean rawSwitchHover = controlsInteractive && isMouseOver(mouseX, mouseY, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT);
		boolean switchHover = effectiveSwitchHover(card.id(), rawSwitchHover);
		boolean editHover = controlsInteractive && isMouseOver(mouseX, mouseY, editX, actionY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
		boolean deleteHover = controlsInteractive && isMouseOver(mouseX, mouseY, deleteX, actionY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
		boolean switchPressed = canSwitch && switchHover && card.id().equals(heldSwitchGroupId);
		boolean shiftDeleteArmed = controlsInteractive && deleteHover && canShiftDelete && Screen.hasShiftDown();

		g.pose().pushPose();
		g.pose().translate(0, 0, CARD_CONTROL_Z);
		UiSkinRenderer.drawSwitch(g, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT,
			card.group().enabled(), !batchMode && canSwitch, switchHover, switchPressed);
		renderIconButton(g, editX, actionY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT,
			UiSkinRenderer.ICON_EDIT, !batchMode && canUseMiddleAction, editHover,
            editHover && heldCardAction != null && heldCardAction.matches(card.id(), canEdit ? GroupAction.EDIT : GroupAction.COPY_AS_CUSTOM));
		renderIconButton(g, deleteX, actionY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT,
			UiSkinRenderer.ICON_DELETE, !batchMode && (canDelete || shiftDeleteArmed), deleteHover,
            shiftDeleteArmed || deleteHover && heldCardAction != null && heldCardAction.matches(card.id(), GroupAction.DELETE));
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
		drawOutline(g, x, y, CARD_WIDTH, CARD_HEIGHT, UiPalette.OUTLINE_SELECTED);
		g.pose().popPose();
	}

	private void renderSourceTab(GuiGraphics g, GroupManagerCard card, int cardX, int cardY, int mouseX, int mouseY) {
		String sourceLabel = switch (card.source()) {
			case BUILTIN -> Component.translatable(ModTranslationKeys.MANAGER_BADGE_BUILTIN).getString();
			case KUBEJS -> Component.translatable(ModTranslationKeys.MANAGER_BADGE_KUBEJS).getString();
			case RESOURCE_PACK -> Component.translatable(ModTranslationKeys.MANAGER_SOURCE_RESOURCE_PACK).getString();
			case USER -> sourceSearchLabel(GroupSource.USER);
		};
        String category = categoryOf(card);
        String label = category == null ? sourceLabel : Component.translatable(
            "collapsible_groups.category.badge." + card.source().name().toLowerCase(java.util.Locale.ROOT),
            CategoryChoices.name(category, categories.snapshot(), GroupRepository.resourceData())).getString();
        String shown = font.width(label) > 126 ? font.plainSubstrByWidth(label, 126 - font.width("…")) + "…" : label;
		int tabWidth = font.width(shown) + 10;
		int tabHeight = 14;
		int left = cardX + 1;
		int bottom = cardY + CARD_HEIGHT - 1;
		int top = bottom - tabHeight;
        if (!shown.equals(label) && isMouseOver(mouseX, mouseY, left, top, tabWidth, tabHeight)) pendingTooltip = Component.literal(label);
		if (card.source() == GroupSource.BUILTIN && !builtinsEnabled
			&& isMouseOver(mouseX, mouseY, left, top, tabWidth, tabHeight)) {
			pendingTooltip = Component.translatable(ModTranslationKeys.MANAGER_BUILTINS_DISABLED);
		}
		g.fill(left, top, left + tabWidth, bottom, UiPalette.SURFACE_DARK);
		g.fill(left, top, left + tabWidth, top + 1, UiPalette.OUTLINE_DARK);
		g.fill(left + tabWidth - 1, top, left + tabWidth, bottom, UiPalette.OUTLINE_DARK);
		g.drawString(font, shown, left + 5,
			UiSkinRenderer.centeredTextY(font, top, tabHeight), UiPalette.TEXT_MUTED, false);
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
		UiSkinRenderer.ButtonState state = !active
			? UiSkinRenderer.ButtonState.DISABLED
			: pressed ? UiSkinRenderer.ButtonState.PRESSED
			: hovered ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL;
		UiSkinRenderer.drawButton(g, font, x, y, w, h, label, state);
	}

	private void renderIconButton(GuiGraphics g, int x, int y, int w, int h,
	                              ResourceLocation icon, boolean active, boolean hovered, boolean pressed) {
		UiSkinRenderer.ButtonState state = !active
			? UiSkinRenderer.ButtonState.DISABLED
			: pressed ? UiSkinRenderer.ButtonState.PRESSED
			: hovered ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL;
		UiSkinRenderer.drawToolbarIconButton(g, x, y, w, h, icon, state);
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
			case SELECT_ALL_RESULTS -> !selectableResultIds().isEmpty();
			case ENABLE -> eligibility.canEnable();
			case DISABLE -> eligibility.canDisable();
			case DELETE -> eligibility.canDelete();
            case MOVE -> batchSelection.selectedCount() > 0 && categories.writable();
		};
	}

	private String batchActionLabel(BatchToolbarAction action) {
		String key = switch (action) {
			case SELECT_ALL_RESULTS -> allResultsSelected()
				? ModTranslationKeys.MANAGER_BATCH_CLEAR_RESULTS
				: ModTranslationKeys.MANAGER_BATCH_SELECT_RESULTS;
			case ENABLE -> ModTranslationKeys.MANAGER_BATCH_ENABLE;
			case DISABLE -> ModTranslationKeys.MANAGER_BATCH_DISABLE;
			case DELETE -> ModTranslationKeys.MANAGER_BATCH_DELETE;
            case MOVE -> "collapsible_groups.category.move";
		};
		return Component.translatable(key).getString();
	}



	private List<String> selectableResultIds() {
		return filteredCards.stream()
			.filter(card -> card.actionEligibility().canRequest(GroupAction.BATCH_SELECT))
			.map(GroupManagerCard::id)
			.toList();
	}

	private boolean allResultsSelected() {
		return batchSelection.containsAll(selectableResultIds());
	}

	private void toggleSelectAllResults() {
		List<String> ids = selectableResultIds();
		if (ids.isEmpty()) return;
		batchSelection = batchSelection.containsAll(ids)
			? batchSelection.deselectAll(ids)
			: batchSelection.selectAll(ids);
	}

    private BatchToolbarAction hoveredBatchAction(double mouseX, double mouseY) {
        if (!batchMenuOpen) return null;
        BatchMenuLayout menu = batchMenuLayout();
        for (BatchToolbarAction action : BatchToolbarAction.values()) if (menu.row(action.ordinal()).contains(mouseX, mouseY)) return action;
        return null;
    }

	private void renderPendingDialog(GuiGraphics g, int mouseX, int mouseY) {
		Component title = pendingBatchDelete != null
			? Component.translatable(ModTranslationKeys.MANAGER_BATCH_DELETE_DIALOG_TITLE,
				pendingBatchDelete.deletableCount())
			: Component.translatable(ModTranslationKeys.MANAGER_DELETE_DIALOG_TITLE);
		List<Component> bodyLines = List.of();
		if (pendingBatchDelete != null) {
			Component body = Component.translatable(ModTranslationKeys.MANAGER_BATCH_DELETE_DIALOG_BODY,
				pendingBatchDelete.deletableCount());
			if (pendingBatchDelete.skippedCount() > 0) {
				bodyLines = List.of(body, Component.translatable(ModTranslationKeys.MANAGER_BATCH_DELETE_DIALOG_SKIPPED,
					pendingBatchDelete.skippedCount()));
			} else {
				bodyLines = List.of(body);
			}
		} else if (pendingDelete != null) {
			bodyLines = List.of(Component.translatable(ModTranslationKeys.MANAGER_DELETE_DIALOG_BODY,
				pendingDelete.displayName()));
		}

		ConfirmDialog.render(g, font, this.width, this.height, title, bodyLines,
			Component.literal(deleteConfirmLabel()), Component.translatable(ModTranslationKeys.BUTTON_CANCEL),
			mouseX, mouseY, true, dialogPress.target());
	}

	private String deleteConfirmLabel() {
		if (pendingBatchDelete != null) {
			return Component.translatable(ModTranslationKeys.MANAGER_BATCH_DELETE_DIALOG_CONFIRM,
				pendingBatchDelete.deletableCount()).getString();
		}
		return Component.translatable(ModTranslationKeys.MANAGER_BTN_DELETE).getString();
	}

	private void drawOutline(GuiGraphics g, int x, int y, int width, int height, int color) {
		UiSkinRenderer.drawOutline(g, x, y, width, height, color);
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
		g.flush();
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
    public void mouseMoved(double mouseX, double mouseY) {
        if (batchMenuOpen) batchMenuFocus = -1;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasPendingDialog()) return handlePendingDialogClick(mouseX, mouseY, button);
        if (categoryPopup != null) {
            if (button == 0) {
                var selected = categoryPopup.clicked(mouseX, mouseY);
                if (selected != null) chooseCategory(selected);
                else if (!categoryPopup.contains(mouseX, mouseY)) clearTransientInputState();
            }
            return true;
        }
        if (batchMenuOpen) {
            batchMenuFocus = -1;
            if (button == 0) {
                BatchToolbarAction action = hoveredBatchAction(mouseX, mouseY);
                if (action != null) {
                    if (batchActionActive(action, currentBatchEligibility())) heldBatchToolbarAction = action;
                } else if (!batchMenuLayout().bounds().contains(mouseX, mouseY)) clearTransientInputState();
            }
            return true;
        }
        if (sortMenuOpen) {
            if (button == 0) {
                if (headerLayout.sort().contains(mouseX, mouseY)) sortButtonHeld = true;
                else if (isOverSortMenu(mouseX, mouseY)) {
                    heldShowEmpty = hoveredShowEmpty(mouseX, mouseY);
                    heldSortMode = hoveredSortMode(mouseX, mouseY);
                } else clearTransientInputState();
            }
            return true;
        }
        if (drawerOpen) {
            if (button == 0) {
                if (contentLayout.categoryToggle(sidebarVisible()).contains(mouseX, mouseY)) { categoryButtonHeld = true; categoryToggleFocused = true; sidebarFocused = false; }
                else if (categorySidebar.contains(mouseX, mouseY)) {
                    categorySidebar.press(mouseX, mouseY);
                    sidebarFocused = true;
                    categoryToggleFocused = false;
                } else closeCategoryDrawer();
            }
            return true;
        }
        if (button == 0) {
            clearTransientInputState();
            if (handleSearchFieldClick(mouseX, mouseY, button)) return true;
            blurSearchField();
            if (contentLayout.categoryToggle(sidebarVisible()).contains(mouseX, mouseY)) { categoryButtonHeld = true; categoryToggleFocused = true; return true; }
            if (sidebarVisible() && categorySidebar.contains(mouseX, mouseY)) {
                sidebarFocused = true;
                categorySidebar.press(mouseX, mouseY);
                return true;
            }
            if (contentLayout.settings().contains(mouseX, mouseY)) { settingsButtonHeld = true; return true; }
            if ((hiddenEmptyCount > 0 || savedGroupIsHiddenEmpty()) && contentLayout.footerHint().contains(mouseX, mouseY)) {
                if (savedGroupIsHiddenEmpty()) {
                    searchField.setValue("");
                    sourceFilter = GroupUiState.ManagerSourceFilter.ALL;
                    GroupUiState.setManagerSourceFilter(sourceFilter);
                }
                setShowEmptyGroups(true);
                return true;
            }
            if (headerLayout.sort().contains(mouseX, mouseY)) { sortButtonHeld = true; return true; }
            if (headerLayout.back().contains(mouseX, mouseY)) { backButtonHeld = true; return true; }
            if (headerLayout.primary().contains(mouseX, mouseY)) { batchToggleButtonHeld = true; return true; }
            if (headerLayout.secondary().contains(mouseX, mouseY)) { newGroupButtonHeld = true; return true; }
            int segment = hoveredSegmentIndex(segmentFilters(), mouseX, mouseY);
            if (segment >= 0) { heldSegmentIndex = segment; return true; }
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
				if (!GroupRepository.setEnabledQuietly(card.id(), newEnabled)) return true;
				updateCardEnabled(card.id(), newEnabled);
				suppressedSwitchHoverGroupId = card.id();
				return true;
			}
            if (editClick) {
                GroupAction action = card.actionEligibility().canRequest(GroupAction.EDIT) ? GroupAction.EDIT : GroupAction.COPY_AS_CUSTOM;
                if (card.actionEligibility().canRequest(action)) heldCardAction = new PressedCardAction(card.id(), action, Screen.hasShiftDown());
                return true;
            }
            if (deleteClick) {
                if (card.actionEligibility().canRequest(GroupAction.DELETE)) heldCardAction = new PressedCardAction(card.id(), GroupAction.DELETE, Screen.hasShiftDown());
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
		dialogPress.begin(ConfirmDialog.hitTest(this.width, this.height, mouseX, mouseY));
		return true;
	}

	private void executeDialogAction(ConfirmDialog.Action action) {
		if (action == ConfirmDialog.Action.SECONDARY) {
			cancelPendingDialog();
		} else if (action == ConfirmDialog.Action.PRIMARY) {
			if (pendingBatchDelete != null) {
				executeBatchDelete();
			} else {
				PendingDelete pending = pendingDelete;
				if (pending != null) executeSingleDelete(pending.groupId(), GroupAction.DELETE);
			}
		}
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
		if (!GroupRepository.deleteQuietlyChecked(id)) {
			operationMessage = Component.translatable("collapsible_groups.manager.operation_failed");
			cancelDeleteDialog();
			return false;
		}
		operationMessage = GroupRepository.findById(id).isEmpty() && categories.problem() != null ? CategoryChoices.label("cleanup_failed") : null;
		GroupRepository.notifyViewer();
		rebuildCards();
		scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
		cancelDeleteDialog();
		return true;
	}

    private void setBatchMode(boolean enabled) {
        if (batchMode == enabled) return;
        ScrollAnchor anchor = scrollAnchor();
        batchMode = enabled;
        batchSelection = BatchSelectionState.empty();
        selectionNoticeUntil = 0;
        clearTransientInputState();
        relayout(anchor);
    }

	private void executeBatchSetEnabled(boolean enabled) {
		List<GroupManagerCard> cards = selectedVisibleCards();
		boolean changed = false;
		GroupAction action = enabled ? GroupAction.BATCH_ENABLE : GroupAction.BATCH_DISABLE;
		for (GroupManagerCard card : cards) {
			if (card.group().enabled() == enabled || !card.actionEligibility().canRequest(action)) {
				continue;
			}
			if (GroupRepository.setEnabledQuietlyWithoutEvent(card.id(), enabled)) {
				updateCardEnabled(card.id(), enabled);
				changed = true;
			}
		}
		if (changed) {
			GroupRepository.notifyEnabledChanged();
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
		int deleted = 0;
		for (String id : deletableIds) {
			if (GroupRepository.deleteQuietlyChecked(id)) deleted++;
		}
		operationMessage = deleted == deletableIds.size() ? null : Component.translatable("collapsible_groups.manager.operation_failed");
        if (operationMessage == null && categories.problem() != null) operationMessage = CategoryChoices.label("cleanup_failed");
		batchSelection = batchSelection.clear();
		if (deleted > 0) GroupRepository.notifyViewer();
		rebuildCards();
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
		Optional<GroupDefinition> copied = GroupRepository.createCustomCopyDraft(id, copiedDisplayName);
		if (copied.isEmpty()) {
			return false;
		}
		clearTransientInputState();
		draftCategory = categoryOf(card);
		Minecraft.getInstance().setScreen(new GroupEditorScreen(this, copied.get(), true, id));
		return true;
	}

	private static String evaluationLabel(GroupEvaluation.Status status) {
		return switch (status) {
			case PENDING -> ModTranslationKeys.MANAGER_EVALUATION_PENDING;
			case ERROR -> ModTranslationKeys.MANAGER_EVALUATION_ERROR;
			case UNAVAILABLE -> ModTranslationKeys.MANAGER_EVALUATION_UNAVAILABLE;
			case COMPLETE -> ModTranslationKeys.MANAGER_EVALUATION_COMPLETE;
		};
	}

	private void recordEvaluationTooltip(GroupManagerCard card, int x, int y, int mouseX, int mouseY) {
		int textX = x + 6 + HEADER_PREVIEW_SIZE + 6;
		if (card.evaluation().complete() || !isMouseOver(mouseX, mouseY, textX, y + CARD_TITLE_Y + 12,
			switchControlX(x) - 4 - textX, font.lineHeight)) return;
		var detail = Component.translatable(evaluationLabel(card.evaluation().status()));
		card.evaluation().issues().forEach(issue -> detail.append("\n" + issue.reason()));
		pendingTooltip = detail;
	}

	private GroupManagerCard findCurrentCard(String id) {
		if (id == null || id.isBlank()) return null;
		for (GroupManagerCard card : allCards) {
			if (id.equals(card.id())) return card;
		}
		return null;
	}

	private void clearTransientInputState() {
        dialogPress.clear();
        categoryPopup = null;
        batchMenuOpen = false;
        batchMenuFocus = -1;
        drawerOpen = false;
        sidebarFocused = false;
        categorySidebar.cancelPress();
        categoryButtonHeld = false;
        categoryToggleFocused = false;
        heldCardAction = null;
        settingsButtonHeld = false;
		backButtonHeld = false;
		heldSegmentIndex = -1;
		batchToggleButtonHeld = false;
		heldBatchToolbarAction = null;
		newGroupButtonHeld = false;
		closeSortMenu();
		isDraggingScrollbar = false;
		heldSwitchGroupId = null;
		suppressedSwitchHoverGroupId = null;
	}

	private boolean handleScrollbarClick(double mouseX, double mouseY) {
        Rect scrollbar = contentLayout.scrollbar();
        int sbX = scrollbar.x();
        int sbY = scrollbar.y();
        int sbH = scrollbar.height();
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
        if (hasPendingDialog()) return true;
        if (categoryPopup != null) { categoryPopup.drag(mouseY); return true; }
        if (batchMenuOpen || sortMenuOpen) return true;
        if (sidebarVisible() && categorySidebar.dragging()) { categorySidebar.drag(mouseY); return true; }
        if (drawerOpen) return true;
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
            if (button == 0) executeDialogAction(dialogPress.release(ConfirmDialog.hitTest(width, height, mouseX, mouseY)));
            return true;
        }
        if (categoryPopup != null) { categoryPopup.release(); return true; }
        if (button != 0) return drawerOpen || batchMenuOpen || sortMenuOpen || super.mouseReleased(mouseX, mouseY, button);
        if (batchMenuOpen) {
            BatchToolbarAction action = heldBatchToolbarAction;
            heldBatchToolbarAction = null;
            if (action != null && action == hoveredBatchAction(mouseX, mouseY)) executeBatchAction(action);
            return true;
        }
        if (categoryButtonHeld) {
            categoryButtonHeld = false;
            if (contentLayout.categoryToggle(sidebarVisible()).contains(mouseX, mouseY)) toggleCategorySidebar();
            return true;
        }
        if (sidebarVisible() && categorySidebar.pressed()) {
            CategoryChoices.Entry selected = categorySidebar.release(mouseX, mouseY);
            if (selected != null) browseCategory(selected);
            return true;
        }
        if (drawerOpen) return true;
        heldSwitchGroupId = null;
        if (heldShowEmpty) {
            heldShowEmpty = false;
            if (hoveredShowEmpty(mouseX, mouseY)) setShowEmptyGroups(!showEmptyGroups);
            return true;
        }
        if (heldSortMode != null) {
            GroupSortMode chosen = heldSortMode;
            heldSortMode = null;
            if (hoveredSortMode(mouseX, mouseY) == chosen) {
                sortMode = chosen;
                GroupUiState.setManagerSortMode(sortMode);
                closeSortMenu();
                rebuildFilteredCards();
            }
            return true;
        }
        if (sortButtonHeld) {
            sortButtonHeld = false;
            if (headerLayout.sort().contains(mouseX, mouseY)) {
                boolean open = !sortMenuOpen;
                clearTransientInputState();
                sortMenuOpen = open;
            }
            return true;
        }
        if (sortMenuOpen) return true;
        if (releaseCardAction(mouseX, mouseY)) return true;
        if (settingsButtonHeld) {
            settingsButtonHeld = false;
            if (contentLayout.settings().contains(mouseX, mouseY)) {
                clearTransientInputState();
                Minecraft.getInstance().setScreen(new com.starskyxiii.collapsible_groups.client.config.GroupConfigScreen(this));
            }
            return true;
        }
        if (backButtonHeld) {
            backButtonHeld = false;
            if (headerLayout.back().contains(mouseX, mouseY)) Minecraft.getInstance().setScreen(previousScreen);
            return true;
        }
        if (heldSegmentIndex >= 0) {
            int index = heldSegmentIndex;
            heldSegmentIndex = -1;
            var filters = segmentFilters();
            if (index < filters.length && headerLayout.sourceAt(mouseX, mouseY) == index && sourceFilter != filters[index]) {
                sourceFilter = filters[index];
                GroupUiState.setManagerSourceFilter(sourceFilter);
                suppressedSwitchHoverGroupId = null;
                rebuildFilteredCards();
            }
            return true;
        }
        if (batchToggleButtonHeld) {
            batchToggleButtonHeld = false;
            if (headerLayout.primary().contains(mouseX, mouseY)) setBatchMode(!batchMode);
            return true;
        }
        if (newGroupButtonHeld) {
            newGroupButtonHeld = false;
            if (headerLayout.secondary().contains(mouseX, mouseY)) {
                if (batchMode) {
                    clearTransientInputState();
                    batchMenuOpen = true;
                } else openEditor(null);
            }
            return true;
        }
        isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (hasPendingDialog()) return true;
        if (categoryPopup != null) { categoryPopup.scroll(deltaY); return true; }
        if (batchMenuOpen || sortMenuOpen) return true;
        if (sidebarVisible() && categorySidebar.contains(mouseX, mouseY)) { categorySidebar.scroll(deltaY); return true; }
        if (drawerOpen) return true;
        suppressedSwitchHoverGroupId = null;
        heldCardAction = null;
        if (scrollHoveredPreview(mouseX, mouseY, deltaY)) return true;
        if (isInsideCardViewport(mouseX, mouseY)) {
            scrollPixelOffset = clamp(scrollPixelOffset + (int) (deltaY * -20), 0, maxScrollPixels());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        heldCardAction = null;
        if (hasPendingDialog()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) cancelPendingDialog();
            return true;
        }
        if (categoryPopup != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) clearTransientInputState();
            else {
                var selected = categoryPopup.keyPressed(keyCode);
                if (selected != null) chooseCategory(selected);
            }
            return true;
        }
        if (batchMenuOpen) {
            heldBatchToolbarAction = null;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) clearTransientInputState();
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_TAB && hasShiftDown()) batchMenuFocus = batchMenuFocus < 0 ? BatchToolbarAction.values().length - 1 : Math.max(0, batchMenuFocus - 1);
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_TAB && !hasShiftDown()) batchMenuFocus = Math.min(BatchToolbarAction.values().length - 1, batchMenuFocus + 1);
            if (keyCode == GLFW.GLFW_KEY_HOME) batchMenuFocus = 0;
            if (keyCode == GLFW.GLFW_KEY_END) batchMenuFocus = BatchToolbarAction.values().length - 1;
            if (batchMenuFocus >= 0 && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE)) executeBatchAction(BatchToolbarAction.values()[batchMenuFocus]);
            return true;
        }
        if (sortMenuOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) clearTransientInputState();
            return true;
        }
        if (sidebarVisible() && !categoryToggleFocused && (drawerOpen || sidebarFocused)) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (drawerOpen) closeCategoryDrawer();
                else { sidebarFocused = false; categoryToggleFocused = true; }
            } else if (keyCode == GLFW.GLFW_KEY_TAB && !drawerOpen) {
                sidebarFocused = false;
                setFocused(searchField);
                searchField.setFocused(true);
            } else {
                var selected = categorySidebar.keyPressed(keyCode == GLFW.GLFW_KEY_TAB ? GLFW.GLFW_KEY_DOWN : keyCode);
                if (selected != null) browseCategory(selected);
            }
            return true;
        }
        if (categoryToggleFocused) {
            categoryButtonHeld = false;
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) toggleCategorySidebar();
            else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (drawerOpen) closeCategoryDrawer();
                else categoryToggleFocused = false;
            } else if (keyCode == GLFW.GLFW_KEY_TAB) {
                categoryToggleFocused = false;
                if (sidebarVisible()) { sidebarFocused = true; categorySidebar.focusSelection(); }
                else { setFocused(searchField); searchField.setFocused(true); }
            }
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!searchQuery.isEmpty()) searchField.setValue("");
                else blurSearchField();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                blurSearchField();
                categoryToggleFocused = true;
                return true;
            }
            if (searchField.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) { categoryToggleFocused = true; return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

	private GroupSortMode hoveredSortMode(double mouseX, double mouseY) {
		if (!sortMenuOpen) return null;
		SortMenuLayout layout = sortMenuLayout();
		for (int i = 0; i < GroupSortMode.values().length; i++) {
			if (isMouseOver(mouseX, mouseY, layout.contentX(), layout.rowY(i),
				layout.contentWidth(), SORT_MENU_ROW_HEIGHT)) {
				return GroupSortMode.values()[i];
			}
		}
		return null;
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
        if (categoryPopup != null || batchMenuOpen || sortMenuOpen || drawerOpen || categoryToggleFocused || (sidebarVisible() && sidebarFocused) || hasPendingDialog()) return true;
		if (searchField != null && searchField.isFocused()
			&& searchField.charTyped(codePoint, modifiers)) {
			return true;
		}
		return super.charTyped(codePoint, modifiers);
	}

	private boolean scrollHoveredPreview(double mouseX, double mouseY, double deltaY) {
		if (!isInsideCardViewport(mouseX, mouseY)) return false;
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
	public void onGroupSaved(SavedGroupContext context) {
		pendingSavedContext = context;
		lastSavedGroupId = context.groupId();
		operationMessage = context.warningKey() == null ? null : Component.translatable(context.warningKey());
        if (context.shouldReveal() && !categories.update(preferences -> preferences.assign(List.of(context.groupId()), draftCategory))) {
            Component warning = CategoryChoices.label("group_saved_category_failed");
            operationMessage = operationMessage == null ? warning : operationMessage.copy().append(" · ").append(warning);
        }
	}

	private void applySavedContext() {
		SavedGroupContext context = pendingSavedContext;
		pendingSavedContext = null;
		if (context == null) return;
		if (!context.shouldReveal()) {
			scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
			return;
		}
		GroupManagerCard savedCard = findCurrentCard(context.groupId());
		if (savedCard == null) return;
        if (!CategoryChoices.ALL.equals(categoryFilter) && !categoryFilter.equals(categoryFilterId(savedCard))) {
            categoryFilter = categoryFilterId(savedCard);
            GroupUiState.setManagerCategoryFilter(categoryFilter);
        }
		if (!GroupManagerSearchMatcher.matchesSource(sourceFilter, GroupSource.USER)) {
			sourceFilter = GroupUiState.ManagerSourceFilter.USER;
			GroupUiState.setManagerSourceFilter(sourceFilter);
		}
		if (!GroupManagerSearchMatcher.matchesQuery(searchQuery, searchFields(savedCard))) {
			searchQuery = "";
			if (searchField != null) searchField.setValue("");
		}
		rebuildFilteredCards();
		ensureCardVisible(context.groupId());
		highlightedSavedGroupId = context.groupId();
		highlightedSavedUntil = System.currentTimeMillis() + SAVED_CARD_HIGHLIGHT_MS;
	}

	private void ensureCardVisible(String groupId) {
		int index = -1;
		for (int i = 0; i < filteredCards.size(); i++) {
			if (groupId.equals(filteredCards.get(i).id())) {
				index = i;
				break;
			}
		}
		if (index < 0) return;
		int y = cardPos(index)[1];
		int top = headerHeight() + CARD_PADDING;
		int bottom = this.height - FOOTER_HEIGHT;
		if (y < top) {
			scrollPixelOffset -= top - y;
		} else if (y + CARD_HEIGHT > bottom) {
			scrollPixelOffset += y + CARD_HEIGHT - bottom;
		}
		scrollPixelOffset = clamp(scrollPixelOffset, 0, maxScrollPixels());
	}

	@Override
	public Screen asScreen() {
		return this;
	}

    private int contentHeight() {
        return contentLayout == null ? Math.max(0, height - headerHeight() - FOOTER_HEIGHT - CARD_PADDING) : contentLayout.scrollHeight();
    }

    private int maxScrollPixels() {
        return contentLayout == null ? Math.max(0, ((filteredCards.size() + cols - 1) / cols) * (CARD_HEIGHT + CARD_PADDING) - contentHeight())
            : contentLayout.maxScroll(filteredCards.size());
    }

    private int totalRowsForCard(GroupManagerCard card) {
        return PreviewGridLayout.totalRows(card.previewEntries().size(), PREVIEW_COLS);
    }

    private int[] cardPos(int index) {
        Rect rect = contentLayout.card(index, scrollPixelOffset);
        return new int[] { rect.x(), rect.y() };
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
        return contentLayout != null && contentLayout.content().contains(mouseX, mouseY)
            && !drawerOpen && !batchMenuOpen && categoryPopup == null && !hasPendingDialog() && !sortMenuOpen;
    }

	private boolean isHeldSwitchHovered(double mouseX, double mouseY) {
		if (heldSwitchGroupId == null || !isInsideCardViewport(mouseX, mouseY)) return false;
		for (int i = 0; i < filteredCards.size(); i++) {
			GroupManagerCard card = filteredCards.get(i);
			if (!card.id().equals(heldSwitchGroupId)) continue;
			int[] pos = cardPos(i);
			return isMouseOver(mouseX, mouseY, switchControlX(pos[0]), switchControlY(pos[1]), SWITCH_WIDTH, SWITCH_HEIGHT);
		}
		return false;
	}

    private void renderScrollbar(GuiGraphics g) {
        Rect rect = contentLayout.scrollbar();
        if (rect.height() <= 0) return;
        UiSkinRenderer.drawScrollbarPixels(g, rect.x(), rect.y(), rect.height(), rect.height(), maxScrollPixels() + rect.height(), scrollPixelOffset);
    }

	private static boolean isMouseOver(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private record SortMenuLayout(int x, int y, int width, int height) {
		int contentX() { return x + SORT_MENU_PADDING; }
		int contentWidth() { return Math.max(0, width - SORT_MENU_PADDING * 2); }
		int rowY(int index) { return y + SORT_MENU_PADDING + index * SORT_MENU_ROW_HEIGHT; }
		int optionY() { return rowY(GroupSortMode.values().length) + SORT_MENU_OPTION_GAP; }
		boolean contains(double mouseX, double mouseY) { return isMouseOver(mouseX, mouseY, x, y, width, height); }
	}

	private record SearchFieldLayout(int x, int y, int width, int height, boolean visible, boolean stacked) {
		static SearchFieldLayout hidden() {
			return new SearchFieldLayout(0, 0, 0, 0, false, false);
		}
	}

	private enum BatchToolbarAction {
		SELECT_ALL_RESULTS,
		ENABLE,
		DISABLE,
		DELETE,
        MOVE
	}

    private record ScrollAnchor(String id, int withinRow, int fallback) {}
    private record BatchMenuLayout(Rect bounds) {
        Rect row(int index) { return new Rect(bounds.x() + 4, bounds.y() + 4 + index * 20, bounds.width() - 8, 20); }
    }
	private record PendingDelete(String groupId, String displayName) {}

	private record PendingBatchDelete(int deletableCount, int skippedCount) {}
}
