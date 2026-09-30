package com.starskyxiii.collapsible_groups.client.widget;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.starskyxiii.collapsible_groups.client.widget.UiSkinRenderer.*;

class UiSkinRendererButtonStateTest {
    @Test void allSegmentStatesHaveLoadableTextures() throws Exception {
        for (ButtonState state : ButtonState.values()) {
            var sprite = segmentSprite(state);
            assertNotNull(sprite, state.name());
            String path = "/assets/" + sprite.getNamespace() + "/textures/gui/sprites/" + sprite.getPath() + ".png";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var image = javax.imageio.ImageIO.read(stream);
                assertNotNull(image, path);
                assertEquals(16, image.getWidth(), path);
                assertEquals(16, image.getHeight(), path);
            }
        }
    }

    @Test void disabledOverridesHoverSelectionAndHeldFeedback() {
        for (boolean selected : new boolean[] {false, true})
            for (boolean hovered : new boolean[] {false, true})
                for (boolean held : new boolean[] {false, true})
                    assertEquals(ButtonState.DISABLED, buttonState(false, selected, hovered, held));
    }

    @Test void heldFeedbackRequiresHoverAndSelectionPersistsAfterRelease() {
        assertEquals(ButtonState.NORMAL, buttonState(true, false, false, true));
        assertEquals(ButtonState.PRESSED, buttonState(true, false, true, true));
        assertEquals(ButtonState.HOVERED, buttonState(true, false, true, false));
        assertEquals(ButtonState.SELECTED, buttonState(true, true, false, false));
        assertEquals(ButtonState.SELECTED_HOVERED, buttonState(true, true, true, false));
        assertEquals(ButtonState.SELECTED_PRESSED, buttonState(true, true, true, true));
    }

    @Test void ordinaryButtonAndToolbarUseTheirOwnTextMotion() {
        assertEquals(-1, buttonTextOffset(ButtonState.NORMAL));
        assertEquals(0, buttonTextOffset(ButtonState.HOVERED));
        assertEquals(1, buttonTextOffset(ButtonState.PRESSED));
        assertEquals(1, buttonTextOffset(ButtonState.SELECTED));
        assertEquals(0, toolbarButtonOffset(ButtonState.NORMAL));
        assertEquals(1, toolbarButtonOffset(ButtonState.HOVERED));
        assertEquals(1, toolbarButtonOffset(ButtonState.PRESSED));
    }
}
