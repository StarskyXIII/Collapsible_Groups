package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.group.filter.ComponentReferenceExtractor;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import com.starskyxiii.collapsible_groups.internal.version.data.Minecraft121ItemDataAccess;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	@Test
	void finiteFloatReferenceValuesRoundTripThroughRawAndVersionedMatchers() {
		JsonPrimitive saturation = new JsonPrimitive(9.6F);
		JsonObject food = new JsonObject();
		food.addProperty("nutrition", 4);
		food.add("saturation", saturation);
		food.addProperty("can_always_eat", false);

		String saturationPickerValue = EncodedValueNormalizer.normalize(saturation);
		String foodPickerValue = EncodedValueNormalizer.normalize(food);
		assertEquals("9.6", saturationPickerValue);
		assertTrue(ItemStackIngredientView.matchesEncodedValue(saturation, saturationPickerValue));
		assertTrue(ItemStackIngredientView.matchesEncodedValue(saturation,
			new com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload("minecraft:data_component", saturation)));
		assertTrue(ItemStackIngredientView.matchesEncodedValue(food, foodPickerValue));
		assertTrue(ItemStackIngredientView.matchesEncodedValue(food,
			new com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload("minecraft:data_component", food)));

		JsonElement pathValue = ComponentPathNavigator.navigatePath(food, "saturation");
		assertTrue(ItemStackIngredientView.matchesEncodedValue(pathValue,
			EncodedValueNormalizer.normalize(pathValue)));
		assertTrue(ItemStackIngredientView.matchesEncodedValue(food,
			"{\"can_always_eat\":false,\"saturation\":9.6,\"nutrition\":4.0}"));
		assertFalse(ItemStackIngredientView.matchesEncodedValue(saturation, "9.61"));
		assertFalse(ItemStackIngredientView.matchesEncodedValue(saturation, "\"9.6\""));
	}

	@Test
	void serializationFallbackPreservesNonFiniteNumberTypes() {
		for (float value : List.of(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
			JsonPrimitive encoded = new JsonPrimitive(value);
			String quoted = "\"" + encoded.getAsString() + "\"";
			assertFalse(ItemStackIngredientView.matchesEncodedValue(
				encoded, EncodedValueNormalizer.normalize(encoded)));
			assertFalse(ItemStackIngredientView.matchesEncodedValue(encoded, quoted));
			org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                new com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload("minecraft:data_component", encoded));
		}

		JsonObject nested = new JsonObject();
		nested.addProperty("value", Float.NaN);
		assertFalse(ItemStackIngredientView.matchesEncodedValue(
			nested, EncodedValueNormalizer.normalize(nested)));
		assertFalse(ItemStackIngredientView.matchesEncodedValue(nested, "{\"value\":\"NaN\"}"));

		JsonArray nonFiniteArray = new JsonArray();
		nonFiniteArray.add(Float.NEGATIVE_INFINITY);
		nonFiniteArray.add(9.6F);
		assertFalse(ItemStackIngredientView.matchesEncodedValue(
			nonFiniteArray, EncodedValueNormalizer.normalize(nonFiniteArray)));
		assertFalse(ItemStackIngredientView.matchesEncodedValue(
			nonFiniteArray, "[\"-Infinity\",9.6]"));
		JsonArray finiteArray = new JsonArray();
		finiteArray.add(9.6F);
		finiteArray.add(foodReferenceValue());
		assertTrue(ItemStackIngredientView.matchesEncodedValue(
			finiteArray, EncodedValueNormalizer.normalize(finiteArray)));
	}

	private static JsonObject foodReferenceValue() {
		JsonObject food = new JsonObject();
		food.addProperty("nutrition", 4);
		food.addProperty("saturation", 9.6F);
		return food;
	}
}
