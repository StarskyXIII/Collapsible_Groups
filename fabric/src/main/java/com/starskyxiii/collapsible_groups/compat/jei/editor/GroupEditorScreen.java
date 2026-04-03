package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.GroupUiState;
import com.starskyxiii.collapsible_groups.compat.jei.manager.GroupManagerScreen;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorChrome;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorLayout;
import com.starskyxiii.collapsible_groups.compat.jei.ui.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupFilterClauseFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Split-pane editor for a single collapsible group.
 * Item-only variant with the shared tabbed editor chrome.
 */
public class GroupEditorScreen extends Screen {
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
	private static final int ERROR_TEXT_COLOR   = 0xFFFF4444;
	private static final int HINT_TEXT_COLOR    = 0xFF7A7A7A;
	private static final int RULE_LABEL_COLOR   = 0xFF86AFC3;
	private static final int RULE_TEXT_COLOR    = 0xFFD8E7EF;
	private static final int HEADER_TEXT_COLOR  = 0xFF8CA6B7;
	private static final int TAB_HEIGHT         = 18;
	private static final int CHIP_HEIGHT        = 18;
	private static final int SEARCH_HEIGHT      = 18;
	private static final int TAB_GAP            = 4;
	private static final int RULE_SECTION_GAP   = 8;
	private static final int RULE_CLAUSE_INDENT = 10;
	private static final int RULE_TEXT_PADDING  = 2;

	private enum BrowserTab {
		ITEMS(ModTranslationKeys.EDITOR_TAB_ITEMS),
		FLUIDS(ModTranslationKeys.EDITOR_TAB_FLUIDS),
		GENERIC(ModTranslationKeys.EDITOR_TAB_GENERIC);

		private final String labelKey;

		BrowserTab(String labelKey) {
			this.labelKey = labelKey;
		}

		public String label() {
			return Component.translatable(labelKey).getString();
		}
	}

	private enum GroupTab {
		CONTENTS(ModTranslationKeys.EDITOR_TAB_CONTENTS),
		RULES(ModTranslationKeys.EDITOR_TAB_RULES);

		private final String labelKey;

		GroupTab(String labelKey) {
			this.labelKey = labelKey;
		}

		public String label() {
			return Component.translatable(labelKey).getString();
		}
	}

	private final GroupManagerScreen parent;
	private final GroupEditorState   state;

	private EditorLeftPanel  leftPanel;
	private EditorRightPanel rightPanel;
	private EditorLayout     layout;

	private BrowserTab activeBrowserTab = BrowserTab.ITEMS;
	private GroupTab   activeGroupTab   = GroupTab.CONTENTS;
	private int rulesScrollOffset = 0;
	private boolean isDraggingRulesScrollbar = false;
	private double rulesScrollbarDragStartMouseY;
	private int rulesScrollbarDragStartOffset;

	private EditBox nameField;
	private EditBox searchField;
	private Button  btnSave;
	private final Component nameFieldHint   = Component.translatable(ModTranslationKeys.EDITOR_NAME_HINT);
	private final Component searchFieldHint = Component.translatable(ModTranslationKeys.EDITOR_SEARCH_HINT);

	private static final class EditorTextField extends EditBox {
		private final Font font;
		private final Component overlayHint;
		private final boolean resetTextColorOnFocus;
		private int currentTextColor = DEFAULT_TEXT_COLOR;

		private EditorTextField(Font font, int x, int y, int width, int height, Component message,
		                        Component overlayHint, boolean resetTextColorOnFocus) {
			super(font, x, y, width, height, message);
			this.font = font;
			this.overlayHint = overlayHint;
			this.resetTextColorOnFocus = resetTextColorOnFocus;
		}

		@Override
		public void setFocused(boolean focused) {
			super.setFocused(focused);
			if (focused && resetTextColorOnFocus) {
				this.setTextColor(DEFAULT_TEXT_COLOR);
			}
		}

		@Override
		public void setTextColor(int color) {
			super.setTextColor(color);
			this.currentTextColor = color;
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
			super.extractWidgetRenderState(g, mouseX, mouseY, partialTicks);
			if (overlayHint == null || isFocused() || !getValue().isEmpty()) return;
			int x = getX() + 4;
			int y = EditorChrome.centeredTextY(font, getY(), getHeight());
			int maxWidth = Math.max(0, getWidth() - 8);
			String text = font.plainSubstrByWidth(overlayHint.getString(), maxWidth);
			int color = (currentTextColor & 0xFFFFFF) == (ERROR_TEXT_COLOR & 0xFFFFFF)
				? ERROR_TEXT_COLOR : HINT_TEXT_COLOR;
			g.pose().pushMatrix();
			g.nextStratum();
			g.text(font, text, x, y, color, false);
			g.pose().popMatrix();
		}
	}

	private record RuleLine(int depth, List<FormattedCharSequence> wrappedLines, int color) {}

	private record RuleSection(String title, List<RuleLine> lines) {}

	private record RulesContent(List<RuleSection> sections, int contentHeight) {}

	public GroupEditorScreen(GroupManagerScreen parent, GroupDefinition existing) {
		super(Component.translatable(existing == null ? ModTranslationKeys.SCREEN_NEW_GROUP : ModTranslationKeys.SCREEN_EDIT_GROUP));
		this.parent = parent;
		this.state  = new GroupEditorState(existing);
	}

	@Override
	protected void init() {
		layout     = EditorLayout.compute(this.width, this.height);
		leftPanel  = new EditorLeftPanel(state, this::onGroupChanged);
		rightPanel = new EditorRightPanel(state, this::onGroupChanged);

		GroupRegistry.populateJeiCachesIfEmpty();
		leftPanel.init(
			GroupRegistry.getJeiAllItems().isEmpty()
				? net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
					.filter(i -> i != net.minecraft.world.item.Items.AIR)
					.map(net.minecraft.world.item.ItemStack::new)
					.toList()
				: GroupRegistry.getJeiAllItems()
		);
		leftPanel.setHideUsed(GroupUiState.hideUsed());

		rightPanel.rebuild();

		int headerY = (EditorLayout.HEADER_HEIGHT - 20) / 2;
		nameField = new EditorTextField(font, 8, headerY, Math.min(220, this.width / 3), 20,
			Component.translatable(ModTranslationKeys.EDITOR_NAME_LABEL), nameFieldHint, true);
		nameField.setMaxLength(64);
		nameField.setValue(state.editName);
		nameField.setTextColor(DEFAULT_TEXT_COLOR);
		nameField.setResponder(value -> {
			state.editName = value;
			updateSaveButtonState();
		});
		addRenderableWidget(nameField);

		EditorChrome.Rect searchRect = searchFieldRect();
		searchField = new EditorTextField(font, searchRect.x(), searchRect.y(), searchRect.width(), searchRect.height(),
			Component.translatable(ModTranslationKeys.EDITOR_SEARCH_LABEL), searchFieldHint, false);
		searchField.setMaxLength(128);
		searchField.setTextColor(DEFAULT_TEXT_COLOR);
		searchField.setResponder(value -> leftPanel.rebuildFilter(value));
		addRenderableWidget(searchField);

		btnSave = addRenderableWidget(Button.builder(Component.translatable(ModTranslationKeys.BUTTON_SAVE), btn -> saveAndClose())
			.bounds(this.width - 118, headerY, 54, 20).build());
		addRenderableWidget(Button.builder(Component.translatable(ModTranslationKeys.BUTTON_CANCEL), btn -> onClose())
			.bounds(this.width - 60, headerY, 54, 20).build());
		updateSaveButtonState();

		applyBrowserTab(activeBrowserTab);
		leftPanel.clampScroll(layout);
		rightPanel.clampScroll(layout);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
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

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		extractBackground(g, mouseX, mouseY, partialTicks);

		int headerH  = EditorLayout.HEADER_HEIGHT;
		int footerY  = this.height - EditorLayout.FOOTER_HEIGHT;
		int divX     = layout.dividerX();
		int panelTop = layout.gridTop() - EditorLayout.LABEL_ROW_HEIGHT - 2;
		int footerTextY = EditorChrome.centeredTextY(font, footerY, EditorLayout.FOOTER_HEIGHT);
		RulesContent rulesContent = activeGroupTab == GroupTab.RULES ? buildRulesContent() : null;

		g.fill(0, 0, this.width, headerH, 0xCC0E0E1A);
		g.fill(0, headerH, this.width, headerH + 1, 0x33667799);

		int lpLeft   = layout.leftGridX() - EditorLayout.PANEL_INSET;
		int lpRight  = layout.leftScrollbarX() + ScrollbarHelper.WIDTH + EditorLayout.PANEL_INSET;
		int lpBottom = footerY + 2;
		g.fill(lpLeft, panelTop, lpRight, lpBottom, 0x55101020);
		drawPanelBorder(g, lpLeft, panelTop, lpRight, lpBottom, 0x22AABBCC, true, false);

		int rpLeft   = layout.rightGridX() - EditorLayout.PANEL_INSET;
		int rpRight  = layout.rightScrollbarX() + ScrollbarHelper.WIDTH + EditorLayout.PANEL_INSET;
		g.fill(rpLeft, panelTop, rpRight, lpBottom, 0x55101020);
		drawPanelBorder(g, rpLeft, panelTop, rpRight, lpBottom, 0x22AABBCC, false, false);

		g.fill(divX - 1, headerH + 1, divX,     footerY, 0x18667799);
		g.fill(divX,     headerH + 1, divX + 1, footerY, 0x44667799);
		g.fill(divX + 1, headerH + 1, divX + 2, footerY, 0x18667799);

		g.fill(0, footerY, this.width, this.height, 0xAA0E0E1A);
		g.fill(0, footerY, this.width, footerY + 1, 0x33667799);

		leftPanel.render(g, mouseX, mouseY, layout);
		if (activeGroupTab == GroupTab.CONTENTS) {
			rightPanel.render(g, mouseX, mouseY, layout);
		} else {
			clearRightHover();
			clampRulesScroll(rulesContent);
			renderRulesPanel(g, rulesContent);
		}

		ScrollbarHelper.render(g, layout.leftScrollbarX(), layout.gridTop(), layout.gridHeight(),
			layout.leftRows(), leftPanel.totalRows(layout), leftPanel.scrollRow);
		if (activeGroupTab == GroupTab.CONTENTS) {
			ScrollbarHelper.render(g, layout.rightScrollbarX(), layout.gridTop(), layout.gridHeight(),
				layout.rightRows(), rightPanel.totalRows(layout), rightPanel.scrollRow);
		} else {
			EditorChrome.Rect rulesViewport = rulesViewportRect();
			ScrollbarHelper.renderPixels(g, layout.rightScrollbarX(), rulesViewport.y(), rulesViewport.height(),
				rulesViewport.height(), rulesContent.contentHeight(), rulesScrollOffset);
		}

		drawBrowserTabs(g, mouseX, mouseY);
		drawGroupTabs(g, mouseX, mouseY);
		EditorChrome.drawChip(g, font, hideUsedChipRect(),
			Component.translatable(ModTranslationKeys.EDITOR_CHIP_HIDE_USED).getString(),
			leftPanel.isHideUsed(), hideUsedChipRect().contains(mouseX, mouseY));

		g.text(font, leftPanel.currentPanelHeader(),
			layout.leftGridX(), panelHeaderY(), HEADER_TEXT_COLOR, false);

		if (activeGroupTab == GroupTab.CONTENTS) {
			g.text(font,
				Component.translatable(ModTranslationKeys.EDITOR_PANEL_CONTENTS_HEADER, rightPanel.groupSummary()).getString(),
				layout.rightGridX(), panelHeaderY(), HEADER_TEXT_COLOR, false);
		} else {
			g.text(font,
				Component.translatable(ModTranslationKeys.EDITOR_PANEL_RULES_HEADER).getString(),
				layout.rightGridX(), panelHeaderY(), HEADER_TEXT_COLOR, false);
		}

		g.text(font, leftPanel.countLabel(), 6, footerTextY, 0x8899AABB, false);
		String footerRightText = activeGroupTab == GroupTab.CONTENTS
			? rightPanel.groupSummary()
			: state.filterEditStatusLabel();
		g.text(font, footerRightText, divX + 6, footerTextY, 0x8899AABB, false);

		for (var child : this.children()) {
			if (child instanceof net.minecraft.client.gui.components.Renderable r) {
				r.extractRenderState(g, mouseX, mouseY, partialTicks);
			}
		}

		GroupEditorTooltipHelper.render(g, mouseX, mouseY, leftPanel, rightPanel, state, font);
		if (btnSave != null && !btnSave.active && isMouseOverWidget(btnSave, mouseX, mouseY)) {
			g.setComponentTooltipForNextFrame(font, state.saveBlockedTooltip(), mouseX, mouseY);
		}
	}

	private void drawBrowserTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		for (BrowserTab tab : BrowserTab.values()) {
			EditorChrome.Rect rect = browserTabRect(tab);
			EditorChrome.drawTab(g, font, rect, tab.label(),
				activeBrowserTab == tab, isBrowserTabEnabled(tab), rect.contains(mouseX, mouseY));
		}
	}

	private void drawGroupTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		for (GroupTab tab : GroupTab.values()) {
			EditorChrome.Rect rect = groupTabRect(tab);
			EditorChrome.drawTab(g, font, rect, tab.label(),
				activeGroupTab == tab, true, rect.contains(mouseX, mouseY));
		}
	}

	private void renderRulesPanel(GuiGraphicsExtractor g, RulesContent rulesContent) {
		EditorChrome.Rect viewport = rulesViewportRect();
		int x = viewport.x() + RULE_TEXT_PADDING;
		int y = viewport.y() + RULE_TEXT_PADDING - rulesScrollOffset;

		g.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
		try {
			for (RuleSection section : rulesContent.sections()) {
				g.text(font, section.title(), x, y, RULE_LABEL_COLOR, false);
				y += font.lineHeight + 2;
				for (RuleLine line : section.lines()) {
					int lineX = x + line.depth() * RULE_CLAUSE_INDENT;
					for (FormattedCharSequence wrappedLine : line.wrappedLines()) {
						g.text(font, wrappedLine, lineX, y, line.color(), false);
						y += font.lineHeight;
					}
				}
				y += RULE_SECTION_GAP;
			}
		} finally {
			g.disableScissor();
		}
	}

	private RulesContent buildRulesContent() {
		int bodyWidth = Math.max(40, rulesViewportRect().width() - RULE_TEXT_PADDING * 2);
		List<RuleSection> sections = new ArrayList<>();
		sections.add(buildRuleSection(Component.translatable(ModTranslationKeys.EDITOR_RULES_STATUS).getString(), state.filterEditStatusLabel(),
			state.isSaveBlockedByUnsupportedFilter() ? ERROR_TEXT_COLOR : RULE_TEXT_COLOR, bodyWidth));
		sections.add(buildRuleSection(Component.translatable(ModTranslationKeys.EDITOR_RULES_UNSUPPORTED).getString(), state.unsupportedNodeKindsLabel(), RULE_TEXT_COLOR, bodyWidth));
		sections.add(buildRuleSection(Component.translatable(ModTranslationKeys.EDITOR_RULES_REASON).getString(), state.unsupportedReasonSummary(), RULE_TEXT_COLOR, bodyWidth));
		sections.add(buildRuleSection(Component.translatable(ModTranslationKeys.EDITOR_RULES_SUMMARY).getString(), state.filterSummary(), RULE_TEXT_COLOR, bodyWidth));
		if (state.shouldShowRuleClauses()) {
			sections.add(buildClauseSection(Component.translatable(ModTranslationKeys.EDITOR_RULES_CLAUSES).getString(), state.rulesDisplayFilter(), RULE_TEXT_COLOR, bodyWidth));
		}
		sections.add(buildRuleSection(Component.translatable(ModTranslationKeys.EDITOR_RULES_PREVIEW_NOTE).getString(), state.previewOwnershipNote(), HINT_TEXT_COLOR, bodyWidth));
		int contentHeight = RULE_TEXT_PADDING;
		for (RuleSection section : sections) {
			int bodyLineCount = section.lines().stream()
				.mapToInt(line -> line.wrappedLines().size())
				.sum();
			contentHeight += font.lineHeight + 2 + bodyLineCount * font.lineHeight + RULE_SECTION_GAP;
		}
		return new RulesContent(sections, contentHeight);
	}

	private RuleSection buildRuleSection(String title, String body, int bodyColor, int bodyWidth) {
		return new RuleSection(title, List.of(new RuleLine(0, wrapRuleBody(body, bodyWidth), bodyColor)));
	}

	private RuleSection buildClauseSection(String title, GroupFilter filter, int bodyColor, int bodyWidth) {
		List<GroupFilterClauseFormatter.Clause> clauses = GroupFilterClauseFormatter.format(filter);
		if (clauses.isEmpty()) {
			return buildRuleSection(title, Component.translatable(ModTranslationKeys.EDITOR_RULES_NO_FILTER).getString(), HINT_TEXT_COLOR, bodyWidth);
		}

		List<RuleLine> lines = new ArrayList<>(clauses.size());
		for (GroupFilterClauseFormatter.Clause clause : clauses) {
			int availableWidth = Math.max(24, bodyWidth - clause.depth() * RULE_CLAUSE_INDENT);
			lines.add(new RuleLine(clause.depth(), wrapRuleBody(formatClause(clause), availableWidth), bodyColor));
		}
		return new RuleSection(title, List.copyOf(lines));
	}

	private List<FormattedCharSequence> wrapRuleBody(String body, int bodyWidth) {
		return font.split(Component.literal(body == null ? "" : body), bodyWidth);
	}

	private static String formatClause(GroupFilterClauseFormatter.Clause clause) {
		String value = clause.value();
		return value == null || value.isBlank()
			? clause.label()
			: clause.label() + ": " + value;
	}

	private void drawPanelBorder(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color,
	                             boolean drawLeftEdge, boolean drawRightEdge) {
		g.fill(x1, y1, x2, y1 + 1, color);
		g.fill(x1, y2 - 1, x2, y2, color);
		if (drawLeftEdge)  g.fill(x1,     y1 + 1, x1 + 1, y2 - 1, color);
		if (drawRightEdge) g.fill(x2 - 1, y1 + 1, x2,     y2 - 1, color);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		if (nameField != null && !nameField.isMouseOver(mouseX, mouseY)) nameField.setFocused(false);
		if (searchField != null && !searchField.isMouseOver(mouseX, mouseY)) searchField.setFocused(false);
		if (super.mouseClicked(event, doubleClick)) return true;
		if (handleChromeClick(mouseX, mouseY, button)) return true;
		if (leftPanel.mouseClicked(mouseX, mouseY, button, layout)) return true;
		if (activeGroupTab == GroupTab.RULES && handleRulesClick(mouseX, mouseY, button)) return true;
		if (activeGroupTab == GroupTab.CONTENTS
			&& rightPanel.mouseClicked(mouseX, mouseY, button, layout, leftPanel.allItems())) return true;
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		if (button != 0) return super.mouseDragged(event, deltaX, deltaY);
		if (leftPanel.mouseDragged(mouseX, mouseY, button, layout)) return true;
		if (activeGroupTab == GroupTab.RULES && handleRulesDrag(mouseY)) return true;
		if (activeGroupTab == GroupTab.CONTENTS && rightPanel.mouseDragged(mouseX, mouseY, button, layout)) return true;
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		int button = event.button();
		leftPanel.mouseReleased(button);
		rightPanel.mouseReleased(button);
		isDraggingRulesScrollbar = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		if (leftPanel.mouseScrolled(mouseX, mouseY, deltaY, layout)) return true;
		if (activeGroupTab == GroupTab.RULES && handleRulesScroll(mouseX, mouseY, deltaY)) return true;
		if (activeGroupTab == GroupTab.CONTENTS && rightPanel.mouseScrolled(mouseX, mouseY, deltaY, layout)) return true;
		return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
	}

	private boolean handleChromeClick(double mouseX, double mouseY, int button) {
		if (button != 0) return false;

		for (BrowserTab tab : BrowserTab.values()) {
			EditorChrome.Rect rect = browserTabRect(tab);
			if (rect.contains(mouseX, mouseY)) {
				if (isBrowserTabEnabled(tab)) {
					activeBrowserTab = tab;
					applyBrowserTab(tab);
				}
				return true;
			}
		}

		for (GroupTab tab : GroupTab.values()) {
			EditorChrome.Rect rect = groupTabRect(tab);
			if (rect.contains(mouseX, mouseY)) {
				activeGroupTab = tab;
				clearRightHover();
				isDraggingRulesScrollbar = false;
				if (tab == GroupTab.RULES) clampRulesScroll(buildRulesContent());
				return true;
			}
		}

		EditorChrome.Rect chipRect = hideUsedChipRect();
		if (chipRect.contains(mouseX, mouseY)) {
			boolean hideUsed = !leftPanel.isHideUsed();
			leftPanel.setHideUsed(hideUsed);
			GroupUiState.setHideUsed(hideUsed);
			leftPanel.rebuildFilter(searchQuery());
			leftPanel.clampScroll(layout);
			return true;
		}

		return false;
	}

	private void applyBrowserTab(BrowserTab tab) {
		String query = searchQuery();
		switch (tab) {
			case ITEMS -> leftPanel.showItems(query);
			case FLUIDS -> leftPanel.showFluids(query);
			case GENERIC -> leftPanel.showGeneric(query);
		}
		leftPanel.clampScroll(layout);
	}

	private boolean isBrowserTabEnabled(BrowserTab tab) {
		return tab == BrowserTab.ITEMS;
	}

	private String searchQuery() {
		return searchField != null ? searchField.getValue() : "";
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
			if (value == tab) {
				return new EditorChrome.Rect(x, tabRowY(), width, TAB_HEIGHT);
			}
			x += width + TAB_GAP;
		}
		return new EditorChrome.Rect(x, tabRowY(), EditorChrome.tabWidth(font, tab.label()), TAB_HEIGHT);
	}

	private EditorChrome.Rect groupTabRect(GroupTab tab) {
		int x = layout.rightGridX();
		for (GroupTab value : GroupTab.values()) {
			int width = EditorChrome.tabWidth(font, value.label());
			if (value == tab) {
				return new EditorChrome.Rect(x, tabRowY(), width, TAB_HEIGHT);
			}
			x += width + TAB_GAP;
		}
		return new EditorChrome.Rect(x, tabRowY(), EditorChrome.tabWidth(font, tab.label()), TAB_HEIGHT);
	}

	private EditorChrome.Rect hideUsedChipRect() {
		int width = EditorChrome.chipWidth(font, "Hide Used");
		int x = layout.leftGridX() + layout.leftGridWidth() - width;
		return new EditorChrome.Rect(x, controlRowY(), width, CHIP_HEIGHT);
	}

	private EditorChrome.Rect searchFieldRect() {
		int x = layout.leftGridX();
		EditorChrome.Rect chipRect = hideUsedChipRect();
		int width = Math.max(92, chipRect.x() - x - 8);
		return new EditorChrome.Rect(x, controlRowY(), width, SEARCH_HEIGHT);
	}

	private EditorChrome.Rect rulesViewportRect() {
		return new EditorChrome.Rect(layout.rightGridX(), layout.gridTop(), layout.rightGridWidth(), layout.gridHeight());
	}

	private int maxRulesScroll(RulesContent rulesContent) {
		return Math.max(0, rulesContent.contentHeight() - rulesViewportRect().height());
	}

	private void clampRulesScroll(RulesContent rulesContent) {
		rulesScrollOffset = ScrollbarHelper.clamp(rulesScrollOffset, 0, maxRulesScroll(rulesContent));
	}

	private boolean handleRulesClick(double mouseX, double mouseY, int button) {
		if (button != 0) return false;

		EditorChrome.Rect viewport = rulesViewportRect();
		if (mouseY >= viewport.y() && mouseY < viewport.bottom()
			&& mouseX >= layout.rightScrollbarX() && mouseX < layout.rightScrollbarX() + ScrollbarHelper.WIDTH) {
			RulesContent rulesContent = buildRulesContent();
			clampRulesScroll(rulesContent);
			isDraggingRulesScrollbar = true;
			rulesScrollbarDragStartMouseY = mouseY;
			rulesScrollOffset = ScrollbarHelper.trackClickToOffset(mouseY, viewport.y(), viewport.height(),
				rulesContent.contentHeight(), viewport.height(), rulesScrollOffset);
			rulesScrollbarDragStartOffset = rulesScrollOffset;
			return true;
		}

		return false;
	}

	private boolean handleRulesDrag(double mouseY) {
		if (!isDraggingRulesScrollbar) return false;

		EditorChrome.Rect viewport = rulesViewportRect();
		RulesContent rulesContent = buildRulesContent();
		rulesScrollOffset = ScrollbarHelper.dragToOffset(mouseY, rulesScrollbarDragStartMouseY, rulesScrollbarDragStartOffset,
			rulesContent.contentHeight(), viewport.height(), viewport.height());
		return true;
	}

	private boolean handleRulesScroll(double mouseX, double mouseY, double deltaY) {
		if (!layout.isInsideRight(mouseX, mouseY)) return false;

		RulesContent rulesContent = buildRulesContent();
		int maxScroll = maxRulesScroll(rulesContent);
		if (maxScroll <= 0) {
			rulesScrollOffset = 0;
			return true;
		}

		rulesScrollOffset = ScrollbarHelper.clamp(rulesScrollOffset - (int) Math.signum(deltaY) * (font.lineHeight + 4),
			0, maxScroll);
		return true;
	}

	private void clearRightHover() {
		rightPanel.hoveredItem = -1;
	}

	private void onGroupChanged() {
		rightPanel.rebuild();
		leftPanel.clampScroll(layout);
		rightPanel.clampScroll(layout);
		updateSaveButtonState();
		if (layout != null && font != null && activeGroupTab == GroupTab.RULES) clampRulesScroll(buildRulesContent());
	}

	private void updateSaveButtonState() {
		if (btnSave != null) {
			btnSave.active = state.canSave();
		}
	}

	private static boolean isMouseOverWidget(Button button, double mouseX, double mouseY) {
		return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
			&& mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
	}
}
