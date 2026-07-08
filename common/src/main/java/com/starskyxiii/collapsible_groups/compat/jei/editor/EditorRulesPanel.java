package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.oreui.RuleNodePaths;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.RuleNodePresentation;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.RuleNodeUiContract;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.RuleFieldRole;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.RuleTagResolution;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorChrome;
import com.starskyxiii.collapsible_groups.compat.jei.ui.OreUiPalette;
import com.starskyxiii.collapsible_groups.compat.jei.ui.OreUiRenderer;
import com.starskyxiii.collapsible_groups.compat.jei.ui.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rules tab of the group editor: a nested condition-builder with per-row chips,
 * collapse-aware group blocks, and modal value pickers / field editors.
 *
 * <p>Owns its widgets and input routing; talks back to the Screen only via the
 * {@code onChanged} callback. All modals render at Z=300 (below the confirm
 * dialog's Z=500) and are mutually exclusive; while any modal is open the
 * Screen routes every event here via {@link #isModalOpen()}.
 */
final class EditorRulesPanel {

	// ── Layout constants ──────────────────────────────────────────────────
	private static final int PAD = 4;
	private static final int GAP = 6;
	private static final int TOOLBAR_H = 20;
	private static final int ROW_H = 20;
	private static final int ROW_GAP = 2;
	private static final int INDENT_W = 12;
	private static final int BAR_W = 3;
	private static final int CHIP_H = 12;
	private static final int CHIP_PAD = 3;
	private static final int CHEVRON_W = 7;
	private static final int ICON_BTN_W = 20;
	private static final int ICON_BTN_H = 20;
	private static final int ICON_BTN_GAP = 2;
	private static final int PICKER_ROW_H = 12;
	private static final int FIELD_H = 20;
	private static final int FIELD_GAP = 4;
	// Sprite (ore_button) nine-slice art is 20px tall; using anything less crops the frame.
	// Menu row spacing (BTN_H + 2) and modalRect height math both key off this constant.
	private static final int BTN_H = 20;
	private static final int MODAL_Z = 300;
	private static final long DOUBLE_CLICK_MS = 350;
	private static final long FILTER_DEBOUNCE_MS = 100;

	// ── Colors ────────────────────────────────────────────────────────────
	private static final int CHIP_POSITIVE = 0xFF3C8527;
	private static final int CHIP_POSITIVE_HOVER = 0xFF4E9E33;
	private static final int CHIP_NEUTRAL = 0xFF58585A;
	private static final int COL_UNRESOLVED = 0xFFE88A82;
	private static final int COL_ROW_SELECTED = 0x334488AA;
	private static final int COL_ROW_HOVER = 0x18FFFFFF;
	private static final int COL_PICKER_HOVER = 0x33FFFFFF;
	private static final int COL_MODAL_DIM = 0x99060A12;
	private static final int COL_DROP_TARGET = 0x553C8527;
	private static final int COL_GHOST_BG = 0xE01A1D24;

	private enum ModalKind { NONE, MENU, PICKER, FORM }

	private record MenuEntry(
		GroupFilterRuleDraft.NodeKind kind,
		@Nullable String presetType,
		boolean wrap
	) {}

	private static final List<MenuEntry> CONDITION_ENTRIES = List.of(
		new MenuEntry(GroupFilterRuleDraft.NodeKind.ID, "item", false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.ID, "fluid", false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.TAG, "item", false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.TAG, "fluid", false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.BLOCK_TAG, null, false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.NAMESPACE, "item", false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.ITEM_PATH_STARTS_WITH, null, false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.ITEM_PATH_CONTAINS, null, false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.ITEM_PATH_ENDS_WITH, null, false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.HAS_COMPONENT, null, false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.COMPONENT_PATH, null, false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.EXACT_STACK, null, false)
	);
	private static final List<MenuEntry> GROUP_INSERT_ENTRIES = List.of(
		new MenuEntry(GroupFilterRuleDraft.NodeKind.ALL, null, false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.ANY, null, false),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.NOT, null, false)
	);
	private static final List<MenuEntry> GROUP_WRAP_ENTRIES = List.of(
		new MenuEntry(GroupFilterRuleDraft.NodeKind.ALL, null, true),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.ANY, null, true),
		new MenuEntry(GroupFilterRuleDraft.NodeKind.NOT, null, true)
	);

	private record Row(GroupFilterRuleDraft.Node node, int depth, String path, boolean collapsed) {}

	// ── Drop-slot model (P6b-2) ───────────────────────────────────────────
	// A resolved drop position while a reparent drag is live.
	//   INTO(parent)        → drop lands at the tail of parent's children (P6b behaviour).
	//   BETWEEN(parent, i)  → insert as parent's i-th child, i in *pre-detach* caller
	//                         coordinates (moveNode reinterprets it after detach).
	// depth is the *inserted* node's indent depth, used to place the insertion line's x.
	// Package-private (not private) so EditorRulesPanelDropSlotTest can exercise resolveSlot.
	enum SlotKind { INTO, BETWEEN }

	record DropSlot(SlotKind kind, GroupFilterRuleDraft.Node parent, int index, int depth) {}

	/**
	 * Pure band → slot decision. Takes only plain data (no GuiGraphics / MC types) so it is
	 * covered directly by common tests. Returns the geometric slot candidate, or {@code null}
	 * for a suppressed no-op. Cycle/capacity validity ({@code canMove(drag, slot.parent)}) is
	 * applied by the caller — this function only decides the band geometry and the
	 * {oldIndex, oldIndex+1} no-op suppression against pre-detach coordinates.
	 *
	 * @param f                 vertical fraction within the row, {@code (my-rowTop)/ROW_H}; may
	 *                          be {@code >= 1.0} when the pointer sits in the ROW_GAP band below.
	 * @param node              the hovered row's node.
	 * @param nodeParent        {@code node.parent()} (null only for the root).
	 * @param indexInParent     {@code nodeParent.children().indexOf(node)}; ignored for root.
	 * @param nodeDepth         the hovered row's indent depth.
	 * @param hasVisibleChildren expanded compound with at least one visible child row.
	 * @param dragParent        the dragged node's current parent (for no-op suppression).
	 * @param dragOldIndex      the dragged node's current index within {@code dragParent}.
	 */
	static DropSlot resolveSlot(
			double f,
			GroupFilterRuleDraft.Node node,
			GroupFilterRuleDraft.Node nodeParent,
			int indexInParent,
			int nodeDepth,
			boolean hasVisibleChildren,
			GroupFilterRuleDraft.Node dragParent,
			int dragOldIndex) {
		boolean compound = node.kind().compound();
		boolean isRoot = nodeParent == null;
		DropSlot slot;
		if (compound) {
			// Root has no top band: its whole upper half folds into the INTO(root) middle band.
			double top = isRoot ? 0.0 : 0.25;
			if (f < top) {
				// Insert as a sibling directly above this compound.
				slot = new DropSlot(SlotKind.BETWEEN, nodeParent, indexInParent, nodeDepth);
			} else if (f < 0.75) {
				// Drop into this compound (tail append — the P6b behaviour).
				slot = new DropSlot(SlotKind.INTO, node, 0, nodeDepth);
			} else if (hasVisibleChildren) {
				// Lower band of an expanded, non-empty compound → become its first child, which
				// matches the visual gap opening just under the compound header.
				slot = new DropSlot(SlotKind.BETWEEN, node, 0, nodeDepth + 1);
			} else {
				// Collapsed or empty compound → insert as a sibling directly below it.
				slot = new DropSlot(SlotKind.BETWEEN, nodeParent, indexInParent + 1, nodeDepth);
			}
		} else {
			// Leaf row: upper half inserts above, lower half (incl. gap band) below.
			int idx = f < 0.5 ? indexInParent : indexInParent + 1;
			slot = new DropSlot(SlotKind.BETWEEN, nodeParent, idx, nodeDepth);
		}
		// No-op suppression: a BETWEEN that would drop the node back into one of the two gaps
		// bordering its current slot has no effect. Compared in pre-detach coordinates.
		if (slot.kind() == SlotKind.BETWEEN
				&& slot.parent() == dragParent
				&& (slot.index() == dragOldIndex || slot.index() == dragOldIndex + 1)) {
			return null;
		}
		return slot;
	}

	// ── Dependencies ──────────────────────────────────────────────────────
	private final EditorRulesState state;
	private final Font font;
	private final Runnable onChanged;

	// ── Body rect ─────────────────────────────────────────────────────────
	private int bodyX, bodyY, bodyW, bodyH;

	// ── List state ────────────────────────────────────────────────────────
	private final Set<String> collapsedPaths = new HashSet<>();
	private int scrollOffset;
	private boolean draggingScroll;
	private double dragStartY;
	private int dragStartOffset;

	// ── Node reparent drag ────────────────────────────────────────────────
	// dragCandidate is armed on a bare row-body press (glyph / chip / edit / delete /
	// scrollbar hits are excluded so they fire their own action instead). It promotes to
	// dragNode only once the pointer travels past DRAG_THRESHOLD, preserving single-click
	// selection semantics for taps that never move.
	private static final int DRAG_THRESHOLD = 4;
	@Nullable
	private GroupFilterRuleDraft.Node dragCandidate;
	@Nullable
	private GroupFilterRuleDraft.Node dragNode;
	@Nullable
	private DropSlot dropSlot;
	// Screen-space Y of the BETWEEN insertion line (undefined for INTO / null slot).
	private int dropLineY;
	private double dragOriginX;
	private double dragOriginY;

	// ── Modal state ───────────────────────────────────────────────────────
	private ModalKind modal = ModalKind.NONE;
	private boolean menuGroupMode;
	private int modalScrollOffset;
	private boolean modalDragging;
	private double modalDragY;
	private int modalDragStart;

	// ── Edit lifecycle (deleteOnCancel / snapshot-restore) ────────────────
	private GroupFilterRuleDraft.Node editingNode;
	private boolean editingIsNew;
	private String snapType = "";
	private String snapPrimary = "";
	private String snapSecondary = "";
	private String snapTertiary = "";

	// ── Picker state ──────────────────────────────────────────────────────
	private RuleNodePresentation.PickerKind pickerKind = RuleNodePresentation.PickerKind.NONE;
	private List<String> pickerSnapshot = List.of();
	private List<String> pickerFiltered = List.of();
	private boolean pickerHasFallback;
	private String pickerFallbackValue = "";
	private int pickerSelected = -1;
	private EditBox pickerSearch;
	private boolean pickerFilterDirty;
	private long pickerFilterDeadline;
	private long lastPickerClickMs;
	private int lastPickerClickIndex = -1;
	private final Map<RuleNodePresentation.PickerKind, String> pickerLastSearch =
		new EnumMap<>(RuleNodePresentation.PickerKind.class);
	// True when the currently open PICKER modal was opened from a field-level "…" button
	// (as opposed to beginEditor's top-level picker-vs-form branch); its four exit paths
	// (confirm / Esc / cancel button / click-outside) all return to FORM instead of
	// closing the editor. See confirmPickerSelection, keyPressed, pickerMouseClicked.
	private boolean pickerReturnsToForm;
	@Nullable
	private RuleFieldRole pickerTargetField;

	// ── Field form state ──────────────────────────────────────────────────
	private EditBox formType;
	private EditBox formPrimary;
	private EditBox formSecondary;
	private EditBox formTertiary;
	private int formVisibleFields;
	private EditBox focusedField;
	private boolean updatingFields;
	// Field-level "…" picker entry point. Only ever targets PRIMARY_VALUE today
	// (componentTypeId on HAS_COMPONENT / COMPONENT_PATH); kept generic for future reuse.
	private boolean formPrimaryHasPickerButton;
	private final Set<RuleFieldRole> formInvalidRoles = new HashSet<>();

	EditorRulesPanel(EditorRulesState state, Font font, Runnable onChanged) {
		this.state = state;
		this.font = font;
		this.onChanged = onChanged;
	}

	// ─────────────────────────────────────────────────────────────────────
	// Lifecycle
	// ─────────────────────────────────────────────────────────────────────

	void init(int bodyX, int bodyY, int bodyW, int bodyH) {
		this.bodyX = bodyX;
		this.bodyY = bodyY;
		this.bodyW = bodyW;
		this.bodyH = bodyH;
		abortModal();
		clampScroll();
	}

	void onActivate() {
		state.ensureRuleSelection();
		abortModal();
		clampScroll();
	}

	void onDeactivate() {
		abortModal();
	}

	void clearFocus() {
		if (focusedField != null) {
			focusedField.setFocused(false);
			focusedField = null;
		}
	}

	boolean isModalOpen() {
		return modal != ModalKind.NONE;
	}

	void onGroupChanged() {
		state.ensureRuleSelection();
		clampScroll();
	}

	/**
	 * Closes any modal, cancelling a pending new node / restoring snapshots first.
	 * Called from init/resize/tab-switch: even when a field-level picker is nested on
	 * top of the form (pickerReturnsToForm), this is a full cancel of the whole editor —
	 * that nested-vs-top-level distinction only matters for the picker's own four exit
	 * paths (see confirmPickerSelection / keyPressed / pickerMouseClicked).
	 */
	private void abortModal() {
		if (modal == ModalKind.PICKER || modal == ModalKind.FORM) {
			cancelEditor();
		}
		modal = ModalKind.NONE;
		modalScrollOffset = 0;
		modalDragging = false;
		pickerSearch = null;
		pickerReturnsToForm = false;
		pickerTargetField = null;
		formType = formPrimary = formSecondary = formTertiary = null;
		formInvalidRoles.clear();
		focusedField = null;
		clearDrag();
	}

	// ─────────────────────────────────────────────────────────────────────
	// Rows
	// ─────────────────────────────────────────────────────────────────────

	private List<Row> buildRows() {
		List<Row> rows = new ArrayList<>();
		int skipDeeperThan = Integer.MAX_VALUE;
		for (GroupFilterRuleDraft.FlatNode flat : state.flattenedRuleNodes()) {
			if (flat.depth() > skipDeeperThan) {
				continue;
			}
			skipDeeperThan = Integer.MAX_VALUE;
			String path = RuleNodePaths.pathOf(flat.node());
			boolean compound = flat.node().kind().compound();
			boolean collapsed = compound && collapsedPaths.contains(path);
			rows.add(new Row(flat.node(), flat.depth(), path, collapsed));
			if (collapsed) {
				skipDeeperThan = flat.depth();
			}
		}
		return rows;
	}

	private EditorChrome.Rect listRect() {
		int top = bodyY + TOOLBAR_H + GAP;
		int width = bodyW - ScrollbarHelper.WIDTH - ScrollbarHelper.GAP;
		int height = bodyH - TOOLBAR_H - GAP;
		return new EditorChrome.Rect(bodyX, top, Math.max(24, width), Math.max(24, height));
	}

	private int contentHeight(List<Row> rows) {
		return PAD + rows.size() * (ROW_H + ROW_GAP);
	}

	private void clampScroll() {
		int max = Math.max(0, contentHeight(buildRows()) - listRect().height());
		scrollOffset = ScrollbarHelper.clamp(scrollOffset, 0, max);
	}

	private int rowIndent(Row row) {
		return listRect().x() + PAD + row.depth() * INDENT_W;
	}

	// Shared row geometry — single source for render and hit-testing.

	private int chipX(Row row) {
		int x = rowIndent(row);
		return row.node().kind().compound() ? x + 11 : x + BAR_W + 3;
	}

	private String chipLabel(GroupFilterRuleDraft.Node node) {
		return Component.translatable(
			RuleNodePresentation.chipLabelKey(node.kind(), node.ingredientType())).getString();
	}

	private int chipWidth(GroupFilterRuleDraft.Node node) {
		int width = font.width(chipLabel(node)) + CHIP_PAD * 2;
		return chipCyclable(node) ? width + CHEVRON_W : width;
	}

	private boolean chipCyclable(GroupFilterRuleDraft.Node node) {
		return node.kind() == GroupFilterRuleDraft.NodeKind.ALL
			|| node.kind() == GroupFilterRuleDraft.NodeKind.ANY;
	}

	private int deleteButtonX(EditorChrome.Rect list) {
		return list.right() - PAD - ICON_BTN_W;
	}

	private int editButtonX(EditorChrome.Rect list) {
		return deleteButtonX(list) - ICON_BTN_GAP - ICON_BTN_W;
	}

	private void cycleOperator(GroupFilterRuleDraft.Node node) {
		if (!chipCyclable(node)) {
			return;
		}
		node.setKind(node.kind() == GroupFilterRuleDraft.NodeKind.ALL
			? GroupFilterRuleDraft.NodeKind.ANY
			: GroupFilterRuleDraft.NodeKind.ALL);
		state.selectRuleNode(node);
		state.markRulesChanged();
		onChanged.run();
	}

	// ─────────────────────────────────────────────────────────────────────
	// Render
	// ─────────────────────────────────────────────────────────────────────

	void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		boolean modalUp = isModalOpen();
		int listMouseX = modalUp ? Integer.MIN_VALUE : mouseX;
		int listMouseY = modalUp ? Integer.MIN_VALUE : mouseY;
		renderToolbar(g, listMouseX, listMouseY);
		renderList(g, listMouseX, listMouseY);
		// Drag ghost tag follows the pointer, drawn last and outside any scissor so it can
		// float over the whole body. Modal dimming/drawing is now hoisted to the full-screen
		// layer by the Screen via renderModals(), so nothing modal-related happens here.
		if (dragNode != null) {
			renderDragGhost(g, mouseX, mouseY);
		}
	}

	/**
	 * Full-screen modal layer: dims the entire screen (header / footer / preview included)
	 * then draws the active rules modal on top. Called by the Screen at the shell's top Z,
	 * mirroring the LOOK-mode settings precedent, so the dim is no longer confined to the
	 * rules body. {@link #render} intentionally no longer paints modals or a body-only dim.
	 */
	void renderModals(GuiGraphics g, int screenW, int screenH, int mouseX, int mouseY) {
		if (!isModalOpen()) {
			return;
		}
		g.pose().pushPose();
		g.pose().translate(0, 0, MODAL_Z);
		g.fill(0, 0, screenW, screenH, COL_MODAL_DIM);
		switch (modal) {
			case MENU -> renderMenu(g, mouseX, mouseY);
			case PICKER -> renderPicker(g, mouseX, mouseY);
			case FORM -> renderForm(g, mouseX, mouseY);
			default -> {}
		}
		g.pose().popPose();
	}

	private void renderDragGhost(GuiGraphics g, int mouseX, int mouseY) {
		if (dragNode == null) {
			return;
		}
		String label = chipLabel(dragNode);
		int w = font.width(label) + 8;
		int h = font.lineHeight + 4;
		int gx = mouseX + 8;
		int gy = mouseY + 8;
		g.pose().pushPose();
		g.pose().translate(0, 0, MODAL_Z);
		g.fill(gx, gy, gx + w, gy + h, COL_GHOST_BG);
		OreUiRenderer.drawOutline(g, gx, gy, w, h, OreUiPalette.OUTLINE_SELECTED);
		g.drawString(font, label, gx + 4, gy + 2, OreUiPalette.TEXT_SELECTED, false);
		g.pose().popPose();
	}

	private void renderToolbar(GuiGraphics g, int mouseX, int mouseY) {
		EditorChrome.Rect addCond = addConditionRect();
		EditorChrome.Rect addGroup = addGroupRect();
		OreUiRenderer.drawButton(g, font, addCond.x(), addCond.y(), addCond.width(), addCond.height(),
			Component.translatable(ModTranslationKeys.EDITOR_RULES_ADD_CONDITION).getString(),
			buttonState(state.canInsertRuleRelative(), addCond.contains(mouseX, mouseY)));
		OreUiRenderer.drawButton(g, font, addGroup.x(), addGroup.y(), addGroup.width(), addGroup.height(),
			Component.translatable(ModTranslationKeys.EDITOR_RULES_ADD_GROUP).getString(),
			buttonState(canOpenGroupMenu(), addGroup.contains(mouseX, mouseY)));
		renderToolbarInfo(g, addCond);
	}

	/** Read-only "<root operator> · N rules" label, anchored to the toolbar's left edge. */
	private void renderToolbarInfo(GuiGraphics g, EditorChrome.Rect firstButton) {
		List<GroupFilterRuleDraft.FlatNode> flat = state.flattenedRuleNodes();
		if (flat.isEmpty()) {
			return;
		}
		GroupFilterRuleDraft.Node root = flat.getFirst().node();
		String rootChip = chipLabel(root);
		int leafCount = RuleNodePresentation.countLeafRules(flat);
		String text = Component.translatable(ModTranslationKeys.EDITOR_RULES_TOOLBAR_INFO, rootChip, leafCount)
			.getString();
		int maxWidth = Math.max(0, firstButton.x() - GAP - (bodyX + PAD));
		int ty = bodyY + (TOOLBAR_H - font.lineHeight) / 2 + 1;
		g.drawString(font, font.plainSubstrByWidth(text, maxWidth), bodyX + PAD, ty, OreUiPalette.TEXT_MUTED, false);
	}

	private boolean canOpenGroupMenu() {
		if (state.canInsertRuleRelative()) {
			return true;
		}
		for (MenuEntry entry : GROUP_WRAP_ENTRIES) {
			if (state.canWrapSelectedRule(entry.kind())) {
				return true;
			}
		}
		return false;
	}

	private EditorChrome.Rect addGroupRect() {
		String label = Component.translatable(ModTranslationKeys.EDITOR_RULES_ADD_GROUP).getString();
		int width = font.width(label) + 16;
		return new EditorChrome.Rect(bodyX + bodyW - width, bodyY, width, TOOLBAR_H);
	}

	private EditorChrome.Rect addConditionRect() {
		String label = Component.translatable(ModTranslationKeys.EDITOR_RULES_ADD_CONDITION).getString();
		int width = font.width(label) + 16;
		return new EditorChrome.Rect(addGroupRect().x() - GAP - width, bodyY, width, TOOLBAR_H);
	}

	private void renderList(GuiGraphics g, int mouseX, int mouseY) {
		EditorChrome.Rect list = listRect();
		List<Row> rows = buildRows();

		if (rows.isEmpty()) {
			String empty = Component.translatable(ModTranslationKeys.EDITOR_RULES_NO_ROOT).getString();
			g.drawString(font, font.plainSubstrByWidth(empty, list.width()),
				list.x() + PAD, list.y() + PAD, OreUiPalette.TEXT_HINT, false);
			return;
		}

		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		try {
			int y = list.y() + PAD - scrollOffset;
			for (Row row : rows) {
				if (y + ROW_H >= list.y() && y <= list.bottom()) {
					renderRow(g, list, row, y, mouseX, mouseY);
				}
				y += ROW_H + ROW_GAP;
			}
			renderAccentBars(g, list, rows);
			// BETWEEN drop line (P6b-2): 2px accent between rows, indented to the inserted node's
			// level. INTO uses the row highlight in renderRow instead. Kept inside the list scissor.
			if (dropSlot != null && dropSlot.kind() == SlotKind.BETWEEN) {
				int lineX = list.x() + PAD + dropSlot.depth() * INDENT_W;
				g.fill(lineX, dropLineY, list.right() - PAD, dropLineY + 2, OreUiPalette.BUTTON_PRIMARY);
			}
		} finally {
			g.disableScissor();
		}

		ScrollbarHelper.renderPixels(g, list.right() + ScrollbarHelper.GAP, list.y(), list.height(),
			list.height(), contentHeight(rows), scrollOffset);
	}

	private void renderAccentBars(GuiGraphics g, EditorChrome.Rect list, List<Row> rows) {
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			if (!row.node().kind().compound() || row.collapsed()) {
				continue;
			}
			int last = i;
			for (int j = i + 1; j < rows.size() && rows.get(j).depth() > row.depth(); j++) {
				last = j;
			}
			if (last == i) {
				continue;
			}
			int barX = rowIndent(row);
			int top = list.y() + PAD - scrollOffset + i * (ROW_H + ROW_GAP) + ROW_H;
			int bottom = list.y() + PAD - scrollOffset + last * (ROW_H + ROW_GAP) + ROW_H;
			int color = row.node().kind() == GroupFilterRuleDraft.NodeKind.NOT
				? OreUiPalette.DANGER
				: CHIP_POSITIVE;
			g.fill(barX, top, barX + BAR_W, bottom, color);
		}
	}

	private void renderRow(GuiGraphics g, EditorChrome.Rect list, Row row, int y, int mouseX, int mouseY) {
		GroupFilterRuleDraft.Node node = row.node();
		boolean selected = node == state.selectedRuleNode();
		boolean rowHover = mouseY >= y && mouseY < y + ROW_H && mouseX >= list.x() && mouseX < list.right();
		boolean intoTarget = dropSlot != null && dropSlot.kind() == SlotKind.INTO && dropSlot.parent() == node;
		if (intoTarget) {
			g.fill(list.x() + 1, y, list.right() - 1, y + ROW_H, COL_DROP_TARGET);
			OreUiRenderer.drawOutline(g, list.x() + 1, y, list.width() - 2, ROW_H, CHIP_POSITIVE);
		} else if (selected) {
			g.fill(list.x() + 1, y, list.right() - 1, y + ROW_H, COL_ROW_SELECTED);
		} else if (rowHover) {
			g.fill(list.x() + 1, y, list.right() - 1, y + ROW_H, COL_ROW_HOVER);
		}

		boolean unresolved = !node.kind().compound()
			&& RuleTagResolution.isUnresolved(node, RuleTagResolution.RegistryLookup.INSTANCE);

		if (node.kind().compound()) {
			renderCollapseGlyph(g, rowIndent(row), y, row.collapsed());
		}

		// Type chip; ALL/ANY chips cycle the operator on click and carry a chevron marker.
		int x = chipX(row);
		String chip = chipLabel(node);
		int chipW = chipWidth(node);
		int chipY = y + (ROW_H - CHIP_H) / 2;
		boolean chipHover = chipCyclable(node) && hoverIn(mouseX, mouseY, x, chipY, chipW, CHIP_H);
		int chipColor = switch (RuleNodePresentation.blockAccent(node.kind())) {
			case POSITIVE -> chipHover ? CHIP_POSITIVE_HOVER : CHIP_POSITIVE;
			case NEGATIVE -> OreUiPalette.DANGER;
			case NONE -> CHIP_NEUTRAL;
		};
		g.fill(x, chipY, x + chipW, chipY + CHIP_H, chipColor);
		g.drawString(font, chip, x + CHIP_PAD, chipY + (CHIP_H - font.lineHeight) / 2 + 1,
			OreUiPalette.TEXT_SELECTED, false);
		if (chipCyclable(node)) {
			int ax = x + chipW - CHIP_PAD - 5;
			int ay = chipY + (CHIP_H - 3) / 2;
			g.fill(ax, ay, ax + 5, ay + 1, OreUiPalette.TEXT_SELECTED);
			g.fill(ax + 1, ay + 1, ax + 4, ay + 2, OreUiPalette.TEXT_SELECTED);
			g.fill(ax + 2, ay + 2, ax + 3, ay + 3, OreUiPalette.TEXT_SELECTED);
		}
		x += chipW + 6;

		// Icons (right-aligned, Manager action-rail primitive)
		int iconY = y + (ROW_H - ICON_BTN_H) / 2;
		int deleteX = deleteButtonX(list);
		OreUiRenderer.drawToolbarIconButton(g, deleteX, iconY, ICON_BTN_W, ICON_BTN_H, OreUiRenderer.ICON_DELETE,
			buttonState(true, hoverIn(mouseX, mouseY, deleteX, iconY, ICON_BTN_W, ICON_BTN_H)));
		int textRight;
		if (node.kind().compound()) {
			textRight = deleteX - 6;
		} else {
			int editX = editButtonX(list);
			OreUiRenderer.drawToolbarIconButton(g, editX, iconY, ICON_BTN_W, ICON_BTN_H, OreUiRenderer.ICON_EDIT,
				buttonState(true, hoverIn(mouseX, mouseY, editX, iconY, ICON_BTN_W, ICON_BTN_H)));
			textRight = editX - 6;
		}

		int textY = y + (ROW_H - font.lineHeight) / 2 + 1;
		if (node.kind().compound()) {
			boolean empty = node.children().isEmpty();
			String count = empty
				? Component.translatable(ModTranslationKeys.EDITOR_RULES_EMPTY_GROUP).getString()
				: Component.translatable(ModTranslationKeys.EDITOR_RULES_CHILD_COUNT, node.children().size()).getString();
			g.drawString(font, font.plainSubstrByWidth(count, Math.max(0, textRight - x)),
				x, textY, empty ? COL_UNRESOLVED : OreUiPalette.TEXT_MUTED, false);
			if (empty) {
				OreUiRenderer.drawOutline(g, list.x() + 1, y, list.width() - 2, ROW_H, OreUiPalette.DANGER);
			}
		} else {
			String warn = unresolved
				? Component.translatable(ModTranslationKeys.EDITOR_RULES_UNRESOLVED_ROW).getString()
				: "";
			int warnW = warn.isEmpty() ? 0 : font.width(warn) + 6;
			String value = RuleNodePresentation.valueText(node);
			g.drawString(font, font.plainSubstrByWidth(value, Math.max(0, textRight - x - warnW)),
				x, textY, OreUiPalette.TEXT_PRIMARY, false);
			if (!warn.isEmpty()) {
				g.drawString(font, warn, textRight - font.width(warn), textY, COL_UNRESOLVED, false);
				OreUiRenderer.drawOutline(g, list.x() + 1, y, list.width() - 2, ROW_H, OreUiPalette.DANGER);
			}
		}
	}

	private int renderCollapseGlyph(GuiGraphics g, int x, int y, boolean collapsed) {
		int cy = y + ROW_H / 2;
		int color = OreUiPalette.TEXT_MUTED;
		if (collapsed) {
			g.fill(x, cy - 3, x + 1, cy + 4, color);
			g.fill(x + 1, cy - 2, x + 2, cy + 3, color);
			g.fill(x + 2, cy - 1, x + 3, cy + 2, color);
			g.fill(x + 3, cy, x + 4, cy + 1, color);
		} else {
			g.fill(x - 1, cy - 1, x + 6, cy, color);
			g.fill(x, cy, x + 5, cy + 1, color);
			g.fill(x + 1, cy + 1, x + 4, cy + 2, color);
			g.fill(x + 2, cy + 2, x + 3, cy + 3, color);
		}
		return 7;
	}

	// ─────────────────────────────────────────────────────────────────────
	// Modal geometry (shared)
	// ─────────────────────────────────────────────────────────────────────

	private EditorChrome.Rect modalRect(int desiredW, int desiredH) {
		int w = Math.min(bodyW - GAP * 2, desiredW);
		int h = Math.min(bodyH - GAP * 2, desiredH);
		int x = bodyX + (bodyW - w) / 2;
		int y = bodyY + (bodyH - h) / 2;
		return new EditorChrome.Rect(x, y, Math.max(60, w), Math.max(60, h));
	}

	private void drawModalPanel(GuiGraphics g, EditorChrome.Rect m, String title) {
		OreUiRenderer.drawPanel(g, m.x(), m.y(), m.width(), m.height());
		g.drawString(font, font.plainSubstrByWidth(title, m.width() - GAP * 2),
			m.x() + GAP, m.y() + GAP, OreUiPalette.TEXT_PRIMARY, false);
	}

	/**
	 * Shared field chrome (picker search box, form fields): double-layer background
	 * plus a three-state outline. Mirrors the Screen shell's {@code renderFieldChrome}
	 * visual vocabulary so modal text fields match the header's name field.
	 */
	private void drawFieldChrome(GuiGraphics g, EditorChrome.Rect rect, boolean focused, boolean hovered) {
		int outline = focused ? OreUiPalette.OUTLINE_SELECTED : hovered ? OreUiPalette.OUTLINE_HOVER : OreUiPalette.OUTLINE_DARK;
		g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), OreUiPalette.SURFACE_DARK);
		g.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1, OreUiPalette.SURFACE);
		OreUiRenderer.drawOutline(g, rect.x(), rect.y(), rect.width(), rect.height(), outline);
	}

	// ─────────────────────────────────────────────────────────────────────
	// Add menu
	// ─────────────────────────────────────────────────────────────────────

	private List<MenuEntry> menuEntries() {
		if (!menuGroupMode) {
			return CONDITION_ENTRIES;
		}
		List<MenuEntry> entries = new ArrayList<>(GROUP_INSERT_ENTRIES);
		if (state.selectedRuleNode() != null) {
			entries.addAll(GROUP_WRAP_ENTRIES);
		}
		return entries;
	}

	private String menuEntryLabel(MenuEntry entry) {
		String base = Component.translatable(
			RuleNodePresentation.chipLabelKey(entry.kind(), entry.presetType() == null ? "item" : entry.presetType()))
			.getString();
		if (entry.wrap()) {
			return Component.translatable(ModTranslationKeys.EDITOR_RULES_WRAP_HEADER).getString() + ": " + base;
		}
		return base;
	}

	private boolean menuEntryEnabled(MenuEntry entry) {
		return entry.wrap() ? state.canWrapSelectedRule(entry.kind()) : state.canInsertRuleRelative();
	}

	private EditorChrome.Rect menuModalRect() {
		int rows = menuEntries().size();
		int desiredH = GAP + font.lineHeight + 4 + rows * (BTN_H + 2) + font.lineHeight + GAP * 2;
		return modalRect(230, desiredH);
	}

	private EditorChrome.Rect menuListRect(EditorChrome.Rect m) {
		int top = m.y() + GAP + font.lineHeight + 4;
		int bottom = m.bottom() - GAP - font.lineHeight - 4;
		return new EditorChrome.Rect(m.x() + GAP, top,
			m.width() - GAP * 2 - ScrollbarHelper.WIDTH - ScrollbarHelper.GAP,
			Math.max(BTN_H, bottom - top));
	}

	private int menuContentHeight() {
		return menuEntries().size() * (BTN_H + 2);
	}

	private void renderMenu(GuiGraphics g, int mouseX, int mouseY) {
		EditorChrome.Rect m = menuModalRect();
		clampModalScroll(menuContentHeight(), menuListRect(m).height());
		String title = Component.translatable(menuGroupMode
			? ModTranslationKeys.EDITOR_RULES_COMPOUND_HEADER
			: ModTranslationKeys.EDITOR_RULES_BASIC_HEADER).getString();
		drawModalPanel(g, m, title);

		EditorChrome.Rect list = menuListRect(m);
		List<MenuEntry> entries = menuEntries();
		String hoverDesc = "";
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		try {
			int y = list.y() - modalScrollOffset;
			for (MenuEntry entry : entries) {
				boolean hovered = mouseX >= list.x() && mouseX < list.right() && mouseY >= y && mouseY < y + BTN_H
					&& mouseY >= list.y() && mouseY < list.bottom();
				OreUiRenderer.drawButton(g, font, list.x(), y, list.width(), BTN_H, menuEntryLabel(entry),
					buttonState(menuEntryEnabled(entry), hovered));
				if (hovered) {
					hoverDesc = Component.translatable(RuleNodePresentation.descriptionKey(entry.kind())).getString();
				}
				y += BTN_H + 2;
			}
		} finally {
			g.disableScissor();
		}
		ScrollbarHelper.renderPixels(g, list.right() + ScrollbarHelper.GAP, list.y(), list.height(),
			list.height(), menuContentHeight(), modalScrollOffset);

		g.drawString(font, font.plainSubstrByWidth(hoverDesc, m.width() - GAP * 2),
			m.x() + GAP, m.bottom() - GAP - font.lineHeight, OreUiPalette.TEXT_HINT, false);
	}

	private boolean menuMouseClicked(double mx, double my) {
		EditorChrome.Rect m = menuModalRect();
		if (!m.contains(mx, my)) {
			modal = ModalKind.NONE;
			return true;
		}
		EditorChrome.Rect list = menuListRect(m);
		if (handleModalScrollbarClick(mx, my, list, menuContentHeight())) {
			return true;
		}
		if (!list.contains(mx, my)) {
			return true;
		}
		// Row pitch must stay (BTN_H + 2), the same spacing renderMenu lays entries out with.
		int index = (int) ((my - list.y() + modalScrollOffset) / (BTN_H + 2));
		List<MenuEntry> entries = menuEntries();
		if (index < 0 || index >= entries.size()) {
			return true;
		}
		MenuEntry entry = entries.get(index);
		if (!menuEntryEnabled(entry)) {
			return true;
		}
		modal = ModalKind.NONE;
		modalScrollOffset = 0;
		if (entry.wrap()) {
			if (state.wrapSelectedRule(entry.kind()) != null) {
				onChanged.run();
			}
			return true;
		}
		if (entry.kind().compound()) {
			if (state.insertRuleRelative(entry.kind()) != null) {
				onChanged.run();
			}
			return true;
		}
		GroupFilterRuleDraft.Node node = state.insertRuleRelativePending(entry.kind());
		if (node == null) {
			return true;
		}
		if (entry.presetType() != null) {
			node.setIngredientType(entry.presetType());
		}
		beginEditor(node, true);
		onChanged.run();
		return true;
	}

	// ─────────────────────────────────────────────────────────────────────
	// Edit lifecycle
	// ─────────────────────────────────────────────────────────────────────

	private void beginEditor(GroupFilterRuleDraft.Node node, boolean isNew) {
		editingNode = node;
		editingIsNew = isNew;
		snapType = node.ingredientType();
		snapPrimary = node.primaryValue();
		snapSecondary = node.secondaryValue();
		snapTertiary = node.tertiaryValue();
		pickerKind = RuleNodePresentation.pickerKind(node.kind(), node.ingredientType());
		if (pickerKind != RuleNodePresentation.PickerKind.NONE) {
			openPicker();
		} else {
			openForm();
		}
	}

	/**
	 * Confirm path: values are already on the node (form) or applied by caller (picker).
	 * For FORM, required fields (per {@link RuleNodeUiContract#requiredRoles()}) must be
	 * non-blank first — a blank required field aborts the confirm and marks itself
	 * invalid (red outline) instead of closing the modal.
	 */
	private void confirmEditor() {
		if (modal == ModalKind.FORM && !validateFormRequiredFields()) {
			return;
		}
		if (editingIsNew) {
			state.commitPendingRuleNode();
		}
		editingNode = null;
		modal = ModalKind.NONE;
		pickerSearch = null;
		pickerReturnsToForm = false;
		pickerTargetField = null;
		formType = formPrimary = formSecondary = formTertiary = null;
		formInvalidRoles.clear();
		focusedField = null;
		state.markRulesChanged();
		onChanged.run();
	}

	/**
	 * Checks the current node's required fields (contract-driven, aligned with
	 * {@link com.starskyxiii.collapsible_groups.core.GroupFilterValidator}) and records
	 * which are blank into {@link #formInvalidRoles} for the red-outline hint.
	 * Returns true only when every required field is non-blank.
	 */
	private boolean validateFormRequiredFields() {
		formInvalidRoles.clear();
		if (editingNode == null) {
			return true;
		}
		RuleNodeUiContract contract = RuleNodeUiContract.forKind(editingNode.kind());
		if (contract.requiresField(RuleFieldRole.PRIMARY_VALUE) && editingNode.primaryValue().isBlank()) {
			formInvalidRoles.add(RuleFieldRole.PRIMARY_VALUE);
		}
		if (contract.requiresField(RuleFieldRole.SECONDARY_VALUE) && editingNode.secondaryValue().isBlank()) {
			formInvalidRoles.add(RuleFieldRole.SECONDARY_VALUE);
		}
		if (contract.requiresField(RuleFieldRole.TERTIARY_VALUE) && editingNode.tertiaryValue().isBlank()) {
			formInvalidRoles.add(RuleFieldRole.TERTIARY_VALUE);
		}
		return formInvalidRoles.isEmpty();
	}

	/** Cancel path: delete a pending new node, or restore the snapshot on an existing one. */
	private void cancelEditor() {
		if (editingNode == null) {
			return;
		}
		if (editingIsNew) {
			state.cancelPendingRuleNode();
		} else {
			editingNode.setIngredientType(snapType);
			editingNode.setPrimaryValue(snapPrimary);
			editingNode.setSecondaryValue(snapSecondary);
			editingNode.setTertiaryValue(snapTertiary);
			state.markRulesChanged();
		}
		editingNode = null;
		modal = ModalKind.NONE;
		pickerSearch = null;
		pickerReturnsToForm = false;
		pickerTargetField = null;
		formType = formPrimary = formSecondary = formTertiary = null;
		formInvalidRoles.clear();
		focusedField = null;
		onChanged.run();
	}

	// ─────────────────────────────────────────────────────────────────────
	// Picker modal
	// ─────────────────────────────────────────────────────────────────────

	private void openPicker() {
		modal = ModalKind.PICKER;
		modalScrollOffset = 0;
		pickerSnapshot = snapshotFor(pickerKind);
		EditorChrome.Rect search = pickerSearchRect(pickerModalRect());
		pickerSearch = new EditBox(font, search.x() + 4, search.y() + (search.height() - font.lineHeight) / 2,
			search.width() - 8, font.lineHeight + 2, Component.empty());
		pickerSearch.setBordered(false);
		pickerSearch.setMaxLength(256);
		pickerSearch.setHint(Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_SEARCH));
		pickerSearch.setValue(pickerLastSearch.getOrDefault(pickerKind, ""));
		pickerSearch.setResponder(value -> {
			pickerLastSearch.put(pickerKind, value);
			pickerFilterDirty = true;
			pickerFilterDeadline = System.currentTimeMillis() + FILTER_DEBOUNCE_MS;
		});
		pickerSearch.setFocused(true);
		focusedField = pickerSearch;
		refilterPicker();
		scrollPickerToCurrent();
	}

	private static List<String> snapshotFor(RuleNodePresentation.PickerKind kind) {
		return switch (kind) {
			case ITEM_TAG -> BuiltInRegistries.ITEM.getTagNames()
				.map(tag -> tag.location().toString()).sorted().toList();
			case FLUID_TAG -> BuiltInRegistries.FLUID.getTagNames()
				.map(tag -> tag.location().toString()).sorted().toList();
			case BLOCK_TAG -> BuiltInRegistries.BLOCK.getTagNames()
				.map(tag -> tag.location().toString()).sorted().toList();
			case NAMESPACE -> Stream.concat(
					BuiltInRegistries.ITEM.keySet().stream(),
					BuiltInRegistries.FLUID.keySet().stream())
				.map(ResourceLocation::getNamespace).distinct().sorted().toList();
			case DATA_COMPONENT_TYPE -> BuiltInRegistries.DATA_COMPONENT_TYPE.keySet().stream()
				.map(ResourceLocation::toString).sorted().toList();
			case NONE -> List.of();
		};
	}

	private void refilterPicker() {
		pickerFilterDirty = false;
		String query = pickerSearch == null ? "" : pickerSearch.getValue().trim();
		String lower = query.toLowerCase(Locale.ROOT);
		pickerFiltered = lower.isEmpty()
			? pickerSnapshot
			: pickerSnapshot.stream().filter(id -> id.contains(lower)).toList();
		pickerHasFallback = !query.isEmpty()
			&& !pickerSnapshot.contains(query)
			&& fallbackValueValid(query);
		pickerFallbackValue = pickerHasFallback ? query : "";
		int total = pickerFiltered.size() + (pickerHasFallback ? 1 : 0);
		pickerSelected = total > 0 ? Math.min(Math.max(pickerSelected, 0), total - 1) : -1;
		clampModalScroll(pickerContentHeight(), pickerListRect(pickerModalRect()).height());
	}

	private boolean fallbackValueValid(String query) {
		if (pickerKind == RuleNodePresentation.PickerKind.NAMESPACE) {
			for (int i = 0; i < query.length(); i++) {
				char c = query.charAt(i);
				if (!(c == '_' || c == '-' || c == '.' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
					return false;
				}
			}
			return !query.isEmpty();
		}
		return ResourceLocation.tryParse(query) != null;
	}

	private void scrollPickerToCurrent() {
		if (editingNode == null) {
			return;
		}
		int index = pickerFiltered.indexOf(editingNode.primaryValue());
		if (index >= 0) {
			int display = index + (pickerHasFallback ? 1 : 0);
			pickerSelected = display;
			EditorChrome.Rect list = pickerListRect(pickerModalRect());
			modalScrollOffset = Math.max(0, display * PICKER_ROW_H - list.height() / 2);
			clampModalScroll(pickerContentHeight(), list.height());
		} else {
			pickerSelected = pickerHasFallback ? 0 : -1;
			modalScrollOffset = 0;
		}
	}

	private String pickerTitle() {
		String key = switch (pickerKind) {
			case ITEM_TAG -> ModTranslationKeys.EDITOR_RULES_PICKER_TITLE_ITEM_TAG;
			case FLUID_TAG -> ModTranslationKeys.EDITOR_RULES_PICKER_TITLE_FLUID_TAG;
			case BLOCK_TAG -> ModTranslationKeys.EDITOR_RULES_PICKER_TITLE_BLOCK_TAG;
			case NAMESPACE -> ModTranslationKeys.EDITOR_RULES_PICKER_TITLE_NAMESPACE;
			case DATA_COMPONENT_TYPE -> ModTranslationKeys.EDITOR_RULES_PICKER_TITLE_DATA_COMPONENT_TYPE;
			case NONE -> ModTranslationKeys.EDITOR_RULES_EDIT_TITLE;
		};
		return Component.translatable(key).getString();
	}

	private EditorChrome.Rect pickerModalRect() {
		return modalRect(260, 210);
	}

	private EditorChrome.Rect pickerSearchRect(EditorChrome.Rect m) {
		return new EditorChrome.Rect(m.x() + GAP, m.y() + GAP + font.lineHeight + 4,
			m.width() - GAP * 2, 14);
	}

	private EditorChrome.Rect pickerListRect(EditorChrome.Rect m) {
		EditorChrome.Rect search = pickerSearchRect(m);
		int top = search.bottom() + 4;
		int bottom = m.bottom() - GAP - BTN_H - 4 - font.lineHeight - 2;
		return new EditorChrome.Rect(m.x() + GAP, top,
			m.width() - GAP * 2 - ScrollbarHelper.WIDTH - ScrollbarHelper.GAP,
			Math.max(PICKER_ROW_H, bottom - top));
	}

	private int pickerContentHeight() {
		return (pickerFiltered.size() + (pickerHasFallback ? 1 : 0)) * PICKER_ROW_H;
	}

	private EditorChrome.Rect pickerConfirmRect(EditorChrome.Rect m) {
		String label = Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_CONFIRM).getString();
		int width = Math.max(52, font.width(label) + 16);
		return new EditorChrome.Rect(m.right() - GAP - width, m.bottom() - GAP - BTN_H, width, BTN_H);
	}

	private EditorChrome.Rect pickerCancelRect(EditorChrome.Rect m) {
		String label = Component.translatable(ModTranslationKeys.BUTTON_CANCEL).getString();
		int width = Math.max(52, font.width(label) + 16);
		EditorChrome.Rect confirm = pickerConfirmRect(m);
		return new EditorChrome.Rect(confirm.x() - GAP - width, confirm.y(), width, BTN_H);
	}

	private void renderPicker(GuiGraphics g, int mouseX, int mouseY) {
		if (pickerFilterDirty && System.currentTimeMillis() >= pickerFilterDeadline) {
			refilterPicker();
		}
		EditorChrome.Rect m = pickerModalRect();
		drawModalPanel(g, m, pickerTitle());

		EditorChrome.Rect search = pickerSearchRect(m);
		boolean searchFocused = pickerSearch != null && pickerSearch.isFocused();
		boolean searchHovered = search.contains(mouseX, mouseY);
		drawFieldChrome(g, search, searchFocused, searchHovered);
		if (pickerSearch != null) {
			pickerSearch.render(g, mouseX, mouseY, 0);
		}

		EditorChrome.Rect list = pickerListRect(m);
		g.fill(list.x(), list.y(), list.right(), list.bottom(), OreUiPalette.SURFACE_DARK);
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		try {
			int y = list.y() - modalScrollOffset;
			int display = 0;
			if (pickerHasFallback) {
				renderPickerRow(g, list, y, display,
					Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_FALLBACK, pickerFallbackValue)
						.getString(),
					COL_UNRESOLVED, mouseX, mouseY);
				y += PICKER_ROW_H;
				display++;
			}
			for (String id : pickerFiltered) {
				if (y + PICKER_ROW_H >= list.y() && y <= list.bottom()) {
					renderPickerRow(g, list, y, display, id, OreUiPalette.TEXT_PRIMARY, mouseX, mouseY);
				}
				y += PICKER_ROW_H;
				display++;
			}
			if (display == 0) {
				g.drawString(font, Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_EMPTY).getString(),
					list.x() + PAD, list.y() + PAD, OreUiPalette.TEXT_HINT, false);
			}
		} finally {
			g.disableScissor();
		}
		ScrollbarHelper.renderPixels(g, list.right() + ScrollbarHelper.GAP, list.y(), list.height(),
			list.height(), pickerContentHeight(), modalScrollOffset);

		String countText = pickerFiltered.size() + " / " + pickerSnapshot.size();
		g.drawString(font, countText, list.x(), list.bottom() + 3, OreUiPalette.TEXT_HINT, false);

		EditorChrome.Rect confirm = pickerConfirmRect(m);
		EditorChrome.Rect cancel = pickerCancelRect(m);
		OreUiRenderer.drawButton(g, font, confirm.x(), confirm.y(), confirm.width(), confirm.height(),
			Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_CONFIRM).getString(),
			buttonState(pickerSelected >= 0, confirm.contains(mouseX, mouseY)));
		OreUiRenderer.drawButton(g, font, cancel.x(), cancel.y(), cancel.width(), cancel.height(),
			Component.translatable(ModTranslationKeys.BUTTON_CANCEL).getString(),
			buttonState(true, cancel.contains(mouseX, mouseY)));
	}

	private void renderPickerRow(GuiGraphics g, EditorChrome.Rect list, int y, int display, String text,
	                             int color, int mouseX, int mouseY) {
		boolean selected = display == pickerSelected;
		boolean hovered = list.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + PICKER_ROW_H;
		if (selected) {
			g.fill(list.x(), y, list.right(), y + PICKER_ROW_H, COL_ROW_SELECTED);
		} else if (hovered) {
			g.fill(list.x(), y, list.right(), y + PICKER_ROW_H, COL_PICKER_HOVER);
		}
		g.drawString(font, font.plainSubstrByWidth(text, list.width() - PAD * 2),
			list.x() + PAD, y + (PICKER_ROW_H - font.lineHeight) / 2 + 1, color, false);
	}

	private boolean pickerMouseClicked(double mx, double my) {
		EditorChrome.Rect m = pickerModalRect();
		if (!m.contains(mx, my)) {
			// Exit path: click outside the modal.
			cancelOrReturnPicker();
			return true;
		}
		EditorChrome.Rect search = pickerSearchRect(m);
		if (search.contains(mx, my) && pickerSearch != null) {
			pickerSearch.setFocused(true);
			focusedField = pickerSearch;
			pickerSearch.mouseClicked(mx, my, 0);
			return true;
		}
		EditorChrome.Rect list = pickerListRect(m);
		if (handleModalScrollbarClick(mx, my, list, pickerContentHeight())) {
			return true;
		}
		if (list.contains(mx, my)) {
			int display = (int) ((my - list.y() + modalScrollOffset) / PICKER_ROW_H);
			int total = pickerFiltered.size() + (pickerHasFallback ? 1 : 0);
			if (display >= 0 && display < total) {
				long now = System.currentTimeMillis();
				boolean doubleClick = display == lastPickerClickIndex
					&& now - lastPickerClickMs <= DOUBLE_CLICK_MS;
				lastPickerClickIndex = display;
				lastPickerClickMs = now;
				pickerSelected = display;
				if (doubleClick) {
					confirmPickerSelection();
				}
			}
			return true;
		}
		if (pickerConfirmRect(m).contains(mx, my)) {
			// Exit path: confirm button.
			confirmPickerSelection();
			return true;
		}
		if (pickerCancelRect(m).contains(mx, my)) {
			// Exit path: cancel button.
			cancelOrReturnPicker();
			return true;
		}
		return true;
	}

	/**
	 * Exit path: confirm (button, double-click, or Enter — see keyPressed). When the
	 * picker was opened from a field-level "…" button, writes the value back through
	 * {@link #setFormFieldValue} and reopens FORM instead of closing the whole editor.
	 */
	private void confirmPickerSelection() {
		if (editingNode == null || pickerSelected < 0) {
			return;
		}
		String value;
		if (pickerHasFallback && pickerSelected == 0) {
			value = pickerFallbackValue;
		} else {
			int index = pickerSelected - (pickerHasFallback ? 1 : 0);
			if (index < 0 || index >= pickerFiltered.size()) {
				return;
			}
			value = pickerFiltered.get(index);
		}
		if (pickerReturnsToForm) {
			RuleFieldRole target = pickerTargetField;
			pickerReturnsToForm = false;
			pickerTargetField = null;
			openForm();
			if (target != null) {
				setFormFieldValue(target, value);
			}
			return;
		}
		editingNode.setPrimaryValue(value);
		confirmEditor();
	}

	/**
	 * Exit path shared by Esc / cancel button / click-outside: when nested inside FORM
	 * via the field-level "…" button, discards the picker and returns to FORM unchanged
	 * (the field keeps whatever value it already had); otherwise this is a top-level
	 * picker (beginEditor's picker-vs-form branch) and cancels the whole pending edit.
	 */
	private void cancelOrReturnPicker() {
		if (pickerReturnsToForm) {
			pickerReturnsToForm = false;
			pickerTargetField = null;
			openForm();
			return;
		}
		cancelEditor();
	}

	/** Moves the picker row selection by ±1 with wraparound, scrolling to keep it visible. */
	private void movePickerSelection(int delta) {
		int total = pickerFiltered.size() + (pickerHasFallback ? 1 : 0);
		if (total <= 0) {
			return;
		}
		pickerSelected = Math.floorMod(pickerSelected + delta, total);
		EditorChrome.Rect list = pickerListRect(pickerModalRect());
		int rowTop = pickerSelected * PICKER_ROW_H;
		int rowBottom = rowTop + PICKER_ROW_H;
		if (rowTop < modalScrollOffset) {
			modalScrollOffset = rowTop;
		} else if (rowBottom > modalScrollOffset + list.height()) {
			modalScrollOffset = rowBottom - list.height();
		}
		clampModalScroll(pickerContentHeight(), list.height());
	}

	// ─────────────────────────────────────────────────────────────────────
	// Field form modal
	// ─────────────────────────────────────────────────────────────────────

	private void openForm() {
		modal = ModalKind.FORM;
		modalScrollOffset = 0;
		RuleNodeUiContract contract = RuleNodeUiContract.forKind(editingNode.kind());
		// Type field is hidden for the common item/fluid presets (the chip already says it)
		// and shown read-only for exotic third-party ingredient types decoded from data.
		boolean showType = contract.exposesField(RuleFieldRole.INGREDIENT_TYPE)
			&& isExoticIngredientType(editingNode.ingredientType());
		boolean showPrimary = contract.exposesField(RuleFieldRole.PRIMARY_VALUE);
		boolean showSecondary = contract.exposesField(RuleFieldRole.SECONDARY_VALUE);
		boolean showTertiary = contract.exposesField(RuleFieldRole.TERTIARY_VALUE);
		formVisibleFields = (showType ? 1 : 0) + (showPrimary ? 1 : 0)
			+ (showSecondary ? 1 : 0) + (showTertiary ? 1 : 0);
		// componentTypeId (primaryValue on HAS_COMPONENT / COMPONENT_PATH) gets a field-level
		// "…" button opening the DATA_COMPONENT_TYPE picker; see confirmPickerSelection /
		// cancelOrReturnPicker for the four exit paths that return to this form.
		formPrimaryHasPickerButton = showPrimary
			&& (editingNode.kind() == GroupFilterRuleDraft.NodeKind.HAS_COMPONENT
				|| editingNode.kind() == GroupFilterRuleDraft.NodeKind.COMPONENT_PATH);

		EditorChrome.Rect m = formModalRect();
		int fx = m.x() + GAP;
		int fw = m.width() - GAP * 2;
		int fy = m.y() + GAP + font.lineHeight + 6;

		formType = null;
		if (showType) {
			formType = buildFormField(fx, fy, fw, Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_TYPE),
				editingNode.ingredientType(), editingNode::setIngredientType);
			formType.setEditable(false);
			formType.setTextColorUneditable(OreUiPalette.TEXT_DISABLED);
			fy += FIELD_H + FIELD_GAP;
		}
		int primaryFieldW = formPrimaryHasPickerButton ? fw - ICON_BTN_W - FIELD_GAP : fw;
		formPrimary = showPrimary
			? buildFormField(fx, fy, primaryFieldW, primaryHint(editingNode), editingNode.primaryValue(),
				editingNode::setPrimaryValue, RuleFieldRole.PRIMARY_VALUE)
			: null;
		if (formPrimary != null) {
			fy += FIELD_H + FIELD_GAP;
		}
		formSecondary = showSecondary
			? buildFormField(fx, fy, fw, secondaryHint(editingNode), editingNode.secondaryValue(),
				editingNode::setSecondaryValue, RuleFieldRole.SECONDARY_VALUE)
			: null;
		if (formSecondary != null) {
			fy += FIELD_H + FIELD_GAP;
		}
		formTertiary = showTertiary
			? buildFormField(fx, fy, fw, Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE),
				editingNode.tertiaryValue(), editingNode::setTertiaryValue, RuleFieldRole.TERTIARY_VALUE)
			: null;

		EditBox first = formPrimary != null ? formPrimary : formSecondary;
		if (first != null) {
			first.setFocused(true);
			focusedField = first;
		}
	}

	/**
	 * Single entry point for writing a value back into a FORM field from outside the
	 * field's own EditBox responder — used by the field-level picker's confirm exit path
	 * today, and intended for P6d's GhostDrop reuse. Updates the node, the EditBox's
	 * displayed text (guarded by {@code updatingFields} so the responder doesn't re-fire
	 * a redundant write), and clears any stale invalid-field highlight for the role.
	 */
	private void setFormFieldValue(RuleFieldRole role, String value) {
		if (editingNode == null) {
			return;
		}
		String safe = value == null ? "" : value;
		EditBox box = switch (role) {
			case INGREDIENT_TYPE -> formType;
			case PRIMARY_VALUE -> formPrimary;
			case SECONDARY_VALUE -> formSecondary;
			case TERTIARY_VALUE -> formTertiary;
		};
		switch (role) {
			case INGREDIENT_TYPE -> editingNode.setIngredientType(safe);
			case PRIMARY_VALUE -> editingNode.setPrimaryValue(safe);
			case SECONDARY_VALUE -> editingNode.setSecondaryValue(safe);
			case TERTIARY_VALUE -> editingNode.setTertiaryValue(safe);
		}
		if (box != null) {
			updatingFields = true;
			try {
				box.setValue(safe);
			} finally {
				updatingFields = false;
			}
		}
		formInvalidRoles.remove(role);
		state.markRulesChanged();
		onChanged.run();
	}

	private EditorChrome.Rect formFieldPickerButtonRect(EditorChrome.Rect m, int fy) {
		return new EditorChrome.Rect(m.right() - GAP - ICON_BTN_W, fy, ICON_BTN_W, FIELD_H);
	}

	private static boolean isExoticIngredientType(String type) {
		String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
		return !(normalized.isEmpty() || normalized.equals("item") || normalized.equals("fluid"));
	}

	private interface FieldWriter {
		void set(String value);
	}

	private EditBox buildFormField(int x, int y, int w, Component hint, String value, FieldWriter writer) {
		return buildFormField(x, y, w, hint, value, writer, null);
	}

	/**
	 * @param role when non-null, typing in this field clears its stale invalid-field
	 *             (red outline) highlight from a previous failed confirm attempt.
	 */
	private EditBox buildFormField(int x, int y, int w, Component hint, String value, FieldWriter writer,
	                                @Nullable RuleFieldRole role) {
		EditBox box = new EditBox(font, x + 4, y + (FIELD_H - font.lineHeight) / 2, w - 8, font.lineHeight + 2,
			Component.empty());
		box.setBordered(false);
		box.setMaxLength(512);
		box.setHint(hint);
		box.setValue(value);
		box.setResponder(text -> {
			if (updatingFields) {
				return;
			}
			writer.set(text);
			if (role != null) {
				formInvalidRoles.remove(role);
			}
			state.markRulesChanged();
			onChanged.run();
		});
		return box;
	}

	private EditorChrome.Rect formModalRect() {
		int fields = Math.max(1, formVisibleFields);
		int desiredH = GAP + font.lineHeight + 6 + fields * (FIELD_H + FIELD_GAP) + BTN_H + GAP * 2;
		return modalRect(250, desiredH);
	}

	private EditorChrome.Rect formConfirmRect(EditorChrome.Rect m) {
		String label = Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_CONFIRM).getString();
		int width = Math.max(52, font.width(label) + 16);
		return new EditorChrome.Rect(m.right() - GAP - width, m.bottom() - GAP - BTN_H, width, BTN_H);
	}

	private EditorChrome.Rect formCancelRect(EditorChrome.Rect m) {
		String label = Component.translatable(ModTranslationKeys.BUTTON_CANCEL).getString();
		int width = Math.max(52, font.width(label) + 16);
		EditorChrome.Rect confirm = formConfirmRect(m);
		return new EditorChrome.Rect(confirm.x() - GAP - width, confirm.y(), width, BTN_H);
	}

	/** (field, role) pairs in form layout order; role is used for invalid-outline lookup. */
	private record FormFieldEntry(EditBox field, RuleFieldRole role) {}

	private List<FormFieldEntry> formFieldEntries() {
		List<FormFieldEntry> entries = new ArrayList<>(4);
		if (formType != null) {
			entries.add(new FormFieldEntry(formType, RuleFieldRole.INGREDIENT_TYPE));
		}
		if (formPrimary != null) {
			entries.add(new FormFieldEntry(formPrimary, RuleFieldRole.PRIMARY_VALUE));
		}
		if (formSecondary != null) {
			entries.add(new FormFieldEntry(formSecondary, RuleFieldRole.SECONDARY_VALUE));
		}
		if (formTertiary != null) {
			entries.add(new FormFieldEntry(formTertiary, RuleFieldRole.TERTIARY_VALUE));
		}
		return entries;
	}

	private void renderForm(GuiGraphics g, int mouseX, int mouseY) {
		EditorChrome.Rect m = formModalRect();
		String title = Component.translatable(ModTranslationKeys.EDITOR_RULES_EDIT_TITLE).getString();
		if (editingNode != null) {
			title = title + ": " + Component.translatable(
				RuleNodePresentation.chipLabelKey(editingNode.kind(), editingNode.ingredientType())).getString();
		}
		drawModalPanel(g, m, title);

		int fy = m.y() + GAP + font.lineHeight + 6;
		for (FormFieldEntry entry : formFieldEntries()) {
			boolean hasPickerButton = entry.field() == formPrimary && formPrimaryHasPickerButton;
			int fieldWidth = hasPickerButton
				? m.width() - GAP * 2 - ICON_BTN_W - FIELD_GAP
				: m.width() - GAP * 2;
			EditorChrome.Rect fieldRect = new EditorChrome.Rect(m.x() + GAP, fy, fieldWidth, FIELD_H);
			boolean invalid = formInvalidRoles.contains(entry.role());
			if (invalid) {
				OreUiRenderer.drawOutline(g, fieldRect.x(), fieldRect.y(), fieldRect.width(), fieldRect.height(),
					OreUiPalette.DANGER);
			} else {
				drawFieldChrome(g, fieldRect, entry.field().isFocused(), fieldRect.contains(mouseX, mouseY));
			}
			entry.field().render(g, mouseX, mouseY, 0);
			if (hasPickerButton) {
				EditorChrome.Rect pickerBtn = formFieldPickerButtonRect(m, fy);
				OreUiRenderer.drawButton(g, font, pickerBtn.x(), pickerBtn.y(), pickerBtn.width(), pickerBtn.height(),
					Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_PICKER_BUTTON).getString(),
					buttonState(true, pickerBtn.contains(mouseX, mouseY)));
			}
			fy += FIELD_H + FIELD_GAP;
		}

		EditorChrome.Rect confirm = formConfirmRect(m);
		EditorChrome.Rect cancel = formCancelRect(m);
		OreUiRenderer.drawButton(g, font, confirm.x(), confirm.y(), confirm.width(), confirm.height(),
			Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_CONFIRM).getString(),
			buttonState(true, confirm.contains(mouseX, mouseY)));
		OreUiRenderer.drawButton(g, font, cancel.x(), cancel.y(), cancel.width(), cancel.height(),
			Component.translatable(ModTranslationKeys.BUTTON_CANCEL).getString(),
			buttonState(true, cancel.contains(mouseX, mouseY)));
	}

	private boolean formMouseClicked(double mx, double my) {
		EditorChrome.Rect m = formModalRect();
		if (!m.contains(mx, my)) {
			cancelEditor();
			return true;
		}
		if (formConfirmRect(m).contains(mx, my)) {
			confirmEditor();
			return true;
		}
		if (formCancelRect(m).contains(mx, my)) {
			cancelEditor();
			return true;
		}
		int fy = m.y() + GAP + font.lineHeight + 6;
		for (FormFieldEntry entry : formFieldEntries()) {
			boolean hasPickerButton = entry.field() == formPrimary && formPrimaryHasPickerButton;
			if (hasPickerButton && formFieldPickerButtonRect(m, fy).contains(mx, my)) {
				openFieldPicker(RuleFieldRole.PRIMARY_VALUE);
				return true;
			}
			if (my >= fy && my < fy + FIELD_H && mx >= m.x() + GAP && mx < m.right() - GAP) {
				setFocusedField(entry.field());
				entry.field().mouseClicked(mx, my, 0);
				return true;
			}
			fy += FIELD_H + FIELD_GAP;
		}
		return true;
	}

	/**
	 * Field-level picker entry point: switches FORM to a DATA_COMPONENT_TYPE
	 * PICKER for {@code targetRole}, without touching the pending-node lifecycle
	 * (editingNode / editingIsNew are untouched — only the modal switches). The four
	 * exit paths (confirmPickerSelection / cancelOrReturnPicker / keyPressed Escape)
	 * bring the panel back to FORM via {@link #setFormFieldValue}.
	 */
	private void openFieldPicker(RuleFieldRole targetRole) {
		if (editingNode == null) {
			return;
		}
		pickerReturnsToForm = true;
		pickerTargetField = targetRole;
		pickerKind = RuleNodePresentation.PickerKind.DATA_COMPONENT_TYPE;
		openPicker();
	}

	private void setFocusedField(EditBox field) {
		if (focusedField != null && focusedField != field) {
			focusedField.setFocused(false);
		}
		focusedField = field;
		field.setFocused(true);
	}

	private Component primaryHint(GroupFilterRuleDraft.Node node) {
		return switch (node.kind()) {
			case ID -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_ID);
			case TAG, BLOCK_TAG -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_TAG);
			case NAMESPACE -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_NAMESPACE);
			case ITEM_PATH_STARTS_WITH, ITEM_PATH_CONTAINS, ITEM_PATH_ENDS_WITH ->
				Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_PATH);
			case EXACT_STACK -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_STACK);
			case HAS_COMPONENT, COMPONENT_PATH -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_COMPONENT);
			default -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE);
		};
	}

	private Component secondaryHint(GroupFilterRuleDraft.Node node) {
		return switch (node.kind()) {
			case HAS_COMPONENT -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE);
			case COMPONENT_PATH -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_PATH);
			default -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE_2);
		};
	}

	// ─────────────────────────────────────────────────────────────────────
	// Input
	// ─────────────────────────────────────────────────────────────────────

	boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		if (modal == ModalKind.MENU) {
			return menuMouseClicked(mx, my);
		}
		if (modal == ModalKind.PICKER) {
			return pickerMouseClicked(mx, my);
		}
		if (modal == ModalKind.FORM) {
			return formMouseClicked(mx, my);
		}

		clearFocus();
		clearDrag();

		EditorChrome.Rect addCond = addConditionRect();
		if (addCond.contains(mx, my)) {
			if (state.canInsertRuleRelative()) {
				openMenu(false);
			}
			return true;
		}
		EditorChrome.Rect addGroup = addGroupRect();
		if (addGroup.contains(mx, my)) {
			if (canOpenGroupMenu()) {
				openMenu(true);
			}
			return true;
		}

		EditorChrome.Rect list = listRect();
		if (mx >= list.right() + ScrollbarHelper.GAP && mx < list.right() + ScrollbarHelper.GAP + ScrollbarHelper.WIDTH
			&& my >= list.y() && my < list.bottom()) {
			List<Row> rows = buildRows();
			draggingScroll = true;
			dragStartY = my;
			dragStartOffset = scrollOffset;
			scrollOffset = ScrollbarHelper.trackClickToOffset(my, list.y(), list.height(),
				contentHeight(rows), list.height(), scrollOffset);
			return true;
		}
		if (list.contains(mx, my)) {
			return handleListClick(mx, my, list);
		}
		return false;
	}

	private void openMenu(boolean groupMode) {
		menuGroupMode = groupMode;
		modal = ModalKind.MENU;
		modalScrollOffset = 0;
	}

	private boolean handleListClick(double mx, double my, EditorChrome.Rect list) {
		List<Row> rows = buildRows();
		int index = (int) ((my - list.y() - PAD + scrollOffset) / (ROW_H + ROW_GAP));
		if (index < 0 || index >= rows.size()) {
			return true;
		}
		Row row = rows.get(index);
		int rowTop = list.y() + PAD - scrollOffset + index * (ROW_H + ROW_GAP);
		if (my >= rowTop + ROW_H) {
			return true;
		}
		GroupFilterRuleDraft.Node node = row.node();

		int iconY = rowTop + (ROW_H - ICON_BTN_H) / 2;
		int deleteX = deleteButtonX(list);
		if (hoverIn(mx, my, deleteX, iconY, ICON_BTN_W, ICON_BTN_H)) {
			state.selectRuleNode(node);
			state.deleteSelectedRule();
			onChanged.run();
			return true;
		}
		if (!node.kind().compound()) {
			int editX = editButtonX(list);
			if (hoverIn(mx, my, editX, iconY, ICON_BTN_W, ICON_BTN_H)) {
				state.selectRuleNode(node);
				beginEditor(node, false);
				return true;
			}
		}
		if (node.kind().compound()) {
			int glyphX = rowIndent(row);
			if (mx >= glyphX - 2 && mx < glyphX + 9) {
				if (row.collapsed()) {
					collapsedPaths.remove(row.path());
				} else {
					collapsedPaths.add(row.path());
				}
				clampScroll();
				return true;
			}
			int chipY = rowTop + (ROW_H - CHIP_H) / 2;
			if (chipCyclable(node) && hoverIn(mx, my, chipX(row), chipY, chipWidth(node), CHIP_H)) {
				cycleOperator(node);
				return true;
			}
		}
		// [pre-審條件 5] Only a bare row-body press (past glyph / chip / edit·delete / scrollbar
		// hits, all of which returned above) arms the reparent drag candidate. Root is skipped —
		// canMove rejects it anyway, but arming it would show a ghost with nowhere to drop.
		state.selectRuleNode(node);
		if (node.parent() != null) {
			dragCandidate = node;
			dragOriginX = mx;
			dragOriginY = my;
		}
		return true;
	}

	boolean mouseDragged(double mx, double my, int button) {
		if (isModalOpen()) {
			if (modalDragging) {
				EditorChrome.Rect list = modal == ModalKind.MENU
					? menuListRect(menuModalRect())
					: pickerListRect(pickerModalRect());
				int content = modal == ModalKind.MENU ? menuContentHeight() : pickerContentHeight();
				modalScrollOffset = ScrollbarHelper.dragToOffset(my, modalDragY, modalDragStart,
					content, list.height(), list.height());
			}
			return true;
		}
		if (button != 0) {
			return false;
		}
		// Node reparent drag takes priority over scrollbar dragging (the two never arm
		// together — dragCandidate is only set on a bare row-body press, scrollbar drag on a
		// gutter press). Promote the candidate to an active drag past the travel threshold.
		if (dragNode != null || dragCandidate != null) {
			if (dragNode == null
				&& Math.abs(mx - dragOriginX) < DRAG_THRESHOLD
				&& Math.abs(my - dragOriginY) < DRAG_THRESHOLD) {
				return true; // still within the click tolerance — keep single-click semantics
			}
			if (dragNode == null) {
				dragNode = dragCandidate;
			}
			updateDropSlot(mx, my);
			return true;
		}
		if (!draggingScroll) {
			return false;
		}
		EditorChrome.Rect list = listRect();
		scrollOffset = ScrollbarHelper.dragToOffset(my, dragStartY, dragStartOffset,
			contentHeight(buildRows()), list.height(), list.height());
		return true;
	}

	/**
	 * Hit-tests the row under the pointer and records the resolved {@link #dropSlot}. Shared by
	 * drag / scroll / release so the three never disagree.
	 *
	 * <p>Coordinate discipline: {@code index} here is a <em>row (geometry) index</em>, used only
	 * to locate the hovered row. The model index handed to moveNode is always
	 * {@code parent.children().indexOf(node)} — while a compound is collapsed its visual row
	 * index diverges from its model index (buildRows' {@code skipDeeperThan} skips descendant
	 * rows), so the two must never be conflated.
	 */
	private void updateDropSlot(double mx, double my) {
		dropSlot = null;
		if (dragNode == null) {
			return;
		}
		EditorChrome.Rect list = listRect();
		if (!list.contains(mx, my)) {
			return;
		}
		List<Row> rows = buildRows();
		int stride = ROW_H + ROW_GAP;
		int index = (int) ((my - list.y() - PAD + scrollOffset) / stride);
		if (index < 0 || index >= rows.size()) {
			return;
		}
		int rowTop = list.y() + PAD - scrollOffset + index * stride;
		// [pre-審必改 1] Do NOT early-return on the ROW_GAP band (my >= rowTop + ROW_H): f then
		// runs into [1.0, 1.1) and folds into the row's lower band. No slot is silently dropped.
		double f = (my - rowTop) / (double) ROW_H;
		Row row = rows.get(index);
		GroupFilterRuleDraft.Node node = row.node();
		GroupFilterRuleDraft.Node nodeParent = node.parent();
		int indexInParent = nodeParent == null ? -1 : nodeParent.children().indexOf(node);
		boolean hasVisibleChildren = node.kind().compound() && !row.collapsed()
			&& index + 1 < rows.size() && rows.get(index + 1).depth() > row.depth();

		GroupFilterRuleDraft.Node dragParent = dragNode.parent();
		int dragOldIndex = dragParent == null ? -1 : dragParent.children().indexOf(dragNode);

		DropSlot slot = resolveSlot(f, node, nodeParent, indexInParent, row.depth(),
			hasVisibleChildren, dragParent, dragOldIndex);
		if (slot == null) {
			return;
		}
		// [pre-審必改 2] Validity via canMove against the *slot's parent* (for BETWEEN(node,0)
		// that parent is node itself), so the cycle/capacity guard actually covers the insertion
		// target and no invalid line is drawn.
		if (slot.kind() == SlotKind.INTO) {
			if (!state.canMoveRuleNode(dragNode, slot.parent())) {
				return;
			}
		} else {
			if (slot.parent() == null || !state.canMoveRuleNode(dragNode, slot.parent())) {
				return;
			}
		}
		dropSlot = slot;
		// Insertion-line Y: centre of the gap the line sits in, aligned to renderAccentBars'
		// "+ROW_H" bottom-of-row baseline. "Above" bands hug the gap above the hovered row;
		// "into-first-child" and "below" bands hug the gap under it.
		boolean above = row.node().kind().compound() ? f < (nodeParent == null ? 0.0 : 0.25) : f < 0.5;
		if (slot.kind() == SlotKind.BETWEEN && above) {
			dropLineY = rowTop - (ROW_GAP + 1) / 2;
		} else {
			dropLineY = rowTop + ROW_H + ROW_GAP / 2;
		}
	}

	boolean mouseReleased(double mx, double my, int button) {
		draggingScroll = false;
		modalDragging = false;
		if (dragNode != null) {
			// Recompute against the release position, then commit. INTO drops at the tail of the
			// target compound (P6b behaviour); BETWEEN inserts at the pre-detach index (moveNode
			// reinterprets it after the detach).
			updateDropSlot(mx, my);
			if (dropSlot != null) {
				GroupFilterRuleDraft.Node node = dragNode;
				int index = dropSlot.kind() == SlotKind.INTO
					? dropSlot.parent().children().size()
					: dropSlot.index();
				if (state.moveRuleNode(node, dropSlot.parent(), index)) {
					onChanged.run();
				}
			}
			clearDrag();
			return true;
		}
		clearDrag();
		return false;
	}

	private void clearDrag() {
		dragCandidate = null;
		dragNode = null;
		dropSlot = null;
	}

	boolean mouseScrolled(double mx, double my, double deltaY) {
		if (dragNode != null) {
			// Scroll stays unlocked mid-drag so long lists can be scrolled to reach an off-screen
			// drop position; recompute the slot afterwards. (Scrollbar-drag / selection / collapse
			// remain locked via their own gates.)
			EditorChrome.Rect list = listRect();
			int max = Math.max(0, contentHeight(buildRows()) - list.height());
			scrollOffset = ScrollbarHelper.clamp(
				scrollOffset - (int) Math.signum(deltaY) * (ROW_H + ROW_GAP), 0, max);
			updateDropSlot(mx, my);
			return true;
		}
		if (modal == ModalKind.MENU) {
			EditorChrome.Rect list = menuListRect(menuModalRect());
			scrollModal(deltaY, menuContentHeight(), list.height());
			return true;
		}
		if (modal == ModalKind.PICKER) {
			EditorChrome.Rect list = pickerListRect(pickerModalRect());
			scrollModal(deltaY, pickerContentHeight(), list.height());
			return true;
		}
		if (modal == ModalKind.FORM) {
			return true;
		}
		EditorChrome.Rect list = listRect();
		if (!list.contains(mx, my)) {
			return false;
		}
		int max = Math.max(0, contentHeight(buildRows()) - list.height());
		if (max <= 0) {
			scrollOffset = 0;
			return true;
		}
		scrollOffset = ScrollbarHelper.clamp(
			scrollOffset - (int) Math.signum(deltaY) * (ROW_H + ROW_GAP), 0, max);
		return true;
	}

	private void scrollModal(double deltaY, int contentHeight, int viewHeight) {
		int max = Math.max(0, contentHeight - viewHeight);
		if (max <= 0) {
			modalScrollOffset = 0;
			return;
		}
		modalScrollOffset = ScrollbarHelper.clamp(
			modalScrollOffset - (int) Math.signum(deltaY) * (font.lineHeight + 4), 0, max);
	}

	private boolean handleModalScrollbarClick(double mx, double my, EditorChrome.Rect list, int contentHeight) {
		int sbX = list.right() + ScrollbarHelper.GAP;
		if (mx < sbX || mx >= sbX + ScrollbarHelper.WIDTH || my < list.y() || my >= list.bottom()) {
			return false;
		}
		modalDragging = true;
		modalDragY = my;
		modalDragStart = modalScrollOffset;
		modalScrollOffset = ScrollbarHelper.trackClickToOffset(my, list.y(), list.height(),
			contentHeight, list.height(), modalScrollOffset);
		return true;
	}

	private void clampModalScroll(int contentHeight, int viewHeight) {
		int max = Math.max(0, contentHeight - viewHeight);
		modalScrollOffset = ScrollbarHelper.clamp(modalScrollOffset, 0, max);
	}

	boolean keyPressed(int key, int scan, int mods) {
		// Esc aborts an in-progress reparent drag first, before any modal / close-editor
		// handling. This branch only fires while a drag is live (modal is NONE then), so it
		// never contends with the modal-Esc / Screen-close-editor paths below or upstream.
		if (key == 256 && dragNode != null) { // Escape
			clearDrag();
			return true;
		}
		if (modal == ModalKind.PICKER && (key == 265 || key == 264)) { // Up / Down
			movePickerSelection(key == 264 ? 1 : -1);
			return true;
		}
		if (modal == ModalKind.FORM && key == 258) { // Tab
			cycleFormFocus((mods & 0x1) != 0); // GLFW_MOD_SHIFT
			return true;
		}
		if (focusedField != null && focusedField.isFocused() && focusedField.keyPressed(key, scan, mods)) {
			return true;
		}
		if (isModalOpen()) {
			if (key == 256) { // Escape
				if (modal == ModalKind.PICKER) {
					// Exit path: Esc.
					cancelOrReturnPicker();
				} else if (modal == ModalKind.FORM) {
					cancelEditor();
				} else {
					modal = ModalKind.NONE;
				}
				return true;
			}
			if (key == 257 || key == 335) { // Enter
				if (modal == ModalKind.PICKER) {
					// Exit path: Enter (same as confirm button).
					confirmPickerSelection();
					return true;
				}
				if (modal == ModalKind.FORM) {
					confirmEditor();
					return true;
				}
			}
			return true;
		}
		return false;
	}

	/** Tab focus cycling within FORM's visible, editable fields (Shift+Tab reverses). */
	private void cycleFormFocus(boolean reverse) {
		List<EditBox> fields = new ArrayList<>(4);
		// formType is always read-only (setEditable(false) in openForm) so it's excluded
		// here; EditBox#isEditable() isn't visible to us, so we track it structurally
		// instead of calling it.
		for (EditBox field : new EditBox[] {formPrimary, formSecondary, formTertiary}) {
			if (field != null) {
				fields.add(field);
			}
		}
		if (fields.isEmpty()) {
			return;
		}
		int current = focusedField == null ? -1 : fields.indexOf(focusedField);
		int next;
		if (current < 0) {
			next = reverse ? fields.size() - 1 : 0;
		} else {
			next = Math.floorMod(current + (reverse ? -1 : 1), fields.size());
		}
		setFocusedField(fields.get(next));
	}

	boolean charTyped(char c, int mods) {
		if (focusedField != null && focusedField.isFocused()) {
			return focusedField.charTyped(c, mods);
		}
		return false;
	}

	// ─────────────────────────────────────────────────────────────────────
	// Helpers
	// ─────────────────────────────────────────────────────────────────────

	private OreUiRenderer.ButtonState buttonState(boolean enabled, boolean hovered) {
		if (!enabled) {
			return OreUiRenderer.ButtonState.DISABLED;
		}
		return hovered ? OreUiRenderer.ButtonState.HOVERED : OreUiRenderer.ButtonState.NORMAL;
	}

	private static boolean hoverIn(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
