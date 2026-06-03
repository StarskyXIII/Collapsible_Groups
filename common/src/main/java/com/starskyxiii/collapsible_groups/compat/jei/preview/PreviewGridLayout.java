package com.starskyxiii.collapsible_groups.compat.jei.preview;

public record PreviewGridLayout(
	int entryCount,
	int columns,
	int visibleRows,
	int rowOffset,
	int totalRows,
	int startIndex,
	int drawCount,
	int overflowCount
) {
	public static PreviewGridLayout fixedColumns(int entryCount, int columns, int visibleRows, int rowOffset) {
		int safeEntryCount = Math.max(0, entryCount);
		int safeColumns = Math.max(1, columns);
		int safeVisibleRows = Math.max(0, visibleRows);
		int safeRowOffset = Math.max(0, rowOffset);
		int totalRows = totalRows(safeEntryCount, safeColumns);
		int startIndex = safeRowOffset * safeColumns;
		int pageSize = safeColumns * safeVisibleRows;
		int remainingAfterFullPage = safeEntryCount - (safeRowOffset + safeVisibleRows) * safeColumns;
		boolean hasOverflow = remainingAfterFullPage > 0;
		int drawCapacity = hasOverflow ? Math.max(0, pageSize - 1) : pageSize;
		int availableFromOffset = Math.max(0, safeEntryCount - startIndex);
		int drawCount = Math.min(drawCapacity, availableFromOffset);
		int overflowCount = hasOverflow ? Math.max(0, availableFromOffset - drawCount) : 0;
		return new PreviewGridLayout(
			safeEntryCount,
			safeColumns,
			safeVisibleRows,
			safeRowOffset,
			totalRows,
			startIndex,
			drawCount,
			overflowCount);
	}

	public static int totalRows(int entryCount, int columns) {
		int safeEntryCount = Math.max(0, entryCount);
		if (safeEntryCount == 0) return 0;
		int safeColumns = Math.max(1, columns);
		return divideCeil(safeEntryCount, safeColumns);
	}

	public boolean hasOverflow() {
		return overflowCount > 0;
	}

	public int overflowColumn() {
		return Math.max(0, columns - 1);
	}

	public int overflowRow() {
		return Math.max(0, visibleRows - 1);
	}

	public void forEachCell(CellConsumer consumer) {
		for (int i = 0; i < drawCount; i++) {
			int column = i % columns;
			int row = i / columns;
			consumer.accept(startIndex + i, column, row);
		}
	}

	private static int divideCeil(int value, int divisor) {
		return divisor <= 0 ? 0 : (value + divisor - 1) / divisor;
	}

	@FunctionalInterface
	public interface CellConsumer {
		void accept(int entryIndex, int column, int row);
	}
}
