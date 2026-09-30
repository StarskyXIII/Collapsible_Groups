package com.starskyxiii.collapsible_groups.client.widget;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiControlFrameTest {
    @ParameterizedTest
    @CsvSource({"20,0", "20,1", "20,2", "18,0", "18,1", "18,2"})
    void frameMovesTopWhileKeepingBottomAndBackground(int height, int depth) {
        int x = 2, y = 2, width = 30;
        int[][] canvas = new int[height + 4][width + 4];
        UiSkinRenderer.drawControlFrame((left, top, right, bottom, color) -> {
            for (int row = top; row < bottom; row++)
                for (int col = left; col < right; col++) canvas[row][col] = color;
        }, x, y, width, height, depth);
        for (int row = 0; row < canvas.length; row++) {
            for (int col = 0; col < canvas[row].length; col++) {
                boolean within = col >= x && col < x + width && row >= y + depth && row < y + height;
                boolean edge = within && (row == y + depth || row == y + height - 1 || col == x || col == x + width - 1);
                assertEquals(edge, canvas[row][col] != 0, "pixel " + col + "," + row);
            }
        }
    }
}
