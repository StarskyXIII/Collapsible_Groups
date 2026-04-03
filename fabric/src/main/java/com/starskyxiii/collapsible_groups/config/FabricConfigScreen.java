package com.starskyxiii.collapsible_groups.config;

import com.starskyxiii.collapsible_groups.CollapsibleGroupsFabric;
import com.starskyxiii.collapsible_groups.compat.jei.ui.ScrollbarHelper;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Simple in-game settings screen for the Fabric loader.
 * Opens via Mod Menu; saves to
 * {@code config/collapsiblegroups/collapsiblegroups.json} and reloads groups on Save.
 * <p>
 * The option area is scrollable so the layout works on small windows.
 */
public class FabricConfigScreen extends Screen {

	private static final int BTN_W         = 220;
	private static final int ROW_H         = 20;
	private static final int ROW_GAP       = 2;
	private static final int SEC_GAP       = 8;
	private static final int SEC_H         = 11;
	private static final int ACTION_BTN_W  = 100;
	private static final int ACTION_BTN_H  = 20;
	private static final int HEADER_H      = 24;
	private static final int FOOTER_H      = 30;
	private static final int SCROLL_STEP   = 20;

	private final Screen parent;

	// Mutable copy of settings being edited (snapshotted once on first init())
	private boolean initialized;
	private boolean defaultGroupsEnabled;
	private boolean loadGeneric;
	private boolean loadVanilla;
	private boolean loadModIntegration;
	private boolean loadChipped;
	private boolean loadRechiseled;
	private boolean loadRS2;
	private boolean showManagerButton;
	private boolean timingLogs;
	private boolean verifyStartupIndex;
	private boolean verifyEditorIndex;

	// Scrollable content area
	private int scrollOffset;
	private int contentHeight;

	// Section headers: content-relative Y positions + labels
	private final List<Integer>   sectionContentY = new ArrayList<>();
	private final List<Component> sectionLabel     = new ArrayList<>();

	// Toggle buttons with their content-relative Y positions
	private final List<Button>  toggleButtons  = new ArrayList<>();
	private final List<Integer> toggleContentY = new ArrayList<>();

	// Footer buttons (Save / Cancel) — rendered outside the scissor region
	private Button saveButton;
	private Button cancelButton;

	public FabricConfigScreen(Screen parent) {
		super(Component.translatable(ModTranslationKeys.CONFIG_SCREEN_TITLE));
		this.parent = parent;
	}

	@Override
	protected void init() {
		sectionContentY.clear();
		sectionLabel.clear();
		toggleButtons.clear();
		toggleContentY.clear();

		// Snapshot current config into edit fields only on first open;
		// subsequent init() calls (e.g. window resize) preserve in-progress edits.
		if (!initialized) {
			FabricConfig.FabricConfigData d = FabricConfig.getData();
			defaultGroupsEnabled = d.defaultGroups.enabled;
			loadGeneric          = d.defaultGroups.loadGeneric;
			loadVanilla          = d.defaultGroups.loadVanilla;
			loadModIntegration   = d.defaultGroups.modIntegration.loadModIntegration;
			loadChipped          = d.defaultGroups.modIntegration.loadChipped;
			loadRechiseled       = d.defaultGroups.modIntegration.loadRechiseled;
			loadRS2              = d.defaultGroups.modIntegration.loadRS2;
			showManagerButton    = d.ui.showManagerButton;
			timingLogs           = d.debug.enableTimingLogs;
			verifyStartupIndex   = d.debug.verifyStartupIndex;
			verifyEditorIndex    = d.debug.verifyEditorPreviewIndex;
			initialized          = true;
		}

		int cx = this.width / 2;

		// All content Y positions are relative to the top of the scrollable area (0-based).
		int y = 0;

		// ── Default Groups ──
		recordSection(y, ModTranslationKeys.CONFIG_SECTION_DEFAULT_GROUPS);
		y += SEC_H + ROW_GAP;

		addToggle(cx, y, ModTranslationKeys.CONFIG_OPT_DEFAULT_GROUPS_ENABLED,
			() -> defaultGroupsEnabled, v -> defaultGroupsEnabled = v); y += ROW_H + ROW_GAP;
		addToggle(cx, y, ModTranslationKeys.CONFIG_OPT_LOAD_GENERIC,
			() -> loadGeneric, v -> loadGeneric = v);                  y += ROW_H + ROW_GAP;
		addToggle(cx, y, ModTranslationKeys.CONFIG_OPT_LOAD_VANILLA,
			() -> loadVanilla, v -> loadVanilla = v);                  y += ROW_H + ROW_GAP;

		// ── Mod Integration ──
		y += SEC_GAP;
		recordSection(y, ModTranslationKeys.CONFIG_SECTION_MOD_INTEGRATION);
		y += SEC_H + ROW_GAP;

		addToggle(cx, y, ModTranslationKeys.CONFIG_OPT_LOAD_MOD_INTEGRATION,
			() -> loadModIntegration, v -> loadModIntegration = v);    y += ROW_H + ROW_GAP;
		addToggle(cx, y, ModTranslationKeys.CONFIG_OPT_LOAD_CHIPPED,
			() -> loadChipped, v -> loadChipped = v);                  y += ROW_H + ROW_GAP;
		addToggle(cx, y, ModTranslationKeys.CONFIG_OPT_LOAD_RECHISELED,
			() -> loadRechiseled, v -> loadRechiseled = v);            y += ROW_H + ROW_GAP;
		addToggle(cx, y, ModTranslationKeys.CONFIG_OPT_LOAD_RS2,
			() -> loadRS2, v -> loadRS2 = v);                          y += ROW_H + ROW_GAP;

		// ── UI ──
		y += SEC_GAP;
		recordSection(y, ModTranslationKeys.CONFIG_SECTION_UI);
		y += SEC_H + ROW_GAP;

		addToggle(cx, y, ModTranslationKeys.CONFIG_OPT_SHOW_MANAGER_BUTTON,
			() -> showManagerButton, v -> showManagerButton = v);      y += ROW_H + ROW_GAP;

		// ── Debug ──
		y += SEC_GAP;
		recordSection(y, ModTranslationKeys.CONFIG_SECTION_DEBUG);
		y += SEC_H + ROW_GAP;

		addToggle(cx, y, ModTranslationKeys.CONFIG_OPT_TIMING_LOGS,
			() -> timingLogs, v -> timingLogs = v);                    y += ROW_H + ROW_GAP;
		addToggle(cx, y, ModTranslationKeys.CONFIG_OPT_VERIFY_STARTUP_INDEX,
			() -> verifyStartupIndex, v -> verifyStartupIndex = v);    y += ROW_H + ROW_GAP;
		addToggle(cx, y, ModTranslationKeys.CONFIG_OPT_VERIFY_EDITOR_INDEX,
			() -> verifyEditorIndex, v -> verifyEditorIndex = v);      y += ROW_H + ROW_GAP;

		contentHeight = y;

		// Clamp scroll after resize
		int maxScroll = maxScrollOffset();
		if (scrollOffset > maxScroll) scrollOffset = maxScroll;

		// ── Save / Cancel (pinned to bottom) ──
		int footerY = this.height - FOOTER_H + (FOOTER_H - ACTION_BTN_H) / 2;
		saveButton = this.addRenderableWidget(Button.builder(
				Component.translatable(ModTranslationKeys.BUTTON_SAVE), b -> onSave())
			.bounds(cx - ACTION_BTN_W - 2, footerY, ACTION_BTN_W, ACTION_BTN_H)
			.build());
		cancelButton = this.addRenderableWidget(Button.builder(
				Component.translatable(ModTranslationKeys.BUTTON_CANCEL), b -> this.onClose())
			.bounds(cx + 2, footerY, ACTION_BTN_W, ACTION_BTN_H)
			.build());
	}

	// ── Viewport helpers ─────────────────────────────────────────────────────

	private int viewportTop()    { return HEADER_H; }
	private int viewportBottom() { return this.height - FOOTER_H; }
	private int viewportHeight() { return viewportBottom() - viewportTop(); }
	private int maxScrollOffset() { return Math.max(0, contentHeight - viewportHeight()); }

	// ── Widget helpers ───────────────────────────────────────────────────────

	private void recordSection(int contentY, String key) {
		sectionContentY.add(contentY);
		sectionLabel.add(Component.translatable(key).withStyle(ChatFormatting.YELLOW));
	}

	private void addToggle(int cx, int contentY, String labelKey, BooleanSupplier getter, Consumer<Boolean> setter) {
		Button button = Button.builder(
				toggleLabel(labelKey, getter.getAsBoolean()),
				b -> {
					boolean next = !getter.getAsBoolean();
					setter.accept(next);
					b.setMessage(toggleLabel(labelKey, next));
				})
			.bounds(cx - BTN_W / 2, 0, BTN_W, ROW_H)
			.build();
		this.addRenderableWidget(button);
		toggleButtons.add(button);
		toggleContentY.add(contentY);
	}

	/** Repositions toggle buttons based on current scroll offset and toggles visibility. */
	private void updateTogglePositions() {
		int vpTop = viewportTop();
		int vpBot = viewportBottom();
		for (int i = 0; i < toggleButtons.size(); i++) {
			Button btn = toggleButtons.get(i);
			int screenY = vpTop + toggleContentY.get(i) - scrollOffset;
			btn.setY(screenY);
			btn.visible = (screenY + ROW_H > vpTop) && (screenY < vpBot);
			btn.active  = btn.visible;
		}
	}

	private static Component toggleLabel(String labelKey, boolean value) {
		MutableComponent label = Component.translatable(labelKey);
		Component val = Component.translatable(
				value ? ModTranslationKeys.CONFIG_VAL_ON : ModTranslationKeys.CONFIG_VAL_OFF)
			.withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
		return label.append(Component.literal(": ")).append(val);
	}

	// ── Save ─────────────────────────────────────────────────────────────────

	private void onSave() {
		FabricConfig.FabricConfigData newData = new FabricConfig.FabricConfigData();
		newData.defaultGroups.enabled                              = defaultGroupsEnabled;
		newData.defaultGroups.loadGeneric                          = loadGeneric;
		newData.defaultGroups.loadVanilla                          = loadVanilla;
		newData.defaultGroups.modIntegration.loadModIntegration    = loadModIntegration;
		newData.defaultGroups.modIntegration.loadChipped           = loadChipped;
		newData.defaultGroups.modIntegration.loadRechiseled        = loadRechiseled;
		newData.defaultGroups.modIntegration.loadRS2               = loadRS2;
		newData.ui.showManagerButton                               = showManagerButton;
		newData.debug.enableTimingLogs         = timingLogs;
		newData.debug.verifyStartupIndex       = verifyStartupIndex;
		newData.debug.verifyEditorPreviewIndex = verifyEditorIndex;

		FabricConfig.save(newData);
		CollapsibleGroupsFabric.reloadGroupsFromCurrentConfig();
		this.onClose();
	}

	// ── Scrolling ────────────────────────────────────────────────────────────

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseY >= viewportTop() && mouseY < viewportBottom()) {
			scrollOffset = ScrollbarHelper.clamp(
				scrollOffset - (int) (verticalAmount * SCROLL_STEP),
				0, maxScrollOffset());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// ── Rendering ────────────────────────────────────────────────────────────

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		this.extractTransparentBackground(g);

		int cx = this.width / 2;
		int vpTop = viewportTop();
		int vpBot = viewportBottom();

		// Title (fixed, above viewport)
		g.centeredText(this.font, this.title, cx, 8, 0xFFFFFFFF);

		// Update toggle button screen positions before rendering
		updateTogglePositions();

		// ── Scrollable content area ──
		g.enableScissor(0, vpTop, this.width, vpBot);

		// Section headers (rendered manually, not widgets)
		for (int i = 0; i < sectionContentY.size(); i++) {
			int screenY = vpTop + sectionContentY.get(i) - scrollOffset;
			if (screenY + SEC_H > vpTop && screenY < vpBot) {
				g.centeredText(this.font, sectionLabel.get(i), cx, screenY, 0xFFFFFFFF);
			}
		}

		// Toggle buttons only (visible ones will render inside scissor)
		for (Button btn : toggleButtons) {
			btn.extractRenderState(g, mx, my, pt);
		}

		g.disableScissor();

		// ── Footer buttons (outside scissor) ──
		saveButton.extractRenderState(g, mx, my, pt);
		cancelButton.extractRenderState(g, mx, my, pt);

		// ── Scrollbar ──
		if (contentHeight > viewportHeight()) {
			int sbX = cx + BTN_W / 2 + ScrollbarHelper.GAP + 2;
			ScrollbarHelper.renderPixels(g, sbX, vpTop, viewportHeight(),
				viewportHeight(), contentHeight, scrollOffset);
		}
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}
}
