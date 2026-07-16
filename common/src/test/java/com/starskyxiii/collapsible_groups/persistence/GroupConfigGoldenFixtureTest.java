package com.starskyxiii.collapsible_groups.persistence;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GroupConfigGoldenFixtureTest {
	@ParameterizedTest
	@ValueSource(strings = {
		"localized-metadata.json",
		"kubejs-item-id.json",
		"kubejs-fluid-id.json",
		"kubejs-generic-id.json"
	})
	void groupDefinitionIsSemanticallyStableAcrossRoundTrip(String fixtureName) throws IOException {
		String source = readFixture(fixtureName);
		GroupDefinition parsed = GroupConfig.fromJson(source);

		assertNotNull(parsed, fixtureName + " must parse");

		GroupDefinition reparsed = GroupConfig.fromJson(GroupConfig.toJson(parsed));
		assertNotNull(reparsed, fixtureName + " must parse after serialization");
		assertEquals(parsed, reparsed, fixtureName + " must retain its semantics");
	}

	private static String readFixture(String fixtureName) throws IOException {
		String path = "/golden-persistence/" + fixtureName;
		try (InputStream stream = GroupConfigGoldenFixtureTest.class.getResourceAsStream(path)) {
			assertNotNull(stream, "Missing fixture " + path);
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
