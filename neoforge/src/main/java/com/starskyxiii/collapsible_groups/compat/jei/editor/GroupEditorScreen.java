package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.GroupUiState;
import com.starskyxiii.collapsible_groups.compat.jei.manager.GroupManagerScreen;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorChrome;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;
import com.starskyxiii.collapsible_groups.compat.jei.ui.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Split-pane editor for a single collapsible group.
 *
 * <p>Contents tab: managed by {@link EditorLeftPanel} (browse) + {@link EditorRightPanel} (preview).
 * Rules tab: fully delegated to {@link EditorRulesPanel}.
 * Edit state lives in {@link GroupEditorState}.
 */
public class GroupEditorScreen extends Screen {

    // ── Shared chrome constants ────────────────────────────────────────────
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFF;
    private static final int ERROR_TEXT_COLOR   = 0xFF4444;
    private static final int HINT_TEXT_COLOR    = 0xFF7A7A7A;
    private static final int HEADER_TEXT_COLOR  = 0x8CA6B7;
    private static final int TAB_HEIGHT   = 18;
    private static final int CHIP_HEIGHT  = 18;
    private static final int SEARCH_HEIGHT = 18;
    private static final int TAB_GAP      = 4;

    // ── Tab enums ─────────────────────────────────────────────────────────
    private enum BrowserTab {
        ITEMS(ModTranslationKeys.EDITOR_TAB_ITEMS),
        FLUIDS(ModTranslationKeys.EDITOR_TAB_FLUIDS),
        GENERIC(ModTranslationKeys.EDITOR_TAB_GENERIC);

        private final String labelKey;
        BrowserTab(String labelKey) { this.labelKey = labelKey; }
        String label() { return Component.translatable(labelKey).getString(); }
    }

    private enum GroupTab {
        CONTENTS(ModTranslationKeys.EDITOR_TAB_CONTENTS),
        RULES(ModTranslationKeys.EDITOR_TAB_RULES);

        private final String labelKey;
        GroupTab(String labelKey) { this.labelKey = labelKey; }
        String label() { return Component.translatable(labelKey).getString(); }
    }

    // ── Screen state ──────────────────────────────────────────────────────
    private final GroupManagerScreen parent;
    private final GroupEditorState   state;

    private EditorLeftPanel  leftPanel;
    private EditorRightPanel rightPanel;
    private EditorRulesPanel rulesPanel;
    private EditorLayout     layout;

    private BrowserTab activeBrowserTab = BrowserTab.ITEMS;
    private GroupTab   activeGroupTab   = GroupTab.CONTENTS;

    // ── Shared header widgets (always registered) ─────────────────────────
    private EditBox nameField;
    private EditBox searchField;
    private Button  btnSave;
    private Button  btnCancel;

    private final Component nameFieldHint   = Component.translatable(ModTranslationKeys.EDITOR_NAME_HINT);
    private final Component searchFieldHint = Component.translatable(ModTranslationKeys.EDITOR_SEARCH_HINT);

    // ── Inner class: hint-overlay EditBox ─────────────────────────────────
    private static final class EditorTextField extends EditBox {
        private final Font font;
        private final Component overlayHint;
        private final boolean resetTextColorOnFocus;
        private int currentTextColor = DEFAULT_TEXT_COLOR;

        EditorTextField(Font font, int x, int y, int width, int height,
                        Component message, Component overlayHint, boolean resetOnFocus) {
            super(font, x, y, width, height, message);
            this.font = font;
            this.overlayHint = overlayHint;
            this.resetTextColorOnFocus = resetOnFocus;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (focused && resetTextColorOnFocus) setTextColor(DEFAULT_TEXT_COLOR);
        }

        @Override
        public void setTextColor(int color) {
            super.setTextColor(color);
            this.currentTextColor = color;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
            super.renderWidget(g, mouseX, mouseY, partialTicks);
            if (overlayHint == null || isFocused() || !getValue().isEmpty()) return;
            int x = getX() + 4;
            int y = EditorChrome.centeredTextY(font, getY(), getHeight());
            int maxWidth = Math.max(0, getWidth() - 8);
            String text = font.plainSubstrByWidth(overlayHint.getString(), maxWidth);
            int color = (currentTextColor & 0xFFFFFF) == (ERROR_TEXT_COLOR & 0xFFFFFF)
                ? ERROR_TEXT_COLOR : HINT_TEXT_COLOR;
            g.pose().pushPose();
            g.pose().translate(0, 0, 1);
            g.drawString(font, text, x, y, color, false);
            g.pose().popPose();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Constructor / Screen lifecycle
    // ─────────────────────────────────────────────────────────────────────

    public GroupEditorScreen(GroupManagerScreen parent, GroupDefinition existing) {
        super(Component.translatable(existing == null
            ? ModTranslationKeys.SCREEN_NEW_GROUP : ModTranslationKeys.SCREEN_EDIT_GROUP));
        this.parent = parent;
        this.state  = new GroupEditorState(existing);
    }

    @Override
    protected void init() {
        layout     = EditorLayout.compute(this.width, this.height);
        leftPanel  = new EditorLeftPanel(state, this::onGroupChanged);
        rightPanel = new EditorRightPanel(state, this::onGroupChanged);
        rulesPanel = new EditorRulesPanel(state, font, this::onGroupChanged);

        GroupRegistry.populateJeiCachesIfEmpty();
        @SuppressWarnings("unchecked")
        List<FluidStack> allFluids = (List<FluidStack>) (List<?>) GroupRegistry.getJeiAllFluids();
        leftPanel.init(
            GroupRegistry.getJeiAllItems().isEmpty()
                ? net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                    .filter(i -> i != net.minecraft.world.item.Items.AIR)
                    .map(net.minecraft.world.item.ItemStack::new)
                    .toList()
                : GroupRegistry.getJeiAllItems(),
            allFluids,
            GroupRegistry.getJeiAllGenericIngredients()
        );
        leftPanel.setHideUsed(GroupUiState.hideUsed());
        rightPanel.rebuild();

        // ── Header widgets ────────────────────────────────────────────────
        int headerY = (EditorLayout.HEADER_HEIGHT - 20) / 2;

        nameField = new EditorTextField(font, 8, headerY, Math.min(220, this.width / 3), 20,
            Component.translatable(ModTranslationKeys.EDITOR_NAME_LABEL), nameFieldHint, true);
        nameField.setMaxLength(64);
        nameField.setValue(state.editName);
        nameField.setTextColor(DEFAULT_TEXT_COLOR);
        nameField.setResponder(value -> { state.editName = value; updateSaveButtonState(); });
        addRenderableWidget(nameField);

        EditorChrome.Rect searchRect = searchFieldRect();
        searchField = new EditorTextField(font, searchRect.x(), searchRect.y(), searchRect.width(), searchRect.height(),
            Component.translatable(ModTranslationKeys.EDITOR_SEARCH_LABEL), searchFieldHint, false);
        searchField.setMaxLength(128);
        searchField.setTextColor(DEFAULT_TEXT_COLOR);
        searchField.setResponder(value -> leftPanel.rebuildFilter(value));
        addRenderableWidget(searchField);

        btnSave = addRenderableWidget(Button.builder(
            Component.translatable(ModTranslationKeys.BUTTON_SAVE), btn -> saveAndClose())
            .bounds(this.width - 118, headerY, 54, 20).build());
        btnCancel = addRenderableWidget(Button.builder(
            Component.translatable(ModTranslationKeys.BUTTON_CANCEL), btn -> onClose())
            .bounds(this.width - 60, headerY, 54, 20).build());

        // ── Rules panel ────────────────────────────────────────────────────
        rulesPanel.init(rulesBodyX(), rulesBodyY(), rulesBodyW(), rulesBodyH());

        updateSaveButtonState();
        applyBrowserTab(activeBrowserTab);
        leftPanel.clampScroll(layout);
        rightPanel.clampScroll(layout);

        // Sync visibility with active tab
        searchField.visible = searchField.active = (activeGroupTab == GroupTab.CONTENTS);
        if (activeGroupTab == GroupTab.RULES) rulesPanel.onActivate();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ─────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        renderBackground(g, mouseX, mouseY, partialTicks);

        int headerH  = EditorLayout.HEADER_HEIGHT;
        int footerY  = this.height - EditorLayout.FOOTER_HEIGHT;
        int footerTextY = EditorChrome.centeredTextY(font, footerY, EditorLayout.FOOTER_HEIGHT);

        // Chrome backgrounds
        g.fill(0, 0, this.width, headerH, 0xCC0E0E1A);
        g.fill(0, headerH, this.width, headerH + 1, 0x33667799);
        g.fill(0, footerY, this.width, this.height, 0xAA0E0E1A);
        g.fill(0, footerY, this.width, footerY + 1, 0x33667799);

        if (activeGroupTab == GroupTab.CONTENTS) {
            // Contents panel backgrounds
            int panelTop = panelTop();
            int lpLeft  = layout.leftGridX()   - EditorLayout.PANEL_INSET;
            int lpRight = layout.leftScrollbarX() + ScrollbarHelper.WIDTH + EditorLayout.PANEL_INSET;
            int lpBottom = footerY + 2;
            g.fill(lpLeft, panelTop, lpRight, lpBottom, 0x55101020);
            drawPanelBorder(g, lpLeft, panelTop, lpRight, lpBottom, 0x22AABBCC, true, false);

            int rpLeft  = layout.rightGridX()   - EditorLayout.PANEL_INSET;
            int rpRight = layout.rightScrollbarX() + ScrollbarHelper.WIDTH + EditorLayout.PANEL_INSET;
            g.fill(rpLeft, panelTop, rpRight, lpBottom, 0x55101020);
            drawPanelBorder(g, rpLeft, panelTop, rpRight, lpBottom, 0x22AABBCC, false, false);

            int divX = layout.dividerX();
            g.fill(divX - 1, headerH + 1, divX,     footerY, 0x18667799);
            g.fill(divX,     headerH + 1, divX + 1, footerY, 0x44667799);
            g.fill(divX + 1, headerH + 1, divX + 2, footerY, 0x18667799);
        }

        // ── Body ──────────────────────────────────────────────────────────
        if (activeGroupTab == GroupTab.CONTENTS) {
            leftPanel.render(g, mouseX, mouseY, layout);
            rightPanel.render(g, mouseX, mouseY, layout);

            ScrollbarHelper.render(g, layout.leftScrollbarX(), layout.gridTop(), layout.gridHeight(),
                layout.leftRows(), leftPanel.totalRows(layout), leftPanel.scrollRow);
            ScrollbarHelper.render(g, layout.rightScrollbarX(), layout.gridTop(), layout.gridHeight(),
                layout.rightRows(), rightPanel.totalRows(layout), rightPanel.scrollRow);

            drawBrowserTabs(g, mouseX, mouseY);
            EditorChrome.drawChip(g, font, hideUsedChipRect(),
                Component.translatable(ModTranslationKeys.EDITOR_CHIP_HIDE_USED).getString(),
                leftPanel.isHideUsed(), hideUsedChipRect().contains(mouseX, mouseY));

            g.drawString(font, leftPanel.currentPanelHeader(),
                layout.leftGridX(), panelHeaderY(), HEADER_TEXT_COLOR, false);
            g.drawString(font,
                Component.translatable(ModTranslationKeys.EDITOR_PANEL_CONTENTS_HEADER,
                    rightPanel.groupSummary()).getString(),
                layout.rightGridX(), panelHeaderY(), HEADER_TEXT_COLOR, false);

            g.drawString(font, leftPanel.countLabel(),         6,                footerTextY, 0x8899AABB, false);
            g.drawString(font, rightPanel.groupSummary(), layout.dividerX() + 6, footerTextY, 0x8899AABB, false);
        } else {
            rulesPanel.render(g, mouseX, mouseY, partialTicks);
        }

        drawGroupTabs(g, mouseX, mouseY);

        // Registered renderables (nameField, searchField, btnSave, btnCancel) — always on top
        for (var renderable : this.renderables) {
            renderable.render(g, mouseX, mouseY, partialTicks);
        }

        // Tooltips
        if (activeGroupTab == GroupTab.CONTENTS) {
            GroupEditorTooltipHelper.render(g, mouseX, mouseY, leftPanel, rightPanel, state, font);
        }
        if (btnSave != null && !btnSave.active && isMouseOverWidget(btnSave, mouseX, mouseY)) {
            g.renderComponentTooltip(font, state.saveBlockedTooltip(), mouseX, mouseY);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input routing
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (activeGroupTab == GroupTab.RULES && rulesPanel.isModalOpen()) {
            rulesPanel.mouseClicked(mx, my, button);
            return true;
        }

        // nameField gets explicit priority so it always captures header clicks
        if (button == 0 && nameField != null && nameField.isMouseOver(mx, my)) {
            if (activeGroupTab == GroupTab.RULES) rulesPanel.clearFocus();
            if (searchField != null) searchField.setFocused(false);
            setFocused(nameField);
            nameField.setFocused(true);
            return nameField.mouseClicked(mx, my, button);
        }
        if (nameField != null && !nameField.isMouseOver(mx, my)) nameField.setFocused(false);

        // Registered widgets: searchField (if visible), btnSave, btnCancel
        if (super.mouseClicked(mx, my, button)) return true;

        // Chrome (tabs, chip)
        if (handleChromeClick(mx, my, button)) return true;

        // Mode-specific body
        if (activeGroupTab == GroupTab.CONTENTS) {
            if (leftPanel.mouseClicked(mx, my, button, layout)) return true;
            if (rightPanel.mouseClicked(mx, my, button, layout, leftPanel.allItems())) return true;
        } else {
            if (rulesPanel.mouseClicked(mx, my, button)) return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (activeGroupTab == GroupTab.RULES && rulesPanel.isModalOpen()) {
            rulesPanel.keyPressed(key, scan, mods);
            return true;
        }
        if (nameField != null && nameField.isFocused() && nameField.keyPressed(key, scan, mods)) return true;
        if (searchField != null && searchField.visible && searchField.isFocused()
            && searchField.keyPressed(key, scan, mods)) return true;
        if (activeGroupTab == GroupTab.RULES && rulesPanel.keyPressed(key, scan, mods)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (activeGroupTab == GroupTab.RULES && rulesPanel.isModalOpen()) {
            rulesPanel.charTyped(c, mods);
            return true;
        }
        if (nameField != null && nameField.isFocused() && nameField.charTyped(c, mods)) return true;
        if (searchField != null && searchField.visible && searchField.isFocused()
            && searchField.charTyped(c, mods)) return true;
        if (activeGroupTab == GroupTab.RULES && rulesPanel.charTyped(c, mods)) return true;
        return super.charTyped(c, mods);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0) return super.mouseDragged(mx, my, button, dx, dy);
        if (activeGroupTab == GroupTab.RULES && rulesPanel.isModalOpen()) {
            rulesPanel.mouseDragged(mx, my, button);
            return true;
        }
        if (activeGroupTab == GroupTab.CONTENTS) {
            if (leftPanel.mouseDragged(mx, my, button, layout))  return true;
            if (rightPanel.mouseDragged(mx, my, button, layout)) return true;
        } else {
            if (rulesPanel.mouseDragged(mx, my, button)) return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (activeGroupTab == GroupTab.CONTENTS) {
            leftPanel.mouseReleased(button);
            rightPanel.mouseReleased(button);
        } else {
            rulesPanel.mouseReleased(mx, my, button);
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (activeGroupTab == GroupTab.RULES && rulesPanel.isModalOpen()) {
            rulesPanel.mouseScrolled(mx, my, dy);
            return true;
        }
        if (activeGroupTab == GroupTab.CONTENTS) {
            if (leftPanel.mouseScrolled(mx, my, dy, layout))  return true;
            if (rightPanel.mouseScrolled(mx, my, dy, layout)) return true;
        } else {
            if (rulesPanel.mouseScrolled(mx, my, dy)) return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        layout = EditorLayout.compute(this.width, this.height);
        int headerY = (EditorLayout.HEADER_HEIGHT - 20) / 2;
        if (nameField  != null) nameField.setPosition(8, headerY);
        if (btnSave    != null) btnSave.setPosition(this.width - 118, headerY);
        if (btnCancel  != null) btnCancel.setPosition(this.width - 60, headerY);
        if (searchField != null) {
            EditorChrome.Rect sr = searchFieldRect();
            searchField.setPosition(sr.x(), sr.y());
            searchField.setWidth(sr.width());
        }
        if (rulesPanel != null) {
            rulesPanel.init(rulesBodyX(), rulesBodyY(), rulesBodyW(), rulesBodyH());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Chrome helpers
    // ─────────────────────────────────────────────────────────────────────

    private void drawBrowserTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (BrowserTab tab : BrowserTab.values()) {
            EditorChrome.Rect r = browserTabRect(tab);
            EditorChrome.drawTab(g, font, r, tab.label(),
                activeBrowserTab == tab, isBrowserTabEnabled(tab), r.contains(mouseX, mouseY));
        }
    }

    private void drawGroupTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (GroupTab tab : GroupTab.values()) {
            EditorChrome.Rect r = groupTabRect(tab);
            EditorChrome.drawTab(g, font, r, tab.label(),
                activeGroupTab == tab, true, r.contains(mouseX, mouseY));
        }
    }

    private boolean handleChromeClick(double mx, double my, int button) {
        if (button != 0) return false;

        if (activeGroupTab == GroupTab.CONTENTS) {
            for (BrowserTab tab : BrowserTab.values()) {
                EditorChrome.Rect r = browserTabRect(tab);
                if (r.contains(mx, my) && isBrowserTabEnabled(tab)) {
                    activeBrowserTab = tab;
                    applyBrowserTab(tab);
                    return true;
                }
            }
        }

        for (GroupTab tab : GroupTab.values()) {
            EditorChrome.Rect r = groupTabRect(tab);
            if (r.contains(mx, my)) {
                if (tab != activeGroupTab) switchToTab(tab);
                return true;
            }
        }

        if (activeGroupTab == GroupTab.CONTENTS) {
            EditorChrome.Rect chip = hideUsedChipRect();
            if (chip.contains(mx, my)) {
                boolean hide = !leftPanel.isHideUsed();
                leftPanel.setHideUsed(hide);
                GroupUiState.setHideUsed(hide);
                leftPanel.rebuildFilter(searchQuery());
                leftPanel.clampScroll(layout);
                return true;
            }
        }
        return false;
    }

    private void switchToTab(GroupTab tab) {
        if (activeGroupTab == GroupTab.RULES) rulesPanel.onDeactivate();
        activeGroupTab = tab;
        clearRightHover();
        boolean isContents = (tab == GroupTab.CONTENTS);
        searchField.visible = searchField.active = isContents;
        if (!isContents) {
            searchField.setFocused(false);
            rulesPanel.onActivate();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Layout helpers
    // ─────────────────────────────────────────────────────────────────────

    private int rulesBodyX() { return 4; }
    private int rulesBodyY() { return EditorLayout.HEADER_HEIGHT + 4 + TAB_HEIGHT + 8; } // = 66
    private int rulesBodyW() { return this.width - 8; }
    private int rulesBodyH() { return (this.height - EditorLayout.FOOTER_HEIGHT) - rulesBodyY(); }

    private int panelTop() {
        return layout.gridTop() - EditorLayout.LABEL_ROW_HEIGHT - 2;
    }

    private int tabRowY() {
        return EditorLayout.HEADER_HEIGHT + 4;
    }

    private int controlRowY() {
        return tabRowY() + TAB_HEIGHT + 5;
    }

    private int panelHeaderY() {
        return layout.gridTop() - font.lineHeight - 1;
    }

    private EditorChrome.Rect browserTabRect(BrowserTab tab) {
        int x = layout.leftGridX();
        for (BrowserTab value : BrowserTab.values()) {
            int width = EditorChrome.tabWidth(font, value.label());
            if (value == tab) return new EditorChrome.Rect(x, tabRowY(), width, TAB_HEIGHT);
            x += width + TAB_GAP;
        }
        return new EditorChrome.Rect(x, tabRowY(), EditorChrome.tabWidth(font, tab.label()), TAB_HEIGHT);
    }

    /** Group tabs always anchor to {@code layout.rightGridX()} — fixed position in both modes. */
    private EditorChrome.Rect groupTabRect(GroupTab tab) {
        int x = layout.rightGridX();
        for (GroupTab value : GroupTab.values()) {
            int width = EditorChrome.tabWidth(font, value.label());
            if (value == tab) return new EditorChrome.Rect(x, tabRowY(), width, TAB_HEIGHT);
            x += width + TAB_GAP;
        }
        return new EditorChrome.Rect(x, tabRowY(), EditorChrome.tabWidth(font, tab.label()), TAB_HEIGHT);
    }

    private EditorChrome.Rect hideUsedChipRect() {
        int width = EditorChrome.chipWidth(font,
            Component.translatable(ModTranslationKeys.EDITOR_CHIP_HIDE_USED).getString());
        int x = layout.leftGridX() + layout.leftGridWidth() - width;
        return new EditorChrome.Rect(x, controlRowY(), width, CHIP_HEIGHT);
    }

    private EditorChrome.Rect searchFieldRect() {
        int x = layout.leftGridX();
        EditorChrome.Rect chip = hideUsedChipRect();
        int width = Math.max(92, chip.x() - x - 8);
        return new EditorChrome.Rect(x, controlRowY(), width, SEARCH_HEIGHT);
    }

    private String searchQuery() {
        return searchField != null ? searchField.getValue() : "";
    }

    // ─────────────────────────────────────────────────────────────────────
    // Drawing utilities
    // ─────────────────────────────────────────────────────────────────────

    private void drawPanelBorder(GuiGraphics g, int x1, int y1, int x2, int y2, int color,
                                 boolean drawLeft, boolean drawRight) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        if (drawLeft)  g.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        if (drawRight) g.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tab / mode helpers
    // ─────────────────────────────────────────────────────────────────────

    private void applyBrowserTab(BrowserTab tab) {
        String q = searchQuery();
        switch (tab) {
            case ITEMS   -> leftPanel.showItems(q);
            case FLUIDS  -> leftPanel.showFluids(q);
            case GENERIC -> leftPanel.showGeneric(q);
        }
        leftPanel.clampScroll(layout);
    }

    private boolean isBrowserTabEnabled(BrowserTab tab) { return true; }

    // ─────────────────────────────────────────────────────────────────────
    // State change
    // ─────────────────────────────────────────────────────────────────────

    private void onGroupChanged() {
        long t = PerformanceTrace.begin();
        state.ensureRuleSelection();
        rightPanel.rebuild();
        leftPanel.clampScroll(layout);
        rightPanel.clampScroll(layout);
        if (activeGroupTab == GroupTab.RULES) rulesPanel.onGroupChanged();
        updateSaveButtonState();
        PerformanceTrace.logIfSlow("GroupEditorScreen.onGroupChanged", t, 10,
            "tab=" + activeGroupTab.name());
    }

    private void updateSaveButtonState() {
        if (btnSave != null) btnSave.active = state.canSave();
    }

    private void saveAndClose() {
        GroupDefinition saved = state.trySave().orElse(null);
        if (saved == null) {
            nameField.setTextColor(ERROR_TEXT_COLOR);
            return;
        }
        nameField.setTextColor(DEFAULT_TEXT_COLOR);
        GroupRegistry.invalidateFullMatchCache(saved.id());
        GroupRegistry.populateFullMatchCacheFromSaved(saved);
        parent.onGroupSaved();
        GroupRegistry.notifyJei();
        Minecraft.getInstance().setScreen(parent);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Hover / misc utilities
    // ─────────────────────────────────────────────────────────────────────

    private void clearRightHover() {
        rightPanel.hoveredItem = -1;
        rightPanel.hoveredFluid = -1;
        rightPanel.hoveredGeneric = -1;
    }

    private void clearLeftHover() {
        leftPanel.hoveredItem = -1;
        leftPanel.hoveredFluid = -1;
        leftPanel.hoveredGeneric = -1;
    }

    private static boolean isMouseOverWidget(Button btn, double mx, double my) {
        return mx >= btn.getX() && mx < btn.getX() + btn.getWidth()
            && my >= btn.getY() && my < btn.getY() + btn.getHeight();
    }
}
