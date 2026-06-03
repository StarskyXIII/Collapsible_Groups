package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;
import com.starskyxiii.collapsible_groups.compat.jei.ui.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

/**
 * Manages the right pane of {@link GroupEditorScreen}: the resolved item,
 * fluid, and generic members of the group currently being edited.
 */
final class EditorRightPanel {

	// -----------------------------------------------------------------------
	// Group content
	// -----------------------------------------------------------------------

	private List<ItemStack> groupItems = List.of();
	private List<Object>    groupFluids = List.of();
	private List<GenericIngredientView> groupGenericIngredients = List.of();

	// -----------------------------------------------------------------------
	// Scroll / hover
	// -----------------------------------------------------------------------

	int scrollRow    = 0;
	int hoveredItem  = -1;
	int hoveredFluid = -1;
	int hoveredGeneric = -1;

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
		GroupDefinition temp = state.buildPreviewDefinition();
		if (state.canUseIndexedItemPreview()) {
			groupItems = GroupRegistry.resolveEditorDraftItems(state.draft, state.editEnabled);
		} else {
			groupItems = GroupRegistry.resolveItems(temp);
		}
		groupFluids = GroupRegistry.resolveFluids(temp);
		groupGenericIngredients = EditorGenericIngredientHelper.buildViews(
			GroupRegistry.resolveGenericIngredients(temp), "ForgeEditorRightPanel.buildGenericViews");
	}

	// -----------------------------------------------------------------------
	// Scroll helpers
	// -----------------------------------------------------------------------

	int totalRows(EditorLayout layout) {
		return sections(layout).totalRows();
	}

	private int maxScrollRow(EditorLayout layout) {
		return Math.max(0, totalRows(layout) - layout.rightRows());
	}

	void clampScroll(EditorLayout layout) {
		scrollRow = ScrollbarHelper.clamp(scrollRow, 0, maxScrollRow(layout));
	}

	private Sections sections(EditorLayout layout) {
		int itemRows = EditorLayout.totalRows(groupItems.size(), layout.rightCols());
		int fluidRows = EditorLayout.totalRows(groupFluids.size(), layout.rightCols());
		int genericRows = EditorLayout.totalRows(groupGenericIngredients.size(), layout.rightCols());
		boolean hasItemSeparator = itemRows > 0 && (fluidRows > 0 || genericRows > 0);
		int fluidStartVRow = itemRows + (hasItemSeparator ? 1 : 0);
		boolean hasFluidSeparator = fluidRows > 0 && genericRows > 0;
		int genericStartVRow = fluidStartVRow + fluidRows + (hasFluidSeparator ? 1 : 0);
		int totalRows = itemRows + fluidRows + genericRows
			+ (hasItemSeparator ? 1 : 0)
			+ (hasFluidSeparator ? 1 : 0);
		return new Sections(itemRows, fluidRows, genericRows, hasItemSeparator, hasFluidSeparator,
			fluidStartVRow, genericStartVRow, totalRows);
	}

	private record Sections(
		int itemRows,
		int fluidRows,
		int genericRows,
		boolean hasItemSeparator,
		boolean hasFluidSeparator,
		int fluidStartVRow,
		int genericStartVRow,
		int totalRows
	) {}

	// -----------------------------------------------------------------------
	// Render
	// -----------------------------------------------------------------------

	void render(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout) {
		hoveredItem = -1;
		hoveredFluid = -1;
		hoveredGeneric = -1;
		Sections sections = sections(layout);

		g.enableScissor(layout.rightGridX(), layout.gridTop(),
			layout.rightGridX() + layout.rightGridWidth(), layout.gridTop() + layout.gridHeight());
		try {
			for (int visRow = 0; visRow < layout.rightRows(); visRow++) {
				int vRow = scrollRow + visRow;
				int y    = layout.gridTop() + visRow * EditorLayout.ITEM_SIZE;
				if (vRow < sections.itemRows()) {
					renderItemRow(g, mouseX, mouseY, layout, vRow, y);
				} else if (sections.hasItemSeparator() && vRow == sections.itemRows()) {
					g.fill(layout.rightGridX(), y + EditorLayout.ITEM_SIZE / 2,
						layout.rightGridX() + layout.rightCols() * EditorLayout.ITEM_SIZE,
						y + EditorLayout.ITEM_SIZE / 2 + 1, 0x33667799);
				} else if (sections.fluidRows() > 0 && vRow >= sections.fluidStartVRow()
					&& vRow < sections.fluidStartVRow() + sections.fluidRows()) {
					renderFluidRow(g, mouseX, mouseY, layout, vRow - sections.fluidStartVRow(), y);
				} else if (sections.hasFluidSeparator() && vRow == sections.fluidStartVRow() + sections.fluidRows()) {
					g.fill(layout.rightGridX(), y + EditorLayout.ITEM_SIZE / 2,
						layout.rightGridX() + layout.rightCols() * EditorLayout.ITEM_SIZE,
						y + EditorLayout.ITEM_SIZE / 2 + 1, 0x33667799);
				} else if (sections.genericRows() > 0 && vRow >= sections.genericStartVRow()
					&& vRow < sections.genericStartVRow() + sections.genericRows()) {
					renderGenericRow(g, mouseX, mouseY, layout, vRow - sections.genericStartVRow(), y);
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
			if (!explicit) g.fill(x, y, x + 16, y + 16, 0x332266BB);
			else if (isWhole) g.fill(x, y, x + 16, y + 16, 0x2855BB77);
			g.renderItem(stack, x, y);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) {
				hoveredItem = idx;
				g.fill(x, y, x + 16, y + 16, explicit ? 0x28FF5555 : 0x1CFFFFFF);
			}
		}
	}

	private void renderGenericRow(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout, int row, int y) {
		int rowStart = row * layout.rightCols();
		for (int col = 0; col < layout.rightCols() && rowStart + col < groupGenericIngredients.size(); col++) {
			GenericIngredientView entry = groupGenericIngredients.get(rowStart + col);
			int idx = rowStart + col;
			int x = layout.rightGridX() + col * EditorLayout.ITEM_SIZE;
			boolean selected = state.isGenericSelected(entry);
			g.fill(x, y, x + 16, y + 16, selected ? 0x2855BB77 : 0x332266BB);
			IngredientCellRenderer.renderGeneric(g, entry, x, y);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) {
				hoveredGeneric = idx;
				g.fill(x, y, x + 16, y + 16, selected ? 0x28FF5555 : 0x1CFFFFFF);
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
			g.fill(x, y, x + 16, y + 16, selected ? 0x2855BB77 : 0x332266BB);
			IngredientCellRenderer.renderFluid(g, fluid, x, y);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) {
				hoveredFluid = idx;
				g.fill(x, y, x + 16, y + 16, selected ? 0x28FF5555 : 0x1CFFFFFF);
			}
		}
	}

	// -----------------------------------------------------------------------
	// Input
	// -----------------------------------------------------------------------

	boolean mouseClicked(double mouseX, double mouseY, int button, EditorLayout layout, List<ItemStack> allItems) {
		if (button != 0) return false;
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

		Sections sections = sections(layout);

		for (int visRow = 0; visRow < layout.rightRows(); visRow++) {
			int vRow = scrollRow + visRow;
			int y    = layout.gridTop() + visRow * EditorLayout.ITEM_SIZE;
			if (vRow < sections.itemRows()) {
				int rowStart = vRow * layout.rightCols();
				for (int col = 0; col < layout.rightCols() && rowStart + col < groupItems.size(); col++) {
					int x = layout.rightGridX() + col * EditorLayout.ITEM_SIZE;
					if (!EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) continue;
					if (!state.canEditContents()) return true;
					ItemStack stack = groupItems.get(rowStart + col);
					if (net.minecraft.client.gui.screens.Screen.hasControlDown()) state.removeAllSelectionsForItem(stack);
					else state.removeSingleSelection(stack, allItems);
					state.syncEditItems();
					onChange.run();
					return true;
				}
			} else if (sections.fluidRows() > 0 && vRow >= sections.fluidStartVRow()
				&& vRow < sections.fluidStartVRow() + sections.fluidRows()) {
				int rowStart = (vRow - sections.fluidStartVRow()) * layout.rightCols();
				for (int col = 0; col < layout.rightCols() && rowStart + col < groupFluids.size(); col++) {
					int x = layout.rightGridX() + col * EditorLayout.ITEM_SIZE;
					if (!EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) continue;
					if (!state.canEditContents()) return true;
					FluidStack fluid = (FluidStack) groupFluids.get(rowStart + col);
					if (state.isFluidSelected(fluid)) {
						state.removeFluidSelection(fluid);
						onChange.run();
					}
					return true;
				}
			} else if (sections.genericRows() > 0 && vRow >= sections.genericStartVRow()
				&& vRow < sections.genericStartVRow() + sections.genericRows()) {
				int rowStart = (vRow - sections.genericStartVRow()) * layout.rightCols();
				for (int col = 0; col < layout.rightCols() && rowStart + col < groupGenericIngredients.size(); col++) {
					int x = layout.rightGridX() + col * EditorLayout.ITEM_SIZE;
					if (!EditorLayout.isMouseOverCell(mouseX, mouseY, x, y)) continue;
					if (!state.canEditContents()) return true;
					GenericIngredientView entry = groupGenericIngredients.get(rowStart + col);
					if (state.isGenericSelected(entry)) {
						state.removeGenericSelection(entry);
						onChange.run();
					}
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

	List<ItemStack> groupItems() { return groupItems; }
	List<Object> groupFluids() { return groupFluids; }
	List<GenericIngredientView> groupGeneric() { return groupGenericIngredients; }

	String groupSummary() {
		return Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_ITEMS, groupItems.size()).getString()
			+ ", " + Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_FLUIDS, groupFluids.size()).getString()
			+ ", " + Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_GENERIC, groupGenericIngredients.size()).getString();
	}
}
