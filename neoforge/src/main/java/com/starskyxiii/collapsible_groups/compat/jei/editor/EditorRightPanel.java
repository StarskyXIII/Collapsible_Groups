package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;
import com.starskyxiii.collapsible_groups.compat.jei.ui.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Manages the right pane of {@link GroupEditorScreen}: the resolved members of
 * the group currently being edited.  Handles scrolling, hover tracking, and
 * click-to-remove for items, fluids, and generic ingredients.
 */
final class EditorRightPanel {

	// -----------------------------------------------------------------------
	// Group content (resolved from current editor state)
	// -----------------------------------------------------------------------

	private List<ItemStack>            groupItems             = List.of();
	private List<Object>               groupFluids            = List.of();
	private List<GenericIngredientView> groupGenericIngredients = List.of();

	// -----------------------------------------------------------------------
	// Scroll / hover / drag
	// -----------------------------------------------------------------------

	int scrollRow       = 0;
	int hoveredItem     = -1;
	int hoveredFluid    = -1;
	int hoveredGeneric  = -1;

	private boolean isDraggingSb    = false;
	private double  sbDragStartMouseY;
	private int     sbDragStartRow;

	// -----------------------------------------------------------------------
	// Dependencies
	// -----------------------------------------------------------------------

	private final GroupEditorState state;
	private final Runnable onChange;

	EditorRightPanel(GroupEditorState state, Runnable onChange) {
		this.state    = state;
		this.onChange = onChange;
	}

	// -----------------------------------------------------------------------
	// Rebuild
	// -----------------------------------------------------------------------

	void rebuild() {
		long traceStart = PerformanceTrace.begin();
		GroupDefinition temp = state.buildPreviewDefinition();
		if (state.isStructurallyEditable()) {
			// New indexed path: avoids full JEI item scan on every edit
			List<ItemStack> indexed = GroupRegistry.resolveEditorDraftItems(state.draft, state.editEnabled);
			if (com.starskyxiii.collapsible_groups.compat.jei.runtime.EditorItemIndex.isVerifyEnabled()) {
				List<ItemStack> scanned = GroupRegistry.resolveItems(temp);
				verifyIndexResult(indexed, scanned);
			}
			groupItems = indexed;
		} else {
			// Non-structurally-editable: keep existing full-scan path
			groupItems = GroupRegistry.resolveItems(temp);
		}
		groupFluids             = GroupRegistry.resolveFluids(temp);
		groupGenericIngredients = buildGenericViews(GroupRegistry.resolveGenericIngredients(temp));
		PerformanceTrace.logIfSlow("EditorRightPanel.rebuild", traceStart, 10,
			"group=" + temp.id()
				+ " items=" + groupItems.size()
				+ " fluids=" + groupFluids.size()
				+ " generic=" + groupGenericIngredients.size());
	}

	private static void verifyIndexResult(List<ItemStack> indexed, List<ItemStack> scanned) {
		if (indexed.size() != scanned.size()) {
			Constants.LOG.warn("[EditorItemIndex] MISMATCH size: indexed={} scanned={}", indexed.size(), scanned.size());
		} else {
			for (int i = 0; i < indexed.size(); i++) {
				if (indexed.get(i) != scanned.get(i)) {
					Constants.LOG.warn("[EditorItemIndex] MISMATCH at index {}: indexed={} scanned={}",
						i, indexed.get(i).getDisplayName().getString(), scanned.get(i).getDisplayName().getString());
					break;
				}
			}
		}
	}

	private List<GenericIngredientView> buildGenericViews(List<GenericIngredientRef> refs) {
		long traceStart = PerformanceTrace.begin();
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null || refs.isEmpty()) return List.of();
		var mgr = runtime.getIngredientManager();
		java.util.List<GenericIngredientView> result = new java.util.ArrayList<>(refs.size());
		for (GenericIngredientRef ref : refs) {
			var type     = ref.type();
			var helper   = mgr.getIngredientHelper(type);
			var renderer = mgr.getIngredientRenderer(type);
			var rl     = helper.getResourceLocation(ref.ingredient());
			String rid = rl != null ? rl.toString()
				: helper.getUid(ref.ingredient(), mezz.jei.api.ingredients.subtypes.UidContext.Ingredient).toString();
			java.util.List<net.minecraft.network.chat.Component> tooltipLines = renderer.getTooltip(ref.ingredient(), net.minecraft.world.item.TooltipFlag.Default.NORMAL);
			net.minecraft.network.chat.Component dn = tooltipLines.isEmpty() ? net.minecraft.network.chat.Component.literal(rid) : tooltipLines.get(0);
			java.util.Set<String> tags = helper.getTagStream(ref.ingredient())
				.map(Object::toString)
				.collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
			String sk = (dn.getString() + "|" + rid + "|" + ref.typeId()).toLowerCase(java.util.Locale.ROOT);
			result.add(new GenericIngredientView(ref.typeId(), type, ref.ingredient(), helper, renderer,
				dn, rid, java.util.Set.copyOf(tags), sk));
		}
		List<GenericIngredientView> copy = List.copyOf(result);
		PerformanceTrace.logIfSlow("EditorRightPanel.buildGenericViews", traceStart, 5,
			"refs=" + refs.size() + " views=" + copy.size());
		return copy;
	}

	// -----------------------------------------------------------------------
	// Scroll helpers
	// -----------------------------------------------------------------------

	int totalRows(EditorLayout layout) {
		int itemRows    = EditorLayout.totalRows(groupItems.size(),              layout.rightCols());
		int fluidRows   = EditorLayout.totalRows(groupFluids.size(),             layout.rightCols());
		int genericRows = EditorLayout.totalRows(groupGenericIngredients.size(), layout.rightCols());
		int total = itemRows + fluidRows + genericRows;
		if (itemRows > 0 && (fluidRows > 0 || genericRows > 0)) total++;
		if (fluidRows > 0 && genericRows > 0) total++;
		return total;
	}

	private int maxScrollRow(EditorLayout layout) {
		return Math.max(0, totalRows(layout) - layout.rightRows());
	}

	void clampScroll(EditorLayout layout) {
		scrollRow = ScrollbarHelper.clamp(scrollRow, 0, maxScrollRow(layout));
	}

	// -----------------------------------------------------------------------
	// Render
	// -----------------------------------------------------------------------

	void render(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout) {
		hoveredItem = hoveredFluid = hoveredGeneric = -1;

		int itemRows    = EditorLayout.totalRows(groupItems.size(),              layout.rightCols());
		int fluidRows   = EditorLayout.totalRows(groupFluids.size(),             layout.rightCols());
		int genericRows = EditorLayout.totalRows(groupGenericIngredients.size(), layout.rightCols());

		int fluidStartVRow = itemRows;
		boolean hasSep1 = itemRows > 0 && (fluidRows > 0 || genericRows > 0);
		if (hasSep1) fluidStartVRow++;

		int genericStartVRow = fluidStartVRow + fluidRows;
		boolean hasSep2 = fluidRows > 0 && genericRows > 0;
		if (hasSep2) genericStartVRow++;

		g.enableScissor(layout.rightGridX(), layout.gridTop(),
			layout.rightGridX() + layout.rightGridWidth(), layout.gridTop() + layout.gridHeight());
		try {
			for (int visRow = 0; visRow < layout.rightRows(); visRow++) {
				int vRow = scrollRow + visRow;
				int y    = layout.gridTop() + visRow * EditorLayout.ITEM_SIZE;

				if (vRow < itemRows) {
					renderItemRow(g, mouseX, mouseY, layout, vRow, y);
				} else if (hasSep1 && vRow == itemRows) {
					g.fill(layout.rightGridX(), y + EditorLayout.ITEM_SIZE / 2,
						layout.rightGridX() + layout.rightCols() * EditorLayout.ITEM_SIZE,
						y + EditorLayout.ITEM_SIZE / 2 + 1, 0x33667799);
				} else if (fluidRows > 0 && vRow >= fluidStartVRow && vRow < fluidStartVRow + fluidRows) {
					renderFluidRow(g, mouseX, mouseY, layout, vRow - fluidStartVRow, y);
				} else if (hasSep2 && vRow == fluidStartVRow + fluidRows) {
					g.fill(layout.rightGridX(), y + EditorLayout.ITEM_SIZE / 2,
						layout.rightGridX() + layout.rightCols() * EditorLayout.ITEM_SIZE,
						y + EditorLayout.ITEM_SIZE / 2 + 1, 0x33667799);
				} else if (genericRows > 0 && vRow >= genericStartVRow && vRow < genericStartVRow + genericRows) {
					renderGenericRow(g, mouseX, mouseY, layout, vRow - genericStartVRow, y);
				}
			}
		} finally {
			g.disableScissor();
		}
	}

	private void renderItemRow(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout, int row, int y) {
		int rowStart = row * layout.rightCols();
		for (int col = 0; col < layout.rightCols() && rowStart + col < groupItems.size(); col++) {
			ItemStack stack = groupItems.get(rowStart + col);
			int idx = rowStart + col;
			int x   = layout.rightGridX() + col * EditorLayout.ITEM_SIZE;
			boolean isExact = state.isExactSelected(stack);
			boolean isWhole = state.isWholeItemSelected(stack);
			boolean explicit = isExact || isWhole;
			if (!explicit) g.fill(x, y, x+16, y+16, 0x332266BB);
			else if (isWhole) g.fill(x, y, x+16, y+16, 0x2855BB77);
			g.renderItem(stack, x, y);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) {
				hoveredItem = idx;
				g.fill(x, y, x+16, y+16, explicit ? 0x28FF5555 : 0x1CFFFFFF);
			}
		}
	}

	private void renderFluidRow(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout, int row, int y) {
		int rowStart = row * layout.rightCols();
		for (int col = 0; col < layout.rightCols() && rowStart + col < groupFluids.size(); col++) {
			FluidStack fluid = (FluidStack) groupFluids.get(rowStart + col);
			int idx = rowStart + col;
			int x   = layout.rightGridX() + col * EditorLayout.ITEM_SIZE;
			boolean selected = state.isFluidSelected(fluid);
			g.fill(x, y, x+16, y+16, selected ? 0x2855BB77 : 0x332266BB);
			IngredientCellRenderer.renderFluid(g, fluid, x, y);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) {
				hoveredFluid = idx;
				g.fill(x, y, x+16, y+16, selected ? 0x28FF5555 : 0x1CFFFFFF);
			}
		}
	}

	private void renderGenericRow(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout, int row, int y) {
		int rowStart = row * layout.rightCols();
		for (int col = 0; col < layout.rightCols() && rowStart + col < groupGenericIngredients.size(); col++) {
			GenericIngredientView entry = groupGenericIngredients.get(rowStart + col);
			int idx = rowStart + col;
			int x   = layout.rightGridX() + col * EditorLayout.ITEM_SIZE;
			boolean selected = state.isGenericSelected(entry);
			g.fill(x, y, x+16, y+16, selected ? 0x2855BB77 : 0x332266BB);
			IngredientCellRenderer.renderGeneric(g, entry, x, y);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) {
				hoveredGeneric = idx;
				g.fill(x, y, x+16, y+16, selected ? 0x28FF5555 : 0x1CFFFFFF);
			}
		}
	}

	// -----------------------------------------------------------------------
	// Input
	// -----------------------------------------------------------------------

	boolean mouseClicked(double mouseX, double mouseY, int button, EditorLayout layout, List<ItemStack> allItems) {
		if (button != 0) return false;
		// Scrollbar
		if (mouseY >= layout.gridTop() && mouseY < layout.gridTop() + layout.gridHeight()
			&& mouseX >= layout.rightScrollbarX() && mouseX < layout.rightScrollbarX() + ScrollbarHelper.WIDTH) {
			isDraggingSb      = true;
			sbDragStartMouseY = mouseY;
			scrollRow = ScrollbarHelper.trackClickToRow(mouseY, layout.gridTop(), layout.gridHeight(),
				totalRows(layout), layout.rightRows(), scrollRow);
			sbDragStartRow = scrollRow;
			return true;
		}
		if (!layout.isInsideRight(mouseX, mouseY)) return false;

		int itemRows    = EditorLayout.totalRows(groupItems.size(),              layout.rightCols());
		int fluidRows   = EditorLayout.totalRows(groupFluids.size(),             layout.rightCols());
		int genericRows = EditorLayout.totalRows(groupGenericIngredients.size(), layout.rightCols());

		int fluidStartVRow = itemRows;
		if (itemRows > 0 && (fluidRows > 0 || genericRows > 0)) fluidStartVRow++;
		int genericStartVRow = fluidStartVRow + fluidRows;
		if (fluidRows > 0 && genericRows > 0) genericStartVRow++;

		for (int visRow = 0; visRow < layout.rightRows(); visRow++) {
			int vRow = scrollRow + visRow;
			int y    = layout.gridTop() + visRow * EditorLayout.ITEM_SIZE;
			if (vRow < itemRows) {
				int rowStart = vRow * layout.rightCols();
				for (int col = 0; col < layout.rightCols() && rowStart + col < groupItems.size(); col++) {
					int x = layout.rightGridX() + col * EditorLayout.ITEM_SIZE;
					if (!EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) continue;
					ItemStack stack = groupItems.get(rowStart + col);
					if (net.minecraft.client.gui.screens.Screen.hasControlDown()) state.removeAllSelectionsForItem(stack);
					else state.removeSingleSelection(stack, allItems);
					state.syncEditItems();
					onChange.run();
					return true;
				}
			} else if (fluidRows > 0 && vRow >= fluidStartVRow && vRow < fluidStartVRow + fluidRows) {
				int rowStart = (vRow - fluidStartVRow) * layout.rightCols();
				for (int col = 0; col < layout.rightCols() && rowStart + col < groupFluids.size(); col++) {
					int x = layout.rightGridX() + col * EditorLayout.ITEM_SIZE;
					if (!EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) continue;
					FluidStack fluid = (FluidStack) groupFluids.get(rowStart + col);
					if (state.isFluidSelected(fluid)) { state.removeFluidSelection(fluid); onChange.run(); }
					return true;
				}
			} else if (genericRows > 0 && vRow >= genericStartVRow && vRow < genericStartVRow + genericRows) {
				int rowStart = (vRow - genericStartVRow) * layout.rightCols();
				for (int col = 0; col < layout.rightCols() && rowStart + col < groupGenericIngredients.size(); col++) {
					int x = layout.rightGridX() + col * EditorLayout.ITEM_SIZE;
					if (!EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) continue;
					GenericIngredientView entry = groupGenericIngredients.get(rowStart + col);
					if (state.isGenericSelected(entry)) { state.removeGenericSelection(entry); onChange.run(); }
					return true;
				}
			}
		}
		return false;
	}

	boolean mouseDragged(double mouseX, double mouseY, int button, EditorLayout layout) {
		if (button != 0 || !isDraggingSb) return false;
		scrollRow = ScrollbarHelper.dragToRow(mouseY, sbDragStartMouseY, sbDragStartRow,
			totalRows(layout), layout.rightRows(), layout.gridHeight());
		return true;
	}

	boolean mouseReleased(int button) {
		if (button != 0) return false;
		isDraggingSb = false;
		return false;
	}

	boolean mouseScrolled(double mouseX, double mouseY, double deltaY, EditorLayout layout) {
		if (!layout.isInsideRight(mouseX, mouseY)) return false;
		scrollRow = ScrollbarHelper.clamp(scrollRow - (int) Math.signum(deltaY), 0, maxScrollRow(layout));
		return true;
	}

	// -----------------------------------------------------------------------
	// Accessors for tooltip helper
	// -----------------------------------------------------------------------

	List<ItemStack>            groupItems()   { return groupItems; }
	List<Object>               groupFluids()  { return groupFluids; }
	List<GenericIngredientView> groupGeneric() { return groupGenericIngredients; }

	String groupSummary() {
		return Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_ITEMS, groupItems.size()).getString()
			+ ", " + Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_FLUIDS, groupFluids.size()).getString()
			+ ", " + Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_GENERIC, groupGenericIngredients.size()).getString();
	}
}
