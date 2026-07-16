package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeServices;

import com.starskyxiii.collapsible_groups.client.editor.EditorFluidIngredientView;
import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import com.starskyxiii.collapsible_groups.client.widget.EditorLayout;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.client.widget.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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
	private List<EditorGenericIngredientView> groupGenericIngredients = List.of();

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
		long traceStart = EditorRuntimeServices.get().beginTrace();
		GroupDefinition temp = state.buildPreviewDefinition();
		if (state.canUseIndexedItemPreview()) {
			List<ItemStack> indexed = EditorRuntimeServices.get().resolveEditorDraftItems(state.draft, state.editEnabled);
			if (EditorRuntimeServices.get().verifyItemIndex()) {
				List<ItemStack> scanned = EditorRuntimeServices.get().resolveItems(temp);
				verifyIndexResult(indexed, scanned);
			}
			groupItems = indexed;
		} else {
			// Hybrid drafts with preserved subtrees are not flat-index safe. Resolve the union of the
			// indexed flat matches and the memoised preserved-subtree full scan — item-for-item and
			// order-for-order identical to resolveItems(temp).
			List<ItemStack> union = EditorRuntimeServices.get().resolveHybridEditorDraftItems(state.draft, state.editEnabled);
			if (EditorRuntimeServices.get().verifyItemIndex()) {
				List<ItemStack> scanned = EditorRuntimeServices.get().resolveItems(temp);
				verifyIndexResult(union, scanned);
			}
			groupItems = union;
		}
		groupFluids = EditorRuntimeServices.get().resolveFluids(temp, "EditorRightPanel.buildFluidViews");
		groupGenericIngredients = EditorRuntimeServices.get().resolveGenericIngredients(
			temp, "EditorRightPanel.buildGenericViews");
		// Converge the rule-coverage id sets from this single resolve pass so
		// the source grid can flag rule-covered cells (green, not toggleable) without
		// re-resolving or reaching into this panel.
		state.updateRuleCoverage(
			state.itemRuleCoverageKeys(groupItems),
			EditorRuleCoverageKeys.fluidIds(groupFluids),
			EditorRuleCoverageKeys.genericKeys(groupGenericIngredients));
		EditorRuntimeServices.get().logIfSlow("EditorRightPanel.rebuild", traceStart, 10,
			"group=" + temp.id()
				+ " items=" + groupItems.size()
				+ " fluids=" + groupFluids.size()
				+ " generic=" + groupGenericIngredients.size());
	}

	private static void verifyIndexResult(List<ItemStack> indexed, List<ItemStack> scanned) {
		if (indexed.size() != scanned.size()) {
			Constants.LOG.warn("[EditorItemIndex] MISMATCH size: indexed={} scanned={}", indexed.size(), scanned.size());
			return;
		}
		for (int i = 0; i < indexed.size(); i++) {
			if (indexed.get(i) != scanned.get(i)) {
				Constants.LOG.warn("[EditorItemIndex] MISMATCH at index {}: indexed={} scanned={}",
					i, indexed.get(i).getDisplayName().getString(), scanned.get(i).getDisplayName().getString());
				break;
			}
		}
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
		UiSkinRenderer.drawSlotGrid(g, layout.rightGridX(), layout.gridTop(),
			layout.rightCols(), layout.rightRows(), EditorLayout.ITEM_SIZE);

		g.enableScissor(layout.rightGridX(), layout.gridTop(),
			layout.rightGridX() + layout.rightGridWidth(), layout.gridTop() + layout.gridHeight());
		try {
			for (int visRow = 0; visRow < layout.rightRows(); visRow++) {
				int vRow = scrollRow + visRow;
				int y = layout.gridTop() + visRow * EditorLayout.ITEM_SIZE;
				if (sections.isItemRow(vRow)) {
					renderItemRow(g, mouseX, mouseY, layout, vRow, y);
				} else if (sections.isFluidRow(vRow)) {
					renderFluidRow(g, mouseX, mouseY, layout, sections.fluidRow(vRow), y);
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
			int iconX = x + 1;
			int iconY = cellY + 1;
			if (!explicit) g.fill(iconX, iconY, iconX + 16, iconY + 16, 0x332266BB);
			else if (isWhole) g.fill(iconX, iconY, iconX + 16, iconY + 16, 0x2855BB77);
			g.renderItem(stack, iconX, iconY);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, cellY)) {
				hoveredItem = idx;
				g.fill(iconX, iconY, iconX + 16, iconY + 16, 0x1CFFFFFF);
				if (explicit && state.canEditContents()) {
					renderRemoveBadge(g, iconX, iconY, mouseX, mouseY);
				}
			}
		});
	}

	private void renderGenericRow(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout, int row, int y) {
		EditorGridTraversal.forRowCells(groupGenericIngredients.size(), row, layout.rightCols(), layout.rightGridX(), y, (idx, x, cellY) -> {
			EditorGenericIngredientView entry = groupGenericIngredients.get(idx);
			boolean selected = state.isGenericSelected(entry);
			int iconX = x + 1;
			int iconY = cellY + 1;
			g.fill(iconX, iconY, iconX + 16, iconY + 16, selected ? 0x2855BB77 : 0x332266BB);
			IngredientCellRenderer.renderGeneric(g, entry, iconX, iconY);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, cellY)) {
				hoveredGeneric = idx;
				g.fill(iconX, iconY, iconX + 16, iconY + 16, 0x1CFFFFFF);
				if (selected && state.canEditContents()) {
					renderRemoveBadge(g, iconX, iconY, mouseX, mouseY);
				}
			}
		});
	}

	private void renderFluidRow(GuiGraphics g, int mouseX, int mouseY, EditorLayout layout, int row, int y) {
		EditorGridTraversal.forRowCells(groupFluids.size(), row, layout.rightCols(), layout.rightGridX(), y, (idx, x, cellY) -> {
			EditorFluidIngredientView fluid = groupFluids.get(idx);
			boolean selected = state.isFluidSelected(fluidIngredient(fluid));
			int iconX = x + 1;
			int iconY = cellY + 1;
			g.fill(iconX, iconY, iconX + 16, iconY + 16, selected ? 0x2855BB77 : 0x332266BB);
			IngredientCellRenderer.renderFluid(g, fluid, iconX, iconY);
			if (EditorLayout.isMouseOverCell(mouseX, mouseY, x, cellY)) {
				hoveredFluid = idx;
				g.fill(iconX, iconY, iconX + 16, iconY + 16, 0x1CFFFFFF);
				if (selected && state.canEditContents()) {
					renderRemoveBadge(g, iconX, iconY, mouseX, mouseY);
				}
			}
		});
	}

	/**
	 * Removal happens only through this hover ×
	 * badge — the cell body never removes. Shown only on hovered, removable cells
	 * of editable groups (read-only groups never render it). Drawn with z raised
	 * above the ingredient depth (~150) so it covers the item/fluid texture.
	 */
	private void renderRemoveBadge(GuiGraphics g, int iconX, int iconY, int mouseX, int mouseY) {
		boolean badgeHovered = UiSkinRenderer.removeBadgeRect(iconX, iconY).contains(mouseX, mouseY);
		g.pose().pushPose();
		g.pose().translate(0, 0, 160);
		UiSkinRenderer.drawRemoveBadge(g, iconX, iconY, badgeHovered);
		g.pose().popPose();
	}

	/** Remove-× hot-zone for the cell at {@code idx}; clicks elsewhere in the cell do nothing. */
	private boolean isOverRemoveBadge(EditorLayout layout, int idx, int cellTop, double mouseX, double mouseY) {
		int cellX = layout.rightGridX() + (idx % layout.rightCols()) * EditorLayout.ITEM_SIZE;
		return UiSkinRenderer.removeBadgeRect(cellX + 1, cellTop + 1).contains(mouseX, mouseY);
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
			int y = layout.gridTop() + visRow * EditorLayout.ITEM_SIZE;
			// The removal hot-zone is the hover × badge only;
			// clicking anywhere else in a cell consumes the click without removing.
			if (sections.isItemRow(vRow)) {
				int idx = EditorGridTraversal.findRowCellIndex(
					groupItems.size(), vRow, layout.rightCols(), layout.rightGridX(), y, mouseX, mouseY);
				if (idx < 0) continue;
				if (!state.canEditContents()) return true;
				ItemStack stack = groupItems.get(idx);
				boolean explicit = state.isExactSelected(stack) || state.isWholeItemSelected(stack);
				if (!explicit || !isOverRemoveBadge(layout, idx, y, mouseX, mouseY)) return true;
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
				if (state.isFluidSelected(fluidIngredient(fluid))
					&& isOverRemoveBadge(layout, idx, y, mouseX, mouseY)) {
					state.removeFluidSelection(fluidIngredient(fluid));
					onChange.run();
				}
				return true;
			} else if (sections.isGenericRow(vRow)) {
				int idx = EditorGridTraversal.findRowCellIndex(
					groupGenericIngredients.size(), sections.genericRow(vRow), layout.rightCols(), layout.rightGridX(), y, mouseX, mouseY);
				if (idx < 0) continue;
				if (!state.canEditContents()) return true;
				EditorGenericIngredientView entry = groupGenericIngredients.get(idx);
				if (state.isGenericSelected(entry)
					&& isOverRemoveBadge(layout, idx, y, mouseX, mouseY)) {
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
	List<EditorGenericIngredientView> groupGeneric() { return groupGenericIngredients; }

	String groupSummary() {
		return Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_ITEMS, groupItems.size()).getString()
			+ ", " + Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_FLUIDS, groupFluids.size()).getString()
			+ ", " + Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_GENERIC, groupGenericIngredients.size()).getString();
	}

	private static Object fluidIngredient(EditorFluidIngredientView fluid) {
		return fluid.ingredient();
	}
}
