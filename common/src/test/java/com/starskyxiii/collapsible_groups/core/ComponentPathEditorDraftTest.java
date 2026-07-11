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
    void componentPathRootIsPreservedAndEditable() {
        // the ComponentPath tree is preserved whole and stays editable; not flat-index safe.
        GroupFilter filter = new GroupFilter.ComponentPath(
            "irons_spellbooks:spell_container",
            "data[0].id",
            "irons_spellbooks:blood_needles"
        );
        GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

        assertTrue(result.structurallyEditable(),
            "A preserved ComponentPath root is still contents-editable");
        assertFalse(result.flatIndexSafe(),
            "A preserved advanced subtree is not flat-index safe");
        assertEquals(java.util.List.of(filter), result.preservedSubtrees());
        assertTrue(result.hasUnsupportedNodeKinds(),
            "A group containing ComponentPath must have unsupported node kinds");
        assertTrue(
            result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.COMPONENT_PATH),
            "Must report COMPONENT_PATH as the unsupported node kind"
        );
    }

    @Test
    void hasComponentRootIsPreservedAndEditable() {
        GroupFilter filter = new GroupFilter.HasComponent(
            "apotheosis:gem", "apotheosis:core/ballast"
        );
        GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

        assertTrue(result.structurallyEditable(),
            "A preserved HasComponent root is still contents-editable");
        assertFalse(result.flatIndexSafe());
        assertEquals(java.util.List.of(filter), result.preservedSubtrees());
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
    void componentPathInsideAllIsPreservedAndEditable() {
        // all(componentPath) normalizes to the bare ComponentPath (single-child All
        // collapses), which is preserved whole. Editable (auto-wrap on edit), not flat-index safe.
        GroupFilter componentPath = new GroupFilter.ComponentPath("comp", "path", "val");
        GroupFilter filter = new GroupFilter.All(java.util.List.of(componentPath));
        GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

        assertTrue(result.structurallyEditable());
        assertFalse(result.flatIndexSafe());
        assertEquals(java.util.List.of(componentPath), result.preservedSubtrees());
        assertTrue(result.hasUnsupportedNodeKinds());
        assertTrue(result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.COMPONENT_PATH));
    }

    @Test
    void componentPathInsideMultiChildAllIsPreservedWithAllKind() {
        // A genuine multi-child All survives normalization and reports the ALL kind too.
        GroupFilter filter = new GroupFilter.All(java.util.List.of(
            new GroupFilter.Tag("item", "c:ingots"),
            new GroupFilter.ComponentPath("comp", "path", "val")
        ));
        GroupFilterEditorDraft.DecodeResult result = GroupFilterEditorDraft.decode(filter);

        assertTrue(result.structurallyEditable());
        assertFalse(result.flatIndexSafe());
        assertEquals(java.util.List.of(filter), result.preservedSubtrees());
        assertTrue(result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.ALL));
        assertTrue(result.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.COMPONENT_PATH));
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
