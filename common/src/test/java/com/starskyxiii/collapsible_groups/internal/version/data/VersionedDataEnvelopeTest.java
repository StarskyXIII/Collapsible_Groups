package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionedDataEnvelopeTest {
	@Test
	void envelopeCarriesSchemaDataFormatAndSourceMinecraftIdentity() {
		String encoded = VersionedDataEnvelope.wrap(
			MinecraftItemDataFormats.EXACT_STACK_1_21_1,
			JsonParser.parseString("{\"id\":\"minecraft:stone\"}"));
		var metadata = JsonParser.parseString(encoded).getAsJsonObject()
			.getAsJsonObject("$collapsible_groups");
		assertEquals("collapsible_groups:exact_stack", metadata.get("schema").getAsString());
		assertEquals(1, metadata.get("schema_version").getAsInt());
		assertEquals("minecraft:item_components", metadata.get("data_format").getAsString());
		assertEquals("1.21.1", metadata.get("source_minecraft").getAsString());
		assertEquals("minecraft:stone", metadata.getAsJsonObject("data").get("id").getAsString());
	}

	@Test
	void ordinaryLegacyObjectWithMetadataNamedFieldsIsNotAnEnvelope() {
		String legacy = "{\"schema\":\"recipe\",\"schema_version\":7,\"data_format\":\"custom\",\"source_minecraft\":\"map_value\"}";
		assertFalse(VersionedDataEnvelope.isEnvelope(legacy));
		assertEquals(VersionedDataEnvelope.Support.LEGACY,
			VersionedDataEnvelope.inspect(legacy, MinecraftItemDataFormats.COMPONENT_VALUE_1_21_1).support());
		assertTrue(ItemStackIngredientView.matchesEncodedValue(JsonParser.parseString(legacy), legacy));
	}

	@Test
	void legacyUnquotedStringStillMatches() {
		assertTrue(ItemStackIngredientView.matchesEncodedValue(
			JsonParser.parseString("\"minecraft:sharpness\""), "minecraft:sharpness"));
	}

	@Test
	void malformedMarkedEnvelopeCannotFallBackToLegacyComparison() {
		String malformed = "{\"$collapsible_groups\":{\"schema\":\"collapsible_groups:component_value\"}}";
		assertTrue(VersionedDataEnvelope.isEnvelope(malformed));
		assertEquals(VersionedDataEnvelope.Support.MALFORMED,
			VersionedDataEnvelope.inspect(malformed, MinecraftItemDataFormats.COMPONENT_VALUE_1_21_1).support());
		assertFalse(ItemStackIngredientView.matchesEncodedValue(JsonParser.parseString(malformed), malformed));
	}

	@Test
	void oversizedSchemaVersionIsMalformedInsteadOfWrappingToCurrentVersion() {
		String oversized = "{\"$collapsible_groups\":{" +
			"\"schema\":\"collapsible_groups:exact_stack\"," +
			"\"schema_version\":4294967297," +
			"\"data_format\":\"minecraft:item_components\"," +
			"\"source_minecraft\":\"1.21.1\"," +
			"\"data\":{}}}";
		assertEquals(VersionedDataEnvelope.Support.MALFORMED,
			VersionedDataEnvelope.inspect(oversized, MinecraftItemDataFormats.EXACT_STACK_1_21_1).support());
	}
}
