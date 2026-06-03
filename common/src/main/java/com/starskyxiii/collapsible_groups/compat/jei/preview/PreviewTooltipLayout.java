package com.starskyxiii.collapsible_groups.compat.jei.preview;

record PreviewTooltipLayout(
	int entryCount,
	int lineCount,
	int maxPerLine,
	int drawColumns,
	int drawCount,
	int overflowDisplayCount
) {
	static PreviewTooltipLayout compute(int entryCount, int maxPerLine, int maxLines) {
		int safeEntryCount = Math.max(0, entryCount);
		if (maxPerLine <= 0 || maxLines <= 0) {
			return new PreviewTooltipLayout(safeEntryCount, 0, 0, 0, 0, 0);
		}

		int maxIngredients = maxPerLine * maxLines;
		int lineCount = Math.min(divideCeil(safeEntryCount, maxPerLine), maxLines);
		int layoutMaxPerLine = Math.min(divideCeil(safeEntryCount, lineCount), maxPerLine);
		boolean hasOverflow = safeEntryCount > maxIngredients;
		int drawCount = hasOverflow ? Math.max(0, maxIngredients - 1) : safeEntryCount;
		int drawColumns = hasOverflow ? divideCeil(drawCount, lineCount) : layoutMaxPerLine;
		int overflowDisplayCount = hasOverflow ? Math.min(safeEntryCount - drawCount, 99) : 0;
		return new PreviewTooltipLayout(
			safeEntryCount,
			lineCount,
			layoutMaxPerLine,
			drawColumns,
			drawCount,
			overflowDisplayCount);
	}

	boolean hasOverflow() {
		return overflowDisplayCount > 0;
	}

	int width(int ingredientSize, int padding) {
		return maxPerLine * ingredientSize + (2 * padding);
	}

	int height(int ingredientSize, int padding) {
		return lineCount * ingredientSize + (2 * padding);
	}

	int column(int drawIndex) {
		return drawColumns <= 0 ? 0 : drawIndex % drawColumns;
	}

	int row(int drawIndex) {
		return drawColumns <= 0 ? 0 : drawIndex / drawColumns;
	}

	private static int divideCeil(int value, int divisor) {
		return divisor <= 0 ? 0 : (value + divisor - 1) / divisor;
	}
}
