package com.starskyxiii.collapsible_groups.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GroupFilterEditorDraft} behavior with ComponentPath nodes.
 *
 * These tests confirm that ComponentPath marks a group as unsupported/read-only
 * in the editor without requiring Minecraft runtime.
 */
class ComponentPathEditorDraftTest {

    @Test
    void componentPathMarksGroupAsNotStructurallyEditable() {
        GroupFilter filter = new GroupFilter.ComponentPath(
            "irons_spellbooks:spell_container",
            "data[0].id",
            "irons_spellbooks:blood_needles"
        );
        GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

        assertFalse(result.structurallyEditable(),
            "A group containing ComponentPath must not be structurally editable");
        assertTrue(result.hasUnsupportedNodeKinds(),
            "A group containing ComponentPath must have unsupported node kinds");
        assertTrue(
            result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.COMPONENT_PATH),
            "Must report COMPONENT_PATH as the unsupported node kind"
        );
    }

    @Test
    void hasComponentStillMarksGroupAsNotStructurallyEditable() {
        GroupFilter filter = new GroupFilter.HasComponent(
            "apotheosis:gem", "apotheosis:core/ballast"
        );
        GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

        assertFalse(result.structurallyEditable(),
            "HasComponent backward compat: must still be non-editable");
        assertTrue(
            result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.HAS_COMPONENT),
            "Must still report HAS_COMPONENT"
        );
    }

    @Test
    void componentPathAndHasComponentHaveDistinctUnsupportedKinds() {
        GroupFilter cpFilter = new GroupFilter.ComponentPath("comp", "path", "val");
        GroupFilter hcFilter = new GroupFilter.HasComponent("comp", "val");

        var cpResult = GroupFilterEditorDraft.decode(cpFilter);
        var hcResult = GroupFilterEditorDraft.decode(hcFilter);

        assertTrue(cpResult.unsupportedNodeKinds().contains(
            GroupFilterEditorDraft.UnsupportedEditorNodeKind.COMPONENT_PATH));
        assertFalse(cpResult.unsupportedNodeKinds().contains(
            GroupFilterEditorDraft.UnsupportedEditorNodeKind.HAS_COMPONENT));

        assertTrue(hcResult.unsupportedNodeKinds().contains(
            GroupFilterEditorDraft.UnsupportedEditorNodeKind.HAS_COMPONENT));
        assertFalse(hcResult.unsupportedNodeKinds().contains(
            GroupFilterEditorDraft.UnsupportedEditorNodeKind.COMPONENT_PATH));
    }

    @Test
    void componentPathInsideAllIsUnsupported() {
        // all(componentPath) — the All node itself marks unsupported, ComponentPath inside
        // is also detected. Group must be non-editable.
        GroupFilter filter = new GroupFilter.All(java.util.List.of(
            new GroupFilter.ComponentPath("comp", "path", "val")
        ));
        GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

        assertFalse(result.structurallyEditable());
        assertTrue(result.hasUnsupportedNodeKinds());
    }

    @Test
    void componentPathUnsupportedNodeKindHasTranslationKeys() {
        GroupFilterEditorDraft.UnsupportedEditorNodeKind kind =
            GroupFilterEditorDraft.UnsupportedEditorNodeKind.COMPONENT_PATH;

        assertNotNull(kind.labelKey(), "COMPONENT_PATH must have a label key");
        assertNotNull(kind.reasonKey(), "COMPONENT_PATH must have a reason key");
        assertFalse(kind.labelKey().isBlank(), "COMPONENT_PATH label key must not be blank");
        assertFalse(kind.reasonKey().isBlank(), "COMPONENT_PATH reason key must not be blank");
        assertEquals(
            "collapsible_groups.editor.unsupported_node.component_path.label",
            kind.labelKey()
        );
        assertEquals(
            "collapsible_groups.editor.unsupported_node.component_path.reason",
            kind.reasonKey()
        );
    }
}
