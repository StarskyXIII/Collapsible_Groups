package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupMatcher;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;
import com.starskyxiii.collapsible_groups.compat.jei.ui.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupItemSelector;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
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
	private List<IJeiFluidIngredient> allFluids = List.of();
	private List<IJeiFluidIngredient> filteredFluids = List.of();
	private List<GenericIngredientView> allGenericIngredients = List.of();
	private List<GenericIngredientView> filteredGenericIngredients = List.of();

	private List<String> allItemsSearchKeys = List.of();
	private final Map<ItemStack, List<String>> otherItemGroupsCache = new IdentityHashMap<>();
	private final Map<IJeiFluidIngredient, List<String>> otherFluidGroupsCache = new IdentityHashMap<>();
	private final Map<GenericIngredientView, List<String>> otherGenericGroupsCache = new IdentityHashMap<>();

	private SourceTab activeTab = SourceTab.ITEMS;

	int scrollRow = 0;
	int hoveredItem = -1;
	int hoveredFluid = -1;
	int hoveredGeneric = -1;

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

	EditorLeftPanel(GroupEditorState state, Runnable onChange) {
		this.state = state;
		this.onChange = onChange;
	}

	void init(List<ItemStack> allItems, List<IJeiFluidIngredient> allFluids,
	          List<GenericIngredientRef> allGenericRefs) {
		this.allItems = allItems;
		this.allFluids = allFluids;
		this.allGenericIngredients = EditorGenericIngredientHelper.buildViews(allGenericRefs,
			"FabricEditorLeftPanel.buildGenericViews");
		buildSearchKeys();
		buildOtherGroupCaches();
	}

	void buildOtherGroupCaches() {
		otherItemGroupsCache.clear();
		otherFluidGroupsCache.clear();
		otherGenericGroupsCache.clear();

		List<GroupDefinition> allGroups = GroupRegistry.getAllIncludingKubeJs();
		Map<String, String> groupNames = EditorGroupOwnershipHelper.enabledGroupDisplayNames(allGroups, state.editId);
		List<GroupDefinition> others = EditorGroupOwnershipHelper.enabledOtherGroups(allGroups, state.editId);

		Map<String, Set<String>> itemReverseIndex = GroupRegistry.getItemIdToGroupIds();
		otherItemGroupsCache.putAll(EditorGroupOwnershipHelper.buildItemOwnership(
			allItems, groupNames, others, itemReverseIndex));

		Map<String, Set<String>> fluidReverseIndex = GroupRegistry.getFluidIdToGroupIds();
		if (fluidReverseIndex != null) {
			for (IJeiFluidIngredient fluid : allFluids) {
				String registryId = fluidId(fluid);
				Set<String> groupIds = fluidReverseIndex.getOrDefault(registryId, Set.of());
				List<String> names = new ArrayList<>();
				for (String groupId : groupIds) {
					String name = groupNames.get(groupId);
					if (name != null) names.add(name);
				}
				if (!names.isEmpty()) otherFluidGroupsCache.put(fluid, names);
			}
		} else {
			for (GroupDefinition other : others) {
				String name = EditorGroupOwnershipHelper.displayName(other);
				for (IJeiFluidIngredient fluid : allFluids) {
					if (GroupMatcher.matchesFluid(other, fluid)) {
						otherFluidGroupsCache.computeIfAbsent(fluid, k -> new ArrayList<>()).add(name);
					}
				}
			}
		}

		otherGenericGroupsCache.putAll(EditorGenericIngredientHelper.buildOwnership(
			allGenericIngredients, others));
	}

	void setHideUsed(boolean hide) {
		this.hideUsed = hide;
	}

	void rebuildFilter(String rawQuery) {
		String q = EditorItemSearchHelper.normalizeQuery(rawQuery);
		scrollRow = 0;
		if (isShowingFluids()) {
			rebuildFluidFilter(q);
		} else if (isShowingGeneric()) {
			rebuildGenericFilter(q);
		} else {
			rebuildItemFilter(q);
		}
	}

	private void rebuildItemFilter(String q) {
		filteredItems = EditorItemSearchHelper.filterItems(allItems, allItemsSearchKeys,
			otherItemGroupsCache, hideUsed, q);
	}

	private void rebuildFluidFilter(String q) {
		filteredFluids = allFluids.stream().filter(fluid -> {
			if (hideUsed && !otherFluidGroupsCache.getOrDefault(fluid, List.of()).isEmpty()) return false;
			if (q.isBlank()) return true;
			String name = fluidName(fluid).toLowerCase(Locale.ROOT);
			String id = fluidId(fluid).toLowerCase(Locale.ROOT);
			return name.contains(q) || id.contains(q);
		}).toList();
	}

	private void rebuildGenericFilter(String q) {
		filteredGenericIngredients = EditorGenericIngredientHelper.filterViews(allGenericIngredients,
			otherGenericGroupsCache, hideUsed, q);
	}

	void render(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout) {
		hoveredItem = -1;
		hoveredFluid = -1;
		hoveredGeneric = -1;
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
					g.fill(x, y, x + 16, y + 16, 0x22FFFFFF);
				}
			}
		} finally {
			if (scissor) g.disableScissor();
		}
	}

	private void renderCell(GuiGraphics g, Object entry, int x, int y) {
		if (isShowingFluids()) {
			IJeiFluidIngredient fluid = (IJeiFluidIngredient) entry;
			if (state.isFluidSelected(fluid)) {
				g.fill(x, y, x + 16, y + 16, 0x4455BB77);
			} else if (!otherFluidGroupsCache.getOrDefault(fluid, List.of()).isEmpty()) {
				g.fill(x, y, x + 16, y + 16, 0x33CC8844);
			}
			IngredientCellRenderer.renderFluid(g, fluid, x, y);
			return;
		}
		if (isShowingGeneric()) {
			GenericIngredientView generic = (GenericIngredientView) entry;
			if (state.isGenericSelected(generic)) {
				g.fill(x, y, x + 16, y + 16, 0x4455BB77);
			} else if (!otherGenericGroupsCache.getOrDefault(generic, List.of()).isEmpty()) {
				g.fill(x, y, x + 16, y + 16, 0x33CC8844);
			}
			IngredientCellRenderer.renderGeneric(g, generic, x, y);
			return;
		}

		ItemStack stack = (ItemStack) entry;
		boolean inWhole = state.isWholeItemSelected(stack);
		boolean inExact = state.isExactSelected(stack);
		if (inWhole || inExact) {
			g.fill(x, y, x + 16, y + 16, inWhole ? 0x4455BB77 : 0x4466DDAA);
		} else if (!otherItemGroupsCache.getOrDefault(stack, List.of()).isEmpty()) {
			g.fill(x, y, x + 16, y + 16, 0x33CC8844);
		}
		g.renderItem(stack, x, y);
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
		if (isShowingFluids()) {
			IJeiFluidIngredient fluid = (IJeiFluidIngredient) entry;
			boolean was = state.isFluidSelected(fluid);
			state.toggleFluidSelection(fluid);
			onChange.run();
			startDrag(was ? DragGesture.FLUID_REMOVE : DragGesture.FLUID_ADD, dragFluidKey(fluid));
			return;
		}
		if (isShowingGeneric()) {
			GenericIngredientView generic = (GenericIngredientView) entry;
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
		switch (dragGesture) {
			case ITEM_ADD -> {
				ItemStack stack = (ItemStack) entry;
				String key = dragAddKey(stack);
				if (dragVisited.add(key) && !state.isWholeItemSelected(stack) && !state.isExactSelected(stack)) {
					state.explicitSet.add(GroupItemSelector.exactSelector(stack));
					state.syncEditItems();
					onChange.run();
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
				IJeiFluidIngredient fluid = (IJeiFluidIngredient) entry;
				String key = dragFluidKey(fluid);
				if (dragVisited.add(key) && !state.isFluidSelected(fluid)) {
					state.addFluidId(key);
					onChange.run();
				}
			}
			case FLUID_REMOVE -> {
				IJeiFluidIngredient fluid = (IJeiFluidIngredient) entry;
				String key = dragFluidKey(fluid);
				if (dragVisited.add(key) && state.isFluidSelected(fluid)) {
					state.removeFluidSelection(fluid);
					onChange.run();
				}
			}
			case GENERIC_ADD -> {
				GenericIngredientView generic = (GenericIngredientView) entry;
				String key = dragGenericKey(generic);
				if (dragVisited.add(key) && !state.isGenericSelected(generic)) {
					state.addGenericId(generic.typeId(), generic.resourceId());
					onChange.run();
				}
			}
			case GENERIC_REMOVE -> {
				GenericIngredientView generic = (GenericIngredientView) entry;
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

	String countLabel() {
		return Component.translatable(ModTranslationKeys.EDITOR_PANEL_COUNT_ENTRIES, entryCount()).getString();
	}

	List<String> otherGroupsForItem(ItemStack stack) {
		return otherItemGroupsCache.getOrDefault(stack, List.of());
	}

	List<String> otherGroupsForFluid(IJeiFluidIngredient fluid) {
		return otherFluidGroupsCache.getOrDefault(fluid, List.of());
	}

	List<String> otherGroupsForGeneric(GenericIngredientView generic) {
		return otherGenericGroupsCache.getOrDefault(generic, List.of());
	}

	List<ItemStack> filteredItems() { return filteredItems; }
	List<IJeiFluidIngredient> filteredFluids() { return filteredFluids; }
	List<GenericIngredientView> filteredGeneric() { return filteredGenericIngredients; }
	List<ItemStack> allItems() { return allItems; }

	boolean isShowingFluids() { return activeTab == SourceTab.FLUIDS; }
	boolean isShowingGeneric() { return activeTab == SourceTab.GENERIC; }
	boolean isShowingItems() { return activeTab == SourceTab.ITEMS; }

	private List<?> currentList() {
		if (isShowingFluids()) return filteredFluids;
		if (isShowingGeneric()) return filteredGenericIngredients;
		return filteredItems;
	}

	private void buildSearchKeys() {
		allItemsSearchKeys = EditorItemSearchHelper.buildSearchKeys(allItems);
	}

	private static String fluidName(IJeiFluidIngredient fluid) {
		return FluidVariantAttributes.getName(fluid.getFluidVariant()).getString();
	}

	private static String fluidId(IJeiFluidIngredient fluid) {
		return BuiltInRegistries.FLUID.getKey(fluid.getFluidVariant().getFluid()).toString();
	}

	private String dragAddKey(ItemStack stack) {
		return GroupItemSelector.exactSelector(stack);
	}

	private String dragRemoveKey(ItemStack stack) {
		return GroupItemSelector.wholeItemSelector(stack) + "|" + state.cachedExactSelector(stack).orElse("?");
	}

	private String dragFluidKey(IJeiFluidIngredient fluid) {
		return fluidId(fluid);
	}

	private String dragGenericKey(GenericIngredientView generic) {
		return EditorGenericIngredientHelper.dragKey(generic);
	}
}
