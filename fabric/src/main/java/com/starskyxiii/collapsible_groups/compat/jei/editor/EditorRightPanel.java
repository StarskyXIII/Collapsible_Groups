package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;
import com.starskyxiii.collapsible_groups.compat.jei.ui.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Manages the right pane of {@link GroupEditorScreen}: the resolved item members
 * of the group currently being edited.
 * Item-only variant (no fluid/generic support on Forge).
 */
final class EditorRightPanel {

	// -----------------------------------------------------------------------
	// Group content
	// -----------------------------------------------------------------------

	private List<ItemStack> groupItems = List.of();

	// -----------------------------------------------------------------------
	// Scroll / hover
	// -----------------------------------------------------------------------

	int scrollRow   = 0;
	int hoveredItem = -1;

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
	}

	// -----------------------------------------------------------------------
	// Scroll helpers
	// -----------------------------------------------------------------------

	int totalRows(EditorLayout layout) {
		return EditorLayout.totalRows(groupItems.size(), layout.rightCols());
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
		hoveredItem = -1;

		g.enableScissor(layout.rightGridX(), layout.gridTop(),
			layout.rightGridX() + layout.rightGridWidth(), layout.gridTop() + layout.gridHeight());
		try {
			for (int visRow = 0; visRow < layout.rightRows(); visRow++) {
				int vRow = scrollRow + visRow;
				int y    = layout.gridTop() + visRow * EditorLayout.ITEM_SIZE;
				if (vRow >= EditorLayout.totalRows(groupItems.size(), layout.rightCols())) break;
				renderItemRow(g, mouseX, mouseY, layout, vRow, y);
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

		int itemRows = EditorLayout.totalRows(groupItems.size(), layout.rightCols());
		for (int visRow = 0; visRow < layout.rightRows(); visRow++) {
			int vRow = scrollRow + visRow;
			if (vRow >= itemRows) break;
			int y        = layout.gridTop() + visRow * EditorLayout.ITEM_SIZE;
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

	String groupSummary() {
		return Component.translatable(ModTranslationKeys.EDITOR_SUMMARY_ITEMS, groupItems.size()).getString();
	}
}
