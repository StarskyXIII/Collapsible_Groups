package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.model.RuleNodePaths;
import com.starskyxiii.collapsible_groups.client.editor.model.RuleNodePresentation;
import com.starskyxiii.collapsible_groups.client.editor.model.RuleNodeUiContract;
import com.starskyxiii.collapsible_groups.client.editor.model.RuleFieldRole;
import com.starskyxiii.collapsible_groups.client.editor.model.RuleTagResolution;
import com.starskyxiii.collapsible_groups.client.widget.EditorChrome;
import com.starskyxiii.collapsible_groups.compat.jei.ui.OreUiEditBoxStyle;
import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.client.widget.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.group.filter.ComponentPathNavigator;
import com.starskyxiii.collapsible_groups.group.filter.ComponentReferenceExtractor;
import com.starskyxiii.collapsible_groups.group.filter.EncodedValueNormalizer;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchQuery;
import com.starskyxiii.collapsible_groups.ingredient.ItemUniverseProvider;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
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
	private static final int REFERENCE_SLOT_SIZE = 22;
	private static final int REFERENCE_ROW_H = 30;
	private static final int REFERENCE_PICKER_ROW_H = 20;
	private static final int ITEM_PICKER_CELL_PITCH = 20;
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

	private enum ModalKind { NONE, MENU, PICKER, FORM, REFERENCE_PICKER }

	private enum ReferencePickerMode { NONE, REFERENCE_ITEM, REFERENCE_COMPONENT, REFERENCE_PATH }

	private enum ReferenceItemTarget { REFERENCE_SLOT, FORM_PRIMARY }

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

	// ── Drop-slot model ───────────────────────────────────────────
	// A resolved drop position while a reparent drag is live.
	// INTO(parent) → drop lands at the tail of parent's children.
	//   BETWEEN(parent, i)  → insert as parent's i-th child, i in *pre-detach* caller
	//                         coordinates (moveNode reinterprets it after detach).
	// depth is the *inserted* node's indent depth, used to place the insertion line's x.
	// Package-private (not private) so EditorRulesPanelDropSlotTest can exercise resolveSlot.
	enum SlotKind { INTO, BETWEEN }

	record DropSlot(SlotKind kind, GroupFilterRuleDraft.Node parent, int index, int depth) {}

	/**
	 * Pure band → slot decision. Takes only plain data (no GuiGraphicsExtractor / MC types) so it is
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
				// Drop into this compound.
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
	private final ItemUniverseProvider itemUniverseProvider;
	private final EditorItemSearchSession itemSearchSession;

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
	@Nullable
	private ItemStack referenceStack;
	private ReferencePickerMode referencePickerMode = ReferencePickerMode.NONE;
	private ReferenceItemTarget referenceItemTarget = ReferenceItemTarget.REFERENCE_SLOT;
	private List<ItemStack> referenceItems = List.of();
	private List<ItemStack> referenceFilteredItems = List.of();
	private List<ComponentReferenceExtractor.ComponentReference> referenceComponents = List.of();
	private List<ComponentReferenceExtractor.ComponentReference> referenceFilteredComponents = List.of();
	private List<ComponentPathNavigator.PathNode> referencePaths = List.of();
	private List<ComponentPathNavigator.PathNode> referenceFilteredPaths = List.of();
	@Nullable
	private ComponentReferenceExtractor.ComponentReference referenceSelectedComponent;
	private EditBox referencePickerSearch;
	private int referencePickerSelected = -1;
	private long lastReferencePickerClickMs;
	private int lastReferencePickerClickIndex = -1;
	private String referenceComponentSearch = "";
	private String referencePathSearch = "";
	private String referenceItemSearch = "";
	private boolean referenceItemFilterDirty;
	private long referenceItemFilterDeadline;

	EditorRulesPanel(
		EditorRulesState state,
		Font font,
		Runnable onChanged,
		ItemUniverseProvider itemUniverseProvider,
		EditorItemSearchSession itemSearchSession
	) {
		this.state = state;
		this.font = font;
		this.onChanged = onChanged;
		this.itemUniverseProvider = itemUniverseProvider;
		this.itemSearchSession = itemSearchSession;
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
		if (modal == ModalKind.PICKER || modal == ModalKind.FORM || modal == ModalKind.REFERENCE_PICKER) {
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
		referenceStack = null;
		resetReferencePickerState();
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

	void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		boolean modalUp = isModalOpen();
		int listMouseX = modalUp ? Integer.MIN_VALUE : mouseX;
		int listMouseY = modalUp ? Integer.MIN_VALUE : mouseY;
		renderToolbar(g, listMouseX, listMouseY);
		renderList(g, listMouseX, listMouseY);
		// Drag ghost tag follows the pointer, drawn last and outside any scissor so it can
		// float over the whole body. The Screen owns modal rendering through renderModals().
		if (dragNode != null) {
			renderDragGhost(g, mouseX, mouseY);
		}
	}

	/**
	 * Dims the entire screen, including header, footer, and preview, then draws the active rules
	 * modal at the shell's top Z. {@link #render} paints only the rules body.
	 */
	void renderModals(GuiGraphicsExtractor g, int screenW, int screenH, int mouseX, int mouseY) {
		if (!isModalOpen()) {
			return;
		}
		g.pose().pushMatrix();
		g.nextStratum();
		g.fill(0, 0, screenW, screenH, COL_MODAL_DIM);
		switch (modal) {
			case MENU -> renderMenu(g, mouseX, mouseY);
			case PICKER -> renderPicker(g, mouseX, mouseY);
			case FORM -> renderForm(g, mouseX, mouseY);
			case REFERENCE_PICKER -> renderReferencePicker(g, mouseX, mouseY);
			default -> {}
		}
		g.pose().popMatrix();
	}

	private void renderDragGhost(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (dragNode == null) {
			return;
		}
		String label = chipLabel(dragNode);
		int w = font.width(label) + 8;
		int h = font.lineHeight + 4;
		int gx = mouseX + 8;
		int gy = mouseY + 8;
		g.pose().pushMatrix();
		g.nextStratum();
		g.fill(gx, gy, gx + w, gy + h, COL_GHOST_BG);
		UiSkinRenderer.drawOutline(g, gx, gy, w, h, UiPalette.OUTLINE_SELECTED);
		g.text(font, label, gx + 4, gy + 2, UiPalette.TEXT_SELECTED, false);
		g.pose().popMatrix();
	}

	private void renderToolbar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		EditorChrome.Rect addCond = addConditionRect();
		EditorChrome.Rect addGroup = addGroupRect();
		UiSkinRenderer.drawButton(g, font, addCond.x(), addCond.y(), addCond.width(), addCond.height(),
			Component.translatable(ModTranslationKeys.EDITOR_RULES_ADD_CONDITION).getString(),
			buttonState(state.canInsertRuleRelative(), addCond.contains(mouseX, mouseY)));
		UiSkinRenderer.drawButton(g, font, addGroup.x(), addGroup.y(), addGroup.width(), addGroup.height(),
			Component.translatable(ModTranslationKeys.EDITOR_RULES_ADD_GROUP).getString(),
			buttonState(canOpenGroupMenu(), addGroup.contains(mouseX, mouseY)));
		renderToolbarInfo(g, addCond);
	}

	/** Read-only "<root operator> · N rules" label, anchored to the toolbar's left edge. */
	private void renderToolbarInfo(GuiGraphicsExtractor g, EditorChrome.Rect firstButton) {
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
		g.text(font, font.plainSubstrByWidth(text, maxWidth), bodyX + PAD, ty, UiPalette.TEXT_MUTED, false);
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

	private void renderList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		EditorChrome.Rect list = listRect();
		List<Row> rows = buildRows();

		if (rows.isEmpty()) {
			String empty = Component.translatable(ModTranslationKeys.EDITOR_RULES_NO_ROOT).getString();
			g.text(font, font.plainSubstrByWidth(empty, list.width()),
				list.x() + PAD, list.y() + PAD, UiPalette.TEXT_HINT, false);
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
			// BETWEEN drop line: 2px accent between rows, indented to the inserted node's
			// level. INTO uses the row highlight in renderRow instead. Kept inside the list scissor.
			if (dropSlot != null && dropSlot.kind() == SlotKind.BETWEEN) {
				int lineX = list.x() + PAD + dropSlot.depth() * INDENT_W;
				g.fill(lineX, dropLineY, list.right() - PAD, dropLineY + 2, UiPalette.BUTTON_PRIMARY);
			}
		} finally {
			g.disableScissor();
		}

		ScrollbarHelper.renderPixels(g, list.right() + ScrollbarHelper.GAP, list.y(), list.height(),
			list.height(), contentHeight(rows), scrollOffset);
	}

	private void renderAccentBars(GuiGraphicsExtractor g, EditorChrome.Rect list, List<Row> rows) {
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
				? UiPalette.DANGER
				: CHIP_POSITIVE;
			g.fill(barX, top, barX + BAR_W, bottom, color);
		}
	}

	private void renderRow(GuiGraphicsExtractor g, EditorChrome.Rect list, Row row, int y, int mouseX, int mouseY) {
		GroupFilterRuleDraft.Node node = row.node();
		boolean selected = node == state.selectedRuleNode();
		boolean rowHover = mouseY >= y && mouseY < y + ROW_H && mouseX >= list.x() && mouseX < list.right();
		boolean intoTarget = dropSlot != null && dropSlot.kind() == SlotKind.INTO && dropSlot.parent() == node;
		if (intoTarget) {
			g.fill(list.x() + 1, y, list.right() - 1, y + ROW_H, COL_DROP_TARGET);
			UiSkinRenderer.drawOutline(g, list.x() + 1, y, list.width() - 2, ROW_H, CHIP_POSITIVE);
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
			case NEGATIVE -> UiPalette.DANGER;
			case NONE -> CHIP_NEUTRAL;
		};
		g.fill(x, chipY, x + chipW, chipY + CHIP_H, chipColor);
		g.text(font, chip, x + CHIP_PAD, chipY + (CHIP_H - font.lineHeight) / 2 + 1,
			UiPalette.TEXT_SELECTED, false);
		if (chipCyclable(node)) {
			int ax = x + chipW - CHIP_PAD - 5;
			int ay = chipY + (CHIP_H - 3) / 2;
			g.fill(ax, ay, ax + 5, ay + 1, UiPalette.TEXT_SELECTED);
			g.fill(ax + 1, ay + 1, ax + 4, ay + 2, UiPalette.TEXT_SELECTED);
			g.fill(ax + 2, ay + 2, ax + 3, ay + 3, UiPalette.TEXT_SELECTED);
		}
		x += chipW + 6;

		// Icons (right-aligned, Manager action-rail primitive)
		int iconY = y + (ROW_H - ICON_BTN_H) / 2;
		int deleteX = deleteButtonX(list);
		UiSkinRenderer.drawToolbarIconButton(g, deleteX, iconY, ICON_BTN_W, ICON_BTN_H, UiSkinRenderer.ICON_DELETE,
			buttonState(true, hoverIn(mouseX, mouseY, deleteX, iconY, ICON_BTN_W, ICON_BTN_H)));
		int textRight;
		if (node.kind().compound()) {
			textRight = deleteX - 6;
		} else {
			int editX = editButtonX(list);
			UiSkinRenderer.drawToolbarIconButton(g, editX, iconY, ICON_BTN_W, ICON_BTN_H, UiSkinRenderer.ICON_EDIT,
				buttonState(true, hoverIn(mouseX, mouseY, editX, iconY, ICON_BTN_W, ICON_BTN_H)));
			textRight = editX - 6;
		}

		int textY = y + (ROW_H - font.lineHeight) / 2 + 1;
		if (node.kind().compound()) {
			boolean empty = node.children().isEmpty();
			String count = empty
				? Component.translatable(ModTranslationKeys.EDITOR_RULES_EMPTY_GROUP).getString()
				: Component.translatable(ModTranslationKeys.EDITOR_RULES_CHILD_COUNT, node.children().size()).getString();
			g.text(font, font.plainSubstrByWidth(count, Math.max(0, textRight - x)),
				x, textY, empty ? COL_UNRESOLVED : UiPalette.TEXT_MUTED, false);
			if (empty) {
				UiSkinRenderer.drawOutline(g, list.x() + 1, y, list.width() - 2, ROW_H, UiPalette.DANGER);
			}
		} else {
			String warn = unresolved
				? Component.translatable(ModTranslationKeys.EDITOR_RULES_UNRESOLVED_ROW).getString()
				: "";
			int warnW = warn.isEmpty() ? 0 : font.width(warn) + 6;
			String value = RuleNodePresentation.valueText(node);
			g.text(font, font.plainSubstrByWidth(value, Math.max(0, textRight - x - warnW)),
				x, textY, UiPalette.TEXT_PRIMARY, false);
			if (!warn.isEmpty()) {
				g.text(font, warn, textRight - font.width(warn), textY, COL_UNRESOLVED, false);
				UiSkinRenderer.drawOutline(g, list.x() + 1, y, list.width() - 2, ROW_H, UiPalette.DANGER);
			}
		}
	}

	private int renderCollapseGlyph(GuiGraphicsExtractor g, int x, int y, boolean collapsed) {
		int cy = y + ROW_H / 2;
		int color = UiPalette.TEXT_MUTED;
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

	private void drawModalPanel(GuiGraphicsExtractor g, EditorChrome.Rect m, String title) {
		UiSkinRenderer.drawPanel(g, m.x(), m.y(), m.width(), m.height());
		g.text(font, font.plainSubstrByWidth(title, m.width() - GAP * 2),
			m.x() + GAP, m.y() + GAP, UiPalette.TEXT_PRIMARY, false);
	}

	/**
	 * Shared field chrome (picker search box, form fields): double-layer background
	 * plus a three-state outline. Mirrors the Screen shell's {@code renderFieldChrome}
	 * visual vocabulary so modal text fields match the header's name field.
	 */
	private void drawFieldChrome(GuiGraphicsExtractor g, EditorChrome.Rect rect, boolean focused, boolean hovered) {
		int outline = focused ? UiPalette.OUTLINE_SELECTED : hovered ? UiPalette.OUTLINE_HOVER : UiPalette.OUTLINE_DARK;
		g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiPalette.SURFACE_DARK);
		g.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1, UiPalette.SURFACE);
		UiSkinRenderer.drawOutline(g, rect.x(), rect.y(), rect.width(), rect.height(), outline);
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

	private void renderMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
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
				UiSkinRenderer.drawButton(g, font, list.x(), y, list.width(), BTN_H, menuEntryLabel(entry),
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

		g.text(font, font.plainSubstrByWidth(hoverDesc, m.width() - GAP * 2),
			m.x() + GAP, m.bottom() - GAP - font.lineHeight, UiPalette.TEXT_HINT, false);
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
		referenceStack = null;
		resetReferencePickerState();
		focusedField = null;
		state.markRulesChanged();
		onChanged.run();
	}

	/**
	 * Checks the current node's required fields (contract-driven, aligned with
	 * {@link com.starskyxiii.collapsible_groups.group.filter.GroupFilterValidator}) and records
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
		referenceStack = null;
		resetReferencePickerState();
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
		pickerSearch.setHint(OreUiEditBoxStyle.hint(
			Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_SEARCH)));
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
			case ITEM_TAG -> BuiltInRegistries.ITEM.getTags().map(tag -> tag.key())
				.map(tag -> tag.location().toString()).sorted().toList();
			case FLUID_TAG -> BuiltInRegistries.FLUID.getTags().map(tag -> tag.key())
				.map(tag -> tag.location().toString()).sorted().toList();
			case BLOCK_TAG -> BuiltInRegistries.BLOCK.getTags().map(tag -> tag.key())
				.map(tag -> tag.location().toString()).sorted().toList();
			case NAMESPACE -> Stream.concat(
					BuiltInRegistries.ITEM.keySet().stream(),
					BuiltInRegistries.FLUID.keySet().stream())
				.map(Identifier::getNamespace).distinct().sorted().toList();
			case DATA_COMPONENT_TYPE -> BuiltInRegistries.DATA_COMPONENT_TYPE.keySet().stream()
				.map(Identifier::toString).sorted().toList();
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
		return Identifier.tryParse(query) != null;
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

	private void renderPicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
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
			pickerSearch.extractRenderState(g, mouseX, mouseY, 0);
		}

		EditorChrome.Rect list = pickerListRect(m);
		g.fill(list.x(), list.y(), list.right(), list.bottom(), UiPalette.SURFACE_DARK);
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
					renderPickerRow(g, list, y, display, id, UiPalette.TEXT_PRIMARY, mouseX, mouseY);
				}
				y += PICKER_ROW_H;
				display++;
			}
			if (display == 0) {
				g.text(font, Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_EMPTY).getString(),
					list.x() + PAD, list.y() + PAD, UiPalette.TEXT_HINT, false);
			}
		} finally {
			g.disableScissor();
		}
		ScrollbarHelper.renderPixels(g, list.right() + ScrollbarHelper.GAP, list.y(), list.height(),
			list.height(), pickerContentHeight(), modalScrollOffset);

		String countText = pickerFiltered.size() + " / " + pickerSnapshot.size();
		g.text(font, countText, list.x(), list.bottom() + 3, UiPalette.TEXT_HINT, false);

		EditorChrome.Rect confirm = pickerConfirmRect(m);
		EditorChrome.Rect cancel = pickerCancelRect(m);
		UiSkinRenderer.drawButton(g, font, confirm.x(), confirm.y(), confirm.width(), confirm.height(),
			Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_CONFIRM).getString(),
			buttonState(pickerSelected >= 0, confirm.contains(mouseX, mouseY)));
		UiSkinRenderer.drawButton(g, font, cancel.x(), cancel.y(), cancel.width(), cancel.height(),
			Component.translatable(ModTranslationKeys.BUTTON_CANCEL).getString(),
			buttonState(true, cancel.contains(mouseX, mouseY)));
	}

	private void renderPickerRow(GuiGraphicsExtractor g, EditorChrome.Rect list, int y, int display, String text,
	                             int color, int mouseX, int mouseY) {
		boolean selected = display == pickerSelected;
		boolean hovered = list.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + PICKER_ROW_H;
		if (selected) {
			g.fill(list.x(), y, list.right(), y + PICKER_ROW_H, COL_ROW_SELECTED);
		} else if (hovered) {
			g.fill(list.x(), y, list.right(), y + PICKER_ROW_H, COL_PICKER_HOVER);
		}
		g.text(font, font.plainSubstrByWidth(text, list.width() - PAD * 2),
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
			pickerSearch.mouseClicked(new MouseButtonEvent(mx, my, new MouseButtonInfo(0, 0)), false);
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
	// Typed reference item / component / path picker
	// ─────────────────────────────────────────────────────────────────────

	private void openReferenceItemPicker(ReferenceItemTarget target) {
		if (editingNode == null) return;
		modal = ModalKind.REFERENCE_PICKER;
		referencePickerMode = ReferencePickerMode.REFERENCE_ITEM;
		referenceItemTarget = target;
		referenceItems = itemUniverseProvider.allStacks().stream()
			.filter(stack -> stack != null && !stack.isEmpty())
			.toList();
		referenceComponents = List.of();
		referenceFilteredComponents = List.of();
		referencePaths = List.of();
		referenceFilteredPaths = List.of();
		referenceSelectedComponent = null;
		modalScrollOffset = 0;
		lastReferencePickerClickMs = 0;
		lastReferencePickerClickIndex = -1;
		initReferencePickerSearch(referenceItemSearch);
		refilterReferencePicker();
		if (target == ReferenceItemTarget.REFERENCE_SLOT && referenceStack != null) {
			referencePickerSelected = indexOfItem(referenceFilteredItems, referenceStack);
		}
		scrollReferencePickerToSelection();
	}

	private void openReferenceComponentPicker() {
		openReferenceComponentPicker(null);
	}

	private void openReferenceComponentPicker(
		@Nullable ComponentReferenceExtractor.ComponentReference preferred
	) {
		if (referenceStack == null || editingNode == null || !hasReferenceSlot()) return;
		modal = ModalKind.REFERENCE_PICKER;
		referencePickerMode = ReferencePickerMode.REFERENCE_COMPONENT;
		referenceItems = List.of();
		referenceFilteredItems = List.of();
		referenceComponents = ComponentReferenceExtractor.extract(referenceStack);
		referencePaths = List.of();
		referenceFilteredPaths = List.of();
		referenceSelectedComponent = preferred;
		modalScrollOffset = 0;
		lastReferencePickerClickMs = 0;
		lastReferencePickerClickIndex = -1;
		initReferencePickerSearch(referenceComponentSearch);
		refilterReferencePicker();
		if (preferred != null) referencePickerSelected = referenceFilteredComponents.indexOf(preferred);
		if (referencePickerSelected < 0) {
			referencePickerSelected = indexOfComponentId(referenceFilteredComponents, editingNode.primaryValue());
		}
		scrollReferencePickerToSelection();
	}

	private void openReferencePathPicker(ComponentReferenceExtractor.ComponentReference component) {
		modal = ModalKind.REFERENCE_PICKER;
		referencePickerMode = ReferencePickerMode.REFERENCE_PATH;
		referenceSelectedComponent = component;
		referencePaths = ComponentPathNavigator.enumerateReachable(component.encodedJson());
		referenceFilteredComponents = List.of();
		modalScrollOffset = 0;
		lastReferencePickerClickMs = 0;
		lastReferencePickerClickIndex = -1;
		initReferencePickerSearch(referencePathSearch);
		refilterReferencePicker();
		referencePickerSelected = indexOfPath(referenceFilteredPaths, editingNode.secondaryValue());
		scrollReferencePickerToSelection();
	}

	private void initReferencePickerSearch(String value) {
		EditorChrome.Rect search = pickerSearchRect(referencePickerModalRect());
		referencePickerSearch = new EditBox(font,
			search.x() + 4, search.y() + (search.height() - font.lineHeight) / 2,
			search.width() - 8, font.lineHeight + 2, Component.empty());
		referencePickerSearch.setBordered(false);
		referencePickerSearch.setMaxLength(256);
		referencePickerSearch.setHint(OreUiEditBoxStyle.hint(Component.translatable(
			referencePickerMode == ReferencePickerMode.REFERENCE_ITEM
				? ModTranslationKeys.EDITOR_RULES_REFERENCE_ITEM_SEARCH
				: ModTranslationKeys.EDITOR_RULES_PICKER_SEARCH)));
		referencePickerSearch.setValue(value);
		referencePickerSearch.setResponder(query -> {
			if (referencePickerMode == ReferencePickerMode.REFERENCE_ITEM) {
				referenceItemSearch = query;
				referenceItemFilterDirty = true;
				referenceItemFilterDeadline = System.currentTimeMillis() + FILTER_DEBOUNCE_MS;
				return;
			} else if (referencePickerMode == ReferencePickerMode.REFERENCE_COMPONENT) {
				referenceComponentSearch = query;
			} else if (referencePickerMode == ReferencePickerMode.REFERENCE_PATH) {
				referencePathSearch = query;
			}
			refilterReferencePicker();
			modalScrollOffset = 0;
		});
		referencePickerSearch.setFocused(true);
		focusedField = referencePickerSearch;
	}

	private void refilterReferencePicker() {
		referenceItemFilterDirty = false;
		String query = referencePickerSearch == null
			? ""
			: referencePickerSearch.getValue().trim();
		String lowerQuery = query.toLowerCase(Locale.ROOT);
		if (referencePickerMode == ReferencePickerMode.REFERENCE_ITEM) {
			IngredientSearchQuery parsed = IngredientSearchQuery.parse(query);
			referenceFilteredItems = parsed.isEmpty()
				? referenceItems
				: referenceItems.stream().filter(stack -> itemSearchSession.matches(stack, parsed)).toList();
			referencePickerSelected = -1;
		} else if (referencePickerMode == ReferencePickerMode.REFERENCE_COMPONENT) {
			referenceFilteredComponents = referenceComponents.stream()
				.filter(entry -> lowerQuery.isEmpty()
					|| entry.componentTypeId().toLowerCase(Locale.ROOT).contains(lowerQuery)
					|| entry.encodedValue().toLowerCase(Locale.ROOT).contains(lowerQuery))
				.toList();
			referencePickerSelected = -1;
		} else if (referencePickerMode == ReferencePickerMode.REFERENCE_PATH) {
			referenceFilteredPaths = referencePaths.stream()
				.filter(entry -> lowerQuery.isEmpty()
					|| entry.path().toLowerCase(Locale.ROOT).contains(lowerQuery)
					|| EncodedValueNormalizer.normalize(entry.value()).toLowerCase(Locale.ROOT).contains(lowerQuery))
				.toList();
			referencePickerSelected = -1;
		}
	}

	private static int indexOfComponentId(
		List<ComponentReferenceExtractor.ComponentReference> entries,
		String componentTypeId
	) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).componentTypeId().equals(componentTypeId)) return i;
		}
		return -1;
	}

	private static int indexOfPath(List<ComponentPathNavigator.PathNode> entries, String path) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).path().equals(path)) return i;
		}
		return -1;
	}

	private static int indexOfItem(List<ItemStack> entries, ItemStack wanted) {
		for (int i = 0; i < entries.size(); i++) {
			if (ItemStack.isSameItemSameComponents(entries.get(i), wanted)) return i;
		}
		return -1;
	}

	private EditorChrome.Rect referencePickerModalRect() {
		return modalRect(300, 230);
	}

	private int referencePickerEntryCount() {
		return switch (referencePickerMode) {
			case REFERENCE_ITEM -> referenceFilteredItems.size();
			case REFERENCE_COMPONENT -> referenceFilteredComponents.size();
			case REFERENCE_PATH -> referenceFilteredPaths.size();
			case NONE -> 0;
		};
	}

	private int referencePickerTotalCount() {
		return switch (referencePickerMode) {
			case REFERENCE_ITEM -> referenceItems.size();
			case REFERENCE_COMPONENT -> referenceComponents.size();
			case REFERENCE_PATH -> referencePaths.size();
			case NONE -> 0;
		};
	}

	private int referencePickerContentHeight() {
		if (referencePickerMode == ReferencePickerMode.REFERENCE_ITEM) {
			int columns = referenceItemColumns(pickerListRect(referencePickerModalRect()));
			return Math.max(1, (referencePickerEntryCount() + columns - 1) / columns) * ITEM_PICKER_CELL_PITCH + 1;
		}
		return referencePickerEntryCount() * REFERENCE_PICKER_ROW_H;
	}

	private String referencePickerTitle() {
		String key = switch (referencePickerMode) {
			case REFERENCE_ITEM -> ModTranslationKeys.EDITOR_RULES_REFERENCE_ITEM_TITLE;
			case REFERENCE_PATH -> ModTranslationKeys.EDITOR_RULES_REFERENCE_PATH_TITLE;
			case REFERENCE_COMPONENT, NONE -> ModTranslationKeys.EDITOR_RULES_REFERENCE_COMPONENT_TITLE;
		};
		return Component.translatable(key).getString();
	}

	private void renderReferencePicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (referencePickerMode == ReferencePickerMode.REFERENCE_ITEM
			&& referenceItemFilterDirty && System.currentTimeMillis() >= referenceItemFilterDeadline) {
			refilterReferencePicker();
			modalScrollOffset = 0;
		}
		EditorChrome.Rect modalRect = referencePickerModalRect();
		drawModalPanel(g, modalRect, referencePickerTitle());
		EditorChrome.Rect search = pickerSearchRect(modalRect);
		drawFieldChrome(g, search,
			referencePickerSearch != null && referencePickerSearch.isFocused(), search.contains(mouseX, mouseY));
		if (referencePickerSearch != null) referencePickerSearch.extractRenderState(g, mouseX, mouseY, 0);

		EditorChrome.Rect list = pickerListRect(modalRect);
		g.fill(list.x(), list.y(), list.right(), list.bottom(), UiPalette.SURFACE_DARK);
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		try {
			if (referencePickerMode == ReferencePickerMode.REFERENCE_ITEM) {
				renderReferenceItemGrid(g, list, mouseX, mouseY);
			} else {
				for (int index = 0; index < referencePickerEntryCount(); index++) {
					int y = list.y() + index * REFERENCE_PICKER_ROW_H - modalScrollOffset;
					if (y + REFERENCE_PICKER_ROW_H >= list.y() && y <= list.bottom()) {
						renderReferencePickerRow(g, list, y, index, mouseX, mouseY);
					}
				}
			}
			if (referencePickerEntryCount() == 0) {
				String key;
				if (referencePickerTotalCount() > 0 || referencePickerMode == ReferencePickerMode.REFERENCE_ITEM) {
					key = ModTranslationKeys.EDITOR_RULES_PICKER_EMPTY;
				} else if (referencePickerMode == ReferencePickerMode.REFERENCE_PATH) {
					key = ModTranslationKeys.EDITOR_RULES_REFERENCE_NO_PATHS;
				} else {
					key = ModTranslationKeys.EDITOR_RULES_REFERENCE_NO_COMPONENTS;
				}
				g.text(font, Component.translatable(key).getString(),
					list.x() + PAD, list.y() + PAD, UiPalette.TEXT_HINT, false);
			}
		} finally {
			g.disableScissor();
		}
		ScrollbarHelper.renderPixels(g, list.right() + ScrollbarHelper.GAP, list.y(), list.height(),
			list.height(), referencePickerContentHeight(), modalScrollOffset);
		g.text(font, referencePickerEntryCount() + " / " + referencePickerTotalCount(),
			list.x(), list.bottom() + 3, UiPalette.TEXT_HINT, false);

		EditorChrome.Rect confirm = pickerConfirmRect(modalRect);
		EditorChrome.Rect cancel = pickerCancelRect(modalRect);
		UiSkinRenderer.drawButton(g, font, confirm.x(), confirm.y(), confirm.width(), confirm.height(),
			Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_CONFIRM).getString(),
			buttonState(referencePickerSelected >= 0, confirm.contains(mouseX, mouseY)));
		String cancelKey = referencePickerMode == ReferencePickerMode.REFERENCE_PATH
			? ModTranslationKeys.EDITOR_RULES_REFERENCE_BACK : ModTranslationKeys.BUTTON_CANCEL;
		UiSkinRenderer.drawButton(g, font, cancel.x(), cancel.y(), cancel.width(), cancel.height(),
			Component.translatable(cancelKey).getString(), buttonState(true, cancel.contains(mouseX, mouseY)));
		if (referencePickerMode == ReferencePickerMode.REFERENCE_ITEM) {
			ItemStack hovered = referenceItemAt(list, mouseX, mouseY);
			if (hovered != null) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
			else if (search.contains(mouseX, mouseY)) {
				g.setComponentTooltipForNextFrame(font, GroupEditorTooltipHelper.searchSyntaxLines(), mouseX, mouseY);
			}
		}
	}

	private int referenceItemColumns(EditorChrome.Rect list) {
		return Math.max(1, (list.width() - 1) / ITEM_PICKER_CELL_PITCH);
	}

	@Nullable
	private ItemStack referenceItemAt(EditorChrome.Rect list, double mouseX, double mouseY) {
		if (!list.contains(mouseX, mouseY)) return null;
		int columns = referenceItemColumns(list);
		int col = (int) ((mouseX - list.x()) / ITEM_PICKER_CELL_PITCH);
		if (col >= columns) return null;
		int row = (int) ((mouseY - list.y() + modalScrollOffset) / ITEM_PICKER_CELL_PITCH);
		int index = row * columns + col;
		return index >= 0 && index < referenceFilteredItems.size() ? referenceFilteredItems.get(index) : null;
	}

	/** Draws only the visible grid rows; a 20k-stack universe never produces 20k layouts. */
	private void renderReferenceItemGrid(GuiGraphicsExtractor g, EditorChrome.Rect list, int mouseX, int mouseY) {
		int columns = referenceItemColumns(list);
		int firstRow = modalScrollOffset / ITEM_PICKER_CELL_PITCH;
		int pixelOffset = modalScrollOffset % ITEM_PICKER_CELL_PITCH;
		int visibleRows = (list.height() + pixelOffset + ITEM_PICKER_CELL_PITCH - 1) / ITEM_PICKER_CELL_PITCH + 1;
		int gridY = list.y() - pixelOffset;
		UiSkinRenderer.drawSlotGrid(g, list.x(), gridY, columns, visibleRows, ITEM_PICKER_CELL_PITCH);
		int firstIndex = firstRow * columns;
		int lastIndex = Math.min(referenceFilteredItems.size(), firstIndex + visibleRows * columns);
		for (int index = firstIndex; index < lastIndex; index++) {
			int visibleIndex = index - firstIndex;
			int col = visibleIndex % columns;
			int row = visibleIndex / columns;
			int x = list.x() + col * ITEM_PICKER_CELL_PITCH;
			int y = gridY + row * ITEM_PICKER_CELL_PITCH;
			boolean hovered = mouseX > x && mouseX < x + ITEM_PICKER_CELL_PITCH
				&& mouseY > y && mouseY < y + ITEM_PICKER_CELL_PITCH;
			if (index == referencePickerSelected) {
				g.fill(x + 1, y + 1, x + ITEM_PICKER_CELL_PITCH, y + ITEM_PICKER_CELL_PITCH, COL_ROW_SELECTED);
			} else if (hovered) {
				g.fill(x + 1, y + 1, x + ITEM_PICKER_CELL_PITCH, y + ITEM_PICKER_CELL_PITCH, COL_PICKER_HOVER);
			}
			g.item(referenceFilteredItems.get(index), x + 2, y + 2);
		}
	}

	private void renderReferencePickerRow(
		GuiGraphicsExtractor g, EditorChrome.Rect list, int y, int index, int mouseX, int mouseY
	) {
		boolean selected = index == referencePickerSelected;
		boolean hovered = list.contains(mouseX, mouseY)
			&& mouseY >= y && mouseY < y + REFERENCE_PICKER_ROW_H;
		if (selected) {
			g.fill(list.x(), y, list.right(), y + REFERENCE_PICKER_ROW_H, COL_ROW_SELECTED);
		} else if (hovered) {
			g.fill(list.x(), y, list.right(), y + REFERENCE_PICKER_ROW_H, COL_PICKER_HOVER);
		}

		String primary;
		String preview;
		if (referencePickerMode == ReferencePickerMode.REFERENCE_COMPONENT) {
			ComponentReferenceExtractor.ComponentReference entry = referenceFilteredComponents.get(index);
			primary = entry.componentTypeId();
			if (entry.fromPatch()) {
				primary += " [" + Component.translatable(ModTranslationKeys.EDITOR_RULES_REFERENCE_PATCH).getString() + "]";
			}
			preview = entry.encodedValue();
		} else {
			ComponentPathNavigator.PathNode entry = referenceFilteredPaths.get(index);
			primary = entry.path();
			preview = EncodedValueNormalizer.normalize(entry.value());
		}
		int primaryWidth = Math.max(40, list.width() * 3 / 5);
		g.text(font, font.plainSubstrByWidth(primary, primaryWidth - PAD * 2),
			list.x() + PAD, y + (REFERENCE_PICKER_ROW_H - font.lineHeight) / 2,
			UiPalette.TEXT_PRIMARY, false);
		int previewX = list.x() + primaryWidth;
		g.text(font, font.plainSubstrByWidth(preview, Math.max(0, list.right() - previewX - PAD)),
			previewX, y + (REFERENCE_PICKER_ROW_H - font.lineHeight) / 2,
			UiPalette.TEXT_HINT, false);
	}

	private boolean referencePickerMouseClicked(double mx, double my) {
		EditorChrome.Rect modalRect = referencePickerModalRect();
		if (!modalRect.contains(mx, my)) {
			cancelOrBackReferencePicker();
			return true;
		}
		EditorChrome.Rect search = pickerSearchRect(modalRect);
		if (search.contains(mx, my) && referencePickerSearch != null) {
			referencePickerSearch.setFocused(true);
			focusedField = referencePickerSearch;
			referencePickerSearch.mouseClicked(new MouseButtonEvent(mx, my, new MouseButtonInfo(0, 0)), false);
			return true;
		}
		EditorChrome.Rect list = pickerListRect(modalRect);
		if (handleModalScrollbarClick(mx, my, list, referencePickerContentHeight())) return true;
		if (list.contains(mx, my)) {
			int index;
			if (referencePickerMode == ReferencePickerMode.REFERENCE_ITEM) {
				int columns = referenceItemColumns(list);
				int row = (int) ((my - list.y() + modalScrollOffset) / ITEM_PICKER_CELL_PITCH);
				int col = (int) ((mx - list.x()) / ITEM_PICKER_CELL_PITCH);
				index = col < columns ? row * columns + col : -1;
			} else {
				index = (int) ((my - list.y() + modalScrollOffset) / REFERENCE_PICKER_ROW_H);
			}
			if (index >= 0 && index < referencePickerEntryCount()) {
				long now = System.currentTimeMillis();
				boolean doubleClick = index == lastReferencePickerClickIndex
					&& now - lastReferencePickerClickMs <= DOUBLE_CLICK_MS;
				lastReferencePickerClickIndex = index;
				lastReferencePickerClickMs = now;
				referencePickerSelected = index;
				if (doubleClick) confirmReferencePickerSelection();
			}
			return true;
		}
		if (pickerConfirmRect(modalRect).contains(mx, my)) {
			confirmReferencePickerSelection();
			return true;
		}
		if (pickerCancelRect(modalRect).contains(mx, my)) {
			cancelOrBackReferencePicker();
			return true;
		}
		return true;
	}

	private void confirmReferencePickerSelection() {
		if (referencePickerMode == ReferencePickerMode.REFERENCE_ITEM && referenceItemFilterDirty) {
			refilterReferencePicker();
		}
		if (editingNode == null || referencePickerSelected < 0) return;
		if (referencePickerMode == ReferencePickerMode.REFERENCE_ITEM) {
			if (referencePickerSelected >= referenceFilteredItems.size()) return;
			ItemStack selected = referenceFilteredItems.get(referencePickerSelected).copy();
			if (referenceItemTarget == ReferenceItemTarget.REFERENCE_SLOT) {
				referenceStack = selected;
				openReferenceComponentPicker();
			} else {
				String value = switch (editingNode.kind()) {
					case ID -> BuiltInRegistries.ITEM.getKey(selected.getItem()).toString();
					case EXACT_STACK -> GroupItemSelector.tryExactSelector(selected)
						.map(selector -> selector.startsWith("stack:")
							? selector.substring("stack:".length()) : selector)
						.orElse("");
					default -> "";
				};
				openForm();
				if (!value.isEmpty()) setFormFieldValue(RuleFieldRole.PRIMARY_VALUE, value);
			}
			return;
		}
		if (referencePickerMode == ReferencePickerMode.REFERENCE_COMPONENT) {
			if (referencePickerSelected >= referenceFilteredComponents.size()) return;
			ComponentReferenceExtractor.ComponentReference component =
				referenceFilteredComponents.get(referencePickerSelected);
			if (editingNode.kind() == GroupFilterRuleDraft.NodeKind.HAS_COMPONENT) {
				openForm();
				setFormFieldValues(Map.of(
					RuleFieldRole.PRIMARY_VALUE, component.componentTypeId(),
					RuleFieldRole.SECONDARY_VALUE, component.encodedValue()));
			} else if (editingNode.kind() == GroupFilterRuleDraft.NodeKind.COMPONENT_PATH) {
				openReferencePathPicker(component);
			}
			return;
		}
		if (referencePickerMode == ReferencePickerMode.REFERENCE_PATH
			&& referenceSelectedComponent != null
			&& referencePickerSelected < referenceFilteredPaths.size()) {
			ComponentReferenceExtractor.ComponentReference component = referenceSelectedComponent;
			ComponentPathNavigator.PathNode path = referenceFilteredPaths.get(referencePickerSelected);
			openForm();
			setFormFieldValues(Map.of(
				RuleFieldRole.PRIMARY_VALUE, component.componentTypeId(),
				RuleFieldRole.SECONDARY_VALUE, path.path(),
				RuleFieldRole.TERTIARY_VALUE, EncodedValueNormalizer.normalize(path.value())));
		}
	}

	private void cancelOrBackReferencePicker() {
		if (referencePickerMode == ReferencePickerMode.REFERENCE_PATH) {
			ComponentReferenceExtractor.ComponentReference component = referenceSelectedComponent;
			openReferenceComponentPicker(component);
		} else {
			openForm();
		}
	}

	private void moveReferencePickerSelection(int delta) {
		int total = referencePickerEntryCount();
		if (total <= 0) return;
		int step = referencePickerMode == ReferencePickerMode.REFERENCE_ITEM
			? referenceItemColumns(pickerListRect(referencePickerModalRect())) : 1;
		referencePickerSelected = referencePickerSelected < 0
			? (delta > 0 ? 0 : total - 1)
			: Math.floorMod(referencePickerSelected + delta * step, total);
		scrollReferencePickerToSelection();
	}

	private void scrollReferencePickerToSelection() {
		if (referencePickerSelected < 0) return;
		EditorChrome.Rect list = pickerListRect(referencePickerModalRect());
		int rowHeight = referencePickerMode == ReferencePickerMode.REFERENCE_ITEM
			? ITEM_PICKER_CELL_PITCH : REFERENCE_PICKER_ROW_H;
		int rowIndex = referencePickerMode == ReferencePickerMode.REFERENCE_ITEM
			? referencePickerSelected / referenceItemColumns(list) : referencePickerSelected;
		int rowTop = rowIndex * rowHeight;
		int rowBottom = rowTop + rowHeight;
		if (rowTop < modalScrollOffset) {
			modalScrollOffset = rowTop;
		} else if (rowBottom > modalScrollOffset + list.height()) {
			modalScrollOffset = rowBottom - list.height();
		}
		clampModalScroll(referencePickerContentHeight(), list.height());
	}

	// ─────────────────────────────────────────────────────────────────────
	// Field form modal
	// ─────────────────────────────────────────────────────────────────────

	private void openForm() {
		resetReferencePickerState();
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
		// Item ID / exact-stack values open the item grid; component primary values keep
		// using the DATA_COMPONENT_TYPE picker. Every exit path returns to this form.
		formPrimaryHasPickerButton = showPrimary
			&& ((editingNode.kind() == GroupFilterRuleDraft.NodeKind.ID
					&& "item".equals(editingNode.ingredientType()))
				|| editingNode.kind() == GroupFilterRuleDraft.NodeKind.EXACT_STACK
				|| editingNode.kind() == GroupFilterRuleDraft.NodeKind.HAS_COMPONENT
				|| editingNode.kind() == GroupFilterRuleDraft.NodeKind.COMPONENT_PATH);

		EditorChrome.Rect m = formModalRect();
		int fx = m.x() + GAP;
		int fw = m.width() - GAP * 2;
		int fy = formFieldsY(m);

		formType = null;
		if (showType) {
			formType = buildFormField(fx, fy, fw, Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_TYPE),
				editingNode.ingredientType(), editingNode::setIngredientType);
			formType.setEditable(false);
			formType.setTextColorUneditable(UiPalette.TEXT_DISABLED);
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

	private void resetReferencePickerState() {
		referencePickerMode = ReferencePickerMode.NONE;
		referenceItemTarget = ReferenceItemTarget.REFERENCE_SLOT;
		referenceItems = List.of();
		referenceFilteredItems = List.of();
		referenceComponents = List.of();
		referenceFilteredComponents = List.of();
		referencePaths = List.of();
		referenceFilteredPaths = List.of();
		referenceSelectedComponent = null;
		referencePickerSearch = null;
		referencePickerSelected = -1;
		lastReferencePickerClickMs = 0;
		lastReferencePickerClickIndex = -1;
		referenceItemFilterDirty = false;
		referenceItemFilterDeadline = 0;
	}

	/**
	 * Single entry point for writing a value back into a FORM field from outside the
	 * field's own EditBox responder — used by typed picker confirm paths. Updates the node,
	 * the EditBox's
	 * displayed text (guarded by {@code updatingFields} so the responder doesn't re-fire
	 * a redundant write), and clears any stale invalid-field highlight for the role.
	 */
	private void setFormFieldValue(RuleFieldRole role, String value) {
		setFormFieldValues(Map.of(role, value == null ? "" : value));
	}

	/** Atomically updates every supplied role and emits exactly one rules/onChanged notification. */
	private void setFormFieldValues(Map<RuleFieldRole, String> values) {
		if (editingNode == null) {
			return;
		}
		updatingFields = true;
		try {
			for (Map.Entry<RuleFieldRole, String> entry : values.entrySet()) {
				RuleFieldRole role = entry.getKey();
				String safe = entry.getValue() == null ? "" : entry.getValue();
				switch (role) {
					case INGREDIENT_TYPE -> editingNode.setIngredientType(safe);
					case PRIMARY_VALUE -> editingNode.setPrimaryValue(safe);
					case SECONDARY_VALUE -> editingNode.setSecondaryValue(safe);
					case TERTIARY_VALUE -> editingNode.setTertiaryValue(safe);
				}
				EditBox box = switch (role) {
					case INGREDIENT_TYPE -> formType;
					case PRIMARY_VALUE -> formPrimary;
					case SECONDARY_VALUE -> formSecondary;
					case TERTIARY_VALUE -> formTertiary;
				};
				if (box != null) {
				box.setValue(safe);
				}
				formInvalidRoles.remove(role);
			}
		} finally {
			updatingFields = false;
		}
		state.markRulesChanged();
		onChanged.run();
	}

	private boolean hasReferenceSlot() {
		return editingNode != null
			&& (editingNode.kind() == GroupFilterRuleDraft.NodeKind.HAS_COMPONENT
				|| editingNode.kind() == GroupFilterRuleDraft.NodeKind.COMPONENT_PATH);
	}

	private int formFieldsY(EditorChrome.Rect modalRect) {
		int y = modalRect.y() + GAP + font.lineHeight + 6;
		return hasReferenceSlot() ? y + REFERENCE_ROW_H : y;
	}

	private EditorChrome.Rect referenceSlotRect(EditorChrome.Rect modalRect) {
		int rowY = modalRect.y() + GAP + font.lineHeight + 6;
		return new EditorChrome.Rect(modalRect.x() + GAP,
			rowY + (REFERENCE_ROW_H - REFERENCE_SLOT_SIZE) / 2,
			REFERENCE_SLOT_SIZE, REFERENCE_SLOT_SIZE);
	}

	private EditorChrome.Rect referenceRowRect(EditorChrome.Rect modalRect) {
		int rowY = modalRect.y() + GAP + font.lineHeight + 6;
		return new EditorChrome.Rect(modalRect.x() + GAP, rowY,
			modalRect.width() - GAP * 2, REFERENCE_ROW_H);
	}

	private EditorChrome.Rect referenceClearButtonRect(EditorChrome.Rect modalRect) {
		String label = Component.translatable(ModTranslationKeys.EDITOR_RULES_REFERENCE_CLEAR).getString();
		int width = Math.max(38, font.width(label) + 10);
		EditorChrome.Rect row = referenceRowRect(modalRect);
		return new EditorChrome.Rect(row.right() - width, row.y() + (row.height() - BTN_H) / 2, width, BTN_H);
	}

	private void renderReferenceSlot(GuiGraphicsExtractor g, EditorChrome.Rect modalRect, int mouseX, int mouseY) {
		if (!hasReferenceSlot()) {
			return;
		}
		EditorChrome.Rect slot = referenceSlotRect(modalRect);
		g.fill(slot.x(), slot.y(), slot.right(), slot.bottom(), UiPalette.SURFACE_DARK);
		drawDashedOutline(g, slot, slot.contains(mouseX, mouseY)
			? UiPalette.OUTLINE_HOVER : UiPalette.OUTLINE_SELECTED);
		if (referenceStack != null && !referenceStack.isEmpty()) {
			g.item(referenceStack, slot.x() + 3, slot.y() + 3);
		}

		int textX = slot.right() + GAP;
		int textRight = modalRect.right() - GAP;
		if (referenceStack != null) {
			textRight = referenceClearButtonRect(modalRect).x() - GAP;
		}
		Component message;
		if (referenceStack != null) {
			message = Component.translatable(ModTranslationKeys.EDITOR_RULES_REFERENCE_SELECTED,
				referenceStack.getHoverName());
		} else {
			message = Component.translatable(ModTranslationKeys.EDITOR_RULES_REFERENCE_CHOOSE);
		}
		String clipped = font.plainSubstrByWidth(message.getString(), Math.max(0, textRight - textX));
		g.text(font, clipped, textX,
			UiSkinRenderer.centeredTextY(font, slot.y(), slot.height()), UiPalette.TEXT_MUTED, false);

		if (referenceStack != null) {
			EditorChrome.Rect clear = referenceClearButtonRect(modalRect);
			UiSkinRenderer.drawButton(g, font, clear.x(), clear.y(), clear.width(), clear.height(),
				Component.translatable(ModTranslationKeys.EDITOR_RULES_REFERENCE_CLEAR).getString(),
				buttonState(true, clear.contains(mouseX, mouseY)));
		}
	}

	private static void drawDashedOutline(GuiGraphicsExtractor g, EditorChrome.Rect rect, int color) {
		for (int x = rect.x(); x < rect.right(); x += 4) {
			g.fill(x, rect.y(), Math.min(x + 2, rect.right()), rect.y() + 1, color);
			g.fill(x, rect.bottom() - 1, Math.min(x + 2, rect.right()), rect.bottom(), color);
		}
		for (int y = rect.y(); y < rect.bottom(); y += 4) {
			g.fill(rect.x(), y, rect.x() + 1, Math.min(y + 2, rect.bottom()), color);
			g.fill(rect.right() - 1, y, rect.right(), Math.min(y + 2, rect.bottom()), color);
		}
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
		box.setHint(OreUiEditBoxStyle.hint(hint));
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
		int referenceHeight = hasReferenceSlot() ? REFERENCE_ROW_H : 0;
		int desiredH = GAP + font.lineHeight + 6 + referenceHeight
			+ fields * (FIELD_H + FIELD_GAP) + BTN_H + GAP * 2;
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

	private void renderForm(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		EditorChrome.Rect m = formModalRect();
		String title = Component.translatable(ModTranslationKeys.EDITOR_RULES_EDIT_TITLE).getString();
		if (editingNode != null) {
			title = title + ": " + Component.translatable(
				RuleNodePresentation.chipLabelKey(editingNode.kind(), editingNode.ingredientType())).getString();
		}
		drawModalPanel(g, m, title);
		renderReferenceSlot(g, m, mouseX, mouseY);

		int fy = formFieldsY(m);
		for (FormFieldEntry entry : formFieldEntries()) {
			boolean hasPickerButton = entry.field() == formPrimary && formPrimaryHasPickerButton;
			int fieldWidth = hasPickerButton
				? m.width() - GAP * 2 - ICON_BTN_W - FIELD_GAP
				: m.width() - GAP * 2;
			EditorChrome.Rect fieldRect = new EditorChrome.Rect(m.x() + GAP, fy, fieldWidth, FIELD_H);
			boolean invalid = formInvalidRoles.contains(entry.role());
			if (invalid) {
				UiSkinRenderer.drawOutline(g, fieldRect.x(), fieldRect.y(), fieldRect.width(), fieldRect.height(),
					UiPalette.DANGER);
			} else {
				drawFieldChrome(g, fieldRect, entry.field().isFocused(), fieldRect.contains(mouseX, mouseY));
			}
			entry.field().extractRenderState(g, mouseX, mouseY, 0);
			if (hasPickerButton) {
				EditorChrome.Rect pickerBtn = formFieldPickerButtonRect(m, fy);
				UiSkinRenderer.drawButton(g, font, pickerBtn.x(), pickerBtn.y(), pickerBtn.width(), pickerBtn.height(),
					Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_PICKER_BUTTON).getString(),
					buttonState(true, pickerBtn.contains(mouseX, mouseY)));
			}
			fy += FIELD_H + FIELD_GAP;
		}

		EditorChrome.Rect confirm = formConfirmRect(m);
		EditorChrome.Rect cancel = formCancelRect(m);
		UiSkinRenderer.drawButton(g, font, confirm.x(), confirm.y(), confirm.width(), confirm.height(),
			Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_CONFIRM).getString(),
			buttonState(true, confirm.contains(mouseX, mouseY)));
		UiSkinRenderer.drawButton(g, font, cancel.x(), cancel.y(), cancel.width(), cancel.height(),
			Component.translatable(ModTranslationKeys.BUTTON_CANCEL).getString(),
			buttonState(true, cancel.contains(mouseX, mouseY)));
	}

	private boolean formMouseClicked(double mx, double my) {
		EditorChrome.Rect m = formModalRect();
		if (!m.contains(mx, my)) {
			cancelEditor();
			return true;
		}
		if (hasReferenceSlot() && referenceStack != null
			&& referenceClearButtonRect(m).contains(mx, my)) {
			referenceStack = null;
			return true;
		}
		if (hasReferenceSlot() && referenceRowRect(m).contains(mx, my)) {
			if (referenceStack == null) {
				openReferenceItemPicker(ReferenceItemTarget.REFERENCE_SLOT);
			} else {
				openReferenceComponentPicker();
			}
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
		int fy = formFieldsY(m);
		for (FormFieldEntry entry : formFieldEntries()) {
			boolean hasPickerButton = entry.field() == formPrimary && formPrimaryHasPickerButton;
			if (hasPickerButton && formFieldPickerButtonRect(m, fy).contains(mx, my)) {
				openFieldPicker(RuleFieldRole.PRIMARY_VALUE);
				return true;
			}
			if (my >= fy && my < fy + FIELD_H && mx >= m.x() + GAP && mx < m.right() - GAP) {
				setFocusedField(entry.field());
				entry.field().mouseClicked(new MouseButtonEvent(mx, my, new MouseButtonInfo(0, 0)), false);
				return true;
			}
			fy += FIELD_H + FIELD_GAP;
		}
		return true;
	}

	/**
	 * Field-level picker entry point: switches FORM to the typed picker appropriate
	 * for the edited node, without touching the pending-node lifecycle
	 * (editingNode / editingIsNew are untouched — only the modal switches). The four
	 * exit paths (confirmPickerSelection / cancelOrReturnPicker / keyPressed Escape)
	 * bring the panel back to FORM via {@link #setFormFieldValue}.
	 */
	private void openFieldPicker(RuleFieldRole targetRole) {
		if (editingNode == null) {
			return;
		}
		if (editingNode.kind() == GroupFilterRuleDraft.NodeKind.ID
			|| editingNode.kind() == GroupFilterRuleDraft.NodeKind.EXACT_STACK) {
			openReferenceItemPicker(ReferenceItemTarget.FORM_PRIMARY);
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
		if (modal == ModalKind.REFERENCE_PICKER) {
			return referencePickerMouseClicked(mx, my);
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
		// Only a bare row-body press (past glyph / chip / edit·delete / scrollbar
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
				EditorChrome.Rect list = switch (modal) {
					case MENU -> menuListRect(menuModalRect());
					case REFERENCE_PICKER -> pickerListRect(referencePickerModalRect());
					default -> pickerListRect(pickerModalRect());
				};
				int content = switch (modal) {
					case MENU -> menuContentHeight();
					case REFERENCE_PICKER -> referencePickerContentHeight();
					default -> pickerContentHeight();
				};
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
		// Do not early-return on the ROW_GAP band (my >= rowTop + ROW_H): f then
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
		// Determine validity with canMove against the *slot's parent* (for BETWEEN(node,0)
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
			// target compound; BETWEEN inserts at the pre-detach index (moveNode
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
		if (modal == ModalKind.REFERENCE_PICKER) {
			EditorChrome.Rect list = pickerListRect(referencePickerModalRect());
			scrollModal(deltaY, referencePickerContentHeight(), list.height());
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
		if (modal == ModalKind.REFERENCE_PICKER && (key == 265 || key == 264)) { // Up / Down
			moveReferencePickerSelection(key == 264 ? 1 : -1);
			return true;
		}
		if (modal == ModalKind.FORM && key == 258) { // Tab
			cycleFormFocus((mods & 0x1) != 0); // GLFW_MOD_SHIFT
			return true;
		}
		if (focusedField != null && focusedField.isFocused() && focusedField.keyPressed(new KeyEvent(key, scan, mods))) {
			return true;
		}
		if (isModalOpen()) {
			if (key == 256) { // Escape
				if (modal == ModalKind.PICKER) {
					// Exit path: Esc.
					cancelOrReturnPicker();
				} else if (modal == ModalKind.REFERENCE_PICKER) {
					cancelOrBackReferencePicker();
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
				if (modal == ModalKind.REFERENCE_PICKER) {
					confirmReferencePickerSelection();
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
			return focusedField.charTyped(new CharacterEvent(c));
		}
		return false;
	}

	// ─────────────────────────────────────────────────────────────────────
	// Helpers
	// ─────────────────────────────────────────────────────────────────────

	private UiSkinRenderer.ButtonState buttonState(boolean enabled, boolean hovered) {
		if (!enabled) {
			return UiSkinRenderer.ButtonState.DISABLED;
		}
		return hovered ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL;
	}

	private static boolean hoverIn(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
