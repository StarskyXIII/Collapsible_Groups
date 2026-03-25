package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;
import com.starskyxiii.collapsible_groups.compat.jei.ui.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupItemSelector;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manages the left pane of {@link GroupEditorScreen}: item browsing,
 * filtering, scrolling, click/drag-selection, and hover tracking.
 * Item-only variant (no fluid/generic support on Fabric).
 */
final class EditorLeftPanel {

	// -----------------------------------------------------------------------
	// Ingredient data
	// -----------------------------------------------------------------------

	private List<ItemStack> allItems      = List.of();
	private List<ItemStack> filteredItems = List.of();

	// -----------------------------------------------------------------------
	// Search-key and "other group" caches
	// -----------------------------------------------------------------------

	private List<String> allItemsSearchKeys = List.of();
	private final Map<ItemStack, List<String>> otherItemGroupsCache = new IdentityHashMap<>();

	// -----------------------------------------------------------------------
	// Scroll / hover / drag
	// -----------------------------------------------------------------------

	int scrollRow  = 0;
	int hoveredItem = -1;

	private boolean isDraggingSb     = false;
	private double  sbDragStartMouseY;
	private int     sbDragStartRow;

	boolean hideUsed = false;

	private DragGesture dragGesture = DragGesture.NONE;
	private final HashSet<String> dragVisited = new HashSet<>();

	private enum DragGesture { NONE, ITEM_ADD, ITEM_REMOVE }

	// -----------------------------------------------------------------------
	// Dependencies
	// -----------------------------------------------------------------------

	private final GroupEditorState state;
	private final Runnable onChange;

	EditorLeftPanel(GroupEditorState state, Runnable onChange) {
		this.state    = state;
		this.onChange = onChange;
	}

	// -----------------------------------------------------------------------
	// Init / rebuild
	// -----------------------------------------------------------------------

	void init(List<ItemStack> allItems) {
		this.allItems = allItems;
		buildSearchKeys();
		buildOtherGroupCaches();
	}

	void buildOtherGroupCaches() {
		otherItemGroupsCache.clear();

		// Cache display names for other enabled groups by id.
		Map<String, String> groupNames = new java.util.HashMap<>();
		for (GroupDefinition g : GroupRegistry.getAllIncludingKubeJs()) {
			if (!g.id().equals(state.editId) && g.enabled())
				groupNames.put(g.id(), displayName(g.id(), g.name()));
		}
		// ??謜??????????? O(items)
		Map<String, java.util.Set<String>> itemReverseIndex = GroupRegistry.getItemIdToGroupIds();
		if (itemReverseIndex != null) {
			for (ItemStack stack : allItems) {
				String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
				java.util.Set<String> groupIds = itemReverseIndex.getOrDefault(registryId, java.util.Set.of());
				List<String> names = new ArrayList<>();
				for (String gid : groupIds) {
					String name = groupNames.get(gid);
					if (name != null) names.add(name);
				}
				if (!names.isEmpty()) otherItemGroupsCache.put(stack, names);
			}
		} else {
			// Fallback when the reverse index is unavailable.
			List<GroupDefinition> others = GroupRegistry.getAllIncludingKubeJs().stream()
				.filter(g -> !g.id().equals(state.editId) && g.enabled()).toList();
			for (GroupDefinition other : others) {
				String name = displayName(other.id(), other.name());
				for (ItemStack stack : allItems)
					if (other.matchesIgnoringEnabled(stack))
						otherItemGroupsCache.computeIfAbsent(stack, k -> new ArrayList<>()).add(name);
			}
		}
	}

	// -----------------------------------------------------------------------
	// Filter
	// -----------------------------------------------------------------------

	void setHideUsed(boolean hide) {
		this.hideUsed = hide;
	}

	void rebuildFilter(String rawQuery) {
		String q = rawQuery == null ? "" : rawQuery.toLowerCase(Locale.ROOT);
		scrollRow = 0;
		List<ItemStack> result = new ArrayList<>();
		for (int i = 0; i < allItems.size(); i++) {
			ItemStack s = allItems.get(i);
			if (hideUsed && !otherItemGroupsCache.getOrDefault(s, List.of()).isEmpty()) continue;
			if (q.isBlank() || allItemsSearchKeys.get(i).contains(q)) result.add(s);
		}
		filteredItems = result;
	}

	// -----------------------------------------------------------------------
	// Render
	// -----------------------------------------------------------------------

	void render(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout) {
		hoveredItem = -1;
		int start = scrollRow * layout.leftCols();
		for (int i = 0; i < layout.leftCols() * layout.leftRows() && start + i < filteredItems.size(); i++) {
			int x = layout.leftGridX() + (i % layout.leftCols()) * EditorLayout.ITEM_SIZE;
			int y = layout.gridTop()   + (i / layout.leftCols()) * EditorLayout.ITEM_SIZE;
			renderCell(g, filteredItems.get(start + i), x, y);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) {
				hoveredItem = start + i;
				g.fill(x, y, x + 16, y + 16, 0x22FFFFFF);
			}
		}
	}

	private void renderCell(GuiGraphics g, ItemStack stack, int x, int y) {
		boolean inWhole = state.isWholeItemSelected(stack);
		boolean inExact = state.isExactSelected(stack);
		if (inWhole || inExact) g.fill(x, y, x + 16, y + 16, inWhole ? 0x4455BB77 : 0x4466DDAA);
		else if (!otherItemGroupsCache.getOrDefault(stack, List.of()).isEmpty())
			g.fill(x, y, x + 16, y + 16, 0x33CC8844);
		g.renderItem(stack, x, y);
	}

	// -----------------------------------------------------------------------
	// Scroll helpers
	// -----------------------------------------------------------------------

	int totalRows(EditorLayout layout) {
		return EditorLayout.totalRows(filteredItems.size(), layout.leftCols());
	}

	private int maxScrollRow(EditorLayout layout) {
		return Math.max(0, totalRows(layout) - layout.leftRows());
	}

	void clampScroll(EditorLayout layout) {
		scrollRow = ScrollbarHelper.clamp(scrollRow, 0, maxScrollRow(layout));
	}

	// -----------------------------------------------------------------------
	// Input
	// -----------------------------------------------------------------------

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
		int start = scrollRow * layout.leftCols();
		for (int i = 0; i < layout.leftCols() * layout.leftRows() && start + i < filteredItems.size(); i++) {
			int x = layout.leftGridX() + (i % layout.leftCols()) * EditorLayout.ITEM_SIZE;
			int y = layout.gridTop()   + (i / layout.leftCols()) * EditorLayout.ITEM_SIZE;
			if (!EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) continue;
			ItemStack stack = filteredItems.get(start + i);
			boolean was = state.isExactSelected(stack) || state.isWholeItemSelected(stack);
			if (net.minecraft.client.gui.screens.Screen.hasControlDown()) state.toggleWholeItemSelection(stack);
			else                                                          state.toggleSingleSelection(stack);
			state.syncEditItems();
			onChange.run();
			startDrag(was ? DragGesture.ITEM_REMOVE : DragGesture.ITEM_ADD,
				was ? dragRemoveKey(stack) : dragAddKey(stack));
			return true;
		}
		return false;
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

	// -----------------------------------------------------------------------
	// Drag gesture
	// -----------------------------------------------------------------------

	private void handleDrag(double mouseX, double mouseY, EditorLayout layout) {
		if (!layout.isInsideLeft(mouseX, mouseY)) return;
		int start = scrollRow * layout.leftCols();
		for (int i = 0; i < layout.leftCols() * layout.leftRows() && start + i < filteredItems.size(); i++) {
			int x = layout.leftGridX() + (i % layout.leftCols()) * EditorLayout.ITEM_SIZE;
			int y = layout.gridTop()   + (i / layout.leftCols()) * EditorLayout.ITEM_SIZE;
			if (!EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) continue;
			applyDragToEntry(filteredItems.get(start + i));
			return;
		}
	}

	private void applyDragToEntry(ItemStack stack) {
		switch (dragGesture) {
			case ITEM_ADD -> {
				String key = dragAddKey(stack);
				if (dragVisited.add(key) && !state.isWholeItemSelected(stack) && !state.isExactSelected(stack)) {
					state.explicitSet.add(GroupItemSelector.exactSelector(stack));
					state.syncEditItems();
					onChange.run();
				}
			}
			case ITEM_REMOVE -> {
				String key = dragRemoveKey(stack);
				if (dragVisited.add(key) && (state.isExactSelected(stack) || state.isWholeItemSelected(stack))) {
					state.removeSingleSelection(stack, allItems);
					state.syncEditItems();
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

	// -----------------------------------------------------------------------
	// Mode management (items-only; mode button is a no-op)
	// -----------------------------------------------------------------------

	void showItems(String searchQuery) {
		scrollRow = 0;
		rebuildFilter(searchQuery);
	}

	void showFluids(String searchQuery) {
		// Item-only editor on Fabric: keep the visible source on items.
	}

	void showGeneric(String searchQuery) {
		// Item-only editor on Fabric: keep the visible source on items.
	}

	boolean isHideUsed()       { return hideUsed; }
	String currentSourceLabel() { return "Items"; }
	String currentPanelHeader() {
		return Component.translatable(ModTranslationKeys.EDITOR_PANEL_ITEMS_HEADER, entryCount()).getString();
	}
	int    entryCount()  { return filteredItems.size(); }
	String countLabel()  {
		return Component.translatable(ModTranslationKeys.EDITOR_PANEL_COUNT_ENTRIES, entryCount()).getString();
	}

	// -----------------------------------------------------------------------
	// Accessors for tooltip helper
	// -----------------------------------------------------------------------

	List<String>    otherGroupsForItem(ItemStack s) { return otherItemGroupsCache.getOrDefault(s, List.of()); }
	List<ItemStack> filteredItems()                 { return filteredItems; }
	List<ItemStack> allItems()                      { return allItems; }

	// -----------------------------------------------------------------------
	// Private helpers
	// -----------------------------------------------------------------------

	private void buildSearchKeys() {
		allItemsSearchKeys = new ArrayList<>(allItems.size());
		for (ItemStack s : allItems) {
			String nm = s.getHoverName().getString().toLowerCase(Locale.ROOT);
			String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString().toLowerCase(Locale.ROOT);
			allItemsSearchKeys.add(nm + "|" + id);
		}
	}

	private static String displayName(String id, String name) {
		return (name != null && !name.isBlank()) ? name : id;
	}

	private String dragAddKey(ItemStack s)    { return GroupItemSelector.exactSelector(s); }
	private String dragRemoveKey(ItemStack s) {
		return GroupItemSelector.wholeItemSelector(s) + "|" + state.cachedExactSelector(s).orElse("?");
	}
}
