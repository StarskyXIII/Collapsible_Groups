package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.oreui.AppearanceDraft;
import com.starskyxiii.collapsible_groups.compat.jei.ui.EditorChrome;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupSampleRenderer;
import com.starskyxiii.collapsible_groups.compat.jei.ui.OreUiPalette;
import com.starskyxiii.collapsible_groups.compat.jei.ui.OreUiRenderer;
import com.starskyxiii.collapsible_groups.compat.jei.ui.SettingsRowLayout;
import com.starskyxiii.collapsible_groups.config.ColorConfigParser;
import com.starskyxiii.collapsible_groups.core.GroupTheme;
import com.starskyxiii.collapsible_groups.core.GroupThemeColors;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

	private static final int SETTINGS_ROW_GAP = 3;
	private static final int SETTINGS_SCROLLBAR_WIDTH = 6;
	private static final int COLOR_MODAL_WIDTH = 266;
	private static final int COLOR_MODAL_HEIGHT = 196;
	private static final int COLOR_DYE_SIZE = 14;
	private static final int COLOR_DYE_GAP = 3;
	// Track length only; the slider visual language (track/knob geometry and
	// anatomy) lives in OreUiRenderer.drawSlider (P5-polish-8).
	private static final int COLOR_SLIDER_WIDTH = 132;
	private static final int PANEL_INSET = 8;
	private static final int ERROR_TEXT_COLOR = 0xFFFF6B5F;
	// General-purpose swatch set: greyscale ramp plus common UI accents.
	private static final int[] DYE_COLORS = {
		0xFFFFFFFF, 0xFFCED2D6, 0xFF9DA3A9, 0xFF6B7178,
		0xFF3A3E44, 0xFF1E1F21, 0xFFE84C4C, 0xFFF08A3C,
		0xFFF2C744, 0xFF5ABF4A, 0xFF3FA9C4, 0xFF3C7DDB,
		0xFF6F5AE0, 0xFFB05AD6, 0xFFE066A6, 0xFF7A5A44
	};

	private static final int ICON_PICKER_SLOT = 18;
	private static final int ICON_PICKER_COLS = 8;

	// ── Dependencies ──────────────────────────────────────────────────────
	private final EditorSettingsState state;
	private final Font font;
	private final Runnable onChanged;
	private final Supplier<List<ItemStack>> groupItems;

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

	// ── Color picker state ────────────────────────────────────────────────
	private @Nullable SettingsColorTarget activeColorTarget;
	private @Nullable AppearanceDraft colorPickerSnapshot;
	private boolean colorPickerDirtySnapshot;
	private int colorPickerArgb = 0xFFFFFFFF;
	private String colorPickerHex = "FFFFFFFF";
	// fix 1 (P5-polish-5): native EditBox for the modal hex field. Independent of
	// iconPickerSearch (the two modals never open together, so no focus race). The
	// String above stays the source of truth for validity/apply; the box mirrors it.
	private @Nullable EditBox colorPickerHexBox;
	private boolean syncingColorPickerHexBox;
	private int draggingColorChannel = -1;

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
	                           Supplier<List<ItemStack>> groupItems) {
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
		if (isColorPickerOpen()) {
			cancelColorPicker();
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
		if (colorPickerHexBox != null) {
			colorPickerHexBox.setFocused(false);
		}
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

	/** Content column width, reserving space for the scrollbar (fix 6+8c). */
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
			OreUiRenderer.drawScrollbarPixels(g, scrollbarX(), content.y(), content.height(),
				content.height(), content.height() + max, scrollOffset);
		}
	}

	/** Renders the color / icon picker modals at the caller-established top Z. */
	public void renderModals(GuiGraphics g, int mouseX, int mouseY) {
		if (!isModalOpen()) return;
		// fix 5: lift the whole modal above the row list's renderItem draws (~z150).
		// The modal backdrop lands at z200, its own items at ~z350, still below the
		// OreConfirmDialog at z500. Panel-drawn tooltips inside stay on this pose.
		g.pose().pushPose();
		g.pose().translate(0, 0, 200);
		if (isColorPickerOpen()) {
			renderColorPickerModal(g, mouseX, mouseY);
		} else if (iconPickerOpen) {
			renderIconPickerModal(g, mouseX, mouseY);
		}
		g.pose().popPose();
	}

	private void renderSectionBackdrop(GuiGraphics g, SettingsRowLayout.Result result, EditorChrome.Rect content) {
		// fix 4: backdrop / divider right edge shares the row column width (which
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
				g.fill(content.x() + 4, lineY, columnRight - 4, lineY + 1, OreUiPalette.OUTLINE_DARK);
			}
			previousRow = row;
		}
		if (sectionTop != null && previousRow != null) {
			drawSectionPanel(g, content.x(), sectionTop, columnWidth,
				previousRow.rect().bottom() + SettingsRowLayout.SECTION_BOTTOM_PAD);
		}
	}

	private void drawSectionPanel(GuiGraphics g, int x, int top, int w, int bottom) {
		g.fill(x, top, x + w, bottom, OreUiPalette.SURFACE);
		OreUiRenderer.drawOutline(g, x, top, w, bottom - top, OreUiPalette.OUTLINE_DARK);
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
			row.rect().x() + SettingsRowLayout.PAD, row.rect().y() + 7, OreUiPalette.TEXT_MUTED, false);
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
		OreUiRenderer.drawSlot(g, slot.x(), slot.y(), slot.width());
		String iconId = back ? state.appearanceDraft().backIconId() : state.appearanceDraft().frontIconId();
		renderItemIconId(g, iconId, slot.x() + 2, slot.y() + 2);

		int textX = slot.right() + 8;
		g.drawString(font, font.plainSubstrByWidth(label, Math.max(0, row.changeBtn().x() - textX - 4)),
			textX, rect.y() + 3, OreUiPalette.TEXT_PRIMARY, false);
		String value = iconId == null ? desc : iconId;
		g.drawString(font, font.plainSubstrByWidth(value, Math.max(0, row.changeBtn().x() - textX - 4)),
			textX, rect.y() + 3 + font.lineHeight + 1,
			iconId == null ? OreUiPalette.TEXT_HINT : OreUiPalette.TEXT_MUTED, false);

		boolean canChange = !backLocked;
		drawRowButton(g, row.changeBtn(),
			Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_CHANGE).getString(),
			buttonState(canChange, canChange && row.changeBtn().contains(mouseX, mouseY)));
		drawRowButton(g, row.clearBtn(),
			Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_CLEAR).getString(),
			buttonState(iconId != null, iconId != null && row.clearBtn().contains(mouseX, mouseY)));
	}

	private void renderSwapRow(GuiGraphics g, SettingsRowLayout.Row row, int mouseX, int mouseY) {
		String label = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_SWAP_ICONS).getString();
		boolean enabled = state.appearanceDraft().frontIconId() != null && state.appearanceDraft().backIconId() != null;
		SettingsRowLayout.Rect swap = row.swapBtn();
		drawRowButton(g, swap, label, buttonState(enabled, enabled && swap.contains(mouseX, mouseY)));
	}

	private void renderColorRow(GuiGraphics g, SettingsRowLayout.Row row, int mouseX, int mouseY) {
		SettingsColorTarget target = (SettingsColorTarget) row.payload();
		SettingsRowLayout.Rect rect = row.rect();
		int textY = OreUiRenderer.centeredTextY(font, rect.y(), rect.height());
		String label = Component.translatable(target.labelKey).getString();

		SettingsRowLayout.Rect swatch = row.swatch();
		int labelX = rect.x() + SettingsRowLayout.PAD;
		int labelMax = Math.max(0, swatch.x() - labelX - 4);
		g.drawString(font, font.plainSubstrByWidth(label, labelMax), labelX, textY, OreUiPalette.TEXT_PRIMARY, false);

		int color = resolvedSettingsColor(target);
		g.fill(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(), color);
		OreUiRenderer.drawOutline(g, swatch.x(), swatch.y(), swatch.width(), swatch.height(), OreUiPalette.OUTLINE_DARK);

		String raw = colorValue(target);
		String value = raw == null ? fallbackHex(target, color) : raw;
		SettingsRowLayout.Rect hexBox = row.hexBox();
		int hexMax = Math.max(0, hexBox.width());
		String clipped = font.plainSubstrByWidth(value, hexMax);
		g.drawString(font, clipped, hexBox.x(), textY, raw == null ? OreUiPalette.TEXT_HINT : OreUiPalette.TEXT_MUTED, false);
		if (font.width(value) > hexMax && rect.contains(mouseX, mouseY)) {
			colorTooltip = value;
		}

		drawRowButton(g, row.pickerBtn(), "...",
			buttonState(true, row.pickerBtn().contains(mouseX, mouseY)));
		drawRowButton(g, row.resetBtn(),
			Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_RESET).getString(),
			buttonState(raw != null, raw != null && row.resetBtn().contains(mouseX, mouseY)));
	}

	private void renderPriorityRow(GuiGraphics g, SettingsRowLayout.Row row, int mouseX, int mouseY) {
		SettingsRowLayout.Rect rect = row.rect();
		String label = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_PRIORITY).getString();
		String desc = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_PRIORITY_DESC).getString();
		g.drawString(font, label, rect.x() + 6, rect.y() + 4, OreUiPalette.TEXT_PRIMARY, false);
		g.drawString(font, font.plainSubstrByWidth(desc, Math.max(0, row.stepMinus().x() - rect.x() - 12)),
			rect.x() + 6, rect.y() + 4 + font.lineHeight + 1, OreUiPalette.TEXT_HINT, false);

		drawRowButton(g, row.stepMinus(), "-",
			buttonState(true, row.stepMinus().contains(mouseX, mouseY)));
		SettingsRowLayout.Rect valueBox = row.valueBox();
		g.fill(valueBox.x(), valueBox.y(), valueBox.right(), valueBox.bottom(), OreUiPalette.SURFACE_DARK);
		boolean focused = priorityEditing;
		OreUiRenderer.drawOutline(g, valueBox.x(), valueBox.y(), valueBox.width(), valueBox.height(),
			focused ? OreUiPalette.OUTLINE_SELECTED : OreUiPalette.OUTLINE_DARK);
		String value = focused ? priorityEditText : String.valueOf(state.editPriority());
		String clipped = font.plainSubstrByWidth(value, Math.max(0, valueBox.width() - 6));
		// fix 9: editing text stays centered (same centering as the idle value);
		// the caret sits just past the centered text's right edge and blinks.
		int valueX = valueBox.x() + Math.max(0, (valueBox.width() - font.width(clipped)) / 2);
		int valueY = OreUiRenderer.centeredTextY(font, valueBox.y(), valueBox.height());
		g.drawString(font, clipped, valueX, valueY, OreUiPalette.TEXT_PRIMARY, false);
		if (focused && (Util.getMillis() / 500) % 2 == 0) {
			int cursorX = Math.min(valueBox.right() - 3, valueX + font.width(clipped) + 1);
			g.fill(cursorX, valueY - 1, cursorX + 1, valueY + font.lineHeight, OreUiPalette.TEXT_PRIMARY);
		}
		drawRowButton(g, row.stepPlus(), "+",
			buttonState(true, row.stepPlus().contains(mouseX, mouseY)));
	}

	private void renderIdRow(GuiGraphics g, SettingsRowLayout.Row row, int mouseX, int mouseY) {
		SettingsRowLayout.Rect rect = row.rect();
		String label = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_GROUP_ID).getString();
		String desc = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_GROUP_ID_DESC).getString();
		g.drawString(font, label, rect.x() + 6, rect.y() + 4, OreUiPalette.TEXT_PRIMARY, false);
		g.drawString(font, font.plainSubstrByWidth(desc, Math.max(0, row.copyBtn().x() - rect.x() - 12)),
			rect.x() + 6, rect.y() + 4 + font.lineHeight + 1, OreUiPalette.TEXT_HINT, false);

		String rawId = state.pendingRawId();
		String value = rawId.isBlank() ? "-" : rawId;
		int idX = rect.x() + 6 + Math.max(font.width(label), 0) + 12;
		int idMax = Math.max(0, row.copyBtn().x() - idX - 6);
		g.drawString(font, font.plainSubstrByWidth(value, idMax),
			idX, rect.y() + 4, rawId.isBlank() ? OreUiPalette.TEXT_HINT : OreUiPalette.TEXT_MUTED, false);
		drawRowButton(g, row.copyBtn(),
			Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_COPY).getString(),
			buttonState(!rawId.isBlank(), !rawId.isBlank() && row.copyBtn().contains(mouseX, mouseY)));
	}

	private void renderEnabledRow(GuiGraphics g, SettingsRowLayout.Row row, int mouseX, int mouseY) {
		SettingsRowLayout.Rect rect = row.rect();
		String label = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_ENABLED).getString();
		g.drawString(font, label, rect.x() + 6, OreUiRenderer.centeredTextY(font, rect.y(), rect.height()),
			OreUiPalette.TEXT_PRIMARY, false);
		SettingsRowLayout.Rect sw = row.switchRect();
		boolean hovered = effectiveSwitchHover(sw.contains(mouseX, mouseY));
		OreUiRenderer.drawSwitch(g, sw.x(), sw.y(), sw.width(), sw.height(), state.editEnabled(), true, hovered, false);
	}

	private void drawRowButton(GuiGraphics g, SettingsRowLayout.Rect rect, String label, OreUiRenderer.ButtonState buttonState) {
		OreUiRenderer.drawButton(g, font, rect.x(), rect.y(), rect.width(), rect.height(), label, buttonState);
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

	private void renderItemIconId(GuiGraphics g, @Nullable String iconId, int x, int y) {
		ItemStack stack = iconStack(iconId);
		if (!stack.isEmpty()) {
			g.renderItem(stack, x, y);
		}
	}

	private ItemStack iconStack(@Nullable String iconId) {
		if (iconId == null || iconId.isBlank()) return ItemStack.EMPTY;
		ResourceLocation loc = ResourceLocation.tryParse(iconId);
		if (loc == null) return ItemStack.EMPTY;
		Item item = BuiltInRegistries.ITEM.get(loc);
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	// ─────────────────────────────────────────────────────────────────────
	// Color picker modal
	// ─────────────────────────────────────────────────────────────────────

	private EditorChrome.Rect colorModalRect() {
		int inset = PANEL_INSET;
		int w = Math.min(COLOR_MODAL_WIDTH, Math.max(120, panelW - inset * 2));
		int h = Math.min(COLOR_MODAL_HEIGHT, Math.max(120, panelH - inset * 2));
		return centeredModalRect(w, h, inset);
	}

	/** Centers a modal of the given size within the settings panel (fix 6). */
	private EditorChrome.Rect centeredModalRect(int w, int h, int inset) {
		int x = clamp(panelX + (panelW - w) / 2, panelX + inset, panelX + panelW - inset - w);
		int y = clamp(panelY + (panelH - h) / 2, panelY + inset, panelY + panelH - inset - h);
		return new EditorChrome.Rect(x, y, w, h);
	}

	public boolean isColorPickerOpen() {
		return activeColorTarget != null;
	}

	/**
	 * Top-left corner (and size) of the modal's hex row, mirroring the vertical
	 * cursor walk in {@link #renderColorPickerModal}. Render and the EditBox share
	 * this one geometry so they never drift (fix 1, P5-polish-5).
	 */
	private EditorChrome.Rect colorHexRowRect() {
		EditorChrome.Rect modal = colorModalRect();
		int y = modal.y() + 8
			+ (font.lineHeight + 6)
			+ (COLOR_DYE_SIZE * 2 + COLOR_DYE_GAP + 8)
			+ 20 * 3 + 22;
		return new EditorChrome.Rect(modal.x() + 8, y, modal.width() - 16, 18);
	}

	/** The editable inner field of the hex row (offset past the "#RRGGBB" label). */
	private EditorChrome.Rect colorHexFieldRect() {
		EditorChrome.Rect row = colorHexRowRect();
		int fieldX = row.x() + 58;
		int fieldW = Math.max(60, row.width() - 58);
		return new EditorChrome.Rect(fieldX, row.y(), fieldW, 18);
	}

	private int colorChannel(int channel) {
		return switch (channel) {
			case 0 -> (colorPickerArgb >>> 16) & 0xFF;
			case 1 -> (colorPickerArgb >>> 8) & 0xFF;
			case 2 -> colorPickerArgb & 0xFF;
			default -> (colorPickerArgb >>> 24) & 0xFF;
		};
	}

	private boolean isHexInputValid(SettingsColorTarget target) {
		String prefixed = "#" + colorPickerHex;
		if (target.rgbOnly) {
			return colorPickerHex.length() == 6 && ColorConfigParser.isValidArgb(prefixed);
		}
		return (colorPickerHex.length() == 6 || colorPickerHex.length() == 8)
			&& ColorConfigParser.isValidArgb(prefixed);
	}

	private void openColorPicker(SettingsColorTarget target) {
		commitPriorityEdit();
		clearSwitchHoverSuppression();
		activeColorTarget = target;
		colorPickerSnapshot = state.appearanceDraft();
		colorPickerDirtySnapshot = dirtyGet.get();
		colorPickerArgb = resolvedSettingsColor(target);
		if (target.rgbOnly) {
			colorPickerArgb = 0xFF000000 | (colorPickerArgb & 0x00FFFFFF);
		}
		colorPickerHex = target.rgbOnly
			? String.format(Locale.ROOT, "%06X", colorPickerArgb & 0x00FFFFFF)
			: String.format(Locale.ROOT, "%08X", colorPickerArgb);
		draggingColorChannel = -1;
		createColorPickerHexBox(target);
	}

	private void createColorPickerHexBox(SettingsColorTarget target) {
		int max = target.rgbOnly ? 6 : 8;
		EditBox box = new EditBox(font, 0, 0, 1, font.lineHeight + 2, Component.empty());
		box.setBordered(false);
		box.setMaxLength(max);
		box.setTextColor(OreUiPalette.TEXT_PRIMARY);
		// Uppercase hex digits only, capped at the channel width.
		box.setFilter(s -> s.chars().allMatch(c -> isHexChar((char) c)) && s.length() <= max);
		box.setValue(colorPickerHex);
		box.setResponder(this::onColorPickerHexTyped);
		colorPickerHexBox = box;
		positionColorPickerHexBox();
		box.setFocused(true);
		box.moveCursorToEnd(false);
	}

	/**
	 * EditBox responder: normalise to uppercase, mirror into {@link #colorPickerHex},
	 * and apply live when valid (non-valid keeps the red frame, no draft change —
	 * the pre-existing live-preview semantics).
	 */
	private void onColorPickerHexTyped(String value) {
		if (syncingColorPickerHexBox) {
			colorPickerHex = value.toUpperCase(Locale.ROOT);
			return;
		}
		String upper = value.toUpperCase(Locale.ROOT);
		if (!upper.equals(value) && colorPickerHexBox != null) {
			int cursor = colorPickerHexBox.getCursorPosition();
			colorPickerHexBox.setValue(upper);
			colorPickerHexBox.setCursorPosition(cursor);
			colorPickerHexBox.setHighlightPos(cursor);
			return; // setValue re-enters this responder with the uppercased text.
		}
		colorPickerHex = upper;
		applyColorPickerHexIfValid();
	}

	private void positionColorPickerHexBox() {
		if (colorPickerHexBox == null) return;
		EditorChrome.Rect field = colorHexFieldRect();
		colorPickerHexBox.setPosition(field.x() + 4,
			OreUiRenderer.textFieldTextY(font, field.y(), field.height()) + 1);
		colorPickerHexBox.setWidth(Math.max(1, field.width() - 8));
	}

	private void confirmColorPicker() {
		activeColorTarget = null;
		colorPickerSnapshot = null;
		colorPickerHexBox = null;
		draggingColorChannel = -1;
	}

	private void cancelColorPicker() {
		if (colorPickerSnapshot != null) {
			state.setAppearanceDraft(colorPickerSnapshot);
			dirtySet.accept(colorPickerDirtySnapshot);
		}
		activeColorTarget = null;
		colorPickerSnapshot = null;
		colorPickerHexBox = null;
		draggingColorChannel = -1;
	}

	private void applyColorPickerArgb(int argb) {
		SettingsColorTarget target = activeColorTarget;
		if (target == null) return;
		if (target.rgbOnly) {
			argb = 0xFF000000 | (argb & 0x00FFFFFF);
		}
		colorPickerArgb = argb;
		colorPickerHex = target.rgbOnly
			? String.format(Locale.ROOT, "%06X", colorPickerArgb & 0x00FFFFFF)
			: String.format(Locale.ROOT, "%08X", colorPickerArgb);
		syncColorPickerHexBox();
		applyColorPickerValue(target);
	}

	/** Push {@link #colorPickerHex} into the EditBox without re-firing apply. */
	private void syncColorPickerHexBox() {
		if (colorPickerHexBox == null || colorPickerHexBox.getValue().equals(colorPickerHex)) return;
		syncingColorPickerHexBox = true;
		try {
			colorPickerHexBox.setValue(colorPickerHex);
		} finally {
			syncingColorPickerHexBox = false;
		}
	}

	private void applyColorPickerValue(SettingsColorTarget target) {
		String value = target.rgbOnly
			? String.format(Locale.ROOT, "#%06X", colorPickerArgb & 0x00FFFFFF)
			: String.format(Locale.ROOT, "#%08X", colorPickerArgb);
		state.setAppearanceDraft(withColor(target, value));
		onChanged.run();
	}

	private void setColorPickerChannel(int channel, int value) {
		value = clamp(value, 0, 255);
		int a = colorChannel(3);
		int r = colorChannel(0);
		int gg = colorChannel(1);
		int b = colorChannel(2);
		switch (channel) {
			case 0 -> r = value;
			case 1 -> gg = value;
			case 2 -> b = value;
			case 3 -> a = value;
			default -> {
			}
		}
		applyColorPickerArgb((a << 24) | (r << 16) | (gg << 8) | b);
	}

	private void renderColorPickerModal(GuiGraphics g, int mouseX, int mouseY) {
		SettingsColorTarget target = activeColorTarget;
		if (target == null) return;
		EditorChrome.Rect modal = colorModalRect();
		OreUiRenderer.drawPanel(g, modal.x(), modal.y(), modal.width(), modal.height());
		OreUiRenderer.drawOutline(g, modal.x(), modal.y(), modal.width(), modal.height(), OreUiPalette.OUTLINE_SELECTED);

		int x = modal.x() + 8;
		int y = modal.y() + 8;
		String title = Component.translatable(ModTranslationKeys.ORE_EDITOR_SETTINGS_COLOR_PICKER,
			Component.translatable(target.labelKey).getString()).getString();
		g.drawString(font, font.plainSubstrByWidth(title, Math.max(0, modal.width() - 16)),
			x, y, OreUiPalette.TEXT_PRIMARY, false);
		y += font.lineHeight + 6;

		int swatchX = modal.right() - 28;
		g.fill(swatchX, y - 1, swatchX + 18, y + 17, colorPickerArgb);
		OreUiRenderer.drawOutline(g, swatchX, y - 1, 18, 18, OreUiPalette.OUTLINE_DARK);
		renderDyePalette(g, x, y, mouseX, mouseY);
		y += COLOR_DYE_SIZE * 2 + COLOR_DYE_GAP + 8;

		renderColorSlider(g, 0, x, y, target.rgbOnly, mouseX, mouseY);
		y += 20;
		renderColorSlider(g, 1, x, y, target.rgbOnly, mouseX, mouseY);
		y += 20;
		renderColorSlider(g, 2, x, y, target.rgbOnly, mouseX, mouseY);
		y += 20;
		renderColorSlider(g, 3, x, y, target.rgbOnly, mouseX, mouseY);
		y += 22;

		renderHexField(g, mouseX, mouseY, target);

		int buttonY = modal.bottom() - 28;
		int buttonW = 58;
		int cancelX = modal.right() - 8 - buttonW;
		int okX = cancelX - SETTINGS_ROW_GAP - buttonW;
		OreUiRenderer.drawButton(g, font, okX, buttonY, buttonW, 20,
			Component.translatable(ModTranslationKeys.EDITOR_RULES_PICKER_CONFIRM).getString(),
			buttonState(true, contains(okX, buttonY, buttonW, 20, mouseX, mouseY)));
		OreUiRenderer.drawButton(g, font, cancelX, buttonY, buttonW, 20,
			Component.translatable(ModTranslationKeys.BUTTON_CANCEL).getString(),
			buttonState(true, contains(cancelX, buttonY, buttonW, 20, mouseX, mouseY)));
	}

	private void renderDyePalette(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
		for (int i = 0; i < DYE_COLORS.length; i++) {
			int col = i % 8;
			int row = i / 8;
			int swatchX = x + col * (COLOR_DYE_SIZE + COLOR_DYE_GAP);
			int swatchY = y + row * (COLOR_DYE_SIZE + COLOR_DYE_GAP);
			g.fill(swatchX, swatchY, swatchX + COLOR_DYE_SIZE, swatchY + COLOR_DYE_SIZE, DYE_COLORS[i]);
			OreUiRenderer.drawOutline(g, swatchX, swatchY, COLOR_DYE_SIZE, COLOR_DYE_SIZE,
				contains(swatchX, swatchY, COLOR_DYE_SIZE, COLOR_DYE_SIZE, mouseX, mouseY)
					? OreUiPalette.OUTLINE_HOVER
					: OreUiPalette.OUTLINE_DARK);
		}
	}

	private void renderColorSlider(GuiGraphics g, int channel, int x, int y, boolean rgbOnly, int mouseX, int mouseY) {
		String label = switch (channel) {
			case 0 -> "R";
			case 1 -> "G";
			case 2 -> "B";
			default -> "A";
		};
		boolean active = channel != 3 || !rgbOnly;
		int value = colorChannel(channel);
		int textColor = active ? OreUiPalette.TEXT_PRIMARY : OreUiPalette.TEXT_DISABLED;
		g.drawString(font, label, x, OreUiRenderer.centeredTextY(font, y, 16), textColor, false);
		int sliderX = x + 16;
		int bandY = y + 2;
		boolean sliderHot = draggingColorChannel == channel
			|| (active && OreUiRenderer.sliderHitBand(sliderX, bandY, COLOR_SLIDER_WIDTH)
				.contains(mouseX, mouseY));
		OreUiRenderer.drawSlider(g, sliderX, bandY, COLOR_SLIDER_WIDTH, value, 255, active, sliderHot);
		String valueText = String.valueOf(value);
		g.drawString(font, valueText, sliderX + COLOR_SLIDER_WIDTH + 8,
			OreUiRenderer.centeredTextY(font, y, 16), textColor, false);
	}

	private void renderHexField(GuiGraphics g, int mouseX, int mouseY, SettingsColorTarget target) {
		EditorChrome.Rect row = colorHexRowRect();
		String label = target.rgbOnly ? "#RRGGBB" : "#AARRGGBB";
		g.drawString(font, label, row.x(), OreUiRenderer.centeredTextY(font, row.y(), 18),
			OreUiPalette.TEXT_MUTED, false);
		EditorChrome.Rect field = colorHexFieldRect();
		boolean valid = isHexInputValid(target);
		boolean focused = colorPickerHexBox != null && colorPickerHexBox.isFocused();
		boolean hovered = field.contains(mouseX, mouseY);
		g.fill(field.x(), field.y(), field.right(), field.bottom(), OreUiPalette.SURFACE_DARK);
		g.fill(field.x() + 1, field.y() + 1, field.right() - 1, field.bottom() - 1, OreUiPalette.SURFACE);
		int outline = !valid ? ERROR_TEXT_COLOR
			: focused ? OreUiPalette.OUTLINE_SELECTED
			: hovered ? OreUiPalette.OUTLINE_HOVER : OreUiPalette.OUTLINE_DARK;
		OreUiRenderer.drawOutline(g, field.x(), field.y(), field.width(), field.height(), outline);
		if (colorPickerHexBox != null) {
			colorPickerHexBox.setTextColor(valid ? OreUiPalette.TEXT_PRIMARY : ERROR_TEXT_COLOR);
			colorPickerHexBox.render(g, mouseX, mouseY, 0);
		}
	}

	private boolean handleColorPickerClick(double mouseX, double mouseY) {
		SettingsColorTarget target = activeColorTarget;
		if (target == null) return false;
		EditorChrome.Rect modal = colorModalRect();
		int buttonY = modal.bottom() - 28;
		int buttonW = 58;
		int cancelX = modal.right() - 8 - buttonW;
		int okX = cancelX - SETTINGS_ROW_GAP - buttonW;
		if (contains(okX, buttonY, buttonW, 20, mouseX, mouseY)) {
			confirmColorPicker();
			return true;
		}
		if (contains(cancelX, buttonY, buttonW, 20, mouseX, mouseY)) {
			cancelColorPicker();
			return true;
		}

		// fix 1: clicking the hex field focuses the EditBox and forwards the click.
		if (colorHexFieldRect().contains(mouseX, mouseY) && colorPickerHexBox != null) {
			colorPickerHexBox.setFocused(true);
			colorPickerHexBox.mouseClicked(mouseX, mouseY, 0);
			return true;
		}
		if (colorPickerHexBox != null) {
			colorPickerHexBox.setFocused(false);
		}

		int paletteX = modal.x() + 8;
		int paletteY = modal.y() + 8 + font.lineHeight + 6;
		for (int i = 0; i < DYE_COLORS.length; i++) {
			int col = i % 8;
			int row = i / 8;
			int x = paletteX + col * (COLOR_DYE_SIZE + COLOR_DYE_GAP);
			int y = paletteY + row * (COLOR_DYE_SIZE + COLOR_DYE_GAP);
			if (contains(x, y, COLOR_DYE_SIZE, COLOR_DYE_SIZE, mouseX, mouseY)) {
				int alpha = target.rgbOnly ? 0xFF000000 : colorPickerArgb & 0xFF000000;
				applyColorPickerArgb(alpha | (DYE_COLORS[i] & 0x00FFFFFF));
				return true;
			}
		}

		int slidersY = paletteY + COLOR_DYE_SIZE * 2 + COLOR_DYE_GAP + 8;
		for (int channel = 0; channel < 4; channel++) {
			if (target.rgbOnly && channel == 3) continue;
			int y = slidersY + channel * 20;
			int sliderX = modal.x() + 8 + 16;
			// Same hit band as the hover check in renderColorSlider (single source).
			if (OreUiRenderer.sliderHitBand(sliderX, y + 2, COLOR_SLIDER_WIDTH).contains(mouseX, mouseY)) {
				draggingColorChannel = channel;
				updateColorSliderFromMouse(mouseX);
				return true;
			}
		}
		// fix 4 (P5-polish-5): click outside the modal confirms (== OK); the live
		// draft already carries the value, so commit just clears the snapshot.
		if (!modal.contains(mouseX, mouseY)) {
			confirmColorPicker();
		}
		return true;
	}

	private boolean handleColorPickerDrag(double mouseX) {
		if (activeColorTarget == null || draggingColorChannel < 0) return false;
		updateColorSliderFromMouse(mouseX);
		return true;
	}

	private void updateColorSliderFromMouse(double mouseX) {
		EditorChrome.Rect modal = colorModalRect();
		int sliderX = modal.x() + 8 + 16;
		int value = (int) Math.round((mouseX - sliderX) * 255.0 / Math.max(1, COLOR_SLIDER_WIDTH));
		setColorPickerChannel(draggingColorChannel, value);
	}

	private boolean handleColorPickerKey(int keyCode, int scanCode, int modifiers) {
		// fix 1: intercept ESC=cancel / ENTER=confirm at the panel level before the
		// EditBox can swallow ENTER (mirrors handleIconPickerKey's ESC-first check).
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			cancelColorPicker();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			confirmColorPicker();
			return true;
		}
		if (colorPickerHexBox != null && colorPickerHexBox.isFocused()) {
			colorPickerHexBox.keyPressed(keyCode, scanCode, modifiers);
		}
		return true;
	}

	private boolean handleColorPickerChar(char codePoint, int modifiers) {
		if (colorPickerHexBox != null && colorPickerHexBox.isFocused()) {
			colorPickerHexBox.charTyped(codePoint, modifiers);
		}
		return true;
	}

	private void applyColorPickerHexIfValid() {
		SettingsColorTarget target = activeColorTarget;
		if (target == null || !isHexInputValid(target)) return;
		String hex = colorPickerHex;
		int parsed = (int) Long.parseUnsignedLong(hex, 16);
		if (target.rgbOnly) {
			applyColorPickerArgb(0xFF000000 | (parsed & 0x00FFFFFF));
		} else if (hex.length() == 6) {
			applyColorPickerArgb((colorPickerArgb & 0xFF000000) | (parsed & 0x00FFFFFF));
		} else {
			applyColorPickerArgb(parsed);
		}
	}

	private boolean isHexChar(char c) {
		return (c >= '0' && c <= '9')
			|| (c >= 'a' && c <= 'f')
			|| (c >= 'A' && c <= 'F');
	}

	// ─────────────────────────────────────────────────────────────────────
	// Icon picker modal (item slot grid, data source = group items)
	// ─────────────────────────────────────────────────────────────────────

	private boolean isIconPickerOpen() {
		return iconPickerOpen;
	}

	private void openIconPicker(boolean back) {
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
		iconPickerSearch.setTextColor(OreUiPalette.TEXT_PRIMARY);
		iconPickerSearch.setResponder(value -> iconPickerScrollOffset = clamp(iconPickerScrollOffset, 0, iconPickerMaxScroll()));
		positionIconPickerSearch();
		iconPickerSearch.setFocused(true);
		scrollIconPickerToSelection();
	}

	private void confirmIconPickerSelection(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		state.setAppearanceDraft(iconPickerTargetBack
			? state.appearanceDraft().withBackIconId(id)
			: state.appearanceDraft().withFrontIconId(id));
		onChanged.run();
		closeIconPicker();
	}

	private void closeIconPicker() {
		iconPickerOpen = false;
		iconPickerScrollOffset = 0;
		iconPickerSearch = null;
	}

	private String selectedIconPickerId() {
		return iconPickerTargetBack ? state.appearanceDraft().backIconId() : state.appearanceDraft().frontIconId();
	}

	private String iconPickerSearchText() {
		return iconPickerSearch == null ? "" : iconPickerSearch.getValue();
	}

	/**
	 * Group items, with the currently selected icon pinned to the front when it
	 * is not otherwise in the group (fix 5). The pinned entry is flagged so the
	 * grid can mark it as a non-group item.
	 */
	private List<PickerEntry> iconPickerEntries() {
		List<PickerEntry> out = new ArrayList<>();
		String query = iconPickerSearchText().trim().toLowerCase(Locale.ROOT);
		String selectedId = selectedIconPickerId();
		ItemStack pinned = iconStack(selectedId);
		boolean selectedInGroup = false;
		List<PickerEntry> body = new ArrayList<>();
		for (ItemStack stack : groupItems.get()) {
			if (stack.isEmpty() || stack.getItem() == Items.AIR) continue;
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			if (id.equals(selectedId)) selectedInGroup = true;
			if (!matchesQuery(stack, id, query)) continue;
			body.add(new PickerEntry(stack, false));
		}
		if (!pinned.isEmpty() && !selectedInGroup) {
			String id = BuiltInRegistries.ITEM.getKey(pinned.getItem()).toString();
			if (matchesQuery(pinned, id, query)) {
				out.add(new PickerEntry(pinned, true));
			}
		}
		out.addAll(body);
		return out;
	}

	private boolean matchesQuery(ItemStack stack, String id, String query) {
		if (query.isEmpty()) return true;
		String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
		return id.toLowerCase(Locale.ROOT).contains(query) || name.contains(query);
	}

	private record PickerEntry(ItemStack stack, boolean nonGroup) {}

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
		iconPickerSearch.setPosition(search.x() + 4, OreUiRenderer.textFieldTextY(font, search.y(), search.height()) + 1);
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
		String selectedId = selectedIconPickerId();
		if (selectedId == null) {
			iconPickerScrollOffset = 0;
			return;
		}
		List<PickerEntry> entries = iconPickerEntries();
		int index = -1;
		for (int i = 0; i < entries.size(); i++) {
			if (BuiltInRegistries.ITEM.getKey(entries.get(i).stack().getItem()).toString().equals(selectedId)) {
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
		OreUiRenderer.drawPanel(g, modal.x(), modal.y(), modal.width(), modal.height());
		OreUiRenderer.drawOutline(g, modal.x(), modal.y(), modal.width(), modal.height(), OreUiPalette.OUTLINE_SELECTED);

		int x = modal.x() + 10;
		int y = modal.y() + 10;
		EditorChrome.Rect close = iconPickerCloseButtonRect();
		g.drawString(font, font.plainSubstrByWidth(
			Component.translatable(ModTranslationKeys.ORE_EDITOR_ICON_PICKER_TITLE).getString(),
			Math.max(0, close.x() - x - 6)),
			x, y, OreUiPalette.TEXT_PRIMARY, false);
		OreUiRenderer.drawButton(g, font, close.x(), close.y(), close.width(), close.height(),
			Component.translatable(ModTranslationKeys.BUTTON_CANCEL).getString(),
			buttonState(true, close.contains(mouseX, mouseY)));

		EditorChrome.Rect search = iconPickerSearchRect();
		boolean searchFocused = iconPickerSearch != null && iconPickerSearch.isFocused();
		boolean searchHovered = search.contains(mouseX, mouseY);
		int outline = searchFocused ? OreUiPalette.OUTLINE_SELECTED
			: searchHovered ? OreUiPalette.OUTLINE_HOVER : OreUiPalette.OUTLINE_DARK;
		g.fill(search.x(), search.y(), search.right(), search.bottom(), OreUiPalette.SURFACE_DARK);
		g.fill(search.x() + 1, search.y() + 1, search.right() - 1, search.bottom() - 1, OreUiPalette.SURFACE);
		OreUiRenderer.drawOutline(g, search.x(), search.y(), search.width(), search.height(), outline);
		if (iconPickerSearch != null) {
			iconPickerSearch.render(g, mouseX, mouseY, 0);
		}

		EditorChrome.Rect grid = iconPickerGridRect();
		List<PickerEntry> entries = iconPickerEntries();
		if (entries.isEmpty()) {
			// fix 10: wrap the empty-state hint inside the grid width instead of
			// clipping it to a single line.
			int lineY = grid.y() + 4;
			for (net.minecraft.util.FormattedCharSequence line : font.split(
					Component.translatable(ModTranslationKeys.ORE_EDITOR_ICON_PICKER_EMPTY), Math.max(1, grid.width()))) {
				g.drawString(font, line, grid.x(), lineY, OreUiPalette.TEXT_HINT, false);
				lineY += font.lineHeight + 1;
			}
			return;
		}

		g.enableScissor(grid.x(), grid.y(), grid.right(), grid.bottom());
		// P5-polish-7: one shared-line grid instead of per-slot outlines (which
		// doubled to 2px between adjacent cells). Scrolls with the cells; the
		// scissor above clips it to the visible window.
		int totalGridRows = (entries.size() + ICON_PICKER_COLS - 1) / ICON_PICKER_COLS;
		OreUiRenderer.drawSlotGrid(g, grid.x(), grid.y() - iconPickerScrollOffset,
			ICON_PICKER_COLS, totalGridRows, ICON_PICKER_SLOT);
		ItemStack hovered = null;
		String selectedId = selectedIconPickerId();
		for (int i = 0; i < entries.size(); i++) {
			PickerEntry entry = entries.get(i);
			int col = i % ICON_PICKER_COLS;
			int row = i / ICON_PICKER_COLS;
			int cx = grid.x() + col * ICON_PICKER_SLOT;
			int cy = grid.y() + row * ICON_PICKER_SLOT - iconPickerScrollOffset;
			if (cy + ICON_PICKER_SLOT < grid.y() || cy > grid.bottom()) continue;
			g.renderItem(entry.stack(), cx + 1, cy + 1);
			String itemId = BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString();
			if (entry.nonGroup()) {
				// Non-group item marker: amber corner tab (top-right) so the "data
				// source = group contents" contract stays visible when pinned.
				g.fill(cx + ICON_PICKER_SLOT - 4, cy + 1, cx + ICON_PICKER_SLOT - 1, cy + 4, 0xFFF2C744);
			}
			if (itemId.equals(selectedId)) {
				OreUiRenderer.drawOutline(g, cx, cy, ICON_PICKER_SLOT, ICON_PICKER_SLOT, OreUiPalette.OUTLINE_SELECTED);
			}
			if (contains(cx, cy, ICON_PICKER_SLOT, ICON_PICKER_SLOT, mouseX, mouseY)) {
				g.fill(cx + 1, cy + 1, cx + ICON_PICKER_SLOT - 1, cy + ICON_PICKER_SLOT - 1, 0x40FFFFFF);
				hovered = entry.stack();
			}
		}
		g.disableScissor();

		int maxScroll = iconPickerMaxScroll();
		if (maxScroll > 0) {
			OreUiRenderer.drawScrollbarPixels(g, grid.right() + 2, grid.y(), grid.height(),
				grid.height(), grid.height() + maxScroll, iconPickerScrollOffset);
		}
		if (hovered != null) {
			g.renderTooltip(font, hovered, mouseX, mouseY);
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
			// fix 4 (P5-polish-5): click outside the modal closes the icon picker.
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
			confirmIconPickerSelection(entries.get(index).stack());
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

	/** @return true if the click was consumed. Priority commit-on-outside-click is caller-driven. */
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (isColorPickerOpen()) {
			if (button == 0) handleColorPickerClick(mouseX, mouseY);
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
				case SWAP -> handleSwapRowClick(row, mouseX, mouseY);
				case COLOR -> handleColorRowClick(row, mouseX, mouseY);
				case PRIORITY -> handlePriorityRowClick(row, mouseX, mouseY);
				case ID -> handleIdRowClick(row, mouseX, mouseY);
				case ENABLED -> handleEnabledRowClick(row, mouseX, mouseY);
				case SUBHEADER -> {
				}
			}
			return true;
		}
		return true;
	}

	private void handleIconRowClick(SettingsRowLayout.Row row, double mouseX, double mouseY) {
		boolean back = (Boolean) row.payload();
		if (row.changeBtn().contains(mouseX, mouseY) || row.slot().contains(mouseX, mouseY)) {
			// Clicking the slot opens the picker too; back-locked is guarded inside
			// openIconPicker's early return.
			openIconPicker(back);
			return;
		}
		if (row.clearBtn().contains(mouseX, mouseY)) {
			String iconId = back ? state.appearanceDraft().backIconId() : state.appearanceDraft().frontIconId();
			if (iconId != null) {
				state.setAppearanceDraft(back ? state.appearanceDraft().clearBackIcon() : state.appearanceDraft().clearFrontIcon());
				onChanged.run();
			}
		}
	}

	private void handleSwapRowClick(SettingsRowLayout.Row row, double mouseX, double mouseY) {
		boolean enabled = state.appearanceDraft().frontIconId() != null && state.appearanceDraft().backIconId() != null;
		if (enabled && row.swapBtn().contains(mouseX, mouseY)) {
			state.setAppearanceDraft(state.appearanceDraft().swapIcons());
			onChanged.run();
		}
	}

	private void handleColorRowClick(SettingsRowLayout.Row row, double mouseX, double mouseY) {
		SettingsColorTarget target = (SettingsColorTarget) row.payload();
		if (row.resetBtn().contains(mouseX, mouseY)) {
			if (colorValue(target) != null) {
				state.setAppearanceDraft(withColor(target, null));
				onChanged.run();
			}
			return;
		}
		if (row.pickerBtn().contains(mouseX, mouseY) || row.swatch().contains(mouseX, mouseY)
			|| row.hexBox().contains(mouseX, mouseY)) {
			openColorPicker(target);
		}
	}

	private void handlePriorityRowClick(SettingsRowLayout.Row row, double mouseX, double mouseY) {
		int step = Screen.hasShiftDown() ? 10 : 1;
		if (row.valueBox().contains(mouseX, mouseY)) {
			beginPriorityEdit();
		} else if (row.stepMinus().contains(mouseX, mouseY)) {
			commitPriorityEdit();
			state.setEditPriority(state.editPriority() - step);
			onChanged.run();
		} else if (row.stepPlus().contains(mouseX, mouseY)) {
			commitPriorityEdit();
			state.setEditPriority(state.editPriority() + step);
			onChanged.run();
		}
	}

	private void handleIdRowClick(SettingsRowLayout.Row row, double mouseX, double mouseY) {
		String rawId = state.pendingRawId();
		if (!rawId.isBlank() && row.copyBtn().contains(mouseX, mouseY)) {
			Minecraft.getInstance().keyboardHandler.setClipboard(rawId);
		}
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
		if (isColorPickerOpen()) {
			if (button == 0) draggingColorChannel = -1;
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
			handleColorPickerDrag(mouseX);
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

	/** Wheel over the whole editor panel scrolls the settings list (fix 6+8d). */
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
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
		if (isColorPickerOpen()) {
			return handleColorPickerKey(keyCode, scanCode, modifiers);
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
			return handleColorPickerChar(codePoint, modifiers);
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
		clampScroll();
		scrollbarDragging = false;
		clearSwitchHoverSuppression();
		if (iconPickerOpen) {
			positionIconPickerSearch();
		}
		if (colorPickerHexBox != null) {
			positionColorPickerHexBox();
		}
	}

	private static boolean contains(int x, int y, int w, int h, double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private OreUiRenderer.ButtonState buttonState(boolean enabled, boolean hovered) {
		if (!enabled) return OreUiRenderer.ButtonState.DISABLED;
		return hovered ? OreUiRenderer.ButtonState.HOVERED : OreUiRenderer.ButtonState.NORMAL;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
