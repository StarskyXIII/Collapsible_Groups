package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorChrome;
import com.starskyxiii.collapsible_groups.compat.jei.ui.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-contained delegate for the Rules tab of {@link GroupEditorScreen}.
 *
 * <p>Owns its own widget list, layout, rendering, and input routing.
 * The Screen never adds these widgets to its own renderable list — all
 * communication back to the Screen flows through the {@code onChanged} callback.
 * When the user clicks Add, a modal overlays the body area.
 */
final class EditorRulesPanel {

    // ── Layout constants ──────────────────────────────────────────────────
    private static final int SIDEBAR_W  = 144;
    private static final int STATUS_H   = 22;
    private static final int GAP        = 6;
    private static final int PAD        = 4;
    private static final int BTN_H      = 18;
    private static final int FIELD_H    = 20;
    private static final int ROW_H      = 18;
    private static final int INDENT_W   = 10;
    private static final int FIELD_GAP  = 4;
    private static final int CONFIG_SCROLLBAR_GAP = 2;

    // ── Colors ────────────────────────────────────────────────────────────
    private static final int COL_DEFAULT = 0xFFFFFF;
    private static final int COL_LABEL   = 0x86AFC3;
    private static final int COL_TEXT    = 0xD8E7EF;
    private static final int COL_META    = 0xBBD7E6;
    private static final int COL_HINT    = 0x7A7A7A;
    private static final int COL_ERROR   = 0xFF4444;
    private static final int COL_SEL_BG  = 0x334488AA;

    // ── Rule kind arrays ──────────────────────────────────────────────────
    private static final GroupFilterRuleDraft.NodeKind[] COMPOUND_KINDS = {
        GroupFilterRuleDraft.NodeKind.ALL,
        GroupFilterRuleDraft.NodeKind.ANY,
        GroupFilterRuleDraft.NodeKind.NOT
    };
    private static final GroupFilterRuleDraft.NodeKind[] BASIC_KINDS = {
        GroupFilterRuleDraft.NodeKind.ID,
        GroupFilterRuleDraft.NodeKind.TAG,
        GroupFilterRuleDraft.NodeKind.NAMESPACE,
        GroupFilterRuleDraft.NodeKind.BLOCK_TAG,
        GroupFilterRuleDraft.NodeKind.ITEM_PATH_STARTS_WITH,
        GroupFilterRuleDraft.NodeKind.ITEM_PATH_ENDS_WITH,
        GroupFilterRuleDraft.NodeKind.HAS_COMPONENT,
        GroupFilterRuleDraft.NodeKind.COMPONENT_PATH,
        GroupFilterRuleDraft.NodeKind.EXACT_STACK
    };
    private static final GroupFilterRuleDraft.NodeKind[] WRAP_KINDS = {
        GroupFilterRuleDraft.NodeKind.ANY,
        GroupFilterRuleDraft.NodeKind.ALL,
        GroupFilterRuleDraft.NodeKind.NOT
    };

    // ── Row record ────────────────────────────────────────────────────────
    private record RuleRow(
        GroupFilterRuleDraft.Node node,
        int depth,
        List<FormattedCharSequence> lines,
        boolean selected
    ) {}

    // ── Dependencies ──────────────────────────────────────────────────────
    private final GroupEditorState state;
    private final Font             font;
    private final Runnable         onChanged;

    // ── Body rect ─────────────────────────────────────────────────────────
    private int bodyX, bodyY, bodyW, bodyH;

    // ── Scroll ────────────────────────────────────────────────────────────
    private int     scrollOffset   = 0;
    private boolean draggingScroll = false;
    private double  dragStartY;
    private int     dragStartOffset;

    private int     configureScrollOffset   = 0;
    private boolean configureDraggingScroll = false;
    private double  configureDragStartY;
    private int     configureDragStartOffset;

    // ── Modal ─────────────────────────────────────────────────────────────
    private boolean modalOpen = false;
    private int     modalScrollOffset   = 0;
    private boolean modalDraggingScroll = false;
    private double  modalDragStartY;
    private int     modalDragStartOffset;

    // ── Widgets (panel-owned, NOT in Screen's widget list) ────────────────
    private Button  btnAdd;
    private Button  btnDelete;
    private final List<Button> insertButtons = new ArrayList<>();
    private final List<Button> wrapButtons   = new ArrayList<>();
    private EditBox fieldType;
    private EditBox fieldPrimary;
    private EditBox fieldSecondary;
    private EditBox fieldTertiary;

    private EditBox  focusedField   = null;
    private boolean  updatingFields = false;

    // ─────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────

    EditorRulesPanel(GroupEditorState state, Font font, Runnable onChanged) {
        this.state     = state;
        this.font      = font;
        this.onChanged = onChanged;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    /** (Re-)creates all widgets for the given body area. Must be called before use or on resize. */
    void init(int bodyX, int bodyY, int bodyW, int bodyH) {
        this.bodyX = bodyX;
        this.bodyY = bodyY;
        this.bodyW = bodyW;
        this.bodyH = bodyH;
        insertButtons.clear();
        wrapButtons.clear();
        focusedField = null;
        configureDraggingScroll = false;
        modalDraggingScroll = false;
        modalScrollOffset = 0;
        createWidgets();
        refreshWidgetStates();
    }

    /** Call when switching TO Rules tab. */
    void onActivate() {
        state.ensureRuleSelection();
        modalOpen = false;
        modalDraggingScroll = false;
        modalScrollOffset = 0;
        refreshWidgetStates();
        clampScroll();
        clampConfigureScroll();
    }

    /** Call when switching AWAY from Rules tab. */
    void onDeactivate() {
        clearFocus();
        modalOpen = false;
        configureDraggingScroll = false;
        modalDraggingScroll = false;
        modalScrollOffset = 0;
    }

    /** Drops panel-local field focus (call when a header widget takes focus). */
    void clearFocus() {
        if (focusedField != null) {
            focusedField.setFocused(false);
            focusedField = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Widget creation
    // ─────────────────────────────────────────────────────────────────────

    private void createWidgets() {
        EditorChrome.Rect actions = actionsRect();
        int bx = actions.x() + PAD;
        int bw = Math.max(72, actions.width() - PAD * 2);
        int btnY0 = actions.y() + font.lineHeight + PAD + 6;

        btnAdd = Button.builder(
            Component.translatable(ModTranslationKeys.EDITOR_RULES_ADD),
            btn -> {
                modalOpen = true;
                modalDraggingScroll = false;
                modalScrollOffset = 0;
                refreshWidgetStates();
            }
        ).bounds(bx, btnY0, bw, BTN_H).build();

        btnDelete = Button.builder(
            Component.translatable(ModTranslationKeys.EDITOR_RULES_DELETE),
            btn -> { state.deleteSelectedRule(); onChanged.run(); }
        ).bounds(bx, btnY0 + BTN_H + 6, bw, BTN_H).build();

        // Modal buttons (positioned inside the modal rect)
        EditorChrome.Rect modal = currentModalRect();
        int colW   = Math.max(90, (modal.width() - GAP * 3) / 2);
        int col0   = modal.x() + GAP;
        int col1   = col0 + colW + GAP;
        int firstY = modalInsertButtonsY(modal);

        for (int i = 0; i < COMPOUND_KINDS.length; i++) {
            final GroupFilterRuleDraft.NodeKind kind = COMPOUND_KINDS[i];
            insertButtons.add(Button.builder(Component.literal(buttonLabel(kind)), btn -> {
                if (state.insertRuleRelative(kind) != null) {
                    modalOpen = false;
                    modalDraggingScroll = false;
                    onChanged.run();
                }
            }).bounds(col0, firstY + i * (BTN_H + 6), colW, BTN_H).build());
        }
        for (int i = 0; i < BASIC_KINDS.length; i++) {
            final GroupFilterRuleDraft.NodeKind kind = BASIC_KINDS[i];
            insertButtons.add(Button.builder(Component.literal(buttonLabel(kind)), btn -> {
                if (state.insertRuleRelative(kind) != null) {
                    modalOpen = false;
                    modalDraggingScroll = false;
                    onChanged.run();
                }
            }).bounds(col1, firstY + i * (BTN_H + 4), colW, BTN_H).build());
        }

        int wrapTop = modalWrapButtonsY(modal);
        for (int i = 0; i < WRAP_KINDS.length; i++) {
            final GroupFilterRuleDraft.NodeKind kind = WRAP_KINDS[i];
            wrapButtons.add(Button.builder(Component.literal(buttonLabel(kind)), btn -> {
                if (state.wrapSelectedRule(kind) != null) {
                    modalOpen = false;
                    modalDraggingScroll = false;
                    onChanged.run();
                }
            }).bounds(col0, wrapTop + i * (BTN_H + 6), colW, BTN_H).build());
        }

        // Configure fields (inside configureRect)
        EditorChrome.Rect cfg = configureRect();
        int fx  = cfg.x() + PAD;
        int fw  = Math.max(60, cfg.width() - PAD * 2);
        int fy  = cfg.y() + font.lineHeight + PAD + 6;

        fieldType      = buildField(fx, fy,      fw, Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_TYPE));
        fieldPrimary   = buildField(fx, fy + 24, fw, Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE));
        fieldSecondary = buildField(fx, fy + 48, fw, Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE_2));
        fieldTertiary  = buildField(fx, fy + 72, fw, Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE_3));
    }

    private EditBox buildField(int x, int y, int w, Component hint) {
        EditBox box = new EditBox(font, x, y, w, FIELD_H, Component.empty());
        box.setMaxLength(512);
        box.setHint(hint);
        box.setResponder(ignored -> applyFieldEdits());
        return box;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────

    void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        List<RuleRow> rows = buildRows();
        renderTree(g, mouseX, mouseY, rows);
        renderScrollbar(g, rows);
        renderStatus(g);
        if (modalOpen) {
            renderModal(g, mouseX, mouseY);
        } else {
            renderSidebar(g, mouseX, mouseY);
        }
    }

    private void renderTree(GuiGraphics g, int mouseX, int mouseY, List<RuleRow> rows) {
        EditorChrome.Rect r = treeRect();
        g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x22101828);
        drawBorder(g, r, 0x22667799);

        int y = r.y() + PAD - scrollOffset;
        g.enableScissor(r.x(), r.y(), r.right(), r.bottom());
        try {
            for (RuleRow row : rows) {
                int rh = rowH(row);
                if (row.selected()) g.fill(r.x() + 1, y, r.right() - 1, y + rh, COL_SEL_BG);
                int lx = r.x() + PAD + row.depth() * INDENT_W;
                int ly = y + 1;
                for (FormattedCharSequence line : row.lines()) {
                    g.drawString(font, line, lx, ly, row.selected() ? COL_DEFAULT : COL_TEXT, false);
                    ly += font.lineHeight;
                }
                y += rh;
            }
        } finally {
            g.disableScissor();
        }
    }

    private void renderScrollbar(GuiGraphics g, List<RuleRow> rows) {
        EditorChrome.Rect tree = treeRect();
        ScrollbarHelper.renderPixels(g, scrollbarX(), tree.y(), tree.height(),
            tree.height(), contentHeight(rows), scrollOffset);
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        EditorChrome.Rect acts = actionsRect();
        EditorChrome.Rect cfg  = configureRect();
        // Actions panel
        g.fill(acts.x(), acts.y(), acts.right(), acts.bottom(), 0x22101828);
        drawBorder(g, acts, 0x22667799);
        g.drawString(font, Component.translatable(ModTranslationKeys.EDITOR_PANEL_RULES_HEADER).getString(),
            acts.x() + PAD, acts.y() + PAD, COL_LABEL, false);
        if (btnAdd    != null) btnAdd.render(g, mouseX, mouseY, 0);
        if (btnDelete != null) btnDelete.render(g, mouseX, mouseY, 0);

        // Configure panel
        g.fill(cfg.x(), cfg.y(), cfg.right(), cfg.bottom(), 0x1A122032);
        drawBorder(g, cfg, 0x22667799);

        GroupFilterRuleDraft.Node sel = state.selectedRuleNode();
        renderConfigureHeader(g, cfg, sel);

        EditorChrome.Rect content = configureContentRect();
        layoutConfigureFields();
        g.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        try {
            renderField(g, mouseX, mouseY, fieldType);
            renderField(g, mouseX, mouseY, fieldPrimary);
            renderField(g, mouseX, mouseY, fieldSecondary);
            renderField(g, mouseX, mouseY, fieldTertiary);
        } finally {
            g.disableScissor();
        }

        EditorChrome.Rect scrollbar = configureScrollbarRect();
        ScrollbarHelper.renderPixels(g, scrollbar.x(), scrollbar.y(), scrollbar.height(),
            content.height(), configureContentHeight(), configureScrollOffset);
    }

    private void renderConfigureHeader(GuiGraphics g, EditorChrome.Rect cfg, GroupFilterRuleDraft.Node sel) {
        int x = cfg.x() + PAD;
        int y = cfg.y() + PAD;

        for (FormattedCharSequence line : configureTitleLines(sel)) {
            g.drawString(font, line, x, y, COL_LABEL, false);
            y += font.lineHeight;
        }

        List<FormattedCharSequence> contextLines = configureContextLines(sel);
        if (!contextLines.isEmpty()) {
            y += 2;
            for (FormattedCharSequence line : contextLines) {
                g.drawString(font, line, x, y, COL_META, false);
                y += font.lineHeight;
            }
        }
    }

    private void renderField(GuiGraphics g, int mx, int my, EditBox field) {
        if (field == null || !field.visible) return;
        field.render(g, mx, my, 0);
    }

    private void renderStatus(GuiGraphics g) {
        EditorChrome.Rect r = statusRect();
        int ty = r.y() + (r.height() - font.lineHeight) / 2;
        List<Component> errors = state.currentValidationErrors();
        String text  = errors.isEmpty() ? state.filterSummary() : errors.getFirst().getString();
        int    color = errors.isEmpty() ? COL_HINT : COL_ERROR;
        g.drawString(font, text, r.x() + PAD, ty, color, false);
    }

    private void renderModal(GuiGraphics g, int mouseX, int mouseY) {
        // Dim the body area
        g.fill(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH, 0x88060A12);

        EditorChrome.Rect m = currentModalRect();
        clampModalScroll(m);
        layoutModalButtons(m);
        g.fill(m.x(), m.y(), m.right(), m.bottom(), 0xEE141A24);
        drawBorder(g, m, 0x55B8D7EA);

        EditorChrome.Rect content = modalContentRect(m);
        EditorChrome.Rect scrollbar = modalScrollbarRect(m);
        int colW  = Math.max(90, (content.width() - GAP) / 2);
        int col0  = content.x();
        int col1  = col0 + colW + GAP;
        int topY  = modalTitleY(m);
        int lblY  = content.y() - modalScrollOffset;

        g.drawString(font, Component.translatable(ModTranslationKeys.EDITOR_RULES_ADD).getString(),
            m.x() + GAP, topY, 0x8CA6B7, false);

        g.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        try {
            g.drawString(font, Component.translatable(ModTranslationKeys.EDITOR_RULES_COMPOUND_HEADER).getString(),
                col0, lblY, COL_LABEL, false);
            g.drawString(font, Component.translatable(ModTranslationKeys.EDITOR_RULES_BASIC_HEADER).getString(),
                col1, lblY, COL_LABEL, false);

            for (Button b : insertButtons) {
                if (b.visible) b.render(g, mouseX, mouseY, 0);
            }

            boolean showWrap = state.selectedRuleNode() != null;
            if (showWrap) {
                int wrapTop = modalWrapHeaderY(m) - modalScrollOffset;
                g.drawString(font, Component.translatable(ModTranslationKeys.EDITOR_RULES_WRAP_HEADER).getString(),
                    col0, wrapTop, COL_LABEL, false);
            }
            for (Button b : wrapButtons) {
                if (b.visible) b.render(g, mouseX, mouseY, 0);
            }
        } finally {
            g.disableScissor();
        }

        ScrollbarHelper.renderPixels(g, scrollbar.x(), scrollbar.y(), scrollbar.height(),
            content.height(), modalScrollableContentHeight(), modalScrollOffset);
    }

    private void layoutConfigureFields() {
        EditorChrome.Rect content = configureContentRect();
        int x = content.x();
        int y = content.y() - configureScrollOffset;
        int width = content.width();
        for (EditBox field : allConfigureFields()) {
            if (field != null && field.visible) {
                field.setPosition(x, y);
                field.setWidth(width);
                y += FIELD_H + FIELD_GAP;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (modalOpen) {
            EditorChrome.Rect m = currentModalRect();
            clampModalScroll(m);
            layoutModalButtons(m);
            EditorChrome.Rect scrollbar = modalScrollbarRect(m);
            if (my >= scrollbar.y() && my < scrollbar.bottom()
                && mx >= scrollbar.x() && mx < scrollbar.right()) {
                modalDraggingScroll = true;
                modalDragStartY = my;
                modalDragStartOffset = modalScrollOffset;
                modalScrollOffset = ScrollbarHelper.trackClickToOffset(my, scrollbar.y(), scrollbar.height(),
                    modalScrollableContentHeight(), modalContentRect(m).height(), modalScrollOffset);
                layoutModalButtons(m);
                return true;
            }
            if (m.contains(mx, my)) {
                if (!modalContentRect(m).contains(mx, my)) return true;
                for (Button b : insertButtons) {
                    if (b.visible && isOver(b, mx, my) && b.mouseClicked(mx, my, button)) return true;
                }
                for (Button b : wrapButtons) {
                    if (b.visible && isOver(b, mx, my) && b.mouseClicked(mx, my, button)) return true;
                }
                return true;
            }
            modalOpen = false;
            modalDraggingScroll = false;
            refreshWidgetStates();
            return true;
        }

        EditorChrome.Rect scrollbar = configureScrollbarRect();
        if (my >= scrollbar.y() && my < scrollbar.bottom()
            && mx >= scrollbar.x() && mx < scrollbar.right()) {
            List<EditBox> fields = visibleFields();
            clampConfigureScroll(fields);
            configureDraggingScroll = true;
            configureDragStartY = my;
            configureDragStartOffset = configureScrollOffset;
            configureScrollOffset = ScrollbarHelper.trackClickToOffset(my, scrollbar.y(), scrollbar.height(),
                configureContentHeight(fields), configureContentRect().height(), configureScrollOffset);
            return true;
        }

        // ── Actions (Add / Delete) ─────────────────────────────────────────
        if (isOver(btnAdd,    mx, my) && btnAdd.mouseClicked(mx, my, button))    return true;
        if (isOver(btnDelete, mx, my) && btnDelete.mouseClicked(mx, my, button)) return true;

        // ── Configure fields ──────────────────────────────────────────────
        for (EditBox f : visibleFields()) {
            if (isOver(f, mx, my)) {
                setFocusedField(f);
                f.mouseClicked(mx, my, button);
                return true;
            }
        }
        // Click outside any field: drop focus
        clearFocus();

        // ── Tree scrollbar ─────────────────────────────────────────────────
        EditorChrome.Rect tree = treeRect();
        if (my >= tree.y() && my < tree.bottom()
            && mx >= scrollbarX() && mx < scrollbarX() + ScrollbarHelper.WIDTH) {
            List<RuleRow> rows = buildRows();
            clampScroll(rows);
            draggingScroll  = true;
            dragStartY      = my;
            dragStartOffset = scrollOffset;
            scrollOffset    = ScrollbarHelper.trackClickToOffset(my, tree.y(), tree.height(),
                contentHeight(rows), tree.height(), scrollOffset);
            return true;
        }

        // ── Tree row click ─────────────────────────────────────────────────
        if (tree.contains(mx, my)) return handleTreeClick(mx, my);

        return false;
    }

    boolean isModalOpen() { return modalOpen; }

    boolean mouseDragged(double mx, double my, int button) {
        if (modalOpen) {
            if (!modalDraggingScroll) return true;
            EditorChrome.Rect modal = currentModalRect();
            EditorChrome.Rect content = modalContentRect(modal);
            EditorChrome.Rect scrollbar = modalScrollbarRect(modal);
            modalScrollOffset = ScrollbarHelper.dragToOffset(my, modalDragStartY, modalDragStartOffset,
                modalScrollableContentHeight(), content.height(), scrollbar.height());
            layoutModalButtons(modal);
            return true;
        }
        if (configureDraggingScroll) {
            List<EditBox> fields = visibleFields();
            EditorChrome.Rect scrollbar = configureScrollbarRect();
            configureScrollOffset = ScrollbarHelper.dragToOffset(my, configureDragStartY, configureDragStartOffset,
                configureContentHeight(fields), configureContentRect().height(), scrollbar.height());
            layoutConfigureFields();
            return true;
        }
        if (button != 0 || !draggingScroll) return false;
        EditorChrome.Rect tree = treeRect();
        List<RuleRow> rows = buildRows();
        scrollOffset = ScrollbarHelper.dragToOffset(my, dragStartY, dragStartOffset,
            contentHeight(rows), tree.height(), tree.height());
        return true;
    }

    boolean mouseReleased(double mx, double my, int button) {
        draggingScroll = false;
        configureDraggingScroll = false;
        modalDraggingScroll = false;
        return false;
    }

    boolean mouseScrolled(double mx, double my, double deltaY) {
        if (modalOpen) {
            EditorChrome.Rect modal = currentModalRect();
            if (!modal.contains(mx, my)) return true;
            EditorChrome.Rect content = modalContentRect(modal);
            int maxScroll = Math.max(0, modalScrollableContentHeight() - content.height());
            if (maxScroll <= 0) {
                modalScrollOffset = 0;
                return true;
            }
            modalScrollOffset = ScrollbarHelper.clamp(
                modalScrollOffset - (int) Math.signum(deltaY) * (font.lineHeight + 4), 0, maxScroll);
            layoutModalButtons(modal);
            return true;
        }
        EditorChrome.Rect config = configureContentRect();
        if (config.contains(mx, my)) {
            List<EditBox> fields = visibleFields();
            int maxScroll = Math.max(0, configureContentHeight(fields) - config.height());
            if (maxScroll <= 0) {
                configureScrollOffset = 0;
                return true;
            }
            configureScrollOffset = ScrollbarHelper.clamp(
                configureScrollOffset - (int) Math.signum(deltaY) * (font.lineHeight + 4), 0, maxScroll);
            layoutConfigureFields();
            return true;
        }
        EditorChrome.Rect tree = treeRect();
        if (!tree.contains(mx, my)) return false;
        List<RuleRow> rows = buildRows();
        int maxScroll = Math.max(0, contentHeight(rows) - tree.height());
        if (maxScroll <= 0) { scrollOffset = 0; return true; }
        scrollOffset = ScrollbarHelper.clamp(
            scrollOffset - (int) Math.signum(deltaY) * (font.lineHeight + 4), 0, maxScroll);
        return true;
    }

    boolean keyPressed(int key, int scan, int mods) {
        if (focusedField != null && focusedField.isFocused()
            && focusedField.keyPressed(key, scan, mods)) return true;
        if (modalOpen && key == 256 /* Escape */) {
            modalOpen = false;
            modalDraggingScroll = false;
            refreshWidgetStates();
            return true;
        }
        return false;
    }

    boolean charTyped(char c, int mods) {
        if (focusedField != null && focusedField.isFocused()) return focusedField.charTyped(c, mods);
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────
    // State-change callback (called by Screen.onGroupChanged)
    // ─────────────────────────────────────────────────────────────────────

    void onGroupChanged() {
        state.ensureRuleSelection();
        refreshWidgetStates();
        clampScroll();
        clampConfigureScroll();
        if (modalOpen) clampModalScroll();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Layout helpers
    // ─────────────────────────────────────────────────────────────────────

    private int treeSectionW() {
        return bodyW - SIDEBAR_W - GAP - ScrollbarHelper.WIDTH - ScrollbarHelper.GAP;
    }

    private int treeSectionH() {
        return bodyH - STATUS_H - GAP;
    }

    /** Clip rect for the tree area (does not include the scrollbar column). */
    EditorChrome.Rect treeRect() {
        return new EditorChrome.Rect(bodyX, bodyY, treeSectionW(), treeSectionH());
    }

    private int scrollbarX() {
        return treeRect().right() + ScrollbarHelper.GAP;
    }

    private EditorChrome.Rect sidebarRect() {
        return new EditorChrome.Rect(bodyX + bodyW - SIDEBAR_W, bodyY, SIDEBAR_W, treeSectionH());
    }

    private EditorChrome.Rect actionsRect() {
        EditorChrome.Rect sb = sidebarRect();
        int h = font.lineHeight + PAD + BTN_H + 6 + BTN_H + PAD * 2;
        return new EditorChrome.Rect(sb.x(), sb.y(), SIDEBAR_W, h);
    }

    private EditorChrome.Rect configureRect() {
        EditorChrome.Rect act = actionsRect();
        int y = act.bottom() + GAP;
        return new EditorChrome.Rect(act.x(), y, SIDEBAR_W, Math.max(24, sidebarRect().bottom() - y));
    }

    private int configureHeaderBottom() {
        EditorChrome.Rect cfg = configureRect();
        int lineCount = configureTitleLines(state.selectedRuleNode()).size() + configureContextLines(state.selectedRuleNode()).size();
        int gap = configureContextLines(state.selectedRuleNode()).isEmpty() ? 0 : 2;
        return cfg.y() + PAD + lineCount * font.lineHeight + gap;
    }

    private EditorChrome.Rect configureContentRect() {
        EditorChrome.Rect cfg = configureRect();
        int top = configureHeaderBottom() + 8;
        int right = cfg.right() - PAD - ScrollbarHelper.WIDTH - CONFIG_SCROLLBAR_GAP;
        int width = Math.max(24, right - (cfg.x() + PAD));
        int height = Math.max(24, cfg.bottom() - top - PAD);
        return new EditorChrome.Rect(cfg.x() + PAD, top, width, height);
    }

    private EditorChrome.Rect configureScrollbarRect() {
        EditorChrome.Rect content = configureContentRect();
        return new EditorChrome.Rect(content.right() + CONFIG_SCROLLBAR_GAP, content.y(), ScrollbarHelper.WIDTH, content.height());
    }

    private EditorChrome.Rect statusRect() {
        int y = bodyY + treeSectionH() + GAP;
        return new EditorChrome.Rect(bodyX, y, bodyW - SIDEBAR_W - GAP, STATUS_H);
    }

    private EditorChrome.Rect modalRect() {
        int minW = 360, maxW = 470, minH = 300;
        int inset = GAP;
        int innerH = modalContentHeight();

        // Allow the modal to borrow the tab/header strip above the rules body.
        // This keeps high GUI-scale layouts usable without forcing the wrap/basic
        // sections to spill out of the frame.
        int availTop = inset;
        int availBottom = bodyY + bodyH - inset;

        // Available space inside insets — the modal must never exceed this.
        int availW = Math.max(0, bodyW - inset * 2);
        int availH = Math.max(0, availBottom - availTop);

        // Desired dimensions: prefer [minW..maxW] and content-driven height.
        // Container always wins: clamp down to availW / availH last.
        int desiredW = Math.min(maxW, Math.max(minW, bodyW * 2 / 3));
        int desiredH = Math.max(minH, innerH);
        int w = Math.min(availW, desiredW);
        int h = Math.min(availH, desiredH);

        // Center horizontally inside the rules body and vertically inside the
        // expanded top-to-bottom viewport that includes the tab/header strip.
        int x = bodyX + inset + (availW - w) / 2;
        int y = availTop + (availH - h) / 2;
        return new EditorChrome.Rect(x, y, w, h);
    }

    private int modalInsertColumnHeight() {
        return Math.max(
            COMPOUND_KINDS.length * (BTN_H + 6) - 6,
            BASIC_KINDS.length    * (BTN_H + 4) - 4
        );
    }

    private int modalTitleY(EditorChrome.Rect modal) {
        return modal.y() + GAP;
    }

    private int modalSectionLabelY(EditorChrome.Rect modal) {
        return modalTitleY(modal) + font.lineHeight + 4;
    }

    private int modalInsertButtonsY(EditorChrome.Rect modal) {
        return modalSectionLabelY(modal) + font.lineHeight + 6;
    }

    private int modalCompoundColumnHeight() {
        return COMPOUND_KINDS.length * (BTN_H + 6) - 6;
    }

    private int modalBasicColumnHeight() {
        return BASIC_KINDS.length * (BTN_H + 4) - 4;
    }

    private int modalWrapHeaderY(EditorChrome.Rect modal) {
        return modalInsertButtonsY(modal) + modalCompoundColumnHeight() + 10;
    }

    private int modalWrapButtonsY(EditorChrome.Rect modal) {
        return modalWrapHeaderY(modal) + font.lineHeight + 4;
    }

    private int modalWrapButtonsHeight() {
        return WRAP_KINDS.length * (BTN_H + 6) - 6;
    }

    private int modalContentHeight() {
        return GAP + font.lineHeight + 4 + modalScrollableContentHeight() + GAP;
    }

    private EditorChrome.Rect currentModalRect() {
        int minW = 360, maxW = 470, minH = 300;
        int inset = GAP;
        int innerH = modalContentHeight();
        int availW = Math.max(0, bodyW - inset * 2);
        int availH = Math.max(0, bodyH - inset * 2);
        int desiredW = Math.min(maxW, Math.max(minW, bodyW * 2 / 3));
        int desiredH = Math.max(minH, innerH);
        int w = Math.min(availW, desiredW);
        int h = Math.min(availH, desiredH);
        int x = bodyX + inset + (availW - w) / 2;
        int y = bodyY + inset + (availH - h) / 2;
        return new EditorChrome.Rect(x, y, w, h);
    }

    private EditorChrome.Rect modalContentRect(EditorChrome.Rect modal) {
        int x = modal.x() + GAP;
        int top = modalTitleY(modal) + font.lineHeight + 4;
        int right = modal.right() - GAP - ScrollbarHelper.WIDTH - ScrollbarHelper.GAP;
        int width = Math.max(24, right - x);
        int height = Math.max(24, modal.bottom() - top - GAP);
        return new EditorChrome.Rect(x, top, width, height);
    }

    private EditorChrome.Rect modalScrollbarRect(EditorChrome.Rect modal) {
        EditorChrome.Rect content = modalContentRect(modal);
        return new EditorChrome.Rect(content.right() + ScrollbarHelper.GAP, content.y(),
            ScrollbarHelper.WIDTH, content.height());
    }

    private int modalScrollableContentHeight() {
        int leftColumnHeight = font.lineHeight + 6 + modalCompoundColumnHeight();
        if (state.selectedRuleNode() != null) {
            leftColumnHeight += 10 + font.lineHeight + 4 + modalWrapButtonsHeight();
        }
        int rightColumnHeight = font.lineHeight + 6 + modalBasicColumnHeight();
        return Math.max(leftColumnHeight, rightColumnHeight);
    }

    private void clampModalScroll() {
        clampModalScroll(currentModalRect());
    }

    private void clampModalScroll(EditorChrome.Rect modal) {
        int max = Math.max(0, modalScrollableContentHeight() - modalContentRect(modal).height());
        modalScrollOffset = ScrollbarHelper.clamp(modalScrollOffset, 0, max);
    }

    private void layoutModalButtons(EditorChrome.Rect modal) {
        EditorChrome.Rect content = modalContentRect(modal);
        int colW = Math.max(90, (content.width() - GAP) / 2);
        int col0 = content.x();
        int col1 = col0 + colW + GAP;
        int sectionY = content.y() - modalScrollOffset;
        int firstY = sectionY + font.lineHeight + 6;

        for (int i = 0; i < COMPOUND_KINDS.length; i++) {
            Button b = insertButtons.get(i);
            b.setPosition(col0, firstY + i * (BTN_H + 6));
            b.setWidth(colW);
        }
        for (int i = 0; i < BASIC_KINDS.length; i++) {
            Button b = insertButtons.get(COMPOUND_KINDS.length + i);
            b.setPosition(col1, firstY + i * (BTN_H + 4));
            b.setWidth(colW);
        }

        int wrapTop = sectionY + font.lineHeight + 6 + modalCompoundColumnHeight() + 10 + font.lineHeight + 4;
        for (int i = 0; i < WRAP_KINDS.length; i++) {
            Button b = wrapButtons.get(i);
            b.setPosition(col0, wrapTop + i * (BTN_H + 6));
            b.setWidth(colW);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tree helpers
    // ─────────────────────────────────────────────────────────────────────

    private List<RuleRow> buildRows() {
        int bodyWidth = Math.max(40, treeSectionW() - PAD * 2);
        List<RuleRow> rows = new ArrayList<>();
        for (GroupFilterRuleDraft.FlatNode fn : state.flattenedRuleNodes()) {
            GroupFilterRuleDraft.Node n = fn.node();
            int ww = Math.max(24, bodyWidth - fn.depth() * INDENT_W);
            rows.add(new RuleRow(n, fn.depth(),
                font.split(Component.literal(describeNode(n)), ww),
                n == state.selectedRuleNode()));
        }
        return List.copyOf(rows);
    }

    private int rowH(RuleRow row) {
        return Math.max(ROW_H, row.lines().size() * font.lineHeight + 2);
    }

    private int contentHeight(List<RuleRow> rows) {
        int h = PAD;
        for (RuleRow r : rows) h += rowH(r);
        return h;
    }

    private void clampScroll() { clampScroll(buildRows()); }

    private void clampScroll(List<RuleRow> rows) {
        int max = Math.max(0, contentHeight(rows) - treeRect().height());
        scrollOffset = ScrollbarHelper.clamp(scrollOffset, 0, max);
    }

    private int configureContentHeight() {
        int count = 0;
        for (EditBox field : allConfigureFields()) {
            if (field != null && field.visible) {
                count++;
            }
        }
        if (count == 0) {
            return font.lineHeight * 2 + PAD * 2;
        }
        return count * FIELD_H + Math.max(0, count - 1) * FIELD_GAP + PAD * 2;
    }

    private int configureContentHeight(List<EditBox> fields) {
        int count = 0;
        for (EditBox field : fields) {
            if (field != null && field.visible) {
                count++;
            }
        }
        if (count == 0) {
            return font.lineHeight * 2 + PAD * 2;
        }
        return count * FIELD_H + Math.max(0, count - 1) * FIELD_GAP + PAD * 2;
    }

    private void clampConfigureScroll() {
        int max = Math.max(0, configureContentHeight() - configureContentRect().height());
        configureScrollOffset = ScrollbarHelper.clamp(configureScrollOffset, 0, max);
    }

    private void clampConfigureScroll(List<EditBox> ignored) {
        int max = Math.max(0, configureContentHeight(ignored) - configureContentRect().height());
        configureScrollOffset = ScrollbarHelper.clamp(configureScrollOffset, 0, max);
    }

    private boolean handleTreeClick(double mx, double my) {
        EditorChrome.Rect r = treeRect();
        int y = r.y() + PAD - scrollOffset;
        for (RuleRow row : buildRows()) {
            int h = rowH(row);
            if (my >= y && my < y + h) {
                state.selectRuleNode(row.node());
                refreshWidgetStates();
                return true;
            }
            y += h;
        }
        return false;
    }

    private List<EditBox> allConfigureFields() {
        List<EditBox> out = new ArrayList<>(4);
        out.add(fieldType);
        out.add(fieldPrimary);
        out.add(fieldSecondary);
        out.add(fieldTertiary);
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Widget state management
    // ─────────────────────────────────────────────────────────────────────

    private void refreshWidgetStates() {
        if (btnAdd == null) return;
        GroupFilterRuleDraft.Node sel = state.selectedRuleNode();

        btnAdd.active    = !modalOpen && state.canInsertRuleRelative();
        btnDelete.active = !modalOpen && state.canDeleteSelectedRule();

        for (int i = 0; i < COMPOUND_KINDS.length; i++) {
            Button b = insertButtons.get(i);
            b.active  = state.canInsertRuleRelative();
            b.visible = modalOpen;
        }
        for (int i = 0; i < BASIC_KINDS.length; i++) {
            Button b = insertButtons.get(COMPOUND_KINDS.length + i);
            b.active  = state.canInsertRuleRelative();
            b.visible = modalOpen;
        }
        boolean showWrap = modalOpen && sel != null;
        for (int i = 0; i < WRAP_KINDS.length; i++) {
            Button b = wrapButtons.get(i);
            b.active  = showWrap && state.canWrapSelectedRule(WRAP_KINDS[i]);
            b.visible = showWrap;
        }

        if (modalOpen) {
            clearFocus();
            setConfigureFieldsVisible(false);
        } else {
            syncFieldsFromNode();
            clampConfigureScroll();
            layoutConfigureFields();
        }
    }

    private void syncFieldsFromNode() {
        if (fieldPrimary == null) return;
        GroupFilterRuleDraft.Node sel = state.selectedRuleNode();
        updatingFields = true;
        try {
            boolean hasType = sel != null && needsTypeField(sel);
            boolean hasPri  = sel != null && needsPrimaryField(sel);
            boolean hasSec  = sel != null && needsSecondaryField(sel);
            boolean hasTer  = sel != null && needsTertiaryField(sel);

            configureField(fieldType,      hasType, typeHint(sel),      sel != null ? sel.ingredientType() : "");
            configureField(fieldPrimary,   hasPri,  primaryHint(sel),   sel != null ? sel.primaryValue()   : "");
            configureField(fieldSecondary, hasSec,  secondaryHint(sel), sel != null ? sel.secondaryValue() : "");
            configureField(fieldTertiary,  hasTer,  tertiaryHint(),     sel != null ? sel.tertiaryValue()  : "");
        } finally {
            updatingFields = false;
        }
    }

    private void configureField(EditBox field, boolean visible, Component hint, String value) {
        field.visible = visible;
        field.active  = visible;
        if (!visible && focusedField == field) {
            field.setFocused(false);
            focusedField = null;
        }
        field.setHint(hint);
        if (!field.getValue().equals(value)) field.setValue(value);
    }

    private void applyFieldEdits() {
        if (updatingFields) return;
        GroupFilterRuleDraft.Node sel = state.selectedRuleNode();
        if (sel == null) return;
        if (needsTypeField(sel)      && fieldType      != null) sel.setIngredientType(fieldType.getValue());
        if (needsPrimaryField(sel)   && fieldPrimary   != null) sel.setPrimaryValue(fieldPrimary.getValue());
        if (needsSecondaryField(sel) && fieldSecondary != null) sel.setSecondaryValue(fieldSecondary.getValue());
        if (needsTertiaryField(sel)  && fieldTertiary  != null) sel.setTertiaryValue(fieldTertiary.getValue());
        state.markRulesChanged();
        onChanged.run();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Focus helpers
    // ─────────────────────────────────────────────────────────────────────

    private void setFocusedField(EditBox field) {
        if (focusedField != null && focusedField != field) focusedField.setFocused(false);
        focusedField = field;
        field.setFocused(true);
    }

    private List<EditBox> visibleFields() {
        List<EditBox> out = new ArrayList<>(4);
        if (fieldType      != null && fieldType.visible)      out.add(fieldType);
        if (fieldPrimary   != null && fieldPrimary.visible)   out.add(fieldPrimary);
        if (fieldSecondary != null && fieldSecondary.visible) out.add(fieldSecondary);
        if (fieldTertiary  != null && fieldTertiary.visible)  out.add(fieldTertiary);
        return out;
    }

    private List<FormattedCharSequence> configureTitleLines(GroupFilterRuleDraft.Node sel) {
        String cfgBase = Component.translatable(ModTranslationKeys.EDITOR_RULES_CONFIGURE).getString();
        String cfgTitle = sel == null ? cfgBase : cfgBase + ": " + sel.kind().name();
        return font.split(Component.literal(cfgTitle), configureHeaderWidth());
    }

    private List<FormattedCharSequence> configureContextLines(GroupFilterRuleDraft.Node sel) {
        if (sel == null) return List.of();
        return font.split(Component.literal(describeContext(sel)), configureHeaderWidth());
    }

    private int configureHeaderWidth() {
        return Math.max(24, configureRect().width() - PAD * 2);
    }

    private void setConfigureFieldsVisible(boolean visible) {
        for (EditBox field : allConfigureFields()) {
            if (field == null) continue;
            field.visible = visible && field.active;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Rule description
    // ─────────────────────────────────────────────────────────────────────

    private String describeNode(GroupFilterRuleDraft.Node n) {
        return switch (n.kind()) {
            case ANY -> "ANY";
            case ALL -> "ALL";
            case NOT -> "NOT";
            case ID              -> n.ingredientType() + " id: "        + n.primaryValue();
            case TAG             -> n.ingredientType() + " tag: "       + n.primaryValue();
            case NAMESPACE       -> n.ingredientType() + " namespace: " + n.primaryValue();
            case BLOCK_TAG       -> "block tag: "                       + n.primaryValue();
            case ITEM_PATH_STARTS_WITH -> "path starts: "              + n.primaryValue();
            case ITEM_PATH_ENDS_WITH   -> "path ends: "                + n.primaryValue();
            case EXACT_STACK     -> "exact: "                          + n.primaryValue();
            case HAS_COMPONENT   -> "has component: "  + n.primaryValue() + " = " + n.secondaryValue();
            case COMPONENT_PATH  -> "comp path: "      + n.primaryValue() + "/" + n.secondaryValue() + "=" + n.tertiaryValue();
        };
    }

    private String describeContext(GroupFilterRuleDraft.Node n) {
        String loc = n.parent() == null ? "Root" : "in " + n.parent().kind().name();
        String ch  = n.kind().compound()
            ? n.children().size() + " child" + (n.children().size() == 1 ? "" : "ren")
            : "Leaf";
        return loc + " | " + ch;
    }

    private String buttonLabel(GroupFilterRuleDraft.NodeKind kind) {
        return switch (kind) {
            case ALL -> "All Of";
            case ANY -> "Any Of";
            case NOT -> "Not";
            case ID  -> "Item ID";
            case TAG -> "Item Tag";
            case NAMESPACE             -> "Namespace";
            case BLOCK_TAG             -> "Block Tag";
            case ITEM_PATH_STARTS_WITH -> "Path Starts";
            case ITEM_PATH_ENDS_WITH   -> "Path Ends";
            case HAS_COMPONENT         -> "Has Component";
            case COMPONENT_PATH        -> "Component Path";
            case EXACT_STACK           -> "Exact Stack";
        };
    }

    private boolean needsTypeField(GroupFilterRuleDraft.Node n) {
        return switch (n.kind()) { case ID, TAG, NAMESPACE -> true; default -> false; };
    }
    private boolean needsPrimaryField(GroupFilterRuleDraft.Node n) { return !n.kind().compound(); }
    private boolean needsSecondaryField(GroupFilterRuleDraft.Node n) {
        return switch (n.kind()) { case HAS_COMPONENT, COMPONENT_PATH -> true; default -> false; };
    }
    private boolean needsTertiaryField(GroupFilterRuleDraft.Node n) {
        return n.kind() == GroupFilterRuleDraft.NodeKind.COMPONENT_PATH;
    }

    private Component typeHint(GroupFilterRuleDraft.Node n) {
        return Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_TYPE);
    }
    private Component primaryHint(GroupFilterRuleDraft.Node n) {
        if (n == null) return Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE);
        return switch (n.kind()) {
            case ID              -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_ID);
            case TAG, BLOCK_TAG  -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_TAG);
            case NAMESPACE       -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_NAMESPACE);
            case ITEM_PATH_STARTS_WITH, ITEM_PATH_ENDS_WITH -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_PATH);
            case EXACT_STACK     -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_STACK);
            case HAS_COMPONENT, COMPONENT_PATH -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_COMPONENT);
            default              -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE);
        };
    }
    private Component secondaryHint(GroupFilterRuleDraft.Node n) {
        if (n == null) return Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE_2);
        return switch (n.kind()) {
            case HAS_COMPONENT  -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE);
            case COMPONENT_PATH -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_PATH);
            default             -> Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE_2);
        };
    }
    private Component tertiaryHint() {
        return Component.translatable(ModTranslationKeys.EDITOR_RULES_FIELD_VALUE);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Drawing utilities
    // ─────────────────────────────────────────────────────────────────────

    private void drawBorder(GuiGraphics g, EditorChrome.Rect r, int color) {
        g.fill(r.x(), r.y(),         r.right(), r.y() + 1,         color);
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(),        color);
        g.fill(r.x(), r.y() + 1,     r.x() + 1, r.bottom() - 1,    color);
        g.fill(r.right() - 1, r.y() + 1, r.right(), r.bottom() - 1, color);
    }

    private static boolean isOver(AbstractWidget w, double mx, double my) {
        return w.visible
            && mx >= w.getX() && mx < w.getX() + w.getWidth()
            && my >= w.getY() && my < w.getY() + w.getHeight();
    }
}
