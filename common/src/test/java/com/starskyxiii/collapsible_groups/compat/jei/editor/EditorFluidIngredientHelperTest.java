package com.starskyxiii.collapsible_groups.compat.jei.editor;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorFluidIngredientHelperTest {
	@Test
	void filtersByBlankQueryNameAndResourceId() {
		EditorFluidIngredientView water = view("Water", "minecraft:water");
		EditorFluidIngredientView lava = view("Lava", "minecraft:lava");
		List<EditorFluidIngredientView> entries = List.of(water, lava);

		assertEquals(entries, EditorFluidIngredientHelper.filterViews(entries, Map.of(), false, ""));
		assertEquals(List.of(water), EditorFluidIngredientHelper.filterViews(entries, Map.of(), false, "water"));
		assertEquals(List.of(lava), EditorFluidIngredientHelper.filterViews(entries, Map.of(), false, "minecraft:lava"));
	}

	@Test
	void hidesOwnedEntriesWhenRequested() {
		EditorFluidIngredientView water = view("Water", "minecraft:water");
		EditorFluidIngredientView lava = view("Lava", "minecraft:lava");
		Map<EditorFluidIngredientView, List<String>> ownership = Map.of(water, List.of("Existing Group"));

		assertEquals(List.of(lava), EditorFluidIngredientHelper.filterViews(List.of(water, lava), ownership, true, ""));
	}

	@Test
	void buildsReverseIndexOwnership() {
		EditorFluidIngredientView water = view("Water", "minecraft:water");
		EditorFluidIngredientView lava = view("Lava", "minecraft:lava");

		Map<EditorFluidIngredientView, List<String>> ownership = EditorFluidIngredientHelper.buildOwnership(
			List.of(water, lava),
			Map.of("water_group", "Water Group"),
			List.of(),
			Map.of(
				"minecraft:water", Set.of("water_group"),
				"minecraft:lava", Set.of("unknown_group")));

		assertEquals(List.of("Water Group"), ownership.get(water));
		assertTrue(!ownership.containsKey(lava));
	}

	@Test
	void buildsTooltipLinesAndDragKey() {
		EditorFluidIngredientView water = view("Water", "minecraft:water");

		List<Component> lines = EditorFluidIngredientHelper.tooltipLines(water);

		assertEquals("Water", lines.get(0).getString());
		assertEquals("minecraft:water", lines.get(1).getString());
		assertEquals("minecraft:water", EditorFluidIngredientHelper.dragKey(water));
	}

	private static EditorFluidIngredientView view(String name, String resourceId) {
		return new EditorFluidIngredientView(
			new Object(),
			Component.literal(name),
			resourceId,
			(name + "|" + resourceId).toLowerCase(java.util.Locale.ROOT),
			null);
	}
}
