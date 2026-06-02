package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupMatcher;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;
import com.starskyxiii.collapsible_groups.compat.jei.ui.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupItemSelector;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class EditorLeftPanel {
	private enum SourceTab {
		ITEMS,
		FLUIDS
	}

	private List<ItemStack> allItems = List.of();
	private List<ItemStack> filteredItems = List.of();
	private List<FluidStack> allFluids = List.of();
	private List<FluidStack> filteredFluids = List.of();

	private List<String> allItemsSearchKeys = List.of();
	private final Map<ItemStack, List<String>> otherItemGroupsCache = new IdentityHashMap<>();
	private final Map<FluidStack, List<String>> otherFluidGroupsCache = new IdentityHashMap<>();

	private SourceTab activeTab = SourceTab.ITEMS;

	int scrollRow = 0;
	int hoveredItem = -1;
	int hoveredFluid = -1;

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
		FLUID_REMOVE
	}

	private final GroupEditorState state;
	private final Runnable onChange;

	EditorLeftPanel(GroupEditorState state, Runnable onChange) {
		this.state = state;
		this.onChange = onChange;
	}

	void init(List<ItemStack> allItems, List<FluidStack> allFluids) {
		this.allItems = allItems;
		this.allFluids = allFluids;
		buildSearchKeys();
		buildOtherGroupCaches();
	}

	void buildOtherGroupCaches() {
		otherItemGroupsCache.clear();
		otherFluidGroupsCache.clear();

		Map<String, String> groupNames = new HashMap<>();
		for (GroupDefinition group : GroupRegistry.getAllIncludingKubeJs()) {
			if (!group.id().equals(state.editId) && group.enabled()) {
				groupNames.put(group.id(), displayName(group.id(), group.name()));
			}
		}

		Map<String, Set<String>> itemReverseIndex = GroupRegistry.getItemIdToGroupIds();
		if (itemReverseIndex != null) {
			for (ItemStack stack : allItems) {
				String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
				Set<String> groupIds = itemReverseIndex.getOrDefault(registryId, Set.of());
				List<String> names = new ArrayList<>();
				for (String groupId : groupIds) {
					String name = groupNames.get(groupId);
					if (name != null) names.add(name);
				}
				if (!names.isEmpty()) otherItemGroupsCache.put(stack, names);
			}
		} else {
			List<GroupDefinition> others = GroupRegistry.getAllIncludingKubeJs().stream()
				.filter(group -> !group.id().equals(state.editId) && group.enabled())
				.toList();
			for (GroupDefinition other : others) {
				String name = displayName(other.id(), other.name());
				for (ItemStack stack : allItems) {
					if (other.matchesIgnoringEnabled(stack)) {
						otherItemGroupsCache.computeIfAbsent(stack, k -> new ArrayList<>()).add(name);
					}
				}
			}
		}

		Map<String, Set<String>> fluidReverseIndex = GroupRegistry.getFluidIdToGroupIds();
		if (fluidReverseIndex != null) {
			for (FluidStack fluid : allFluids) {
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
			List<GroupDefinition> others = GroupRegistry.getAllIncludingKubeJs().stream()
				.filter(group -> !group.id().equals(state.editId) && group.enabled())
				.toList();
			for (GroupDefinition other : others) {
				String name = displayName(other.id(), other.name());
				for (FluidStack fluid : allFluids) {
					if (GroupMatcher.matchesFluid(other, fluid)) {
						otherFluidGroupsCache.computeIfAbsent(fluid, k -> new ArrayList<>()).add(name);
					}
				}
			}
		}
	}

	void setHideUsed(boolean hide) {
		this.hideUsed = hide;
	}

	void rebuildFilter(String rawQuery) {
		String q = rawQuery == null ? "" : rawQuery.toLowerCase(Locale.ROOT);
		scrollRow = 0;
		if (isShowingFluids()) {
			rebuildFluidFilter(q);
		} else {
			rebuildItemFilter(q);
		}
	}

	private void rebuildItemFilter(String q) {
		List<ItemStack> result = new ArrayList<>();
		for (int i = 0; i < allItems.size(); i++) {
			ItemStack stack = allItems.get(i);
			if (hideUsed && !otherItemGroupsCache.getOrDefault(stack, List.of()).isEmpty()) continue;
			if (q.isBlank() || allItemsSearchKeys.get(i).contains(q)) result.add(stack);
		}
		filteredItems = result;
	}

	private void rebuildFluidFilter(String q) {
		filteredFluids = allFluids.stream().filter(fluid -> {
			if (hideUsed && !otherFluidGroupsCache.getOrDefault(fluid, List.of()).isEmpty()) return false;
			if (q.isBlank()) return true;
			String name = fluid.getDisplayName().getString().toLowerCase(Locale.ROOT);
			String id = fluidId(fluid).toLowerCase(Locale.ROOT);
			return name.contains(q) || id.contains(q);
		}).toList();
	}

	void render(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout) {
		hoveredItem = -1;
		hoveredFluid = -1;
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
	}

	private void renderCell(GuiGraphics g, Object entry, int x, int y) {
		if (isShowingFluids()) {
			FluidStack fluid = (FluidStack) entry;
			if (state.isFluidSelected(fluid)) {
				g.fill(x, y, x + 16, y + 16, 0x4455BB77);
			} else if (!otherFluidGroupsCache.getOrDefault(fluid, List.of()).isEmpty()) {
				g.fill(x, y, x + 16, y + 16, 0x33CC8844);
			}
			IngredientCellRenderer.renderFluid(g, fluid, x, y);
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
			FluidStack fluid = (FluidStack) entry;
			boolean was = state.isFluidSelected(fluid);
			state.toggleFluidSelection(fluid);
			onChange.run();
			startDrag(was ? DragGesture.FLUID_REMOVE : DragGesture.FLUID_ADD, dragFluidKey(fluid));
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
				FluidStack fluid = (FluidStack) entry;
				String key = dragFluidKey(fluid);
				if (dragVisited.add(key) && !state.isFluidSelected(fluid)) {
					state.addFluidId(key);
					onChange.run();
				}
			}
			case FLUID_REMOVE -> {
				FluidStack fluid = (FluidStack) entry;
				String key = dragFluidKey(fluid);
				if (dragVisited.add(key) && state.isFluidSelected(fluid)) {
					state.removeFluidSelection(fluid);
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
		showItems(searchQuery);
	}

	boolean isHideUsed() { return hideUsed; }

	String currentSourceLabel() {
		return Component.translatable(isShowingFluids()
			? ModTranslationKeys.EDITOR_TAB_FLUIDS
			: ModTranslationKeys.EDITOR_TAB_ITEMS).getString();
	}

	String currentPanelHeader() {
		return Component.translatable(isShowingFluids()
			? ModTranslationKeys.EDITOR_PANEL_FLUIDS_HEADER
			: ModTranslationKeys.EDITOR_PANEL_ITEMS_HEADER, entryCount()).getString();
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

	List<String> otherGroupsForFluid(FluidStack fluid) {
		return otherFluidGroupsCache.getOrDefault(fluid, List.of());
	}

	List<ItemStack> filteredItems() { return filteredItems; }
	List<FluidStack> filteredFluids() { return filteredFluids; }
	List<ItemStack> allItems() { return allItems; }

	boolean isShowingFluids() { return activeTab == SourceTab.FLUIDS; }
	boolean isShowingItems() { return activeTab == SourceTab.ITEMS; }

	private List<?> currentList() {
		return isShowingFluids() ? filteredFluids : filteredItems;
	}

	private void buildSearchKeys() {
		allItemsSearchKeys = new ArrayList<>(allItems.size());
		for (ItemStack stack : allItems) {
			String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
			allItemsSearchKeys.add(name + "|" + id);
		}
	}

	private static String displayName(String id, String name) {
		return (name != null && !name.isBlank()) ? name : id;
	}

	private static String fluidId(FluidStack fluid) {
		return BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
	}

	private String dragAddKey(ItemStack stack) {
		return GroupItemSelector.exactSelector(stack);
	}

	private String dragRemoveKey(ItemStack stack) {
		return GroupItemSelector.wholeItemSelector(stack) + "|" + state.cachedExactSelector(stack).orElse("?");
	}

	private String dragFluidKey(FluidStack fluid) {
		return fluidId(fluid);
	}
}
