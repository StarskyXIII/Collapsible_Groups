package com.starskyxiii.collapsible_groups.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientSearchQueryTest {
	@Test
	void parsesMixedTermsByPrefixAndNormalizesCase() {
		IngredientSearchQuery query = IngredientSearchQuery.parse(
			"  Shiny @MineCraft #C:ORES/IRON $FORGE:INGOTS Ingot ");

		assertEquals(List.of("shiny", "ingot"), query.textTerms());
		assertEquals(List.of("minecraft"), query.modTerms());
		assertEquals(List.of("c:ores/iron"), query.tagTerms());
		assertEquals(List.of("forge:ingots"), query.tooltipTerms());
	}

	@Test
	void matchesEveryTermByContainsAcrossDocumentValues() {
		IngredientSearchDocument document = IngredientSearchDocument.of(
			List.of("Polished Iron", "example_tools:polished_iron_ingot"),
			List.of("Example Tools", "example_tools"),
			Set.of("c:ingots/iron", "forge:ingots"));

		assertTrue(IngredientSearchQuery.parse("iron @tools #ingots/iron").matches(document));
		assertTrue(IngredientSearchQuery.parse("polished @example $forge:ing")
			.matches(document, () -> List.of("forge:ingots tooltip")));
		assertFalse(IngredientSearchQuery.parse("iron @missing").matches(document));
		assertFalse(IngredientSearchQuery.parse("iron #missing").matches(document));
	}

	@Test
	void prefixOnlyTokensArePlainTextAndEmptyPrefixedTermsNeverMatchEverything() {
		IngredientSearchQuery query = IngredientSearchQuery.parse("@ # $");
		assertEquals(List.of("@", "#", "$"), query.textTerms());
		assertEquals(List.of(), query.modTerms());
		assertEquals(List.of(), query.tagTerms());
		assertEquals(List.of(), query.tooltipTerms());
		assertFalse(query.matches(IngredientSearchDocument.of(List.of("stone"), List.of("minecraft"), Set.of())));
	}

	@Test
	void multipleTooltipTermsUseAndSemantics() {
		// Tooltip values arrive pre-normalised (lowercased, formatting stripped) from the
		// provider; the query layer matches them verbatim.
		IngredientSearchDocument document = IngredientSearchDocument.of(List.of("book"), List.of("minecraft"), Set.of());
		assertTrue(IngredientSearchQuery.parse("$sharp $level")
			.matches(document, () -> List.of("sharpness", "level v")));
		assertFalse(IngredientSearchQuery.parse("$sharp $smite")
			.matches(document, () -> List.of("sharpness", "level v")));
	}

	@Test
	void tooltipSupplierIsOnlyInvokedForTooltipTerms() {
		IngredientSearchDocument document = IngredientSearchDocument.of(List.of("book"), List.of("minecraft"), Set.of());
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		java.util.function.Supplier<List<String>> counting = () -> {
			calls.incrementAndGet();
			return List.of("sharpness");
		};
		assertTrue(IngredientSearchQuery.parse("book").matches(document, counting));
		assertTrue(IngredientSearchQuery.parse("@minecraft").matches(document, counting));
		assertFalse(IngredientSearchQuery.parse("#missing").matches(document, counting));
		assertEquals(0, calls.get());
		assertTrue(IngredientSearchQuery.parse("$sharp").matches(document, counting));
		assertEquals(1, calls.get());
	}

	@Test
	void tagQueryDoesNotFallBackToPlainText() {
		IngredientSearchDocument document = IngredientSearchDocument.of(
			List.of("c:ores/iron"), List.of("example"), Set.of());
		assertFalse(IngredientSearchQuery.parse("#c:ores/iron").matches(document));
		assertFalse(IngredientSearchQuery.parse("$c:ores/iron").matches(document));
	}

	@Test
	void documentCopiesAndNormalizesInputs() {
		List<String> text = new ArrayList<>(List.of("  COPPER Ore "));
		IngredientSearchDocument document = IngredientSearchDocument.of(text, List.of(" Example "), Set.of(" C:ORES "));
		text.add("mutated");

		assertEquals(List.of("copper ore"), document.textValues());
		assertEquals(List.of("example"), document.modValues());
		assertEquals(Set.of("c:ores"), document.tagValues());
		assertThrows(UnsupportedOperationException.class, () -> document.textValues().add("x"));
	}
}
