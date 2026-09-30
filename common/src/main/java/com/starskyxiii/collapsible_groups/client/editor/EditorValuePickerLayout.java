package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.widget.EditorChrome.Rect;

record EditorValuePickerLayout(Rect cancel, Rect manual, Rect confirm) {
	static EditorValuePickerLayout create(Rect bounds, int cancelWidth, int manualWidth, int confirmWidth) {
		int available = bounds.width() - 12;
		cancelWidth = Math.min(Math.max(52, cancelWidth + 16), (available - 6) / 2);
		confirmWidth = Math.min(Math.max(52, confirmWidth + 16), (available - 6) / 2);
		manualWidth = Math.min(Math.max(52, manualWidth + 16), available);
		int y = bounds.bottom() - 26;
		Rect confirm = new Rect(bounds.right() - 6 - confirmWidth, y, confirmWidth, 20);
		Rect cancel = new Rect(confirm.x() - 6 - cancelWidth, y, cancelWidth, 20);
		boolean wrap = cancelWidth + manualWidth + confirmWidth + 12 > available;
		Rect manual = new Rect(bounds.x() + 6, wrap ? y - 24 : y, manualWidth, 20);
		return new EditorValuePickerLayout(cancel, manual, confirm);
	}
	int top() { return Math.min(cancel.y(), manual.y()); }
}
