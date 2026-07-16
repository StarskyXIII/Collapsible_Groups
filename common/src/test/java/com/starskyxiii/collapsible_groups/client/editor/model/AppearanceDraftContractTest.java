package com.starskyxiii.collapsible_groups.client.editor.model;

import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;

import com.starskyxiii.collapsible_groups.group.GroupTheme;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AppearanceDraftContractTest {
	@Test
	void convertsFlatIconIdsAndThemeWithoutChangingJsonShape() {
		GroupTheme theme = new GroupTheme("#FFD166", "#11111111", "#22222222", "#33333333", "#44444444");

		AppearanceDraft draft = AppearanceDraft.fromIconIds(
			List.of("minecraft:stone", "minecraft:diamond", "minecraft:emerald"),
			theme
		);

		assertEquals("minecraft:stone", draft.frontIconId());
		assertEquals("minecraft:diamond", draft.backIconId());
		assertEquals(List.of("minecraft:emerald"), draft.extraIconIds());
		assertEquals(List.of("minecraft:stone", "minecraft:diamond", "minecraft:emerald"), draft.toIconIds());
		assertEquals(theme, draft.toTheme());
	}

	@Test
	void dropsBackAndExtraWhenFrontIsMissingBecauseJsonHasNoSparseSlot() {
		AppearanceDraft draft = new AppearanceDraft(
			null,
			"minecraft:gold_ingot",
			List.of("minecraft:emerald"),
			null,
			null,
			null,
			null,
			null
		);

		assertEquals(null, draft.frontIconId());
		assertEquals(null, draft.backIconId());
		assertEquals(List.of(), draft.extraIconIds());
		assertEquals(List.of(), draft.toIconIds());
	}

	@Test
	void clearingFrontClearsDependentIconsAndSwapRequiresTwoIcons() {
		AppearanceDraft draft = AppearanceDraft.fromIconIds(
			List.of("minecraft:stone", "minecraft:diamond", "minecraft:emerald"),
			GroupTheme.EMPTY
		);

		AppearanceDraft cleared = draft.clearFrontIcon();
		assertEquals(null, cleared.frontIconId());
		assertEquals(null, cleared.backIconId());
		assertEquals(List.of(), cleared.extraIconIds());
		assertEquals(List.of(), cleared.toIconIds());

		assertSame(cleared, cleared.swapIcons());

		AppearanceDraft swapped = draft.swapIcons();
		assertEquals(List.of("minecraft:diamond", "minecraft:stone", "minecraft:emerald"), swapped.toIconIds());
	}

	@Test
	void emptyDraftReusesEmptyThemeSingleton() {
		AppearanceDraft draft = AppearanceDraft.fromIconIds(List.of(), GroupTheme.EMPTY);

		assertSame(GroupTheme.EMPTY, draft.toTheme());
	}

	@Test
	void settingBackWithNoFrontIsNoOpAndColorsNormalizeThroughTheme() {
		AppearanceDraft draft = AppearanceDraft.fromIconIds(List.of(), GroupTheme.EMPTY)
			.withBackIconId(" minecraft:apple ")
			.withNameColor(" #ABCDEF ")
			.withExpandedGroupBorder(" ");

		assertEquals(null, draft.frontIconId());
		assertEquals(null, draft.backIconId());
		assertEquals(List.of(), draft.toIconIds());
		assertEquals("#ABCDEF", draft.toTheme().nameColor());
		assertEquals(null, draft.toTheme().expandedGroupBorder());
	}

	@Test
	void backIconRequiresFrontAndPreservesExtraWhenFrontChanges() {
		AppearanceDraft draft = AppearanceDraft.fromIconIds(
			List.of("minecraft:stone", "minecraft:diamond", "minecraft:emerald"),
			GroupTheme.EMPTY
		);

		assertEquals(List.of("minecraft:lapis_lazuli", "minecraft:diamond", "minecraft:emerald"),
			draft.withFrontIconId(" minecraft:lapis_lazuli ").toIconIds());
		assertEquals(List.of("minecraft:stone", "minecraft:apple", "minecraft:emerald"),
			draft.withBackIconId(" minecraft:apple ").toIconIds());
	}
}
