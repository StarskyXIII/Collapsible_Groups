package com.starskyxiii.collapsible_groups.client.editor.model;

import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;

import com.starskyxiii.collapsible_groups.group.GroupTheme;
import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
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

		assertEquals(GroupIconDefinition.item("minecraft:stone"), draft.frontIconId());
		assertEquals(GroupIconDefinition.item("minecraft:diamond"), draft.backIconId());
		assertEquals(List.of(GroupIconDefinition.item("minecraft:emerald")), draft.extraIconIds());
		assertEquals(items("minecraft:stone", "minecraft:diamond", "minecraft:emerald"), draft.toIconIds());
		assertEquals(theme, draft.toTheme());
	}

	@Test
	void dropsBackAndExtraWhenFrontIsMissingBecauseJsonHasNoSparseSlot() {
		AppearanceDraft draft = new AppearanceDraft(
			null,
			GroupIconDefinition.item("minecraft:gold_ingot"),
			List.of(GroupIconDefinition.item("minecraft:emerald")),
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
		assertEquals(items("minecraft:diamond", "minecraft:stone", "minecraft:emerald"), swapped.toIconIds());
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

		assertEquals(items("minecraft:lapis_lazuli", "minecraft:diamond", "minecraft:emerald"),
			draft.withFrontIconId(" minecraft:lapis_lazuli ").toIconIds());
		assertEquals(items("minecraft:stone", "minecraft:apple", "minecraft:emerald"),
			draft.withBackIconId(" minecraft:apple ").toIconIds());
	}

	@Test
	void preservesTypedFrontBackAndExtraIcons() {
		GroupIconDefinition fluid = new GroupIconDefinition("fluid", "minecraft:water");
		GroupIconDefinition generic = new GroupIconDefinition("test:chemical", "test:oxygen");
		AppearanceDraft draft = AppearanceDraft.fromIconIds(
			List.of(fluid, generic, GroupIconDefinition.item("minecraft:diamond")), GroupTheme.EMPTY);

		assertEquals(fluid, draft.frontIconId());
		assertEquals(generic, draft.backIconId());
		assertEquals(List.of(fluid, generic, GroupIconDefinition.item("minecraft:diamond")), draft.toIconIds());
	}

	private static List<GroupIconDefinition> items(String... ids) {
		return java.util.Arrays.stream(ids).map(GroupIconDefinition::item).toList();
	}
}
