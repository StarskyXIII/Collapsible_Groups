package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import com.starskyxiii.collapsible_groups.client.widget.EditorChrome;
import com.starskyxiii.collapsible_groups.client.widget.SettingsRowLayout;
import com.starskyxiii.collapsible_groups.client.widget.SwitchHoverState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditorSettingsPanelSwitchTest {
    private static final EditorChrome.Rect CONTENT = new EditorChrome.Rect(20, 40, 240, 600);

    @Test void clickTogglesOnMouseDownAndStaysStableThroughReleaseAndNoOpWheel() {
        var draft = new Draft();
        var hover = new SwitchHoverState<String>();
        var panel = panel(draft, hover, CONTENT);
        var control = control();
        double x = control.x() + 2, y = control.y() + 2;

        assertTrue(panel.mouseClicked(x, y, 0));
        assertTrue(draft.enabled);
        assertFalse(hover.allowsHover("enabled"));
        panel.mouseReleased(x, y, 0);
        panel.mouseScrolled(x, y, 1);
        assertTrue(draft.enabled);
        assertFalse(hover.allowsHover("enabled"));
        panel.mouseClicked(x, y, 0);
        assertFalse(draft.enabled);
        assertFalse(hover.allowsHover("enabled"));
        panel.mouseDragged(control.right() + 1, y, 0);
        panel.updateSwitchHover(x, y);
        assertTrue(hover.allowsHover("enabled"));
    }

    @Test void transientCleanupAndRecreatedPanelKeepTheSameHoveredTarget() {
        var draft = new Draft();
        var hover = new SwitchHoverState<String>();
        var panel = panel(draft, hover, CONTENT);
        var control = control();
        double x = control.x() + 2, y = control.y() + 2;
        panel.mouseClicked(x, y, 0);
        panel.clearHeldCommand();
        panel.repositionElements();
        panel.onDeactivate();
        panel.onActivate();
        panel.updateSwitchHover(x, y);
        assertFalse(hover.allowsHover("enabled"));
        panel = panel(draft, hover, CONTENT);
        panel.updateSwitchHover(x, y);
        assertFalse(hover.allowsHover("enabled"));
        panel.init(CONTENT, CONTENT.y(), new EditorChrome.Rect(CONTENT.x() + 80, CONTENT.y(), CONTENT.width(), CONTENT.height()));
        panel.updateSwitchHover(x, y);
        assertTrue(hover.allowsHover("enabled"));
    }

    @Test void clippedSwitchCannotToggleAndMovingContentAwayEndsSuppression() {
        var draft = new Draft();
        var hover = new SwitchHoverState<String>();
        var panel = panel(draft, hover, CONTENT);
        var control = control();
        double x = control.x() + 2, y = control.y() + 2;
        panel.mouseClicked(x, y, 0);
        var clipped = new EditorChrome.Rect(CONTENT.x(), CONTENT.y(), CONTENT.width(), control.y() - CONTENT.y());
        panel.init(clipped, clipped.y(), clipped);
        assertFalse(panel.mouseClicked(x, y, 0));
        assertTrue(draft.enabled);
        assertTrue(hover.allowsHover("enabled"));
    }

    private static EditorSettingsPanel panel(Draft draft, SwitchHoverState<String> hover, EditorChrome.Rect content) {
        var panel = new EditorSettingsPanel(draft, null, () -> {}, List::of, hover);
        panel.init(content, content.y(), content);
        return panel;
    }

    private static SettingsRowLayout.Rect control() {
        return SettingsRowLayout.compute(CONTENT.x(), CONTENT.y(), CONTENT.width() - 9, 0,
                EditorSettingsPanel.SettingsColorTarget.values()).rows().stream()
            .filter(row -> row.kind() == SettingsRowLayout.Kind.ENABLED).findFirst().orElseThrow().switchRect();
    }

    private static final class Draft implements EditorSettingsState {
        private boolean enabled;
        @Override public AppearanceDraft appearanceDraft() { return null; }
        @Override public void setAppearanceDraft(AppearanceDraft draft) { fail("Unexpected appearance edit"); }
        @Override public int editPriority() { return 0; }
        @Override public void setEditPriority(int priority) { fail("Unexpected priority edit"); }
        @Override public boolean editEnabled() { return enabled; }
        @Override public void setEditEnabled(boolean value) { enabled = value; }
        @Override public String editId() { return "group"; }
        @Override public String pendingRawId() { return "group"; }
    }
}
