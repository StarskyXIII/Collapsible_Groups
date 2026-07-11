package com.starskyxiii.collapsible_groups.core;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPickerSearchQueryTest {
	@Test
	void parsesMixedTermsWhitespaceAndCaseInsensitively() {
		ItemPickerSearchQuery query = ItemPickerSearchQuery.parse("  Shiny   @MineCraft  #C:ORES/IRON  Ingot ");

		assertEquals(java.util.List.of("shiny", "ingot"), query.textTerms());
		assertEquals(java.util.List.of("minecraft"), query.namespacePrefixes());
		assertEquals(java.util.List.of("c:ores/iron"), query.tagTerms());
	}

	@Test
	void matchesEveryPlainNamespaceAndTagTerm() {
		ItemPickerSearchQuery query = ItemPickerSearchQuery.parse("Iron @example #c:ingots/iron");
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example_tools", "polished_iron_ingot");

		assertTrue(query.matches("Polished IRON", id, Set.of("c:ingots/iron")::contains));
		assertFalse(query.matches("Polished iron", id, ignored -> false));
		assertFalse(query.matches("Polished iron", ResourceLocation.fromNamespaceAndPath("other", "iron"),
			ignored -> true));
	}

	@Test
	void emptyAndLonePrefixTokensRemainWellDefined() {
		assertTrue(ItemPickerSearchQuery.parse("  ").isEmpty());
		ItemPickerSearchQuery query = ItemPickerSearchQuery.parse("@ #");
		assertEquals(java.util.List.of("@", "#"), query.textTerms());
	}
}
