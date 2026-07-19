package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.filter.Filters;

import com.starskyxiii.collapsible_groups.client.editor.EditorFluidIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeAccess;
import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeServices;
import com.starskyxiii.collapsible_groups.client.manager.model.GroupUiState;
import com.starskyxiii.collapsible_groups.client.manager.GroupManagerParent;
import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import com.starskyxiii.collapsible_groups.client.editor.model.EditorContentFilter;
import com.starskyxiii.collapsible_groups.client.preview.model.EditorPreviewSummary;
import com.starskyxiii.collapsible_groups.client.editor.model.EditorShellMode;
import com.starskyxiii.collapsible_groups.client.widget.EditorChrome;
import com.starskyxiii.collapsible_groups.client.widget.EditorLayout;
import com.starskyxiii.collapsible_groups.client.widget.ConfirmDialog;
import com.starskyxiii.collapsible_groups.client.widget.EditorShellLayout;
import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.client.manager.model.SavedGroupContext;
import com.starskyxiii.collapsible_groups.group.GroupThemeColors;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;

public class GroupEditorScreen extends Screen {
	private static final int ERROR_TEXT_COLOR = 0xFFFF6B5F;
	private static final int READY_TEXT_COLOR = 0xFF7FB95A;
	private static final int UNRESOLVED_TEXT_COLOR = 0xFFE88A82;
	private static final int HEADER_PREVIEW_SIZE = 22;
	private static final int PREVIEW_ICON_SIZE = 16;
	private static final int SEGMENT_OVERLAP = 1;
	private static final int SETTINGS_ROW_GAP = 3;
	private static final int SETTINGS_SCROLLBAR_WIDTH = 6;
	private static final int COLOR_MODAL_WIDTH = 266;
	private static final int COLOR_MODAL_HEIGHT = 196;
	private static final int COLOR_DYE_SIZE = 14;
	private static final int COLOR_DYE_GAP = 3;
	private static final int COLOR_SLIDER_WIDTH = 132;
	private static final int[] DYE_COLORS = {
		0xFFFFFFFF, 0xFFFFD83D, 0xFFFFA43B, 0xFFFF5A7A,
		0xFFC36BFF, 0xFF6F7DFF, 0xFF45B8FF, 0xFF4DD4AC,
		0xFF5ABF4A, 0xFF9AD64D, 0xFF8B6A4A, 0xFFB6B6B6,
		0xFF7A7F85, 0xFF3B3F45, 0xFF5C7CFA, 0xFF1E1E1F
	};

	private final GroupManagerParent parent;
	private final SavedGroupContext.SaveKind saveKind;
	private final GroupEditorState state;
	private final EditorItemSearchSession itemSearchSession = new EditorItemSearchSession();
	private List<ItemStack> editorItemUniverse = List.of();
	private final Component nameFieldHint = Component.translatable(ModTranslationKeys.EDITOR_NAME_HINT);
	private final Component searchFieldHint = Component.translatable(ModTranslationKeys.EDITOR_SEARCH_HINT);

	private EditorShellLayout shell;
	private EditorLayout layout;
	private EditorLeftPanel leftPanel;
	private EditorRightPanel rightPanel;
	private EditorRulesPanel rulesPanel;
	private EditorSettingsPanel settingsPanel;
	private EditBox nameField;
	private EditBox searchField;

	private EditorShellMode activeMode = EditorShellMode.CONTENTS;
	private EditorContentFilter activeContentFilter = EditorContentFilter.ITEMS;
	private boolean hasGenericIngredients = false;
	private boolean editorDataLoading = true;
	private int editorLoadGeneration = 0;
	private boolean dirty = false;
	private boolean saveButtonHeld = false;
	private boolean cancelButtonHeld = false;
	private boolean disableSourceAfterCopy = false;
	private boolean disableSourceCheckboxHeld = false;
	private EditorShellMode heldMode = null;
	private EditorContentFilter heldContentFilter = null;
	private boolean hideUsedHeld = false;
	private boolean discardDialogOpen = false;
	private boolean settingsPreviewExpanded = true;
	private int settingsPreviewPage = 0;
	private @Nullable EditorRuntimeAccess.PreviewLayout settingsPreviewLayout = null;
	// settings-mode preview hover tooltip, recorded during the
	// preview render and drawn once at render() end (scissor closed, all panels up).
	private @Nullable List<Component> previewHoverLines = null;
	private Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> previewHoverVisual = Optional.empty();
	private @Nullable ItemStack previewHoverItem = null;
	private int previewHoverX = 0;
	private int previewHoverY = 0;

	public GroupEditorScreen(GroupManagerParent parent, GroupDefinition existing) {
		this(parent, existing, false, null);
	}

	public GroupEditorScreen(GroupManagerParent parent, GroupDefinition existing, boolean copyDraft) {
		this(parent, existing, copyDraft, null);
	}

	public GroupEditorScreen(
		GroupManagerParent parent,
		GroupDefinition existing,
		boolean copyDraft,
		@Nullable String sourceGroupId
	) {
		super(screenTitle(existing, copyDraft, sourceGroupId));
		this.parent = parent;
		this.saveKind = copyDraft
			? SavedGroupContext.SaveKind.COPIED
			: existing == null ? SavedGroupContext.SaveKind.CREATED : SavedGroupContext.SaveKind.UPDATED;
		this.state = new GroupEditorState(existing, copyDraft, sourceGroupId);
		this.disableSourceAfterCopy = this.state.isCopyDraft() && this.state.sourceGroupId() != null;
	}

	private static Component screenTitle(GroupDefinition existing, boolean copyDraft, @Nullable String sourceGroupId) {
		if (copyDraft) {
			String sourceName = sourceDisplayName(sourceGroupId);
			return sourceName == null
				? Component.translatable(ModTranslationKeys.SCREEN_COPY_GROUP)
				: Component.translatable(ModTranslationKeys.SCREEN_COPY_GROUP_NAMED, sourceName);
		}
		return Component.translatable(existing == null
			? ModTranslationKeys.SCREEN_NEW_GROUP
			: ModTranslationKeys.SCREEN_EDIT_GROUP);
	}

	@Nullable
	private static String sourceDisplayName(@Nullable String sourceGroupId) {
		if (sourceGroupId == null || sourceGroupId.isBlank()) return null;
		return EditorRuntimeServices.get().findGroup(sourceGroupId)
			.map(group -> group.displayName().resolveClientDisplayText())
			.filter(name -> !name.isBlank())
			.orElse(null);
	}

	@Override
	protected void init() {
		int loadGeneration = ++editorLoadGeneration;
		shell = computeShellLayout();
		layout = shell.panelLayout();
		leftPanel = new EditorLeftPanel(state, this::onGroupChanged, itemSearchSession);
		rightPanel = new EditorRightPanel(state, this::onGroupChanged);
		rulesPanel = new EditorRulesPanel(state, font, this::onGroupChanged, this::editorItems, itemSearchSession);
		settingsPanel = new EditorSettingsPanel(state, font, this::onGroupChanged,
			this::settingsPreviewEntries);
		settingsPanel.setDirtyGate(() -> dirty, value -> dirty = value);

		editorDataLoading = true;
		leftPanel.init(List.of(), List.of(), List.of());
		leftPanel.setHideUsed(GroupUiState.hideUsed());
		initFields();
		initRulesPanel();
		initSettingsPanel();
		applyContentFilter(activeContentFilter);
		leftPanel.clampScroll(layout);
		rightPanel.clampScroll(previewLayout());
		updateWidgetVisibility();
		GroupDefinition entryDefinition = state.buildPreviewDefinition();
		EditorRuntimeServices.get().prepareEditorEntry(entryDefinition).whenComplete((ignored, error) ->
			minecraft.execute(() -> finishEditorEntryLoad(loadGeneration, error)));
	}

	private void finishEditorEntryLoad(int loadGeneration, Throwable error) {
		if (loadGeneration != editorLoadGeneration || minecraft.screen != this) return;
		if (error != null) {
			editorDataLoading = false;
			return;
		}
		List<EditorFluidIngredientView> allFluids = EditorRuntimeServices.get().allFluids(
			"EditorLeftPanel.buildFluidViews");
		List<EditorGenericIngredientView> allGenericIngredients = EditorRuntimeServices.get().allGenericIngredients(
			"EditorLeftPanel.buildGenericViews");
		hasGenericIngredients = !allGenericIngredients.isEmpty();
		leftPanel.init(editorItems(), allFluids, allGenericIngredients);
		rightPanel.rebuildFromPreparedCache();
		applyContentFilter(activeContentFilter);
		leftPanel.clampScroll(layout);
		rightPanel.clampScroll(previewLayout());
		editorDataLoading = false;
	}

	private List<ItemStack> editorItems() {
		if (editorItemUniverse.isEmpty()) editorItemUniverse = EditorRuntimeServices.get().allItems();
		return editorItemUniverse;
	}

	private EditorShellLayout computeShellLayout() {
		boolean showDisableSourceOption = showDisableSourceOption();
		int disableSourceLabelWidth = showDisableSourceOption ? font.width(disableSourceLabel().getString()) : 0;
		return EditorShellLayout.compute(this.width, this.height, showDisableSourceOption, disableSourceLabelWidth);
	}

	private boolean showDisableSourceOption() {
		return state.isCopyDraft() && state.sourceGroupId() != null;
	}

	private Component disableSourceLabel() {
		return Component.translatable(ModTranslationKeys.EDITOR_COPY_DISABLE_SOURCE);
	}

	private void initFields() {
		nameField = new EditBox(font, 0, 0, 1, font.lineHeight,
			Component.translatable(ModTranslationKeys.EDITOR_NAME_LABEL));
		nameField.setMaxLength(64);
		nameField.setBordered(false);
		nameField.setTextColor(UiPalette.TEXT_PRIMARY);
		nameField.setTextColorUneditable(UiPalette.TEXT_DISABLED);
		nameField.setHint(nameFieldHint);
		positionField(nameField, shell.nameField());
		nameField.setValue(state.editName);
		nameField.setResponder(value -> {
			state.setEditName(value);
			nameField.setTextColor(UiPalette.TEXT_PRIMARY);
			markDirty();
		});
		addRenderableWidget(nameField);

		searchField = new EditBox(font, 0, 0, 1, font.lineHeight,
			Component.translatable(ModTranslationKeys.EDITOR_SEARCH_LABEL));
		searchField.setMaxLength(128);
		searchField.setBordered(false);
		searchField.setTextColor(UiPalette.TEXT_PRIMARY);
		searchField.setTextColorUneditable(UiPalette.TEXT_DISABLED);
		searchField.setHint(searchFieldHint);
		searchField.setResponder(value -> {
			leftPanel.rebuildFilter(value);
			leftPanel.clampScroll(layout);
		});
		positionField(searchField, shell.searchField());
		addRenderableWidget(searchField);
	}

	private void initRulesPanel() {
		EditorShellLayout.Rect panel = shell.editorPanel();
		int x = panel.x() + EditorShellLayout.PANEL_INSET;
		int y = shell.contentTitle().bottom() + 6;
		int w = Math.max(80, panel.width() - EditorShellLayout.PANEL_INSET * 2);
		int h = Math.max(40, panel.bottom() - y - EditorShellLayout.PANEL_INSET);
		rulesPanel.init(x, y, w, h);
		if (activeMode == EditorShellMode.RULES) rulesPanel.onActivate();
	}

	private void initSettingsPanel() {
		EditorShellLayout.Rect panel = shell.editorPanel();
		EditorShellLayout.Rect content = settingsContentRect();
		settingsPanel.init(
			new EditorChrome.Rect(panel.x(), panel.y(), panel.width(), panel.height()),
			shell.contentTitle().bottom(),
			new EditorChrome.Rect(content.x(), content.y(), content.width(), content.height()));
		if (activeMode == EditorShellMode.LOOK) settingsPanel.onActivate();
	}

	private void positionField(EditBox field, EditorShellLayout.Rect rect) {
		field.setPosition(rect.x() + 5, UiSkinRenderer.textFieldTextY(font, rect.y(), rect.height()) + 1);
		field.setWidth(Math.max(1, rect.width() - 10));
	}

	@Override
	public void onClose() {
		if (discardDialogOpen) {
			discardDialogOpen = false;
			return;
		}
		requestClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
		g.fill(0, 0, this.width, this.height, UiPalette.SCREEN_SCRIM);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
		renderBackground(g, mouseX, mouseY, partialTicks);
		UiSkinRenderer.drawScreenBars(g, this.width, this.height,
			EditorShellLayout.HEADER_HEIGHT, EditorShellLayout.FOOTER_HEIGHT);
		previewHoverLines = null;
		previewHoverVisual = Optional.empty();
		previewHoverItem = null;
		renderHeader(g, mouseX, mouseY);
		renderShellPanels(g, mouseX, mouseY, partialTicks);
		renderFooter(g);

		for (var child : this.children()) {
			if (child instanceof Renderable renderable) {
				renderable.render(g, mouseX, mouseY, partialTicks);
			}
		}

		if (discardDialogOpen) {
			renderDiscardDialog(g, mouseX, mouseY);
			return;
		}
		if (activeMode == EditorShellMode.LOOK && settingsPanel.isModalOpen()) {
			settingsPanel.renderModals(g, mouseX, mouseY);
			return;
		}
		if (activeMode == EditorShellMode.RULES && rulesPanel.isModalOpen()) {
			rulesPanel.renderModals(g, this.width, this.height, mouseX, mouseY);
			return;
		}
		if (activeMode == EditorShellMode.LOOK) {
			Component settingsTooltip = settingsPanel.hoverTooltip();
			if (settingsTooltip != null) {
				g.renderComponentTooltip(font, List.of(settingsTooltip), mouseX, mouseY);
				return;
			}
			// preview hover tooltip (suppressed above when a
			// settings modal or the discard dialog is open, both of which return early).
			if (previewHoverItem != null) {
				g.renderTooltip(font, previewHoverItem, previewHoverX, previewHoverY);
				return;
			}
			if (previewHoverLines != null) {
				g.renderTooltip(font, previewHoverLines, previewHoverVisual, previewHoverX, previewHoverY);
				return;
			}
		}

		boolean disableSourceTooltip = renderDisableSourceTooltip(g, mouseX, mouseY);
		boolean hideUsedHover = isHideUsedHover(mouseX, mouseY);
		if (!disableSourceTooltip && hideUsedHover) {
			g.renderComponentTooltip(font,
				List.of(Component.translatable(ModTranslationKeys.EDITOR_CHIP_HIDE_USED)), mouseX, mouseY);
		} else if (!disableSourceTooltip && activeMode == EditorShellMode.CONTENTS) {
			GroupEditorTooltipHelper.render(g, mouseX, mouseY, leftPanel, rightPanel, state, font, shell,
				activeMode == EditorShellMode.CONTENTS && searchField != null && searchField.visible);
		} else if (!disableSourceTooltip
			&& (activeMode != EditorShellMode.RULES || !rulesPanel.isModalOpen())) {
			leftPanel.clearHover();
			GroupEditorTooltipHelper.render(g, mouseX, mouseY, leftPanel, rightPanel, state, font, shell,
				activeMode == EditorShellMode.CONTENTS && searchField != null && searchField.visible);
		}
		if (shell.saveButton().contains(mouseX, mouseY) && !canSaveNow()) {
			g.renderComponentTooltip(font, saveDisabledTooltip(), mouseX, mouseY);
		}
	}

	private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
		renderActionTitle(g, shell.actionTitle());
		renderHeaderButton(g, shell.saveButton(), Component.translatable(ModTranslationKeys.BUTTON_SAVE).getString(),
			canSaveNow(), saveButtonHeld, mouseX, mouseY);
		renderHeaderButton(g, shell.cancelButton(), Component.translatable(ModTranslationKeys.BUTTON_CANCEL).getString(),
			true, cancelButtonHeld, mouseX, mouseY);
		renderCompactHeaderPreview(g, shell.headerPreview());
		renderFieldChrome(g, shell.nameField(), nameField != null && nameField.isFocused(),
			shell.nameField().contains(mouseX, mouseY));
		renderDisableSourceOption(g, mouseX, mouseY);
		renderModeSegments(g, mouseX, mouseY);
	}

	private void renderActionTitle(GuiGraphics g, EditorShellLayout.Rect rect) {
		Component boldTitle = this.title.copy().withStyle(ChatFormatting.BOLD);
		FormattedCharSequence clipped = Language.getInstance().getVisualOrder(
			font.substrByWidth(boldTitle, Math.max(0, rect.width())));
		g.drawString(font, clipped, rect.x(), UiSkinRenderer.centeredTextY(font, rect.y(), rect.height()),
			UiPalette.TEXT_PRIMARY, false);
	}

	private void renderDisableSourceOption(GuiGraphics g, int mouseX, int mouseY) {
		EditorShellLayout.Rect checkbox = shell.disableSourceCheckbox();
		EditorShellLayout.Rect label = shell.disableSourceLabel();
		if (!showDisableSourceOption() || checkbox == null || label == null) return;

		boolean hovered = disableSourceOptionContains(mouseX, mouseY);
		g.fill(checkbox.x(), checkbox.y(), checkbox.right(), checkbox.bottom(), UiPalette.OUTLINE_DARK);
		g.fill(checkbox.x() + 2, checkbox.y() + 2, checkbox.right() - 2, checkbox.bottom() - 2,
			disableSourceAfterCopy ? UiPalette.OUTLINE_SELECTED : UiPalette.SURFACE_DARK);
		if (hovered) {
			UiSkinRenderer.drawOutline(g, checkbox.x(), checkbox.y(), checkbox.width(), checkbox.height(),
				UiPalette.OUTLINE_HOVER);
		}
		if (disableSourceAfterCopy) {
			renderCheckboxMark(g, checkbox);
		}

		String text = disableSourceLabel().getString();
		String clipped = font.plainSubstrByWidth(text, Math.max(0, label.width()));
		g.drawString(font, clipped, label.x(), UiSkinRenderer.centeredTextY(font, label.y(), label.height()),
			hovered ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_MUTED, false);
	}

	private boolean renderDisableSourceTooltip(GuiGraphics g, int mouseX, int mouseY) {
		EditorShellLayout.Rect label = shell.disableSourceLabel();
		if (!showDisableSourceOption() || label == null || !disableSourceOptionContains(mouseX, mouseY)) return false;
		Component text = disableSourceLabel();
		if (font.width(text.getString()) <= label.width()) return false;
		g.renderComponentTooltip(font, List.of(text), mouseX, mouseY);
		return true;
	}

	private void renderCheckboxMark(GuiGraphics g, EditorShellLayout.Rect checkbox) {
		int color = UiPalette.TEXT_SELECTED;
		int x = checkbox.x();
		int y = checkbox.y();
		g.fill(x + 3, y + 7, x + 5, y + 9, color);
		g.fill(x + 5, y + 9, x + 7, y + 11, color);
		g.fill(x + 7, y + 6, x + 9, y + 9, color);
		g.fill(x + 9, y + 4, x + 11, y + 7, color);
	}

	private void renderShellPanels(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
		UiSkinRenderer.drawPanel(g, shell.editorPanel().x(), shell.editorPanel().y(),
			shell.editorPanel().width(), shell.editorPanel().height());
		UiSkinRenderer.drawPanel(g, shell.previewPanel().x(), shell.previewPanel().y(),
			shell.previewPanel().width(), shell.previewPanel().height());

		if (activeMode == EditorShellMode.CONTENTS) {
			renderContentsPanel(g, mouseX, mouseY);
		} else if (activeMode == EditorShellMode.RULES) {
			renderRulesPanel(g, mouseX, mouseY, partialTicks);
		} else {
			renderSettingsPanel(g, mouseX, mouseY);
		}
		renderPreviewPanel(g, mouseX, mouseY);
		if (editorDataLoading) {
			Component loading = Component.translatable(ModTranslationKeys.EDITOR_LOADING);
			g.drawCenteredString(font, loading,
				(shell.editorPanel().x() + shell.previewPanel().right()) / 2,
				shell.editorPanel().y() + shell.editorPanel().height() / 2,
				UiPalette.TEXT_HINT);
		}
	}

	private void renderModeSegments(GuiGraphics g, int mouseX, int mouseY) {
		for (EditorShellMode mode : EditorShellMode.values()) {
			EditorShellLayout.Rect rect = modeSegmentRect(mode);
			boolean hovered = rect.contains(mouseX, mouseY);
			UiSkinRenderer.ButtonState state = buttonState(true, mode == activeMode, hovered, heldMode == mode);
			UiSkinRenderer.drawSegment(g, font, rect.x(), rect.y(), rect.width(), rect.height(),
				Component.translatable(mode.labelKey()).getString(), state);
		}
	}

	private void renderContentsPanel(GuiGraphics g, int mouseX, int mouseY) {
		renderContentFilterSegments(g, mouseX, mouseY);
		renderFieldChrome(g, shell.searchField(), searchField != null && searchField.isFocused(),
			shell.searchField().contains(mouseX, mouseY));
		renderHideUsedButton(g, mouseX, mouseY);

		renderSourceCount(g);
		leftPanel.render(g, mouseX, mouseY, layout);
		renderOreScrollbar(g, layout.leftScrollbarX(), layout.gridTop(), layout.gridHeight(),
			layout.leftRows(), leftPanel.totalRows(layout), leftPanel.scrollRow);
	}

	private void renderSourceCount(GuiGraphics g) {
		EditorShellLayout.Rect row = shell.contentCount();
		String summary = Component.translatable(ModTranslationKeys.ORE_EDITOR_SOURCE_COUNT_SUMMARY,
			leftPanel.entryCount(), leftPanel.totalEntryCount(), selectedContentCount()).getString();
		String clippedCount = font.plainSubstrByWidth(summary, Math.max(0, row.width()));
		g.drawString(font, clippedCount, row.right() - font.width(clippedCount), row.y(),
			UiPalette.TEXT_HINT, false);
	}

	private int selectedContentCount() {
		return switch (activeContentFilter) {
			case FLUIDS -> rightPanel.groupFluids().size();
			case OTHER_TYPES -> rightPanel.groupGeneric().size();
			case ITEMS -> rightPanel.groupItems().size();
		};
	}

	private void renderContentFilterSegments(GuiGraphics g, int mouseX, int mouseY) {
		for (EditorContentFilter filter : EditorContentFilter.values()) {
			EditorShellLayout.Rect rect = contentFilterRect(filter);
			boolean enabled = isContentFilterEnabled(filter);
			boolean hovered = enabled && rect.contains(mouseX, mouseY);
			UiSkinRenderer.ButtonState state = buttonState(enabled, filter == activeContentFilter, hovered,
				heldContentFilter == filter);
			UiSkinRenderer.drawSegment(g, font, rect.x(), rect.y(), rect.width(), rect.height(),
				Component.translatable(filter.labelKey()).getString(), state);
		}
	}

	private void renderHideUsedButton(GuiGraphics g, int mouseX, int mouseY) {
		EditorShellLayout.Rect rect = shell.hideUsedButton();
		boolean hovered = rect.contains(mouseX, mouseY);
		// boolean toggle uses a switch (matching the Manager enabled switch).
		// drawSwitch centers a fixed-width visual inside the (wider) hit rect.
		UiSkinRenderer.drawSwitch(g, rect.x(), rect.y(), rect.width(), rect.height(),
			leftPanel.isHideUsed(), true, hovered, hideUsedHeld);
	}

	private boolean isHideUsedHover(int mouseX, int mouseY) {
		return activeMode == EditorShellMode.CONTENTS && shell.hideUsedButton().contains(mouseX, mouseY);
	}

	private void renderRulesPanel(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
		EditorShellLayout.Rect title = shell.contentTitle();
		g.drawString(font, Component.translatable(ModTranslationKeys.EDITOR_TAB_RULES),
			title.x(), title.y(), UiPalette.TEXT_PRIMARY, false);
		rulesPanel.render(g, mouseX, mouseY, partialTicks);
	}

	private EditorShellLayout.Rect settingsContentRect() {
		EditorShellLayout.Rect panel = shell.editorPanel();
		EditorShellLayout.Rect title = shell.contentTitle();
		int x = title.x();
		int w = Math.max(1, panel.width() - EditorShellLayout.PANEL_INSET * 2);
		int y = title.bottom() + 6;
		int h = Math.max(1, panel.bottom() - y - EditorShellLayout.PANEL_INSET - 2);
		return new EditorShellLayout.Rect(x, y, w, h);
	}

	private void renderSettingsPanel(GuiGraphics g, int mouseX, int mouseY) {
		EditorShellLayout.Rect title = shell.contentTitle();
		g.drawString(font, Component.translatable(ModTranslationKeys.ORE_EDITOR_MODE_SETTINGS),
			title.x(), title.y(), UiPalette.TEXT_PRIMARY, false);
		settingsPanel.render(g, mouseX, mouseY);
	}

	private void renderPreviewPanel(GuiGraphics g, int mouseX, int mouseY) {
		if (activeMode == EditorShellMode.LOOK) {
			renderSettingsPreviewPanel(g, mouseX, mouseY);
			return;
		}
		EditorLayout previewLayout = previewLayout();
		EditorShellLayout.Rect title = shell.previewTitle();
		int x = title.x();
		int y = title.y();
		g.drawString(font, Component.translatable(ModTranslationKeys.ORE_EDITOR_PREVIEW_HEADER),
			x, y, UiPalette.TEXT_PRIMARY, false);
		renderFullHeaderPreview(g, shell.previewHeader());

		boolean blockPreviewHover = activeMode == EditorShellMode.RULES && rulesPanel.isModalOpen();
		int panelMouseX = blockPreviewHover ? Integer.MIN_VALUE : mouseX;
		int panelMouseY = blockPreviewHover ? Integer.MIN_VALUE : mouseY;
		if (previewEntryCount() == 0) {
			renderPreviewEmptyState(g);
		} else {
			rightPanel.render(g, panelMouseX, panelMouseY, previewLayout);
		}
		renderOreScrollbar(g, previewLayout.rightScrollbarX(), previewLayout.gridTop(), previewLayout.gridHeight(),
			previewLayout.rightRows(), rightPanel.totalRows(previewLayout), rightPanel.scrollRow);
	}

	private void renderSettingsPreviewPanel(GuiGraphics g, int mouseX, int mouseY) {
		EditorShellLayout.Rect title = shell.previewTitle();
		g.drawString(font, Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_PREVIEW_HEADER),
			title.x(), title.y(), UiPalette.TEXT_PRIMARY, false);
		Component hint = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_PREVIEW_HINT);
		var hintLines = font.split(hint, Math.max(1, title.width()));
		int hintY = title.bottom() + 2;
		int drawn = Math.min(2, hintLines.size());
		for (int i = 0; i < drawn; i++) {
			g.drawString(font, hintLines.get(i), title.x(), hintY + i * (font.lineHeight + 1),
				UiPalette.TEXT_HINT, false);
		}

		EditorShellLayout.Rect area = settingsPreviewAreaRect();
		List<EditorRuntimeAccess.PreviewEntry> entries = settingsPreviewEntries();
		settingsPreviewLayout = EditorRuntimeServices.get().renderPreview(g, sampleRect(area), settingsPreviewExpanded,
			settingsPreviewPage, state.appearanceDraft, settingsSampleHeaderIcons(entries), entries,
			font, sampleFallbacks());
		settingsPreviewPage = settingsPreviewLayout.page();
		recordSettingsPreviewHover(mouseX, mouseY);
	}

	/**
	 * record (not draw) the preview hover tooltip. The header
	 * cell mirrors the live {@code GroupHeaderElement.getTooltip} (name in the draft
	 * name color, count label, collapsed preview grid, expand/collapse hint); child
	 * cells surface the standard item tooltip. Drawn later in {@link #render}.
	 */
	private void recordSettingsPreviewHover(int mouseX, int mouseY) {
		if (settingsPreviewLayout == null) return;
		for (EditorRuntimeAccess.PreviewCell cell : settingsPreviewLayout.cells()) {
			if (!cell.rect().contains(mouseX, mouseY)) continue;
			previewHoverX = mouseX;
			previewHoverY = mouseY;
			if (cell.header()) {
				int nameColor = GroupThemeColors.nameColor(
					state.appearanceDraft.toTheme(), Services.CONFIG.groupNameColor());
				String name = state.editName == null || state.editName.isBlank()
					? this.title.getString() : state.editName;
				EditorRuntimeAccess.PreviewTooltip result = EditorRuntimeServices.get().previewTooltip(
					name, nameColor & 0x00FFFFFF,
					rightPanel.groupItems().size(), rightPanel.groupFluids().size(), rightPanel.groupGeneric().size(),
					settingsPreviewExpanded, settingsPreviewEntries());
				previewHoverLines = result.lines();
				previewHoverVisual = result.visual();
			} else {
				List<EditorRuntimeAccess.PreviewEntry> entries = settingsPreviewEntries();
				if (cell.itemIndex() >= 0 && cell.itemIndex() < entries.size()) {
					recordPreviewEntryTooltip(entries.get(cell.itemIndex()));
				}
			}
			return;
		}
	}

	/** Non-empty group items — same filter GroupSampleRenderer uses for child cells. */
	/** Live-aligned preview entries for the collapsed header preview grid. */
	private List<EditorRuntimeAccess.PreviewEntry> settingsPreviewEntries() {
		List<EditorRuntimeAccess.PreviewEntry> entries = new java.util.ArrayList<>();
		for (ItemStack stack : rightPanel.groupItems()) entries.add(EditorRuntimeAccess.PreviewEntry.item(stack));
		for (var fluid : rightPanel.groupFluids()) entries.add(EditorRuntimeAccess.PreviewEntry.fluid(fluid));
		for (var generic : rightPanel.groupGeneric()) {
			entries.add(EditorRuntimeAccess.PreviewEntry.generic(generic));
		}
		return entries;
	}

	private void recordPreviewEntryTooltip(EditorRuntimeAccess.PreviewEntry entry) {
		switch (entry.kind()) {
			case ITEM -> previewHoverItem = (ItemStack) entry.value();
			case FLUID -> previewHoverLines = EditorRuntimeServices.get()
				.fluidTooltip((EditorFluidIngredientView) entry.value());
			case GENERIC -> previewHoverLines = EditorRuntimeServices.get()
				.genericTooltip((EditorGenericIngredientView) entry.value());
		}
	}

	private EditorRuntimeAccess.PreviewRect sampleRect(EditorShellLayout.Rect area) {
		return new EditorRuntimeAccess.PreviewRect(area.x(), area.y(), area.width(), area.height());
	}

	private EditorRuntimeAccess.PreviewFallbacks sampleFallbacks() {
		return new EditorRuntimeAccess.PreviewFallbacks(
			Services.CONFIG.groupNameColor(),
			Services.CONFIG.collapsedGroupBackgroundColor(),
			Services.CONFIG.expandedGroupBackgroundColor(),
			Services.CONFIG.expandedGroupBackgroundColor(),
			Services.CONFIG.expandedGroupBorderColor());
	}

	/**
	 * Header stack icons: honour the draft icon selection (front, back) when
	 * present, else fall back to the group's own items (the live JEI source).
	 */
	private List<EditorRuntimeAccess.PreviewEntry> settingsSampleHeaderIcons(
		List<EditorRuntimeAccess.PreviewEntry> fallbackEntries
	) {
		return EditorRuntimeServices.get().resolveHeaderIcons(
			state.appearanceDraft.toIconIds(), fallbackEntries);
	}

	private void renderPreviewEmptyState(GuiGraphics g) {
		EditorShellLayout.Rect body = shell.previewBody();
		String text = font.plainSubstrByWidth(
			Component.translatable(ModTranslationKeys.ORE_EDITOR_PREVIEW_EMPTY).getString(),
			Math.max(0, body.width() - 12));
		int x = body.x() + Math.max(0, (body.width() - font.width(text)) / 2);
		int y = body.y() + Math.max(0, (body.height() - font.lineHeight) / 2);
		g.drawString(font, text, x, y, UiPalette.TEXT_HINT, false);
	}

	private void renderOreScrollbar(GuiGraphics g, int x, int y, int height, int visibleRows, int totalRows, int rowOffset) {
		UiSkinRenderer.drawMiniScrollbar(g, x, y, height, visibleRows, totalRows, rowOffset);
	}

	/**
	 * Shared header-preview box: background follows the live
	 * appearance draft (so new/edited colors reflect immediately), icons honour the
	 * draft's front/back selection when present, else fall back to the live group
	 * items via {@link #renderStackedPreviewIcons} (keeps fluid/generic groups from
	 * degrading to an empty slot). Used by both the compact and full previews.
	 */
	private void renderHeaderPreviewBox(GuiGraphics g, int x, int y) {
		int background = GroupThemeColors.collapsedHeaderBackground(
			state.appearanceDraft.toTheme(), Services.CONFIG.collapsedGroupBackgroundColor());
		g.fill(x, y, x + HEADER_PREVIEW_SIZE, y + HEADER_PREVIEW_SIZE, background);
		UiSkinRenderer.drawOutline(g, x, y, HEADER_PREVIEW_SIZE, HEADER_PREVIEW_SIZE, UiPalette.OUTLINE_DARK);
		renderStackedEditorIcons(g, settingsSampleHeaderIcons(settingsPreviewEntries()), x, y);
	}

	private void renderStackedEditorIcons(GuiGraphics g, List<EditorRuntimeAccess.PreviewEntry> icons, int x, int y) {
		if (icons.isEmpty()) return;
		g.pose().pushPose();
		g.pose().translate(0, 0, 120);
		if (icons.size() > 1) {
			renderPreviewEntry(g, icons.get(1), x + 4, y + 2);
			g.pose().translate(0, 0, 8);
			renderPreviewEntry(g, icons.get(0), x + 2, y + 4);
		} else {
			int inset = (HEADER_PREVIEW_SIZE - PREVIEW_ICON_SIZE) / 2;
			renderPreviewEntry(g, icons.get(0), x + inset, y + inset);
		}
		g.pose().popPose();
	}

	private void renderCompactHeaderPreview(GuiGraphics g, EditorShellLayout.Rect row) {
		int x = row.x();
		int y = row.y() + Math.max(0, (row.height() - HEADER_PREVIEW_SIZE) / 2);
		renderHeaderPreviewBox(g, x, y);
	}

	private void renderFullHeaderPreview(GuiGraphics g, EditorShellLayout.Rect row) {
		int x = row.x();
		int y = row.y() + Math.max(0, (row.height() - HEADER_PREVIEW_SIZE) / 2);
		renderHeaderPreviewBox(g, x, y);
		int textX = x + HEADER_PREVIEW_SIZE + 6;
		int maxWidth = Math.max(0, row.right() - textX);
		int textBlockHeight = font.lineHeight * 2 + 2;
		int textY = y + Math.max(0, (HEADER_PREVIEW_SIZE - textBlockHeight) / 2);
		String name = state.editName == null || state.editName.isBlank() ? this.title.getString() : state.editName;
		String clipped = font.plainSubstrByWidth(name, maxWidth);
		g.drawString(font, clipped, textX, textY, UiPalette.TEXT_PRIMARY, false);
		String summary = font.plainSubstrByWidth(fullPreviewSummary(), maxWidth);
		g.drawString(font, summary, textX, textY + font.lineHeight + 2, UiPalette.TEXT_MUTED, false);
	}

	private String fullPreviewSummary() {
		EditorPreviewSummary summary = EditorPreviewSummary.of(
			rightPanel.groupItems().size(),
			rightPanel.groupFluids().size(),
			rightPanel.groupGeneric().size()
		);
		return Component.translatable(summary.labelKey(), summary.labelArgs()).getString();
	}

	private void renderStackedPreviewIcons(GuiGraphics g, int x, int y) {
		int count = previewEntryCount();
		if (count <= 0) return;
		g.pose().pushPose();
		g.pose().translate(0, 0, 120);
		if (count > 1) {
			renderPreviewEntry(g, 1, x + 4, y + 2);
			g.pose().translate(0, 0, 8);
			renderPreviewEntry(g, 0, x + 2, y + 4);
		} else {
			int inset = (HEADER_PREVIEW_SIZE - PREVIEW_ICON_SIZE) / 2;
			renderPreviewEntry(g, 0, x + inset, y + inset);
		}
		g.pose().popPose();
	}

	/**
	 * Settings-mode preview area: start just below the "JEI Preview" title
	 * and its operation hint, not the Contents/Rules 22px stacked-icon header the
	 * shared {@code previewBody} bakes in — that offset left ~35px of dead space.
	 */
	private EditorShellLayout.Rect settingsPreviewAreaRect() {
		EditorShellLayout.Rect panel = shell.previewPanel();
		EditorShellLayout.Rect title = shell.previewTitle();
		int x = title.x();
		// the preview area starts below however many hint lines actually
		// render (capped at 2), matching renderSettingsPreviewPanel line count so
		// the two never drift.
		int hintLines = Math.min(2, font.split(
			Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_PREVIEW_HINT), Math.max(1, title.width())).size());
		int top = title.bottom() + 2 + Math.max(1, hintLines) * (font.lineHeight + 1) - 1 + 6;
		int width = Math.max(18, title.width());
		int height = Math.max(18, panel.bottom() - top - EditorShellLayout.PANEL_INSET);
		return new EditorShellLayout.Rect(x, top, width, height);
	}

	private int previewEntryCount() {
		return rightPanel.groupItems().size() + rightPanel.groupFluids().size() + rightPanel.groupGeneric().size();
	}

	private void renderPreviewEntry(GuiGraphics g, int index, int x, int y) {
		int itemCount = rightPanel.groupItems().size();
		if (index < itemCount) {
			g.renderItem(rightPanel.groupItems().get(index), x, y);
			return;
		}
		index -= itemCount;
		int fluidCount = rightPanel.groupFluids().size();
		if (index < fluidCount) {
			IngredientCellRenderer.renderFluid(g, rightPanel.groupFluids().get(index), x, y);
			return;
		}
		index -= fluidCount;
		if (index < rightPanel.groupGeneric().size()) {
			IngredientCellRenderer.renderGeneric(g, rightPanel.groupGeneric().get(index), x, y);
		}
	}

	private void renderPreviewEntry(GuiGraphics g, EditorRuntimeAccess.PreviewEntry entry, int x, int y) {
		switch (entry.kind()) {
			case ITEM -> g.renderItem((ItemStack) entry.value(), x, y);
			case FLUID -> IngredientCellRenderer.renderFluid(
				g, (EditorFluidIngredientView) entry.value(), x, y);
			case GENERIC -> IngredientCellRenderer.renderGeneric(
				g, (EditorGenericIngredientView) entry.value(), x, y);
		}
	}

	private void renderFooter(GuiGraphics g) {
		int y = shell.footerStatus().y() + UiSkinRenderer.centeredTextY(font, 0, shell.footerStatus().height());
		Component status = footerStatus();
		int unresolvedCount = state.unresolvedRuleCount();
		int color = !state.canSave() ? ERROR_TEXT_COLOR
			: unresolvedCount > 0 ? UNRESOLVED_TEXT_COLOR
			: dirty ? READY_TEXT_COLOR : UiPalette.TEXT_HINT;
		String clipped = font.plainSubstrByWidth(status.getString(), Math.max(0, shell.footerStatus().width()));
		g.drawString(font, clipped, shell.footerStatus().x(), y, color, false);

		String hint = Component.translatable(ModTranslationKeys.ORE_EDITOR_FOOTER_HINT).getString();
		int hintWidth = Math.max(0, Math.min(shell.footerHint().width(),
			shell.footerHint().right() - shell.footerStatus().right() - 8));
		String clippedHint = font.plainSubstrByWidth(hint, hintWidth);
		g.drawString(font, clippedHint, shell.footerHint().right() - font.width(clippedHint), y,
			UiPalette.TEXT_HINT, false);
	}

	private Component footerStatus() {
		if (!state.canSave()) {
			return Component.translatable(ModTranslationKeys.ORE_EDITOR_STATUS_SAVE_BLOCKED, saveDisabledReason().getString());
		}
		int unresolved = state.unresolvedRuleCount();
		if (unresolved > 0) {
			return Component.translatable(ModTranslationKeys.ORE_EDITOR_STATUS_UNRESOLVED, unresolved);
		}
		if (hasPendingSave()) return Component.translatable(ModTranslationKeys.ORE_EDITOR_STATUS_READY);
		return Component.translatable(ModTranslationKeys.ORE_EDITOR_STATUS_CLEAN);
	}

	private void renderHeaderButton(GuiGraphics g, EditorShellLayout.Rect rect, String label, boolean active,
	                                boolean held, int mouseX, int mouseY) {
		boolean hovered = rect.contains(mouseX, mouseY);
		UiSkinRenderer.ButtonState state = !active
			? UiSkinRenderer.ButtonState.DISABLED
			: held && hovered ? UiSkinRenderer.ButtonState.PRESSED
			: hovered ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL;
		UiSkinRenderer.drawButton(g, font, rect.x(), rect.y(), rect.width(), rect.height(), label, state);
	}

	private void renderDiscardDialog(GuiGraphics g, int mouseX, int mouseY) {
		ConfirmDialog.render(g, font, this.width, this.height,
			Component.translatable(ModTranslationKeys.ORE_EDITOR_DISCARD_DIALOG_TITLE),
			List.of(Component.translatable(ModTranslationKeys.ORE_EDITOR_DISCARD_DIALOG_BODY)),
			Component.translatable(ModTranslationKeys.ORE_EDITOR_DISCARD_DIALOG_CONFIRM),
			Component.translatable(ModTranslationKeys.ORE_EDITOR_DISCARD_DIALOG_CANCEL),
			mouseX, mouseY);
	}

	private UiSkinRenderer.ButtonState buttonState(boolean active, boolean selected, boolean hovered, boolean held) {
		if (!active) return UiSkinRenderer.ButtonState.DISABLED;
		if (selected) {
			if (held && hovered) return UiSkinRenderer.ButtonState.SELECTED_PRESSED;
			return hovered ? UiSkinRenderer.ButtonState.SELECTED_HOVERED : UiSkinRenderer.ButtonState.SELECTED;
		}
		if (held && hovered) return UiSkinRenderer.ButtonState.PRESSED;
		return hovered ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL;
	}

	private void renderFieldChrome(GuiGraphics g, EditorShellLayout.Rect rect, boolean focused, boolean hovered) {
		int outline = focused ? UiPalette.OUTLINE_SELECTED : hovered ? UiPalette.OUTLINE_HOVER : UiPalette.OUTLINE_DARK;
		g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiPalette.SURFACE_DARK);
		g.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1, UiPalette.SURFACE);
		UiSkinRenderer.drawOutline(g, rect.x(), rect.y(), rect.width(), rect.height(), outline);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (discardDialogOpen) {
			handleDiscardDialogClick(mouseX, mouseY, button);
			return true;
		}
		if (activeMode == EditorShellMode.LOOK && settingsPanel.isModalOpen()) {
			settingsPanel.mouseClicked(mouseX, mouseY, button);
			return true;
		}
		if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
		if (activeMode == EditorShellMode.RULES && rulesPanel.isModalOpen()) {
			rulesPanel.mouseClicked(mouseX, mouseY, button);
			return true;
		}
		if (handleNameFieldClick(mouseX, mouseY, button)) return true;
		if (handleSearchFieldClick(mouseX, mouseY, button)) return true;
		if (handleHeaderButtonClick(mouseX, mouseY)) {
			blurEditorFields();
			return true;
		}
		blurEditorFields();
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		if (handleModeClick(mouseX, mouseY)) return true;
		if (activeMode == EditorShellMode.CONTENTS && handleContentsChromeClick(mouseX, mouseY)) return true;

		if (activeMode == EditorShellMode.CONTENTS) {
			if (leftPanel.mouseClicked(mouseX, mouseY, button, layout)) return true;
			return rightPanel.mouseClicked(mouseX, mouseY, button, previewLayout(), leftPanel.allItems());
		}
		if (activeMode == EditorShellMode.RULES) {
			return rulesPanel.mouseClicked(mouseX, mouseY, button);
		}
		if (activeMode == EditorShellMode.LOOK) {
			if (settingsPanel.mouseClicked(mouseX, mouseY, button)) return true;
			return handleSettingsPreviewClick(mouseX, mouseY);
		}
		return false;
	}

	private boolean handleNameFieldClick(double mouseX, double mouseY, int button) {
		if (nameField == null || !shell.nameField().contains(mouseX, mouseY)) return false;
		if (searchField != null) searchField.setFocused(false);
		if (activeMode == EditorShellMode.RULES) rulesPanel.clearFocus();
		setFocused(nameField);
		nameField.setFocused(true);
		nameField.setTextColor(UiPalette.TEXT_PRIMARY);
		nameField.mouseClicked(mouseX, mouseY, button);
		return true;
	}

	private boolean handleSearchFieldClick(double mouseX, double mouseY, int button) {
		if (searchField == null || !searchField.visible || !shell.searchField().contains(mouseX, mouseY)) return false;
		if (nameField != null) nameField.setFocused(false);
		if (activeMode == EditorShellMode.RULES) rulesPanel.clearFocus();
		setFocused(searchField);
		searchField.setFocused(true);
		searchField.mouseClicked(mouseX, mouseY, button);
		return true;
	}

	private void blurEditorFields() {
		if (nameField != null) nameField.setFocused(false);
		if (searchField != null) searchField.setFocused(false);
		if (rulesPanel != null) rulesPanel.clearFocus();
		if (settingsPanel != null) settingsPanel.clearFocus();
	}

	private boolean handleHeaderButtonClick(double mouseX, double mouseY) {
		if (shell.saveButton().contains(mouseX, mouseY)) {
			saveButtonHeld = canSaveNow();
			return true;
		}
		if (shell.cancelButton().contains(mouseX, mouseY)) {
			cancelButtonHeld = true;
			return true;
		}
		if (disableSourceOptionContains(mouseX, mouseY)) {
			disableSourceCheckboxHeld = true;
			return true;
		}
		return false;
	}

	private boolean handleModeClick(double mouseX, double mouseY) {
		for (EditorShellMode mode : EditorShellMode.values()) {
			EditorShellLayout.Rect rect = modeSegmentRect(mode);
			if (rect.contains(mouseX, mouseY)) {
				heldMode = mode;
				return true;
			}
		}
		return false;
	}

	private boolean handleContentsChromeClick(double mouseX, double mouseY) {
		for (EditorContentFilter filter : EditorContentFilter.values()) {
			EditorShellLayout.Rect rect = contentFilterRect(filter);
			if (rect.contains(mouseX, mouseY) && isContentFilterEnabled(filter)) {
				heldContentFilter = filter;
				return true;
			}
		}
		if (shell.hideUsedButton().contains(mouseX, mouseY)) {
			hideUsedHeld = true;
			return true;
		}
		return false;
	}

	private boolean handleSettingsPreviewClick(double mouseX, double mouseY) {
		EditorRuntimeAccess.PreviewLayout layout = settingsPreviewLayout != null
			? settingsPreviewLayout
			: EditorRuntimeServices.get().layoutPreview(sampleRect(settingsPreviewAreaRect()), settingsPreviewExpanded,
				previewEntryCount(), settingsPreviewPage);
		if (layout.previousPageButton() != null && layout.previousPageButton().contains(mouseX, mouseY)
			&& layout.canPageBackward()) {
			settingsPreviewPage = Math.max(0, layout.page() - 1);
			return true;
		}
		if (layout.nextPageButton() != null && layout.nextPageButton().contains(mouseX, mouseY)
			&& layout.canPageForward()) {
			settingsPreviewPage = layout.page() + 1;
			return true;
		}
		if (layout.headerCell().contains(mouseX, mouseY)) {
			settingsPreviewExpanded = !settingsPreviewExpanded;
			settingsPreviewPage = 0;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (discardDialogOpen) {
			clearHeldControls();
			return true;
		}
		if (activeMode == EditorShellMode.LOOK && settingsPanel.mouseReleased(mouseX, mouseY, button)) {
			clearHeldControls();
			return true;
		}
		if (button == 0) {
			if (saveButtonHeld && shell.saveButton().contains(mouseX, mouseY)) saveAndClose();
			if (cancelButtonHeld && shell.cancelButton().contains(mouseX, mouseY)) requestClose();
			if (disableSourceCheckboxHeld && disableSourceOptionContains(mouseX, mouseY)) {
				disableSourceAfterCopy = !disableSourceAfterCopy;
			}
			if (heldMode != null && modeSegmentContains(heldMode, mouseX, mouseY)) switchMode(heldMode);
			if (heldContentFilter != null && contentFilterContains(heldContentFilter, mouseX, mouseY)) {
				applyContentFilter(heldContentFilter);
			}
			if (hideUsedHeld && shell.hideUsedButton().contains(mouseX, mouseY)) toggleHideUsed();
			clearHeldControls();
		}
		if (activeMode == EditorShellMode.CONTENTS) {
			leftPanel.mouseReleased(button);
			rightPanel.mouseReleased(button);
		} else if (activeMode == EditorShellMode.RULES) {
			rulesPanel.mouseReleased(mouseX, mouseY, button);
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private boolean modeSegmentContains(EditorShellMode mode, double mouseX, double mouseY) {
		return modeSegmentRect(mode).contains(mouseX, mouseY);
	}

	private boolean contentFilterContains(EditorContentFilter filter, double mouseX, double mouseY) {
		return contentFilterRect(filter).contains(mouseX, mouseY);
	}

	private boolean disableSourceOptionContains(double mouseX, double mouseY) {
		if (!showDisableSourceOption()) return false;
		EditorShellLayout.Rect checkbox = shell.disableSourceCheckbox();
		EditorShellLayout.Rect label = shell.disableSourceLabel();
		return (checkbox != null && checkbox.contains(mouseX, mouseY))
			|| (label != null && label.contains(mouseX, mouseY));
	}

	private EditorShellLayout.Rect modeSegmentRect(EditorShellMode mode) {
		EditorShellMode[] modes = EditorShellMode.values();
		int width = Math.max(1, (shell.modeSegmentRow().width() + SEGMENT_OVERLAP * (modes.length - 1)) / modes.length);
		for (int i = 0; i < modes.length; i++) {
			EditorShellMode value = modes[i];
			int x = shell.modeSegmentRow().x() + i * (width - SEGMENT_OVERLAP);
			int w = i == modes.length - 1 ? shell.modeSegmentRow().right() - x : width;
			EditorShellLayout.Rect rect = new EditorShellLayout.Rect(x, shell.modeSegmentRow().y(), w,
				shell.modeSegmentRow().height());
			if (value == mode) return rect;
		}
		return new EditorShellLayout.Rect(shell.modeSegmentRow().x(), shell.modeSegmentRow().y(),
			1, shell.modeSegmentRow().height());
	}

	private EditorShellLayout.Rect contentFilterRect(EditorContentFilter filter) {
		EditorContentFilter[] filters = EditorContentFilter.values();
		int width = Math.max(1, (shell.contentTypeRow().width() + SEGMENT_OVERLAP * (filters.length - 1)) / filters.length);
		for (int i = 0; i < filters.length; i++) {
			EditorContentFilter value = filters[i];
			int x = shell.contentTypeRow().x() + i * (width - SEGMENT_OVERLAP);
			int w = i == filters.length - 1 ? shell.contentTypeRow().right() - x : width;
			EditorShellLayout.Rect rect = new EditorShellLayout.Rect(x, shell.contentTypeRow().y(), w,
				shell.contentTypeRow().height());
			if (value == filter) return rect;
		}
		return new EditorShellLayout.Rect(shell.contentTypeRow().x(), shell.contentTypeRow().y(),
			1, shell.contentTypeRow().height());
	}

	private void handleDiscardDialogClick(double mouseX, double mouseY, int button) {
		if (button != 0) return;
		ConfirmDialog.Action action = ConfirmDialog.hitTest(this.width, this.height, mouseX, mouseY);
		if (action == ConfirmDialog.Action.PRIMARY) {
			closeWithoutSaving();
		} else if (action == ConfirmDialog.Action.SECONDARY) {
			discardDialogOpen = false;
		}
	}

	private void requestClose() {
		if (activeMode == EditorShellMode.LOOK && settingsPanel.isModalOpen()) {
			settingsPanel.onDeactivate();
			return;
		}
		settingsPanel.commitPriorityEdit();
		settingsPanel.clearSwitchHoverSuppression();
		clearHeldControls();
		blurEditorFields();
		if (dirty) {
			discardDialogOpen = true;
			return;
		}
		closeWithoutSaving();
	}

	private void closeWithoutSaving() {
		discardDialogOpen = false;
		Minecraft.getInstance().setScreen(parent.asScreen());
	}

	private void clearHeldControls() {
		saveButtonHeld = false;
		cancelButtonHeld = false;
		disableSourceCheckboxHeld = false;
		heldMode = null;
		heldContentFilter = null;
		hideUsedHeld = false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (discardDialogOpen) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) discardDialogOpen = false;
			return true;
		}
		if (activeMode == EditorShellMode.LOOK && settingsPanel.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		if (activeMode == EditorShellMode.RULES && rulesPanel.isModalOpen()) {
			rulesPanel.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		if (nameField != null && nameField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				blurEditorFields();
				return true;
			}
			if (nameField.keyPressed(keyCode, scanCode, modifiers)) return true;
		}
		if (searchField != null && searchField.visible && searchField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				if (!searchQuery().isEmpty()) {
					searchField.setValue("");
				} else {
					blurEditorFields();
				}
				return true;
			}
			if (searchField.keyPressed(keyCode, scanCode, modifiers)) return true;
		}
		if (activeMode == EditorShellMode.RULES && rulesPanel.keyPressed(keyCode, scanCode, modifiers)) return true;
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			requestClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (discardDialogOpen) return true;
		if (activeMode == EditorShellMode.LOOK && settingsPanel.charTyped(codePoint, modifiers)) {
			return true;
		}
		if (activeMode == EditorShellMode.RULES && rulesPanel.isModalOpen()) {
			rulesPanel.charTyped(codePoint, modifiers);
			return true;
		}
		if (nameField != null && nameField.isFocused() && nameField.charTyped(codePoint, modifiers)) return true;
		if (searchField != null && searchField.visible && searchField.isFocused()
			&& searchField.charTyped(codePoint, modifiers)) return true;
		if (activeMode == EditorShellMode.RULES && rulesPanel.charTyped(codePoint, modifiers)) return true;
		return super.charTyped(codePoint, modifiers);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (discardDialogOpen) {
			clearHeldControls();
			return true;
		}
		if (button != 0) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		if (activeMode == EditorShellMode.LOOK && settingsPanel.mouseDragged(mouseX, mouseY, button)) {
			return true;
		}
		if (activeMode == EditorShellMode.RULES && rulesPanel.isModalOpen()) {
			rulesPanel.mouseDragged(mouseX, mouseY, button);
			return true;
		}
		if (activeMode == EditorShellMode.CONTENTS) {
			if (leftPanel.mouseDragged(mouseX, mouseY, button, layout)) return true;
			if (rightPanel.mouseDragged(mouseX, mouseY, button, previewLayout())) return true;
		} else if (activeMode == EditorShellMode.RULES) {
			if (rulesPanel.mouseDragged(mouseX, mouseY, button)) return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (discardDialogOpen) return true;
		if (activeMode == EditorShellMode.LOOK && settingsPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		if (activeMode == EditorShellMode.RULES && rulesPanel.isModalOpen()) {
			rulesPanel.mouseScrolled(mouseX, mouseY, scrollY);
			return true;
		}
		if (activeMode == EditorShellMode.CONTENTS && leftPanel.mouseScrolled(mouseX, mouseY, scrollY, layout)) {
			return true;
		}
		if (activeMode == EditorShellMode.RULES && rulesPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		if (rightPanel.mouseScrolled(mouseX, mouseY, scrollY, previewLayout())) return true;
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		shell = computeShellLayout();
		layout = shell.panelLayout();
		if (nameField != null) positionField(nameField, shell.nameField());
		if (searchField != null) positionField(searchField, shell.searchField());
		if (rulesPanel != null) initRulesPanel();
		if (settingsPanel != null) {
			initSettingsPanel();
			settingsPanel.repositionElements();
		}
		if (leftPanel != null) leftPanel.clampScroll(layout);
		if (rightPanel != null) rightPanel.clampScroll(previewLayout());
	}

	private EditorLayout previewLayout() {
		return shell.previewLayout();
	}

	private void switchMode(EditorShellMode mode) {
		if (mode == activeMode) return;
		if (activeMode == EditorShellMode.RULES) rulesPanel.onDeactivate();
		if (activeMode == EditorShellMode.LOOK) settingsPanel.onDeactivate();
		activeMode = mode;
		clearLeftHover();
		clearRightHover();
		if (activeMode == EditorShellMode.RULES) rulesPanel.onActivate();
		if (activeMode == EditorShellMode.LOOK) settingsPanel.onActivate();
		updateWidgetVisibility();
	}

	private void updateWidgetVisibility() {
		boolean contents = activeMode == EditorShellMode.CONTENTS;
		if (searchField != null) {
			searchField.visible = contents;
			searchField.active = contents;
			if (!contents) searchField.setFocused(false);
		}
	}

	private void applyContentFilter(EditorContentFilter filter) {
		if (!isContentFilterEnabled(filter)) filter = EditorContentFilter.ITEMS;
		activeContentFilter = filter;
		clearLeftHover();
		switch (filter) {
			case ITEMS -> leftPanel.showItems(searchQuery());
			case FLUIDS -> leftPanel.showFluids(searchQuery());
			case OTHER_TYPES -> leftPanel.showGeneric(searchQuery());
		}
		leftPanel.clampScroll(layout);
	}

	private boolean isContentFilterEnabled(EditorContentFilter filter) {
		return filter != EditorContentFilter.OTHER_TYPES || hasGenericIngredients;
	}

	private void toggleHideUsed() {
		boolean hide = !leftPanel.isHideUsed();
		leftPanel.setHideUsed(hide);
		GroupUiState.setHideUsed(hide);
		leftPanel.rebuildFilter(searchQuery());
		leftPanel.clampScroll(layout);
	}

	private String searchQuery() {
		return searchField == null ? "" : searchField.getValue();
	}

	private void onGroupChanged() {
		markDirty();
		state.ensureRuleSelection();
		rightPanel.rebuild();
		leftPanel.clampScroll(layout);
		rightPanel.clampScroll(previewLayout());
		if (activeMode == EditorShellMode.RULES) rulesPanel.onGroupChanged();
	}

	private void markDirty() {
		dirty = true;
	}

	private boolean canSaveNow() {
		return hasPendingSave() && state.canSave();
	}

	private boolean hasPendingSave() {
		return dirty || state.isCopyDraft();
	}

	private List<Component> saveDisabledTooltip() {
		if (!hasPendingSave() && state.canSave()) return List.of(Component.translatable(ModTranslationKeys.ORE_EDITOR_STATUS_CLEAN));
		return state.saveBlockedTooltip();
	}

	private Component saveDisabledReason() {
		if (!hasPendingSave() && state.canSave()) return Component.translatable(ModTranslationKeys.ORE_EDITOR_STATUS_CLEAN);
		List<Component> tooltip = state.saveBlockedTooltip();
		if (tooltip.isEmpty()) return Component.translatable(ModTranslationKeys.EDITOR_SAVE_ERROR);
		return tooltip.size() > 1 ? tooltip.get(1) : tooltip.get(0);
	}

	private void saveAndClose() {
		settingsPanel.commitPriorityEdit();
		if (!canSaveNow()) return;
		GroupDefinition saved = state.trySave().orElse(null);
		if (saved == null) {
			if (nameField != null) nameField.setTextColor(ERROR_TEXT_COLOR);
			return;
		}
		if (nameField != null) nameField.setTextColor(UiPalette.TEXT_PRIMARY);
		EditorRuntimeServices.get().invalidateFullMatchCache(saved.id());
		EditorRuntimeServices.get().populateFullMatchCacheFromSaved(saved);
		disableSourceAfterCopyIfRequested();
		parent.onGroupSaved(new SavedGroupContext(saved.id(), saveKind));
		EditorRuntimeServices.get().notifyViewer();
		Minecraft.getInstance().setScreen(parent.asScreen());
	}

	private void disableSourceAfterCopyIfRequested() {
		String sourceGroupId = state.sourceGroupId();
		if (!state.isCopyDraft() || !disableSourceAfterCopy || sourceGroupId == null) return;
		EditorRuntimeServices.get().setEnabledQuietlyWithoutEvent(sourceGroupId, false);
	}

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
}
