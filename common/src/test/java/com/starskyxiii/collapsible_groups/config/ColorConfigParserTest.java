package com.starskyxiii.collapsible_groups.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorConfigParserTest {
	@Test
	void parsesArgbFormats() {
		assertEquals(0x80AABBCC, ColorConfigParser.parseArgb("#80AABBCC", 0x18FFFFFF));
		assertEquals(0x11223344, ColorConfigParser.parseArgb("0x11223344", 0x18FFFFFF));
		assertEquals(0x55667788, ColorConfigParser.parseArgb("55667788", 0x18FFFFFF));
	}

	@Test
	void rgbValuesKeepFallbackAlpha() {
		assertEquals(0x24FF0000, ColorConfigParser.parseArgb("#FF0000", 0x24FFFFFF));
		assertEquals(0x2400FF00, ColorConfigParser.parseArgb("0x00FF00", 0x24FFFFFF));
	}

	@Test
	void invalidValuesReturnFallback() {
		assertEquals(0x18FFFFFF, ColorConfigParser.parseArgb(null, 0x18FFFFFF));
		assertEquals(0x18FFFFFF, ColorConfigParser.parseArgb("#XYZ", 0x18FFFFFF));
		assertEquals(0x18FFFFFF, ColorConfigParser.parseArgb("#12345", 0x18FFFFFF));
		assertEquals(0x18FFFFFF, ColorConfigParser.parseArgb("#1234567890", 0x18FFFFFF));
	}
}
