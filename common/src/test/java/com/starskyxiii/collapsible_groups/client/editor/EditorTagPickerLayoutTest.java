package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.widget.EditorChrome.Rect;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditorTagPickerLayoutTest {
	@Test void translatedButtonsFitWideAndNarrowViewportsWithoutOverlap() {
		for (int width : List.of(200, 280, 360)) {
			for (int manualWidth : List.of(90, 125, 250)) {
				var bounds = new Rect(10, 10, width, 228);
				var layout = EditorTagPickerLayout.create(bounds, 40, manualWidth, 45);
				var buttons = List.of(layout.cancel(), layout.manual(), layout.confirm());
				for (var button : buttons) {
					assertTrue(button.x() >= bounds.x() + 6);
					assertTrue(button.right() <= bounds.right() - 6);
					assertTrue(button.y() >= layout.top());
					assertTrue(button.bottom() <= bounds.bottom() - 6);
				}
				for (int i = 0; i < buttons.size(); i++) for (int j = i + 1; j < buttons.size(); j++) {
					var a = buttons.get(i);
					var b = buttons.get(j);
					assertTrue(a.right() <= b.x() || b.right() <= a.x() || a.bottom() <= b.y() || b.bottom() <= a.y());
				}
			}
		}
	}
}
