package com.starskyxiii.collapsible_groups.group.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtEditorDraftPreservationTest {
	@Test
	void contentsDraftPreservesNativeNbtSubtrees() {
		GroupFilter source = new GroupFilter.Any(List.of(
			new GroupFilter.Nbt("{value:1b}"),
			new GroupFilter.NbtPath("value", "1b"),
			new GroupFilter.Id("item", "minecraft:stone")));
		GroupFilterEditorDraft.DecodeResult decoded = GroupFilterEditorDraft.decode(source);
		assertTrue(decoded.structurallyEditable());
		assertTrue(decoded.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.NBT));
		assertTrue(decoded.unsupportedNodeKinds().contains(GroupFilterEditorDraft.UnsupportedEditorNodeKind.NBT_PATH));
		assertEquals(source, decoded.draft().toFilter().orElseThrow());
	}
}
