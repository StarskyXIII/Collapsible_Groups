package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.widget.ConfirmDialog;
import com.starskyxiii.collapsible_groups.client.widget.ColorPicker;
import com.starskyxiii.collapsible_groups.client.widget.CommandPress;

import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import com.starskyxiii.collapsible_groups.client.widget.EditorChrome;
import com.starskyxiii.collapsible_groups.client.widget.UiPalette;
import com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer;
import com.starskyxiii.collapsible_groups.client.widget.SettingsRowLayout;
import com.starskyxiii.collapsible_groups.config.ColorConfigParser;
import com.starskyxiii.collapsible_groups.group.GroupTheme;
import com.starskyxiii.collapsible_groups.group.GroupThemeColors;
import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Settings tab of the group editor: appearance (icons + colors) and behavior
 * (priority / id / enabled) rows, plus panel-owned color-picker and icon-picker
 * modals and the inline priority editor.
 *
 * <p>Mirrors {@link EditorRulesPanel}: owns its transient UI state (scroll,
 * modal buffers, the icon-picker search {@link EditBox}) and reaches durable
 * draft data only through {@link EditorSettingsState}. Both modals are mutually
 * exclusive and covered by {@link #isModalOpen()}; while either is open the
 * Screen routes every event here. The panel never draws tooltips itself — it
 * exposes {@link #hoverTooltip()} so the Screen can render them at top Z.
 */
public final class EditorSettingsPanel {
    private record Command(String kind, Object value) {}
    private final CommandPress<Command> press = new CommandPress<>();
    private int priorityStep;


	private static final int SETTINGS_ROW_GAP = 3;
	private static final int SETTINGS_SCROLLBAR_WIDTH = 6;
	private static final int PANEL_INSET = 8;
	private static final int ERROR_TEXT_COLOR = 0xFFFF6B5F;

	private static final int ICON_PICKER_SLOT = 18;
	private static final int ICON_PICKER_COLS = 8;

	// ── Dependencies ──────────────────────────────────────────────────────
	private final EditorSettingsState state;
	private final Font font;
	private final Runnable onChanged;
	private final Supplier<List<EditorRuntimeAccess.PreviewEntry>> groupItems;

	// Dirty gate: lets the color picker restore the Screen's dirty flag on cancel
	// (matching the pre-downsink behavior where markDirty is monotonic otherwise).
	private Supplier<Boolean> dirtyGet = () -> Boolean.TRUE;
	private Consumer<Boolean> dirtySet = value -> {};

	// ── Content rect (settings row column) ────────────────────────────────
	private int contentX, contentY, contentW, contentH;
	// Panel rect (for scrollbar X and modal placement).
	private int panelX, panelY, panelW, panelH;
	// Title bottom used for modal vertical placement.
	private int titleBottom;

	// ── Scroll state ──────────────────────────────────────────────────────
	private int scrollOffset;
	private boolean scrollbarDragging;
	private int scrollbarDragStartY;
	private int scrollbarDragStartOffset;
	private boolean switchHoverSuppressed;

	private @Nullable ColorPicker colorPicker;

	// ── Icon picker state ─────────────────────────────────────────────────
	private boolean iconPickerOpen;
	private boolean iconPickerTargetBack;
	private int iconPickerScrollOffset;
	private @Nullable EditBox iconPickerSearch;

	// ── Priority inline editor ────────────────────────────────────────────
	private boolean priorityEditing;
	private int priorityEditSnapshot;
	private String priorityEditText = "";

	// ── Tooltip surfaced to the Screen ────────────────────────────────────
	private @Nullable String colorTooltip;

	public enum SettingsColorTarget {
		NAME(ModTranslationKeys.ORE_EDITOR_SETTINGS_COLOR_NAME, true),
		COLLAPSED_HEADER(ModTranslationKeys.ORE_EDITOR_SETTINGS_COLOR_COLLAPSED_HEADER, false),
		EXPANDED_HEADER(ModTranslationKeys.ORE_EDITOR_SETTINGS_COLOR_EXPANDED_HEADER, false),
		EXPANDED_GROUP_BACKGROUND(ModTranslationKeys.ORE_EDITOR_SETTINGS_COLOR_EXPANDED_GROUP_BG, false),
		EXPANDED_GROUP_BORDER(ModTranslationKeys.ORE_EDITOR_SETTINGS_COLOR_EXPANDED_GROUP_BORDER, false);

		private final String labelKey;
		private final boolean rgbOnly;

		SettingsColorTarget(String labelKey, boolean rgbOnly) {
			this.labelKey = labelKey;
			this.rgbOnly = rgbOnly;
		}
	}

	public EditorSettingsPanel(EditorSettingsState state, Font font, Runnable onChanged,
	                           Supplier<List<EditorRuntimeAccess.PreviewEntry>> groupItems) {
		this.state = state;
		this.font = font;
		this.onChanged = onChanged;
		this.groupItems = groupItems;
	}

	/** Wires the Screen's dirty flag so the color picker can roll it back on cancel. */
	public void setDirtyGate(Supplier<Boolean> get, Consumer<Boolean> set) {
		this.dirtyGet = get;
		this.dirtySet = set;
	}

	// ─────────────────────────────────────────────────────────────────────
	// Lifecycle
	// ─────────────────────────────────────────────────────────────────────

	/**
	 * @param panel       editor panel rect (for scrollbar / modal placement)
	 * @param titleBottom bottom of the settings title (for modal placement)
	 * @param content     settings row content column rect
	 */
	public void init(EditorChrome.Rect panel, int titleBottom, EditorChrome.Rect content) {
        press.clear();
		this.panelX = panel.x();
		this.panelY = panel.y();
		this.panelW = panel.width();
		this.panelH = panel.height();
		this.titleBottom = titleBottom;
		this.contentX = content.x();
		this.contentY = content.y();
		this.contentW = content.width();
		this.contentH = content.height();
		clampScroll();
	}

	public void onActivate() {
		clampScroll();
	}

	public void onDeactivate() {
        press.clear();
		if (isColorPickerOpen()) {
			colorPicker.cancel();
		}
		if (iconPickerOpen) {
			closeIconPicker();
		}
		commitPriorityEdit();
		clearSwitchHoverSuppression();
		scrollbarDragging = false;
	}

	public void clearFocus() {
		if (iconPickerSearch != null) {
			iconPickerSearch.setFocused(false);
		}
		if (colorPicker != null) colorPicker.clearFocus();
	}

	public boolean isModalOpen() {
		return isColorPickerOpen() || iconPickerOpen;
	}

	@Nullable
	public Component hoverTooltip() {
		return colorTooltip == null ? null : Component.literal(colorTooltip);
	}

	public void commitPriorityEdit() {
		if (!priorityEditing) return;
		String trimmed = priorityEditText.trim();
		try {
			int value = Integer.parseInt(trimmed);
			if (value != state.editPriority()) {
				state.setEditPriority(value);
				onChanged.run();
			}
		} catch (NumberFormatException ignored) {
			state.setEditPriority(priorityEditSnapshot);
		}
		priorityEditing = false;
		priorityEditText = "";
	}

	public void clearSwitchHoverSuppression() {
		switchHoverSuppressed = false;
	}

	// ─────────────────────────────────────────────────────────────────────
	// Geometry
	// ─────────────────────────────────────────────────────────────────────

	private EditorChrome.Rect contentRect() {
		return new EditorChrome.Rect(contentX, contentY, Math.max(1, contentW), Math.max(1, contentH));
	}

	private EditorChrome.Rect panelRect() {
		return new EditorChrome.Rect(panelX, panelY, Math.max(1, panelW), Math.max(1, panelH));
	}

	/** Content column width, reserving space for the scrollbar. */
	private int rowColumnWidth() {
		return Math.max(1, contentW - SETTINGS_SCROLLBAR_WIDTH - SETTINGS_ROW_GAP);
	}

	private SettingsRowLayout.Result settingsLayout() {
		return SettingsRowLayout.compute(contentX, contentY, rowColumnWidth(),
			scrollOffset, SettingsColorTarget.values());
	}

	private int maxScroll() {
		int contentHeight = SettingsRowLayout.compute(contentX, contentY, rowColumnWidth(), 0,
			SettingsColorTarget.values()).contentHeight();
		return Math.max(0, contentHeight - contentH);
	}

	private void clampScroll() {
		scrollOffset = clamp(scrollOffset, 0, maxScroll());
	}

	private int scrollbarX() {
		return panelX + panelW - PANEL_INSET - SETTINGS_SCROLLBAR_WIDTH;
	}

	private EditorChrome.Rect scrollbarTrackRect() {
		return new EditorChrome.Rect(scrollbarX(), contentY, SETTINGS_SCROLLBAR_WIDTH, contentH);
	}

	private EditorChrome.Rect scrollbarThumbRect() {
		EditorChrome.Rect track = scrollbarTrackRect();
		int max = maxScroll();
		if (max <= 0) return new EditorChrome.Rect(track.x(), track.y(), track.width(), track.height());
		int contentHeight = track.height() + max;
		int thumbHeight = Math.max(14, track.height() * track.height() / contentHeight);
		int travel = Math.max(1, track.height() - thumbHeight);
		int thumbY = track.y() + travel * scrollOffset / max;
		return new EditorChrome.Rect(track.x(), thumbY, track.width(), thumbHeight);
	}

	// ─────────────────────────────────────────────────────────────────────
	// Render (rows + backdrop + scrollbar)
	// ─────────────────────────────────────────────────────────────────────

	public void render(GuiGraphics g, int mouseX, int mouseY) {
		colorTooltip = null;
		EditorChrome.Rect content = contentRect();
		clampScroll();
		SettingsRowLayout.Result result = settingsLayout();

		g.enableScissor(content.x(), content.y(), content.right(), content.bottom());
		renderSectionBackdrop(g, result, content);
		boolean modalBlocking = isModalOpen();
		int rmx = modalBlocking ? Integer.MIN_VALUE : mouseX;
		int rmy = modalBlocking ? Integer.MIN_VALUE : mouseY;
		for (SettingsRowLayout.Row row : result.rows()) {
			renderRow(g, row, rmx, rmy);
		}
		g.disableScissor();

		int max = maxScroll();
		if (max > 0) {
			UiSkinRenderer.drawScrollbarPixels(g, scrollbarX(), content.y(), content.height(),
				content.height(), content.height() + max, scrollOffset);
		}
	}

	public void renderModals(GuiGraphics g, int mouseX, int mouseY) {
		if (!isModalOpen()) return;
		g.pose().pushPose();
		g.pose().translate(0, 0, 500);
		if (isColorPickerOpen()) {
			colorPicker.render(g, mouseX, mouseY);
		} else if (iconPickerOpen) {
			renderIconPickerModal(g, mouseX, mouseY);
		}
		g.pose().popPose();
	}

	private void renderSectionBackdrop(GuiGraphics g, SettingsRowLayout.Result result, EditorChrome.Rect content) {
		// Backdrop / divider right edge shares the row column width (which
		// reserves the scrollbar gutter) so the scrollbar never overlaps the frame.
		int columnWidth = rowColumnWidth();
		int columnRight = content.x() + columnWidth;
		Integer sectionTop = null;
		SettingsRowLayout.Row previousRow = null;
		for (SettingsRowLayout.Row row : result.rows()) {
			if (row.kind() == SettingsRowLayout.Kind.SUBHEADER) {
				if (sectionTop != null && previousRow != null) {
					drawSectionPanel(g, content.x(), sectionTop, columnWidth,
						previousRow.rect().bottom() + SettingsRowLayout.SECTION_BOTTOM_PAD);
				}
				sectionTop = row.rect().y();
				previousRow = row;
				continue;
			}
			if (sectionTop == null) {
				sectionTop = row.rect().y();
			} else if (previousRow != null && previousRow.kind() != SettingsRowLayout.Kind.SUBHEADER) {
				int lineY = row.rect().y() - SettingsRowLayout.ROW_GAP / 2 - 1;
				g.fill(content.x() + 4, lineY, columnRight - 4, lineY + 1, UiPalette.OUTLINE_DARK);
			}
			previousRow = row;
		}
		if (sectionTop != null && previousRow != null) {
			drawSectionPanel(g, content.x(), sectionTop, columnWidth,
				previousRow.rect().bottom() + SettingsRowLayout.SECTION_BOTTOM_PAD);
		}
	}

	private void drawSectionPanel(GuiGraphics g, int x, int top, int w, int bottom) {
		g.fill(x, top, x + w, bottom, UiPalette.SURFACE);
		UiSkinRenderer.drawOutline(g, x, top, w, bottom - top, UiPalette.OUTLINE_DARK);
	}

	private void renderRow(GuiGraphics g, SettingsRowLayout.Row row, int mouseX, int mouseY) {
		switch (row.kind()) {
			case SUBHEADER -> renderSubheader(g, row);
			case ICON -> renderIconRow(g, row, mouseX, mouseY);
			case SWAP -> renderSwapRow(g, row, mouseX, mouseY);
			case COLOR -> renderColorRow(g, row, mouseX, mouseY);
			case PRIORITY -> renderPriorityRow(g, row, mouseX, mouseY);
			case ID -> renderIdRow(g, row, mouseX, mouseY);
			case ENABLED -> renderEnabledRow(g, row, mouseX, mouseY);
		}
	}

	private String subheaderLabel(String payloadKey) {
		return switch (payloadKey) {
			case "appearance.icons" -> Component.translatable(ModTranslationKeys.ORE_EDITOR_APPEARANCE_ICONS).getString();
			case "appearance.colors" -> Component.translatable(ModTranslationKeys.ORE_EDITOR_APPEARANCE_COLORS).getString();
			default -> Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_BEHAVIOR).getString();
		};
	}

	private void renderSubheader(GuiGraphics g, SettingsRowLayout.Row row) {
		String label = subheaderLabel((String) row.payload());
		g.drawString(font, font.plainSubstrByWidth(label, Math.max(0, row.rect().width())),
			row.rect().x() + SettingsRowLayout.PAD, row.rect().y() + 7, UiPalette.TEXT_MUTED, false);
	}

	private void renderIconRow(GuiGraphics g, SettingsRowLayout.Row row, int mouseX, int mouseY) {
		boolean back = (Boolean) row.payload();
		SettingsRowLayout.Rect rect = row.rect();
		boolean backLocked = back && !state.appearanceDraft().canEditBackIcon();
		String label = Component.translatable(back
			? ModTranslationKeys.ORE_EDITOR_SETTINGS_BACK_ICON
			: ModTranslationKeys.ORE_EDITOR_SETTINGS_FRONT_ICON).getString();
		String desc = Component.translatable(back
			? (backLocked
				? ModTranslationKeys.ORE_EDITOR_SETTINGS_BACK_ICON_LOCKED
				: ModTranslationKeys.ORE_EDITOR_SETTINGS_BACK_ICON_DESC)
			: ModTranslationKeys.ORE_EDITOR_SETTINGS_FRONT_ICON_DESC).getString();

		SettingsRowLayout.Rect slot = row.slot();
		UiSkinRenderer.drawSlot(g, slot.x(), slot.y(), slot.width());
		GroupIconDefinition iconId = back ? state.appearanceDraft().backIconId() : state.appearanceDraft().frontIconId();
		renderIcon(g, iconId, slot.x() + 2, slot.y() + 2);

		int textX = slot.right() + 8;
		g.drawString(font, font.plainSubstrByWidth(label, Math.max(0, row.changeBtn().x() - textX - 4)),
			textX, rect.y() + 3, UiPalette.TEXT_PRIMARY, false);
		String value = iconId == null ? desc : iconLabel(iconId);
		g.drawString(font, font.plainSubstrByWidth(value, Math.max(0, row.changeBtn().x() - textX - 4)),
			textX, rect.y() + 3 + font.lineHeight + 1,
			iconId == null ? UiPalette.TEXT_HINT : UiPalette.TEXT_MUTED, false);

		boolean canChange = !backLocked;
		drawRowButton(g, row.changeBtn(), new Command("icon", back),
			Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_CHANGE).getString(),
			buttonState(canChange, canChange && row.changeBtn().contains(mouseX, mouseY)));
		drawRowButton(g, row.clearBtn(), new Command("clear", back),
			Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_CLEAR).getString(),
			buttonState(iconId != null, iconId != null && row.clearBtn().contains(mouseX, mouseY)));
	}

	private void renderSwapRow(GuiGraphics g, SettingsRowLayout.Row row, int mouseX, int mouseY) {
		String label = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_SWAP_ICONS).getString();
		boolean enabled = state.appearanceDraft().frontIconId() != null && state.appearanceDraft().backIconId() != null;
		SettingsRowLayout.Rect swap = row.swapBtn();
		drawRowButton(g, swap, new Command("swap", null), label, buttonState(enabled, enabled && swap.contains(mouseX, mouseY)));
	}

	private void renderColorRow(GuiGraphics g, SettingsRowLayout.Row row, int mouseX, int mouseY) {
		SettingsColorTarget target = (SettingsColorTarget) row.payload();
		SettingsRowLayout.Rect rect = row.rect();
		int textY = UiSkinRenderer.centeredTextY(font, rect.y(), rect.height());
		String label = Component.translatable(target.labelKey).getString();

		SettingsRowLayout.Rect swatch = row.swatch();
		int labelX = rect.x() + SettingsRowLayout.PAD;
		int labelMax = Math.max(0, swatch.x() - labelX - 4);
		g.drawString(font, font.plainSubstrByWidth(label, labelMax), labelX, textY, UiPalette.TEXT_PRIMARY, false);

		int color = resolvedSettingsColor(target);
		g.fill(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(), color);
		UiSkinRenderer.drawOutline(g, swatch.x(), swatch.y(), swatch.width(), swatch.height(), UiPalette.OUTLINE_DARK);

		String raw = colorValue(target);
		String value = raw == null ? fallbackHex(target, color) : raw;
		SettingsRowLayout.Rect hexBox = row.hexBox();
		int hexMax = Math.max(0, hexBox.width());
		String clipped = font.plainSubstrByWidth(value, hexMax);
		g.drawString(font, clipped, hexBox.x(), textY, raw == null ? UiPalette.TEXT_HINT : UiPalette.TEXT_MUTED, false);
		if (font.width(value) > hexMax && rect.contains(mouseX, mouseY)) {
			colorTooltip = value;
		}

		drawRowButton(g, row.pickerBtn(), new Command("color", target), "...",
			buttonState(true, row.pickerBtn().contains(mouseX, mouseY)));
		drawRowButton(g, row.resetBtn(), new Command("reset", target),
			Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_RESET).getString(),
			buttonState(raw != null, raw != null && row.resetBtn().contains(mouseX, mouseY)));
	}

	private void renderPriorityRow(GuiGraphics g, SettingsRowLayout.Row row, int mouseX, int mouseY) {
		SettingsRowLayout.Rect rect = row.rect();
		String label = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_PRIORITY).getString();
		String desc = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_PRIORITY_DESC).getString();
		g.drawString(font, label, rect.x() + 6, rect.y() + 4, UiPalette.TEXT_PRIMARY, false);
		g.drawString(font, font.plainSubstrByWidth(desc, Math.max(0, row.stepMinus().x() - rect.x() - 12)),
			rect.x() + 6, rect.y() + 4 + font.lineHeight + 1, UiPalette.TEXT_HINT, false);

		drawRowButton(g, row.stepMinus(), new Command("priority", -1), "-",
			buttonState(true, row.stepMinus().contains(mouseX, mouseY)));
		SettingsRowLayout.Rect valueBox = row.valueBox();
		g.fill(valueBox.x(), valueBox.y(), valueBox.right(), valueBox.bottom(), UiPalette.SURFACE_DARK);
		boolean focused = priorityEditing;
		UiSkinRenderer.drawOutline(g, valueBox.x(), valueBox.y(), valueBox.width(), valueBox.height(),
			focused ? UiPalette.OUTLINE_SELECTED : UiPalette.OUTLINE_DARK);
		String value = focused ? priorityEditText : String.valueOf(state.editPriority());
		String clipped = font.plainSubstrByWidth(value, Math.max(0, valueBox.width() - 6));
		// Editing text stays centered, matching the idle value;
		// the caret sits just past the centered text's right edge and blinks.
		int valueX = valueBox.x() + Math.max(0, (valueBox.width() - font.width(clipped)) / 2);
		int valueY = UiSkinRenderer.centeredTextY(font, valueBox.y(), valueBox.height());
		g.drawString(font, clipped, valueX, valueY, UiPalette.TEXT_PRIMARY, false);
		if (focused && (Util.getMillis() / 500) % 2 == 0) {
			int cursorX = Math.min(valueBox.right() - 3, valueX + font.width(clipped) + 1);
			g.fill(cursorX, valueY - 1, cursorX + 1, valueY + font.lineHeight, UiPalette.TEXT_PRIMARY);
		}
		drawRowButton(g, row.stepPlus(), new Command("priority", 1), "+",
			buttonState(true, row.stepPlus().contains(mouseX, mouseY)));
	}

	private void renderIdRow(GuiGraphics g, SettingsRowLayout.Row row, int mouseX, int mouseY) {
		SettingsRowLayout.Rect rect = row.rect();
		String label = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_GROUP_ID).getString();
		String desc = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_GROUP_ID_DESC).getString();
		g.drawString(font, label, rect.x() + 6, rect.y() + 4, UiPalette.TEXT_PRIMARY, false);
		g.drawString(font, font.plainSubstrByWidth(desc, Math.max(0, row.copyBtn().x() - rect.x() - 12)),
			rect.x() + 6, rect.y() + 4 + font.lineHeight + 1, UiPalette.TEXT_HINT, false);

		String rawId = state.pendingRawId();
		String value = rawId.isBlank() ? "-" : rawId;
		int idX = rect.x() + 6 + Math.max(font.width(label), 0) + 12;
		int idMax = Math.max(0, row.copyBtn().x() - idX - 6);
		g.drawString(font, font.plainSubstrByWidth(value, idMax),
			idX, rect.y() + 4, rawId.isBlank() ? UiPalette.TEXT_HINT : UiPalette.TEXT_MUTED, false);
		drawRowButton(g, row.copyBtn(), new Command("copy", rawId),
			Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_COPY).getString(),
			buttonState(!rawId.isBlank(), !rawId.isBlank() && row.copyBtn().contains(mouseX, mouseY)));
	}

	private void renderEnabledRow(GuiGraphics g, SettingsRowLayout.Row row, int mouseX, int mouseY) {
		SettingsRowLayout.Rect rect = row.rect();
		String label = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_ENABLED).getString();
		g.drawString(font, label, rect.x() + 6, UiSkinRenderer.centeredTextY(font, rect.y(), rect.height()),
			UiPalette.TEXT_PRIMARY, false);
		SettingsRowLayout.Rect sw = row.switchRect();
		boolean hovered = effectiveSwitchHover(sw.contains(mouseX, mouseY));
		UiSkinRenderer.drawSwitch(g, sw.x(), sw.y(), sw.width(), sw.height(), state.editEnabled(), true, hovered, false);
	}

	private void drawRowButton(GuiGraphics g, SettingsRowLayout.Rect rect, Command command, String label, UiSkinRenderer.ButtonState buttonState) {
        if (buttonState == UiSkinRenderer.ButtonState.HOVERED && press.isHeld(command))
            buttonState = UiSkinRenderer.ButtonState.PRESSED;
		UiSkinRenderer.drawButton(g, font, rect.x(), rect.y(), rect.width(), rect.height(), label, buttonState);
	}

	// ─────────────────────────────────────────────────────────────────────
	// Color resolution
	// ─────────────────────────────────────────────────────────────────────

	private GroupTheme settingsTheme() {
		return state.appearanceDraft().toTheme();
	}

	private int resolvedSettingsColor(SettingsColorTarget target) {
		GroupTheme theme = settingsTheme();
		return switch (target) {
			case NAME -> 0xFF000000 | GroupThemeColors.nameColor(theme, Services.CONFIG.groupNameColor());
			case COLLAPSED_HEADER -> GroupThemeColors.collapsedHeaderBackground(theme, Services.CONFIG.collapsedGroupBackgroundColor());
			case EXPANDED_HEADER -> GroupThemeColors.expandedHeaderBackground(theme, Services.CONFIG.expandedGroupBackgroundColor());
			case EXPANDED_GROUP_BACKGROUND -> GroupThemeColors.expandedGroupBackground(theme, Services.CONFIG.expandedGroupBackgroundColor());
			case EXPANDED_GROUP_BORDER -> GroupThemeColors.expandedGroupBorder(theme, Services.CONFIG.expandedGroupBorderColor());
		};
	}

	private String colorValue(SettingsColorTarget target) {
		GroupTheme theme = settingsTheme();
		return switch (target) {
			case NAME -> theme.nameColor();
			case COLLAPSED_HEADER -> theme.collapsedHeaderBackground();
			case EXPANDED_HEADER -> theme.expandedHeaderBackground();
			case EXPANDED_GROUP_BACKGROUND -> theme.expandedGroupBackground();
			case EXPANDED_GROUP_BORDER -> theme.expandedGroupBorder();
		};
	}

	private AppearanceDraft withColor(SettingsColorTarget target, @Nullable String value) {
		return switch (target) {
			case NAME -> state.appearanceDraft().withNameColor(value);
			case COLLAPSED_HEADER -> state.appearanceDraft().withCollapsedHeaderBackground(value);
			case EXPANDED_HEADER -> state.appearanceDraft().withExpandedHeaderBackground(value);
			case EXPANDED_GROUP_BACKGROUND -> state.appearanceDraft().withExpandedGroupBackground(value);
			case EXPANDED_GROUP_BORDER -> state.appearanceDraft().withExpandedGroupBorder(value);
		};
	}

	private String fallbackHex(SettingsColorTarget target, int argb) {
		return target.rgbOnly
			? String.format(Locale.ROOT, "#%06X", argb & 0x00FFFFFF)
			: String.format(Locale.ROOT, "#%08X", argb);
	}

	private void renderIcon(GuiGraphics g, @Nullable GroupIconDefinition iconId, int x, int y) {
		if (iconId == null) return;
		List<EditorRuntimeAccess.PreviewEntry> resolved = EditorRuntimeServices.get()
			.resolveHeaderIcons(List.of(iconId), List.of());
		if (!resolved.isEmpty()) renderPickerEntry(g, resolved.get(0), x, y);
	}

	private static String iconLabel(GroupIconDefinition icon) {
		return icon.isItem() ? icon.valueId() : icon.ingredientType() + ": " + icon.valueId();
	}

	private static boolean sameIcon(GroupIconDefinition left, @Nullable GroupIconDefinition right) {
		return right != null
			&& left.valueId().equals(right.valueId())
			&& left.canonicalIngredientType().equals(right.canonicalIngredientType());
	}

    public boolean isColorPickerOpen() {
        return colorPicker != null && colorPicker.isOpen();
    }

    private EditorChrome.Rect centeredModalRect(int w, int h, int inset) {
        int x = clamp(panelX + (panelW - w) / 2, panelX + inset, panelX + panelW - inset - w);
        int y = clamp(panelY + (panelH - h) / 2, panelY + inset, panelY + panelH - inset - h);
        return new EditorChrome.Rect(x, y, w, h);
    }

    private void openColorPicker(SettingsColorTarget target) {
        press.clear();
        commitPriorityEdit();
        clearSwitchHoverSuppression();
        AppearanceDraft previous = state.appearanceDraft();
        boolean wasDirty = dirtyGet.get();
        colorPicker = new ColorPicker(font,
            Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_COLOR_PICKER, Component.translatable(target.labelKey)),
            resolvedSettingsColor(target), target.rgbOnly, value -> {
                state.setAppearanceDraft(withColor(target, com.starskyxiii.collapsible_groups.config.SettingsSnapshot.hex(value, target.rgbOnly)));
                onChanged.run();
            }, () -> {
                state.setAppearanceDraft(previous);
                dirtySet.accept(wasDirty);
            }, colorPickerArea());
    }

    private EditorChrome.Rect colorPickerArea() {
        if (panelW >= 282 && panelH >= 212) return panelRect();
        var window = Minecraft.getInstance().getWindow();
        return new EditorChrome.Rect(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

	// ─────────────────────────────────────────────────────────────────────
	// Icon picker modal (typed ingredient grid, data source = group contents)
	// ─────────────────────────────────────────────────────────────────────

	private boolean isIconPickerOpen() {
		return iconPickerOpen;
	}

	private void openIconPicker(boolean back) {
        press.clear();
		if (back && !state.appearanceDraft().canEditBackIcon()) {
			return;
		}
		commitPriorityEdit();
		clearSwitchHoverSuppression();
		iconPickerOpen = true;
		iconPickerTargetBack = back;
		iconPickerScrollOffset = 0;
		iconPickerSearch = new EditBox(font, 0, 0, 1, font.lineHeight + 2, Component.empty());
		iconPickerSearch.setBordered(false);
		iconPickerSearch.setMaxLength(64);
		iconPickerSearch.setHint(Component.translatable(ModTranslationKeys.ORE_EDITOR_ICON_PICKER_SEARCH));
		iconPickerSearch.setTextColor(UiPalette.TEXT_PRIMARY);
		iconPickerSearch.setResponder(value -> iconPickerScrollOffset = clamp(iconPickerScrollOffset, 0, iconPickerMaxScroll()));
		positionIconPickerSearch();
		iconPickerSearch.setFocused(true);
		scrollIconPickerToSelection();
	}

	private void confirmIconPickerSelection(EditorRuntimeAccess.PreviewEntry entry) {
		state.setAppearanceDraft(iconPickerTargetBack
			? state.appearanceDraft().withBackIconId(entry.icon())
			: state.appearanceDraft().withFrontIconId(entry.icon()));
		onChanged.run();
		closeIconPicker();
	}

	private void closeIconPicker() {
        press.clear();
		iconPickerOpen = false;
		iconPickerScrollOffset = 0;
		iconPickerSearch = null;
	}

	private GroupIconDefinition selectedIconPickerId() {
		return iconPickerTargetBack ? state.appearanceDraft().backIconId() : state.appearanceDraft().frontIconId();
	}

	private String iconPickerSearchText() {
		return iconPickerSearch == null ? "" : iconPickerSearch.getValue();
	}

	/**
	 * Group items, with the currently selected icon pinned to the front when it
	 * is not otherwise in the group. The pinned entry is flagged so the
	 * grid can mark it as a non-group item.
	 */
	private List<PickerEntry> iconPickerEntries() {
		List<PickerEntry> out = new ArrayList<>();
		String query = iconPickerSearchText().trim().toLowerCase(Locale.ROOT);
		GroupIconDefinition selectedId = selectedIconPickerId();
		List<EditorRuntimeAccess.PreviewEntry> pinned = selectedId == null ? List.of()
			: EditorRuntimeServices.get().resolveHeaderIcons(List.of(selectedId), List.of());
		boolean selectedInGroup = false;
		List<PickerEntry> body = new ArrayList<>();
		for (EditorRuntimeAccess.PreviewEntry entry : groupItems.get()) {
			if (sameIcon(entry.icon(), selectedId)) selectedInGroup = true;
			if (!matchesQuery(entry, query)) continue;
			body.add(new PickerEntry(entry, false));
		}
		if (!pinned.isEmpty() && !selectedInGroup) {
			EditorRuntimeAccess.PreviewEntry entry = pinned.get(0);
			if (matchesQuery(entry, query)) {
				out.add(new PickerEntry(entry, true));
			}
		}
		out.addAll(body);
		return out;
	}

	private boolean matchesQuery(EditorRuntimeAccess.PreviewEntry entry, String query) {
		if (query.isEmpty()) return true;
		String name = switch (entry.kind()) {
			case ITEM -> ((ItemStack) entry.value()).getHoverName().getString();
			case FLUID -> ((EditorFluidIngredientView) entry.value()).displayName().getString();
			case GENERIC -> ((EditorGenericIngredientView) entry.value()).displayName().getString();
		};
		return iconLabel(entry.icon()).toLowerCase(Locale.ROOT).contains(query)
			|| name.toLowerCase(Locale.ROOT).contains(query);
	}

	private static void renderPickerEntry(GuiGraphics g, EditorRuntimeAccess.PreviewEntry entry, int x, int y) {
		switch (entry.kind()) {
			case ITEM -> g.renderItem((ItemStack) entry.value(), x, y);
			case FLUID -> IngredientCellRenderer.renderFluid(
				g, (EditorFluidIngredientView) entry.value(), x, y);
			case GENERIC -> IngredientCellRenderer.renderGeneric(
				g, (EditorGenericIngredientView) entry.value(), x, y);
		}
	}

	private void renderPickerTooltip(GuiGraphics g, EditorRuntimeAccess.PreviewEntry entry, int x, int y) {
		switch (entry.kind()) {
			case ITEM -> g.renderTooltip(font, (ItemStack) entry.value(), x, y);
			case FLUID -> g.renderTooltip(font, EditorRuntimeServices.get()
				.fluidTooltip((EditorFluidIngredientView) entry.value()), java.util.Optional.empty(), x, y);
			case GENERIC -> g.renderTooltip(font, EditorRuntimeServices.get()
				.genericTooltip((EditorGenericIngredientView) entry.value()), java.util.Optional.empty(), x, y);
		}
	}

	private record PickerEntry(EditorRuntimeAccess.PreviewEntry ingredient, boolean nonGroup) {}

	private EditorChrome.Rect iconPickerModalRect() {
		int inset = PANEL_INSET;
		int w = Math.min(ICON_PICKER_COLS * ICON_PICKER_SLOT + 24, Math.max(120, panelW - inset * 2));
		int h = Math.min(230, Math.max(120, panelH - inset * 2));
		return centeredModalRect(w, h, inset);
	}

	private EditorChrome.Rect iconPickerSearchRect() {
		EditorChrome.Rect modal = iconPickerModalRect();
		int y = modal.y() + 10 + font.lineHeight + 8;
		return new EditorChrome.Rect(modal.x() + 10, y, modal.width() - 20, 18);
	}

	private EditorChrome.Rect iconPickerGridRect() {
		EditorChrome.Rect modal = iconPickerModalRect();
		int top = iconPickerSearchRect().bottom() + 8;
		return new EditorChrome.Rect(modal.x() + 10, top,
			ICON_PICKER_COLS * ICON_PICKER_SLOT + 1, Math.max(ICON_PICKER_SLOT, modal.bottom() - 10 - top));
	}

	private EditorChrome.Rect iconPickerCloseButtonRect() {
		EditorChrome.Rect modal = iconPickerModalRect();
		return new EditorChrome.Rect(modal.right() - 10 - SettingsRowLayout.WIDE_BUTTON_WIDTH,
			modal.y() + 6, SettingsRowLayout.WIDE_BUTTON_WIDTH, SettingsRowLayout.BUTTON_HEIGHT);
	}

	private void positionIconPickerSearch() {
		if (iconPickerSearch == null) return;
		EditorChrome.Rect search = iconPickerSearchRect();
		iconPickerSearch.setPosition(search.x() + 4, UiSkinRenderer.textFieldTextY(font, search.y(), search.height()) + 1);
		iconPickerSearch.setWidth(Math.max(1, search.width() - 8));
	}

	private int iconPickerContentHeight() {
		int count = iconPickerEntries().size();
		int rows = (count + ICON_PICKER_COLS - 1) / ICON_PICKER_COLS;
		return rows * ICON_PICKER_SLOT + 1;
	}

	private int iconPickerMaxScroll() {
		return Math.max(0, iconPickerContentHeight() - iconPickerGridRect().height());
	}

	private void scrollIconPickerToSelection() {
		GroupIconDefinition selectedId = selectedIconPickerId();
		if (selectedId == null) {
			iconPickerScrollOffset = 0;
			return;
		}
		List<PickerEntry> entries = iconPickerEntries();
		int index = -1;
		for (int i = 0; i < entries.size(); i++) {
			if (sameIcon(entries.get(i).ingredient().icon(), selectedId)) {
				index = i;
				break;
			}
		}
		if (index < 0) {
			iconPickerScrollOffset = 0;
			return;
		}
		EditorChrome.Rect grid = iconPickerGridRect();
		int rowTop = (index / ICON_PICKER_COLS) * ICON_PICKER_SLOT;
		iconPickerScrollOffset = clamp(rowTop - grid.height() / 2 + ICON_PICKER_SLOT / 2, 0, iconPickerMaxScroll());
	}

	private void renderIconPickerModal(GuiGraphics g, int mouseX, int mouseY) {
		EditorChrome.Rect modal = iconPickerModalRect();
		UiSkinRenderer.drawPanel(g, modal.x(), modal.y(), modal.width(), modal.height());
		UiSkinRenderer.drawOutline(g, modal.x(), modal.y(), modal.width(), modal.height(), UiPalette.OUTLINE_SELECTED);

		int x = modal.x() + 10;
		int y = modal.y() + 10;
		EditorChrome.Rect close = iconPickerCloseButtonRect();
		g.drawString(font, font.plainSubstrByWidth(
			Component.translatable(ModTranslationKeys.ORE_EDITOR_ICON_PICKER_TITLE).getString(),
			Math.max(0, close.x() - x - 6)),
			x, y, UiPalette.TEXT_PRIMARY, false);
		UiSkinRenderer.drawButton(g, font, close.x(), close.y(), close.width(), close.height(),
			Component.translatable(ModTranslationKeys.BUTTON_CANCEL).getString(),
			UiSkinRenderer.buttonState(true, false, close.contains(mouseX, mouseY), press.isHeld(new Command("close", null))));

		EditorChrome.Rect search = iconPickerSearchRect();
		boolean searchFocused = iconPickerSearch != null && iconPickerSearch.isFocused();
		boolean searchHovered = search.contains(mouseX, mouseY);
		int outline = searchFocused ? UiPalette.OUTLINE_SELECTED
			: searchHovered ? UiPalette.OUTLINE_HOVER : UiPalette.OUTLINE_DARK;
		g.fill(search.x(), search.y(), search.right(), search.bottom(), UiPalette.SURFACE_DARK);
		g.fill(search.x() + 1, search.y() + 1, search.right() - 1, search.bottom() - 1, UiPalette.SURFACE);
		UiSkinRenderer.drawOutline(g, search.x(), search.y(), search.width(), search.height(), outline);
		if (iconPickerSearch != null) {
			iconPickerSearch.render(g, mouseX, mouseY, 0);
		}

		EditorChrome.Rect grid = iconPickerGridRect();
		List<PickerEntry> entries = iconPickerEntries();
		if (entries.isEmpty()) {
			// Wrap the empty-state hint inside the grid width.
			int lineY = grid.y() + 4;
			for (net.minecraft.util.FormattedCharSequence line : font.split(
					Component.translatable(ModTranslationKeys.ORE_EDITOR_ICON_PICKER_EMPTY), Math.max(1, grid.width()))) {
				g.drawString(font, line, grid.x(), lineY, UiPalette.TEXT_HINT, false);
				lineY += font.lineHeight + 1;
			}
			return;
		}

		g.enableScissor(grid.x(), grid.y(), grid.right(), grid.bottom());
		// one shared-line grid instead of per-slot outlines (which
		// doubled to 2px between adjacent cells). Scrolls with the cells; the
		// scissor above clips it to the visible window.
		int totalGridRows = (entries.size() + ICON_PICKER_COLS - 1) / ICON_PICKER_COLS;
		UiSkinRenderer.drawSlotGrid(g, grid.x(), grid.y() - iconPickerScrollOffset,
			ICON_PICKER_COLS, totalGridRows, ICON_PICKER_SLOT);
		EditorRuntimeAccess.PreviewEntry hovered = null;
		GroupIconDefinition selectedId = selectedIconPickerId();
		for (int i = 0; i < entries.size(); i++) {
			PickerEntry entry = entries.get(i);
			int col = i % ICON_PICKER_COLS;
			int row = i / ICON_PICKER_COLS;
			int cx = grid.x() + col * ICON_PICKER_SLOT;
			int cy = grid.y() + row * ICON_PICKER_SLOT - iconPickerScrollOffset;
			if (cy + ICON_PICKER_SLOT < grid.y() || cy > grid.bottom()) continue;
			renderPickerEntry(g, entry.ingredient(), cx + 1, cy + 1);
			if (entry.nonGroup()) {
				// Non-group item marker: amber corner tab (top-right) so the "data
				// source = group contents" contract stays visible when pinned.
				g.fill(cx + ICON_PICKER_SLOT - 4, cy + 1, cx + ICON_PICKER_SLOT - 1, cy + 4, 0xFFF2C744);
			}
			if (sameIcon(entry.ingredient().icon(), selectedId)) {
				UiSkinRenderer.drawOutline(g, cx, cy, ICON_PICKER_SLOT, ICON_PICKER_SLOT, UiPalette.OUTLINE_SELECTED);
			}
			if (contains(cx, cy, ICON_PICKER_SLOT, ICON_PICKER_SLOT, mouseX, mouseY)) {
				g.fill(cx + 1, cy + 1, cx + ICON_PICKER_SLOT - 1, cy + ICON_PICKER_SLOT - 1, 0x40FFFFFF);
				hovered = entry.ingredient();
			}
		}
		g.disableScissor();

		int maxScroll = iconPickerMaxScroll();
		if (maxScroll > 0) {
			UiSkinRenderer.drawScrollbarPixels(g, grid.right() + 2, grid.y(), grid.height(),
				grid.height(), grid.height() + maxScroll, iconPickerScrollOffset);
		}
		if (hovered != null) {
			renderPickerTooltip(g, hovered, mouseX, mouseY);
		}
	}

	private boolean handleIconPickerClick(double mouseX, double mouseY) {
		EditorChrome.Rect modal = iconPickerModalRect();
		if (iconPickerCloseButtonRect().contains(mouseX, mouseY)) {
			closeIconPicker();
			return true;
		}
		EditorChrome.Rect search = iconPickerSearchRect();
		if (search.contains(mouseX, mouseY) && iconPickerSearch != null) {
			iconPickerSearch.setFocused(true);
			iconPickerSearch.mouseClicked(mouseX, mouseY, 0);
			return true;
		}
		EditorChrome.Rect grid = iconPickerGridRect();
		if (!grid.contains(mouseX, mouseY)) {
			// click outside the modal closes the icon picker.
			if (!modal.contains(mouseX, mouseY)) {
				closeIconPicker();
			}
			return true;
		}
		List<PickerEntry> entries = iconPickerEntries();
		int col = (int) ((mouseX - grid.x()) / ICON_PICKER_SLOT);
		int row = (int) ((mouseY - grid.y() + iconPickerScrollOffset) / ICON_PICKER_SLOT);
		if (col < 0 || col >= ICON_PICKER_COLS) return true;
		int index = row * ICON_PICKER_COLS + col;
		if (index >= 0 && index < entries.size()) {
			confirmIconPickerSelection(entries.get(index).ingredient());
		}
		return true;
	}

	private boolean handleIconPickerScroll(double scrollY) {
		int maxScroll = iconPickerMaxScroll();
		if (maxScroll <= 0) return true;
		iconPickerScrollOffset = clamp(iconPickerScrollOffset - (int) (scrollY * ICON_PICKER_SLOT), 0, maxScroll);
		return true;
	}

	private boolean handleIconPickerKey(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			closeIconPicker();
			return true;
		}
		if (iconPickerSearch != null && iconPickerSearch.isFocused()
			&& iconPickerSearch.keyPressed(keyCode, scanCode, modifiers)) {
			iconPickerScrollOffset = clamp(iconPickerScrollOffset, 0, iconPickerMaxScroll());
			return true;
		}
		return true;
	}

	private boolean handleIconPickerChar(char codePoint, int modifiers) {
		if (iconPickerSearch != null && iconPickerSearch.charTyped(codePoint, modifiers)) {
			iconPickerScrollOffset = clamp(iconPickerScrollOffset, 0, iconPickerMaxScroll());
		}
		return true;
	}

	// ─────────────────────────────────────────────────────────────────────
	// Priority inline editor
	// ─────────────────────────────────────────────────────────────────────

	private void beginPriorityEdit() {
		priorityEditing = true;
		priorityEditSnapshot = state.editPriority();
		priorityEditText = String.valueOf(state.editPriority());
	}

	private void cancelPriorityEdit() {
		if (!priorityEditing) return;
		state.setEditPriority(priorityEditSnapshot);
		priorityEditing = false;
		priorityEditText = "";
	}

	private boolean handlePriorityKey(int keyCode) {
		if (!priorityEditing) return false;
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			cancelPriorityEdit();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			commitPriorityEdit();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			if (!priorityEditText.isEmpty()) {
				priorityEditText = priorityEditText.substring(0, priorityEditText.length() - 1);
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_DELETE) {
			priorityEditText = "";
			return true;
		}
		return true;
	}

	private boolean handlePriorityChar(char codePoint) {
		if (!priorityEditing) return false;
		if (codePoint >= '0' && codePoint <= '9') {
			if (priorityEditText.length() < 11) {
				priorityEditText += codePoint;
			}
			return true;
		}
		if (codePoint == '-' && priorityEditText.isEmpty()) {
			priorityEditText = "-";
			return true;
		}
		return true;
	}

	private boolean isPriorityValueBoxAt(double mouseX, double mouseY) {
		if (!contentRect().contains(mouseX, mouseY)) return false;
		for (SettingsRowLayout.Row row : settingsLayout().rows()) {
			if (row.kind() == SettingsRowLayout.Kind.PRIORITY && row.valueBox().contains(mouseX, mouseY)) {
				return true;
			}
		}
		return false;
	}

	// ─────────────────────────────────────────────────────────────────────
	// Scrollbar interaction
	// ─────────────────────────────────────────────────────────────────────

	private boolean handleScrollbarClick(double mouseX, double mouseY) {
		if (maxScroll() <= 0) return false;
		EditorChrome.Rect track = scrollbarTrackRect();
		if (!track.contains(mouseX, mouseY)) return false;
		EditorChrome.Rect thumb = scrollbarThumbRect();
		if (!thumb.contains(mouseX, mouseY)) {
			scrollToThumbCenter((int) mouseY);
		}
		scrollbarDragging = true;
		scrollbarDragStartY = (int) mouseY;
		scrollbarDragStartOffset = scrollOffset;
		clearSwitchHoverSuppression();
		commitPriorityEdit();
		return true;
	}

	private void handleScrollbarDrag(double mouseY) {
		EditorChrome.Rect track = scrollbarTrackRect();
		EditorChrome.Rect thumb = scrollbarThumbRect();
		int max = maxScroll();
		int travel = Math.max(1, track.height() - thumb.height());
		int delta = (int) mouseY - scrollbarDragStartY;
		scrollOffset = clamp(scrollbarDragStartOffset + delta * max / travel, 0, max);
	}

	private void scrollToThumbCenter(int mouseY) {
		EditorChrome.Rect track = scrollbarTrackRect();
		EditorChrome.Rect thumb = scrollbarThumbRect();
		int max = maxScroll();
		int travel = Math.max(1, track.height() - thumb.height());
		int thumbTop = clamp(mouseY - thumb.height() / 2, track.y(), track.bottom() - thumb.height());
		scrollOffset = clamp((thumbTop - track.y()) * max / travel, 0, max);
	}

	private boolean effectiveSwitchHover(boolean rawHover) {
		if (!rawHover) {
			clearSwitchHoverSuppression();
			return false;
		}
		return !switchHoverSuppressed;
	}

	// ─────────────────────────────────────────────────────────────────────
	// Input entry points (Screen delegates here; already gated by mode)
	// ─────────────────────────────────────────────────────────────────────

    private Command commandAt(double x, double y) {
        if (isColorPickerOpen()) return null;
        if (iconPickerOpen) return iconPickerCloseButtonRect().contains(x, y) ? new Command("close", null) : null;
        if (!contentRect().contains(x, y)) return null;
        for (var row : settingsLayout().rows()) {
            if (!row.rect().contains(x, y)) continue;
            switch (row.kind()) {
                case ICON -> {
                    boolean back = (Boolean) row.payload();
                    if (row.changeBtn().contains(x, y) && (!back || state.appearanceDraft().canEditBackIcon())) return new Command("icon", back);
                    if (row.clearBtn().contains(x, y) && (back ? state.appearanceDraft().backIconId() : state.appearanceDraft().frontIconId()) != null)
                        return new Command("clear", back);
                }
                case SWAP -> {
                    if (row.swapBtn().contains(x, y) && state.appearanceDraft().frontIconId() != null && state.appearanceDraft().backIconId() != null)
                        return new Command("swap", null);
                }
                case COLOR -> {
                    var target = (SettingsColorTarget) row.payload();
                    if (row.pickerBtn().contains(x, y)) return new Command("color", target);
                    if (row.resetBtn().contains(x, y) && colorValue(target) != null) return new Command("reset", target);
                }
                case PRIORITY -> {
                    if (row.stepMinus().contains(x, y)) return new Command("priority", -1);
                    if (row.stepPlus().contains(x, y)) return new Command("priority", 1);
                }
                case ID -> {
                    if (row.copyBtn().contains(x, y) && !state.pendingRawId().isBlank()) return new Command("copy", state.pendingRawId());
                }
                default -> {}
            }
        }
        return null;
    }

    private void execute(Command command) {
        commitPriorityEdit();
        switch (command.kind()) {
            case "close" -> closeIconPicker();
            case "icon" -> openIconPicker((Boolean) command.value());
            case "clear" -> {
                state.setAppearanceDraft((Boolean) command.value() ? state.appearanceDraft().clearBackIcon() : state.appearanceDraft().clearFrontIcon());
                onChanged.run();
            }
            case "swap" -> { state.setAppearanceDraft(state.appearanceDraft().swapIcons()); onChanged.run(); }
            case "color" -> openColorPicker((SettingsColorTarget) command.value());
            case "reset" -> { state.setAppearanceDraft(withColor((SettingsColorTarget) command.value(), null)); onChanged.run(); }
            case "priority" -> { commitPriorityEdit(); state.setEditPriority(state.editPriority() + (Integer) command.value() * priorityStep); onChanged.run(); }
            case "copy" -> Minecraft.getInstance().keyboardHandler.setClipboard((String) command.value());
        }
    }

    public void clearHeldCommand() { press.clear(); }

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            press.begin(commandAt(mouseX, mouseY));
            if (press.target() != null) { priorityStep = Screen.hasShiftDown() ? 10 : 1; return true; }
        }
		if (isColorPickerOpen()) {
			colorPicker.mouseClicked(mouseX, mouseY, button);
			return true;
		}
		if (iconPickerOpen) {
			if (button == 0) handleIconPickerClick(mouseX, mouseY);
			return true;
		}
		if (button != 0) return false;
		if (priorityEditing && !isPriorityValueBoxAt(mouseX, mouseY)) {
			commitPriorityEdit();
		}
		if (handleScrollbarClick(mouseX, mouseY)) return true;
		EditorChrome.Rect content = contentRect();
		if (!content.contains(mouseX, mouseY)) return false;
		clampScroll();
		for (SettingsRowLayout.Row row : settingsLayout().rows()) {
			if (!row.rect().contains(mouseX, mouseY)) continue;
			switch (row.kind()) {
				case ICON -> handleIconRowClick(row, mouseX, mouseY);
				case SWAP, ID -> {}
				case COLOR -> handleColorRowClick(row, mouseX, mouseY);
				case PRIORITY -> handlePriorityRowClick(row, mouseX, mouseY);
				case ENABLED -> handleEnabledRowClick(row, mouseX, mouseY);
				case SUBHEADER -> {
				}
			}
			return true;
		}
		return true;
	}

	private void handleIconRowClick(SettingsRowLayout.Row row, double mouseX, double mouseY) {
		if (row.slot().contains(mouseX, mouseY)) openIconPicker((Boolean) row.payload());
	}

	private void handleColorRowClick(SettingsRowLayout.Row row, double mouseX, double mouseY) {
		if (row.swatch().contains(mouseX, mouseY) || row.hexBox().contains(mouseX, mouseY))
			openColorPicker((SettingsColorTarget) row.payload());
	}

	private void handlePriorityRowClick(SettingsRowLayout.Row row, double mouseX, double mouseY) {
		if (row.valueBox().contains(mouseX, mouseY)) beginPriorityEdit();
	}

	private void handleEnabledRowClick(SettingsRowLayout.Row row, double mouseX, double mouseY) {
		if (row.switchRect().contains(mouseX, mouseY)) {
			commitPriorityEdit();
			state.setEditEnabled(!state.editEnabled());
			switchHoverSuppressed = true;
			onChanged.run();
		}
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && press.target() != null) {
            Command command = press.release(commandAt(mouseX, mouseY));
            if (command != null) execute(command);
            return true;
        }
		if (isColorPickerOpen()) {
			if (button == 0) colorPicker.mouseReleased(mouseX, mouseY, button);
			return true;
		}
		if (iconPickerOpen) {
			return true;
		}
		if (scrollbarDragging) {
			scrollbarDragging = false;
			return true;
		}
		return false;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button) {
		if (button != 0) return false;
		if (isColorPickerOpen()) {
			colorPicker.mouseDragged(mouseX);
			return true;
		}
		if (iconPickerOpen) {
			return true;
		}
		if (scrollbarDragging) {
			handleScrollbarDrag(mouseY);
			return true;
		}
		return false;
	}

	/** Wheel over the whole editor panel scrolls the settings list. */
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        press.clear();
		if (isColorPickerOpen()) return true;
		if (iconPickerOpen) return handleIconPickerScroll(scrollY);
		if (!panelRect().contains(mouseX, mouseY)) return false;
		int max = maxScroll();
		if (max > 0) {
			scrollOffset = clamp(scrollOffset - (int) (scrollY * 16), 0, max);
			clearSwitchHoverSuppression();
			return true;
		}
		return false;
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        press.clear();
		if (isColorPickerOpen()) {
			return colorPicker.keyPressed(keyCode, scanCode, modifiers);
		}
		if (iconPickerOpen) {
			return handleIconPickerKey(keyCode, scanCode, modifiers);
		}
		if (priorityEditing) {
			return handlePriorityKey(keyCode);
		}
		return false;
	}

	public boolean charTyped(char codePoint, int modifiers) {
		if (isColorPickerOpen()) {
			return colorPicker.charTyped(codePoint, modifiers);
		}
		if (iconPickerOpen) {
			return handleIconPickerChar(codePoint, modifiers);
		}
		if (priorityEditing) {
			return handlePriorityChar(codePoint);
		}
		return false;
	}

	public void repositionElements() {
        press.clear();
		clampScroll();
		scrollbarDragging = false;
		clearSwitchHoverSuppression();
		if (iconPickerOpen) {
			positionIconPickerSearch();
		}
		if (colorPicker != null) colorPicker.setBounds(colorPickerArea());
	}

	private static boolean contains(int x, int y, int w, int h, double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private UiSkinRenderer.ButtonState buttonState(boolean enabled, boolean hovered) {
		if (!enabled) return UiSkinRenderer.ButtonState.DISABLED;
		return hovered ? UiSkinRenderer.ButtonState.HOVERED : UiSkinRenderer.ButtonState.NORMAL;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
