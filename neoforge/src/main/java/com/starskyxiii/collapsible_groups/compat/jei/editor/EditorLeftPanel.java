package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupMatcher;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;
import com.starskyxiii.collapsible_groups.compat.jei.ui.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupItemSelector;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the left pane of {@link GroupEditorScreen}: ingredient browsing,
 * mode cycling, filtering, scrolling, click/drag-selection, and hover tracking.
 *
 * <p>The screen owns this panel and delegates all left-side rendering and input to it.
 * Layout coordinates are passed in via {@link EditorLayout}; the panel does not cache screen dimensions.
 */
final class EditorLeftPanel {

	// -----------------------------------------------------------------------
	// Ingredient data
	// -----------------------------------------------------------------------

	private List<ItemStack> allItems             = List.of();
	private List<ItemStack> filteredItems        = List.of();
	private List<FluidStack> allFluids           = List.of();
	private List<FluidStack> filteredFluids      = List.of();
	private List<GenericIngredientView> allGenericIngredients      = List.of();
	private List<GenericIngredientView> filteredGenericIngredients = List.of();

	// -----------------------------------------------------------------------
	// Search-key and "other group" caches
	// -----------------------------------------------------------------------

	private List<String> allItemsSearchKeys = List.of();
	private final Map<ItemStack, List<String>>             otherItemGroupsCache    = new IdentityHashMap<>();
	private final Map<FluidStack, List<String>>            otherFluidGroupsCache   = new IdentityHashMap<>();
	private final Map<GenericIngredientView, List<String>> otherGenericGroupsCache = new IdentityHashMap<>();

	// -----------------------------------------------------------------------
	// Mode
	// -----------------------------------------------------------------------

	private enum SourceTab {
		ITEMS,
		FLUIDS,
		GENERIC
	}

	private SourceTab activeTab = SourceTab.ITEMS;

	// -----------------------------------------------------------------------
	// Scroll / hover / drag
	// -----------------------------------------------------------------------

	int scrollRow = 0;
	int hoveredItem    = -1;
	int hoveredFluid   = -1;
	int hoveredGeneric = -1;

	private boolean isDraggingSb       = false;
	private double  sbDragStartMouseY;
	private int     sbDragStartRow;

	boolean hideUsed = false;

	private DragGesture dragGesture = DragGesture.NONE;
	private final HashSet<String> dragVisited = new HashSet<>();

	private enum DragGesture {
		NONE,
		ITEM_ADD, ITEM_REMOVE,
		FLUID_ADD, FLUID_REMOVE,
		GENERIC_ADD, GENERIC_REMOVE
	}

	// -----------------------------------------------------------------------
	// Dependencies
	// -----------------------------------------------------------------------

	private final GroupEditorState state;
	private final Runnable onChange;     // called when the group selection changes

	EditorLeftPanel(GroupEditorState state, Runnable onChange) {
		this.state    = state;
		this.onChange = onChange;
	}

	// -----------------------------------------------------------------------
	// Init / rebuild
	// -----------------------------------------------------------------------

	void init(List<ItemStack> allItems,
	          List<FluidStack> allFluids,
	          List<GenericIngredientRef> allGenericRefs) {
		this.allItems  = allItems;
		this.allFluids = allFluids;
		this.allGenericIngredients = buildViews(allGenericRefs);
		buildSearchKeys();
		buildOtherGroupCaches();
	}

	/** Re-computes the "other groups" caches after the group set may have changed. */
	void buildOtherGroupCaches() {
		otherItemGroupsCache.clear();
		otherFluidGroupsCache.clear();
		otherGenericGroupsCache.clear();

		// Build groupId -> display name map (excluding the group being edited and disabled groups)
		Map<String, String> groupNames = new HashMap<>();
		for (GroupDefinition g : GroupRegistry.getAllIncludingKubeJs()) {
			if (!g.id().equals(state.editId) && g.enabled())
				groupNames.put(g.id(), displayName(g.id(), g.name()));
		}

		// Items: reverse index O(items)
		Map<String, Set<String>> itemReverseIndex = GroupRegistry.getItemIdToGroupIds();
		if (itemReverseIndex != null) {
			for (ItemStack stack : allItems) {
				String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
				Set<String> groupIds = itemReverseIndex.getOrDefault(registryId, Set.of());
				List<String> names = new ArrayList<>();
				for (String gid : groupIds) {
					String name = groupNames.get(gid);
					if (name != null) names.add(name);
				}
				if (!names.isEmpty()) otherItemGroupsCache.put(stack, names);
			}
		} else {
			// Fallback: reverse index not yet built
			List<GroupDefinition> others = GroupRegistry.getAllIncludingKubeJs().stream()
				.filter(g -> !g.id().equals(state.editId) && g.enabled()).toList();
			for (GroupDefinition other : others) {
				String name = displayName(other.id(), other.name());
				for (ItemStack stack : allItems)
					if (other.matchesIgnoringEnabled(stack))
						otherItemGroupsCache.computeIfAbsent(stack, k -> new ArrayList<>()).add(name);
			}
		}

		// Fluids: reverse index O(fluids)
		Map<String, Set<String>> fluidReverseIndex = GroupRegistry.getFluidIdToGroupIds();
		if (fluidReverseIndex != null) {
			for (FluidStack fluid : allFluids) {
				String registryId = BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
				Set<String> groupIds = fluidReverseIndex.getOrDefault(registryId, Set.of());
				List<String> names = new ArrayList<>();
				for (String gid : groupIds) {
					String name = groupNames.get(gid);
					if (name != null) names.add(name);
				}
				if (!names.isEmpty()) otherFluidGroupsCache.put(fluid, names);
			}
		} else {
			List<GroupDefinition> others = GroupRegistry.getAllIncludingKubeJs().stream()
				.filter(g -> !g.id().equals(state.editId) && g.enabled()).toList();
			for (GroupDefinition other : others) {
				String name = displayName(other.id(), other.name());
				for (FluidStack fluid : allFluids)
					if (GroupMatcher.matchesFluid(other, fluid))
						otherFluidGroupsCache.computeIfAbsent(fluid, k -> new ArrayList<>()).add(name);
			}
		}

		// Generics: still uses live scan (small count, no reverse index)
		List<GroupDefinition> others = GroupRegistry.getAllIncludingKubeJs().stream()
			.filter(g -> !g.id().equals(state.editId) && g.enabled()).toList();
		for (GenericIngredientView entry : allGenericIngredients) {
			List<String> names = matchingGroupNames(others, entry,
				(g, e) -> GroupMatcher.matchesGeneric(g, e.typeId(), e.ingredient(), e.helper()));
			if (!names.isEmpty()) otherGenericGroupsCache.put(entry, names);
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
		if (isShowingFluids()) {
			rebuildFluidFilter(q);
		} else if (isShowingGeneric()) {
			rebuildGenericFilter(q);
		} else {
			rebuildItemFilter(q);
		}
	}

	private void rebuildItemFilter(String q) {
		List<ItemStack> result = new ArrayList<>();
		for (int i = 0; i < allItems.size(); i++) {
			ItemStack s = allItems.get(i);
			if (hideUsed && !otherItemGroupsCache.getOrDefault(s, List.of()).isEmpty()) continue;
			if (q.isBlank() || allItemsSearchKeys.get(i).contains(q)) result.add(s);
		}
		filteredItems = result;
	}

	private void rebuildFluidFilter(String q) {
		filteredFluids = allFluids.stream().filter(fs -> {
			if (hideUsed && !otherFluidGroupsCache.getOrDefault(fs, List.of()).isEmpty()) return false;
			if (q.isBlank()) return true;
			String name = fs.getHoverName().getString().toLowerCase(Locale.ROOT);
			String id   = BuiltInRegistries.FLUID.getKey(fs.getFluid()).toString().toLowerCase(Locale.ROOT);
			return name.contains(q) || id.contains(q);
		}).toList();
	}

	private void rebuildGenericFilter(String q) {
		filteredGenericIngredients = allGenericIngredients.stream().filter(e -> {
			if (hideUsed && !otherGenericGroupsCache.getOrDefault(e, List.of()).isEmpty()) return false;
			return q.isBlank() || e.searchKey().contains(q);
		}).toList();
	}

	// -----------------------------------------------------------------------
	// Render
	// -----------------------------------------------------------------------

	void render(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout) {
		hoveredItem = hoveredFluid = hoveredGeneric = -1;
		if (isShowingFluids()) {
			renderGrid(g, mouseX, mouseY, layout);
		} else if (isShowingGeneric()) {
			g.enableScissor(layout.leftGridX(), layout.gridTop(),
				layout.leftGridX() + layout.leftGridWidth(), layout.gridTop() + layout.gridHeight());
			renderGrid(g, mouseX, mouseY, layout);
			g.disableScissor();
		} else {
			renderGrid(g, mouseX, mouseY, layout);
		}
	}

	private void renderGrid(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout) {
		List<?> list = currentList();
		int start = scrollRow * layout.leftCols();
		for (int i = 0; i < layout.leftCols() * layout.leftRows() && start + i < list.size(); i++) {
			int x = layout.leftGridX() + (i % layout.leftCols()) * EditorLayout.ITEM_SIZE;
			int y = layout.gridTop()   + (i / layout.leftCols()) * EditorLayout.ITEM_SIZE;
			renderCell(g, list.get(start + i), x, y, start + i);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) {
				setHover(start + i);
				g.fill(x, y, x + 16, y + 16, 0x22FFFFFF);
			}
		}
	}

	private void renderCell(GuiGraphics g, Object entry, int x, int y, int idx) {
		if (isShowingFluids()) {
			FluidStack fluid = (FluidStack) entry;
			if (state.isFluidSelected(fluid))                  g.fill(x, y, x+16, y+16, 0x4455BB77);
			else if (!otherFluidGroupsCache.getOrDefault(fluid, List.of()).isEmpty()) g.fill(x, y, x+16, y+16, 0x33CC8844);
			IngredientCellRenderer.renderFluid(g, fluid, x, y);
		} else if (isShowingGeneric()) {
			GenericIngredientView entry2 = (GenericIngredientView) entry;
			if (state.isGenericSelected(entry2))               g.fill(x, y, x+16, y+16, 0x4455BB77);
			else if (!otherGenericGroupsCache.getOrDefault(entry2, List.of()).isEmpty()) g.fill(x, y, x+16, y+16, 0x33CC8844);
			IngredientCellRenderer.renderGeneric(g, entry2, x, y);
		} else {
			ItemStack stack = (ItemStack) entry;
			boolean inWhole = state.isWholeItemSelected(stack);
			boolean inExact = state.isExactSelected(stack);
			if (inWhole || inExact) g.fill(x, y, x+16, y+16, inWhole ? 0x4455BB77 : 0x4466DDAA);
			else if (!otherItemGroupsCache.getOrDefault(stack, List.of()).isEmpty()) g.fill(x, y, x+16, y+16, 0x33CC8844);
			g.renderItem(stack, x, y);
		}
	}

	private void setHover(int idx) {
		if (isShowingFluids())   hoveredFluid   = idx;
		else if (isShowingGeneric()) hoveredGeneric = idx;
		else                         hoveredItem    = idx;
	}

	// -----------------------------------------------------------------------
	// Scroll helpers
	// -----------------------------------------------------------------------

	int totalRows(EditorLayout layout) {
		return EditorLayout.totalRows(currentList().size(), layout.leftCols());
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
		// Scrollbar
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
			int y = layout.gridTop()   + (i / layout.leftCols()) * EditorLayout.ITEM_SIZE;
			if (!EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) continue;
			handleCellClick(list.get(start + i), start + i);
			return true;
		}
		return false;
	}

	private void handleCellClick(Object entry, int idx) {
		if (!state.canEditContents()) {
			return;
		}
		if (isShowingFluids()) {
			FluidStack fluid = (FluidStack) entry;
			boolean was = state.isFluidSelected(fluid);
			state.toggleFluidSelection(fluid);
			onChange.run();
			startDrag(was ? DragGesture.FLUID_REMOVE : DragGesture.FLUID_ADD, dragFluidKey(fluid));
		} else if (isShowingGeneric()) {
			GenericIngredientView e = (GenericIngredientView) entry;
			boolean was = state.isGenericSelected(e);
			state.toggleGenericSelection(e);
			onChange.run();
			startDrag(was ? DragGesture.GENERIC_REMOVE : DragGesture.GENERIC_ADD, dragGenericKey(e));
		} else {
			ItemStack stack = (ItemStack) entry;
			boolean was = state.isExactSelected(stack) || state.isWholeItemSelected(stack);
			if (net.minecraft.client.gui.screens.Screen.hasControlDown()) state.toggleWholeItemSelection(stack);
			else                                                          state.toggleSingleSelection(stack);
			state.syncEditItems();
			onChange.run();
			startDrag(was ? DragGesture.ITEM_REMOVE : DragGesture.ITEM_ADD,
				was ? dragRemoveKey(stack) : dragAddKey(stack));
		}
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
		List<?> list = currentList();
		int start = scrollRow * layout.leftCols();
		for (int i = 0; i < layout.leftCols() * layout.leftRows() && start + i < list.size(); i++) {
			int x = layout.leftGridX() + (i % layout.leftCols()) * EditorLayout.ITEM_SIZE;
			int y = layout.gridTop()   + (i / layout.leftCols()) * EditorLayout.ITEM_SIZE;
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
			case GENERIC_ADD -> {
				GenericIngredientView e = (GenericIngredientView) entry;
				String key = dragGenericKey(e);
				if (dragVisited.add(key) && !state.isGenericSelected(e)) {
					state.addGenericId(e.typeId(), e.resourceId());
					onChange.run();
				}
			}
			case GENERIC_REMOVE -> {
				GenericIngredientView e = (GenericIngredientView) entry;
				String key = dragGenericKey(e);
				if (dragVisited.add(key) && state.isGenericSelected(e)) {
					state.removeGenericSelection(e);
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
	// Mode management
	// -----------------------------------------------------------------------

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

	boolean isHideUsed() {
		return hideUsed;
	}

	String currentSourceLabel() {
		return switch (activeTab) {
			case FLUIDS -> net.minecraft.network.chat.Component.translatable(ModTranslationKeys.EDITOR_TAB_FLUIDS).getString();
			case GENERIC -> net.minecraft.network.chat.Component.translatable(ModTranslationKeys.EDITOR_TAB_GENERIC).getString();
			case ITEMS  -> net.minecraft.network.chat.Component.translatable(ModTranslationKeys.EDITOR_TAB_ITEMS).getString();
		};
	}

	String currentPanelHeader() {
		String key = switch (activeTab) {
			case FLUIDS -> ModTranslationKeys.EDITOR_PANEL_FLUIDS_HEADER;
			case GENERIC -> ModTranslationKeys.EDITOR_PANEL_GENERIC_HEADER;
			case ITEMS  -> ModTranslationKeys.EDITOR_PANEL_ITEMS_HEADER;
		};
		return net.minecraft.network.chat.Component.translatable(key, entryCount()).getString();
	}

	int entryCount() {
		return currentList().size();
	}

	String countLabel() {
		return net.minecraft.network.chat.Component.translatable(ModTranslationKeys.EDITOR_PANEL_COUNT_ENTRIES, entryCount()).getString();
	}

	// -----------------------------------------------------------------------
	// Accessors for tooltip helper
	// -----------------------------------------------------------------------

	List<String> otherGroupsForItem(ItemStack s)         { return otherItemGroupsCache.getOrDefault(s, List.of()); }
	List<String> otherGroupsForFluid(FluidStack f)       { return otherFluidGroupsCache.getOrDefault(f, List.of()); }
	List<String> otherGroupsForGeneric(GenericIngredientView e) { return otherGenericGroupsCache.getOrDefault(e, List.of()); }

	List<ItemStack> filteredItems()              { return filteredItems; }
	List<FluidStack> filteredFluids()            { return filteredFluids; }
	List<GenericIngredientView> filteredGeneric(){ return filteredGenericIngredients; }
	List<ItemStack> allItems()                   { return allItems; }

	boolean isShowingFluids()   { return activeTab == SourceTab.FLUIDS; }
	boolean isShowingGeneric()  { return activeTab == SourceTab.GENERIC; }
	boolean isShowingItems()    { return activeTab == SourceTab.ITEMS; }

	// -----------------------------------------------------------------------
	// Private helpers
	// -----------------------------------------------------------------------

	private List<?> currentList() {
		if (isShowingFluids())   return filteredFluids;
		if (isShowingGeneric())  return filteredGenericIngredients;
		return filteredItems;
	}

	private void buildSearchKeys() {
		allItemsSearchKeys = new ArrayList<>(allItems.size());
		for (ItemStack s : allItems) {
			String nm = s.getHoverName().getString().toLowerCase(Locale.ROOT);
			String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString().toLowerCase(Locale.ROOT);
			allItemsSearchKeys.add(nm + "|" + id);
		}
	}

	private List<GenericIngredientView> buildViews(List<GenericIngredientRef> refs) {
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null || refs.isEmpty()) return List.of();
		var mgr = runtime.getIngredientManager();
		List<GenericIngredientView> result = new ArrayList<>(refs.size());
		for (GenericIngredientRef ref : refs) {
			IIngredientType<Object> type     = ref.type();
			IIngredientHelper<Object> helper   = mgr.getIngredientHelper(type);
			IIngredientRenderer<Object> renderer = mgr.getIngredientRenderer(type);
			var rl = helper.getResourceLocation(ref.ingredient());
			String resourceId = rl != null
				? rl.toString()
				: helper.getUid(ref.ingredient(), UidContext.Ingredient).toString();
			List<Component> tooltipLines = renderer.getTooltip(ref.ingredient(), net.minecraft.world.item.TooltipFlag.Default.NORMAL);
			Component displayName = tooltipLines.isEmpty() ? Component.literal(resourceId) : tooltipLines.get(0);
			Set<String> tagIds = helper.getTagStream(ref.ingredient())
				.map(Object::toString)
				.collect(Collectors.toCollection(LinkedHashSet::new));
			String searchKey = (displayName.getString() + "|" + resourceId + "|" + ref.typeId()).toLowerCase(Locale.ROOT);
			result.add(new GenericIngredientView(ref.typeId(), type, ref.ingredient(), helper, renderer,
				displayName, resourceId, Set.copyOf(tagIds), searchKey));
		}
		return List.copyOf(result);
	}

	private static String displayName(String id, String name) {
		return (name != null && !name.isBlank()) ? name : id;
	}

	private <U> List<String> matchingGroupNames(List<GroupDefinition> groups, U ingredient,
	                                             java.util.function.BiPredicate<GroupDefinition, U> matcher) {
		Set<String> names = new LinkedHashSet<>();
		for (GroupDefinition g : groups) {
			if (matcher.test(g, ingredient)) names.add(displayName(g.id(), g.name()));
		}
		return List.copyOf(names);
	}

	// Drag gesture key generators - used to deduplicate entries in dragVisited
	private String dragAddKey(ItemStack s)    { return GroupItemSelector.exactSelector(s); }
	private String dragRemoveKey(ItemStack s) {
		return GroupItemSelector.wholeItemSelector(s) + "|" + state.cachedExactSelector(s).orElse("?");
	}
	private String dragFluidKey(FluidStack f)       { return BuiltInRegistries.FLUID.getKey(f.getFluid()).toString(); }
	private String dragGenericKey(GenericIngredientView e) { return e.typeId() + "|" + e.resourceId(); }
}
