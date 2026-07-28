package com.starskyxiii.collapsible_groups.compat.emi;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmiIdentityNormalizerTest {
	@Test void canonicalizesObjectKeysAndStandardItemType() {
		var result = EmiIdentityNormalizer.identify(EmiIdentityNormalizer.StandardKind.ITEM,
			JsonParser.parseString("{\"z\":2,\"type\":\"item\",\"a\":{\"y\":1,\"x\":0}}"),
			"ignored", "minecraft:stone", "");
		assertEquals("item", result.typeId());
		assertEquals("{\"a\":{\"x\":0,\"y\":1},\"type\":\"item\",\"z\":2}", result.valueId());
		assertTrue(result.serializable());
	}

	@Test void customSerializerGetsCanonicalTypeAndAliases() {
		var result = EmiIdentityNormalizer.identify(EmiIdentityNormalizer.StandardKind.CUSTOM,
			JsonParser.parseString("{\"id\":\"mod:a\",\"type\":\"mod:chemical\"}"),
			"ignored", "mod:a", "");
		assertEquals("emi:mod:chemical", result.typeId());
		assertEquals(java.util.List.of("mod:chemical", "chemical"), result.aliases());
	}

	@Test void nullSerializerUsesExplicitUnstableFallback() {
		var result = EmiIdentityNormalizer.identify(EmiIdentityNormalizer.StandardKind.CUSTOM,
			null, "example.ChemicalStack", "mod:oxygen", "components={}");
		assertEquals("emi_class:example.ChemicalStack", result.typeId());
		assertEquals("mod:oxygen|components={}", result.valueId());
		assertFalse(result.serializable());
	}
}
