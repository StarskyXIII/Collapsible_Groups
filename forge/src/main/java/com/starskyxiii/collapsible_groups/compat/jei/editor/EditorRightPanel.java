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
	private List<EditorFluidIngredientView> groupFluids = List.of();
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
		groupFluids = EditorFluidIngredientHelper.buildViews(
			GroupRegistry.resolveFluids(temp), "ForgeEditorRightPanel.buildFluidViews");
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

	private EditorPanelSections sections(EditorLayout layout) {
		return EditorPanelSections.compute(
			groupItems.size(),
			groupFluids.size(),
			groupGenericIngredients.size(),
			layout.rightCols());
	}

	// -----------------------------------------------------------------------
	// Render
	// -----------------------------------------------------------------------

	void render(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout) {
		hoveredItem = -1;
		hoveredFluid = -1;
		hoveredGeneric = -1;
		EditorPanelSections sections = sections(layout);

		g.enableScissor(layout.rightGridX(), layout.gridTop(),
			layout.rightGridX() + layout.rightGridWidth(), layout.gridTop() + layout.gridHeight());
		try {
			for (int visRow = 0; visRow < layout.rightRows(); visRow++) {
				int vRow = scrollRow + visRow;
				int y    = layout.gridTop() + visRow * EditorLayout.ITEM_SIZE;
				if (sections.isItemRow(vRow)) {
					renderItemRow(g, mouseX, mouseY, layout, vRow, y);
				} else if (sections.isItemSeparatorRow(vRow)) {
					g.fill(layout.rightGridX(), y + EditorLayout.ITEM_SIZE / 2,
						layout.rightGridX() + layout.rightCols() * EditorLayout.ITEM_SIZE,
						y + EditorLayout.ITEM_SIZE / 2 + 1, 0x33667799);
				} else if (sections.isFluidRow(vRow)) {
					renderFluidRow(g, mouseX, mouseY, layout, sections.fluidRow(vRow), y);
				} else if (sections.isFluidSeparatorRow(vRow)) {
					g.fill(layout.rightGridX(), y + EditorLayout.ITEM_SIZE / 2,
						layout.rightGridX() + layout.rightCols() * EditorLayout.ITEM_SIZE,
						y + EditorLayout.ITEM_SIZE / 2 + 1, 0x33667799);
				} else if (sections.isGenericRow(vRow)) {
					renderGenericRow(g, mouseX, mouseY, layout, sections.genericRow(vRow), y);
				}
			}
		} finally {
			g.disableScissor();
		}
	}

	private void renderItemRow(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout, int row, int y) {
		EditorGridTraversal.forRowCells(groupItems.size(), row, layout.rightCols(), layout.rightGridX(), y, (idx, x, cellY) -> {
			ItemStack stack = groupItems.get(idx);
			boolean isExact = state.isExactSelected(stack);
			boolean isWhole = state.isWholeItemSelected(stack);
			boolean explicit = isExact || isWhole;
			if (!explicit) g.fill(x, cellY, x + 16, cellY + 16, 0x332266BB);
			else if (isWhole) g.fill(x, cellY, x + 16, cellY + 16, 0x2855BB77);
			g.renderItem(stack, x, cellY);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, cellY)) {
				hoveredItem = idx;
				g.fill(x, cellY, x + 16, cellY + 16, explicit ? 0x28FF5555 : 0x1CFFFFFF);
			}
		});
	}

	private void renderGenericRow(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout, int row, int y) {
		EditorGridTraversal.forRowCells(groupGenericIngredients.size(), row, layout.rightCols(), layout.rightGridX(), y, (idx, x, cellY) -> {
			GenericIngredientView entry = groupGenericIngredients.get(idx);
			boolean selected = state.isGenericSelected(entry);
			g.fill(x, cellY, x + 16, cellY + 16, selected ? 0x2855BB77 : 0x332266BB);
			IngredientCellRenderer.renderGeneric(g, entry, x, cellY);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, cellY)) {
				hoveredGeneric = idx;
				g.fill(x, cellY, x + 16, cellY + 16, selected ? 0x28FF5555 : 0x1CFFFFFF);
			}
		});
	}

	private void renderFluidRow(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout, int row, int y) {
		EditorGridTraversal.forRowCells(groupFluids.size(), row, layout.rightCols(), layout.rightGridX(), y, (idx, x, cellY) -> {
			EditorFluidIngredientView fluid = groupFluids.get(idx);
			boolean selected = state.isFluidSelected(fluidIngredient(fluid));
			g.fill(x, cellY, x + 16, cellY + 16, selected ? 0x2855BB77 : 0x332266BB);
			IngredientCellRenderer.renderFluid(g, fluid, x, cellY);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, cellY)) {
				hoveredFluid = idx;
				g.fill(x, cellY, x + 16, cellY + 16, selected ? 0x28FF5555 : 0x1CFFFFFF);
			}
		});
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

		EditorPanelSections sections = sections(layout);

		for (int visRow = 0; visRow < layout.rightRows(); visRow++) {
			int vRow = scrollRow + visRow;
			int y    = layout.gridTop() + visRow * EditorLayout.ITEM_SIZE;
			if (sections.isItemRow(vRow)) {
				int idx = EditorGridTraversal.findRowCellIndex(
					groupItems.size(), vRow, layout.rightCols(), layout.rightGridX(), y, mouseX, mouseY);
				if (idx < 0) continue;
				if (!state.canEditContents()) return true;
				ItemStack stack = groupItems.get(idx);
				if (net.minecraft.client.gui.screens.Screen.hasControlDown()) state.removeAllSelectionsForItem(stack);
				else state.removeSingleSelection(stack, allItems);
				state.syncEditItems();
				onChange.run();
				return true;
			} else if (sections.isFluidRow(vRow)) {
				int idx = EditorGridTraversal.findRowCellIndex(
					groupFluids.size(), sections.fluidRow(vRow), layout.rightCols(), layout.rightGridX(), y, mouseX, mouseY);
				if (idx < 0) continue;
				if (!state.canEditContents()) return true;
				EditorFluidIngredientView fluid = groupFluids.get(idx);
				if (state.isFluidSelected(fluidIngredient(fluid))) {
					state.removeFluidSelection(fluidIngredient(fluid));
					onChange.run();
				}
				return true;
			} else if (sections.isGenericRow(vRow)) {
				int idx = EditorGridTraversal.findRowCellIndex(
					groupGenericIngredients.size(), sections.genericRow(vRow), layout.rightCols(), layout.rightGridX(), y, mouseX, mouseY);
				if (idx < 0) continue;
				if (!state.canEditContents()) return true;
				GenericIngredientView entry = groupGenericIngredients.get(idx);
				if (state.isGenericSelected(entry)) {
					state.removeGenericSelection(entry);
					onChange.run();
				}
				return true;
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
	List<EditorFluidIngredientView> groupFluids() { return groupFluids; }
	List<GenericIngredientView> groupGeneric() { return groupGenericIngredients; }

	String groupSummary() {
		return Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_ITEMS, groupItems.size()).getString()
			+ ", " + Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_FLUIDS, groupFluids.size()).getString()
			+ ", " + Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_GENERIC, groupGenericIngredients.size()).getString();
	}

	private static FluidStack fluidIngredient(EditorFluidIngredientView fluid) {
		return (FluidStack) fluid.ingredient();
	}
}
