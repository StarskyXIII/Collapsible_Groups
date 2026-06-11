package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.core.GroupTheme;
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

		assertEquals("minecraft:stone", draft.bottomFrontIconId());
		assertEquals("minecraft:diamond", draft.topBackIconId());
		assertEquals(List.of("minecraft:emerald"), draft.extraIconIds());
		assertEquals(List.of("minecraft:stone", "minecraft:diamond", "minecraft:emerald"), draft.toIconIds());
		assertEquals(theme, draft.toTheme());
	}

	@Test
	void promotesTopIconWhenBottomIsMissing() {
		AppearanceDraft draft = new AppearanceDraft(
			null,
			"minecraft:gold_ingot",
			List.of(),
			null,
			null,
			null,
			null,
			null
		);

		assertEquals("minecraft:gold_ingot", draft.bottomFrontIconId());
		assertEquals(null, draft.topBackIconId());
		assertEquals(List.of("minecraft:gold_ingot"), draft.toIconIds());
	}

	@Test
	void clearingBottomPromotesTopAndSwapRequiresTwoIcons() {
		AppearanceDraft draft = AppearanceDraft.fromIconIds(
			List.of("minecraft:stone", "minecraft:diamond", "minecraft:emerald"),
			GroupTheme.EMPTY
		);

		AppearanceDraft promoted = draft.clearBottomFrontIcon();
		assertEquals("minecraft:diamond", promoted.bottomFrontIconId());
		assertEquals(null, promoted.topBackIconId());
		assertEquals(List.of("minecraft:diamond", "minecraft:emerald"), promoted.toIconIds());

		assertSame(promoted, promoted.swapIcons());

		AppearanceDraft swapped = draft.swapIcons();
		assertEquals(List.of("minecraft:diamond", "minecraft:stone", "minecraft:emerald"), swapped.toIconIds());
	}

	@Test
	void settingTopWithNoBottomPromotesToBottomAndColorsNormalizeThroughTheme() {
		AppearanceDraft draft = AppearanceDraft.fromIconIds(List.of(), GroupTheme.EMPTY)
			.withTopBackIconId(" minecraft:apple ")
			.withNameColor(" #ABCDEF ")
			.withExpandedGroupBorder(" ");

		assertEquals("minecraft:apple", draft.bottomFrontIconId());
		assertEquals(null, draft.topBackIconId());
		assertEquals(List.of("minecraft:apple"), draft.toIconIds());
		assertEquals("#ABCDEF", draft.toTheme().nameColor());
		assertEquals(null, draft.toTheme().expandedGroupBorder());
	}
}
