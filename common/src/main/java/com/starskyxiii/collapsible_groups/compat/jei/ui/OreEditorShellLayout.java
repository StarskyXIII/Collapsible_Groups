package com.starskyxiii.collapsible_groups.compat.jei.ui;

import org.jetbrains.annotations.Nullable;

public record OreEditorShellLayout(
	Rect header,
	Rect footer,
	Rect editorPanel,
	Rect previewPanel,
	Rect actionTitle,
	Rect headerPreview,
	Rect nameField,
	Rect saveButton,
	Rect cancelButton,
	@Nullable Rect disableSourceCheckbox,
	@Nullable Rect disableSourceLabel,
	Rect modeSegmentRow,
	Rect contentTitle,
	Rect contentTypeRow,
	Rect searchField,
	Rect hideUsedButton,
	Rect contentCount,
	Rect previewTitle,
	Rect previewHeader,
	Rect previewBody,
	Rect footerStatus,
	Rect footerHint,
	EditorLayout panelLayout,
	EditorLayout previewLayout
) {
	public static final int HEADER_HEIGHT = 75;
	public static final int FOOTER_HEIGHT = 28;
	public static final int OUTER_MARGIN = 6;
	public static final int GAP = 6;
	public static final int ACTION_BUTTON_WIDTH = 56;
	public static final int ACTION_BUTTON_HEIGHT = 20;
	public static final int SEGMENT_HEIGHT = 18;
	public static final int PANEL_INSET = 8;
	public static final int HEADER_PREVIEW_MIN_WIDTH = 80;
	public static final int HEADER_PREVIEW_MAX_WIDTH = 150;
	public static final int HEADER_PREVIEW_NAME_GAP = 4;
	public static final int HEADER_NAME_MIN_WIDTH = 54;
	public static final int HEADER_NAME_MAX_WIDTH = 180;
	public static final int MODE_SEGMENT_MAX_WIDTH = 210;
	public static final int CONTENT_TITLE_HEIGHT = 12;
	public static final int CONTENT_COUNT_HEIGHT = 12;
	public static final int PREVIEW_MIN_WIDTH = 132;
	public static final int PREVIEW_MAX_WIDTH = 190;
	public static final int PREVIEW_HEADER_HEIGHT = 22;
	public static final int FIRST_ROW_Y = 5;
	public static final int SECOND_ROW_Y = 28;
	public static final int THIRD_ROW_Y = 51;
	public static final int DISABLE_SOURCE_CHECKBOX_SIZE = 14;
	private static final int DISABLE_SOURCE_LABEL_GAP = 4;
	private static final int PREVIEW_HEADER_TOP_GAP = 3;
	private static final int PREVIEW_HEADER_BOTTOM_GAP = 10;

	public static OreEditorShellLayout compute(int screenWidth, int screenHeight) {
		return compute(screenWidth, screenHeight, false, 0);
	}

	public static OreEditorShellLayout compute(int screenWidth, int screenHeight, boolean showDisableSourceOption) {
		return compute(screenWidth, screenHeight, showDisableSourceOption, 0);
	}

	public static OreEditorShellLayout compute(
		int screenWidth,
		int screenHeight,
		boolean showDisableSourceOption,
		int disableSourceLabelWidth
	) {
		int footerY = Math.max(HEADER_HEIGHT + 96, screenHeight - FOOTER_HEIGHT);
		Rect header = new Rect(0, 0, screenWidth, HEADER_HEIGHT);
		Rect footer = new Rect(0, footerY, screenWidth, Math.max(0, screenHeight - footerY));

		int bodyY = HEADER_HEIGHT + GAP;
		int bodyHeight = Math.max(72, footerY - bodyY - GAP);

		int available = Math.max(180, screenWidth - OUTER_MARGIN * 2 - GAP);
		int previewWidth = clamp(screenWidth * 22 / 100, PREVIEW_MIN_WIDTH, PREVIEW_MAX_WIDTH);
		if (available - previewWidth < 170) {
			previewWidth = Math.max(PREVIEW_MIN_WIDTH, available - 170);
		}
		int editorWidth = Math.max(120, available - previewWidth);
		Rect editorPanel = new Rect(OUTER_MARGIN, bodyY, editorWidth, bodyHeight);
		Rect previewPanel = new Rect(editorPanel.right() + GAP, bodyY, previewWidth, bodyHeight);

		Rect cancelButton = new Rect(screenWidth - OUTER_MARGIN - ACTION_BUTTON_WIDTH, FIRST_ROW_Y,
			ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
		Rect saveButton = new Rect(cancelButton.x() - GAP - ACTION_BUTTON_WIDTH, FIRST_ROW_Y,
			ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
		Rect actionTitle = new Rect(OUTER_MARGIN, FIRST_ROW_Y,
			Math.max(1, saveButton.x() - OUTER_MARGIN - GAP), ACTION_BUTTON_HEIGHT);

		int rowX = OUTER_MARGIN;
		int rowRight = screenWidth - OUTER_MARGIN;
		int headerPreviewWidth = PREVIEW_HEADER_HEIGHT;
		Rect headerPreview = new Rect(rowX, SECOND_ROW_Y - 1, headerPreviewWidth, PREVIEW_HEADER_HEIGHT);
		int nameX = headerPreview.right() + HEADER_PREVIEW_NAME_GAP;
		int nameAvailable = rowRight - nameX;
		int nameWidth = Math.max(1, Math.min(HEADER_NAME_MAX_WIDTH, nameAvailable));
		Rect nameField = new Rect(nameX, SECOND_ROW_Y, nameWidth, ACTION_BUTTON_HEIGHT);

		int modeMaxWidth = Math.max(1, Math.min(MODE_SEGMENT_MAX_WIDTH, screenWidth - OUTER_MARGIN * 2));
		int modeWidth = modeMaxWidth;
		if (showDisableSourceOption) {
			int minOptionWidth = DISABLE_SOURCE_CHECKBOX_SIZE + DISABLE_SOURCE_LABEL_GAP + 1;
			int copyModeWidth = rowRight - OUTER_MARGIN - GAP - minOptionWidth;
			modeWidth = Math.max(1, Math.min(modeMaxWidth, copyModeWidth));
		}
		Rect modeSegmentRow = new Rect(OUTER_MARGIN, THIRD_ROW_Y, modeWidth, SEGMENT_HEIGHT);

		Rect disableSourceCheckbox = null;
		Rect disableSourceLabel = null;
		if (showDisableSourceOption) {
			int maxLabelWidth = Math.max(1, rowRight - modeSegmentRow.right() - GAP
				- DISABLE_SOURCE_CHECKBOX_SIZE - DISABLE_SOURCE_LABEL_GAP);
			int labelWidth = clamp(disableSourceLabelWidth, 1, maxLabelWidth);
			int optionWidth = DISABLE_SOURCE_CHECKBOX_SIZE + DISABLE_SOURCE_LABEL_GAP + labelWidth;
			int optionX = rowRight - optionWidth;
			int checkboxY = THIRD_ROW_Y + Math.max(0, (SEGMENT_HEIGHT - DISABLE_SOURCE_CHECKBOX_SIZE) / 2);
			disableSourceCheckbox = new Rect(optionX, checkboxY,
				DISABLE_SOURCE_CHECKBOX_SIZE, DISABLE_SOURCE_CHECKBOX_SIZE);
			disableSourceLabel = new Rect(disableSourceCheckbox.right() + DISABLE_SOURCE_LABEL_GAP,
				THIRD_ROW_Y, labelWidth, SEGMENT_HEIGHT);
		}

		Rect contentTitle = new Rect(editorPanel.x() + PANEL_INSET, editorPanel.y() + PANEL_INSET,
			Math.max(1, editorPanel.width() - PANEL_INSET * 2), CONTENT_TITLE_HEIGHT);
		int contentY = editorPanel.y() + PANEL_INSET;
		Rect contentTypeRow = new Rect(editorPanel.x() + PANEL_INSET, contentY,
			Math.max(1, editorPanel.width() - PANEL_INSET * 2), SEGMENT_HEIGHT);
		int controlY = contentTypeRow.bottom() + 8;
		int hideUsedWidth = 64;
		Rect hideUsedButton = new Rect(editorPanel.right() - PANEL_INSET - hideUsedWidth, controlY,
			hideUsedWidth, SEGMENT_HEIGHT);
		Rect searchField = new Rect(editorPanel.x() + PANEL_INSET, controlY,
			Math.max(48, hideUsedButton.x() - editorPanel.x() - PANEL_INSET - GAP), SEGMENT_HEIGHT);
		Rect contentCount = new Rect(editorPanel.x() + PANEL_INSET, searchField.bottom() + 8,
			Math.max(1, editorPanel.width() - PANEL_INSET * 2), CONTENT_COUNT_HEIGHT);
		Rect previewTitle = new Rect(previewPanel.x() + PANEL_INSET, previewPanel.y() + PANEL_INSET,
			Math.max(1, previewPanel.width() - PANEL_INSET * 2), CONTENT_TITLE_HEIGHT);
		int sourceGridTop = contentCount.bottom() + 7;
		Rect previewHeader = new Rect(previewTitle.x(), previewTitle.bottom() + PREVIEW_HEADER_TOP_GAP,
			previewTitle.width(), PREVIEW_HEADER_HEIGHT);
		int previewGridTop = previewHeader.bottom() + PREVIEW_HEADER_BOTTOM_GAP;
		Rect previewBody = new Rect(previewPanel.x() + PANEL_INSET, previewGridTop,
			Math.max(1, previewPanel.width() - PANEL_INSET * 2),
			Math.max(EditorLayout.ITEM_SIZE, previewPanel.bottom() - previewGridTop - PANEL_INSET));
		int footerHeight = Math.max(0, screenHeight - footerY);
		int footerHintWidth = Math.min(220, Math.max(96, screenWidth / 3));
		Rect footerHint = new Rect(screenWidth - OUTER_MARGIN - footerHintWidth, footerY, footerHintWidth, footerHeight);
		Rect footerStatus = new Rect(OUTER_MARGIN, footerY, Math.max(1, footerHint.x() - OUTER_MARGIN - GAP),
			footerHeight);

		EditorLayout panelLayout = panelLayout(editorPanel, previewPanel, sourceGridTop);
		EditorLayout previewLayout = panelLayout(editorPanel, previewPanel, previewBody.y());
		return new OreEditorShellLayout(header, footer, editorPanel, previewPanel, actionTitle, headerPreview,
			nameField, saveButton, cancelButton, disableSourceCheckbox, disableSourceLabel, modeSegmentRow,
			contentTitle, contentTypeRow, searchField, hideUsedButton, contentCount, previewTitle, previewHeader,
			previewBody, footerStatus, footerHint, panelLayout, previewLayout);
	}

	private static EditorLayout panelLayout(Rect editorPanel, Rect previewPanel, int gridTop) {
		int gridHeight = Math.max(EditorLayout.ITEM_SIZE, editorPanel.bottom() - gridTop - PANEL_INSET);
		int leftGridX = editorPanel.x() + PANEL_INSET;
		int rightGridX = previewPanel.x() + PANEL_INSET;
		int leftGridWidth = Math.max(EditorLayout.ITEM_SIZE,
			editorPanel.width() - PANEL_INSET * 2 - ScrollbarHelper.WIDTH - ScrollbarHelper.GAP);
		int rightGridWidth = Math.max(EditorLayout.ITEM_SIZE,
			previewPanel.width() - PANEL_INSET * 2 - ScrollbarHelper.WIDTH - ScrollbarHelper.GAP);
		int leftCols = Math.max(1, leftGridWidth / EditorLayout.ITEM_SIZE);
		int rightCols = Math.max(1, rightGridWidth / EditorLayout.ITEM_SIZE);
		int rows = Math.max(1, gridHeight / EditorLayout.ITEM_SIZE);
		int leftScrollbarX = leftGridX + leftGridWidth + ScrollbarHelper.GAP;
		int rightScrollbarX = rightGridX + rightGridWidth + ScrollbarHelper.GAP;
		return new EditorLayout(previewPanel.x() - GAP / 2, leftGridX, rightGridX, leftGridWidth, rightGridWidth,
			gridTop, gridHeight, leftCols, rows, rightCols, rows, leftScrollbarX, rightScrollbarX);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public record Rect(int x, int y, int width, int height) {
		public int right() {
			return x + width;
		}

		public int bottom() {
			return y + height;
		}

		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
		}
	}
}
