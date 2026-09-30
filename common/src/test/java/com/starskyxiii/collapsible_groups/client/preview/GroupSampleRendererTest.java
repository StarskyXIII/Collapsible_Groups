package com.starskyxiii.collapsible_groups.client.preview;

import com.starskyxiii.collapsible_groups.group.GroupTheme;
import com.starskyxiii.collapsible_groups.group.GroupThemeColors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupSampleRendererTest {
	@Test
	void slotPitchMatchesLiveViewerSpacing() {
		// Must equal GroupBorderRenderer's SPACING (18) so preview borders line up.
		assertEquals(18, GroupSampleRenderer.SLOT_PITCH);
	}

	@Test
	void resolvesColorsThroughSharedThemeColors() {
		// The renderer must resolve draft colors via GroupThemeColors against the
		// caller fallbacks (not GroupThemeResolver), so a null theme yields the
		// fallback and an override yields the override.
		GroupTheme empty = GroupTheme.EMPTY;
		int fallbackBorder = 0x884CAF50;
		assertEquals(fallbackBorder, GroupThemeColors.expandedGroupBorder(empty, fallbackBorder));

		GroupTheme override = new GroupTheme(null, null, null, null, "#FF112233");
		assertEquals(0xFF112233, GroupThemeColors.expandedGroupBorder(override, fallbackBorder));
	}

	@Test
	void nameColorForcesOpaqueAlphaLikeRenderer() {
		// Renderer applies 0xFF000000 | nameColor; verify the RGB fallback path.
		GroupTheme empty = GroupTheme.EMPTY;
		int fallbackRgb = 0xFFD166;
		int resolved = 0xFF000000 | GroupThemeColors.nameColor(empty, fallbackRgb);
		assertEquals(0xFFFFD166, resolved);
	}

	@Test
	void layoutStartsAtAreaOriginWithHeaderAsFirstCell() {
		GroupSampleRenderer.Rect area = new GroupSampleRenderer.Rect(30, 40, 72, 54);

		GroupSampleRenderer.Layout layout = GroupSampleRenderer.layout(area, true, 3, 0);

		assertEquals(new GroupSampleRenderer.Rect(30, 40, 16, 16), layout.headerCell());
		assertEquals(4, layout.cells().size(), "header plus three visible item cells");
		assertEquals(new GroupSampleRenderer.Rect(48, 40, 16, 16), layout.cells().get(1).rect());
	}

	@Test
	void pageButtonsSplitToBottomCornersLikeRealViewer() {
		// ◀ is pinned to the area's bottom-left and ▶ to the bottom-right, so the
		// page counter can sit centered between them.
		GroupSampleRenderer.Rect area = new GroupSampleRenderer.Rect(0, 0, 36, 60);
		GroupSampleRenderer.Layout layout = GroupSampleRenderer.layout(area, true, 5, 0);

		assertTrue(layout.hasPages(), "5 items over a 36x60 area must paginate");
		GroupSampleRenderer.Rect prev = layout.previousPageButton();
		GroupSampleRenderer.Rect next = layout.nextPageButton();
		assertEquals(area.x(), prev.x(), "previous button hugs the left edge");
		assertEquals(area.right(), next.right(), "next button hugs the right edge");
		assertTrue(prev.x() < next.x(), "previous is left of next");
		assertEquals(prev.y(), next.y(), "both page buttons share the bottom row");
	}

	@Test
	void layoutPaginatesChildrenAfterPinnedHeaderCell() {
		GroupSampleRenderer.Rect area = new GroupSampleRenderer.Rect(0, 0, 36, 60);

		GroupSampleRenderer.Layout first = GroupSampleRenderer.layout(area, true, 5, 0);
		GroupSampleRenderer.Layout second = GroupSampleRenderer.layout(area, true, 5, 1);

		assertEquals(3, first.childCapacity());
		assertEquals(2, first.pageCount());
		assertEquals(0, first.cells().get(1).itemIndex());
		assertEquals(3, second.cells().get(1).itemIndex());
		assertEquals(new GroupSampleRenderer.Rect(0, 0, 16, 16), second.headerCell());
	}

	// the preview hover tooltip picks a cell by containment.
	// Mirror that hit-test against the layout cells (header / item / none).

	@Test
	void cellHitTestClassifiesHeaderItemAndMiss() {
		GroupSampleRenderer.Rect area = new GroupSampleRenderer.Rect(30, 40, 72, 54);
		GroupSampleRenderer.Layout layout = GroupSampleRenderer.layout(area, true, 3, 0);

		// Header cell centre (30..46, 40..56).
		assertEquals(CellHit.HEADER, hitKind(layout, 34, 44));
		// First item cell centre (48..64, 40..56).
		GroupSampleRenderer.Cell item = layout.cells().get(1);
		assertEquals(0, item.itemIndex());
		assertEquals(CellHit.ITEM, hitKind(layout, item.rect().x() + 2, item.rect().y() + 2));
		// Gap between the 16px cell and the 18px pitch (x == 46 is past the header cell).
		assertEquals(CellHit.NONE, hitKind(layout, 47, 44));
	}

	private enum CellHit { HEADER, ITEM, NONE }

	private static CellHit hitKind(GroupSampleRenderer.Layout layout, double mouseX, double mouseY) {
		for (GroupSampleRenderer.Cell cell : layout.cells()) {
			if (cell.rect().contains(mouseX, mouseY)) {
				return cell.header() ? CellHit.HEADER : CellHit.ITEM;
			}
		}
		return CellHit.NONE;
	}
}
