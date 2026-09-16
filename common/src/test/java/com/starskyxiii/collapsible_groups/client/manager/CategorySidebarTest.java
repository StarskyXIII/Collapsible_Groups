package com.starskyxiii.collapsible_groups.client.manager;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class CategorySidebarTest {
    private CategorySidebar sidebar(String selected) {
        CategorySidebar sidebar = new CategorySidebar();
        sidebar.entries(IntStream.range(0, 20).mapToObj(i ->
            new CategoryChoices.Entry("category-" + i, Component.literal("Category " + i))).toList(), selected);
        sidebar.layout(new ManagerHeaderLayout.Rect(0, 80, 112, 138));
        return sidebar;
    }

    @Test
    void releasingOutsideOrOnAnotherRowDoesNotNavigate() {
        CategorySidebar sidebar = sidebar("category-0");
        sidebar.press(20, 115);
        assertNull(sidebar.release(150, 115));
        sidebar.press(20, 115);
        assertNull(sidebar.release(20, 135));
        sidebar.press(20, 115);
        assertEquals("category-0", sidebar.release(20, 115).id());
    }

    @Test
    void selectedCategoryIsVisibleAfterFirstLayout() {
        CategorySidebar sidebar = sidebar("category-19");
        sidebar.press(20, 155);
        assertEquals("category-19", sidebar.release(20, 155).id());
    }

    @Test void heldFeedbackAndReleaseAreCancelledByScrollLayoutAndNewEntries() {
        CategorySidebar sidebar = sidebar("category-0");
        sidebar.press(20, 115);
        assertTrue(sidebar.isHeld(0, true));
        assertFalse(sidebar.isHeld(0, false));
        sidebar.scroll(-1);
        assertFalse(sidebar.pressed());
        assertNull(sidebar.release(20, 115));
        sidebar.press(20, 115);
        sidebar.layout(new ManagerHeaderLayout.Rect(0, 80, 112, 138));
        assertNull(sidebar.release(20, 115));
        sidebar.press(20, 115);
        sidebar.entries(java.util.List.of(new CategoryChoices.Entry("replacement", Component.literal("Replacement"))), "replacement");
        assertNull(sidebar.release(20, 115));
    }

    @Test void headingCannotSelectAndThumbStartsBelowIt() {
        CategorySidebar sidebar = sidebar("category-0");
        sidebar.press(20, 95);
        assertNull(sidebar.release(20, 95));
        sidebar.press(105, 111);
        assertTrue(sidebar.dragging());
        sidebar.drag(111);
        assertNull(sidebar.release(105, 111));
        sidebar.press(20, 115);
        assertEquals("category-0", sidebar.release(20, 115).id());
        sidebar.keyPressed(GLFW.GLFW_KEY_END);
        sidebar.focusSelection();
        assertEquals("category-0", sidebar.keyPressed(GLFW.GLFW_KEY_ENTER).id());
    }

    @Test
    void scrollingDoesNotSelectAndKeyboardCanReachManagement() {
        CategorySidebar sidebar = sidebar("category-0");
        sidebar.scroll(1);
        assertEquals("category-0", sidebar.keyPressed(GLFW.GLFW_KEY_ENTER).id());
        sidebar.scroll(-1);
        sidebar.press(20, 115);
        assertEquals("category-1", sidebar.release(20, 115).id());
        sidebar.keyPressed(GLFW.GLFW_KEY_END);
        assertEquals(CategoryChoices.MANAGE, sidebar.keyPressed(GLFW.GLFW_KEY_ENTER).id());
    }
}
