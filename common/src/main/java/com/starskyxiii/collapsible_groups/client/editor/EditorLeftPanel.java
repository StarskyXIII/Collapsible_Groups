package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeServices;

import com.starskyxiii.collapsible_groups.client.editor.EditorFluidIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.model.IngredientSourceCellState;
import com.starskyxiii.collapsible_groups.client.widget.EditorLayout;
import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.client.widget.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchQuery;
import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EditorLeftPanel {
	private enum SourceTab {
		ITEMS,
		FLUIDS,
		GENERIC
	}

	private List<ItemStack> allItems = List.of();
	private List<ItemStack> filteredItems = List.of();
	private List<EditorFluidIngredientView> allFluids = List.of();
	private List<EditorFluidIngredientView> filteredFluids = List.of();
	private List<EditorGenericIngredientView> allGenericIngredients = List.of();
	private List<EditorGenericIngredientView> filteredGenericIngredients = List.of();

	private final EditorItemSearchSession itemSearchSession;
	private final Map<ItemStack, List<String>> otherItemGroupsCache = new IdentityHashMap<>();
	private final Map<EditorFluidIngredientView, List<String>> otherFluidGroupsCache = new IdentityHashMap<>();
	private final Map<EditorGenericIngredientView, List<String>> otherGenericGroupsCache = new IdentityHashMap<>();

	private SourceTab activeTab = SourceTab.ITEMS;

	int scrollRow = 0;
	int hoveredItem = -1;
	int hoveredFluid = -1;
	int hoveredGeneric = -1;

	void clearHover() {
		hoveredItem = -1;
		hoveredFluid = -1;
		hoveredGeneric = -1;
	}

	private boolean isDraggingSb = false;
	private double sbDragStartMouseY;
	private int sbDragStartRow;

	boolean hideUsed = false;

	private DragGesture dragGesture = DragGesture.NONE;
	private final HashSet<String> dragVisited = new HashSet<>();

	private enum DragGesture {
		NONE,
		ITEM_ADD,
		ITEM_REMOVE,
		FLUID_ADD,
		FLUID_REMOVE,
		GENERIC_ADD,
		GENERIC_REMOVE
	}

	private final GroupEditorState state;
	private final Runnable onChange;

	EditorLeftPanel(GroupEditorState state, Runnable onChange, EditorItemSearchSession itemSearchSession) {
		this.state = state;
		this.onChange = onChange;
		this.itemSearchSession = itemSearchSession;
	}

	void init(List<ItemStack> allItems, List<EditorFluidIngredientView> allFluids,
	          List<EditorGenericIngredientView> allGenericIngredients) {
		this.allItems = allItems;
		this.allFluids = allFluids;
		this.allGenericIngredients = allGenericIngredients;
		buildOtherGroupCaches();
	}

	void buildOtherGroupCaches() {
		otherItemGroupsCache.clear();
		otherFluidGroupsCache.clear();
		otherGenericGroupsCache.clear();

		List<GroupDefinition> allGroups = EditorRuntimeServices.get().allGroups();
		Map<String, String> groupNames = EditorGroupOwnershipHelper.enabledGroupDisplayNames(allGroups, state.editId);
		List<GroupDefinition> others = EditorGroupOwnershipHelper.enabledOtherGroups(allGroups, state.editId);

		Map<String, Set<String>> itemReverseIndex = EditorRuntimeServices.get().itemReverseIndex();
		otherItemGroupsCache.putAll(EditorGroupOwnershipHelper.buildItemOwnership(
			allItems, groupNames, others, itemReverseIndex));

		Map<String, Set<String>> fluidReverseIndex = EditorRuntimeServices.get().fluidReverseIndex();
		otherFluidGroupsCache.putAll(EditorRuntimeServices.get().fluidOwnership(
			allFluids, groupNames, others, fluidReverseIndex));

		otherGenericGroupsCache.putAll(EditorRuntimeServices.get().genericOwnership(
			allGenericIngredients, others));
	}

	void setHideUsed(boolean hide) {
		this.hideUsed = hide;
	}

	void rebuildFilter(String rawQuery) {
		IngredientSearchQuery query = IngredientSearchQuery.parse(rawQuery);
		scrollRow = 0;
		if (isShowingFluids()) {
			rebuildFluidFilter(query);
		} else if (isShowingGeneric()) {
			rebuildGenericFilter(query);
		} else {
			rebuildItemFilter(query);
		}
	}

	private void rebuildItemFilter(IngredientSearchQuery query) {
		filteredItems = EditorItemSearchHelper.filterItems(allItems, itemSearchSession,
			otherItemGroupsCache, hideUsed, query);
	}

	private void rebuildFluidFilter(IngredientSearchQuery query) {
		filteredFluids = EditorRuntimeServices.get().filterFluids(allFluids, otherFluidGroupsCache, hideUsed, query);
	}

	private void rebuildGenericFilter(IngredientSearchQuery query) {
		filteredGenericIngredients = EditorRuntimeServices.get().filterGeneric(allGenericIngredients,
			otherGenericGroupsCache, hideUsed, query);
	}

	void render(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout) {
		hoveredItem = -1;
		hoveredFluid = -1;
		hoveredGeneric = -1;
		UiSkinRenderer.drawSlotGrid(g, layout.leftGridX(), layout.gridTop(),
			layout.leftCols(), layout.leftRows(), EditorLayout.ITEM_SIZE);
		boolean scissor = isShowingGeneric();
		if (scissor) {
			g.enableScissor(layout.leftGridX(), layout.gridTop(),
				layout.leftGridX() + layout.leftGridWidth(), layout.gridTop() + layout.gridHeight());
		}
		try {
			List<?> list = currentList();
			int start = scrollRow * layout.leftCols();
			for (int i = 0; i < layout.leftCols() * layout.leftRows() && start + i < list.size(); i++) {
				int x = layout.leftGridX() + (i % layout.leftCols()) * EditorLayout.ITEM_SIZE;
				int y = layout.gridTop() + (i / layout.leftCols()) * EditorLayout.ITEM_SIZE;
				renderCell(g, list.get(start + i), x, y);
				if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) {
					setHover(start + i);
					g.fill(x + 1, y + 1, x + EditorLayout.ITEM_SIZE, y + EditorLayout.ITEM_SIZE, 0x22FFFFFF);
				}
			}
		} finally {
			if (scissor) g.disableScissor();
		}
	}

	/** Faint amber base fill for an overlap source cell (symmetric to the green select fill). */
	private static final int OVERLAP_FILL = 0x44F2C744;

	/**
	 * four-state cell (normal / selected-in-current-group / rule-covered /
	 * overlap), resolved through the common {@link IngredientSourceCellState}
	 * contract. There is no base occupied tint: overlap is a faint amber base fill
	 * plus an amber corner tab + faint amber frame; selected and rule-covered share
	 * the green base fill plus a green corner tab + faint green frame (via
	 * {@link UiSkinRenderer#drawSelectedMarker}). The markers are drawn <em>after</em>
	 * the ingredient with z raised above the item depth (~150), because fills issued
	 * before renderItem land under full-cover fluid/item textures.
	 * {@link IngredientSourceCellState#resolve} enforces the precedence (explicit &gt;
	 * rule-covered &gt; overlap &gt; normal), so at most one marker branch fires.
	 * Overlap cells stay clickable (adding to this group); rule-covered cells are not
	 * toggleable (the rule owns them).
	 */
	private void renderCell(GuiGraphics g, Object entry, int x, int y) {
		int iconX = x + 1;
		int iconY = y + 1;
		IngredientSourceCellState cellState;
		boolean inWhole = false;
		if (isShowingFluids()) {
			EditorFluidIngredientView fluid = (EditorFluidIngredientView) entry;
			boolean selected = state.isFluidSelected(fluidIngredient(fluid));
			cellState = IngredientSourceCellState.resolve(
				selected,
				!selected && state.isFluidRuleCovered(EditorRuleCoverageKeys.fluidKey(fluid)),
				otherFluidGroupsCache.getOrDefault(fluid, List.of()), false);
			paintBaseFill(g, cellState, iconX, iconY, false);
			IngredientCellRenderer.renderFluid(g, fluid, iconX, iconY);
		} else if (isShowingGeneric()) {
			EditorGenericIngredientView generic = (EditorGenericIngredientView) entry;
			boolean selected = state.isGenericSelected(generic);
			cellState = IngredientSourceCellState.resolve(
				selected,
				!selected && state.isGenericRuleCovered(EditorRuleCoverageKeys.genericKey(generic)),
				otherGenericGroupsCache.getOrDefault(generic, List.of()), false);
			paintBaseFill(g, cellState, iconX, iconY, false);
			IngredientCellRenderer.renderGeneric(g, generic, iconX, iconY);
		} else {
			ItemStack stack = (ItemStack) entry;
			inWhole = state.isWholeItemSelected(stack);
			boolean inExact = state.isExactSelected(stack);
			boolean selected = inWhole || inExact;
			cellState = IngredientSourceCellState.resolve(
				selected,
				!selected && state.itemRuleCoverageKey(stack).map(state::isItemRuleCovered).orElse(false),
				otherItemGroupsCache.getOrDefault(stack, List.of()), false);
			paintBaseFill(g, cellState, iconX, iconY, inWhole);
			g.renderItem(stack, iconX, iconY);
		}
		if (cellState.overlapped()) {
			g.pose().pushPose();
			g.pose().translate(0, 0, 160);
			UiSkinRenderer.drawOverlapMarker(g, iconX, iconY, 16);
			g.pose().popPose();
		} else if (cellState.renderedAsSelected()) {
			g.pose().pushPose();
			g.pose().translate(0, 0, 160);
			UiSkinRenderer.drawSelectedMarker(g, iconX, iconY, 16);
			g.pose().popPose();
		}
	}

	/**
	 * Base fill drawn before the ingredient: green for selected / rule-covered
	 * (whole-item selections keep their slightly cooler shade), faint amber for
	 * overlap.
	 */
	private static void paintBaseFill(GuiGraphics g, IngredientSourceCellState cellState,
	                                  int iconX, int iconY, boolean inWhole) {
		if (cellState.renderedAsSelected()) {
			g.fill(iconX, iconY, iconX + 16, iconY + 16, inWhole ? 0x4455BB77 : 0x4466DDAA);
		} else if (cellState.overlapped()) {
			g.fill(iconX, iconY, iconX + 16, iconY + 16, OVERLAP_FILL);
		}
	}

	private void setHover(int idx) {
		if (isShowingFluids()) hoveredFluid = idx;
		else if (isShowingGeneric()) hoveredGeneric = idx;
		else hoveredItem = idx;
	}

	int totalRows(EditorLayout layout) {
		return EditorLayout.totalRows(currentList().size(), layout.leftCols());
	}

	private int maxScrollRow(EditorLayout layout) {
		return Math.max(0, totalRows(layout) - layout.leftRows());
	}

	void clampScroll(EditorLayout layout) {
		scrollRow = ScrollbarHelper.clamp(scrollRow, 0, maxScrollRow(layout));
	}

	boolean mouseClicked(double mouseX, double mouseY, int button, EditorLayout layout) {
		if (button != 0) return false;
		if (mouseY >= layout.gridTop() && mouseY < layout.gridTop() + layout.gridHeight()
			&& mouseX >= layout.leftScrollbarX() && mouseX < layout.leftScrollbarX() + ScrollbarHelper.WIDTH) {
			isDraggingSb = true;
			sbDragStartMouseY = mouseY;
			scrollRow = ScrollbarHelper.trackClickToRow(mouseY, layout.gridTop(), layout.gridHeight(),
				totalRows(layout), layout.leftRows(), scrollRow);
			sbDragStartRow = scrollRow;
			return true;
		}
		if (!layout.isInsideLeft(mouseX, mouseY)) return false;

		List<?> list = currentList();
		int start = scrollRow * layout.leftCols();
		for (int i = 0; i < layout.leftCols() * layout.leftRows() && start + i < list.size(); i++) {
			int x = layout.leftGridX() + (i % layout.leftCols()) * EditorLayout.ITEM_SIZE;
			int y = layout.gridTop() + (i / layout.leftCols()) * EditorLayout.ITEM_SIZE;
			if (!EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) continue;
			handleCellClick(list.get(start + i));
			return true;
		}
		return false;
	}

	private void handleCellClick(Object entry) {
		if (!state.canEditContents()) {
			return;
		}
		// rule-covered cells are not toggleable — the rule owns the membership,
		// so a click is a no-op (the tooltip points the user at the rules mode).
		if (!canToggleCurrentGroup(entry)) {
			return;
		}
		if (isShowingFluids()) {
			EditorFluidIngredientView fluid = (EditorFluidIngredientView) entry;
			boolean was = state.isFluidSelected(fluidIngredient(fluid));
			state.toggleFluidSelection(fluidIngredient(fluid));
			onChange.run();
			startDrag(was ? DragGesture.FLUID_REMOVE : DragGesture.FLUID_ADD, dragFluidKey(fluid));
			return;
		}
		if (isShowingGeneric()) {
			EditorGenericIngredientView generic = (EditorGenericIngredientView) entry;
			boolean was = state.isGenericSelected(generic);
			state.toggleGenericSelection(generic);
			onChange.run();
			startDrag(was ? DragGesture.GENERIC_REMOVE : DragGesture.GENERIC_ADD, dragGenericKey(generic));
			return;
		}

		ItemStack stack = (ItemStack) entry;
		boolean was = state.isExactSelected(stack) || state.isWholeItemSelected(stack);
		if (net.minecraft.client.gui.screens.Screen.hasControlDown()) state.toggleWholeItemSelection(stack);
		else state.toggleSingleSelection(stack);
		state.syncEditItems();
		onChange.run();
		startDrag(was ? DragGesture.ITEM_REMOVE : DragGesture.ITEM_ADD,
			was ? dragRemoveKey(stack) : dragAddKey(stack));
	}

	/**
	 * gate: an entry is toggleable unless it is rule-covered by the current
	 * group (explicit selections are never rule-covered, so they stay toggleable to
	 * allow deselect). Guards both the click and drag toggle paths.
	 */
	private boolean canToggleCurrentGroup(Object entry) {
		if (isShowingFluids()) {
			EditorFluidIngredientView fluid = (EditorFluidIngredientView) entry;
			return state.isFluidSelected(fluidIngredient(fluid))
				|| !state.isFluidRuleCovered(EditorRuleCoverageKeys.fluidKey(fluid));
		}
		if (isShowingGeneric()) {
			EditorGenericIngredientView generic = (EditorGenericIngredientView) entry;
			return state.isGenericSelected(generic)
				|| !state.isGenericRuleCovered(EditorRuleCoverageKeys.genericKey(generic));
		}
		ItemStack stack = (ItemStack) entry;
		return state.isExactSelected(stack) || state.isWholeItemSelected(stack)
			|| !state.itemRuleCoverageKey(stack).map(state::isItemRuleCovered).orElse(false);
	}

	boolean mouseDragged(double mouseX, double mouseY, int button, EditorLayout layout) {
		if (button != 0) return false;
		if (isDraggingSb) {
			scrollRow = ScrollbarHelper.dragToRow(mouseY, sbDragStartMouseY, sbDragStartRow,
				totalRows(layout), layout.leftRows(), layout.gridHeight());
			return true;
		}
		if (dragGesture != DragGesture.NONE) {
			handleDrag(mouseX, mouseY, layout);
			return true;
		}
		return false;
	}

	boolean mouseReleased(int button) {
		if (button != 0) return false;
		isDraggingSb = false;
		if (dragGesture != DragGesture.NONE) {
			dragGesture = DragGesture.NONE;
			dragVisited.clear();
			return true;
		}
		return false;
	}

	boolean mouseScrolled(double mouseX, double mouseY, double deltaY, EditorLayout layout) {
		if (!layout.isInsideLeft(mouseX, mouseY)) return false;
		scrollRow = ScrollbarHelper.clamp(scrollRow - (int) Math.signum(deltaY), 0, maxScrollRow(layout));
		return true;
	}

	private void handleDrag(double mouseX, double mouseY, EditorLayout layout) {
		if (!layout.isInsideLeft(mouseX, mouseY)) return;
		List<?> list = currentList();
		int start = scrollRow * layout.leftCols();
		for (int i = 0; i < layout.leftCols() * layout.leftRows() && start + i < list.size(); i++) {
			int x = layout.leftGridX() + (i % layout.leftCols()) * EditorLayout.ITEM_SIZE;
			int y = layout.gridTop() + (i / layout.leftCols()) * EditorLayout.ITEM_SIZE;
			if (!EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) continue;
			applyDragToEntry(list.get(start + i));
			return;
		}
	}

	private void applyDragToEntry(Object entry) {
		if (!state.canEditContents()) {
			return;
		}
		// same rule-covered gate as the click path — a drag over a rule-covered
		// cell must not toggle it.
		if (!canToggleCurrentGroup(entry)) {
			return;
		}
		switch (dragGesture) {
			case ITEM_ADD -> {
				ItemStack stack = (ItemStack) entry;
				String key = dragAddKey(stack);
				if (dragVisited.add(key) && !state.isWholeItemSelected(stack) && !state.isExactSelected(stack)) {
					if (state.addSingleSelectionIfAbsent(stack)) {
						state.syncEditItems();
						onChange.run();
					}
				}
			}
			case ITEM_REMOVE -> {
				ItemStack stack = (ItemStack) entry;
				String key = dragRemoveKey(stack);
				if (dragVisited.add(key) && (state.isExactSelected(stack) || state.isWholeItemSelected(stack))) {
					state.removeSingleSelection(stack, allItems);
					state.syncEditItems();
					onChange.run();
				}
			}
			case FLUID_ADD -> {
				EditorFluidIngredientView fluid = (EditorFluidIngredientView) entry;
				String key = dragFluidKey(fluid);
				if (dragVisited.add(key) && !state.isFluidSelected(fluidIngredient(fluid))) {
					state.addFluidId(key);
					onChange.run();
				}
			}
			case FLUID_REMOVE -> {
				EditorFluidIngredientView fluid = (EditorFluidIngredientView) entry;
				String key = dragFluidKey(fluid);
				if (dragVisited.add(key) && state.isFluidSelected(fluidIngredient(fluid))) {
					state.removeFluidSelection(fluidIngredient(fluid));
					onChange.run();
				}
			}
			case GENERIC_ADD -> {
				EditorGenericIngredientView generic = (EditorGenericIngredientView) entry;
				String key = dragGenericKey(generic);
				if (dragVisited.add(key) && !state.isGenericSelected(generic)) {
					state.addGenericId(generic.typeId(), generic.resourceId());
					onChange.run();
				}
			}
			case GENERIC_REMOVE -> {
				EditorGenericIngredientView generic = (EditorGenericIngredientView) entry;
				String key = dragGenericKey(generic);
				if (dragVisited.add(key) && state.isGenericSelected(generic)) {
					state.removeGenericSelection(generic);
					onChange.run();
				}
			}
			default -> {}
		}
	}

	private void startDrag(DragGesture gesture, String visitKey) {
		dragGesture = gesture;
		dragVisited.clear();
		dragVisited.add(visitKey);
	}

	void showItems(String searchQuery) {
		activeTab = SourceTab.ITEMS;
		scrollRow = 0;
		rebuildFilter(searchQuery);
	}

	void showFluids(String searchQuery) {
		activeTab = SourceTab.FLUIDS;
		scrollRow = 0;
		rebuildFilter(searchQuery);
	}

	void showGeneric(String searchQuery) {
		activeTab = SourceTab.GENERIC;
		scrollRow = 0;
		rebuildFilter(searchQuery);
	}

	boolean isHideUsed() { return hideUsed; }

	String currentSourceLabel() {
		return switch (activeTab) {
			case FLUIDS -> Component.translatable(ModTranslationKeys.EDITOR_TAB_FLUIDS).getString();
			case GENERIC -> Component.translatable(ModTranslationKeys.EDITOR_TAB_GENERIC).getString();
			case ITEMS -> Component.translatable(ModTranslationKeys.EDITOR_TAB_ITEMS).getString();
		};
	}

	String currentPanelHeader() {
		String key = switch (activeTab) {
			case FLUIDS -> ModTranslationKeys.EDITOR_PANEL_FLUIDS_HEADER;
			case GENERIC -> ModTranslationKeys.EDITOR_PANEL_GENERIC_HEADER;
			case ITEMS -> ModTranslationKeys.EDITOR_PANEL_ITEMS_HEADER;
		};
		return Component.translatable(key, entryCount()).getString();
	}

	int entryCount() {
		return currentList().size();
	}

	int totalEntryCount() {
		if (isShowingFluids()) return allFluids.size();
		if (isShowingGeneric()) return allGenericIngredients.size();
		return allItems.size();
	}

	String countLabel() {
		return Component.translatable(ModTranslationKeys.EDITOR_PANEL_COUNT_ENTRIES, entryCount()).getString();
	}

	List<String> otherGroupsForItem(ItemStack stack) {
		return otherItemGroupsCache.getOrDefault(stack, List.of());
	}

	List<String> otherGroupsForFluid(EditorFluidIngredientView fluid) {
		return otherFluidGroupsCache.getOrDefault(fluid, List.of());
	}

	List<String> otherGroupsForGeneric(EditorGenericIngredientView generic) {
		return otherGenericGroupsCache.getOrDefault(generic, List.of());
	}

	List<ItemStack> filteredItems() { return filteredItems; }
	List<EditorFluidIngredientView> filteredFluids() { return filteredFluids; }
	List<EditorGenericIngredientView> filteredGeneric() { return filteredGenericIngredients; }
	List<ItemStack> allItems() { return allItems; }

	boolean isShowingFluids() { return activeTab == SourceTab.FLUIDS; }
	boolean isShowingGeneric() { return activeTab == SourceTab.GENERIC; }
	boolean isShowingItems() { return activeTab == SourceTab.ITEMS; }

	private List<?> currentList() {
		if (isShowingFluids()) return filteredFluids;
		if (isShowingGeneric()) return filteredGenericIngredients;
		return filteredItems;
	}

	private String dragAddKey(ItemStack stack) {
		return GroupItemSelector.exactSelector(stack);
	}

	private String dragRemoveKey(ItemStack stack) {
		return GroupItemSelector.wholeItemSelector(stack) + "|" + state.cachedExactSelector(stack).orElse("?");
	}

	private String dragFluidKey(EditorFluidIngredientView fluid) {
		return fluid.resourceId();
	}

	private String dragGenericKey(EditorGenericIngredientView generic) {
		return generic.typeId() + "|" + generic.resourceId();
	}

	private static Object fluidIngredient(EditorFluidIngredientView fluid) {
		return fluid.ingredient();
	}
}
