package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.group.filter.ComponentReferenceExtractor;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentReferenceExtractorTest {
	private static JsonElement json(String value) {
		return JsonParser.parseString(value);
	}

	@Test
	void effectiveMergeSuppressesRemovedSkipsFailuresDeduplicatesAndSortsPatchFirst() {
		List<ComponentReferenceExtractor.EffectiveEntry<String, JsonElement>> effective = List.of(
			new ComponentReferenceExtractor.EffectiveEntry<>("prototype-a", json("{\"value\":1}")),
			new ComponentReferenceExtractor.EffectiveEntry<>("patch-b", json("\"minecraft:sharpness\"")),
			new ComponentReferenceExtractor.EffectiveEntry<>("removed", json("99")),
			new ComponentReferenceExtractor.EffectiveEntry<>("no-id", json("1")),
			new ComponentReferenceExtractor.EffectiveEntry<>("no-codec", json("2")),
			new ComponentReferenceExtractor.EffectiveEntry<>("throws", json("3")),
			new ComponentReferenceExtractor.EffectiveEntry<>("prototype-duplicate", json("4")),
			new ComponentReferenceExtractor.EffectiveEntry<>("patch-duplicate", json("5"))
		);
		List<ComponentReferenceExtractor.PatchEntry<String>> patch = List.of(
			new ComponentReferenceExtractor.PatchEntry<>("patch-b", false),
			new ComponentReferenceExtractor.PatchEntry<>("removed", true),
			new ComponentReferenceExtractor.PatchEntry<>("patch-duplicate", false)
		);

		List<ComponentReferenceExtractor.ComponentReference> result =
			ComponentReferenceExtractor.extractEffective(
				effective,
				patch,
				"test-ops",
				key -> switch (key) {
					case "no-id" -> null;
					case "prototype-duplicate", "patch-duplicate" -> "minecraft:duplicate";
					default -> "minecraft:" + key;
				},
				(ops, key, value) -> {
					assertEquals("test-ops", ops);
					if (key.equals("no-codec")) return Optional.empty();
					if (key.equals("throws")) throw new IllegalStateException("encode failure");
					return Optional.of(value);
				}
			);

		assertEquals(List.of("minecraft:duplicate", "minecraft:patch-b", "minecraft:prototype-a"),
			result.stream().map(ComponentReferenceExtractor.ComponentReference::componentTypeId).toList());
		assertEquals(List.of(true, true, false),
			result.stream().map(ComponentReferenceExtractor.ComponentReference::fromPatch).toList());
		assertEquals("minecraft:sharpness", result.get(1).encodedValue(),
			"string primitives must be unwrapped for the production matcher");
		assertEquals("5", result.get(0).encodedValue(), "the patch-marked duplicate id must win");
	}

	@Test
	void normalizedPrefillRoundTripsThroughProductionMatcher() {
		List<ComponentReferenceExtractor.EffectiveEntry<String, JsonElement>> effective = List.of(
			new ComponentReferenceExtractor.EffectiveEntry<>("string", json("\"minecraft:sharpness\"")),
			new ComponentReferenceExtractor.EffectiveEntry<>("number", json("12")),
			new ComponentReferenceExtractor.EffectiveEntry<>("object", json("{\"levels\":{\"minecraft:sharpness\":5}}")),
			new ComponentReferenceExtractor.EffectiveEntry<>("array", json("[1,\"two\",null]"))
		);
		List<ComponentReferenceExtractor.ComponentReference> references =
			ComponentReferenceExtractor.extractEffective(
				effective, List.of(), new Object(), key -> "minecraft:" + key,
				(ops, key, value) -> Optional.of(value));

		assertEquals(4, references.size());
		for (ComponentReferenceExtractor.ComponentReference reference : references) {
			assertTrue(ItemStackIngredientView.matchesEncodedValue(
				reference.encodedJson(), reference.encodedValue()), reference.componentTypeId());
		}
	}
}
